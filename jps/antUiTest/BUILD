load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "antUiTest",
    srcs = ["src"],
    iml_files = ["antUiTest.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/ant[module]",
        "//tools/adt/idea/uitest-framework[module, test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/idea/.idea/libraries:truth[test]",
    ],
)
