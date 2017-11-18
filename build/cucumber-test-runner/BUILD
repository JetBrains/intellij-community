load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "cucumber-test-runner",
    srcs = ["src"],
    iml_files = ["cucumber-test-runner.iml"],
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
        "//tools/idea/plugins/cucumber-jvm-formatter",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
        "//tools/idea:lvcs-impl",
        "//tools/idea:platform-resources",
        "//tools/idea:RegExpSupport",
        "//tools/idea:xml-analysis-impl",
        "//tools/idea:xdebugger-impl",
        "//tools/idea:dom-openapi",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:dom-impl",
        "//tools/idea:images",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:testFramework[module]",
        "//tools/idea/.idea/libraries:cucumber-core",
    ],
)
