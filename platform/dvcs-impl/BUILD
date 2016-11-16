load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "idea.dvcs-impl",
    # do not sort: must match IML order
    srcs = [
        "src",
        "resources",
    ],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools:idea.core-api[module]",
        "//tools:idea.platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools:idea.core-impl[module]",
        "//tools/idea/platform/vcs-log/api:idea.vcs-log-api[module]",
        "//tools/idea/platform/vcs-log/impl:idea.vcs-log-impl[module]",
        "//tools/idea/platform/dvcs-api:idea.dvcs-api[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/platform/vcs-tests:idea.vcs-tests[module, test]",
    ],
)
