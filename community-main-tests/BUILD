load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "community-main-tests",
    iml_files = ["community-main-tests.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:community-main[module, test]",
        "//tools/adt/idea/android:android-tests[module, test]",
        "//tools/adt/idea/designer:designer-tests[module, test]",
    ],
)
