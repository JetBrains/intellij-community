load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "lang-api",
    srcs = ["src"],
    iml_files = ["lang-api.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    tags = ["managed"],
    test_srcs = ["testSources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/platform-api",
        "//tools/idea/platform/lvcs-api",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools/idea/platform/indexing-api",
        "//tools/idea/platform/projectModel-api",
        "//tools/idea/platform/analysis-api",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/lvcs-api[module]",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools/idea/platform/indexing-api[module]",
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools/idea/platform/analysis-api[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
