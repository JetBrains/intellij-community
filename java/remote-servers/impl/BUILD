load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "remote-servers-java-impl",
    srcs = ["src"],
    iml_files = ["remote-servers-java-impl.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
    ],
    visibility = ["//visibility:public"],
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
        "//tools/idea/java/remote-servers/api:remote-servers-java-api[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/platform/remote-servers/impl:remote-servers-impl[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/java/openapi[module]",
    ],
)
