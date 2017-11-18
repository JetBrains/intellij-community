load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "remote-servers-java-api",
    srcs = ["src"],
    iml_files = ["remote-servers-java-api.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:external-system-impl",
        "//tools/idea:execution-impl",
        "//tools/idea:testExtensions",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/remote-servers/api:remote-servers-api"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:lvcs-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/remote-servers/api:remote-servers-api[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
    ],
)
