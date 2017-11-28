load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "remote-servers-java-api",
    srcs = ["src"],
    iml_files = ["remote-servers-java-api.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:execution-impl",
        "//tools/idea:external-system-impl",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/remote-servers/api:remote-servers-api"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/remote-servers/api:remote-servers-api[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
    ],
)
