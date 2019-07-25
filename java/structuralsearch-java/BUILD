load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java.structuralSearch",
    srcs = ["src"],
    iml_files = ["intellij.java.structuralSearch.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/structuralsearch:intellij.platform.structuralSearch[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/java/openapi:intellij.java[module]",
        "//tools/idea:intellij.java.impl[module]",
        "//tools/idea/platform/duplicates-analysis:intellij.platform.duplicates.analysis[module]",
    ],
)
