load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.analysis.impl",
    srcs = ["src"],
    iml_files = ["intellij.platform.analysis.impl.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/analysis-api:intellij.platform.analysis",
        "//tools/idea/platform/core-impl:intellij.platform.core.impl",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/analysis-api:intellij.platform.analysis[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/core-impl:intellij.platform.core.impl[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/extensions:intellij.platform.extensions[module]",
        "//tools/idea/resources-en:intellij.java.resources.en[module]",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl[module]",
        "//tools/idea/platform/editor-ui-ex:intellij.platform.editor.ex[module]",
        "//tools/idea/platform/indexing-api:intellij.platform.indexing[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/platform/util:intellij.platform.util.ui[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:HdrHistogram",
        "//tools/idea/.idea/libraries:protobuf",
    ],
)
