load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.json.tests",
    iml_files = ["intellij.json.tests.iml"],
    test_resources = ["testData"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/json:intellij.json[module, test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
        "//tools/idea/spellchecker:intellij.spellchecker[module, test]",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions[module, test]",
    ],
)
