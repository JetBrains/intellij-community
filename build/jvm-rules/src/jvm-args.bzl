def get_jvm_flags(flags):
    return [
        # "-XX:+UseZGC",
        # "-XX:+ZGenerational",
        "-Xms2g",
        "-Xmx8g",
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
    ] + flags
