load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.indexing",
    srcs = ["src"],
    iml_files = ["intellij.platform.indexing.iml"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/core-api:intellij.platform.core"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel[module]",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools/idea/.idea/libraries:JDOM",
    ],
)
