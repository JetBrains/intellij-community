load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "jsp-openapi",
    srcs = ["src"],
    iml_files = ["jsp-openapi.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/java/jsp-base-openapi"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/java/jsp-base-openapi[module]",
    ],
)
