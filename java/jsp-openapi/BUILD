load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "jsp-openapi",
    srcs = ["src"],
    iml_files = ["jsp-openapi.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:xml-structure-view-impl",
        "//tools/idea:spellchecker",
        "//tools/idea:images",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/java/jsp-base-openapi"],
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
        "//tools/idea/java/openapi[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/java/jsp-base-openapi[module]",
    ],
)
