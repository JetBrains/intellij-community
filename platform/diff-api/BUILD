load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.platform.diff",
    srcs = ["src"],
    iml_files = ["intellij.platform.diff.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/platform-api:intellij.platform.ide"],
    deps = ["//tools/idea/platform/platform-api:intellij.platform.ide[module]"],
)
