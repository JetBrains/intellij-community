load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "platform-build-scripts-tests",
    iml_files = ["platform-build-scripts-tests.iml"],
    tags = ["managed"],
    test_resources = ["testResources"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/json",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/java/java-runtime",
        "//tools/idea/plugins/ui-designer/jps-plugin:ui-designer-jps-plugin",
        "//tools/idea/plugins/groovy/jps-plugin:groovy-jps-plugin",
        "//tools/idea/plugins/groovy/rt:groovy_rt",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/build-scripts:platform-build-scripts[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
    ],
)
