java_import(
    name = "commons-cli-1.2",
    jars = ["commons-cli-1.2.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "utilities",
    srcs = [
        "jarjar-1.5.jar",
        "launcher-generator.jar",
    ] + glob([
        "gant/lib/*",
        "jps/*",
    ]),
    visibility = ["//visibility:public"],
)

java_import(
    name = "gant/lib/gant-1.9.11_groovy-2.3.0",
    jars = ["gant/lib/gant-1.9.11_groovy-2.3.0.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)
