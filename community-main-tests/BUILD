load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.idea.community.main.tests",
    iml_files = ["intellij.idea.community.main.tests.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/java/remote-servers/impl:intellij.java.remoteServers.impl",
        "//tools/idea:intellij.java.ui.tests",
        "//tools/idea/java/plugin:intellij.java.plugin",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//tools/idea/plugins/ui-designer-core:intellij.uiDesigner",
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
        "//tools/idea/platform/platform-main:intellij.platform.main",
        "//tools/idea/plugins/github:intellij.vcs.github",
        "//tools/idea/colorSchemes:intellij.platform.colorSchemes",
        "//tools/idea/plugins/groovy/structuralsearch-groovy:intellij.groovy.structuralSearch",
        "//tools/idea/plugins/git4idea/remote-servers-git-java:intellij.java.remoteServers.git",
        "//tools/idea/jps/javac-ref-scanner-8:intellij.java.jps.javacRefScanner8",
        "//tools/idea/plugins/stream-debugger:intellij.java.debugger.streams",
        "//tools/idea/plugins/editorconfig:intellij.editorconfig",
        "//tools/idea/plugins/changeReminder:intellij.vcs.changeReminder",
        "//tools/idea/plugins/sh:intellij.sh",
        "//tools/idea/plugins/gradle-maven:intellij.gradle.java.maven",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:intellij.idea.community.main[module, test]",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
        "//tools/adt/idea/designer:intellij.android.designer.tests[module, test]",
    ],
)
