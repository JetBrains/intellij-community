load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "jetCheck",
    srcs = ["src"],
    iml_files = ["jetCheck.iml"],
    tags = ["managed"],
    test_srcs = ["test"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/util[module, test]",
    ],
)
