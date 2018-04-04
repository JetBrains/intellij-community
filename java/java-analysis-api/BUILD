load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.java.analysis",
    srcs = ["src"],
    iml_files = ["intellij.java.analysis.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/uast/uast-common:intellij.platform.uast"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/analysis-api:intellij.platform.analysis[module]",
        "//tools/idea/java/java-psi-api:intellij.java.psi[module]",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/uast/uast-common:intellij.platform.uast[module]",
    ],
)
