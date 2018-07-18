load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java.compiler.antTasks",
    srcs = ["src"],
    iml_files = ["intellij.java.compiler.antTasks.iml"],
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Ant",
        "//tools/idea/java/compiler/forms-compiler:intellij.java.guiForms.compiler[module]",
        "//tools/idea/platform/forms_rt:intellij.java.guiForms.rt[module]",
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/java/compiler/instrumentation-util:intellij.java.compiler.instrumentationUtil[module]",
    ],
)
