load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@rules_java//java/common:java_common.bzl", "java_common")
load("@rules_jvm//:jvm.bzl", _jvm_platform_transition = "jvm_platform_transition", _scrubbed_host_platform_transition = "scrubbed_host_platform_transition")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")

# This is the very first draft, many things are missing (todo):
# * add `version` and `since-build`/`until-build` attribute in plugin.xml
# * inline descriptors of content modules in plugin.xml
# * provide an option to skip optional content modules if JARs aren't specified for them
def _ij_plugin_impl(ctx):
    plugin_descriptor_module_info = ctx.attr.descriptor_module[_KtJvmInfo]
    dir_name = _module_name_to_directory_name(plugin_descriptor_module_info.module_name)
    output_dir = ctx.actions.declare_directory(dir_name)
    content_yaml_file = ctx.actions.declare_file("plugin-content.yaml")

    plugin_descriptor_jar = plugin_descriptor_module_info.all_output_jars[0]
    inputs = [plugin_descriptor_jar]

    # Files are added to `args` as artifacts and not as strings, so their paths are rewritten when path mapping is enabled.
    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add_all([output_dir], expand_directories = False)
    args.add("--plugin_content_yaml")
    args.add(content_yaml_file)
    args.add_all(
        "--descriptor_module",
        [plugin_descriptor_jar],
        format_each = plugin_descriptor_module_info.module_name + ":%s",
    )
    for content_module in ctx.attr.content_modules:
        content_module_info = content_module[_KtJvmInfo]
        content_module_jar = content_module_info.all_output_jars[0]
        args.add_all(
            "--content_module",
            [content_module_jar],
            format_each = content_module_info.module_name + ":%s",
        )
        inputs.append(content_module_jar)

    java_runtime = ctx.attr._tool_java_runtime[java_common.JavaRuntimeInfo]
    ctx.actions.run(
        # the JBR files are intentionally not declared as inputs: their exec paths and digests are platform-specific,
        # so they would make the action key differ across Linux/macOS/Windows (see `bazel_scrubbing.cfg`).
        # `JvmCompile` in `@rules_jvm` relies on the same JBR-is-available-in-the-exec-root assumption.
        inputs = depset(inputs),
        outputs = [output_dir, content_yaml_file],
        tools = [ctx.file._packager_launcher, ctx.file._packager],
        executable = java_runtime.java_executable_exec_path,
        execution_requirements = {
            "supports-workers": "1",
            "supports-multiplex-workers": "1",
            "supports-worker-cancellation": "1",
            "supports-path-mapping": "1",
            "supports-multiplex-sandboxing": "1",
        },
        arguments = ctx.attr._packager_jvm_flags[BuildSettingInfo].value + [
            ctx.file._packager_launcher.path,
            ctx.file._packager.path,
            args,
        ],
        mnemonic = "IjPluginPackaging",
        progress_message = "Packaging plugin %{label}",
    )
    return [
        DefaultInfo(files = depset([output_dir])),
    ]

def _module_name_to_directory_name(module_name):
    if module_name.startswith("intellij."):
        module_name = module_name[len("intellij."):]
    return module_name.replace(".", "-")

ij_plugin = rule(
    doc = """\
Builds a directory containing distribution of a plugin for IntelliJ-based IDEs.
This is an experimental rule, its API will change, please do not migrate the plugins to it yet.
""",
    attrs = {
        "_packager": attr.label(
            default = Label("//platform/build-scripts/bazel-rules/ij-plugin-packager:ij-plugin-packager_deploy.jar"),
            allow_single_file = True,
            # the deploy jar is platform-independent, so build it under a host-independent output directory to keep the
            # `IjPluginPackaging` action key (and thus its remote cache entries) the same on Linux/macOS/Windows
            cfg = _scrubbed_host_platform_transition,
        ),
        "_packager_jvm_flags": attr.label(
            default = Label("//platform/build-scripts/bazel-rules/ij-plugin-packager:ij-plugin-packager-jvm_flags"),
        ),
        "_packager_launcher": attr.label(
            default = Label("@rules_jvm//:rules/impl/MemoryLauncher.java"),
            allow_single_file = True,
        ),
        "_tool_java_runtime": attr.label(
            default = Label("@bazel_tools//tools/jdk:current_java_runtime"),
            cfg = "exec",
        ),
        "descriptor_module": attr.label(
            doc = """The target that contains the plugin descriptor (META-INF/plugin.xml)""",
            providers = [_KtJvmInfo],
            mandatory = True,
        ),
        "content_modules": attr.label_list(
            doc = """The list of targets that produce plugin content modules registered in the plugin""",
            providers = [_KtJvmInfo],
        ),
    },
    implementation = _ij_plugin_impl,
    cfg = _jvm_platform_transition,
)
