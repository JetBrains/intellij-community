load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "execution-openapi",
    srcs = ["src"],
    iml_files = ["execution-openapi.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/xdebugger-api"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea/resources[module]",
        "//tools/idea/platform/xdebugger-api[module]",
    ],
)
