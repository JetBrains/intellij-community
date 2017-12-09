load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "RegExpSupport",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["RegExpSupport.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/images",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/dom-impl",
        "//tools/idea/xml/relaxng",
        "//tools/idea/spellchecker",
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea/.idea/libraries:Jaxen",
        "//tools/idea/xml/xml-psi-api[module]",
    ],
)
