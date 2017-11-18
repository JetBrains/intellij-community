load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "antUiTest",
    srcs = ["src"],
    iml_files = ["antUiTest.iml"],
    tags = ["managed"],
    test_runtime_deps = ["//tools/idea/plugins/maven:maven2-server-impl"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:lvcs-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/ant[module]",
        "//tools/adt/idea/uitest-framework[module, test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/idea/.idea/libraries:truth[test]",
    ],
)
