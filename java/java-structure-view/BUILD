load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.java.structureView",
    srcs = ["src"],
    iml_files = ["intellij.java.structureView.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/structure-view-impl:intellij.platform.structureView.impl"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/structure-view-impl:intellij.platform.structureView.impl[module]",
        "//tools/idea/java/java-psi-impl:intellij.java.psi.impl[module]",
    ],
)
