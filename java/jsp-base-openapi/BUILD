load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "jsp-base-openapi",
    srcs = ["src"],
    iml_files = ["jsp-base-openapi.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/xml/openapi:xml-openapi"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/platform/lang-api[module]",
    ],
)
