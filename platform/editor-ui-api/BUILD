load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "editor-ui-api",
    srcs = ["src"],
    iml_files = ["editor-ui-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/platform/indexing-api[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
