load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "debugger-openapi",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea/java/jsp-openapi[module]",
        "//tools/idea/java/java-psi-api[module]",
        "//tools/idea/resources-en[module]",
    ],
)
