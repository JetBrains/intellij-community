load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.platform.analysis",
    srcs = ["src"],
    iml_files = ["intellij.platform.analysis.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/editor-ui-api:intellij.platform.editor"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/platform/extensions:intellij.platform.extensions[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel[module]",
        "//tools/idea/platform/editor-ui-api:intellij.platform.editor[module]",
    ],
)
