load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.externalSystem.impl",
    srcs = ["src"],
    iml_files = ["intellij.platform.externalSystem.impl.iml"],
    resources = ["resources"],
    test_resources = ["testResources"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/smRunner:intellij.platform.smRunner"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/idea/platform/external-system-api:intellij.platform.externalSystem[module]",
        "//tools/idea/platform/projectModel-impl:intellij.platform.projectModel.impl[module]",
        "//tools/idea/java/compiler/openapi:intellij.java.compiler[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/java/execution/openapi:intellij.java.execution[module]",
        "//tools/idea/platform/vcs-api:intellij.platform.vcs[module]",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner[module]",
        "//tools/idea/platform/smRunner:intellij.platform.smRunner[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea:intellij.java.impl[module]",
        "//tools/idea/java/openapi:intellij.java[module]",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl[module]",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions[module, test]",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl[module]",
        "//tools/idea:intellij.java.compiler.tests[module, test]",
        "//tools/idea/.idea/libraries:de.ruedigermoeller_fst",
        "//tools/idea/.idea/libraries:Objenesis",
        "//tools/idea/.idea/libraries:jackson",
    ],
)
