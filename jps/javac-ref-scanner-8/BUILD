load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "javac-ref-scanner-8",
    srcs = ["src"],
    iml_files = ["javac-ref-scanner-8.iml"],
    tags = ["managed"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jps/jps-builders[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/idea/platform/core-api[module, test]",
        "//tools/idea:platform-impl_and_others[module]",
    ],
)
