load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.core.impl",
    srcs = ["src"],
    iml_files = ["intellij.platform.core.impl.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/core-api:intellij.platform.core",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/platform/util:intellij.platform.util.ui[module]",
        "//tools/idea/platform/core-ui:intellij.platform.core.ui[module]",
        "//tools/idea/.idea/libraries:fastutil-min",
        "//tools/idea/.idea/libraries:Trove4j",
    ],
)
