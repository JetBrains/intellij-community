load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "dvcs-api",
    srcs = ["src"],
    iml_files = ["dvcs-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/vcs-log/graph-api:vcs-log-graph-api"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util-rt[module]",
        "//tools/idea/platform/vcs-log/graph-api:vcs-log-graph-api[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/platform/extensions[module]",
        "//tools/idea/platform/vcs-log/api:vcs-log-api[module]",
    ],
)
