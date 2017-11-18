load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "execution-openapi",
    srcs = ["src"],
    iml_files = ["execution-openapi.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:xml",
        "//tools/idea:spellchecker",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/xdebugger-api"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:RegExpSupport",
        "//tools/idea:xml-analysis-impl",
        "//tools/idea:xdebugger-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:dom-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
        "//tools/idea:lvcs-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea/resources[module]",
        "//tools/idea/platform/xdebugger-api[module]",
    ],
)
