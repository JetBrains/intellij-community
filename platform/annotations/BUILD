load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.platform.annotations.java5",
    srcs = ["java5/src"],
    iml_files = ["intellij.platform.annotations.java5.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/annotations/common:intellij.platform.annotations.common"],
    deps = ["//tools/idea/platform/annotations/common:intellij.platform.annotations.common[module]"],
)

iml_module(
    name = "intellij.platform.annotations",
    srcs = ["java8/src"],
    iml_files = ["java8/intellij.platform.annotations.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/annotations/common:intellij.platform.annotations.common"],
    deps = ["//tools/idea/platform/annotations/common:intellij.platform.annotations.common[module]"],
)
