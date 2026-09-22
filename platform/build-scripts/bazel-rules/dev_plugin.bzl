"""Packs one simple plugin of a dev distribution directly, with no plan file and no Kotlin preparation.

A simple plugin is a plugin whose every asset is a jar or a plain copy. Each jar merges module outputs, library jars and,
for the jar of the main module, the patched descriptor. A plain copy places one file, or every file of a directory, at a
destination as it is. The plugin's own `BUILD.bazel` states the jars on `dev_dist_plugin(jars = ...)` and the copies on
`dev_dist_plugin(files = ...)`. This file packs the jars with the same packer `content_module_jar` uses, and it names the
copied files to the collector, which hashes them; the composer copies their bytes once, into the distribution. A plugin
with any other asset keeps the plan driven chain of `dev_plugin_remainder.bzl`.

Two rules, because of the product configuration. The consumer reaches `_dev_plugin` in the product's configuration, so
the descriptor it packs is stamped for that product. The module jars and the copied files must not follow: they are the
same files for every product, so `_dev_plugin_inputs` resets the product flag and hands them over through a provider.
That is the reset `dev_plugin_remainder.bzl` applies to its compiled inputs.

The component is platform-neutral. The collector writes a manifest with no os and no arch, so one component serves every
target platform of the product.
"""

load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@rules_java//java:defs.bzl", "JavaInfo")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load(":content_module_jar.bzl", "ContentModuleJarInfo", "declare_spans", "library_entries", "merge_order_jars", "module_output_jar", "pack_jar")
load(":dev_dist_content.bzl", "DevDistContentInfo")
load(":dev_dist_plugin_descriptor.bzl", "DevDistPluginDescriptorInfo", "DevDistProductInfo", "dev_dist_neutral_product_transition")
load(":dev_plugin_source_tree.bzl", "source_tree_entries", "source_tree_prefix")
load(":intellij_dev_dist.bzl", "IntellijDevFragmentInfo")

DevPluginInputsInfo = provider(
    doc = "The compiled inputs of one simple plugin, resolved in the neutral product configuration.",
    fields = {
        "module_jars": "dict of JPS module name to its output jar `File`.",
        "libraries": "dict of library token to `struct(label, jars)`. The token is the label string the plugin's `BUILD.bazel` writes.",
        "content_jars": "dict of JPS module name to `struct(jar, metadata)`: the jar a `content_module_jar` target packed.",
        "files": "dict of label token to the tuple of regular `File`s the label produces. The token is the label string `files` names.",
        "content": "`DevDistContentInfo`: every raw module jar and library the plugin merges, the members of a reused content module jar included.",
    },
)

def _dev_plugin_inputs_impl(ctx):
    module_jars = {}
    for target, name in ctx.attr.modules.items():
        jar = module_output_jar(target)
        if jar == None:
            fail("%s produced no output jar for module '%s'" % (target.label, name), attr = "modules")
        if name in module_jars:
            fail("module '%s' is named twice" % name, attr = "modules")
        module_jars[name] = jar

    libraries = {}
    for target, token in ctx.attr.libraries.items():
        if token in libraries:
            fail("library token '%s' is named twice" % token, attr = "libraries")
        if JavaInfo in target:
            libraries[token] = library_entries(ctx, [target], attr_name = "libraries")[0]
            continue

        # A jar file label: one archive the layout takes as it is.
        files = target[DefaultInfo].files.to_list()
        if len(files) != 1 or not files[0].basename.endswith(".jar"):
            fail("%s is neither a library container nor one jar file" % target.label, attr = "libraries")
        libraries[token] = struct(label = str(target.label), jars = tuple(files))

    # The raw content, for the fragment that lays the plugin out without packing it: the plugin's own module jars and
    # libraries, plus what each reused content module jar merged. Its recipe travels with it, see `ContentModuleJarInfo`.
    content_module_jars = list(module_jars.values())
    content_library_jars = list(libraries.values())
    content_jars = {}
    for target in ctx.attr.content_module_jars:
        info = target[ContentModuleJarInfo]
        jar = info.jar
        metadata = info.metadata
        if type(jar) != "File" or type(metadata) != "File":
            fail("%s packs no jar" % target.label, attr = "content_module_jars")
        if info.module_name in content_jars:
            fail("content module '%s' is packed twice" % info.module_name, attr = "content_module_jars")
        content_jars[info.module_name] = struct(jar = jar, metadata = metadata)
        content_module_jars.extend(info.member_jars)
        content_library_jars.extend(info.library_jars)

    files = {}
    for target, token in ctx.attr.file_targets.items():
        if token in files:
            fail("file token '%s' is named twice" % token, attr = "file_targets")
        listed = target[DefaultInfo].files.to_list()
        for file in listed:
            if file.is_directory:
                fail("%s produces directory %s, and a copy takes regular files only" % (target.label, file.path), attr = "file_targets")
        files[token] = tuple(listed)

    return [
        DefaultInfo(files = depset()),
        DevPluginInputsInfo(
            module_jars = module_jars,
            libraries = libraries,
            content_jars = content_jars,
            files = files,
            content = DevDistContentInfo(module_jars = depset(content_module_jars), library_jars = depset(content_library_jars)),
        ),
    ]

_dev_plugin_inputs = rule(
    doc = "The compiled inputs of one simple plugin, in the neutral product configuration.",
    implementation = _dev_plugin_inputs_impl,
    cfg = dev_dist_neutral_product_transition,
    attrs = {
        "modules": attr.label_keyed_string_dict(
            doc = "Every module a jar merges, valued by its JPS module name.",
            providers = [_KtJvmInfo],
        ),
        "libraries": attr.label_keyed_string_dict(
            doc = "Every library container or jar file a jar merges, valued by the token `jars` names it with.",
            allow_files = [".jar"],
        ),
        "content_module_jars": attr.label_list(
            doc = "The `content_module_jar` targets of the content modules no `jars` entry merges.",
            providers = [ContentModuleJarInfo],
        ),
        "file_targets": attr.label_keyed_string_dict(
            doc = "Every source file or filegroup `files` copies, valued by the token `files` names it with.",
            allow_files = True,
        ),
        "_allowlist_function_transition": attr.label(default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist")),
    },
)

def is_library_token(token):
    """A label token names a library container or a jar file. `isLabelToken` of the generator spells the same rule.

    A container token expands to every runtime jar of the library, in order. A jar file token, which ends in `.jar`, is
    one archive.
    """
    return "//" in token

def dev_dist_plugin_directory(main_module, directory_name = ""):
    """The plugin's directory in the distribution - `("intellij.json", "")` gives `"plugins/json"`.

    The derived name is `convertModuleNameToFileName` of `PluginLayout.kt`: the main module with the `intellij.` prefix
    removed and every dot replaced by a dash. A layout that states `directory_name` takes it instead. Both plugin tiers
    derive their directory here, so a plugin is placed the same way whichever tier packs it.
    """
    return "plugins/" + (directory_name if directory_name else main_module.removeprefix("intellij.").replace(".", "-"))

def _check_destination(destination, attr = "jars", jar = True):
    if jar and not destination.endswith(".jar"):
        fail("'%s' is not a jar name" % destination, attr = attr)
    if destination.startswith("/"):
        fail("'%s' is absolute; state the path relative to the plugin directory" % destination, attr = attr)
    for element in destination.split("/"):
        if element == "" or element == "." or element == "..":
            fail("'%s' holds an empty or relative path element" % destination, attr = attr)

def _parents(destination):
    parts = destination.split("/")
    return ["/".join(parts[:size]) for size in range(1, len(parts))]

# Every destination of the plugin, so a jar and a copy never meet. `owned` maps a file destination to the words that say
# where it comes from; `directories` holds every directory a destination implies, and `copied` every directory a `files`
# entry copies as a whole.
def _new_destinations():
    return struct(owned = {}, directories = {}, copied = {})

def _claim_destination(destinations, destination, owner, attr, directory = None):
    """Owns `destination`. `directory` is the copied directory an entry belongs to; any other copied directory is a conflict."""
    if destination in destinations.owned:
        existing = destinations.owned[destination]
        if existing == owner:
            fail("'%s' is %s twice" % (destination, owner), attr = attr)
        fail("'%s' is both %s and %s" % (destination, existing, owner), attr = attr)
    if destination in destinations.directories or destination in destinations.copied:
        fail("'%s' is a directory of another destination" % destination, attr = attr)
    for parent in _parents(destination):
        if parent in destinations.owned:
            fail("'%s' is below '%s', which is %s" % (destination, parent, destinations.owned[parent]), attr = attr)
        if parent in destinations.copied and parent != directory:
            fail("'%s' is below '%s', which `file_prefixes` copies as a whole" % (destination, parent), attr = attr)
        destinations.directories[parent] = True
    destinations.owned[destination] = owner

def _claim_copied_directory(destinations, destination):
    if destination in destinations.owned:
        fail("'%s' is both %s and copied as a whole" % (destination, destinations.owned[destination]), attr = "file_prefixes")
    if destination in destinations.directories:
        fail("'%s' is a directory of another destination" % destination, attr = "file_prefixes")
    for parent in _parents(destination):
        if parent in destinations.owned:
            fail("'%s' is below '%s', which is %s" % (destination, parent, destinations.owned[parent]), attr = "file_prefixes")
        if parent in destinations.copied:
            fail("'%s' is below '%s', which `file_prefixes` also copies as a whole" % (destination, parent), attr = "file_prefixes")
    destinations.copied[destination] = True

_STATED_IN_JARS = "stated in `jars`"
_REUSED = "reused from a content module jar"
_COPIED = "copied by `files`"

def _copies(ctx, inputs, destinations):
    """The copied files as `struct(destination, file, executable)`, sorted by destination.

    A destination in `file_prefixes` is a directory copy: every file of the label below the prefix lands at
    `<destination>/<entry>`. Any other destination copies the one file its label produces.
    """
    for destination in ctx.attr.file_prefixes:
        if destination not in ctx.attr.files:
            fail("file_prefixes names '%s', which `files` does not copy" % destination, attr = "file_prefixes")
    for destination in ctx.attr.executable_files:
        if destination not in ctx.attr.files:
            fail("executable_files names '%s', which `files` does not copy" % destination, attr = "executable_files")
        if destination in ctx.attr.file_prefixes:
            fail("executable_files names '%s', which is a directory copy; the mode of a copied directory is not stated" % destination, attr = "executable_files")

    for destination in ctx.attr.file_prefixes:
        _check_destination(destination, attr = "file_prefixes", jar = False)
        _claim_copied_directory(destinations, destination)

    copies = []
    for destination in sorted(ctx.attr.files):
        token = ctx.attr.files[destination]
        sources = inputs.files.get(token)
        if sources == None:
            fail("'%s' names %s, which `file_targets` does not declare" % (destination, token), attr = "files")
        prefix = ctx.attr.file_prefixes.get(destination)
        if prefix == None:
            _check_destination(destination, attr = "files", jar = False)
            if len(sources) != 1:
                fail("'%s' copies one file, and %s produces %d files; state a directory copy in `file_prefixes`" % (destination, token, len(sources)), attr = "files")
            _claim_destination(destinations, destination, _COPIED, "files")
            copies.append(struct(destination = destination, file = sources[0], executable = destination in ctx.attr.executable_files))
            continue
        entries = source_tree_entries(sources, source_tree_prefix(prefix, destination), destination, token)
        for entry in sorted(entries):
            path = destination + "/" + entry
            _claim_destination(destinations, path, _COPIED, "files", directory = destination)
            copies.append(struct(destination = path, file = entries[entry], executable = False))
    return copies

def _dev_plugin_impl(ctx):
    inputs = ctx.attr.inputs[DevPluginInputsInfo]
    descriptor_info = ctx.attr.descriptor[DevDistPluginDescriptorInfo]
    product = ctx.attr._product_info[DevDistProductInfo]
    main_module = ctx.attr.main_module
    if not product.platform_prefix:
        fail("dev_plugin requires a product configuration: %s states no platform prefix, so no product asked for %s" % (
            ctx.attr._product_info.label,
            ctx.label,
        ))
    if descriptor_info.plugin_main_module != main_module:
        fail("%s describes '%s', not '%s'" % (ctx.attr.descriptor.label, descriptor_info.plugin_main_module, main_module), attr = "descriptor")
    if not ctx.attr.jars:
        fail("%s states no jar" % ctx.label, attr = "jars")
    plugin_directory = ctx.attr.plugin_directory
    if not plugin_directory.startswith("plugins/") or plugin_directory.count("/") != 1 or plugin_directory.endswith("/"):
        fail("'%s' is not `plugins/<directory>`" % plugin_directory, attr = "plugin_directory")

    packed = []
    spans = []
    module_owner = {}
    destinations = _new_destinations()
    for destination, tokens in ctx.attr.jars.items():
        _check_destination(destination)
        _claim_destination(destinations, destination, _STATED_IN_JARS, "jars")
        if not tokens:
            fail("'%s' merges nothing" % destination, attr = "jars")

        module_jars = []
        module_names = []
        library_entries = []
        for token in tokens:
            if is_library_token(token):
                entry = inputs.libraries.get(token)
                if entry == None:
                    fail("'%s' names library %s, which `libraries` does not declare" % (destination, token), attr = "jars")
                library_entries.append(entry)
            else:
                jar = inputs.module_jars.get(token)
                if jar == None:
                    fail("'%s' names module '%s', which `modules` does not declare" % (destination, token), attr = "jars")
                if token in module_names:
                    fail("'%s' names module '%s' twice" % (destination, token), attr = "jars")

                # A module may be merged into several jars. `tomee.agent.rt` is in the Tomcat main jar and in a specifics
                # jar, for example.
                module_owner.setdefault(token, destination)
                module_names.append(token)
                module_jars.append(jar)

        library_jars = merge_order_jars(library_entries)
        has_main = main_module in module_names
        output = ctx.actions.declare_file(ctx.label.name + "/" + destination)
        metadata = ctx.actions.declare_file(ctx.label.name + ".metadata/" + destination + ".json")
        jar_spans = declare_spans(ctx, ctx.label.name + "/" + destination[:-len(".jar")])
        pack_jar(
            ctx,
            output = output,
            spans = jar_spans,
            module_jars = module_jars,
            library_jars = library_jars,
            merged_module_names = module_names,
            mnemonic = "PackDevPluginJar",
            progress_message = "Packing %s of %%{label}" % destination,
            extra_flags = ["merge-entities=true"],
            descriptor = descriptor_info.descriptor if has_main else None,
            descriptor_module = main_module if has_main else None,
            metadata = metadata,
        )
        packed.append(struct(destination = destination, jar = output, metadata = metadata))
        if jar_spans != None:
            spans.append(jar_spans)

    if main_module not in module_owner:
        fail("the main module '%s' is merged into no jar" % main_module, attr = "jars")

    # A content module no jar merges ships the jar its own `content_module_jar` target packed.
    for name in ctx.attr.module_jar_paths:
        if name not in inputs.content_jars:
            fail("module_jar_paths names '%s', which `content_module_jars` does not pack" % name, attr = "module_jar_paths")
    for name in inputs.content_jars:
        if name in module_owner:
            continue
        destination = ctx.attr.module_jar_paths.get(name, "lib/modules/%s.jar" % name)
        _check_destination(destination)
        _claim_destination(destinations, destination, _REUSED, "jars")
        content = inputs.content_jars[name]
        packed.append(struct(destination = destination, jar = content.jar, metadata = content.metadata))

    # The collector reads the jars in spec order and writes the classpath record in that order. `classpath_jars` states
    # the order when the plan's order is not the default one.
    if ctx.attr.classpath_jars:
        by_destination = {entry.destination: entry for entry in packed}
        if sorted(ctx.attr.classpath_jars) != sorted(by_destination.keys()):
            fail("classpath_jars must name every jar once; the jars are %s" % sorted(by_destination.keys()), attr = "classpath_jars")
        packed = [by_destination[destination] for destination in ctx.attr.classpath_jars]

    copies = _copies(ctx, inputs, destinations)
    copied_files = [copy.file for copy in copies]

    classpath_descriptor = descriptor_info.classpath_descriptor
    spec = ctx.actions.declare_file(ctx.label.name + ".packed.json")
    spec_content = {
        "version": 1,
        "pluginDirectory": plugin_directory,
        "descriptor": classpath_descriptor.path,
        "jars": [
            {"destination": entry.destination, "source": entry.jar.path, "metadata": entry.metadata.path}
            for entry in packed
        ],
    }

    # Absent, and not empty, for a plugin without a copy: the spec of every jar-only plugin stays byte-identical.
    if copies:
        spec_content["files"] = [
            {"destination": copy.destination, "source": copy.file.path, "executable": copy.executable}
            for copy in copies
        ]
    ctx.actions.write(spec, json.encode(spec_content) + "\n")

    manifest = ctx.actions.declare_file(ctx.label.name + ".component.json")
    classpath = ctx.actions.declare_file(ctx.label.name + ".plugin-classpath-part")
    outputs = [manifest, classpath]
    arguments = ctx.actions.args()
    arguments.add(spec, format = "--plugin-component=%s")
    arguments.add(manifest, format = "--component-manifest=%s")
    arguments.add(classpath, format = "--plugin-classpath-part=%s")
    arguments.add(main_module, format = "--kind=%s")
    arguments.add(product.platform_prefix, format = "--platform-prefix=%s")
    arguments.add("--platform-neutral")
    if ctx.attr._trace_spans[BuildSettingInfo].value:
        trace = ctx.actions.declare_file(ctx.label.name + ".component.spans.json")
        spans.append(trace)
        outputs.append(trace)
        arguments.add(trace, format = "--trace-file=%s")
    ctx.actions.run(
        mnemonic = "CollectDevPluginComponent",
        executable = ctx.executable._collector,
        inputs = depset([spec, classpath_descriptor] + [entry.jar for entry in packed] + [entry.metadata for entry in packed] + copied_files),
        outputs = outputs,
        arguments = [arguments],
        execution_requirements = {"block-network": "1", "no-remote-cache": "1", "no-remote-exec": "1"},
        progress_message = "Collecting plugin component metadata %{label}",
    )

    payload = depset([entry.jar for entry in packed] + copied_files)
    return [
        DefaultInfo(files = depset([manifest, classpath]), runfiles = ctx.runfiles(transitive_files = payload)),
        # The raw content, published beside the packed component: `dev_dist_plugin_content` unions it per product for
        # the fragment that lays the plugin out without packing it.
        inputs.content,
        IntellijDevFragmentInfo(
            name = main_module,
            home = None,
            payload = payload,
            manifest = manifest,
            plugin_classpath_part = classpath,
            plugin_classpath_prefix = None,
            inputs_manifest = None,
            unused_inputs = None,
        ),
        OutputGroupInfo(
            dev_dist_plugin_outputs = depset([manifest, classpath], transitive = [payload]),
            dev_dist_plugin_classpath = depset([classpath]),
            file_metadata = depset([entry.metadata for entry in packed]),
            trace_spans = depset(spans),
        ),
    ]

_dev_plugin = rule(
    doc = """One simple plugin, packed and collected as a platform-neutral component.

Not transitioned. The consumer reaches it in the product configuration, and `_product_info` reads the product there.""",
    implementation = _dev_plugin_impl,
    attrs = {
        "inputs": attr.label(mandatory = True, providers = [DevPluginInputsInfo]),
        "descriptor": attr.label(
            doc = "The plugin's `dev_dist_plugin_descriptor` target, stamped for the product this component is built for.",
            mandatory = True,
            providers = [DevDistPluginDescriptorInfo],
        ),
        "main_module": attr.string(mandatory = True, doc = "The main JPS module. It names the component."),
        "plugin_directory": attr.string(mandatory = True, doc = "`plugins/<directory>`, the plugin's directory in the distribution."),
        "jars": attr.string_list_dict(
            doc = """The jars, keyed by destination relative to the plugin directory and valued by source tokens in merge order.

A token is a JPS module name, or a label token, which holds `//`, of a library container or a jar file. The jar of the
main module receives the patched descriptor.""",
            mandatory = True,
        ),
        "module_jar_paths": attr.string_dict(
            doc = "The destination of a reused content module jar that is not `lib/modules/<module>.jar`, keyed by module name.",
        ),
        "classpath_jars": attr.string_list(
            doc = """The classpath order of every jar, by destination. Empty takes the default order: the `jars` keys, then the
reused content module jars in `content_module_jars` order.""",
        ),
        "files": attr.string_dict(
            doc = """The plain copies, keyed by destination relative to the plugin directory and valued by the label token of
the source. A destination `file_prefixes` names copies every file of the label below the prefix; any other destination
copies the one file the label produces. A copied file is not on the plugin classpath.""",
        ),
        "file_prefixes": attr.string_dict(
            doc = """The repository-relative prefix of each directory copy in `files`, keyed by destination. A file of the label
at `<prefix>/<entry>` lands at `<destination>/<entry>`.""",
        ),
        "executable_files": attr.string_list(
            doc = "The single-file destinations of `files` the distribution marks executable, as `withResource*` does with mode 493.",
        ),
        "_collector": attr.label(default = "//build/content-module-packer/dev-dist-collector", executable = True, cfg = "exec"),
        "_packer": attr.label(default = "//build/content-module-packer", executable = True, cfg = "exec"),
        "_trace_spans": attr.label(default = "//platform/build-scripts/bazel-rules:trace_spans", providers = [BuildSettingInfo]),
        "_product_info": attr.label(
            doc = "The product, read through the flag the consumer's transition sets. The default states no product, and the rule fails on it.",
            default = Label("//build:dev_dist_product_info"),
            providers = [DevDistProductInfo],
        ),
    },
)

def dev_plugin(
        name,
        main_module,
        descriptor,
        plugin_directory,
        modules,
        libraries = [],
        content_module_jars = [],
        jars = {},
        module_jar_paths = {},
        classpath_jars = [],
        files = {},
        file_prefixes = {},
        executable_files = [],
        tags = [],
        visibility = ["//visibility:public"],
        **kwargs):
    """Declares one simple plugin component and its compiled inputs, both `manual`.

    Args:
        name: the component target. `<name>_inputs` holds the compiled inputs.
        main_module: the main JPS module.
        descriptor: the plugin's `dev_dist_plugin_descriptor` target.
        plugin_directory: `plugins/<directory>`.
        modules: dict of module target to JPS module name, every module a jar merges.
        libraries: the library container and jar file labels `jars` names, as the same strings.
        content_module_jars: the `content_module_jar` targets of the content modules no jar merges.
        jars: destination to source tokens, see `_dev_plugin`.
        module_jar_paths: the destination of a reused content module jar when it is not `lib/modules/<module>.jar`.
        classpath_jars: the classpath order of every jar, when the default order is not the plan's order. The default
            order is the `jars` keys, then the reused content module jars in `content_module_jars` order.
        files: destination to the label of a plain copy, see `_dev_plugin`. The label is a source file, a filegroup or
            another target that produces regular files.
        file_prefixes: destination to repository-relative prefix, for every entry of `files` that copies a directory.
        executable_files: the single-file destinations of `files` that are executable.
        tags: extra tags. `manual` is added.
        visibility: the component's visibility, public by default because the product's dist is in another package.
            The inputs target is private.
        **kwargs: `testonly` and other common attributes of the component.
    """
    inputs = name + "_inputs"
    inputs_attrs = {"testonly": kwargs["testonly"]} if "testonly" in kwargs else {}
    _dev_plugin_inputs(
        name = inputs,
        modules = modules,
        libraries = {library: library for library in libraries},
        content_module_jars = content_module_jars,
        file_targets = {label: label for label in files.values()},
        tags = ["manual"],
        visibility = ["//visibility:private"],
        **inputs_attrs
    )
    _dev_plugin(
        name = name,
        visibility = visibility,
        inputs = ":" + inputs,
        descriptor = descriptor,
        main_module = main_module,
        plugin_directory = plugin_directory,
        jars = jars,
        module_jar_paths = module_jar_paths,
        classpath_jars = classpath_jars,
        files = files,
        file_prefixes = file_prefixes,
        executable_files = executable_files,
        tags = tags + ["manual"],
        **kwargs
    )
