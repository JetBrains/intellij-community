load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "jps-model-impl",
    srcs = ["src"],
    iml_files = ["jps-model-impl.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
    ],
)

iml_module(
    name = "jps-model-tests",
    iml_files = ["jps-model-tests.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:RegExpSupport",
        "//tools/idea:lvcs-impl",
        "//tools/idea:xml-structure-view-impl",
        "//tools/idea:spellchecker",
        "//tools/idea:relaxng",
        "//tools/idea:json",
        "//tools/idea:images",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea:testFramework"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jps/model-impl:jps-model-impl[module]",
        "//tools/idea:testFramework[module, test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea:testExtensions[module, test]",
    ],
)
