load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "jps-standalone-builder",
    srcs = ["src"],
    iml_files = ["jps-standalone-builder.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/json",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/jps/model-serialization:jps-model-serialization"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/java/java-runtime",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/.idea/libraries:cli-parser",
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/idea/.idea/libraries:gant-groovy",
        "//tools/idea/.idea/libraries:Log4J",
    ],
)
