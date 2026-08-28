"""Writes one plugin's patched `META-INF/plugin.xml` in an action of its own.

A dev-distribution fragment computes that text today, inside the assembly that evaluates the whole product layout. This
rule is the other producer: one action per plugin, whose declared inputs are the descriptors the patch reads and whose
output is the text the plugin's main jar receives. Every fragment of the product now reads that output instead of
computing it, so the byte comparison of the two producers is `./build/dev-dist.cmd descriptors --two-producer`, which
declares both producers inside this rule. A second producer is worth having only while something compares the two
(ADR 0006 rule 2).

Modelled on two neighbours, each for what it already settled. `ij_plugin` for the per-plugin grain and for the build
number arriving as a declared file. `content_module_jar` for the provider, for the `manual` tag and for a packer named
directly rather than pushed in through a flag.

**Remote-cacheable on purpose.** These actions read tens of small XML files, not a platform's gigabytes, so
`_LOCAL_DISK_CACHE_ONLY` of `intellij_dev_dist.bzl` does not apply here and `local` is not set. A cheap hermetic action
that both caches keep is the property ADR 0006 asks for.
"""

load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load("//build:dev_launch_dependencies.bzl", "HOST_PLATFORMS")

DevDistPluginDescriptorInfo = provider(
    doc = """One plugin's patched descriptor, and the plugin it belongs to.

    A provider rather than a bare `DefaultInfo`, for the reason `prepacked_content_modules` gives: a consumer that names
    a target which produces no descriptor must be refused by Bazel at analysis, not by a reader a whole build later.""",
    fields = {
        "plugin_main_module": "The JPS module whose resources carry the descriptor.",
        "descriptor": "The patched `META-INF/plugin.xml` as a `File`.",
    },
)

# The manifest key a fragment reads a produced descriptor under. Its own namespace: every other key of the input
# manifest is a Bazel label string, so it starts with `@` or `//`, and a module jar's key also ends in `.jar`. This one
# starts with a word and holds no `//`, so it can collide with nothing already there.
#
# Written once here and once in Kotlin, in `BazelBuildInputs.producedPluginDescriptorIfDeclared`. Both spellings name
# each other, because a drift between them reads as "no descriptor was declared" and the patch then runs as before.
DEV_DIST_DESCRIPTOR_KEY_PREFIX = "dev-dist-descriptor:"

DevDistPluginDescriptorSetInfo = provider(
    doc = """The produced descriptors of one fragment of one product.

    A set target and not a `label_list` on the fragment, for two reasons the plan states. A descriptor target is
    per (plugin, product), and most plugins have their main module in `@community//` while these targets are
    root-repository ones - so the plugin's own package cannot name its own descriptor target. And `//build` cannot
    compute which fragment lays out which plugin: the generated plan holds that partition, and it lives in this
    package.""",
    fields = {
        "descriptors": "A depset of `struct(plugin_main_module, descriptor)`, one per plugin the fragment patches.",
    },
)

def _dev_dist_plugin_descriptor_set_impl(ctx):
    records = []
    seen = {}
    for target in ctx.attr.descriptors:
        main_module = target[DevDistPluginDescriptorInfo].plugin_main_module
        earlier = seen.get(main_module)
        if earlier != None:
            # A fragment reads a produced descriptor under one manifest key per plugin, so two variants reaching one
            # platform would make the key ambiguous. Refused here, where both are in one list.
            fail("%s and %s both produce the descriptor of '%s'" % (earlier, target.label, main_module), attr = "descriptors")
        seen[main_module] = target.label
        records.append(struct(
            plugin_main_module = main_module,
            descriptor = target[DevDistPluginDescriptorInfo].descriptor,
        ))
    return [DevDistPluginDescriptorSetInfo(descriptors = depset(records))]

_dev_dist_plugin_descriptor_set = rule(
    doc = "One fragment's produced plugin descriptors, as the one label a fragment declares.",
    implementation = _dev_dist_plugin_descriptor_set_impl,
    attrs = {
        "descriptors": attr.label_list(
            doc = """The `dev_dist_plugin_descriptor` targets of the plugins this fragment lays out.

The provider gate is the whole check, for `prepacked_content_modules`' reason: a target that produces no descriptor has
no plugin to name, and Bazel must refuse it at analysis rather than a reader a whole build later.""",
            providers = [DevDistPluginDescriptorInfo],
        ),
    },
)

def dev_dist_plugin_descriptor_set_target_name(platform_prefix, fragment_name, platform):
    """This set's target name - `("idea", "plugins_rest", "darwin_aarch64")`.

    Per platform, because a plugin whose descriptor differs by operating system or architecture has one target per
    variant and a fragment must be handed the one its own platform takes. A fragment built for the host selects among
    these sets; one built for a named target platform names one directly.

    Public because two packages write it: this one declares the target, and `//build` names it on the fragment. It is
    spelled the way `dev_dist_content_sets.bzl` spells a content set, so the two sets of one fragment read alike.

    Args:
        platform_prefix: the product's platform prefix.
        fragment_name: the fragment name, `plugins_` included.
        platform: a `HOST_PLATFORMS` entry of `dev_launch_dependencies.bzl`.
    """
    return "%s_%s_%s_descriptors" % (platform_prefix, fragment_name, platform)

def _descriptor_request(ctx, module_name, output):
    """One (plugin, layout variant) request as a parameter file, and the files it reads.

    Built once per producer, because the only field that differs between the two is `--out`. Both binaries take this
    exact spelling, which is what makes the reference run a comparison of two implementations over one request.

    Args:
        ctx: the rule context.
        module_name: the plugin's main JPS module.
        output: the `File` this producer writes.

    Returns:
        A `(Args, [File])` pair: the request, and every file it names.
    """
    args = ctx.actions.args()
    args.set_param_file_format("multiline")

    # A parameter file, so the request is the action's arguments and not a generated file per plugin. Both tools accept
    # `--flagfile=<path>` and nothing else on their command line.
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add(output, format = "--out=%s")
    args.add(module_name, format = "--main-module=%s")
    args.add(ctx.file.descriptor, format = "--source=%s")
    args.add(ctx.file._build_number_file, format = "--build-number-file=%s")
    args.add(ctx.attr.build_date_seconds, format = "--build-date-seconds=%s")
    args.add(ctx.attr.release_date, format = "--release-date=%s")
    args.add(ctx.attr.release_version, format = "--release-version=%s")
    args.add("--eap=" + str(ctx.attr.eap).lower())
    args.add("--exact-version=" + str(ctx.attr.exact_version).lower())
    args.add("--retain-product-descriptor=" + str(ctx.attr.retain_product_descriptor).lower())
    args.add("--embed-content-modules=" + str(ctx.attr.embed_content_modules).lower())
    if ctx.attr.directory_name:
        args.add(ctx.attr.directory_name, format = "--directory-name=%s")
    if ctx.attr.main_jar_name:
        args.add(ctx.attr.main_jar_name, format = "--main-jar-name=%s")
    if ctx.attr.version_suffix:
        args.add(ctx.attr.version_suffix, format = "--version-suffix=%s")
    args.add_all(ctx.attr.markers, format_each = "--marker=%s")
    args.add_all(ctx.attr.content_modules, format_each = "--content-module=%s")
    args.add_all(ctx.attr.separate_jar, format_each = "--separate-jar=%s")
    args.add_all(ctx.attr.plugin_modules, format_each = "--plugin-module=%s")
    args.add_all(ctx.attr.platform_modules, format_each = "--platform-module=%s")

    inputs = [ctx.file.descriptor, ctx.file._build_number_file]
    for label, load_path in ctx.attr.descriptors.items():
        files = label.files.to_list()
        if len(files) != 1:
            fail("%s declares %d files, and a descriptor must name exactly one" % (label.label, len(files)), attr = "descriptors")
        args.add("--plugin-descriptor=" + load_path + "=" + files[0].path)
        inputs.append(files[0])
    for label, load_path in ctx.attr.platform_descriptors.items():
        files = label.files.to_list()
        if len(files) != 1:
            fail("%s declares %d files, and a descriptor must name exactly one" % (label.label, len(files)), attr = "platform_descriptors")
        args.add("--platform-descriptor=" + load_path + "=" + files[0].path)
        inputs.append(files[0])
    for label, load_path in ctx.attr.library_descriptors.items():
        files = label.files.to_list()
        if len(files) != 1:
            fail("%s declares %d files, and a library jar must name exactly one" % (label.label, len(files)), attr = "library_descriptors")
        args.add("--plugin-descriptor-in-jar=" + load_path + "=" + files[0].path)
        inputs.append(files[0])
    return args, inputs

def _dev_dist_plugin_descriptor_impl(ctx):
    module_name = ctx.attr.descriptor_module[_KtJvmInfo].module_name
    if not module_name:
        fail("%s is the plugin's main module but states no module name" % ctx.attr.descriptor_module.label, attr = "descriptor_module")

    # The variant's own directory, so two variants of one plugin never collide and the file's own name stays the main
    # module. `./build/dev-dist.cmd descriptors` joins the artifact by that name, and `--two-producer` pairs the two
    # producers by the whole path - which is why the reference file below lands in the same directory.
    directory = ctx.attr.variant + "/" if ctx.attr.variant else ""
    descriptor = ctx.actions.declare_file(directory + module_name + ".plugin.xml")
    args, inputs = _descriptor_request(ctx, module_name, descriptor)
    ctx.actions.run(
        # One mnemonic for every plugin descriptor, so a strategy or an execution-info override reaches all of them.
        mnemonic = "DevDistPluginDescriptor",
        inputs = depset(inputs),
        outputs = [descriptor],
        executable = ctx.executable._patcher,
        arguments = [args],
        progress_message = "Patching the plugin descriptor of %{label}",
    )

    # The reference producer, and nothing at all when the flag is off. Two producers of one text is what ADR 0006 rule 2
    # asks for, and the Go executor above is the one a distribution reads.
    #
    # **A second action, not a side output of the first.** A side output is written by the same tool, and the whole
    # point here is a different implementation over the same request. The first action's key, arguments and declared
    # outputs are therefore untouched either way.
    files = [descriptor]
    if ctx.attr._dev_dist_descriptor_reference[BuildSettingInfo].value:
        reference = ctx.actions.declare_file(directory + module_name + ".plugin.reference.xml")
        reference_args, reference_inputs = _descriptor_request(ctx, module_name, reference)
        ctx.actions.run(
            mnemonic = "DevDistPluginDescriptorReference",
            inputs = depset(reference_inputs),
            outputs = [reference],
            executable = ctx.executable._reference_patcher,
            arguments = [reference_args],
            progress_message = "Patching the plugin descriptor of %{label} with the JVM reference tool",
        )

        # In `DefaultInfo`, because `./build/dev-dist.cmd descriptors --two-producer` reads the files of the
        # `filegroup` this package declares, and a `filegroup` propagates `DefaultInfo` and no other output group. A
        # fragment reads `DevDistPluginDescriptorInfo.descriptor` instead, so no distribution can pick this file up,
        # and with the flag off there is no file to pick up at all.
        files.append(reference)

    return [
        DefaultInfo(files = depset(files)),
        DevDistPluginDescriptorInfo(plugin_main_module = module_name, descriptor = descriptor),
    ]

_dev_dist_plugin_descriptor = rule(
    doc = "Patches one plugin's `META-INF/plugin.xml` the way a dev-distribution assembly does.",
    implementation = _dev_dist_plugin_descriptor_impl,
    attrs = {
        "descriptor_module": attr.label(
            doc = """The plugin's main module - the one whose resources carry `META-INF/plugin.xml`.

The same key `dev_dist_plugin_content.descriptor_module` uses, so one plugin is named by one target in both places.
`KtJvmInfo.module_name` is what carries the name, which both compile backends set.""",
            mandatory = True,
            providers = [_KtJvmInfo],
        ),
        "descriptor": attr.label(
            doc = """The plugin's own `META-INF/plugin.xml`, as the exported source file.

Declared rather than derived: the file sits under a production resource root, and which directory that is cannot be
computed from a label. Everything else about the plugin is derived - see the macro.""",
            mandatory = True,
            allow_single_file = [".xml"],
        ),
        "descriptors": attr.label_keyed_string_dict(
            doc = """Every other descriptor this plugin's patch can reach, keyed by target and valued by load path.

The load path is what a resolver asks the descriptor cache for. Seeding the cache from these files is what lets the
action run with no JPS project model: `resolveElement` reads the cache before it touches a module output.""",
            allow_files = [".xml"],
        ),
        "platform_descriptors": attr.label_keyed_string_dict(
            doc = "The same, for the platform's search scope.",
            allow_files = [".xml"],
        ),
        "library_descriptors": attr.label_keyed_string_dict(
            doc = """A descriptor no production source root holds, keyed by the jar target and valued by load path.

The load path is also the zip entry, because `toLoadPath` strips the leading `/`. `findFileInModuleLibraryDependencies`
is the assembly's route to such a file, and it belongs to `DescriptorSearchPass.MODULE_OUTPUT` alone. One plugin of this
product needs it: the Kotlin compiler ships `META-INF/analysis-api/analysis-api-fir.xml` inside a library jar.""",
            allow_files = [".jar"],
        ),
        "variant": attr.string(
            doc = """The layout variant, empty for a plugin whose one layout serves every platform.

`darwin_aarch64` for a variant restricted to one operating system and one architecture, `windows` for one restricted to
an operating system alone. It names the output's directory, so two variants of one plugin never collide.""",
        ),
        "markers": attr.string_list(
            doc = """The layout's raw text patch as marker-table rows, in the order it applies them.

Two shapes. `os-arch:<osId>:<marketplaceName>` is the OS and architecture dependency placeholder, whose replacement text
`osArchDescriptorMarker` owns; `marker:<literal>:<replacement>` is a plain replacement. Both producers replace the first
occurrence of a plain string, and an unknown shape fails the action rather than emitting an unpatched text.""",
        ),
        "version_suffix": attr.string(
            doc = """What the layout appends to the IDE build version - `PluginLayout.versionSuffix`.

Empty for a layout that stamps the build version unchanged, which is nearly every one. A per-variant deviation: an
OS-specific plugin states the marketplace operating system and architecture in its version.""",
        ),
        "content_modules": attr.string_list(
            doc = """The plugin's content modules, filtered and in descriptor order.

The assembly drops an optional `<module/>` a `ContentModuleFilter` refuses, and that filter reads the JPS project model.
This states the survivors instead, so the action needs no filter.""",
        ),
        "separate_jar": attr.string_list(
            doc = "Which content module's embedded descriptor takes `separate-jar=\"true\"`. A deviation, normally empty.",
        ),
        "plugin_modules": attr.string_list(doc = "The plugin's own descriptor search scope, by JPS module name."),
        "platform_modules": attr.string_list(doc = "The platform's descriptor search scope, by JPS module name."),
        "embed_content_modules": attr.bool(
            default = True,
            doc = "False for a layout that scrambles paths: it embeds no content module descriptor, and the assembly agrees.",
        ),
        "exact_version": attr.bool(doc = "`PluginLayout.pluginCompatibilityExactVersion`."),
        "retain_product_descriptor": attr.bool(doc = "`PluginLayout.retainProductDescriptorForBundledPlugin`."),
        "eap": attr.bool(doc = "The `eap` attribute of the product's `ApplicationInfo.xml`."),
        "release_date": attr.string(mandatory = True, doc = "`ApplicationInfoProperties.majorReleaseDate`."),
        "release_version": attr.string(mandatory = True, doc = "`ApplicationInfoProperties.releaseVersionForLicensing`."),
        "directory_name": attr.string(doc = "The plugin directory, when the layout does not take the derived one."),
        "main_jar_name": attr.string(doc = "The main jar, when the layout does not take the derived one."),
        "build_date_seconds": attr.string(
            default = "1767225600",  # 2026-01-01T00:00:00Z
            doc = """The build date the `.SNAPSHOT` plugin version suffix becomes.

The same default `intellij_dev_dist_fragment.build_date_seconds` pins, and it has to stay the same value: a fragment and
this action stamp one plugin's version, and a byte comparison of the two is the gate.""",
        ),
        "_build_number_file": attr.label(
            allow_single_file = True,
            default = Label("@community//:build.txt"),
            doc = "Exactly `ij_plugin._default_ide_build_number_file`: the build number is a declared file, never a path a tool computes.",
        ),
        "_patcher": attr.label(
            doc = """The Go executor.

ADR 0006 puts the executors in Go, and a descriptor feeds every plugin main jar, so a JVM action for it sits on the
build's critical path. `_reference_patcher` is the JVM tool it replaced, and it stays as the second producer.""",
            default = Label("//build/plugin-descriptor-patcher:plugin-descriptor-patcher"),
            executable = True,
            cfg = "exec",
        ),
        "_reference_patcher": attr.label(
            doc = """The JVM reference producer, run only under `dev_dist_descriptor_reference`.

It calls `applyPluginDescriptorPatch`, the same body a dev assembly runs. So a byte comparison of its output against
`_patcher`'s is a comparison of two implementations of one request, over one set of declared inputs.""",
            default = Label("//platform/build-scripts/bazel-rules/dev-dist-plugin-descriptor:dev-dist-plugin-descriptor"),
            executable = True,
            cfg = "exec",
        ),
        "_dev_dist_descriptor_reference": attr.label(
            default = Label("//platform/build-scripts/bazel-rules:dev_dist_descriptor_reference"),
            providers = [BuildSettingInfo],
        ),
    },
)

def dev_dist_plugin_descriptor_target_name(main_module, variant = ""):
    """This target's name - `("intellij.xpath", "")` gives `"intellij.xpath_dev_descriptor"`.

    Keyed by the main module and not by the module target's own name, because a target name repeats: several plugins
    keep their main module in a package whose production target is called `plugin`, and two of those would declare one
    target twice. The main module is unique across the product, and it is already the stem of the output file and the
    join key of `./build/dev-dist.cmd descriptors`.

    The variant joins the name where the plugin has one, because a plugin whose descriptor differs by operating system
    or architecture declares one target per variant.

    Public because the same name is written in two places that must agree: the target, and whatever names it.

    Args:
        main_module: the plugin's main JPS module.
        variant: the layout variant, or empty for a plugin whose one layout serves every platform.
    """
    if variant:
        return main_module + "_" + variant + "_dev_descriptor"
    return main_module + "_dev_descriptor"

def dev_dist_plugin_descriptor_key(main_module, variant = ""):
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

def dev_dist_plugin_descriptor(main_module, descriptor_module, descriptor, variant = "", tags = [], visibility = ["//visibility:public"], **kwargs):
    """`_dev_dist_plugin_descriptor` with what every plugin says the same way filled in.

    Three things the macro derives rather than have them restated once per plugin. `name` comes from `main_module`, the
    way `dev_dist_plugin_content` and `content_module_jar` derive their own. The descriptor's label comes from the
    module target's own package, which is where `exportDescriptorFiles` put the `exports_files` entry. And `manual` is
    added, for `content_module_jar`'s reason: these are per-plugin targets of a measurement, and `bazel build //...`
    must not run all of them.

    Args:
        main_module: the plugin's main JPS module, which names the target.
        descriptor_module: the plugin's main module target - see the rule's own `descriptor_module`.
        descriptor: the descriptor's path inside that module's Bazel package, normally `<resource root>/META-INF/plugin.xml`.
        variant: the layout variant, which joins the target's name and the output's directory.
        tags: extra tags. `manual` is added.
        visibility: public by default.
        **kwargs: see `_dev_dist_plugin_descriptor`.
    """
    _dev_dist_plugin_descriptor(
        name = dev_dist_plugin_descriptor_target_name(main_module, variant),
        descriptor_module = descriptor_module,
        descriptor = descriptor_module.rpartition(":")[0] + ":" + descriptor,
        variant = variant,
        tags = tags + ["manual"],
        visibility = visibility,
        **kwargs
    )

# The plan keys that state a deviation against the population, and the field each one reaches on the rule.
_DEVIATION_FIELDS = [
    "content_modules",
    "descriptors",
    "library_descriptors",
    "markers",
    "version_suffix",
    "separate_jar",
    "no_embedding",
    "exact_version",
    "retain_product_descriptor",
    "directory_name",
    "main_jar_name",
]

def _descriptors_by_label(main_module, pairs):
    """The plan's `(load path, label)` pairs as the `descriptors` attribute wants them, keyed by label.

    Keyed by label because that is what makes the file an input. Two load paths answered by one file would collapse
    into one dict entry and the second load path would go missing, so that case fails here rather than at the action.

    Args:
        main_module: the plugin, named in the failure.
        pairs: the plan's list of `(load path, label)`.
    """
    result = {}
    for (load_path, label) in pairs:
        earlier = result.get(label)
        if earlier != None and earlier != load_path:
            fail("dev_dist_plugin_descriptors: '%s' reads %s under both '%s' and '%s'" % (
                main_module,
                label,
                earlier,
                load_path,
            ))
        result[label] = load_path
    return result

def dev_dist_plugin_descriptors(name, product, platform_prefix, visibility = ["//visibility:public"]):
    """One `dev_dist_plugin_descriptor` target per plan entry, a `filegroup` over all of them, and one set target per
    (plugin fragment, platform).

    A set exists for every name `product.fragments` holds and every `HOST_PLATFORMS` entry, and an empty one where the
    plan names no plugin. That is what makes the population a single-file toggle: with `plugins = []` every label
    `//build` names still resolves, and every fragment then declares nothing.

    Args:
        name: the `filegroup`'s name. `./build/dev-dist.cmd descriptors` names it.
        product: one product's entry of `DEV_DIST_PLUGIN_DESCRIPTORS`.
        platform_prefix: the product's platform prefix, which names the set targets.
        visibility: the `filegroup`'s and the sets' visibility. The per-entry targets are public, like every other
            target here.
    """
    population = {dev_dist_plugin_descriptor_key(plugin.main_module, plugin.variant): None for plugin in product.plugins}
    plugin_names = {plugin.main_module: None for plugin in product.plugins}

    # A deviation key that reaches no plan entry is a typo that would otherwise pass as a default. Checked over the whole
    # plan, because the rule sees one entry and cannot see a key that reached none.
    for field in _DEVIATION_FIELDS:
        for key in getattr(product, field):
            if key not in population:
                fail("dev_dist_plugin_descriptors: %s names '%s', which is not in the population" % (field, key))

    # `fragment_reads` is keyed by plugin and not by entry: a fragment reads one descriptor per plugin, and which
    # variant that is follows from the platform it assembles for.
    for main_module in product.fragment_reads:
        if main_module not in plugin_names:
            fail("dev_dist_plugin_descriptors: fragment_reads names '%s', which is not in the population" % main_module)

    for plugin in product.plugins:
        key = dev_dist_plugin_descriptor_key(plugin.main_module, plugin.variant)
        dev_dist_plugin_descriptor(
            main_module = plugin.main_module,
            variant = plugin.variant,
            descriptor_module = plugin.module,
            descriptor = plugin.descriptor,
            content_modules = product.content_modules.get(key, []),
            descriptors = _descriptors_by_label(key, product.descriptors.get(key, [])),
            library_descriptors = _descriptors_by_label(key, product.library_descriptors.get(key, [])),
            markers = product.markers.get(key, []),
            version_suffix = product.version_suffix.get(key, ""),
            directory_name = product.directory_name.get(key, ""),
            eap = product.eap,
            embed_content_modules = key not in product.no_embedding,
            exact_version = key in product.exact_version,
            main_jar_name = product.main_jar_name.get(key, ""),
            release_date = product.release_date,
            release_version = product.release_version,
            retain_product_descriptor = key in product.retain_product_descriptor,
            separate_jar = product.separate_jar.get(key, []),
        )

    native.filegroup(
        name = name,
        srcs = [
            ":" + dev_dist_plugin_descriptor_target_name(plugin.main_module, plugin.variant)
            for plugin in product.plugins
        ],
        tags = ["manual"],
        visibility = visibility,
    )

    # A plugin whose fragment the product does not name would be declared to no fragment, and the analysis test of
    # `//build:idea_dev_plugins_descriptor_declaration_test` would then compare a plan entry against nothing. Refused
    # here, where the plan is read, rather than there.
    #
    # `fragment_reads` and not `plugins`, although the plan states the same set in both today. That list is the switch
    # a two-arm measurement flips: an older copy of the generated file restored in place puts every fragment back on
    # the computed path, and this loop is what reads the switch - see the plan file's header.
    by_fragment_and_platform = {
        fragment: {platform: [] for platform in HOST_PLATFORMS}
        for fragment in product.fragments
    }
    for plugin in product.plugins:
        if plugin.main_module not in product.fragment_reads:
            continue
        if plugin.fragment not in by_fragment_and_platform:
            fail("dev_dist_plugin_descriptors: '%s' names fragment '%s', which is not in %s" % (
                plugin.main_module,
                plugin.fragment,
                product.fragments,
            ))
        by_platform = by_fragment_and_platform[plugin.fragment]
        for platform in plugin.platforms:
            if platform not in by_platform:
                fail("dev_dist_plugin_descriptors: '%s' names platform '%s', which is not in %s" % (
                    plugin.main_module,
                    platform,
                    HOST_PLATFORMS,
                ))
            by_platform[platform].append(
                ":" + dev_dist_plugin_descriptor_target_name(plugin.main_module, plugin.variant),
            )

    for fragment in product.fragments:
        for platform in HOST_PLATFORMS:
            _dev_dist_plugin_descriptor_set(
                name = dev_dist_plugin_descriptor_set_target_name(platform_prefix, fragment, platform),
                descriptors = by_fragment_and_platform[fragment][platform],
                # `manual` for the per-entry targets' reason: a wildcard build must run none of these actions on its
                # own. An explicit dependency still builds them, which is how a fragment that declares a set gets its
                # files.
                tags = ["manual"],
                visibility = visibility,
            )
