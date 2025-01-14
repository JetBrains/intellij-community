def get_jvm_flags(flags):
    return [
#         "-XX:+UseZGC",
#         "-XX:+ZGenerational",
        "-Xms2g",
        "-Xmx8g",
        "-XX:ReservedCodeCacheSize=512m",
        "-Djava.awt.headless=true",
        "-Dapple.awt.UIElement=true",
        "-Didea.io.use.nio2=true",
        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "-Dkotlin.environment.keepalive=true",
    ] + flags
