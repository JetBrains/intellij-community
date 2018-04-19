load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java.rt",
    srcs = ["src"],
    iml_files = ["intellij.java.rt.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/.idea/libraries:Ant",
    ],
)
