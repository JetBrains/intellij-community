load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.jvm.analysis",
    srcs = ["src"],
    iml_files = ["intellij.jvm.analysis.iml"],
    test_runtime_deps = ["//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/uast/uast-common:intellij.platform.uast",
        "//tools/idea/java/java-psi-api:intellij.java.psi",
        "//tools/idea/platform/analysis-api:intellij.platform.analysis",
        "//tools/idea:intellij.java.impl",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/uast/uast-common:intellij.platform.uast[module]",
        "//tools/idea/java/java-psi-api:intellij.java.psi[module]",
        "//tools/idea/platform/analysis-api:intellij.platform.analysis[module]",
        "//tools/idea:intellij.java.impl[module]",
    ],
)
