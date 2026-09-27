"""Resolves one embedded product descriptor from declared XML files."""

load("@rules_java//java:defs.bzl", "JavaInfo")
load(":content_module_jar.bzl", "library_entries")

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

def _dev_dist_embedded_product_descriptor_impl(ctx):
    source = ctx.file.source
    declared = declared_descriptors(ctx)
    output = ctx.actions.declare_file(ctx.label.name + ".xml")
    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add("--embedded-product")
    args.add(output, format = "--out=%s")
    args.add(source, format = "--source=%s")
    args.add_all(declared.descriptor_args, format_each = "--descriptor=%s")
    args.add_all(declared.descriptor_jar_args, format_each = "--descriptor-in-jar=%s")
    args.add_all(ctx.attr.modules, format_each = "--module=%s")
    args.add_all(ctx.attr.separate_jar, format_each = "--separate-jar=%s")
    ctx.actions.run(
        mnemonic = "DevDistEmbeddedProductDescriptor",
        inputs = depset([source] + declared.inputs),
        outputs = [output],
        executable = ctx.executable._resolver,
        arguments = [args],
        progress_message = "Resolving the embedded product descriptor of %{label}",
    )
    return [DefaultInfo(files = depset([output]))]

_dev_dist_embedded_product_descriptor = rule(
    implementation = _dev_dist_embedded_product_descriptor_impl,
    attrs = {
        "source": attr.label(
            mandatory = True,
            allow_single_file = [".xml"],
            doc = "The exported embedded product descriptor.",
        ),
        "descriptors": attr.label_keyed_string_dict(
            allow_files = [".xml"],
            doc = "Exact descriptor inputs keyed by target and valued by resolver load path.",
        ),
        "library_descriptors": attr.label_keyed_string_dict(
            providers = [[JavaInfo]],
            doc = "Ordered runtime jar containers valued by space-separated resolver load paths.",
        ),
        "modules": attr.string_list(
            doc = "The descriptor search scope by JPS module name.",
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
        source = None,
        product = None,
        tags = [],
        visibility = ["//visibility:public"],
        **kwargs):
    """Declares one embedded product descriptor action.

    `product` is the `dev-build.json` key of the home of a class other than the baseline class. The baseline product
    omits it and keeps the unsuffixed target name.
    """
    if not source:
        fail("dev_dist_embedded_product_descriptor requires a source")
    _dev_dist_embedded_product_descriptor(
        name = dev_dist_embedded_product_descriptor_target_name(main_module, product),
        source = source,
        tags = tags + ["manual"],
        visibility = visibility,
        **kwargs
    )
