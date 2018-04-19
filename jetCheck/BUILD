load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.tools.jetCheck",
    srcs = ["src"],
    iml_files = ["intellij.tools.jetCheck.iml"],
    test_srcs = ["test"],
    test_tags = ["manual"],  # b/77882590
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/idea/platform/util:intellij.platform.util"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/annotations:intellij.platform.annotations.java5[module]",
    ],
)
