load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.java.debugger.agent",
    srcs = ["src"],
    iml_files = ["intellij.java.debugger.agent.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/debugger/debugger-agent-storage:intellij.java.debugger.agent.storage[module]",
        "//tools/idea/java/debugger/debugger-agent:lib/asm-capture",
    ],
)

filegroup(
    name = "artifact_files",
    srcs = ["META-INF/MANIFEST.MF"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "lib/asm-capture",
    jars = ["lib/asm-capture.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)
