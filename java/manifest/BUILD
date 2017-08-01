load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "manifest",
    srcs = ["src"],
    iml_files = ["manifest.iml"],
    tags = ["managed"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
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
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea:platform-impl_and_others[module]",
    ],
)
