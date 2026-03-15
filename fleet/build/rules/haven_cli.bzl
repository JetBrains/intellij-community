load("@rules_java//java/common:java_common.bzl", "java_common")

HAVEN_CLI_ATTR = {
    "_haven_cli_launcher": attr.label(
        default = "@rules_jvm//:rules/impl/MemoryLauncher.java",
        allow_single_file = True,
    ),
    "_haven_cli": attr.label(
        default = "//fleet/build/cli:haven_deploy.jar",
        allow_single_file = True,
    ),
    "_tool_java_runtime": attr.label(
        default = Label("@bazel_tools//tools/jdk:current_java_runtime"),
        cfg = "exec",
    ),
}

def run_haven_cli(
        ctx,
        mnemonic,
        arguments,
        inputs = [],
        outputs = [],
        tools = [],
        progress_message = ""):
    java_runtime = ctx.attr._tool_java_runtime[java_common.JavaRuntimeInfo]
    ctx.actions.run(
        mnemonic = mnemonic,
        inputs = inputs,
        outputs = outputs,
        tools = [ctx.file._haven_cli_launcher, ctx.file._haven_cli, java_runtime.files] + tools,
        executable = java_runtime.java_executable_exec_path,
        arguments = [
            ctx.file._haven_cli_launcher.path,
            ctx.file._haven_cli.path,
        ] + arguments,
        progress_message = progress_message,
    )
