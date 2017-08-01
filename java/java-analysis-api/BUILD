load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "java-analysis-api",
    srcs = ["src"],
    iml_files = ["java-analysis-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/analysis-api[module]",
        "//tools/idea/java/java-psi-api[module]",
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
