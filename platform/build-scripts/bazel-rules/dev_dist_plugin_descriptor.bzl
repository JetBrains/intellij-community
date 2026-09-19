"""Writes one plugin's patched `META-INF/plugin.xml` in an action of its own.

The rule runs one action per plugin. Its declared inputs are the descriptors the patch reads. Its output is the text
the plugin's main jar receives. Every fragment of the product reads that output instead of computing it inside the
assembly that evaluates the whole product layout. The Go writer is the one producer of the text.

Modelled on two neighbours, each for what it already settled. `ij_plugin` for the per-plugin grain and for the build
number arriving as a declared file. `content_module_jar` for the provider, for the `manual` tag and for a packer named
directly rather than pushed in through a flag.

**Remote-cacheable on purpose.** These actions read tens of small XML files, not a platform's gigabytes, so
`_LOCAL_DISK_CACHE_ONLY` of `intellij_dev_dist.bzl` does not apply here and `local` is not set. A cheap hermetic action
that both caches keep is the property ADR 0006 asks for.
"""

load("@rules_java//java:defs.bzl", "JavaInfo")
load("//build:dev_launch_dependencies.bzl", "HOST_PLATFORMS", "platform_parts")
load(":content_module_jar.bzl", "library_entries")

DevDistPluginDescriptorInfo = provider(
    doc = """One plugin's patched descriptor, and the plugin it belongs to.

    The provider lets Bazel reject a target that produces no descriptor during analysis.""",
    fields = {
        "plugin_main_module": "The JPS module whose resources carry the descriptor.",
        "descriptor": "The patched `META-INF/plugin.xml` as a `File`.",
        "classpath_descriptor": """The patched descriptor with embedded content modules for classpath generation.

        Always in its final byte form: a classpath writer copies the bytes and applies no XML rewrite.""",
        "platforms": "The `HOST_PLATFORMS` entries this layout variant serves.",
        "_declaration": "Private versioned metadata with the declared File objects and action parameters.",
        "_declaration_file": "The private metadata file. It is not a default output or an action input.",
    },
)

# The manifest key a fragment reads a produced descriptor under. Its own namespace: every other key of the input
# manifest is a Bazel label string, so it starts with `@` or `//`, and a module jar's key also ends in `.jar`. This one
# starts with a word and holds no `//`, so it can collide with nothing already there.
#
# Written once here and once in Kotlin, in `BazelBuildInputs.producedPluginDescriptorIfDeclared`. Both spellings name
# each other, because a drift between them reads as "no descriptor was declared" and the patch then runs as before.
DEV_DIST_DESCRIPTOR_KEY_PREFIX = "dev-dist-descriptor:"

DevDistProductInfo = provider(
    doc = """The product scalars every plugin's descriptor stamp needs.

    A configuration and not four attributes on the leaf rule. One plugin's patched descriptor differs between two
    products only in these values, so a leaf that stated them would be a leaf per (plugin, product). Read through
    a `label_flag`, a product's set target names its own values, and one leaf per plugin then answers every product
    that bundles the plugin. The exception is a plugin two products state differently. The later product gets a leaf
    of its own under `build/dev-dist-descriptors/<module>/<product>`, and that leaf still reads its stamps here.""",
    fields = {
        "eap": "The `eap` attribute of the product's `ApplicationInfo.xml`.",
        "release_date": "`ApplicationInfoProperties.majorReleaseDate`.",
        "release_version": "`ApplicationInfoProperties.releaseVersionForLicensing`.",
        "marketplace_names": "`OsFamily.osId` and `JvmArchitecture.marketplaceName`, keyed by the token `HOST_PLATFORMS` spells.",
        "platform_prefix": "The product's platform prefix, `idea` for example. Empty in the flag's default.",
        "_producer": "The actual provider declaration, not the forwarding label flag or a consumer label.",
    },
)

def _dev_dist_product_info_impl(ctx):
    return [DevDistProductInfo(
        eap = ctx.attr.eap,
        release_date = ctx.attr.release_date,
        release_version = ctx.attr.release_version,
        marketplace_names = ctx.attr.marketplace_names,
        platform_prefix = ctx.attr.platform_prefix,
        _producer = _descriptor_producer_identity(ctx),
    )]

dev_dist_product_info = rule(
    doc = """One product's descriptor stamps, as the target `@community//build:dev_dist_product_info` points at.

    Public because two packages declare one: `@community//build` declares the empty default of the flag, and the
    descriptor macro declares the product's own.""",
    implementation = _dev_dist_product_info_impl,
    fragments = ["platform"],
    attrs = {
        "eap": attr.bool(doc = "The `eap` attribute of the product's `ApplicationInfo.xml`."),
        "release_date": attr.string(doc = "`ApplicationInfoProperties.majorReleaseDate`. Empty in the flag's default."),
        "release_version": attr.string(doc = "`ApplicationInfoProperties.releaseVersionForLicensing`. Empty in the flag's default."),
        "marketplace_names": attr.string_dict(
            doc = """`OsFamily.osId` and `JvmArchitecture.marketplaceName`, keyed by the token `HOST_PLATFORMS` spells.

Generated, because no rule can read an enum. Here and not on the leaf, so a leaf beside a plugin derives the stamps of
a one-platform layout variant without stating the table - see `dev_dist_plugin_descriptor_os_arch_stamps`.""",
        ),
        "platform_prefix": attr.string(
            doc = "The product's platform prefix. A packed plugin component states it to the collector. Empty in the flag's default.",
        ),
    },
)

# The flag a descriptor action reads the product scalars through. Canonical, because a transition resolves neither an
# apparent repository name nor a relative label.
_PRODUCT_INFO_FLAG = str(Label("//build:dev_dist_product_info"))

# The flag's default. A rule that must not depend on a product resets the flag to it, see
# `dev_dist_neutral_product_transition`. A `Label` and not its string: Bazel trims a flag that equals its default from
# the configuration only when the two values compare equal, and the default is a `Label`.
_NO_PRODUCT_INFO = Label("//build:no_dev_dist_product_info")

def _dev_dist_product_info_transition_impl(settings, attr):
    product_info = getattr(attr, "product_info", None)
    if product_info == None:
        # The consumer names no product, so the leaf keeps the configuration it is reached in.
        return {_PRODUCT_INFO_FLAG: settings[_PRODUCT_INFO_FLAG]}
    return {_PRODUCT_INFO_FLAG: str(product_info)}

# What names the product on the way down to a leaf. The leaf declares no product, so the target that collects leaves
# per product is the one that states it.
dev_dist_product_info_transition = transition(
    implementation = _dev_dist_product_info_transition_impl,
    inputs = [_PRODUCT_INFO_FLAG],
    outputs = [_PRODUCT_INFO_FLAG],
)

def _dev_dist_neutral_product_transition_impl(_settings, _attr):
    return {_PRODUCT_INFO_FLAG: _NO_PRODUCT_INFO}

# The reset. A compiled module jar is the same file for every product, so a rule that collects module jars under a
# product configuration resets the flag to its default. The default value is trimmed from the configuration, so the
# jars come from the default configuration and no product recompiles them.
dev_dist_neutral_product_transition = transition(
    implementation = _dev_dist_neutral_product_transition_impl,
    inputs = [],
    outputs = [_PRODUCT_INFO_FLAG],
)

_PRODUCT_INFO_ATTR = {
    "product_info": attr.label(
        doc = """This product's `dev_dist_product_info`, which every descriptor below is configured with.

The transition on `descriptors` reads this label, so a leaf stamps the product that asked for it.""",
        mandatory = True,
        providers = [DevDistProductInfo],
    ),
    "_allowlist_function_transition": attr.label(
        default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist"),
    ),
}

DevDistPluginDescriptorSetInfo = provider(
    doc = """The produced descriptors of one fragment of one product.

    A set target and not a `label_list` on the fragment: the set selects, per plugin, the variant its platform takes.
    No macro of this package declares a set today. The rule `_dev_dist_plugin_descriptor_set` stays for a fragment that
    reads produced descriptors.

    The set is also where the product is named. It transitions every descriptor below it onto its own
    `dev_dist_product_info`, so one leaf per plugin serves any number of products.""",
    fields = {
        "descriptors": "A depset of `struct(plugin_main_module, descriptor)`, one per plugin the fragment patches.",
    },
)

def _dev_dist_plugin_descriptor_set_impl(ctx):
    if ctx.attr.platform not in HOST_PLATFORMS:
        fail("'%s' is not one of %s" % (ctx.attr.platform, HOST_PLATFORMS), attr = "platform")
    records = []
    metadata = []
    seen = {}
    for target in ctx.attr.descriptors:
        info = target[DevDistPluginDescriptorInfo]
        if ctx.attr.platform not in info.platforms:
            continue
        main_module = info.plugin_main_module
        earlier = seen.get(main_module)
        if earlier != None:
            # A fragment reads a produced descriptor under one manifest key per plugin, so two variants reaching one
            # platform would make the key ambiguous. Refused here, where both are in one list.
            #
            # Load-bearing, and not a guard against a generator defect. Every variant of one plugin is in this list, so
            # the platform filter above is the whole reason exactly one of them survives.
            fail("%s and %s both produce the descriptor of '%s' on '%s'" % (
                earlier,
                target.label,
                main_module,
                ctx.attr.platform,
            ), attr = "descriptors")
        seen[main_module] = target.label
        records.append(struct(
            plugin_main_module = main_module,
            descriptor = info.descriptor,
        ))
        metadata.append(info._declaration_file)
    return [
        DevDistPluginDescriptorSetInfo(descriptors = depset(records)),
        OutputGroupInfo(_dev_dist_descriptor_metadata = depset(metadata)),
    ]

_dev_dist_plugin_descriptor_set = rule(
    doc = "One fragment's produced plugin descriptors, as the one label a fragment declares.",
    implementation = _dev_dist_plugin_descriptor_set_impl,
    attrs = {
        "descriptors": attr.label_list(
            doc = """Every `dev_dist_plugin_descriptor` target of the plugins this fragment lays out.

Every layout variant of each of them, because which variant one platform takes follows from the variant itself. So one
list serves all six platforms, and `platform` selects inside it.

The provider gate is the whole check. A target that produces no descriptor has no plugin to name.""",
            cfg = dev_dist_product_info_transition,
            providers = [DevDistPluginDescriptorInfo],
        ),
        "platform": attr.string(
            doc = """The `HOST_PLATFORMS` entry this set is the set of.

A plugin restricted to one operating system or one architecture reaches a fragment of that platform alone, so the set
holds the variant that platform takes and nothing else. Selected while Bazel analyses, from the provider each variant
carries.""",
            mandatory = True,
        ),
    } | _PRODUCT_INFO_ATTR,
)

def _dev_dist_plugin_descriptor_group_impl(ctx):
    return [
        DefaultInfo(files = depset(transitive = [
            target[DefaultInfo].files
            for target in ctx.attr.descriptors
        ])),
        OutputGroupInfo(_dev_dist_descriptor_metadata = depset([
            target[DevDistPluginDescriptorInfo]._declaration_file
            for target in ctx.attr.descriptors
        ])),
    ]

dev_dist_plugin_descriptor_group = rule(
    doc = """Produced descriptors of one product, as one target that names the product.

    A rule and not a `filegroup`, because the product scalars arrive through a transition and a `filegroup` states none.
    `//build:idea_dev_descriptor_leaf_build_test` builds one such group, and the produced descriptors are its
    `DefaultInfo` files.

    Public, because a leaf no longer builds on its own: the flag's default states no product and the leaf fails at
    analysis. So anything that wants to build a leaf names it through a group, and `//build:*_descriptor_build_test` is
    the guard that does.""",
    implementation = _dev_dist_plugin_descriptor_group_impl,
    attrs = {
        "descriptors": attr.label_list(
            doc = "Every `dev_dist_plugin_descriptor` target of this product, every layout variant included.",
            cfg = dev_dist_product_info_transition,
            providers = [DevDistPluginDescriptorInfo],
        ),
    } | _PRODUCT_INFO_ATTR,
)

def _os_arch_stamps(ctx, product):
    """The marker rows and the version suffix this leaf stamps: the stated ones, or the ones its variant gives.

    Derived here and not in a macro, because every leaf now sits in the package of its own plugin and the table the
    derivation reads is a product fact. The product arrives through the configuration, so a leaf states neither the
    table nor the pair.

    Args:
        ctx: the rule context.
        product: the `DevDistProductInfo` this leaf is configured with.

    Returns:
        `struct(markers, version_suffix)`.
    """
    stamps = dev_dist_plugin_descriptor_os_arch_stamps(product.marketplace_names, ctx.attr.variant)
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
    product = ctx.attr._product_info[DevDistProductInfo]
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
        ("--release-date", product.release_date, "formatted"),
        ("--release-version", product.release_version, "formatted"),
        ("--eap", str(product.eap).lower(), "literal"),
        ("--exact-version", str(ctx.attr.exact_version).lower(), "literal"),
        ("--retain-product-descriptor", str(ctx.attr.retain_product_descriptor).lower(), "literal"),
        ("--embed-content-modules", str(embed_content_modules).lower(), "literal"),
        ("--reserialize-before-content-embedding", str(reserialize_before_content_embedding).lower(), "literal"),
    ]
    if source != None:
        parameters.insert(1, ("--source", source, "formatted"))
    else:
        parameters.insert(1, ("--source-in-jar", ctx.attr.descriptor_entry + "=" + source_jar.path, "literal"))
    if ctx.attr.directory_name:
        parameters.append(("--directory-name", ctx.attr.directory_name, "formatted"))
    if ctx.attr.main_jar_name:
        parameters.append(("--main-jar-name", ctx.attr.main_jar_name, "formatted"))
    stamps = _os_arch_stamps(ctx, product)
    if stamps.version_suffix:
        parameters.append(("--version-suffix", stamps.version_suffix, "formatted"))
    parameters.extend([
        ("--marker", stamps.markers, "repeated"),
        ("--refused-content-module", ctx.attr.refused_content_modules, "repeated"),
        ("--separate-jar", ctx.attr.separate_jar, "repeated"),
        ("--plugin-module", ctx.attr.plugin_modules, "repeated"),
        ("--platform-module", ctx.attr.platform_modules, "repeated"),
    ])

    source_file = source if source != None else source_jar
    source_label = ctx.attr.descriptor.label if source != None else jars.descriptor_jar_label
    inputs = [source_file, ctx.file._build_number_file]
    sources = [
        _descriptor_source_binding("descriptor" if source != None else "descriptor_jar", source_label, ctx.attr.descriptor_entry if source_jar != None else None, [source_file]),
        _descriptor_source_binding("build_number", ctx.attr._build_number_file.label, None, [ctx.file._build_number_file]),
    ]

    # One answer per load path. The Go executor seeds the files first and puts a jar entry in only when the path is
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
    for label, load_path in ctx.attr.platform_descriptors.items():
        files = label.files.to_list()
        if len(files) != 1:
            fail("%s declares %d files, and a descriptor must name exactly one" % (label.label, len(files)), attr = "platform_descriptors")
        parameters.append(("--platform-descriptor", load_path + "=" + files[0].path, "literal"))
        sources.append(_descriptor_source_binding("platform_descriptors", label.label, load_path, files))
        inputs.append(files[0])
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
    the byte form a classpath writer copies. The Go executor writes it from the same request, so the classpath
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
        arguments = [args],
        progress_message = progress_message,
        execution_requirements = action.execution_requirements,
    )
    return action

def _descriptor_declaration_json(declaration):
    return json.encode({
        "version": declaration.version,
        "producer": declaration.producer,
        "product": {
            "producer": declaration.product._producer,
            "dependency_label": str(declaration.product_dependency_label),
            "eap": declaration.product.eap,
            "release_date": declaration.product.release_date,
            "release_version": declaration.product.release_version,
            "marketplace_names": declaration.product.marketplace_names,
        },
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
    if ctx.attr.unresolved_descriptor_modules:
        fail("Missing selected descriptors for %s: %s. Regenerate the dev sections." % (
            ctx.attr.main_module,
            ctx.attr.unresolved_descriptor_modules,
        ), attr = "unresolved_descriptor_modules")
    module_name = ctx.attr.main_module
    if not module_name:
        fail("The plugin must state its main module", attr = "main_module")

    # Fail closed. The flag's default states no product, and a descriptor stamped from it would carry an empty release
    # date and an empty release version. A product's set target sets the flag on the way down. A leaf built on its own
    # has to set the flag itself, and the failure below says which flag that is.
    product = ctx.attr._product_info[DevDistProductInfo]
    if not product.release_date or not product.release_version:
        fail("%s states no release date and no release version, so no product asked for this descriptor. Point %s at one" % (
            ctx.attr._product_info.label,
            _PRODUCT_INFO_FLAG,
        ))

    # The variant's own directory, so two variants of one plugin never collide and the file's own name stays the main
    # module.
    directory = ctx.attr.variant + "/" if ctx.attr.variant else ""
    descriptor = ctx.actions.declare_file(directory + module_name + ".plugin.xml")

    # The classpath descriptor is always a file of its own, in its final byte form. An ordinary plugin gets it from the
    # primary action as a second output. A plugin that embeds no content module gets it from a second action, which
    # embeds them and normalizes the XML first.
    classpath_descriptor = ctx.actions.declare_file(directory + module_name + ".plugin.classpath.xml")
    request = _descriptor_request(ctx, module_name)
    actions = [_descriptor_action(
        ctx,
        request,
        descriptor,
        ctx.attr._writer,
        # One mnemonic for every plugin descriptor, so a strategy or an execution-info override reaches all of them.
        mnemonic = "DevDistPluginDescriptor",
        progress_message = "Patching the plugin descriptor of %{label}",
        reserialized_output = classpath_descriptor if ctx.attr.embed_content_modules else None,
    )]

    if not ctx.attr.embed_content_modules:
        classpath_request = _descriptor_request(
            ctx,
            module_name,
            embed_content_modules = True,
            reserialize_before_content_embedding = True,
        )
        actions.append(_descriptor_action(
            ctx,
            classpath_request,
            classpath_descriptor,
            ctx.attr._writer,
            mnemonic = "DevDistPluginClasspathDescriptor",
            progress_message = "Embedding the classpath descriptor of %{label}",
        ))

    files = [descriptor]

    platforms = _resolved_platforms(ctx)
    declaration = struct(
        version = 1,
        producer = _descriptor_producer_identity(ctx),
        product = product,
        product_dependency_label = ctx.attr._product_info.label,
        scope = {
            "main_module": module_name,
            "variant": ctx.attr.variant,
            "declared_platforms": ctx.attr.platforms,
            "platforms": platforms,
            "declared_markers": ctx.attr.markers,
            "declared_version_suffix": ctx.attr.version_suffix,
            "markers": request.stamps.markers,
            "version_suffix": request.stamps.version_suffix,
        },
        sources = request.sources,
        actions = tuple(actions),
        primary_output = descriptor,
        default_outputs = tuple(files),
    )
    metadata = ctx.actions.declare_file(ctx.label.name + ".declaration.json")
    ctx.actions.write(metadata, _descriptor_declaration_json(declaration))
    return [
        DefaultInfo(files = depset(files)),
        OutputGroupInfo(_dev_dist_descriptor_metadata = depset([metadata])),
        DevDistPluginDescriptorInfo(
            plugin_main_module = module_name,
            descriptor = descriptor,
            classpath_descriptor = classpath_descriptor,
            platforms = platforms,
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
    doc = "Patches one plugin's `META-INF/plugin.xml` the way a dev-distribution assembly does.",
    implementation = _dev_dist_plugin_descriptor_impl,
    fragments = ["platform"],
    attrs = {
        "main_module": attr.string(
            doc = "The main JPS module that identifies this descriptor.",
            mandatory = True,
        ),
        "unresolved_descriptor_modules": attr.string_list(
            doc = """The descriptor modules the dev section names but no descriptor target answers.

The macro writes the list. A non-empty list fails analysis and asks for a regeneration of the dev sections.""",
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
        "platform_descriptors": attr.label_keyed_string_dict(
            doc = """The same, for the platform's search scope. No generator writes it; see `plugin_modules`.""",
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
        "refused_content_modules": attr.string_list(
            doc = """The content modules the product's filter refuses. Normally empty.

The assembly drops an optional `<module/>` a `ContentModuleFilter` refuses, and that filter reads the JPS project model.
The survivors are `descriptor`'s own `<content>`, which this action already declares, so only the refusals are stated
here. A refusal that reaches no `<module/>` fails the action.""",
        ),
        "separate_jar": attr.string_list(
            doc = "Which content module's embedded descriptor takes `separate-jar=\"true\"`. A deviation, normally empty.",
        ),
        "plugin_modules": attr.string_list(
            doc = """The plugin's own descriptor search scope, by JPS module name.

No generator writes it. A descriptor a plugin reads is declared by label, so the scope decides nothing but the answer
of the executor's `copyWithExtraSearchPath`, which reads one module name. Both executors keep the option, because the
port is field for field and a scope is what the platform's own resolver takes.""",
        ),
        "platform_modules": attr.string_list(
            doc = "The platform's descriptor search scope, by JPS module name. No generator writes it; see `plugin_modules`.",
        ),
        "embed_content_modules": attr.bool(
            default = True,
            doc = "False for a layout that scrambles paths: it embeds no content module descriptor, and the assembly agrees.",
        ),
        "exact_version": attr.bool(doc = "`PluginLayout.pluginCompatibilityExactVersion`."),
        "retain_product_descriptor": attr.bool(doc = "`PluginLayout.retainProductDescriptorForBundledPlugin`."),
        "directory_name": attr.string(doc = "The plugin directory, when the layout does not take the derived one."),
        "main_jar_name": attr.string(doc = "The main jar, when the layout does not take the derived one."),
        "_build_number_file": attr.label(
            allow_single_file = True,
            default = Label("@community//:build.txt"),
            doc = "Exactly `ij_plugin._default_ide_build_number_file`: the build number is a declared file, never a path a tool computes.",
        ),
        "_writer": attr.label(
            doc = """The Go executor.

ADR 0006 puts the executors in Go, and a descriptor feeds every plugin main jar, so a JVM action for it sits on the
build's critical path.""",
            default = Label("//build/plugin-descriptor-writer:plugin-descriptor-writer"),
            executable = True,
            cfg = "exec",
        ),
        "_product_info": attr.label(
            doc = """The product the stamps come from, as a flag rather than three attributes.

The default states nothing, so a leaf reached by no product's set target fails at analysis. See `DevDistProductInfo`.""",
            default = Label("//build:dev_dist_product_info"),
            providers = [DevDistProductInfo],
        ),
    },
)

# The operating-system tokens `HOST_PLATFORMS` spells, and the architecture tokens. Derived from that list and never
# written again, because a second spelling of either set would let the two disagree.
_HOST_PLATFORM_OPERATING_SYSTEMS = sorted({platform_parts(platform).os: None for platform in HOST_PLATFORMS})

_HOST_PLATFORM_ARCHITECTURES = sorted({platform_parts(platform).arch: None for platform in HOST_PLATFORMS})

# What a descriptor target's name ends in. One owner, because `dev_dist_plugin_descriptor_target_name` writes it and
# `dev_dist_plugin_descriptor_entry_of` strips it.
_DEV_DESCRIPTOR_SUFFIX = "_dev_descriptor"

# Every variant a descriptor target's name can carry. `HOST_PLATFORMS` comes first, because `darwin_aarch64` ends in
# `aarch64` and the shorter token must not claim it. The operating-system tokens and the architecture tokens are
# disjoint, so their order between themselves says nothing.
_DEV_DESCRIPTOR_VARIANTS = HOST_PLATFORMS + _HOST_PLATFORM_OPERATING_SYSTEMS + _HOST_PLATFORM_ARCHITECTURES

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

def dev_dist_plugin_descriptor_os_arch_stamps(marketplace_names, variant):
    """The marker row and the version suffix a one-platform layout variant takes.

    Both are mechanical. The row states the `<!-- OS/ARCH-DEPENDENCY-PLACEHOLDER -->` replacement, whose text
    `osArchDescriptorMarker` owns, and a plugin bundled for one platform states that platform in its version. So the
    plan states neither, and `osArchStamps` of `devDistPluginDescriptorPlan.kt` verifies the layout against the same
    pair. A layout that disagrees is held out by name, which is why deriving both here is safe.

    Args:
        marketplace_names: `product.marketplace_names`. `OsFamily.osId` and `JvmArchitecture.marketplaceName`, keyed by
            the token `HOST_PLATFORMS` spells. Generated, because no rule can read an enum.
        variant: the layout variant.

    Returns:
        `struct(markers, version_suffix)`, or `None` for a variant that names no single platform.
    """
    if variant not in HOST_PLATFORMS:
        return None
    parts = platform_parts(variant)
    os = marketplace_names.get(parts.os)
    arch = marketplace_names.get(parts.arch)
    if os == None or arch == None:
        fail("dev_dist_plugin_descriptor: marketplace_names does not answer both '%s' and '%s' of variant '%s'" % (
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

def dev_dist_plugin_descriptor_entry_of(label):
    """The plugin and the layout variant `dev_dist_plugin_descriptor_target_name` was called with, read back from what
    it returned.

    The inverse of that function, and it takes a label as well as a bare name, because what names a descriptor target
    names it as `":<name>"`. The plan states one label per plan entry, and both readers of that list need the plugin and
    its variant, so the label carries the pair and no second list has to.

    Only a variant this repository knows is stripped, and the target-name suffix is required. A name that ends in
    neither is a name this function did not write, and it fails here rather than answering with a plugin that does not
    exist.

    A main module whose own name ends in `_x64` or another variant token therefore parses as a shorter plugin with a
    variant. A JPS module name states dots and no underscore of that shape, and the leaf reads its `module_name` out of
    its own module target, so such a name would not match its leaf.

    Args:
        label: a descriptor target's label or its bare name.

    Returns:
        `struct(main_module, variant)`. `variant` is empty for a plugin whose one layout serves every platform.
    """
    name = label.rpartition(":")[2]
    if not name.endswith(_DEV_DESCRIPTOR_SUFFIX):
        fail("dev_dist_plugin_descriptor: '%s' names no descriptor target, because a name of one ends in '%s'" % (
            label,
            _DEV_DESCRIPTOR_SUFFIX,
        ))
    stem = name[:-len(_DEV_DESCRIPTOR_SUFFIX)]
    entry_variant = ""
    for variant in _DEV_DESCRIPTOR_VARIANTS:
        if stem.endswith("_" + variant):
            stem = stem[:-(len(variant) + 1)]
            entry_variant = variant
            break
    if not stem:
        fail("dev_dist_plugin_descriptor: '%s' names no plugin, because everything before '%s' is a variant" % (
            label,
            _DEV_DESCRIPTOR_SUFFIX,
        ))
    return struct(main_module = stem, variant = entry_variant)

def _descriptor_key(main_module, variant = ""):
    """The key every deviation table of the plan is keyed by - `("intellij.jcef.plugin", "darwin_aarch64")`.

    A deviation is a fact about one (plugin, variant) and not about the plugin: two variants state different markers, and
    the OS-specific ones state different versions. `planEntryKey` composes the same key on the generator side.

    Args:
        main_module: the plugin's main JPS module.
        variant: the layout variant, or empty for a plugin whose one layout serves every platform.
    """
    if variant:
        return main_module + "/" + variant
    return main_module

def dev_dist_plugin_descriptor(
        main_module,
        descriptor = "",
        descriptor_module = "",
        descriptor_jar = None,
        descriptor_entry = "",
        variant = "",
        tags = [],
        visibility = ["//visibility:public"],
        **kwargs):
    """`_dev_dist_plugin_descriptor` with what every plugin says the same way filled in.

    Three things the macro derives rather than have them restated once per plugin. `name` comes from `main_module`, the
    way `content_module_jar` derives its own. The descriptor's label comes from the module target's own package, which
    is where `exportDescriptorFiles` put the `exports_files` entry. And `manual` is added, for `content_module_jar`'s
    reason: these are per-plugin targets of a measurement, and `bazel build //...` must not run all of them.

    Args:
        main_module: the plugin's main JPS module, which names the target.
        descriptor_module: The main target label, used only to resolve the descriptor's package.
        descriptor: the descriptor's path inside that module's Bazel package, normally `<resource root>/META-INF/plugin.xml`.
        variant: the layout variant, which joins the target's name and the output's directory.
        tags: extra tags. `manual` is added.
        visibility: public by default.
        **kwargs: see `_dev_dist_plugin_descriptor`.
    """
    if bool(descriptor) == bool(descriptor_jar):
        fail("dev_dist_plugin_descriptor requires exactly one descriptor source")
    source = descriptor_module.rpartition(":")[0] + ":" + descriptor if descriptor else None
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

# What `fragment_reads` states. `all` means every fragment reads the produced descriptor of every plugin the plan
# expresses, which is the state of every product today. `none` puts every fragment back on the computed path, and it is
# the second arm of the two-arm measurement.
_FRAGMENT_READS_MODES = ["all", "none"]

def _descriptor_fragment_reads_mode(product):
    """The plan's `fragment_reads` mode, refused when it states neither `all` nor `none`.

    The one owner of that refusal, because a mode the plan misspells would otherwise read as `none` and take every
    fragment off the produced descriptor without saying so.

    Args:
        product: one product's entry of `DEV_DIST_PLUGIN_DESCRIPTORS`.
    """
    if product.fragment_reads not in _FRAGMENT_READS_MODES:
        fail("dev_dist_plugin_descriptor: fragment_reads is '%s', and it states one of %s" % (
            product.fragment_reads,
            _FRAGMENT_READS_MODES,
        ))
    return product.fragment_reads

def dev_dist_plugin_descriptors(name, product, platform_prefix, visibility = ["//visibility:public"]):
    """One group over every descriptor target of a product, and one `dev_dist_product_info`.

    The group is one product's whole population. The population is a single-file toggle: with `descriptor_targets = []`
    the group still resolves and names no descriptor.

    Args:
        name: the group target's name.
        product: one product's entry of `DEV_DIST_PLUGIN_DESCRIPTORS`.
        platform_prefix: the product's platform prefix, which names the product info target.
        visibility: the group's and the product info's visibility.
    """

    # The product's own scalars, as the one target every descriptor of this product is configured with.
    product_info = platform_prefix + "_product_info"
    dev_dist_product_info(
        name = product_info,
        eap = product.eap,
        marketplace_names = product.marketplace_names,
        release_date = product.release_date,
        release_version = product.release_version,
        platform_prefix = platform_prefix,
        # `manual`, for the reason every other target of this package is: `bazel build //...` must run no descriptor
        # action. This one runs none and declares no output, and the tag keeps the package's rule one sentence.
        tags = ["manual"],
        visibility = visibility,
    )

    # The plugin and the variant of each label, read out of the label itself. Two labels that name one plan entry are
    # refused here, where the plan is read: the set below refuses them as well, and its failure speaks about a platform
    # rather than about a plan that states one leaf twice.
    entry_by_key = {}
    entries = []
    plugin_names = {}
    for label in product.descriptor_targets:
        entry = dev_dist_plugin_descriptor_entry_of(label)
        key = _descriptor_key(entry.main_module, entry.variant)
        earlier = entry_by_key.get(key)
        if earlier != None:
            fail("dev_dist_plugin_descriptors: both %s and %s name the descriptor of '%s'" % (earlier, label, key))
        entry_by_key[key] = label
        entries.append((label, entry))
        plugin_names[entry.main_module] = None

    # The switch, checked over the whole plan. No rule reads it today, so this check is what keeps a misspelled mode
    # and a stale opt-out name from passing as the default.
    if _descriptor_fragment_reads_mode(product) == "none" and product.fragment_reads_opt_out:
        fail("dev_dist_plugin_descriptors: fragment_reads is 'none', so an opt-out list restates it")
    for main_module in product.fragment_reads_opt_out:
        if main_module not in plugin_names:
            fail("dev_dist_plugin_descriptors: fragment_reads_opt_out names '%s', which is not in the population" % main_module)

    dev_dist_plugin_descriptor_group(
        name = name,
        descriptors = product.descriptor_targets,
        product_info = ":" + product_info,
        tags = ["manual"],
        visibility = visibility,
    )
