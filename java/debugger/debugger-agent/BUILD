load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java.debugger.agent",
    srcs = ["src"],
    iml_files = ["intellij.java.debugger.agent.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/debugger/debugger-agent-storage:intellij.java.debugger.agent.storage[module]",
        "//tools/idea/java/debugger/debugger-agent/lib:asm-capture",
    ],
)

filegroup(
    name = "artifact_files",
    srcs = ["META-INF/MANIFEST.MF"],
    visibility = ["//visibility:public"],
)
