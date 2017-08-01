load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "forms-compiler",
    srcs = ["src"],
    iml_files = ["forms-compiler.iml"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],  # This is flaky on macOS and we don't use it anyway.
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/java/compiler/instrumentation-util"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/platform/forms_rt[module]",
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools/idea/java/compiler/instrumentation-util[module]",
    ],
)
