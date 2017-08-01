load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "cucumber-test-runner",
    srcs = ["src"],
    iml_files = ["cucumber-test-runner.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:cucumber-core",
        "//tools/idea/plugins/cucumber-jvm-formatter[module]",
    ],
)
