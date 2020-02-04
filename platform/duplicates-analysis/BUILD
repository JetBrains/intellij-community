load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.duplicates.analysis",
    srcs = ["src"],
    iml_files = ["intellij.platform.duplicates.analysis.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/analysis-impl:intellij.platform.analysis.impl",
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/platform/extensions:intellij.platform.extensions",
        "//tools/idea/platform/util:intellij.platform.util",
        "//tools/idea/platform/indexing-api:intellij.platform.indexing",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
        "//tools/idea/.idea/libraries:javassist",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/analysis-impl:intellij.platform.analysis.impl[module]",
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/platform/extensions:intellij.platform.extensions[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/indexing-api:intellij.platform.indexing[module]",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel[module]",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl[module]",
        "//tools/idea/.idea/libraries:XStream",
    ],
)
