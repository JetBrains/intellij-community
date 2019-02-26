load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.jvm.analysis.tests",
    srcs = ["src"],
    iml_files = ["intellij.jvm.analysis.tests.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea:intellij.platform.jps.build_and_others",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
    ],
)
