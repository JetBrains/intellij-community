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
        "//tools/idea/RegExpSupport",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/xml/relaxng",
        "//tools/idea/json",
        "//tools/idea/images",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/testFramework"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jps/model-impl:jps-model-impl[module]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/platform/testFramework/extensions:testExtensions[module, test]",
    ],
)
