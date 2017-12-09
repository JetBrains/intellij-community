load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "external-system-impl",
    srcs = ["src"],
    iml_files = ["external-system-impl.iml"],
    resources = ["resources"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/platform/external-system-api[module]",
        "//tools/idea/platform/projectModel-impl[module]",
        "//tools/idea/java/compiler/openapi:compiler-openapi[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/platform/testRunner[module]",
        "//tools/idea/platform/smRunner[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea/platform/configuration-store-impl[module]",
        "//tools/idea/platform/testFramework/extensions:testExtensions[module, test]",
        "//tools/idea/java/execution/impl:execution-impl[module]",
        "//tools/idea:compiler-tests[module, test]",
    ],
)
