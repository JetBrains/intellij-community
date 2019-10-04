load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.buildScripts.tests",
    iml_files = ["intellij.platform.buildScripts.tests.iml"],
    test_resources = ["testResources"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/build-scripts:intellij.platform.buildScripts[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8[test]",
    ],
)
