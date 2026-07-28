load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")

# This is the very first draft, many things are missing (todo):
# * rewrite implementation from shell to Kotlin;
# * exclude files not used in production (e.g., icon-robots.txt);
# * investigate differences in __index__ files;
# * add `version` and `since-build`/`until-build` attribute in plugin.xml
# * inline descriptors of content modules in plugin.xml
# * put content modules with loading=embedded to lib/, not lib/modules/;
def _ij_plugin_impl(ctx):
  dir_name = ctx.attr.name
  output_dir = ctx.actions.declare_directory(dir_name)

  inputs = []
  inputs.append(ctx.attr.descriptor_module[_KtJvmInfo].all_output_jars[0])
  plugin_descriptor_module_info = ctx.attr.descriptor_module[_KtJvmInfo]
  args = ctx.actions.args()
  args.add(output_dir.path)
  args.add(plugin_descriptor_module_info.all_output_jars[0])
  for content_module in ctx.attr.content_modules:
    content_module_info = content_module[_KtJvmInfo]
    args.add(content_module_info.all_output_jars[0])
    args.add(content_module_info.module_name + ".jar")
    inputs.append(content_module_info.all_output_jars[0])

  ctx.actions.run_shell(
    inputs = inputs,
    outputs = [output_dir],
    arguments = [args],
    command = """
set -eu
out="$1/lib"
shift
mkdir -p "$out"
cp "$1" "$out"
shift

while [ "$#" -gt 0 ]; do
  input="$1"
  output_name="$2"
  shift 2
  mkdir -p "$out/modules"
  cp "$input" "$out/modules/$output_name"
done
""",
    mnemonic = "BuildIjPlugin",
  )
  return [
    DefaultInfo(files = depset([output_dir]))
  ]


ij_plugin = rule(
    doc = """\
Builds a directory containing distribution of a plugin for IntelliJ-based IDEs.
This is an experimental rule, its API will change, please do not migrate the plugins to it yet.
""",
  attrs = {
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
