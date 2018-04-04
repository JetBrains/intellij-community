load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.platform.indexing.impl",
    srcs = ["src"],
    iml_files = ["intellij.platform.indexing.impl.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/indexing-api:intellij.platform.indexing",
        "//tools/idea/.idea/libraries:NanoXML",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-impl:intellij.platform.core.impl[module]",
        "//tools/idea/platform/indexing-api:intellij.platform.indexing[module]",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl[module]",
        "//tools/idea/.idea/libraries:NanoXML",
    ],
)
