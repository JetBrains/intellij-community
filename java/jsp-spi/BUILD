load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "jsp-spi",
    srcs = ["src"],
    iml_files = ["jsp-spi.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/java/jsp-openapi",
        "//tools/idea/java/openapi",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/jsp-openapi[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea/java/jsp-base-openapi[module]",
    ],
)
