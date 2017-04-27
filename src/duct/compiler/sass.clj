(ns duct.compiler.sass
  (:import [io.bit3.jsass Options]
           [io.bit3.jsass.context FileContext])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [integrant.core :as ig]
            [medley.core :as m]))

(def ^:private compiler (io.bit3.jsass.Compiler.))

(def ^:private re-ext #"\.([^/]*?)$")

(defn- file-ext [f]
  (second (re-find re-ext (str f))))

(defn- find-files [dir exts]
  (filter (comp (set exts) file-ext) (file-seq (io/file dir))))

(defn- relative-path [dir file]
  (str (.relativize (.toPath (io/file dir)) (.toPath (io/file file)))))

(defn- css-output-file [input-file source-path output-path]
  (-> (relative-path source-path input-file)
      (str/replace re-ext ".css")
      (as-> f (io/file output-path f))))

(defn- file-mapping-1 [source-path output-path]
  (->> (find-files source-path ["scss" "sass"])
       (map (juxt identity #(css-output-file % source-path output-path)))
       (into {})))

(defn- file-mapping [{:keys [source-paths output-path]}]
  (into {} (map #(file-mapping-1 % output-path)) source-paths))

(defn- timestamp-map [files]
  (into {} (map (juxt identity #(.lastModified %)) files)))

(defn- compile-sass [in out]
  (let [context (FileContext. (.toURI in) (.toURI out) (Options.))
        result  (.compile compiler context)]
    (.mkdirs (.getParentFile out))
    (spit out (.getCss result))))

(defn- remove-unchanged [in->out file->timestamp]
  (m/filter-keys #(some-> (file->timestamp %) (< (.lastModified %))) in->out))

(defn- compile-results [in->out]
  {:output     (map (comp str val) in->out)
   :timestamps (timestamp-map (keys in->out))})

(derive :duct.compiler/sass :duct/compiler)

(defmethod ig/init-key :duct.compiler/sass [_ opts]
  (let [in->out (file-mapping opts)]
    (run! (fn [[in out]] (compile-sass in out)) in->out)
    (compile-results in->out)))

(defmethod ig/resume-key :duct.compiler/sass [key opts _ {:keys [timestamps]}]
  (let [in->out (file-mapping opts)]
    (run! (fn [[in out]] (compile-sass in out)) (remove-unchanged in->out timestamps))
    (compile-results in->out)))
