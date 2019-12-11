load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.jvm.analysis.kotlin.tests",
    iml_files = ["intellij.jvm.analysis.kotlin.tests.iml"],
    test_runtime_deps = ["//tools/idea:intellij.java.ui.tests"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:KotlinPlugin",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl",
        "//tools/idea/java/plugin:intellij.java.plugin",
        "//tools/idea/java/manifest:intellij.java.manifest",
        "//tools/idea/java/remote-servers/impl:intellij.java.remoteServers.impl",
        "//tools/idea/plugins/ui-designer-core:intellij.uiDesigner",
        "//tools/idea/java/structuralsearch-java:intellij.java.structuralSearch",
        "//tools/idea/java/typeMigration:intellij.java.typeMigration",
        "//tools/idea/plugins/IntelliLang:intellij.java.langInjection",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jvm/jvm-analysis-tests-api:intellij.jvm.analysis.tests[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8[test]",
        "//tools/idea:intellij.java.tests[module, test]",
    ],
)
