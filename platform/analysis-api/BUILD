load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.analysis",
    srcs = ["src"],
    iml_files = ["intellij.platform.analysis.iml"],
    resources = ["resources"],
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
        "//tools/idea/platform/util:intellij.platform.util.ui[module]",
    ],
)
