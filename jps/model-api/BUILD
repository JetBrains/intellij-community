load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "jps-model-api",
    srcs = ["src"],
    iml_files = ["jps-model-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/util-rt[module]",
    ],
)
