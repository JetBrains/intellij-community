load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "json-tests",
    iml_files = ["json-tests.iml"],
    tags = ["managed"],
    test_resources = ["testData"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:lvcs-impl",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:execution-impl",
        "//tools/idea:external-system-impl",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:testFramework[module, test]",
        "//tools/idea:json[module, test]",
        "//tools/idea:jps-builders_and_others[module, test]",
        "//tools/idea:spellchecker[module, test]",
    ],
)
