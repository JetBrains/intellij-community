load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "external-system-api",
    srcs = ["src"],
    iml_files = ["external-system-api.iml"],
    resources = ["resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/annotations",
        "//tools/idea/platform/external-system-rt",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/java/compiler/openapi:compiler-openapi[module]",
        "//tools/idea/platform/external-system-rt[module]",
    ],
)
