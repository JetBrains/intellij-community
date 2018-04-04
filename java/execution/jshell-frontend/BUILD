load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.java.jshell.frontend",
    srcs = ["src"],
    exclude = ["src/com/intellij/execution/jshell/frontend/Main.java"],
    iml_files = ["intellij.java.jshell.frontend.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools/idea/java/execution/jshell-protocol:intellij.java.jshell.protocol[module]"],
)
