load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.idea.community.main.tests",
    iml_files = ["intellij.idea.community.main.tests.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/vendor/intellij/cidr:intellij.c.doxygen",
        "//tools/idea/jps/javac-ref-scanner-8:intellij.java.jps.javacRefScanner8",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:intellij.idea.community.main[module, test]",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
        "//tools/adt/idea/designer:intellij.android.designer.tests[module, test]",
    ],
)
