load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java.analysis",
    srcs = ["src"],
    iml_files = ["intellij.java.analysis.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/uast/uast-common:intellij.platform.uast"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/analysis-api:intellij.platform.analysis[module]",
        "//tools/idea/java/java-psi-api:intellij.java.psi[module]",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/uast/uast-common:intellij.platform.uast[module]",
    ],
)
