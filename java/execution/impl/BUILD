load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java.execution.impl",
    srcs = ["src"],
    iml_files = ["intellij.java.execution.impl.iml"],
    test_runtime_deps = ["//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/java/execution/openapi:intellij.java.execution",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:precompiled_jshell-frontend",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi:intellij.java[module]",
        "//tools/idea/java/execution/openapi:intellij.java.execution[module]",
        "//tools/idea/java/java-runtime:intellij.java.rt[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner[module]",
        "//tools/idea/java/compiler/openapi:intellij.java.compiler[module]",
        "//tools/idea:intellij.java.impl[module]",
        "//tools/idea/java/java-indexing-api:intellij.java.indexing[module]",
        "//tools/idea/platform/smRunner:intellij.platform.smRunner[module]",
        "//tools/idea/java/execution/jshell-protocol:intellij.java.jshell.protocol[module]",
        "//tools/idea/.idea/libraries:intellij-coverage",
        "//tools/idea/.idea/libraries:intellij-test-discovery",
        "//tools/idea/platform/external-system-api:intellij.platform.externalSystem[module]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:jackson",
        "//tools/idea/.idea/libraries:miglayout-swing",
    ],
)
