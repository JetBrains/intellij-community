load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "analysis-impl",
    srcs = ["src"],
    iml_files = ["analysis-impl.iml"],
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
        "//tools/idea/platform/analysis-api",
        "//tools/idea/platform/core-impl",
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
        "//tools/idea/platform/analysis-api[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/extensions[module]",
        "//tools/idea/resources-en[module]",
        "//tools/idea/platform/projectModel-impl[module]",
        "//tools/idea/platform/editor-ui-ex[module]",
        "//tools/idea/platform/indexing-impl[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/uast/uast-common[module]",
        "//tools/idea/platform/external-system-api[module]",
    ],
)
