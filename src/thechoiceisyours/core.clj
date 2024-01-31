(ns thechoiceisyours.core
  (:require
   [clojure.java.shell :as shell]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [org.httpkit.server :as server])
  (:import
   [java.time Instant LocalDateTime ZoneOffset]))

(defn app
  [request]
  (condp #(string/starts-with? %1 %2) (:uri request)
    "/"
    (do (shell/sh "sh" "-c"
                  (str "rm -f resources/public/output.mp4;"
                       "rm -f resources/video_segments.txt"))
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body    (io/input-stream (io/resource "public/index.html"))})
    "/trailer"
    {:status  200
     :headers {"Content-Type" "text/html"
               "Refresh"      "46; url=/"}
     :body    (io/input-stream (io/resource "public/trailer.html"))}
    "/generate_trailer"
    (if-let [body (some-> (:body request) slurp)]
      (let [now (LocalDateTime/ofInstant (-> (java.time.Instant/now)
                                             (.plusSeconds 25))
                                         (ZoneOffset/systemDefault))
            time (format "%02d%02d"
                         (.getHour now)
                         (.getMinute now))
            params (->> (string/split body #"&")
                        (reduce (fn [accl s]
                                  (let [[k v] (string/split s #"=")]
                                    (assoc accl (keyword k) v)))
                                {:b (str (inc (rand-int 3)))
                                 :e (str (inc (rand-int 3)))
                                 :g (str (inc (rand-int 3)))
                                 :h (str (inc (rand-int 3)))
                                 :time time}))]
        (shell/sh "sh" "-c"
                  (str "rm -f resources/video_segments.txt;"
                       (format "cat > resources/video_segments.txt << EOF
file 'segments/%s-a.mp4'
file 'segments/%s-b-%s.mp4'
file 'segments/%s-%s.mp4'
file 'segments/%s-d.mp4'
file 'segments/%s-e-%s.mp4'
file 'segments/%s-f.mp4'
file 'segments/%s-g-%s.mp4'
file 'segments/%s-h-%s.mp4'
EOF"
                               (:choice params)
                               (:choice params)
                               (:b params)
                               (:choice params)
                               (:time params)
                               (:choice params)
                               (:choice params)
                               (:e params)
                               (:choice params)
                               (:choice params)
                               (:g params)
                               (:choice params)
                               (:h params))))
        (shell/sh "sh" "-c"
                  (string/join " "
                               ["ffmpeg"
                                "-hide_banner"
                                "-loglevel"
                                "error"
                                "-f concat"
                                "-safe 0"
                                "-i resources/video_segments.txt"
                                "-c copy"
                                "-y"
                                (-> (io/resource "public")
                                    io/file
                                    (.getAbsolutePath)
                                    (str "/output.mp4"))]))
        (if (io/resource "public/output.mp4")
          {:status  301
           :headers {"Location" "/trailer"}}
          {:status  301
           :headers {"Location" "/"}}))
      {:status  301
       :headers {"Location" "/"}})
    "/trailer.mp4"
    (if (io/resource "public/output.mp4")
      {:status  200
       :headers {"Content-Type" "video/mp4"}
       :body    (io/input-stream (io/resource "public/output.mp4"))}
      {:status  404
       :headers {"Content-Type" "text/html"}
       :body    "Error 404"})
    "/app.css"
    {:status  200
     :headers {"Content-Type" "text/css"}
     :body    (io/input-stream (io/resource "public/app.css"))}
    "/librebaskerville-regular.woff"
    {:status  200
     :headers {"Content-Type" "font/woff"}
     :body    (-> (io/resource "public/librebaskerville-regular.woff")
                  io/input-stream)}
    "/sprite-desktop-default.png"
    {:status  200
     :headers {"Content-Type" "image/png"}
     :body    (-> (io/resource "public/sprite-desktop-default.png")
                  io/input-stream)}
    "/meta-sm.jpg"
    {:status  200
     :headers {"Content-Type" "image/jpg"}
     :body    (io/input-stream (io/resource "public/meta-sm.jpg"))}
    ;; else
    {:status  404
     :body    ""}))

(defn -main
  [& args]
  (server/run-server #'app {:port 8080})
  (println "Server started on http://127.0.0.1:8080"))
