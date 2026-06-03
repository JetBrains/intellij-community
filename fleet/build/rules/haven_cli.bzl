load("@rules_java//java/common:java_common.bzl", "java_common")

HAVEN_CLI_ATTR = {
    "_haven_cli_launcher": attr.label(
        default = "@rules_jvm//:rules/impl/MemoryLauncher.java",
        allow_single_file = True,
    ),
    "_haven_cli": attr.label(
        default = "//fleet/build/cli-worker:haven-cli-worker_deploy.jar",
        allow_single_file = True,
        cfg = "exec",
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
        mnemonic = "HavenCli",  # ensure all haven-cli workers are shared regardless of the mnemonic of the action
        inputs = inputs,
        outputs = outputs,
        tools = [ctx.file._haven_cli_launcher, ctx.file._haven_cli, java_runtime.files] + tools,
        executable = java_runtime.java_executable_exec_path,
        execution_requirements = {
            "supports-workers": "1",
            "supports-multiplex-workers": "1",
            "supports-worker-cancellation": "1",
            "supports-path-mapping": "1",
        },
        arguments = [
            ctx.file._haven_cli_launcher.path,
            ctx.file._haven_cli.path,
        ] + arguments,
        progress_message = "%s: %s" % (mnemonic, progress_message),
    )
