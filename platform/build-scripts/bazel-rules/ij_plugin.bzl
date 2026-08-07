load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")

# This is the very first draft, many things are missing (todo):
# * add `version` and `since-build`/`until-build` attribute in plugin.xml
# * inline descriptors of content modules in plugin.xml
# * provide an option to skip optional content modules if JARs aren't specified for them
def _ij_plugin_impl(ctx):
    dir_name = ctx.attr.name
    output_dir = ctx.actions.declare_directory(dir_name)
    content_yaml_file = ctx.actions.declare_file("plugin-content.yaml")

    plugin_descriptor_module_info = ctx.attr.descriptor_module[_KtJvmInfo]
    plugin_descriptor_jar = plugin_descriptor_module_info.all_output_jars[0]
    inputs = [plugin_descriptor_jar]
    args = ctx.actions.args()
    args.add(output_dir.path)
    args.add("--plugin_content_yaml")
    args.add(content_yaml_file.path)
    args.add("--descriptor_module")
    args.add(plugin_descriptor_module_info.module_name + ":" + plugin_descriptor_jar.path)
    for content_module in ctx.attr.content_modules:
        content_module_info = content_module[_KtJvmInfo]
        content_module_jar = content_module_info.all_output_jars[0]
        args.add("--content_module")
        args.add(content_module_info.module_name + ":" + content_module_jar.path)
        inputs.append(content_module_jar)

    ctx.actions.run(
        inputs = inputs,
        outputs = [output_dir, content_yaml_file],
        arguments = [args],
        executable = ctx.executable._packager,
        mnemonic = "IjPluginPackaging",
    )
    return [
        DefaultInfo(files = depset([output_dir])),
    ]

ij_plugin = rule(
    doc = """\
Builds a directory containing distribution of a plugin for IntelliJ-based IDEs.
This is an experimental rule, its API will change, please do not migrate the plugins to it yet.
""",
    attrs = {
        "_packager": attr.label(
            default = Label("//platform/build-scripts/bazel-rules/ij-plugin-packager:ij-plugin-packager"),
            executable = True,
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
)
