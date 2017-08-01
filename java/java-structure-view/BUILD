load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "java-structure-view",
    srcs = ["src"],
    iml_files = ["java-structure-view.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/structure-view-impl"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/structure-view-impl[module]",
        "//tools/idea/java/java-psi-impl[module]",
    ],
)
