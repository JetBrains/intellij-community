load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "built-in-server-api",
    srcs = ["src"],
    iml_files = ["built-in-server-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/.idea/libraries:Netty",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/platform/platform-api[module]",
    ],
)
