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
    name = "gant/lib/gant-1.9.11_groovy-2.3.0",
    jars = ["gant/lib/gant-1.9.11_groovy-2.3.0.jar"],
    tags = ["managed"],
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
