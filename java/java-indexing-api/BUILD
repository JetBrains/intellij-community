load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "java-indexing-api",
    srcs = ["src"],
    iml_files = ["java-indexing-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/java/java-psi-api[module]",
        "//tools/idea/platform/indexing-api[module]",
    ],
)
