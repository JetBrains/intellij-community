load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "dvcs-impl",
    # do not sort: must match IML order
    srcs = ["src"],
    iml_files = ["dvcs-impl.iml"],
    resources = ["resources"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:xml-structure-view-impl",
        "//tools/idea:spellchecker",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
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
        "//tools/idea/platform/core-api[module]",
        "//tools/idea:vcs-impl[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea:platform-impl[module]",
        "//tools/idea/platform/vcs-log/api:vcs-log-api[module]",
        "//tools/idea/platform/vcs-log/impl:vcs-log-impl[module]",
        "//tools/idea:images[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea/platform/dvcs-api[module]",
        "//tools/idea:testFramework[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/platform/vcs-tests[module, test]",
    ],
)
