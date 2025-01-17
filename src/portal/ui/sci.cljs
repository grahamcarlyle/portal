(ns ^:no-doc portal.ui.sci
  (:require [sci.core :as sci]))

(defn- find-ns* [ctx ns-sym]
  (sci/eval-form ctx (list 'clojure.core/the-ns (list 'quote ns-sym))))

(defonce ctx (atom nil))

(defn eval-string [msg]
  (try
    (let [ctx    @ctx
          sci-ns (if-let [ns (some-> msg :ns symbol)]
                   (try (find-ns* ctx ns)
                        (catch :default _
                          (sci/create-ns ns nil)))
                   (sci/create-ns 'cljs.user nil))
          reader (sci/reader (:code msg))
          stdio  (atom [])
          out-fn (fn [val] (swap! stdio conj val))]
      (when-let [n (:line msg)]   (set! (.-line reader) n))
      (when-let [n (:column msg)] (set! (.-column reader) n))
      (sci/with-bindings
        {sci/*1 *1
         sci/*2 *2
         sci/*3 *3
         sci/*e *e
         sci/ns sci-ns
         sci/print-newline true
         sci/print-fn      #(out-fn {:tag :out :val %})
         sci/print-err-fn  #(out-fn {:tag :err :val %})
         sci/file (:file msg)}
        (cond->
         {:value (loop [last-val nil]
                   (let [form (sci/parse-next ctx reader)]
                     (if (= ::sci/eof form)
                       last-val
                       (let [value (sci/eval-form ctx form)]
                         (set! *3 *2)
                         (set! *2 *1)
                         (set! *1 value)
                         (recur value)))))
          :ns    (str @sci/ns)}

          (seq @stdio) (assoc :stdio @stdio))))
    (catch :default e
      (let [sci-error? (isa? (:type (ex-data e)) :sci/error)]
        (throw (if sci-error?
                 (or (ex-cause e) e)
                 e))))))
