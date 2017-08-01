load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "duplicates-analysis",
    srcs = ["src"],
    iml_files = ["duplicates-analysis.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/analysis-impl",
        "//tools/idea/platform/annotations",
        "//tools/idea/platform/extensions",
        "//tools/idea/platform/util",
        "//tools/idea/platform/indexing-api",
        "//tools/idea/platform/projectModel-api",
        "//tools/idea/platform/projectModel-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/analysis-impl[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/extensions[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/indexing-api[module]",
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/platform/projectModel-impl[module]",
    ],
)
