load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "indexing-api",
    srcs = ["src"],
    iml_files = ["indexing-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/core-api"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/.idea/libraries:NanoXML",
    ],
)
