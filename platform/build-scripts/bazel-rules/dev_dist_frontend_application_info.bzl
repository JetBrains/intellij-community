"""Produces the application info of an embedded frontend from declared files."""

def dev_dist_frontend_application_info_target_name(main_module):
    """Returns the target name for a plugin's embedded frontend application info."""
    return main_module + "_dev_frontend_application_info"

def _dev_dist_frontend_application_info_impl(ctx):
    output = ctx.actions.declare_file(ctx.label.name + ".xml")
    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add("--application-info")
    args.add(output, format = "--out=%s")
    args.add(ctx.file.client_application_info, format = "--client-application-info=%s")
    args.add(ctx.file.product_application_info, format = "--product-application-info=%s")
    args.add(ctx.file.build_number, format = "--build-number=%s")
    if ctx.attr.eap_override:
        args.add(ctx.attr.eap_override, format = "--eap-override=%s")
    if ctx.attr.version_suffix_override:
        args.add(ctx.attr.version_suffix_override, format = "--version-suffix-override=%s")
    if ctx.attr.nightly:
        args.add("--nightly")
    if ctx.attr.branch_name:
        args.add(ctx.attr.branch_name, format = "--branch-name=%s")
    ctx.actions.run(
        mnemonic = "DevDistFrontendApplicationInfo",
        inputs = [ctx.file.client_application_info, ctx.file.product_application_info, ctx.file.build_number],
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
            doc = "The application info template of the embedded frontend, with its `__BUILD__` markers.",
        ),
        "product_application_info": attr.label(
            mandatory = True,
            allow_single_file = [".xml"],
            doc = "The application info of the product whose names, version and release date the frontend takes.",
        ),
        "build_number": attr.label(
            mandatory = True,
            allow_single_file = [".txt"],
            doc = "The file that holds the build number.",
        ),
        "eap_override": attr.string(
            doc = "The `intellij.build.override.application.version.isEAP` value. Empty for none.",
        ),
        "version_suffix_override": attr.string(
            doc = "The `intellij.build.override.application.version.suffix` value. Empty for none.",
        ),
        "nightly": attr.bool(
            doc = "Whether the build is nightly, which stamps the branch name for every build number.",
        ),
        "branch_name": attr.string(
            doc = "The branch name to stamp. Empty for none.",
        ),
        "_resolver": attr.label(
            default = "//platform/build-scripts/bazel-rules/dev-dist-plugin-descriptor:dev-dist-plugin-descriptor",
            executable = True,
            cfg = "exec",
        ),
    },
)

def dev_dist_frontend_application_info(
        main_module,
        client_application_info,
        product_application_info,
        build_number,
        tags = [],
        visibility = ["//visibility:public"],
        **kwargs):
    """Declares one embedded frontend application info action."""
    _dev_dist_frontend_application_info(
        name = dev_dist_frontend_application_info_target_name(main_module),
        client_application_info = client_application_info,
        product_application_info = product_application_info,
        build_number = build_number,
        tags = tags + ["manual"],
        visibility = visibility,
        **kwargs
    )
