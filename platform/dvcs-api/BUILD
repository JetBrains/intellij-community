load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.platform.vcs.dvcs",
    srcs = ["src"],
    iml_files = ["intellij.platform.vcs.dvcs.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/vcs-log/graph-api:intellij.platform.vcs.log.graph"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util-rt:intellij.platform.util.rt[module]",
        "//tools/idea/platform/vcs-log/graph-api:intellij.platform.vcs.log.graph[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/vcs-api:intellij.platform.vcs[module]",
        "//tools/idea/platform/extensions:intellij.platform.extensions[module]",
        "//tools/idea/platform/vcs-log/api:intellij.platform.vcs.log[module]",
    ],
)
