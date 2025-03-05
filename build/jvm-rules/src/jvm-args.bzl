def get_jvm_flags(flags):
    return [
        # "-XX:+UseZGC",
        # "-XX:+ZGenerational",
        "-Xms2g",
        "-Xmx16g",
        "-XX:ReservedCodeCacheSize=512m",
        "-Djava.awt.headless=true",
        "-Dapple.awt.UIElement=true",
        # IJ PHM
        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        # Apache Arrow, but we already opened java.nio for PHM
        # "--add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED",
        # kotlin compiler
        "-Dkotlin.environment.keepalive=true",
        "-Didea.io.use.nio2=true",
        # a new one is quite buggy (broken in netty 4.2.0-RC3, so we use RC2)
        "-Dio.netty.allocator.type=pooled",
        # see TargetConfigurationDigestProperty.KOTLIN_VERSION - we invalidate cache if kotlinc version changed
        "-Dkotlin.jps.skip.cache.version.check=true",
        "-Djps.track.library.dependencies=true",
    ] + flags
