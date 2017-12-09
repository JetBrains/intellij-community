load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "execution-openapi",
    srcs = ["src"],
    iml_files = ["execution-openapi.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/xdebugger-api"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea/resources[module]",
        "//tools/idea/platform/xdebugger-api[module]",
    ],
)
