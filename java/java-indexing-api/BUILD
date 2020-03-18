load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java.indexing",
    srcs = ["src"],
    iml_files = ["intellij.java.indexing.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/java/java-psi-api:intellij.java.psi[module]",
        "//tools/idea/platform/indexing-api:intellij.platform.indexing[module]",
        "//tools/idea/platform/util:intellij.platform.concurrency[module]",
    ],
)
