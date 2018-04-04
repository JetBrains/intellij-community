load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.tools.jetCheck",
    srcs = ["src"],
    iml_files = ["intellij.tools.jetCheck.iml"],
    tags = ["managed"],
    test_srcs = ["test"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/annotations:intellij.platform.annotations.java5[module]",
    ],
)
