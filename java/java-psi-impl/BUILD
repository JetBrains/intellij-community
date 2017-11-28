load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "java-psi-impl",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["java-psi-impl.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/java/java-psi-api",
        "//tools/idea/platform/core-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/java-psi-api[module]",
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea/resources-en[module]",
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:StreamEx",
    ],
)
