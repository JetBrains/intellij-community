load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "javac2",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/java/compiler/forms-compiler[module]",
        "//tools/idea/platform/forms_rt[module]",
        "//tools/idea/.idea/libraries:asm5",
        "//tools/idea/java/compiler/instrumentation-util[module]",
    ],
)
