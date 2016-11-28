load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "jps-plugin-system",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jps/model-impl:jps-model-impl[module]",
        "//tools/idea/platform/util[module]",
    ],
)
