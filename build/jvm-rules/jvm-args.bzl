def get_jvm_flags(flags):
    return [
        # G1GC works best for builds with small to medium amount of changes; returns unused committed heap pages to the OS when idle (JEP 346)
        "-XX:+UseG1GC",
        # Grow heap when less than 40% free memory is available; after GC allow max 40% of unused memory and reclaim the rest of committed memory to OS
        # setting values too low might lead to more frequent GCs during work
        "-XX:MinHeapFreeRatio=40",
        "-XX:MaxHeapFreeRatio=40",

        # Generational ZGC works good for bigger builds, but is less optimal for small and medium builds; returns unused committed heap pages to the OS when idle
        #"-XX:+UseZGC",
        #"-XX:+ZGenerational",
        #"-XX:ZUncommitDelay=10",   # reclaim unused memory in 10 secs
        #"-XX:SoftMaxHeapSize=2g",  # try to keep heap compact under 2 Gbytes

        "-Xmx20g",
        "-Xms256m",

        # IJ PSI cache
        "-XX:SoftRefLRUPolicyMSPerMB=50",

        # Code Cache (JDK defaults are sufficient — 240m total, auto-segmented)
        #"-XX:NonProfiledCodeHeapSize=512m",
        #"-XX:ProfiledCodeHeapSize=512m",
        #"-XX:ReservedCodeCacheSize=2048m",

        # Prevent JVM logging warnings and errors to stdout because it breaks the protocol between Bazel and the worker process
        "-XX:+DisplayVMOutputToStderr",
        "-Xlog:disable",
        "-Xlog:all=warning:stderr:uptime,level,tags",
        # Headless
        "-Djava.awt.headless=true",
        "-Dapple.awt.UIElement=true",

        # Allow querying OS-specific error message for hardlinks-related error detection on Windows
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED",

        # kotlin compiler
        "-Dkotlin.environment.keepalive=true",

        # https://github.com/netty/netty/issues/11532
        "-Dio.netty.tryReflectionSetAccessible=true",

        # Set UTF-8 by default as per https://openjdk.org/jeps/400
        "-Dfile.encoding=UTF-8",
    ] + flags
