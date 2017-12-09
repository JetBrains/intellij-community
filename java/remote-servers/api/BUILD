load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "remote-servers-java-api",
    srcs = ["src"],
    iml_files = ["remote-servers-java-api.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/remote-servers/api:remote-servers-api"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/remote-servers/api:remote-servers-api[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
    ],
)
