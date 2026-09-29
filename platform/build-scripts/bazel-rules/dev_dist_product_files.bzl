"""The launch files of a product, rendered by the tool `product-files` from the launch model of the product."""

load(":intellij_dev_dist.bzl", "DEV_DIST_PINNED_BUILD_DATE_IN_SECONDS")

def _dev_dist_product_files_impl(ctx):
    outputs = [ctx.outputs.build_txt, ctx.outputs.idea_properties_out, ctx.outputs.vmoptions, ctx.outputs.product_info]
    args = ctx.actions.args()
    args.add("--model=" + ctx.file.model.path)
    args.add("--platform=" + ctx.attr.platform)
    args.add("--application-info=" + ctx.file.application_info.path)
    inputs = [ctx.file.model, ctx.file.application_info, ctx.file.build_number, ctx.file.opened_packages, ctx.file.idea_properties]
    if ctx.file.host_application_info:
        args.add("--host-application-info=" + ctx.file.host_application_info.path)
        inputs.append(ctx.file.host_application_info)
    for replacement in ctx.attr.replacements:
        args.add("--replacement=" + replacement)
    args.add("--build-number=" + ctx.file.build_number.path)
    args.add("--build-date-seconds=" + DEV_DIST_PINNED_BUILD_DATE_IN_SECONDS)
    args.add("--opened-packages=" + ctx.file.opened_packages.path)
    args.add("--idea-properties=" + ctx.file.idea_properties.path)
    args.add("--build-txt-out=" + ctx.outputs.build_txt.path)
    args.add("--idea-properties-out=" + ctx.outputs.idea_properties_out.path)
    args.add("--vmoptions-out=" + ctx.outputs.vmoptions.path)
    args.add("--product-info-out=" + ctx.outputs.product_info.path)
    ctx.actions.run(
        executable = ctx.executable.tool,
        arguments = [args],
        inputs = inputs,
        outputs = outputs,
        mnemonic = "DevDistProductFiles",
        progress_message = "Rendering the launch files of %{label}",
    )
    return [DefaultInfo(files = depset(outputs))]

dev_dist_product_files = rule(
    doc = """Renders `build.txt`, `bin/idea.properties`, the vmoptions file and `bin/product-info.json` of a product.

    The four outputs have fixed names, because the vmoptions file name depends on the OS and a `select` cannot name an
    output. The component that places them maps each one to its path in the distribution. The action reads the launch
    model, the application info sources, `build.txt`, `OpenedPackages.txt` and one `idea.properties`, and nothing else.
    So it does not read the project model. The launch model states no fact of the application info and no build number.
    The action derives them from the declared sources, so an edit of the version or the suffix changes no generated file.
    """,
    implementation = _dev_dist_product_files_impl,
    attrs = {
        "tool": attr.label(
            default = Label("//build/dev-dist-tools/bins/product-files"),
            executable = True,
            cfg = "exec",
        ),
        "model": attr.label(allow_single_file = [".json"], mandatory = True, doc = "The launch model that the plan generator writes for the product."),
        "platform": attr.string(mandatory = True, doc = "The `HOST_PLATFORMS` entry to render for, such as `darwin_aarch64`."),
        "application_info": attr.label(
            allow_single_file = [".xml"],
            mandatory = True,
            doc = "The `idea/<prefix>ApplicationInfo.xml` source of the product, before the marker replacement.",
        ),
        "host_application_info": attr.label(
            allow_single_file = [".xml"],
            doc = "The application info source of the host product of a frontend. The host states the names, the version and the release date.",
        ),
        "replacements": attr.string_list(doc = "`ProductProperties.appInfoXmlReplacements` as `KEY=VALUE`, in their order."),
        "build_number": attr.label(
            allow_single_file = True,
            default = Label("@community//:build.txt"),
            doc = "The file whose text is the build number of the dev distribution.",
        ),
        "opened_packages": attr.label(
            allow_single_file = True,
            default = Label("//platform/platform-impl:resources/META-INF/OpenedPackages.txt"),
        ),
        "idea_properties": attr.label(allow_single_file = True, mandatory = True, doc = "The base `idea.properties` that the model names."),
        "build_txt": attr.output(mandatory = True),
        "idea_properties_out": attr.output(mandatory = True),
        "vmoptions": attr.output(mandatory = True),
        "product_info": attr.output(mandatory = True),
    },
)
