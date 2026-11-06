"""Writes one plugin's patched `META-INF/plugin.xml` and its classpath descriptor.

The leaf writes the descriptor that the plugin's main jar receives. A leaf that embeds no content module gets its
classpath descriptor from a separate target in the neutral product configuration. Both plugin tiers read the files
through `DevDistPluginDescriptorInfo`. The plugin descriptor writer is the one producer of the text.

Modelled on two neighbours, each for what it already settled. `ij_plugin` for the per-plugin grain and for the build
number arriving as a declared file. `content_module_jar` for the provider, for the `manual` tag and for a packer named
directly rather than pushed in through a flag.

**Remote-cacheable on purpose.** These actions read tens of small XML files, not a platform's gigabytes, so
`_LOCAL_DISK_CACHE_ONLY` of `intellij_dev_dist.bzl` does not apply here and `local` is not set. A cheap hermetic action
that both caches keep is the property ADR 0006 asks for.
"""

load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@rules_java//java:defs.bzl", "JavaInfo")
load("//build:dev_dist_product_info.bzl", "DEV_DIST_MARKETPLACE_NAMES")
load("//build:host_platforms.bzl", "HOST_PLATFORMS", "platform_parts")
load(":content_module_jar.bzl", "library_entries")
load(":dev_dist_build_date.bzl", "DEV_DIST_PINNED_BUILD_DATE_IN_SECONDS")
load(":dev_dist_tool_info.bzl", "dev_dist_tool_inputs")

DevDistPluginDescriptorInfo = provider(
    doc = """One plugin's patched descriptor, and the plugin it belongs to.

    The provider lets Bazel reject a target that produces no descriptor during analysis.""",
    fields = {
        "plugin_main_module": "The JPS module whose resources carry the descriptor.",
        "descriptor": "The patched `META-INF/plugin.xml` as a `File`.",
        "classpath_descriptor": """The patched descriptor with embedded content modules for classpath generation.

        Always in its final byte form: a classpath writer copies the bytes and applies no XML rewrite.""",
        "platforms": "The `HOST_PLATFORMS` entries this layout variant serves.",
        "mode_refusals": """The content modules each mode refuses at run time, keyed by the mode. The descriptor keeps them.

        A component reads the list of its own mode, see `dev_dist_mode_refusals`. `dev_plugin` and the chain place no jar
        whose every module is in that list, and no reused jar of such a module.""",
        "embeds_content_modules": "Whether the leaf embeds the body of every content module. Then its bytes read no mode.",
        "stamp_class_modes": """The modes whose refusal list of this leaf is not empty, sorted.

        A component of the plugin states the same list, so its stamp class matches the class of this leaf.""",
        "_declaration": "Private versioned metadata with the declared File objects and action parameters.",
        "_declaration_file": "The private metadata file. It is not a default output or an action input.",
    },
)

DevDistPluginClasspathDescriptorInfo = provider(
    doc = "The classpath descriptor written in the neutral product configuration.",
    fields = {
        "descriptor": "The classpath descriptor as a `File`.",
        "stamp_class": "The stamp class of the classpath descriptor target.",
        "_declaration": "The private versioned metadata of this target's action.",
    },
)

# The flag of the stamp class. Canonical, because a transition resolves neither an apparent repository name nor a
# relative label.
_STAMP_CLASS_FLAG = str(Label("//build:dev_dist_stamp_class"))

# The default of the stamp class flag: no mode.
_NO_STAMP_CLASS = ""

def _dev_dist_stamp_class_consumer_transition_impl(settings, attr):
    stamp_class = getattr(attr, "stamp_class", None)
    if not stamp_class:
        # The consumer states no mode, so the target keeps the configuration it is reached in.
        return {_STAMP_CLASS_FLAG: settings[_STAMP_CLASS_FLAG]}
    return {_STAMP_CLASS_FLAG: stamp_class}

# What names the mode on the way down to a plugin component. The component declares no product, so the target that
# collects components per product states the mode of that product. The component then keeps only its stamp class, see
# `dev_dist_stamp_class_transition`.
dev_dist_stamp_class_consumer_transition = transition(
    implementation = _dev_dist_stamp_class_consumer_transition_impl,
    inputs = [_STAMP_CLASS_FLAG],
    outputs = [_STAMP_CLASS_FLAG],
)

def _refusing_modes(attr):
    stated = getattr(attr, "stamp_class_modes", None)
    if stated != None:
        return stated
    return [mode for mode, modules in attr.mode_refused_content_modules.items() if modules]

def _stamp_class_modes(attr):
    # A descriptor that embeds every content module reads no mode, so the leaf and the target that packs its jar keep
    # none. A component states no `embed_content_modules`, because it places the jars of its mode.
    if getattr(attr, "embed_content_modules", False):
        return []
    return _refusing_modes(attr)

def _dev_dist_stamp_class_transition_impl(settings, attr):
    mode = settings[_STAMP_CLASS_FLAG]
    return {_STAMP_CLASS_FLAG: mode if mode in _stamp_class_modes(attr) else _NO_STAMP_CLASS}

# The incoming transition of a plugin component, of a descriptor leaf and of the target that packs the descriptor jar. It
# keeps the mode only when the plugin refuses content modules for that mode. A leaf that embeds every content module
# keeps no mode. The default is trimmed from the configuration. So the components of every product of one mode share
# one configuration, and one action packs them for all.
dev_dist_stamp_class_transition = transition(
    implementation = _dev_dist_stamp_class_transition_impl,
    inputs = [_STAMP_CLASS_FLAG],
    outputs = [_STAMP_CLASS_FLAG],
)

def _dev_dist_neutral_product_transition_impl(_settings, _attr):
    return {_STAMP_CLASS_FLAG: _NO_STAMP_CLASS}

# The reset. A compiled module jar is the same file for every product, so a rule that collects module jars under a
# stamp class resets both flags to their defaults. The defaults are trimmed from the configuration, so the jars come
# from the default configuration and no product recompiles them.
dev_dist_neutral_product_transition = transition(
    implementation = _dev_dist_neutral_product_transition_impl,
    inputs = [],
    outputs = [_STAMP_CLASS_FLAG],
)

# The attributes of a plugin component rule: the facts its incoming transition reads, and the allowlist. The descriptor
# leaf states the same facts, and the component checks both against it, see `dev_dist_check_stamp_class`.
DEV_DIST_STAMP_CLASS_ATTRS = {
    "stamp_class_modes": attr.string_list(
        doc = """The modes whose refusal list of the descriptor is not empty, sorted. Empty for most plugins.

The incoming transition keeps the mode of the consumer only when this list names it.""",
    ),
    "_allowlist_function_transition": attr.label(
        default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist"),
    ),
}

# The flag of the mode, for a rule that places the jars of its mode.
DEV_DIST_STAMP_CLASS_FLAG_ATTR = {
    "_stamp_class": attr.label(
        doc = "The mode of the stamp class, the flag `dev_dist_stamp_class`.",
        default = Label("//build:dev_dist_stamp_class"),
        providers = [BuildSettingInfo],
    ),
}

def dev_dist_mode_refusals(ctx, descriptor):
    """The content modules that the mode of this rule refuses, from the leaf `descriptor`.

    The rule declares `DEV_DIST_STAMP_CLASS_FLAG_ATTR`. A leaf that embeds every content module is configured with no
    mode, so the rule reads its own mode and not the one of the leaf.
    """
    return descriptor.mode_refusals.get(ctx.attr._stamp_class[BuildSettingInfo].value, [])

def dev_dist_check_stamp_class(ctx, descriptor):
    """Fails unless the stamp class modes of a component equal the ones of its descriptor leaf.

    A component whose facts lack a mode would build under no mode, so its descriptor would embed no refused module.

    Args:
        ctx: the rule context of the component.
        descriptor: the `DevDistPluginDescriptorInfo` of the component.
    """
    if sorted(ctx.attr.stamp_class_modes) != descriptor.stamp_class_modes:
        fail("%s states the stamp class modes %s, and its descriptor states %s" % (
            ctx.label,
            sorted(ctx.attr.stamp_class_modes),
            descriptor.stamp_class_modes,
        ), attr = "stamp_class_modes")

# The attributes a rule that collects per-product components declares, with the allowlist the transition needs.
# Public, because `dev_dist_plugin_content` of `dev_dist_content.bzl` collects plugin components the same way.
DEV_DIST_STAMP_CLASS_CONSUMER_ATTRS = {
    "stamp_class": attr.string(
        doc = """The mode of the product, such as `monolith` or `frontend`: the value of `dev_dist_stamp_class` below this target.

The generated `DEV_DIST_PRODUCT_INFO` states the mode of each product.""",
        mandatory = True,
    ),
    "_allowlist_function_transition": attr.label(
        default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist"),
    ),
}

def _dev_dist_plugin_descriptor_group_impl(ctx):
    return [
        DefaultInfo(files = depset(transitive = [
            target[DefaultInfo].files
            for target in ctx.attr.descriptors
        ])),
        OutputGroupInfo(_dev_dist_descriptor_metadata = depset(transitive = [
            target[OutputGroupInfo]._dev_dist_descriptor_metadata
            for target in ctx.attr.descriptors
        ])),
    ]

dev_dist_plugin_descriptor_group = rule(
    doc = """Produced descriptors of one product, as one target that states the mode of the product.

    A rule and not a `filegroup`, because the mode arrives through a transition and a `filegroup` states none.
    `//build:idea_dev_descriptor_leaf_build_test` builds one such group, and the produced descriptors are its
    `DefaultInfo` files. `//build:*_descriptor_build_test` is the guard that builds a group.""",
    implementation = _dev_dist_plugin_descriptor_group_impl,
    attrs = {
        "descriptors": attr.label_list(
            doc = "Every `dev_dist_plugin_descriptor` target of this product, every layout variant included.",
            cfg = dev_dist_stamp_class_consumer_transition,
            providers = [DevDistPluginDescriptorInfo],
        ),
    } | DEV_DIST_STAMP_CLASS_CONSUMER_ATTRS,
)

def _os_arch_stamps(ctx):
    """The marker rows and the version suffix this leaf stamps: the stated ones, or the ones its variant gives.

    Derived here and not in a macro, because every leaf sits in the package of its own plugin. The table the
    derivation reads is `DEV_DIST_MARKETPLACE_NAMES`, so a leaf states neither the table nor the pair.

    Args:
        ctx: the rule context.

    Returns:
        `struct(markers, version_suffix)`.
    """
    stamps = dev_dist_plugin_descriptor_os_arch_stamps(ctx.attr.variant)
    if stamps == None:
        return struct(markers = ctx.attr.markers, version_suffix = ctx.attr.version_suffix)

    # The convention has one spelling. A stated pair here would be a checked-in copy of what the variant gives, and
    # the leaf writes none, so this is a hand edit rather than a deviation.
    if ctx.attr.markers or ctx.attr.version_suffix:
        fail("variant '%s' gives the marker row and the version suffix, so %s restates them" % (
            ctx.attr.variant,
            ctx.label,
        ), attr = "markers")
    return stamps

def _descriptor_request(ctx, module_name, embed_content_modules = None, reserialize_before_content_embedding = False):
    """Collects the ordered parameters and File bindings for both descriptor producers.

    Args:
        ctx: the rule context.
        module_name: the plugin's main JPS module.
        embed_content_modules: an optional override for classpath generation.
        reserialize_before_content_embedding: whether to finish XML normalization before content descriptors are embedded.

    Returns:
        The parameters, declared inputs, source bindings, and resolved stamps.
    """
    stamp_class = ctx.attr._stamp_class[BuildSettingInfo].value
    jars = ctx.attr.jar_inputs[_DevDistDescriptorJarsInfo] if ctx.attr.jar_inputs != None else _NO_DESCRIPTOR_JARS
    source = ctx.file.descriptor
    source_jar = jars.descriptor_jar
    if (source == None) == (source_jar == None):
        fail("exactly one of descriptor and descriptor_jar must be set")
    if source_jar != None and not ctx.attr.descriptor_entry:
        fail("descriptor_entry is required with descriptor_jar", attr = "descriptor_entry")

    if embed_content_modules == None:
        embed_content_modules = ctx.attr.embed_content_modules

    parameters = [
        ("--main-module", module_name, "formatted"),
        ("--build-number-file", ctx.file._build_number_file, "formatted"),
        ("--compatible-build-range", _compatible_build_range(ctx), "literal"),
    ]

    # A retained `product-descriptor` takes the pinned build date as its release date, and the writer derives the rest
    # from the build number. No leaf reads an application info, so one leaf serves every product.
    if ctx.attr.retain_product_descriptor:
        parameters.append(("--build-date-seconds", DEV_DIST_PINNED_BUILD_DATE_IN_SECONDS, "literal"))
    parameters.extend([
        ("--retain-product-descriptor", str(ctx.attr.retain_product_descriptor).lower(), "literal"),
        ("--embed-content-modules", str(embed_content_modules).lower(), "literal"),
        ("--reserialize-before-content-embedding", str(reserialize_before_content_embedding).lower(), "literal"),
    ])

    # The distribution places no jar of a module the product's mode refuses, so a descriptor that embeds no body
    # still embeds the body of such a module. The run time reads it there and excludes the module.
    if not embed_content_modules:
        parameters.append(("--embed-content-module", ctx.attr.mode_refused_content_modules.get(stamp_class, []), "repeated"))
    if source != None:
        parameters.insert(1, ("--source", source, "formatted"))
    else:
        parameters.insert(1, ("--source-in-jar", ctx.attr.descriptor_entry + "=" + source_jar.path, "literal"))
    stamps = _os_arch_stamps(ctx)
    if stamps.version_suffix:
        parameters.append(("--version-suffix", stamps.version_suffix, "formatted"))
    parameters.extend([
        ("--marker", stamps.markers, "repeated"),
        ("--refused-content-module", ctx.attr.refused_content_modules, "repeated"),
        ("--separate-jar", ctx.attr.separate_jar, "repeated"),
    ])

    source_file = source if source != None else source_jar
    source_label = ctx.attr.descriptor.label if source != None else jars.descriptor_jar_label
    inputs = [source_file, ctx.file._build_number_file]
    sources = [
        _descriptor_source_binding("descriptor" if source != None else "descriptor_jar", source_label, ctx.attr.descriptor_entry if source_jar != None else None, [source_file]),
        _descriptor_source_binding("build_number", ctx.attr._build_number_file.label, None, [ctx.file._build_number_file]),
    ]

    # One answer per load path. The writer seeds the files first and puts a jar entry in only when the path is
    # absent. So a load path two declarations answer is refused here, where every declaration is visible.
    answered_by = {}
    for label, load_path in ctx.attr.descriptors.items():
        files = label.files.to_list()
        if len(files) != 1:
            fail("%s declares %d files, and a descriptor must name exactly one" % (label.label, len(files)), attr = "descriptors")
        if load_path in answered_by:
            fail("%s and %s both answer the load path '%s'" % (answered_by[load_path], label.label, load_path), attr = "descriptors")
        answered_by[load_path] = label.label
        parameters.append(("--plugin-descriptor", load_path + "=" + files[0].path, "literal"))
        sources.append(_descriptor_source_binding("descriptors", label.label, load_path, files))
        inputs.append(files[0])
    for jar in jars.descriptor_jars:
        if jar.load_path in answered_by:
            fail("%s and %s both answer the load path '%s'" % (answered_by[jar.load_path], jar.label, jar.load_path), attr = "descriptor_jars")
        answered_by[jar.load_path] = jar.label
        parameters.append(("--plugin-descriptor-in-jar", jar.load_path + "=" + jar.files[0].path, "literal"))
        sources.append(_descriptor_source_binding("descriptor_jars", jar.label, jar.load_path, jar.files))
        inputs.append(jar.files[0])
    for library in jars.library_descriptors:
        for load_path in library.load_paths:
            if load_path in answered_by:
                fail("%s and %s both answer the load path '%s'" % (answered_by[load_path], library.label, load_path), attr = "library_descriptors")
            answered_by[load_path] = library.label
            sources.append(_descriptor_source_binding("library_descriptors", library.label, load_path, library.jars))

            # Every jar of the container, in the container's own order. Which one holds the entry is a fact inside a zip,
            # so no rule can know it. The executor takes the first jar that answers, the way the assembly's
            # `findFileInModuleLibraryDependencies` asks each declared library jar in turn.
            for jar in library.jars:
                parameters.append(("--plugin-descriptor-in-jar", load_path + "=" + jar.path, "literal"))
        inputs.extend(library.jars)
    return struct(parameters = tuple(parameters), inputs = tuple(inputs), sources = tuple(sources), stamps = stamps)

def _compatible_build_range(ctx):
    """The since and until range of the leaf: the stated one, `EXACT` for an exact version, else `NEWER_WITH_SAME_BASELINE`.

    A dev distribution bundles every plugin, and the run time checks no range of a bundled plugin. So the EAP flag of
    the product selects no range here, and one leaf serves an EAP and a release product.
    """
    if ctx.attr.compatible_build_range:
        return ctx.attr.compatible_build_range
    return "EXACT" if ctx.attr.exact_version else "NEWER_WITH_SAME_BASELINE"

def _descriptor_source_binding(attribute, label, load_path, files):
    return struct(attribute = attribute, label = label, load_path = load_path, files = tuple(files))

_DevDistDescriptorJarsInfo = provider(
    doc = "The jar inputs of one descriptor leaf, resolved in the neutral product configuration.",
    fields = {
        "descriptor_jar": "The `File` that holds the plugin descriptor, or None when `descriptor` is a source file.",
        "descriptor_jar_label": "Its `Label`, or None.",
        "descriptor_jars": "tuple of struct(label, load_path, files): one content descriptor jar per load path.",
        "library_descriptors": "tuple of struct(label, load_paths, jars): one library container and the load paths it answers.",
    },
)

_NO_DESCRIPTOR_JARS = _DevDistDescriptorJarsInfo(descriptor_jar = None, descriptor_jar_label = None, descriptor_jars = (), library_descriptors = ())

def _dev_dist_plugin_descriptor_jars_impl(ctx):
    descriptor_jars = []
    for label, load_path in ctx.attr.descriptor_jars.items():
        files = label.files.to_list()
        if len(files) != 1:
            fail("%s declares %d files, and a descriptor jar must name exactly one" % (label.label, len(files)), attr = "descriptor_jars")
        descriptor_jars.append(struct(label = label.label, load_path = load_path, files = tuple(files)))
    library_descriptors = []
    for container, load_paths in ctx.attr.library_descriptors.items():
        jars = library_entries(ctx, [container], attr_name = "library_descriptors")[0].jars
        library_descriptors.append(struct(label = container.label, load_paths = tuple(load_paths.split(" ")), jars = jars))
    return [
        DefaultInfo(files = depset()),
        _DevDistDescriptorJarsInfo(
            descriptor_jar = ctx.file.descriptor_jar,
            descriptor_jar_label = ctx.attr.descriptor_jar.label if ctx.attr.descriptor_jar != None else None,
            descriptor_jars = tuple(descriptor_jars),
            library_descriptors = tuple(library_descriptors),
        ),
    ]

_dev_dist_plugin_descriptor_jars = rule(
    doc = """The jar inputs of one descriptor leaf, in the neutral product configuration.

The leaf is configured for a product, so the stamps it writes are that product's. A jar it reads is a module output or a
library, and both are the same files for every product. Without this reset, the product configuration reached the module
behind every jar and compiled it a second time. `_dev_plugin_inputs` of `dev_plugin.bzl` is the same reset for a packed
plugin's jars.""",
    implementation = _dev_dist_plugin_descriptor_jars_impl,
    cfg = dev_dist_neutral_product_transition,
    attrs = {
        "descriptor_jar": attr.label(
            doc = "The test output jar that contains the plugin descriptor.",
            allow_single_file = [".jar"],
        ),
        "descriptor_jars": attr.label_keyed_string_dict(
            doc = "Test output jars that contain content descriptors, valued by descriptor load path.",
            allow_files = [".jar"],
        ),
        "library_descriptors": attr.label_keyed_string_dict(
            doc = """A descriptor no production source root holds, keyed by the library container and valued by its load paths.

A value states one load path, or several separated by a space. The load path is also the zip entry, because `toLoadPath`
strips the leading `/`. `findFileInModuleLibraryDependencies` is the assembly's route to such a file, and it belongs to
`DescriptorSearchPass.MODULE_OUTPUT` alone. One plugin of this product needs it: the Kotlin compiler ships
`META-INF/analysis-api/analysis-api-fir.xml` inside a library jar.

The **container** target, and not a jar of it: a per-jar label carries the artifact version, so a Maven bump rewrote
every checked-in file that named the jar. A container label carries no version, so a bump now rewrites only the
library's own package. The action expands the container back into its jars - see `_descriptor_request`.""",
            providers = [[JavaInfo]],
        ),
        "_allowlist_function_transition": attr.label(default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist")),
    },
)

def _descriptor_producer_identity(ctx):
    return {
        "label": str(ctx.label),
        "bin_dir": ctx.bin_dir.path,
        "target_platform": str(ctx.fragments.platform.platform),
        "configuration_checksum": None,
    }

def _descriptor_file_identity(file):
    return {
        "path": file.path,
        "short_path": file.short_path,
        "basename": file.basename,
        "owner": str(file.owner) if file.owner != None else None,
        "root": file.root.path,
        "root_kind": "source" if file.is_source else "generated",
        "is_directory": file.is_directory,
    }

def _descriptor_action(ctx, request, output, tool, mnemonic, progress_message, reserialized_output = None):
    """Runs one descriptor producer.

    `reserialized_output`, when given, is a second output: the same descriptor with every content module embedded, in
    the byte form a classpath writer copies. The writer produces it from the same request, so the classpath
    descriptor of an ordinary plugin costs no second action.
    """
    operations = (("--out", output, "formatted"),) + request.parameters
    if reserialized_output != None:
        operations += (("--reserialized-output", reserialized_output, "formatted"),)
    action = struct(
        parameters = tuple([
            (flag, item)
            for flag, value, mode in operations
            for item in (value if mode == "repeated" else [value])
        ]),
        inputs = request.inputs,
        outputs = (output,) + ((reserialized_output,) if reserialized_output != None else ()),
        executable = tool[DefaultInfo].files_to_run.executable,
        _tool_target = tool,
        tool_label = tool.label,
        tool_runfiles = tool[DefaultInfo].default_runfiles.files,
        mnemonic = mnemonic,
        param_file_format = "multiline",
        param_file_arg = "--flagfile=%s",
        use_param_file_always = True,
        execution_requirements = {},
    )
    args = ctx.actions.args()
    args.set_param_file_format(action.param_file_format)
    args.use_param_file(action.param_file_arg, use_always = action.use_param_file_always)
    for flag, value, mode in operations:
        if mode == "repeated":
            args.add_all(value, format_each = flag + "=%s")
        elif mode == "literal":
            args.add(flag + "=" + value)
        else:
            args.add(value, format = flag + "=%s")
    ctx.actions.run(
        mnemonic = action.mnemonic,
        inputs = depset(action.inputs),
        outputs = list(action.outputs),
        executable = action.executable,
        tools = dev_dist_tool_inputs(action._tool_target),
        arguments = [args],
        progress_message = progress_message,
        execution_requirements = action.execution_requirements,
    )
    return action

def _descriptor_declaration_json(declaration):
    return json.encode({
        "version": declaration.version,
        "producer": declaration.producer,
        "scope": declaration.scope,
        "sources": [{
            "attribute": source.attribute,
            "label": str(source.label),
            "load_path": source.load_path,
            "files": [_descriptor_file_identity(file) for file in source.files],
        } for source in declaration.sources],
        "actions": [{
            "mnemonic": action.mnemonic,
            "executable": _descriptor_file_identity(action.executable),
            "tool_label": str(action.tool_label),
            "tool_runfiles": [_descriptor_file_identity(file) for file in action.tool_runfiles.to_list()],
            "parameters": [{"flag": flag, "value": value.path if type(value) == "File" else value} for flag, value in action.parameters],
            "param_file_format": action.param_file_format,
            "param_file_arg": action.param_file_arg,
            "use_param_file_always": action.use_param_file_always,
            "declared_inputs": [_descriptor_file_identity(file) for file in action.inputs],
            "outputs": [_descriptor_file_identity(file) for file in action.outputs],
            "execution_requirements": action.execution_requirements,
        } for action in declaration.actions],
        "primary_output": _descriptor_file_identity(declaration.primary_output),
        "default_outputs": [_descriptor_file_identity(file) for file in declaration.default_outputs],
    }) + "\n"

def _dev_dist_plugin_descriptor_impl(ctx):
    module_name = ctx.attr.main_module
    if not module_name:
        fail("The plugin must state its main module", attr = "main_module")

    # The mode of the stamp class. The incoming transition keeps it only when this leaf refuses modules for it.
    stamp_class = ctx.attr._stamp_class[BuildSettingInfo].value

    # The variant's own directory, so two variants of one plugin never collide and the file's own name stays the main
    # module.
    directory = ctx.attr.variant + "/" if ctx.attr.variant else ""
    descriptor = ctx.actions.declare_file(directory + module_name + ".plugin.xml")

    classpath_descriptor_target = ctx.attr.classpath_descriptor_target
    if ctx.attr.embed_content_modules:
        classpath_descriptor = ctx.actions.declare_file(directory + module_name + ".plugin.classpath.xml")
    else:
        if classpath_descriptor_target == None:
            fail("a plugin that embeds no content modules must state its classpath descriptor target", attr = "classpath_descriptor_target")
        classpath_descriptor = classpath_descriptor_target[DevDistPluginClasspathDescriptorInfo].descriptor
    request = _descriptor_request(ctx, module_name)
    action = _descriptor_action(
        ctx,
        request,
        descriptor,
        ctx.attr._writer,
        # One mnemonic for every plugin descriptor, so a strategy or an execution-info override reaches all of them.
        mnemonic = "DevDistPluginDescriptor",
        progress_message = "Patching the plugin descriptor of %{label}",
        reserialized_output = classpath_descriptor if ctx.attr.embed_content_modules else None,
    )

    files = [descriptor]

    platforms = _resolved_platforms(ctx)
    declaration = struct(
        version = 3,
        producer = _descriptor_producer_identity(ctx),
        scope = {
            "main_module": module_name,
            "stamp_class": stamp_class,
            "variant": ctx.attr.variant,
            "declared_platforms": ctx.attr.platforms,
            "platforms": platforms,
            "declared_markers": ctx.attr.markers,
            "declared_version_suffix": ctx.attr.version_suffix,
            "markers": request.stamps.markers,
            "version_suffix": request.stamps.version_suffix,
        },
        sources = request.sources,
        actions = (action,),
        primary_output = descriptor,
        default_outputs = tuple(files),
    )
    metadata = ctx.actions.declare_file(ctx.label.name + ".declaration.json")
    ctx.actions.write(metadata, _descriptor_declaration_json(declaration))
    classpath_metadata = []
    if classpath_descriptor_target != None:
        classpath_metadata = [classpath_descriptor_target[OutputGroupInfo]._dev_dist_descriptor_metadata]
    return [
        DefaultInfo(files = depset(files)),
        OutputGroupInfo(_dev_dist_descriptor_metadata = depset([metadata], transitive = classpath_metadata)),
        DevDistPluginDescriptorInfo(
            plugin_main_module = module_name,
            descriptor = descriptor,
            classpath_descriptor = classpath_descriptor,
            platforms = platforms,
            mode_refusals = {mode: list(modules) for mode, modules in ctx.attr.mode_refused_content_modules.items()},
            embeds_content_modules = ctx.attr.embed_content_modules,
            stamp_class_modes = sorted(_refusing_modes(ctx.attr)),
            _declaration = declaration,
            _declaration_file = metadata,
        ),
    ]

def _resolved_platforms(ctx):
    """Which platforms this variant serves: the stated set, or the one the variant gives.

    Validated here rather than trusted, because a set filters on these names and a name no platform has would make the
    target reach no set at all.

    Args:
        ctx: the rule context.

    Returns:
        The `HOST_PLATFORMS` entries this target's descriptor is the descriptor of.
    """
    if not ctx.attr.platforms:
        return dev_dist_plugin_descriptor_platforms(ctx.attr.variant)
    for platform in ctx.attr.platforms:
        if platform not in HOST_PLATFORMS:
            fail("'%s' is not one of %s" % (platform, HOST_PLATFORMS), attr = "platforms")
    return ctx.attr.platforms

_dev_dist_plugin_descriptor = rule(
    doc = """Patches one plugin's `META-INF/plugin.xml` the way a dev-distribution assembly does.

The incoming `dev_dist_stamp_class_transition` keeps only the stamp class of the leaf, so a group and a component
reach one configured leaf per stamp class.""",
    implementation = _dev_dist_plugin_descriptor_impl,
    cfg = dev_dist_stamp_class_transition,
    fragments = ["platform"],
    attrs = {
        "main_module": attr.string(
            doc = "The main JPS module that identifies this descriptor.",
            mandatory = True,
        ),
        "descriptor": attr.label(
            doc = """The plugin's own `META-INF/plugin.xml`, as the exported source file.

Declared rather than derived: the file sits under a production resource root, and which directory that is cannot be
computed from a label. Everything else about the plugin is derived - see the macro.""",
            allow_single_file = [".xml"],
        ),
        "jar_inputs": attr.label(
            doc = "The leaf's `_dev_dist_plugin_descriptor_jars` target, or None when the leaf reads no jar. The macro declares it.",
            providers = [_DevDistDescriptorJarsInfo],
        ),
        "descriptor_entry": attr.string(
            doc = "The plugin descriptor path inside the descriptor jar.",
        ),
        "descriptors": attr.label_keyed_string_dict(
            doc = """Every other descriptor this plugin's patch can reach, keyed by target and valued by load path.

The load path is what a resolver asks the descriptor cache for. Seeding the cache from these files is what lets the
action run with no JPS project model: `resolveElement` reads the cache before it touches a module output.""",
            allow_files = [".xml"],
        ),
        "variant": attr.string(
            doc = """The layout variant, empty for a plugin whose one layout serves every platform.

`darwin_aarch64` for a variant restricted to one operating system and one architecture, `windows` for one restricted to
an operating system alone. It names the output's directory, so two variants of one plugin never collide. It also gives
`platforms`, which is why a plan states no platform list.""",
        ),
        "platforms": attr.string_list(
            doc = """Which `HOST_PLATFORMS` entries this variant serves. Empty takes the set `variant` gives.

Stated only by a layout whose bundling restriction no variant token spells. No layout of this product states one, so no
generator writes it, and it is kept on purpose: the variant token cannot spell every restriction a layout may take.
`dev_dist_plugin_descriptor_platforms` answers the four shapes a token has, and `layoutVariant` of
`devDistPluginDescriptorPlan.kt` is the authority both mirror.""",
        ),
        "markers": attr.string_list(
            doc = """The layout's raw text patch as marker-table rows, in the order it applies them.

Two shapes. `os-arch:<osId>:<marketplaceName>` is the OS and architecture dependency placeholder, whose replacement text
`osArchDescriptorMarker` owns; `marker:<literal>:<replacement>` is a plain replacement. The executor replaces the first
occurrence of a plain string, and an unknown shape fails the action rather than emitting an unpatched text.""",
        ),
        "version_suffix": attr.string(
            doc = """What the layout appends to the IDE build version - `PluginLayout.versionSuffix`.

Empty for a layout that stamps the build version unchanged, which is nearly every one. A per-variant deviation: an
OS-specific plugin states the marketplace operating system and architecture in its version.""",
        ),
        "compatible_build_range": attr.string(
            doc = """Range constraint override.""",
        ),
        "refused_content_modules": attr.string_list(
            doc = """The content modules the product's filter refuses. Normally empty.

The assembly drops an optional `<module/>` a `ContentModuleFilter` refuses, and that filter reads the JPS project model.
The survivors are `descriptor`'s own `<content>`, which this action already declares, so only the refusals are stated
here. A refusal that reaches no `<module/>` fails the action.""",
        ),
        "mode_refused_content_modules": attr.string_list_dict(
            doc = """The content modules each product mode refuses at run time, keyed by the mode, such as `frontend`.

A fact of the plugin's modules: a frontend refuses the content modules that reach a backend root, and that rule reads
the module alone. The descriptor keeps every module, and the run time excludes a refused one. The provider states the
list of the mode `dev_dist_stamp_class` names, and the rules place no jar of such a module under that mode.""",
        ),
        "separate_jar": attr.string_list(
            doc = "Which content module's embedded descriptor takes `separate-jar=\"true\"`. A deviation, normally empty.",
        ),
        "embed_content_modules": attr.bool(
            default = True,
            doc = "False for a layout that scrambles paths: it embeds no content module descriptor, and the assembly agrees.",
        ),
        "exact_version": attr.bool(doc = "`PluginLayout.pluginCompatibilityExactVersion`."),
        "retain_product_descriptor": attr.bool(doc = "`PluginLayout.retainProductDescriptorForBundledPlugin`."),
        "_build_number_file": attr.label(
            allow_single_file = True,
            default = Label("@community//:build.txt"),
            doc = "Exactly `ij_plugin._default_ide_build_number_file`: the build number is a declared file, never a path a tool computes.",
        ),
        "_writer": attr.label(
            doc = """The plugin descriptor writer.

A descriptor feeds every plugin main jar. So the writer is a native executable, and no JVM action for it sits on the
critical path of the build.""",
            default = Label("//platform/build-scripts/bazel-rules:plugin_descriptor_writer"),
            executable = True,
            cfg = "exec",
        ),
        "_stamp_class": attr.label(
            doc = "The mode of the stamp class, the flag `dev_dist_stamp_class`. The leaf embeds the refusals of that mode.",
            default = Label("//build:dev_dist_stamp_class"),
            providers = [BuildSettingInfo],
        ),
        "_allowlist_function_transition": attr.label(default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist")),
        "classpath_descriptor_target": attr.label(
            doc = "Set by the macro to the separate classpath descriptor target when the leaf embeds no content modules.",
            providers = [DevDistPluginClasspathDescriptorInfo],
        ),
    },
)

def _dev_dist_plugin_classpath_descriptor_impl(ctx):
    module_name = ctx.attr.main_module
    if not module_name:
        fail("The plugin must state its main module", attr = "main_module")

    stamp_class = ctx.attr._stamp_class[BuildSettingInfo].value
    directory = ctx.attr.variant + "/" if ctx.attr.variant else ""
    classpath_descriptor = ctx.actions.declare_file(directory + module_name + ".plugin.classpath.xml")
    request = _descriptor_request(
        ctx,
        module_name,
        embed_content_modules = True,
        reserialize_before_content_embedding = True,
    )
    action = _descriptor_action(
        ctx,
        request,
        classpath_descriptor,
        ctx.attr._writer,
        mnemonic = "DevDistPluginClasspathDescriptor",
        progress_message = "Embedding the classpath descriptor of %{label}",
    )
    platforms = _resolved_platforms(ctx)
    declaration = struct(
        version = 3,
        producer = _descriptor_producer_identity(ctx),
        scope = {
            "main_module": module_name,
            "stamp_class": stamp_class,
            "variant": ctx.attr.variant,
            "declared_platforms": ctx.attr.platforms,
            "platforms": platforms,
            "declared_markers": ctx.attr.markers,
            "declared_version_suffix": ctx.attr.version_suffix,
            "markers": request.stamps.markers,
            "version_suffix": request.stamps.version_suffix,
        },
        sources = request.sources,
        actions = (action,),
        primary_output = classpath_descriptor,
        default_outputs = (classpath_descriptor,),
    )
    metadata = ctx.actions.declare_file(ctx.label.name + ".declaration.json")
    ctx.actions.write(metadata, _descriptor_declaration_json(declaration))
    return [
        DefaultInfo(files = depset([classpath_descriptor])),
        OutputGroupInfo(_dev_dist_descriptor_metadata = depset([metadata])),
        DevDistPluginClasspathDescriptorInfo(
            descriptor = classpath_descriptor,
            stamp_class = stamp_class,
            _declaration = declaration,
        ),
    ]

_dev_dist_plugin_classpath_descriptor = rule(
    doc = "Writes one plugin's classpath descriptor in the neutral product configuration.",
    implementation = _dev_dist_plugin_classpath_descriptor_impl,
    cfg = dev_dist_neutral_product_transition,
    fragments = ["platform"],
    attrs = {
        "main_module": attr.string(
            doc = "The main JPS module that identifies this descriptor.",
            mandatory = True,
        ),
        "descriptor": attr.label(allow_single_file = [".xml"]),
        "jar_inputs": attr.label(providers = [_DevDistDescriptorJarsInfo]),
        "descriptor_entry": attr.string(),
        "descriptors": attr.label_keyed_string_dict(allow_files = [".xml"]),
        "variant": attr.string(),
        "platforms": attr.string_list(),
        "markers": attr.string_list(),
        "version_suffix": attr.string(),
        "compatible_build_range": attr.string(),
        "refused_content_modules": attr.string_list(),
        "mode_refused_content_modules": attr.string_list_dict(),
        "separate_jar": attr.string_list(),
        "exact_version": attr.bool(),
        "retain_product_descriptor": attr.bool(),
        "_build_number_file": attr.label(
            allow_single_file = True,
            default = Label("@community//:build.txt"),
        ),
        "_writer": attr.label(
            default = Label("//platform/build-scripts/bazel-rules:plugin_descriptor_writer"),
            executable = True,
            cfg = "exec",
        ),
    } | DEV_DIST_STAMP_CLASS_FLAG_ATTR | {
        "_allowlist_function_transition": attr.label(default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist")),
    },
)

# The operating-system tokens `HOST_PLATFORMS` spells, and the architecture tokens. Derived from that list and never
# written again, because a second spelling of either set would let the two disagree.
_HOST_PLATFORM_OPERATING_SYSTEMS = sorted({platform_parts(platform).os: None for platform in HOST_PLATFORMS})

_HOST_PLATFORM_ARCHITECTURES = sorted({platform_parts(platform).arch: None for platform in HOST_PLATFORMS})

# What a descriptor target's name ends in. `dev_dist_plugin_descriptor_target_name` writes it.
_DEV_DESCRIPTOR_SUFFIX = "_dev_descriptor"

def dev_dist_plugin_descriptor_platforms(variant):
    """Which `HOST_PLATFORMS` entries one layout variant serves - `"darwin"` gives the two macOS platforms.

    A variant has four shapes, and `layoutVariant` of `devDistPluginDescriptorPlan.kt` is the authority this mirrors.
    The empty variant serves every platform. An operating-system token serves that operating system on every
    architecture. An architecture token serves that architecture on every operating system. A `HOST_PLATFORMS` entry
    serves itself alone.

    So the platform set of an entry follows from its variant, and a plan that stated both would state the same fact
    twice.

    Args:
        variant: the layout variant, or empty for a plugin whose one layout serves every platform.

    Returns:
        The `HOST_PLATFORMS` entries this variant serves, in `HOST_PLATFORMS` order.
    """
    if not variant:
        return HOST_PLATFORMS
    if variant in _HOST_PLATFORM_OPERATING_SYSTEMS:
        return [platform for platform in HOST_PLATFORMS if platform.startswith(variant + "_")]
    if variant in _HOST_PLATFORM_ARCHITECTURES:
        return [platform for platform in HOST_PLATFORMS if platform.endswith("_" + variant)]
    if variant in HOST_PLATFORMS:
        return [variant]
    fail("dev_dist_plugin_descriptor: '%s' is no layout variant. It names no operating system of %s, no architecture of %s and no platform of %s" % (
        variant,
        _HOST_PLATFORM_OPERATING_SYSTEMS,
        _HOST_PLATFORM_ARCHITECTURES,
        HOST_PLATFORMS,
    ))

def dev_dist_plugin_descriptor_os_arch_stamps(variant):
    """The marker row and the version suffix a one-platform layout variant takes.

    Both are mechanical. The row states the `<!-- OS/ARCH-DEPENDENCY-PLACEHOLDER -->` replacement, whose text
    `osArchDescriptorMarker` owns, and a plugin bundled for one platform states that platform in its version. So the
    plan states neither, and `osArchStamps` of `devDistPluginDescriptorPlan.kt` verifies the layout against the same
    pair. A layout that disagrees is held out by name, which is why deriving both here is safe.

    The spellings come from `DEV_DIST_MARKETPLACE_NAMES`: `OsFamily.osId` and `JvmArchitecture.marketplaceName`, keyed
    by the token `HOST_PLATFORMS` spells. Generated, because no rule can read an enum.

    Args:
        variant: the layout variant.

    Returns:
        `struct(markers, version_suffix)`, or `None` for a variant that names no single platform.
    """
    if variant not in HOST_PLATFORMS:
        return None
    parts = platform_parts(variant)
    os = DEV_DIST_MARKETPLACE_NAMES.get(parts.os)
    arch = DEV_DIST_MARKETPLACE_NAMES.get(parts.arch)
    if os == None or arch == None:
        fail("dev_dist_plugin_descriptor: DEV_DIST_MARKETPLACE_NAMES does not answer both '%s' and '%s' of variant '%s'" % (
            parts.os,
            parts.arch,
            variant,
        ))
    return struct(
        markers = ["os-arch:%s:%s" % (os, arch)],
        version_suffix = "-%s-%s" % (os, arch),
    )

def dev_dist_plugin_descriptor_target_name(main_module, variant = ""):
    """This target's name - `("intellij.xpath", "")` gives `"intellij.xpath_dev_descriptor"`.

    Keyed by the main module and not by the module target's own name, because a target name repeats: several plugins
    keep their main module in a package whose production target is called `plugin`, and two of those would declare one
    target twice. The main module is unique across the product, and it is already the stem of the output file.

    The variant joins the name where the plugin has one, because a plugin whose descriptor differs by operating system
    or architecture declares one target per variant.

    Public because the same name is written in two places that must agree: the target, and whatever names it.

    Args:
        main_module: the plugin's main JPS module.
        variant: the layout variant, or empty for a plugin whose one layout serves every platform.
    """
    if variant:
        return main_module + "_" + variant + _DEV_DESCRIPTOR_SUFFIX
    return main_module + _DEV_DESCRIPTOR_SUFFIX

def dev_dist_plugin_descriptor(
        main_module,
        descriptor = "",
        descriptor_jar = None,
        descriptor_entry = "",
        content_modules = [],
        descriptor_index = {},
        variant = "",
        tags = [],
        visibility = ["//visibility:public"],
        **kwargs):
    """`_dev_dist_plugin_descriptor` with what every plugin says the same way filled in.

    Three things the macro derives rather than have them restated once per plugin. `name` comes from `main_module`, the
    way `content_module_jar` derives its own. The conventional `descriptors` rows come from `descriptor_index`. And
    `manual` is added, for `content_module_jar`'s reason: these are per-plugin targets of a measurement, and
    `bazel build //...` must not run all of them.

    A row is derived for each name in `content_modules` that the index knows. A name the index does not know is a
    merged member, a `sub/module` name or a module whose row the caller states. The macro skips it. An explicit row wins
    by label and by load path, because the descriptor writer refuses a load path declared twice.

    A leaf that embeds no content module also gets a `<name>_classpath` target. That target writes the classpath
    descriptor in the neutral product configuration.

    Args:
        main_module: the plugin's main JPS module, which names the target.
        descriptor: the label of the plugin's `META-INF/plugin.xml`, package-relative or full.
        descriptor_jar: the jar that holds the plugin descriptor, in place of `descriptor`.
        descriptor_entry: the plugin descriptor path inside `descriptor_jar`.
        content_modules: the module names whose conventional descriptors the patch can reach.
        descriptor_index: the conventional descriptor label of each module, from the JPS bridge of this half.
        variant: the layout variant, which joins the target's name and the output's directory.
        tags: extra tags. `manual` is added.
        visibility: public by default.
        **kwargs: see `_dev_dist_plugin_descriptor`.
    """
    if bool(descriptor) == bool(descriptor_jar):
        fail("dev_dist_plugin_descriptor requires exactly one descriptor source")
    source = descriptor or None

    descriptors = dict(kwargs.pop("descriptors", {}))
    stated_load_paths = {path: True for path in descriptors.values()}
    for module_name in content_modules:
        label = descriptor_index.get(module_name)
        if label == None or label in descriptors or (module_name + ".xml") in stated_load_paths:
            continue
        descriptors[label] = module_name + ".xml"
    if descriptors:
        kwargs["descriptors"] = {label: descriptors[label] for label in sorted(descriptors)}
    name = dev_dist_plugin_descriptor_target_name(main_module, variant)
    descriptor_jars = kwargs.pop("descriptor_jars", {})
    library_descriptors = kwargs.pop("library_descriptors", {})
    jar_inputs = None
    if descriptor_jar or descriptor_jars or library_descriptors:
        jar_inputs = name + "_jars"
        _dev_dist_plugin_descriptor_jars(
            name = jar_inputs,
            descriptor_jar = descriptor_jar,
            descriptor_jars = descriptor_jars,
            library_descriptors = library_descriptors,
            tags = ["manual"],
            visibility = ["//visibility:private"],
            **{key: kwargs[key] for key in ["testonly"] if key in kwargs}
        )
    if not kwargs.get("embed_content_modules", True):
        classpath_kwargs = {
            key: value
            for key, value in kwargs.items()
            if key != "embed_content_modules"
        }
        classpath_name = name + "_classpath"
        _dev_dist_plugin_classpath_descriptor(
            name = classpath_name,
            main_module = main_module,
            descriptor = source,
            jar_inputs = ":" + jar_inputs if jar_inputs else None,
            descriptor_entry = descriptor_entry,
            variant = variant,
            tags = ["manual"],
            visibility = ["//visibility:private"],
            **classpath_kwargs
        )
        kwargs["classpath_descriptor_target"] = ":" + classpath_name
    _dev_dist_plugin_descriptor(
        name = name,
        main_module = main_module,
        descriptor = source,
        jar_inputs = ":" + jar_inputs if jar_inputs else None,
        descriptor_entry = descriptor_entry,
        variant = variant,
        tags = tags + ["manual"],
        visibility = visibility,
        **kwargs
    )
