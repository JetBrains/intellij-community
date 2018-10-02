load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.idea.community.main.tests",
    iml_files = ["intellij.idea.community.main.tests.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/idea/platform/platform-main:intellij.platform.main",
        "//tools/idea/plugins/cvs/cvs-plugin:intellij.vcs.cvs",
        "//tools/idea/plugins/github:intellij.vcs.github",
        "//tools/idea/platform/remote-servers/impl:intellij.platform.remoteServers.impl",
        "//tools/idea/colorSchemes:intellij.platform.colorSchemes",
        "//tools/idea/plugins/yaml:intellij.yaml",
        "//tools/idea/plugins/groovy/structuralsearch-groovy:intellij.groovy.structuralSearch",
        "//tools/idea/plugins/git4idea/remote-servers-git-java:intellij.java.remoteServers.git",
        "//tools/idea/plugins/terminal:intellij.terminal",
        "//tools/idea/jps/javac-ref-scanner-8:intellij.java.jps.javacRefScanner8",
        "//tools/idea/plugins/stats-collector:intellij.statsCollector",
        "//tools/idea/plugins/stream-debugger:intellij.java.debugger.streams",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:intellij.idea.community.main[module, test]",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
        "//tools/adt/idea/designer:intellij.android.designer.tests[module, test]",
    ],
)
