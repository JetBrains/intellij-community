load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.jps.build.javac.rt",
    srcs = ["src"],
    exclude = [
        "src/org/jetbrains/jps/javac/OptimizedFileManager.java",
        "src/org/jetbrains/jps/javac/OptimizedFileManager17.java",
    ],
    iml_files = ["intellij.platform.jps.build.javac.rt.iml"],
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/idea/jps/lib:optimizedFileManager"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations:intellij.platform.annotations.java5[module]",
        "//tools/idea/platform/util-rt:intellij.platform.util.rt[module]",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/.idea/libraries:netty-codec-http",
    ],
)
