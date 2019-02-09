load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java.indexing.impl",
    srcs = ["src"],
    iml_files = ["intellij.java.indexing.impl.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/java/java-psi-api:intellij.java.psi",
        "//tools/idea/java/java-psi-impl:intellij.java.psi.impl",
        "//tools/idea/platform/indexing-api:intellij.platform.indexing",
        "//tools/idea/platform/indexing-impl:intellij.platform.indexing.impl",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel",
        "//tools/idea/java/java-indexing-api:intellij.java.indexing",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/java-psi-api:intellij.java.psi[module]",
        "//tools/idea/java/java-psi-impl:intellij.java.psi.impl[module]",
        "//tools/idea/platform/indexing-api:intellij.platform.indexing[module]",
        "//tools/idea/platform/indexing-impl:intellij.platform.indexing.impl[module]",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel[module]",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl[module]",
        "//tools/idea/java/java-indexing-api:intellij.java.indexing[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
    ],
)
