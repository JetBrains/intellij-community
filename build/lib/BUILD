filegroup(
    name = "utilities",
    srcs = [
        "jarjar-1.5.jar",
        "jarjar-1.0.jar",
        "launcher-generator.jar",
    ] + glob([
        "gant/lib/*",
        "jps/*",
    ]),
    visibility = ["//visibility:public"],
)

java_import(
    name = "jps",
    jars = [
        "jps/jps-builders.jar",
        "jps/jps-model.jar",
        "jps/util.jar",
    ],
    visibility = ["//visibility:public"],
)
