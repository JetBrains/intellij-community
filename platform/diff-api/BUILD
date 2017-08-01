load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "diff-api",
    srcs = ["src"],
    iml_files = ["diff-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/platform-api"],
    deps = ["//tools/idea/platform/platform-api[module]"],
)
