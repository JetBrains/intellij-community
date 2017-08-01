load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "core-impl",
    srcs = ["src"],
    iml_files = ["core-impl.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/core-api"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/.idea/libraries:picocontainer",
        "//tools/idea/.idea/libraries:Guava",
    ],
)
