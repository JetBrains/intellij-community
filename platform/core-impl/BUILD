load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.platform.core.impl",
    srcs = ["src"],
    iml_files = ["intellij.platform.core.impl.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/core-api:intellij.platform.core"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools/idea/.idea/libraries:Guava",
    ],
)
