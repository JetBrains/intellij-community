load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "indexing-impl",
    srcs = ["src"],
    iml_files = ["indexing-impl.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/indexing-api",
        "//tools/idea/.idea/libraries:NanoXML",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea/platform/indexing-api[module]",
        "//tools/idea/platform/projectModel-impl[module]",
        "//tools/idea/.idea/libraries:NanoXML",
    ],
)
