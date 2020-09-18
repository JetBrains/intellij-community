load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.buildScripts.testFramework",
    iml_files = ["intellij.platform.buildScripts.testFramework.iml"],
    test_srcs = ["src"],
    test_tags = ["manual"],  # test infrastructure; no tests
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/build-scripts:intellij.platform.buildScripts[module, test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
    ],
)
