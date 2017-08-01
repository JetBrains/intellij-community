load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "dvcs-impl",
    # do not sort: must match IML order
    srcs = ["src"],
    iml_files = ["dvcs-impl.iml"],
    resources = ["resources"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea/platform/vcs-log/api:vcs-log-api[module]",
        "//tools/idea/platform/vcs-log/impl:vcs-log-impl[module]",
        "//tools/idea/platform/dvcs-api[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/platform/vcs-tests[module, test]",
    ],
)
