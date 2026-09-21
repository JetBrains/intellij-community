"""Resolves one embedded product descriptor from declared XML files."""

load("@rules_java//java:defs.bzl", "JavaInfo")
load(":content_module_jar.bzl", "library_entries")

def dev_dist_embedded_product_descriptor_target_name(main_module, product = None):
    """Returns the target name for a plugin's embedded product descriptor.

    The baseline product keeps the unsuffixed name. A divergent product appends `_<product>` so one package can hold
    one helper per product that packs a CWM frontend of its own.
    """
    name = main_module + "_dev_embedded_product_descriptor"
    return name if not product else name + "_" + product

def _dev_dist_embedded_product_descriptor_impl(ctx):
    source = ctx.file.source
    inputs = [source]
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

    output = ctx.actions.declare_file(ctx.label.name + ".xml")
    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add("--embedded-product")
    args.add(output, format = "--out=%s")
    args.add(source, format = "--source=%s")
    args.add_all(descriptor_args, format_each = "--descriptor=%s")
    args.add_all(descriptor_jar_args, format_each = "--descriptor-in-jar=%s")
    args.add_all(ctx.attr.modules, format_each = "--module=%s")
    args.add_all(ctx.attr.separate_jar, format_each = "--separate-jar=%s")
    ctx.actions.run(
        mnemonic = "DevDistEmbeddedProductDescriptor",
        inputs = depset(inputs),
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
            default = "//build/plugin-descriptor-writer",
            executable = True,
            cfg = "exec",
        ),
    },
)

def dev_dist_embedded_product_descriptor(
        main_module,
        source_module = None,
        source = None,
        product = None,
        tags = [],
        visibility = ["//visibility:public"],
        **kwargs):
    """Declares one embedded product descriptor action.

    `product` is the `dev-build.json` key of a divergent product. The baseline product omits it and keeps the unsuffixed
    target name.
    """
    if not source:
        fail("dev_dist_embedded_product_descriptor requires a source")
    source_label = source_module.rpartition(":")[0] + ":" + source if source_module else source
    _dev_dist_embedded_product_descriptor(
        name = dev_dist_embedded_product_descriptor_target_name(main_module, product),
        source = source_label,
        tags = tags + ["manual"],
        visibility = visibility,
        **kwargs
    )
