load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.java.rt",
    srcs = ["src"],
    iml_files = ["intellij.java.rt.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/.idea/libraries:Ant",
    ],
)
