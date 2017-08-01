load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "analysis-impl",
    srcs = ["src"],
    iml_files = ["analysis-impl.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/analysis-api",
        "//tools/idea/platform/core-impl",
        "//tools/idea/platform/projectModel-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/analysis-api[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/extensions[module]",
        "//tools/idea/resources-en[module]",
        "//tools/idea/platform/projectModel-impl[module]",
        "//tools/idea/platform/editor-ui-ex[module]",
        "//tools/idea/platform/indexing-impl[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
