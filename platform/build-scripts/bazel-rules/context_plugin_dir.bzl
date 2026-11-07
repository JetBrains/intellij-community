"""Plugins as plugin directories of an IDE context, built apart from every distribution.

The platform loads each plugin directory under `<context>/plugins`. A test installs these files from its runfiles,
through the manifest that the `context_plugin_manifest` output group holds, so a change of the plugin reaches the IDE
with no distribution compose. The module index beside the manifest names the plugin directory of each main module, so a
test installs only the plugins that it asks for. The Air lane daemon, the UI agent of ide-starter and `starter_test`
read this rule.
"""

load(":dev_dist_plugin_descriptor.bzl", "DEV_DIST_STAMP_CLASS_CONSUMER_ATTRS", "dev_dist_stamp_class_consumer_transition")
load(":intellij_dev_dist.bzl", "DevDistPlacementInfo", "IntellijDevFragmentInfo")

ContextPluginInfo = provider(
    doc = "The files of the plugin directories of an IDE context.",
    fields = {
        "files": "Dictionary from a destination relative to `<context>/plugins`, in slash form, to its `File`.",
        "manifest": """The manifest `File`: one `<destination>\\t<path>` line per file, in destination order. The path is
relative to the directory of the manifest. The manifest names no digest, so a change of a file keeps it.""",
        "directories": "Dictionary from the main module of each plugin to its plugin directory below `<context>/plugins`.",
        "modules": """The module index `File`: one `<main module>\\t<plugin directory>` line per plugin, in module order. It
is the sibling `<name>.context-plugin-modules.txt` of the manifest.""",
    },
)

_PLUGINS_PREFIX = "plugins/"

def _placed_directory(plugin):
    """The one plugin directory that `plugin` places its files in, below `plugins/`."""
    placement = plugin[DevDistPlacementInfo]
    if placement.trees:
        fail("%s places directory trees %s; a context plugin copies files only" % (plugin.label, sorted(placement.trees)), attr = "plugins")
    if placement.executables:
        fail("%s marks %s executable; a context plugin holds no executable" % (plugin.label, placement.executables), attr = "plugins")
    if not placement.files:
        fail("%s places no file" % plugin.label, attr = "plugins")
    directories = {}
    for destination in placement.files:
        if not destination.startswith(_PLUGINS_PREFIX):
            fail("'%s' is not under `plugins/`" % destination, attr = "plugins")
        directories[destination[len(_PLUGINS_PREFIX):].partition("/")[0]] = True
    if len(directories) != 1:
        fail("%s places files in %d plugin directories %s; a context plugin is one directory" % (
            plugin.label,
            len(directories),
            sorted(directories),
        ), attr = "plugins")
    return directories.keys()[0]

def _context_plugin_dir_impl(ctx):
    owners = {}
    directories = {}
    for plugin in ctx.attr.plugins:
        directory = _placed_directory(plugin)
        if directory in owners:
            fail("%s and %s both place the plugin directory %s" % (owners[directory], plugin.label, directory), attr = "plugins")
        owners[directory] = plugin.label
        module = plugin[IntellijDevFragmentInfo].name
        if module in directories:
            fail("Two components have the main module %s" % module, attr = "plugins")
        directories[module] = directory

    files = {}
    for plugin in ctx.attr.plugins:
        placed = plugin[DevDistPlacementInfo].files
        for destination in sorted(placed):
            relative = destination[len(_PLUGINS_PREFIX):]
            output = ctx.actions.declare_file(ctx.label.name + "/" + relative)
            ctx.actions.symlink(output = output, target_file = placed[destination])
            files[relative] = output

    manifest = ctx.actions.declare_file(ctx.label.name + ".context-plugins.txt")
    ctx.actions.write(manifest, "".join([
        "%s\t%s/%s\n" % (destination, ctx.label.name, destination)
        for destination in sorted(files)
    ]))
    modules = ctx.actions.declare_file(ctx.label.name + ".context-plugin-modules.txt")
    ctx.actions.write(modules, "".join([
        "%s\t%s\n" % (module, directories[module])
        for module in sorted(directories)
    ]))
    outputs = files.values()
    return [
        DefaultInfo(files = depset(outputs), runfiles = ctx.runfiles(files = outputs + [manifest, modules])),
        ContextPluginInfo(files = files, manifest = manifest, directories = directories, modules = modules),
        OutputGroupInfo(context_plugin_manifest = depset([manifest]), context_plugin_modules = depset([modules])),
    ]

context_plugin_dir = rule(
    doc = """The plugin directories of an IDE context, as one output file per placed file.

Each entry of `plugins` is the generated `dev_plugin` component of one plugin, and it places one plugin directory. Two
plugins never place the same directory. The components are platform-neutral, so one target serves every guest. The
stamp class transition gives the components the mode they require, and `stamp_class` names that mode.
Each output is a link to the packed file, at `<name>/<destination below plugins/>`. The manifest
`<name>.context-plugins.txt` names the files of every plugin. It is a runfile beside them and the
`context_plugin_manifest` output group. The module index `<name>.context-plugin-modules.txt` names the plugin directory of
each main module. It is a runfile too and the `context_plugin_modules` output group.""",
    implementation = _context_plugin_dir_impl,
    attrs = dict(
        DEV_DIST_STAMP_CLASS_CONSUMER_ATTRS,
        plugins = attr.label_list(
            doc = "The `dev_plugin` component of each plugin.",
            mandatory = True,
            allow_empty = False,
            providers = [DevDistPlacementInfo, IntellijDevFragmentInfo],
            cfg = dev_dist_stamp_class_consumer_transition,
        ),
    ),
)
