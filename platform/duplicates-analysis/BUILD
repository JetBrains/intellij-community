load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "duplicates-analysis",
    srcs = ["src"],
    iml_files = ["duplicates-analysis.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:xml-structure-view-impl",
        "//tools/idea:spellchecker",
        "//tools/idea:images",
    ],
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
    runtime_deps = [
        "//tools/idea:RegExpSupport",
        "//tools/idea:xml-analysis-impl",
        "//tools/idea:xdebugger-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:dom-impl",
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
