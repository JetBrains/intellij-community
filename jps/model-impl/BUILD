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
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea:platform-impl_and_others"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jps/model-impl:jps-model-impl[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:assertJ[test]",
    ],
)
