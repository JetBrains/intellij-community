load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "editor-ui-ex",
    srcs = ["src"],
    iml_files = ["editor-ui-ex.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/editor-ui-api",
        "//tools/idea/platform/util",
        "//tools/idea/platform/annotations",
        "//tools/idea/platform/core-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea/platform/indexing-impl[module]",
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/platform/analysis-api[module]",
        "//tools/idea/platform/projectModel-impl[module]",
    ],
)
