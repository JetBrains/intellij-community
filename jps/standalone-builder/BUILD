load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "jps-standalone-builder",
    srcs = ["src"],
    iml_files = ["jps-standalone-builder.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/jps/model-serialization:jps-model-serialization"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/jps/jps-builders[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/.idea/libraries:cli-parser",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/idea/build/lib:gant/lib/gant-1.9.11_groovy-2.3.0",
        "//tools/idea/.idea/libraries:Log4J",
    ],
)
