def get_jvm_flags(flags):
    return [
        # Memory
        # "-XX:+UseZGC",
        # "-XX:+ZGenerational",
        "-Xms4g",
        "-Xmx16g",
        # IJ PSI cache
        "-XX:SoftRefLRUPolicyMSPerMB=50",
        # Code Cache
        "-XX:NonProfiledCodeHeapSize=512m",
        "-XX:ProfiledCodeHeapSize=512m",
        "-XX:ReservedCodeCacheSize=2048m",
        # Headless
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
        # https://github.com/netty/netty/issues/11532
        "-Dio.netty.tryReflectionSetAccessible=true",
        # see TargetConfigurationDigestProperty.KOTLIN_VERSION - we invalidate cache if kotlinc version changed
        "-Dkotlin.jps.skip.cache.version.check=true",
    ] + flags
