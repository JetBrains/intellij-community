load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.tools.dependencies.gradle",
    iml_files = ["intellij.tools.dependencies.gradle.iml"],
    visibility = ["//visibility:public"],
)

# Used in tools/adt/idea to generate the idea gradle-api jar at build time
# rather than relying on prebuilts, as the idea build does.
exports_files(["build.gradle"])
