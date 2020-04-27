load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.indexing.impl",
    srcs = ["src"],
    iml_files = ["intellij.platform.indexing.impl.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = ["//tools/idea/platform/indexing-api:intellij.platform.indexing"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-impl:intellij.platform.core.impl[module]",
        "//tools/idea/platform/indexing-api:intellij.platform.indexing[module]",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl[module]",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:StreamEx",
    ],
)
