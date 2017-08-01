load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "analysis-api",
    srcs = ["src"],
    iml_files = ["analysis-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/editor-ui-api"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/platform/extensions[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/platform/editor-ui-api[module]",
    ],
)
