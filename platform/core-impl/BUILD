load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.core.impl",
    srcs = ["src"],
    iml_files = ["intellij.platform.core.impl.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/core-api:intellij.platform.core"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/platform/util:intellij.platform.util.ui[module]",
        "//tools/idea/platform/core-ui:intellij.platform.core.ui[module]",
    ],
)
