load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "annotations",
    srcs = ["java5/src"],
    iml_files = ["annotations.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/annotations/common:annotations-common"],
    deps = ["//tools/idea/platform/annotations/common:annotations-common[module]"],
)

iml_module(
    name = "annotations-java8",
    srcs = ["java8/src"],
    iml_files = ["java8/annotations-java8.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/annotations/common:annotations-common"],
    deps = ["//tools/idea/platform/annotations/common:annotations-common[module]"],
)
