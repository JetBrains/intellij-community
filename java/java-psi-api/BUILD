load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.java.psi",
    srcs = ["src"],
    iml_files = ["intellij.java.psi.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/core-api:intellij.platform.core"],
    deps = ["//tools/idea/platform/core-api:intellij.platform.core[module]"],
)
