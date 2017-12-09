load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "jshell-frontend",
    srcs = ["src"],
    exclude = ["src/com/intellij/execution/jshell/frontend/Main.java"],
    iml_files = ["jshell-frontend.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools/idea/java/execution/jshell-protocol[module]"],
)
