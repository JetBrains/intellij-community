load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "jps-builders-6",
    srcs = ["src"],
    exclude = [
        "src/org/jetbrains/jps/javac/OptimizedFileManager.java",
        "src/org/jetbrains/jps/javac/OptimizedFileManager17.java",
    ],
    iml_files = ["jps-builders-6.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/idea/jps/lib:optimizedFileManager"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/util-rt[module]",
        "//tools/idea/.idea/libraries:Netty",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:protobuf",
    ],
)
