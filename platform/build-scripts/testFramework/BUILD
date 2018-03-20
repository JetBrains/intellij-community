load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "buildScripts-testFramework",
    iml_files = ["buildScripts-testFramework.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/json",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/plugins/ui-designer/jps-plugin:ui-designer-jps-plugin",
        "//tools/idea/plugins/groovy/jps-plugin:groovy-jps-plugin",
        "//tools/idea/plugins/groovy/rt:groovy_rt",
    ],
    test_srcs = ["src"],
    test_tags = ["manual"],  # test infrastructure; no tests
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea/platform/build-scripts:platform-build-scripts[module, test]",
    ],
)
