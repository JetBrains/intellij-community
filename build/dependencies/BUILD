load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.tools.dependencies",
    iml_files = ["intellij.tools.dependencies.iml"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "prebuilts",
    srcs = glob(["*.gradle"]) + [
        "gradle.properties",
        "gradlew",
        "gradle/wrapper/gradle-wrapper.jar",
        "gradle/wrapper/gradle-wrapper.properties",
    ],
    visibility = ["//visibility:public"],
)
