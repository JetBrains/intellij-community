load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "java-indexing-impl",
    srcs = ["src"],
    iml_files = ["java-indexing-impl.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/java/java-psi-api",
        "//tools/idea/java/java-psi-impl",
        "//tools/idea/platform/indexing-api",
        "//tools/idea/platform/indexing-impl",
        "//tools/idea/platform/projectModel-api",
        "//tools/idea/java/java-indexing-api",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/java-psi-api[module]",
        "//tools/idea/java/java-psi-impl[module]",
        "//tools/idea/platform/indexing-api[module]",
        "//tools/idea/platform/indexing-impl[module]",
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/platform/projectModel-impl[module]",
        "//tools/idea/java/java-indexing-api[module]",
        "//tools/idea/.idea/libraries:Guava",
    ],
)
