load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.java.indexing",
    srcs = ["src"],
    iml_files = ["intellij.java.indexing.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/java/java-psi-api:intellij.java.psi[module]",
        "//tools/idea/platform/indexing-api:intellij.platform.indexing[module]",
    ],
)
