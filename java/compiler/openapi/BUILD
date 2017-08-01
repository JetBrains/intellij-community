load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "compiler-openapi",
    srcs = ["src"],
    iml_files = ["compiler-openapi.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools/idea/java/openapi[module]"],
)
