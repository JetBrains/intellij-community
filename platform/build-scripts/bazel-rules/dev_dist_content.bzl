"""The content boundary of a dev distribution, expressed as a Bazel provider.

`DevDistContentInfo` carries the module and library jars one slice of a distribution reads, and
`intellij_dev_build_inputs` turns them into manifest entries. It has two producers. `dev_dist_platform_payload` is the
content of the packed `lib/` jars of one product. `dev_dist_plugin_content` is the raw content of the bundled plugins of
one product, as the plugin components publish it.
"""

load(":content_module_jar.bzl", "ContentModuleJarInfo", "DevDistPlatformJarInfo")
load(":dev_dist_plugin_descriptor.bzl", "DEV_DIST_PRODUCT_INFO_ATTR", "dev_dist_product_info_transition")

DevDistContentInfo = provider(
    doc = "The module and library jars one slice of a dev distribution is made of.",
    fields = {
        # Bare `File`s, because the only consumer - `intellij_dev_build_inputs` - keys a module jar by
        # `str(file.owner) + ".jar"` and needs nothing else from the target that produced it.
        "module_jars": "depset of File: the jars of the modules this content declares as its own members.",
        # Not bare `File`s, unlike the module halves: a library's manifest key is the container target's label, which is
        # not derivable from the files. The producer expands the container into its jars.
        "library_jars": "depset of struct(label, jars): `label` being the container target's own label.",
    },
)

DevDistPlatformPayloadInfo = provider(
    doc = "The packed `lib/` jars of the platform of one product, and what each of them merges.",
    fields = {
        "packed_jars": "depset of File: the `lib/<module>.jar`s a `content_module_jar` target packed.",
        # Jars only in `packed_jars`, because the byte gate reads it as the set of jars to compare. The native tree of a
        # content module jar travels in the record, and the packed-jars component places it from there.
        "packed_metadata": """depset of struct(jar, metadata, relative_path, native_tree, native_metadata,
        native_lib_dir, core_classpath): the metadata and the destination of each packed jar, and the native tree of the
        payload's platform with its own metadata and the `lib/` subdirectory the tree goes to. `None` and empty for a jar
        without one. `core_classpath` is true for a jar on the core classpath, see `core_classpath_jar_names`.""",
        "packed_jar_names": """list of string: their destinations within `lib/`, sorted. The reference target packs them.

        A destination, not a file name: a platform jar can name a subdirectory of `lib/`.""",
        "core_classpath_jar_names": """list of string: the destinations of the packed jars on the core classpath, sorted.

        A packed jar is on the core classpath when it is a direct child of `lib/` and `module_system_loaded` does not
        name it. The plan generator checks this rule against the rule of the production build.""",
        "layout": """list of struct(destination, member_modules, library_jars), sorted by destination: what each packed
        jar merges, in merge order, as `ContentModuleJarInfo` and `DevDistPlatformJarInfo` state it. The runtime module
        repository reads it as the layout of the core plugin.""",
    },
)

def _dev_dist_platform_payload_impl(ctx):
    packed_labels = {target.label: True for target in ctx.attr.packed}
    unpacked = [str(target.label) for target in ctx.attr.module_system_loaded if target.label not in packed_labels]
    if unpacked:
        fail("%s: module_system_loaded names jars that packed does not name: %s" % (ctx.label, ", ".join(unpacked)), attr = "module_system_loaded")
    module_system_loaded = {target.label: True for target in ctx.attr.module_system_loaded}
    packed_jars = []
    packed_metadata = []
    packed_member_jars = []
    packed_library_jars = []
    packed_destinations = []
    layout = []
    core_classpath_jar_names = []
    native_dir_owners = {}
    for target in ctx.attr.packed:
        info = target[ContentModuleJarInfo] if ContentModuleJarInfo in target else target[DevDistPlatformJarInfo]
        packed_jars.append(info.jar)
        packed_destinations.append(struct(destination = info.relative_path, jar = info.jar))

        # Only a content module jar carries natives, and it has a tree for each platform. The payload takes the one of
        # its own platform.
        native = None
        native_lib_dir = ""
        if ContentModuleJarInfo in target and info.native_trees:
            if not ctx.attr.native_platform:
                fail("%s: %s packs native files, so the payload needs native_platform" % (ctx.label, target.label), attr = "native_platform")
            native = info.native_trees.get(ctx.attr.native_platform)
            if native == None:
                fail("%s: %s has no native tree for '%s'" % (ctx.label, target.label, ctx.attr.native_platform), attr = "native_platform")
            native_lib_dir = info.native_lib_dir
        if native != None:
            # One owner per `lib/<dir>/`, like one owner per jar destination below: two trees in one directory would
            # be two producers of whichever files they share.
            previous = native_dir_owners.get(native_lib_dir)
            if previous != None:
                fail("%s: lib/%s/ receives the native tree of both %s and %s" % (ctx.label, native_lib_dir, previous.owner, info.jar.owner))
            native_dir_owners[native_lib_dir] = info.jar

        # Only a direct child of `lib/` can be on the core classpath, and a jar the module system loads is not on it.
        core_classpath = "/" not in info.relative_path and target.label not in module_system_loaded
        if core_classpath:
            core_classpath_jar_names.append(info.relative_path)
        packed_metadata.append(struct(
            jar = info.jar,
            metadata = info.metadata,
            relative_path = info.relative_path,
            native_tree = native.tree if native else None,
            native_metadata = native.metadata if native else None,
            native_lib_dir = native_lib_dir,
            core_classpath = core_classpath,
        ))
        packed_member_jars.extend(info.member_jars)
        packed_library_jars.extend(info.library_jars)
        layout.append(struct(destination = info.relative_path, member_modules = info.member_modules, library_jars = info.library_jars))

    packed = depset(packed_jars)

    # Keyed by the destination each jar declares, not by the name of the file that holds it. A platform jar can name a
    # subdirectory of `lib/`, so two jars can share a file name and still land in two places, and one destination can be
    # claimed by two jars whose file names differ.
    owner_by_name = {}
    for entry in packed_destinations:
        previous = owner_by_name.get(entry.destination)
        if previous != None and previous != entry.jar:
            # Two producers for one `lib/` path. `mergeDevBuildComponent` would catch it during a compose, but only as a
            # colliding path; here the two owning modules can still be named.
            fail("%s: %s is packed by both %s and %s" % (ctx.label, entry.destination, previous.owner, entry.jar.owner))
        owner_by_name[entry.destination] = entry.jar

    if not owner_by_name:
        fail("%s: no module in this payload packs a `lib/` jar, which cannot be right for a platform payload" % ctx.label)

    return [
        DevDistPlatformPayloadInfo(
            packed_jars = packed,
            packed_metadata = depset(packed_metadata),
            packed_jar_names = sorted(owner_by_name.keys()),
            core_classpath_jar_names = sorted({name: True for name in core_classpath_jar_names}.keys()),
            layout = sorted(layout, key = lambda entry: entry.destination),
        ),
        # The reference target's whole declaration: it packs the handed-over jars the `JarPackager` way, so what it reads
        # is exactly what is inside them - the member module jars and the libraries merged into them. Ordinary content,
        # so it arrives through the content boundary `intellij_dev_build_inputs` reads.
        DevDistContentInfo(
            module_jars = depset(packed_member_jars),
            library_jars = depset(packed_library_jars),
        ),
    ]

dev_dist_platform_payload = rule(
    doc = """The packed `lib/` jars of the platform of one product, and what each of them merges.

    `packed` names one packing target per jar. One provider answers every consumer, so the answers cannot disagree:

    * `packed_jars` and `packed_metadata` go to `intellij_dev_packed_jars_component`, which composes them in and marks
      the jars of the core classpath;
    * `packed_jar_names` go to the reference target as the jars it packs and nothing else;
    * `layout` goes to `dev_dist_runtime_module_repository` as the layout of the core plugin;
    * `DevDistContentInfo` is what those jars merge, and is the reference target's whole declaration.
    """,
    implementation = _dev_dist_platform_payload_impl,
    attrs = {
        "packed": attr.label_list(
            doc = "The `content_module_jar` and `dev_dist_platform_jar` targets of the platform, one per `lib/` jar. " +
                  "This list is the handover set.",
            providers = [[ContentModuleJarInfo], [DevDistPlatformJarInfo]],
            mandatory = True,
        ),
        "module_system_loaded": attr.label_list(
            doc = "The `content_module_jar` targets of `packed` whose jars the module system loads. Every other " +
                  "packed jar that is a direct child of `lib/` is on the core classpath.",
            providers = [ContentModuleJarInfo],
        ),
        "native_platform": attr.string(
            doc = "The `HOST_PLATFORMS` token of the payload's native trees. Configurable: the macro passes a " +
                  "`select()` over the host when the set names no target platform. Empty when no packed jar has natives.",
        ),
    },
)

def _dev_dist_plugin_content_impl(ctx):
    module_jars = []
    library_jars = []
    for target in ctx.attr.plugins + ctx.attr.deps:
        content = target[DevDistContentInfo]
        module_jars.append(content.module_jars)
        library_jars.append(content.library_jars)
    return [
        DefaultInfo(files = depset()),
        DevDistContentInfo(
            module_jars = depset(transitive = module_jars),
            library_jars = depset(transitive = library_jars),
        ),
    ]

dev_dist_plugin_content = rule(
    doc = """The raw module and library jars of the bundled plugins of one product, as one `DevDistContentInfo`.

    The runtime module repository fragment lays every bundled plugin out without files and resolves the output and the
    libraries of each module it reaches. Those jars used to arrive as a generated name list of ~2 000 names per
    product. They are the same jars every plugin component already reads, so the components publish them and this
    rule unions them. Raw jars only: no packed plugin jar and no descriptor is in here, so a plugin source edit
    re-keys no fragment through this target.

    `plugins` are the components of the product's own bundled plugins. They need the product configuration, which
    `product_info` sets on the way down, exactly as `intellij_dev_fragments_dist` does. The module jars below them
    still come from the neutral configuration through each component's inputs target, so no module compiles twice.
    `deps` are other content targets, already configured: the content of the embedded frontend's own bundled plugins,
    collected under the frontend's product.
    """,
    implementation = _dev_dist_plugin_content_impl,
    attrs = {
        "plugins": attr.label_list(
            doc = "The plugin components this product bundles. Each one publishes `DevDistContentInfo`.",
            cfg = dev_dist_product_info_transition,
            providers = [DevDistContentInfo],
        ),
        "deps": attr.label_list(
            doc = "Other content targets, merged as they are.",
            providers = [DevDistContentInfo],
        ),
    } | DEV_DIST_PRODUCT_INFO_ATTR,
)

def _dev_dist_plugin_components_impl(ctx):
    return [DefaultInfo(files = depset(transitive = [target[OutputGroupInfo].dev_dist_plugin_outputs for target in ctx.attr.plugins]))]

dev_dist_plugin_components = rule(
    doc = """Builds plugin components under one product, every platform variant of each.

    A component is `manual`, because it builds only under a product. A distribution is `manual` too, and it reaches
    only the variant of the host platform. This target is not `manual`, so `bazel build //...` builds each listed
    component with its descriptor. `product_info` sets the product on the way down, as `dev_dist_plugin_content` does.
    """,
    implementation = _dev_dist_plugin_components_impl,
    attrs = {
        "plugins": attr.label_list(
            doc = "The plugin components to build. Each one publishes the `dev_dist_plugin_outputs` output group.",
            cfg = dev_dist_product_info_transition,
            mandatory = True,
        ),
    } | DEV_DIST_PRODUCT_INFO_ATTR,
)

def dev_dist_plugin_component_builds(components, product_info):
    """Declares one `<product>_plugin_components` target per product, so that each component builds once.

    A component serves every product that states it alike, so the first product that names a component builds it.

    Args:
        components: The component map, `{product: {tier: {main module: label or {platform: label}}}}`.
        product_info: The label pattern of a product's `dev_dist_product_info`, with `%s` for the product key.
    """
    claimed = {}
    for product, tiers in components.items():
        plugins = []
        for entries in tiers.values():
            for value in entries.values():
                for label in (value.values() if type(value) == "dict" else [value]):
                    if label not in claimed:
                        claimed[label] = True
                        plugins.append(label)
        if plugins:
            dev_dist_plugin_components(
                name = product + "_plugin_components",
                plugins = plugins,
                product_info = product_info % product,
            )
