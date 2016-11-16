load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "idea.cucumber-test-runner",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools:idea.platform-api[module]",
        "//tools:idea.platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:cucumber-core",
        "//tools/idea/plugins/cucumber-jvm-formatter:idea.cucumber-jvm-formatter[module]",
        "//tools/idea/.idea/libraries:cucumber-java",
    ],
)
