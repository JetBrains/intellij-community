load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "debugger-openapi",
    srcs = ["src"],
    iml_files = ["debugger-openapi.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
    ],
    visibility = ["//visibility:public"],
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
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea/java/jsp-openapi[module]",
        "//tools/idea/java/java-psi-api[module]",
        "//tools/idea/resources-en[module]",
    ],
)
