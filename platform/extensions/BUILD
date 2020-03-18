load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.extensions",
    srcs = ["src"],
    iml_files = ["intellij.platform.extensions.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
    ],
)
