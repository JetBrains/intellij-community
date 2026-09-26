"""Produces the two generated entries of the application-info module jar of a product from declared files.

`dev_dist_product_descriptor` resolves the product descriptor, `META-INF/plugin.xml` or `META-INF/<prefix>Plugin.xml`, and
the prefix of `plugins/plugin-classpath.txt`.
`dev_dist_product_application_info` stamps `idea/<prefix>ApplicationInfo.xml`. The generator writes both targets into
`//build/dev-dist-product-descriptors`, and `dev_dist_platform_jar` patches their outputs into the jar.
"""

load("@rules_java//java:defs.bzl", "JavaInfo")
load(":dev_dist_embedded_product_descriptor.bzl", "declared_descriptors")

def _dev_dist_product_descriptor_impl(ctx):
    source = ctx.file.source
    declared = declared_descriptors(ctx)
    output = ctx.actions.declare_file(ctx.label.name + ".xml")
    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add("--product-descriptor")
    args.add(output, format = "--out=%s")
    args.add(source, format = "--source=%s")
    args.add(ctx.attr.main_module, format = "--main-module=%s")
    args.add_all(declared.descriptor_args, format_each = "--descriptor=%s")
    args.add_all(declared.descriptor_jar_args, format_each = "--descriptor-in-jar=%s")
    args.add_all(ctx.attr.refused_content_modules, format_each = "--refused-content-module=%s")
    args.add_all(ctx.attr.scrambled_content_modules, format_each = "--scrambled-content-module=%s")
    args.add(ctx.outputs.plugin_classpath_prefix, format = "--plugin-classpath-prefix=%s")
    args.add(ctx.outputs.classpath_descriptor, format = "--classpath-descriptor=%s")
    ctx.actions.run(
        mnemonic = "DevDistProductDescriptor",
        inputs = depset([source] + declared.inputs),
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
        "source": attr.label(
            mandatory = True,
            allow_single_file = [".xml"],
            doc = "The generated Product DSL content of the product, with the module sets and the deprecated includes inlined.",
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
    args.add(ctx.file.build_number, format = "--build-number=%s")
    args.add(ctx.attr.product_code, format = "--product-code=%s")
    args.add_all(ctx.attr.replacements, format_each = "--replacement=%s")
    ctx.actions.run(
        mnemonic = "DevDistProductApplicationInfo",
        inputs = [ctx.file.source, ctx.file.build_number],
        outputs = [output],
        executable = ctx.executable._resolver,
        arguments = [args],
        progress_message = "Stamping the application info of %{label}",
    )
    return [DefaultInfo(files = depset([output]))]

_dev_dist_product_application_info = rule(
    implementation = _dev_dist_product_application_info_impl,
    attrs = {
        "source": attr.label(
            mandatory = True,
            allow_single_file = [".xml"],
            doc = "The `idea/<prefix>ApplicationInfo.xml` of the application-info module, with its markers.",
        ),
        "build_number": attr.label(
            default = Label("@community//:build.txt"),
            allow_single_file = [".txt"],
            doc = "The file that holds the build number.",
        ),
        "product_code": attr.string(
            mandatory = True,
            doc = "`ApplicationInfoProperties.productCode`, the prefix of the stamped build number.",
        ),
        "replacements": attr.string_list(
            doc = "`ProductProperties.appInfoXmlReplacements` as `<key>=<value>`, in their order.",
        ),
        "_resolver": attr.label(
            default = "//platform/build-scripts/bazel-rules:plugin_descriptor_writer",
            executable = True,
            cfg = "exec",
        ),
    },
)

def dev_dist_product_descriptor(name, tags = [], visibility = ["//visibility:public"], **kwargs):
    """Declares one product descriptor action. The target is `manual`, like every packing target."""
    _dev_dist_product_descriptor(name = name, tags = tags + ["manual"], visibility = visibility, **kwargs)

def dev_dist_product_application_info(name, tags = [], visibility = ["//visibility:public"], **kwargs):
    """Declares one application info stamp action. The target is `manual`, like every packing target."""
    _dev_dist_product_application_info(name = name, tags = tags + ["manual"], visibility = visibility, **kwargs)
