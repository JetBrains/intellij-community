"""Produces the generated entries of the application-info module jar of a product from declared files.

`dev_dist_product_descriptor` resolves the product descriptor, `META-INF/plugin.xml` or `META-INF/<prefix>Plugin.xml`, and
the prefix of `plugins/plugin-classpath.txt`. In the content form, the descriptor writer composes the product content from
the rows that the macro derives from the module-set table, see `dev_dist_product_content.bzl`.
`dev_dist_product_application_info` replaces the product markers of `idea/<prefix>ApplicationInfo.xml`. Only a product
with `appInfoXmlReplacements` has this target. The generator writes the targets into `//build/dev-dist-product-descriptors`,
and `dev_dist_platform_jar` patches their outputs into the jar.
"""

load("@rules_java//java:defs.bzl", "JavaInfo")
load(":dev_dist_embedded_product_descriptor.bzl", "add_product_content_args", "declared_descriptors")
load(":dev_dist_product_content.bzl", "product_content_attributes")

def _dev_dist_product_descriptor_impl(ctx):
    source = ctx.file.source
    declared = declared_descriptors(ctx)
    output = ctx.actions.declare_file(ctx.label.name + ".xml")
    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add("--product-descriptor")
    args.add(output, format = "--out=%s")
    if source:
        args.add(source, format = "--source=%s")
    args.add(ctx.attr.main_module, format = "--main-module=%s")
    if not source:
        add_product_content_args(ctx, args)
    args.add_all(declared.descriptor_args, format_each = "--descriptor=%s")
    args.add_all(declared.descriptor_jar_args, format_each = "--descriptor-in-jar=%s")
    args.add_all(ctx.attr.refused_content_modules, format_each = "--refused-content-module=%s")
    args.add_all(ctx.attr.scrambled_content_modules, format_each = "--scrambled-content-module=%s")
    args.add(ctx.outputs.plugin_classpath_prefix, format = "--plugin-classpath-prefix=%s")
    args.add(ctx.outputs.classpath_descriptor, format = "--classpath-descriptor=%s")
    ctx.actions.run(
        mnemonic = "DevDistProductDescriptor",
        inputs = depset(([source] if source else []) + declared.inputs),
        outputs = [output, ctx.outputs.plugin_classpath_prefix, ctx.outputs.classpath_descriptor],
        executable = ctx.executable._resolver,
        arguments = [args],
        progress_message = "Resolving the product descriptor of %{label}",
    )
    return [DefaultInfo(files = depset([output]))]

_dev_dist_product_descriptor = rule(
    doc = """Resolves the product descriptor, and writes the prefix of `plugins/plugin-classpath.txt` from it.

The prefix is a predeclared output, `<name>.plugin-classpath-prefix`, so a component names it by its label. So is the
descriptor of that prefix alone, `<name>.classpath.xml`: the core plugin descriptor of the runtime module repository.""",
    implementation = _dev_dist_product_descriptor_impl,
    outputs = {
        "plugin_classpath_prefix": "%{name}.plugin-classpath-prefix",
        "classpath_descriptor": "%{name}.classpath.xml",
    },
    attrs = {
        # TRANSITION(product content attributes; remove after the generator run)
        "source": attr.label(
            allow_single_file = [".xml"],
            doc = """The generated Product DSL content of the product, with the module sets and the deprecated includes inlined.

            The content attributes replace it. A target states either this or the content attributes.""",
        ),
        "aliases": attr.string_list(
            doc = "The plugin ids of the `<module value>` rows: the product aliases and the set aliases, sorted.",
        ),
        "includes": attr.string_list(
            doc = "The deprecated includes as `<kind>=<href>`, in order. The kind is `required` or `optional`.",
        ),
        "content_rows": attr.string_list(
            doc = "The rows of the module-set content block in walk order: `name[;loading=<rule>][;required-if-available=<module>]`.",
        ),
        "additional_rows": attr.string_list(
            doc = "The rows of the additional modules in order. A row without a namespace adds `;private` after the name.",
        ),
        "main_module": attr.string(
            mandatory = True,
            doc = "The application-info module, which names the product in a failure.",
        ),
        "descriptors": attr.label_keyed_string_dict(
            allow_files = [".xml"],
            doc = "Exact descriptor inputs keyed by target and valued by resolver load path.",
        ),
        "library_descriptors": attr.label_keyed_string_dict(
            providers = [[JavaInfo]],
            doc = "Ordered runtime jar containers valued by space-separated resolver load paths.",
        ),
        "refused_content_modules": attr.string_list(
            doc = "The optional content modules that the content module filter of the product refuses.",
        ),
        "scrambled_content_modules": attr.string_list(
            doc = "The content modules that the product scrambles. Their `<module/>` elements get no descriptor.",
        ),
        "_resolver": attr.label(
            default = "//platform/build-scripts/bazel-rules:plugin_descriptor_writer",
            executable = True,
            cfg = "exec",
        ),
    },
)

def _dev_dist_product_application_info_impl(ctx):
    output = ctx.actions.declare_file(ctx.label.name + ".xml")
    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add("--stamp-application-info")
    args.add(output, format = "--out=%s")
    args.add(ctx.file.source, format = "--source=%s")
    args.add_all(ctx.attr.replacements, format_each = "--replacement=%s")
    ctx.actions.run(
        mnemonic = "DevDistProductApplicationInfo",
        inputs = [ctx.file.source],
        outputs = [output],
        executable = ctx.executable._resolver,
        arguments = [args],
        progress_message = "Replacing the product markers of the application info of %{label}",
    )
    return [DefaultInfo(files = depset([output]))]

_dev_dist_product_application_info = rule(
    doc = """Replaces the markers of `ProductProperties.appInfoXmlReplacements` in the application info, and no other marker.

The action stamps no build number and no build date. The run time reads the build number from `build.txt`.""",
    implementation = _dev_dist_product_application_info_impl,
    attrs = {
        "source": attr.label(
            mandatory = True,
            allow_single_file = [".xml"],
            doc = "The `idea/<prefix>ApplicationInfo.xml` of the application-info module, with its markers.",
        ),
        "replacements": attr.string_list(
            mandatory = True,
            allow_empty = False,
            doc = "`ProductProperties.appInfoXmlReplacements` as `<key>=<value>`, in their order.",
        ),
        "_resolver": attr.label(
            default = "//platform/build-scripts/bazel-rules:plugin_descriptor_writer",
            executable = True,
            cfg = "exec",
        ),
    },
)

def dev_dist_product_descriptor(
        name,
        source = None,
        aliases = [],
        includes = {},
        module_sets = [],
        module_set_table = {},
        loading_overrides = {},
        content_modules = [],
        private_content_modules = [],
        content_module_loading = {},
        content_module_required_if_available = {},
        descriptor_index = {},
        descriptors = {},
        tags = [],
        visibility = ["//visibility:public"],
        **kwargs):
    """Declares one product descriptor action. The target is `manual`, like every packing target.

    The content of the product comes from the module-set table, see `product_content_rows`. The macro derives the
    `descriptors` row of each set member and additional module that the index knows, less the refused modules. An
    explicit row wins by label and by load path.

    Args:
        name: the target name.
        source: the generated content of the product. The content attributes replace it.
        aliases: the product aliases.
        includes: the deprecated includes, href to `required` or `optional`, in order.
        module_sets: the top-level set names, in DSL order.
        module_set_table: `DEV_DIST_MODULE_SETS` of the half.
        loading_overrides: the loading rule of a member, which a top-level set states for its own members.
        content_modules: the additional modules, in DSL order.
        private_content_modules: the additional modules without a namespace.
        content_module_loading: the loading rule of an additional module.
        content_module_required_if_available: the `required-if-available` module of an additional module.
        descriptor_index: the conventional descriptor label of each module, from the JPS bridge of this half.
        descriptors: the explicit descriptor rows, keyed by label and valued by load path.
        tags: extra tags. `manual` is added.
        visibility: public by default.
        **kwargs: see `_dev_dist_product_descriptor`.
    """

    # TRANSITION(product content attributes; remove after the generator run): `source` and the content form are exclusive.
    attributes = dict(kwargs)
    attributes.update(product_content_attributes(
        caller = "dev_dist_product_descriptor",
        source = source,
        aliases = aliases,
        includes = includes,
        module_sets = module_sets,
        module_set_table = module_set_table,
        loading_overrides = loading_overrides,
        content_modules = content_modules,
        private_content_modules = private_content_modules,
        content_module_loading = content_module_loading,
        content_module_required_if_available = content_module_required_if_available,
        descriptor_index = descriptor_index,
        descriptors = descriptors,
        refused_content_modules = kwargs.get("refused_content_modules", []),
    ))
    _dev_dist_product_descriptor(name = name, tags = tags + ["manual"], visibility = visibility, **attributes)

def dev_dist_product_application_info(name, tags = [], visibility = ["//visibility:public"], **kwargs):
    """Declares one application info marker action. The target is `manual`, like every packing target."""
    _dev_dist_product_application_info(name = name, tags = tags + ["manual"], visibility = visibility, **kwargs)
