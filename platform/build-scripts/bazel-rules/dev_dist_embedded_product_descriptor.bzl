"""Resolves one embedded product descriptor from declared XML files.

The descriptor writer composes the content from the rows that the macro derives from the module-set table of the half.
See `dev_dist_product_content.bzl`.
"""

load("@rules_java//java:defs.bzl", "JavaInfo")
load(":content_module_jar.bzl", "library_entries")
load(":dev_dist_product_content.bzl", "product_content_attributes")

def dev_dist_embedded_product_descriptor_target_name(main_module, product = None):
    """Returns the target name for a plugin's embedded product descriptor.

    The baseline product keeps the unsuffixed name. The home of another class appends `_<product>`, so one package
    holds one helper per class. A class is the products whose embedded descriptor has one text, and its home is its
    first product. The other products of the class read the helper of the home.
    """
    name = main_module + "_dev_embedded_product_descriptor"
    return name if not product else name + "_" + product

def declared_descriptors(ctx):
    """The `descriptors` and `library_descriptors` attributes as action inputs and writer arguments.

    One answer per load path: a load path that two declarations answer fails the analysis. The product descriptor rule
    shares this with the embedded product descriptor rule.

    Args:
        ctx: the rule context. The rule declares `descriptors` and `library_descriptors`.

    Returns:
        `struct(inputs, descriptor_args, descriptor_jar_args)`.
    """
    inputs = []
    answered_by = {}
    descriptor_args = []
    descriptor_jar_args = []
    for target, load_path in ctx.attr.descriptors.items():
        files = target.files.to_list()
        if len(files) != 1:
            fail("%s provides %d files. An embedded descriptor input must provide one" % (target.label, len(files)), attr = "descriptors")
        if load_path in answered_by:
            fail("%s and %s both provide '%s'" % (answered_by[load_path], target.label, load_path), attr = "descriptors")
        answered_by[load_path] = target.label
        inputs.append(files[0])
        descriptor_args.append(load_path + "=" + files[0].path)
    for container, load_paths in ctx.attr.library_descriptors.items():
        jars = library_entries(ctx, [container], attr_name = "library_descriptors")[0].jars
        for load_path in load_paths.split(" "):
            if load_path in answered_by:
                fail("%s and %s both provide '%s'" % (answered_by[load_path], container.label, load_path), attr = "library_descriptors")
            answered_by[load_path] = container.label
            for jar in jars:
                descriptor_jar_args.append(load_path + "=" + jar.path)
        inputs.extend(jars)
    return struct(inputs = inputs, descriptor_args = descriptor_args, descriptor_jar_args = descriptor_jar_args)

def add_product_content_args(ctx, args):
    """Adds the content attributes as writer arguments: the aliases, the includes, the set rows, the additional rows.

    The product descriptor rule shares this with the embedded product descriptor rule.

    Args:
        ctx: the rule context. The rule declares `aliases`, `includes`, `content_rows` and `additional_rows`.
        args: the `Args` of the writer action.
    """
    args.add_all(ctx.attr.aliases, format_each = "--alias=%s")
    args.add_all(ctx.attr.includes, format_each = "--include=%s")
    args.add_all(ctx.attr.content_rows, format_each = "--content-module=%s")
    args.add_all(ctx.attr.additional_rows, format_each = "--additional-module=%s")

def _dev_dist_embedded_product_descriptor_impl(ctx):
    declared = declared_descriptors(ctx)
    output = ctx.actions.declare_file(ctx.label.name + ".xml")
    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add("--embedded-product")
    args.add(output, format = "--out=%s")
    add_product_content_args(ctx, args)
    args.add_all(declared.descriptor_args, format_each = "--descriptor=%s")
    args.add_all(declared.descriptor_jar_args, format_each = "--descriptor-in-jar=%s")
    args.add_all(ctx.attr.separate_jar, format_each = "--separate-jar=%s")
    ctx.actions.run(
        mnemonic = "DevDistEmbeddedProductDescriptor",
        inputs = declared.inputs,
        outputs = [output],
        executable = ctx.executable._resolver,
        arguments = [args],
        progress_message = "Resolving the embedded product descriptor of %{label}",
    )
    return [DefaultInfo(files = depset([output]))]

_dev_dist_embedded_product_descriptor = rule(
    implementation = _dev_dist_embedded_product_descriptor_impl,
    attrs = {
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
        "descriptors": attr.label_keyed_string_dict(
            allow_files = [".xml"],
            doc = "Exact descriptor inputs keyed by target and valued by resolver load path.",
        ),
        "library_descriptors": attr.label_keyed_string_dict(
            providers = [[JavaInfo]],
            doc = "Ordered runtime jar containers valued by space-separated resolver load paths.",
        ),
        "separate_jar": attr.string_list(
            doc = "Content modules whose embedded descriptor takes separate-jar=true.",
        ),
        "_resolver": attr.label(
            default = "//platform/build-scripts/bazel-rules:plugin_descriptor_writer",
            executable = True,
            cfg = "exec",
        ),
    },
)

def dev_dist_embedded_product_descriptor(
        main_module,
        product = None,
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
    """Declares one embedded product descriptor action.

    `product` is the `dev-build.json` key of the home of a class other than the baseline class. The baseline product
    omits it and keeps the unsuffixed target name.

    The content comes from the module-set table, as for `dev_dist_product_descriptor`. The macro derives the
    `descriptors` row of each set member and additional module that the index knows. An explicit row wins by label and
    by load path.

    Args:
        main_module: the main module of the plugin, which names the target.
        product: the `dev-build.json` key of the home of the class, or `None` for the baseline class.
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
        **kwargs: see `_dev_dist_embedded_product_descriptor`.
    """
    attributes = dict(kwargs)
    attributes.update(product_content_attributes(
        caller = "dev_dist_embedded_product_descriptor",
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
    ))
    _dev_dist_embedded_product_descriptor(
        name = dev_dist_embedded_product_descriptor_target_name(main_module, product),
        tags = tags + ["manual"],
        visibility = visibility,
        **attributes
    )
