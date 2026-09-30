"""Produces the application info of an embedded frontend from declared files."""

def dev_dist_frontend_application_info_target_name(main_module, product = None):
    """Returns the target name for a plugin's embedded frontend application info.

    The baseline product keeps the unsuffixed name. A divergent product appends `_<product>` so one package can hold
    one helper per product that packs a CWM frontend of its own.
    """
    name = main_module + "_dev_frontend_application_info"
    return name if not product else name + "_" + product

def _dev_dist_frontend_application_info_impl(ctx):
    output = ctx.actions.declare_file(ctx.label.name + ".xml")
    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add("--application-info")
    args.add(output, format = "--out=%s")
    args.add(ctx.file.client_application_info, format = "--client-application-info=%s")
    args.add(ctx.file.product_application_info, format = "--product-application-info=%s")
    ctx.actions.run(
        mnemonic = "DevDistFrontendApplicationInfo",
        inputs = [ctx.file.client_application_info, ctx.file.product_application_info],
        outputs = [output],
        executable = ctx.executable._resolver,
        arguments = [args],
        progress_message = "Producing the frontend application info of %{label}",
    )
    return [DefaultInfo(files = depset([output]))]

_dev_dist_frontend_application_info = rule(
    implementation = _dev_dist_frontend_application_info_impl,
    attrs = {
        "client_application_info": attr.label(
            mandatory = True,
            allow_single_file = [".xml"],
            doc = "The application info template of the embedded frontend. Its build markers stay, and the run time reads `build.txt`.",
        ),
        "product_application_info": attr.label(
            mandatory = True,
            allow_single_file = [".xml"],
            doc = "The application info of the product whose names, version and release date the frontend takes.",
        ),
        "_resolver": attr.label(
            default = "//platform/build-scripts/bazel-rules:plugin_descriptor_writer",
            executable = True,
            cfg = "exec",
        ),
    },
)

def dev_dist_frontend_application_info(
        main_module,
        client_application_info,
        product_application_info,
        product = None,
        tags = [],
        visibility = ["//visibility:public"],
        **kwargs):
    """Declares one embedded frontend application info action.

    `product` is the `dev-build.json` key of a divergent product. The baseline product omits it and keeps the unsuffixed
    target name.
    """
    _dev_dist_frontend_application_info(
        name = dev_dist_frontend_application_info_target_name(main_module, product),
        client_application_info = client_application_info,
        product_application_info = product_application_info,
        tags = tags + ["manual"],
        visibility = visibility,
        **kwargs
    )
