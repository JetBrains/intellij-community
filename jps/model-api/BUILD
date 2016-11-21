load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "jps-model-api",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/util-rt[module]",
    ],
)
