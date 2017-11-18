load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "debugger-openapi",
    srcs = ["src"],
    iml_files = ["debugger-openapi.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:xml",
        "//tools/idea:spellchecker",
    ],
    visibility = ["//visibility:public"],
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
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea/java/jsp-openapi[module]",
        "//tools/idea/java/java-psi-api[module]",
        "//tools/idea/resources-en[module]",
    ],
)
