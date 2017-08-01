load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "structuralsearch-java",
    srcs = ["src"],
    iml_files = ["structuralsearch-java.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/structuralsearch[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/platform/duplicates-analysis[module]",
    ],
)
