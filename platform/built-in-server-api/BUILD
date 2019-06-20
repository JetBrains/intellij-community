load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.builtInServer",
    srcs = ["src"],
    iml_files = ["intellij.platform.builtInServer.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/.idea/libraries:netty-codec-http",
        "//tools/idea/platform/util-ex:intellij.platform.util.ex[module]",
    ],
)
