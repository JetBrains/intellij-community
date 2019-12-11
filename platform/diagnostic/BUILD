load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.diagnostic",
    srcs = ["src"],
    iml_files = ["intellij.platform.diagnostic.iml"],
    package_prefixes = {"src": "com.intellij.diagnostic"},
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:bouncy-castle-provider",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:jackson",
        "//tools/idea/platform/core-impl:intellij.platform.core.impl[module]",
        "//tools/idea/platform/util-ex:intellij.platform.util.ex[module]",
        "//tools/idea/platform/platform-util-io:intellij.platform.ide.util.io[module]",
        "//tools/idea/platform/util:intellij.platform.concurrency[module]",
        "//tools/idea/platform/util:intellij.platform.util.ui[module]",
    ],
)
