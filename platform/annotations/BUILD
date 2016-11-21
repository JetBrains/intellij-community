load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "annotations",
    srcs = ["java5/src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/annotations/common:annotations-common"],
    deps = ["//tools/idea/platform/annotations/common:annotations-common[module]"],
)
