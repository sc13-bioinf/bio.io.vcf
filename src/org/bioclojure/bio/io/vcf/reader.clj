(ns org.bioclojure.bio.io.vcf.reader
  (:require [clojure.java.io :as io]
            [org.bioclojure.bio.io.vcf.header-parser :refer [parse-headers]]
            [org.bioclojure.bio.io.vcf.variant-parser :refer [variant-parser]])
  (:import [java.util.zip GZIPInputStream]
           [java.io IOException]))

(def ^:private header? (partial re-find #"^#"))

(defn- open-input-stream
  "Attempt to open the stream `s` as a GZIP input stream. If it is not
  in GZIP format, reset and return the raw stream."
  [s]
  (let [raw-stream (io/input-stream s)]
    (try
      (.mark raw-stream 1024)
      (GZIPInputStream. raw-stream)
      (catch IOException e
        (if (= (.getMessage e) "Not in GZIP format")
          (doto raw-stream (.reset))
          (throw e))))))

;; The VcfReader record stores a java.io.BufferedReader and the parsed
;; VCF headers. We implement the java.io.Closeable interface so that a
;; VcfReader object can be used in with-open.
(defrecord VcfReader [reader headers]
  java.io.Closeable
  (close [_]
    (.close reader)))

(defn vcf-reader
  "Open a VCF for reading and parse the header. `vcf` can be anything
  understood by clojure.java.io/input-stream. Gzip-compressed data is
  detected and automatically decompressed."
  [vcf]
  (let [reader (io/reader (open-input-stream vcf))
        headers (parse-headers (take-while header? (line-seq reader)))]
    (VcfReader. reader headers)))

(defn sample-ids
  "Return a list of sample ids from the VCF."
  [vcf]
  (drop 9 (get-in vcf [:headers :columns])))

(defn header
  "Return the specified header from the VCF. When no keys are
  specified, return a map with all headers from the VCF."
  [vcf & ks]
  (get-in vcf (into [:headers] ks)))

(defn variant-seq
  "Return a lazy sequence of parsed variants from the VCF. Akin to
  line-seq."
  [vcf]
  (map (variant-parser (:headers vcf)) (line-seq (:reader vcf))))

(defn pass?
  "Returns true if any of the variant filter values are 'PASS',
  otherwise false."
  [variant]
  (some #(= % "PASS") (:filter variant)))
