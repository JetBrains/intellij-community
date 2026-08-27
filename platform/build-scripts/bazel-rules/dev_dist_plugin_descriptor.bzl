"""Writes one plugin's patched `META-INF/plugin.xml` in an action of its own.

A dev-distribution fragment computes that text today, inside the assembly that evaluates the whole product layout. This
rule is the other producer: one action per plugin, whose declared inputs are the descriptors the patch reads and whose
output is the text the plugin's main jar receives. It changes no assembly. `./build/dev-dist.cmd descriptors` compares
the two producers byte for byte, which is the only thing that makes a second producer worth having (ADR 0006 rule 2).

Modelled on two neighbours, each for what it already settled. `ij_plugin` for the per-plugin grain and for the build
number arriving as a declared file. `content_module_jar` for the provider, for the `manual` tag and for a packer named
directly rather than pushed in through a flag.

**Remote-cacheable on purpose.** These actions read tens of small XML files, not a platform's gigabytes, so
`_LOCAL_DISK_CACHE_ONLY` of `intellij_dev_dist.bzl` does not apply here and `local` is not set. A cheap hermetic action
that both caches keep is the property ADR 0006 asks for.
"""

load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")

DevDistPluginDescriptorInfo = provider(
    doc = """One plugin's patched descriptor, and the plugin it belongs to.

    A provider rather than a bare `DefaultInfo`, for the reason `prepacked_content_modules` gives: a consumer that names
    a target which produces no descriptor must be refused by Bazel at analysis, not by a reader a whole build later.""",
    fields = {
        "plugin_main_module": "The JPS module whose resources carry the descriptor.",
        "descriptor": "The patched `META-INF/plugin.xml` as a `File`.",
    },
)

def _dev_dist_plugin_descriptor_impl(ctx):
    module_name = ctx.attr.descriptor_module[_KtJvmInfo].module_name
    if not module_name:
        fail("%s is the plugin's main module but states no module name" % ctx.attr.descriptor_module.label, attr = "descriptor_module")

    # Declared first, and it stays first. `_declare_side_output` in `intellij_dev_dist.bzl` states why: a Bazel profile
    # names an action by its primary output, and a side output put ahead of this one re-points that name in silence.
    descriptor = ctx.actions.declare_file(module_name + ".plugin.xml")
    outputs = [descriptor]

    args = ctx.actions.args()
    args.set_param_file_format("multiline")

    # A parameter file, so the request is the action's arguments and not a generated file per plugin. The tool accepts
    # `--flagfile=<path>` and nothing else on its command line.
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add(descriptor, format = "--out=%s")
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

    ctx.actions.run(
        # One mnemonic for every plugin descriptor, so a strategy or an execution-info override reaches all of them.
        mnemonic = "DevDistPluginDescriptor",
        inputs = depset(inputs),
        outputs = outputs,
        executable = ctx.executable._patcher,
        arguments = [args],
        progress_message = "Patching the plugin descriptor of %{label}",
    )
    return [
        DefaultInfo(files = depset([descriptor])),
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
            default = Label("//platform/build-scripts/bazel-rules/dev-dist-plugin-descriptor:dev-dist-plugin-descriptor"),
            executable = True,
            cfg = "exec",
        ),
    },
)

def dev_dist_plugin_descriptor_target_name(descriptor_module):
    """This target's name, from the plugin's main module target - `":xpath"` gives `"xpath_dev_descriptor"`.

    Public because the same name is written in two places that must agree: the target, and whatever names it.
    """
    return descriptor_module.rpartition(":")[2] + "_dev_descriptor"

def dev_dist_plugin_descriptor(descriptor_module, descriptor, tags = [], visibility = ["//visibility:public"], **kwargs):
    """`_dev_dist_plugin_descriptor` with what every plugin says the same way filled in.

    Three things the macro derives rather than have them restated once per plugin. `name` comes from
    `descriptor_module`, the way `dev_dist_plugin_content` and `content_module_jar` derive their own. The descriptor's
    label comes from the module target's own package, which is where `exportDescriptorFiles` put the `exports_files`
    entry. And `manual` is added, for `content_module_jar`'s reason: these are per-plugin targets of a measurement, and
    `bazel build //...` must not run all of them.

    Args:
        descriptor_module: the plugin's main module target - see the rule's own `descriptor_module`.
        descriptor: the descriptor's path inside that module's Bazel package, normally `<resource root>/META-INF/plugin.xml`.
        tags: extra tags. `manual` is added.
        visibility: public by default.
        **kwargs: see `_dev_dist_plugin_descriptor`.
    """
    _dev_dist_plugin_descriptor(
        name = dev_dist_plugin_descriptor_target_name(descriptor_module),
        descriptor_module = descriptor_module,
        descriptor = descriptor_module.rpartition(":")[0] + ":" + descriptor,
        tags = tags + ["manual"],
        visibility = visibility,
        **kwargs
    )

# The plan keys that state a deviation against the population, and the field each one reaches on the rule.
_DEVIATION_FIELDS = [
    "content_modules",
    "no_embedding",
    "exact_version",
    "retain_product_descriptor",
    "directory_name",
    "main_jar_name",
]

def dev_dist_plugin_descriptors(name, product, visibility = ["//visibility:public"]):
    """One `dev_dist_plugin_descriptor` target per plugin the plan names, and a `filegroup` over all of them.

    Args:
        name: the `filegroup`'s name. `./build/dev-dist.cmd descriptors` names it.
        product: one product's entry of `DEV_DIST_PLUGIN_DESCRIPTORS`.
        visibility: the `filegroup`'s visibility. The per-plugin targets are public, like every other target here.
    """
    population = {plugin.main_module: None for plugin in product.plugins}

    # A deviation key that reaches no plugin is a typo that would otherwise pass as a default. Checked over the whole
    # plan, because the rule sees one plugin and cannot see a key that reached none.
    for field in _DEVIATION_FIELDS:
        for main_module in getattr(product, field):
            if main_module not in population:
                fail("dev_dist_plugin_descriptors: %s names '%s', which is not in the population" % (field, main_module))

    for plugin in product.plugins:
        dev_dist_plugin_descriptor(
            descriptor_module = plugin.module,
            descriptor = plugin.descriptor,
            content_modules = product.content_modules.get(plugin.main_module, []),
            directory_name = product.directory_name.get(plugin.main_module, ""),
            eap = product.eap,
            embed_content_modules = plugin.main_module not in product.no_embedding,
            exact_version = plugin.main_module in product.exact_version,
            main_jar_name = product.main_jar_name.get(plugin.main_module, ""),
            release_date = product.release_date,
            release_version = product.release_version,
            retain_product_descriptor = plugin.main_module in product.retain_product_descriptor,
        )

    native.filegroup(
        name = name,
        srcs = [":" + dev_dist_plugin_descriptor_target_name(plugin.module) for plugin in product.plugins],
        tags = ["manual"],
        visibility = visibility,
    )
