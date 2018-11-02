load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "antUiTest",
    srcs = ["src"],
    iml_files = ["antUiTest.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
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
        "//tools/idea/plugins/ant:intellij.ant[module]",
        "//tools/adt/idea/uitest-framework:intellij.android.guiTestFramework[module, test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/idea/.idea/libraries:truth[test]",
    ],
)
