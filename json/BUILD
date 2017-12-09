load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "json",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["json.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    resources = ["resources"],
    tags = ["managed"],
    test_runtime_deps = ["//tools/idea/images"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/spellchecker[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:gson",
    ],
)
