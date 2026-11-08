"""Plugins as plugin directories of an IDE context, built apart from every distribution.

A test reads the pool through the manifest that the `context_plugin_manifest` output group holds, so a change of the
plugin reaches the IDE with no distribution compose. The module index beside the manifest names the plugin directory of
each main module, so a test gets only the plugins that it asks for. IDE Starter, for `starter_test` and its UI agent,
gives the IDE each plugin directory of the runfiles through `-Dplugin.path`, and the IDE loads it in place. The Air lane
daemon installs the files into `<context>/plugins`.
"""

load(":dev_dist_plugin_descriptor.bzl", "DEV_DIST_STAMP_CLASS_CONSUMER_ATTRS", "dev_dist_stamp_class_consumer_transition")
load(":intellij_dev_dist.bzl", "DevDistPlacementInfo", "IntellijDevFragmentInfo")

ContextPluginInfo = provider(
    doc = "The files and the directory trees of the plugin directories of an IDE context.",
    fields = {
        "files": "Dictionary from a file destination relative to `<context>/plugins`, in slash form, to its `File`.",
        "trees": """Dictionary from a directory destination relative to `<context>/plugins`, in slash form, to its directory
`File`. The directory holds every entry below the destination at the same relative path.""",
        "manifest": """The manifest `File`, in destination order. A file has one `<destination>\t<path>` line. A directory
tree has one `<destination>/\t<path>/` line. The path is relative to the directory of the manifest. The manifest names
no digest, so a change of a file keeps it.""",
        "directories": "Dictionary from the main module of each plugin to its plugin directory below `<context>/plugins`.",
        "modules": """The module index `File`: one `<main module>\t<plugin directory>` line per plugin, in module order. It
is the sibling `<name>.context-plugin-modules.txt` of the manifest.""",
    },
)

_PLUGINS_PREFIX = "plugins/"

def _parents(destination):
    """The directories above `destination`, outermost first."""
    parts = destination.split("/")
    return ["/".join(parts[:index]) for index in range(1, len(parts))]

def _placed_directory(plugin):
    """The one plugin directory that `plugin` places its files and directory trees in, below `plugins/`."""
    placement = plugin[DevDistPlacementInfo]
    if placement.executables:
        fail("%s marks %s executable; a context plugin holds no executable" % (plugin.label, placement.executables), attr = "plugins")
    destinations = list(placement.files) + list(placement.trees)
    if not destinations:
        fail("%s places no file" % plugin.label, attr = "plugins")
    directories = {}
    for destination in destinations:
        if not destination.startswith(_PLUGINS_PREFIX):
            fail("'%s' is not under `plugins/`" % destination, attr = "plugins")
        relative = destination[len(_PLUGINS_PREFIX):]
        if "/" not in relative:
            fail("%s places '%s' as a whole; a context plugin places entries below its plugin directory" % (plugin.label, destination), attr = "plugins")
        directories[relative.partition("/")[0]] = True
        for parent in _parents(destination):
            if parent in placement.trees:
                fail("%s places '%s' below the directory tree '%s'" % (plugin.label, destination, parent), attr = "plugins")
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
    trees = {}
    for plugin in ctx.attr.plugins:
        placement = plugin[DevDistPlacementInfo]
        for destination in sorted(placement.files):
            relative = destination[len(_PLUGINS_PREFIX):]
            output = ctx.actions.declare_file(ctx.label.name + "/" + relative)
            ctx.actions.symlink(output = output, target_file = placement.files[destination])
            files[relative] = output
        for destination in sorted(placement.trees):
            relative = destination[len(_PLUGINS_PREFIX):]
            output = ctx.actions.declare_directory(ctx.label.name + "/" + relative)
            ctx.actions.symlink(output = output, target_file = placement.trees[destination])
            trees[relative] = output

    lines = {destination: "%s\t%s/%s\n" % (destination, ctx.label.name, destination) for destination in files}
    lines.update({destination + "/": "%s/\t%s/%s/\n" % (destination, ctx.label.name, destination) for destination in trees})
    manifest = ctx.actions.declare_file(ctx.label.name + ".context-plugins.txt")
    ctx.actions.write(manifest, "".join([lines[destination] for destination in sorted(lines)]))
    modules = ctx.actions.declare_file(ctx.label.name + ".context-plugin-modules.txt")
    ctx.actions.write(modules, "".join([
        "%s\t%s\n" % (module, directories[module])
        for module in sorted(directories)
    ]))
    outputs = files.values() + trees.values()
    return [
        DefaultInfo(files = depset(outputs), runfiles = ctx.runfiles(files = outputs + [manifest, modules])),
        ContextPluginInfo(files = files, trees = trees, manifest = manifest, directories = directories, modules = modules),
        OutputGroupInfo(context_plugin_manifest = depset([manifest]), context_plugin_modules = depset([modules])),
    ]

context_plugin_dir = rule(
    doc = """The plugin directories of an IDE context, as one output per placed file and per placed directory tree.

Each entry of `plugins` is the generated `dev_plugin` component of one plugin, and it places one plugin directory. Two
plugins never place the same directory. A component can place directory trees in its plugin directory, and no
executable. The stamp class transition gives the components the mode they require, and `stamp_class` names that mode.
Each output is a link to the packed file or directory tree, at `<name>/<destination below plugins/>`. The manifest
`<name>.context-plugins.txt` names the files and the directory trees of every plugin. It is a runfile beside them and the
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
