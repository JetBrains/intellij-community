load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.editor.ex",
    srcs = ["src"],
    iml_files = ["intellij.platform.editor.ex.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/editor-ui-api:intellij.platform.editor",
        "//tools/idea/platform/util:intellij.platform.util",
        "//tools/idea/platform/core-impl:intellij.platform.core.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/editor-ui-api:intellij.platform.editor[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/core-impl:intellij.platform.core.impl[module]",
        "//tools/idea/platform/indexing-impl:intellij.platform.indexing.impl[module]",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel[module]",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl[module]",
        "//tools/idea/platform/util:intellij.platform.util.ui[module]",
        "//tools/idea/platform/analysis-api:intellij.platform.analysis[module]",
    ],
)
