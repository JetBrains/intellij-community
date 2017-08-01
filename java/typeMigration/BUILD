load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "typeMigration",
    srcs = ["src"],
    iml_files = ["typeMigration.iml"],
    tags = ["managed"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea/platform/structuralsearch[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/java/structuralsearch-java[module]",
    ],
)
