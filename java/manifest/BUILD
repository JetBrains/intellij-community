load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "manifest",
    srcs = ["src"],
    iml_files = ["manifest.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:execution-impl",
        "//tools/idea:external-system-impl",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:xdebugger-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/analysis-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea/java/java-psi-api[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea:jps-builders_and_others[module, test]",
        "//tools/idea:spellchecker[module]",
    ],
)
