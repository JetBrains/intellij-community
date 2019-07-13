load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.core.ui",
    srcs = ["src"],
    iml_files = ["intellij.platform.core.ui.iml"],
    package_prefixes = {"src": "com.intellij"},
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/util:intellij.platform.util.ui[module]",
    ],
)
