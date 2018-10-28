load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java.psi.impl",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["intellij.java.psi.impl.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/java/java-psi-api:intellij.java.psi",
        "//tools/idea/platform/core-impl:intellij.platform.core.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/java-psi-api:intellij.java.psi[module]",
        "//tools/idea/platform/core-impl:intellij.platform.core.impl[module]",
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:StreamEx",
    ],
)
