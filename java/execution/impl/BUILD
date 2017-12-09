load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "execution-impl",
    srcs = ["src"],
    iml_files = ["execution-impl.iml"],
    tags = ["managed"],
    test_runtime_deps = ["//tools/idea/platform/external-system-impl"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/java/execution/openapi:execution-openapi",
        "//tools/idea/platform/testRunner",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:precompiled_jshell-frontend",
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
        "//tools/idea/java/openapi[module]",
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea/java/java-runtime[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools/idea/platform/testRunner[module]",
        "//tools/idea/java/compiler/openapi:compiler-openapi[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/java/java-indexing-api[module]",
        "//tools/idea/platform/smRunner[module]",
        "//tools/idea/java/execution/jshell-protocol[module]",
        "//tools/idea/.idea/libraries:Coverage",
    ],
)
