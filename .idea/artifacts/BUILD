load("//tools/base/bazel:bazel.bzl", "iml_artifact")

# TODO: generate iml_artifacts automatically
iml_artifact(
    name = "debugger-agent",
    dirs = {"META-INF": ["//tools/idea/java/debugger/debugger-agent:artifact_files"]},
    modules = ["//tools/idea/java/debugger/debugger-agent:intellij.java.debugger.agent"],
    visibility = ["//visibility:public"],
)

iml_artifact(
    name = "debugger-agent-storage",
    modules = ["//tools/idea/java/debugger/debugger-agent-storage:intellij.java.debugger.agent.storage"],
    visibility = ["//visibility:public"],
)

iml_artifact(
    name = "maven-event-listener",
    modules = ["//tools/idea/plugins/maven/maven-event-listener:intellij.maven.server.eventListener"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "xmls",
    srcs = glob(["*.xml"]),
    visibility = ["//visibility:public"],
)
