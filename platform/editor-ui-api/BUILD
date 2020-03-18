load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.editor",
    srcs = ["src"],
    iml_files = ["intellij.platform.editor.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel[module]",
        "//tools/idea/platform/indexing-api:intellij.platform.indexing[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:jetbrains-annotations",
        "//tools/idea/platform/util-ex:intellij.platform.util.ex[module]",
        "//tools/idea/platform/util:intellij.platform.concurrency[module]",
        "//tools/idea/platform/util:intellij.platform.util.ui[module]",
        "//tools/idea/platform/core-ui:intellij.platform.core.ui[module]",
    ],
)
