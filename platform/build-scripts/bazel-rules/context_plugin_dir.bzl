"""Plugins as plugin directories of an IDE context, built apart from every distribution.

The platform loads each plugin directory under `<context>/plugins`. A test installs these files from its runfiles,
through the manifest that the `context_plugin_manifest` output group holds, so a change of the plugin reaches the IDE
with no distribution compose. The Air lane daemon and the UI agent of ide-starter both read this rule.
"""

load(":dev_dist_plugin_descriptor.bzl", "DEV_DIST_PRODUCT_INFO_ATTR", "dev_dist_product_info_transition")
load(":intellij_dev_dist.bzl", "DevDistPlacementInfo")

ContextPluginInfo = provider(
    doc = "The files of the plugin directories of an IDE context.",
    fields = {
        "files": "Dictionary from a destination relative to `<context>/plugins`, in slash form, to its `File`.",
        "manifest": """The manifest `File`: one `<destination>\\t<path>` line per file, in destination order. The path is
relative to the directory of the manifest. The manifest names no digest, so a change of a file keeps it.""",
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
    for plugin in ctx.attr.plugins:
        directory = _placed_directory(plugin)
        if directory in owners:
            fail("%s and %s both place the plugin directory %s" % (owners[directory], plugin.label, directory), attr = "plugins")
        owners[directory] = plugin.label

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
    outputs = files.values()
    return [
        DefaultInfo(files = depset(outputs), runfiles = ctx.runfiles(files = outputs + [manifest])),
        ContextPluginInfo(files = files, manifest = manifest),
        OutputGroupInfo(context_plugin_manifest = depset([manifest])),
    ]

context_plugin_dir = rule(
    doc = """The plugin directories of an IDE context, as one output file per placed file.

Each entry of `plugins` is the generated `dev_plugin` component of one plugin, and it places one plugin directory. Two
plugins never place the same directory. The components are platform-neutral, so one target serves every guest. The
product transition gives the components the product configuration they require, and `product_info` names that product.
Each output is a link to the packed file, at `<name>/<destination below plugins/>`. The manifest
`<name>.context-plugins.txt` names the files of every plugin. It is a runfile beside them and the
`context_plugin_manifest` output group.""",
    implementation = _context_plugin_dir_impl,
    attrs = dict(
        DEV_DIST_PRODUCT_INFO_ATTR,
        plugins = attr.label_list(
            doc = "The `dev_plugin` component of each plugin.",
            mandatory = True,
            allow_empty = False,
            providers = [DevDistPlacementInfo],
            cfg = dev_dist_product_info_transition,
        ),
    ),
)
