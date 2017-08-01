load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "json-tests",
    iml_files = ["json-tests.iml"],
    tags = ["managed"],
    test_resources = ["testData"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea:compiler-impl_and_others[module]",
    ],
)
