load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java.guiForms.compiler",
    srcs = ["src"],
    iml_files = ["intellij.java.guiForms.compiler.iml"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],  # This is flaky on macOS and we don't use it anyway.
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/java/compiler/instrumentation-util:intellij.java.compiler.instrumentationUtil"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/platform/forms_rt:intellij.java.guiForms.rt[module]",
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/.idea/libraries:JUnit3[test]",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools/idea/java/compiler/instrumentation-util:intellij.java.compiler.instrumentationUtil[module]",
    ],
)
