"""The launch files of a product, rendered by the tool `product-files` from the launch model of the product."""

def _dev_dist_product_files_impl(ctx):
    outputs = [ctx.outputs.build_txt, ctx.outputs.idea_properties_out, ctx.outputs.vmoptions, ctx.outputs.product_info]
    args = ctx.actions.args()
    args.add("--model=" + ctx.file.model.path)
    args.add("--platform=" + ctx.attr.platform)
    args.add("--opened-packages=" + ctx.file.opened_packages.path)
    args.add("--idea-properties=" + ctx.file.idea_properties.path)
    args.add("--build-txt-out=" + ctx.outputs.build_txt.path)
    args.add("--idea-properties-out=" + ctx.outputs.idea_properties_out.path)
    args.add("--vmoptions-out=" + ctx.outputs.vmoptions.path)
    args.add("--product-info-out=" + ctx.outputs.product_info.path)
    ctx.actions.run(
        executable = ctx.executable.tool,
        arguments = [args],
        inputs = [ctx.file.model, ctx.file.opened_packages, ctx.file.idea_properties],
        outputs = outputs,
        mnemonic = "DevDistProductFiles",
        progress_message = "Rendering the launch files of %{label}",
    )
    return [DefaultInfo(files = depset(outputs))]

dev_dist_product_files = rule(
    doc = """Renders `build.txt`, `bin/idea.properties`, the vmoptions file and `bin/product-info.json` of a product.

    The four outputs have fixed names, because the vmoptions file name depends on the OS and a `select` cannot name an
    output. The component that places them maps each one to its path in the distribution. The action reads the launch
    model, `OpenedPackages.txt` and one `idea.properties`, and nothing else, so it does not read the project model.
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
