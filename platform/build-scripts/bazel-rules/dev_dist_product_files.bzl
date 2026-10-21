"""The launch files of a product, rendered by the tool `product-files` from the launch model of the product."""

load(":dev_dist_build_date.bzl", "DEV_DIST_PINNED_BUILD_DATE_IN_SECONDS")
load(":dev_dist_content.bzl", "DevDistPlatformPayloadInfo")

def product_info_destination(os, language_server):
    """The path of `product-info.json` in a distribution, for an OS token of `platform_parts`.

    The production writers own the rule. `MacDistributionBuilder.writeProductInfoFile` writes
    `Resources/product-info.json`, and the root for a language server. `LinuxDistributionBuilder.writeProductJsonFile`
    and `WindowsDistributionBuilder.writeProductJsonFile` write the root. The tool renders `vmOptionsFilePath` and
    `launcherPath` relative to the directory of this path, as production does. The path is relative to the home, see
    `home_prefix`.
    """
    if os not in ("darwin", "linux", "windows"):
        fail("'%s' is not an OS token of platform_parts" % os)
    if os == "darwin" and not language_server:
        return "Resources/product-info.json"
    return "product-info.json"

def home_prefix(os, language_server):
    """The directory of a distribution that holds the home of the IDE, for an OS token of `platform_parts`.

    The result is `Contents` for a macOS app and `""` for every other distribution. Production lays a macOS app out
    below `<name>.app/Contents`, and `MacDistributionBuilder.layoutMacCli` keeps `bin` of a language server at the
    root. The native launcher expects the `Contents` level on macOS. It strips that level to find the Toolbox-style
    vmoptions file `<app>.vmoptions` beside the app. Every destination of a component and of the placement is relative
    to the home.
    """
    if os not in ("darwin", "linux", "windows"):
        fail("'%s' is not an OS token of platform_parts" % os)
    if os == "darwin" and not language_server:
        return "Contents"
    return ""

def _dev_dist_product_files_impl(ctx):
    outputs = [ctx.outputs.build_txt, ctx.outputs.idea_properties_out, ctx.outputs.vmoptions, ctx.outputs.product_info]
    args = ctx.actions.args()
    args.add("--model=" + ctx.file.model.path)
    args.add("--platform=" + ctx.attr.platform)
    args.add("--application-info=" + ctx.file.application_info.path)
    inputs = [
        ctx.file.model,
        ctx.file.application_info,
        ctx.file.build_number,
        ctx.file.opened_packages,
        ctx.file.idea_properties,
        ctx.file.common_vm_options,
    ]
    if ctx.file.host_application_info:
        args.add("--host-application-info=" + ctx.file.host_application_info.path)
        inputs.append(ctx.file.host_application_info)
    for replacement in ctx.attr.replacements:
        args.add("--replacement=" + replacement)
    for argument in ctx.attr.dev_jvm_arguments:
        args.add("--dev-jvm-argument=" + argument)
    args.add("--build-number=" + ctx.file.build_number.path)
    args.add("--build-date-seconds=" + DEV_DIST_PINNED_BUILD_DATE_IN_SECONDS)
    args.add("--opened-packages=" + ctx.file.opened_packages.path)
    args.add("--idea-properties=" + ctx.file.idea_properties.path)
    args.add("--common-vmoptions=" + ctx.file.common_vm_options.path)
    args.add("--build-txt-out=" + ctx.outputs.build_txt.path)
    args.add("--idea-properties-out=" + ctx.outputs.idea_properties_out.path)
    args.add("--vmoptions-out=" + ctx.outputs.vmoptions.path)
    args.add("--product-info-out=" + ctx.outputs.product_info.path)
    if ctx.outputs.frontend_vmoptions:
        args.add("--frontend-vmoptions-out=" + ctx.outputs.frontend_vmoptions.path)
        outputs.append(ctx.outputs.frontend_vmoptions)
    if ctx.attr.platform_payload:
        # The names are known at analysis, so the action reads one small file and no jar of the payload.
        core_classpath = ctx.actions.declare_file(ctx.label.name + ".core-classpath.txt")
        ctx.actions.write(
            core_classpath,
            "".join([name + "\n" for name in ctx.attr.platform_payload[DevDistPlatformPayloadInfo].core_classpath_jar_names]),
        )
        args.add("--core-classpath-jar-names=" + core_classpath.path)
        inputs.append(core_classpath)
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
    doc = """Renders `build.txt`, `bin/idea.properties`, the vmoptions file and `product-info.json` of a product.

    A product with an embedded frontend also gets the vmoptions file of the frontend, so the rule has five outputs.
    The outputs have fixed names, because the vmoptions file names depend on the OS and a `select` cannot name an
    output. The component that places them maps each one to its path in the distribution. `product_info_destination`
    states the path of `product-info.json`. The action reads the launch
    model, the application info sources, `build.txt`, `OpenedPackages.txt`, one `idea.properties` and
    `common.vmoptions`, and nothing else. So it does not read the project model. The launch model states no fact of the
    application info and no build number. The action derives them from the declared sources. So an edit of the version,
    the suffix, the release date or the EAP flag changes no generated file. An edit of a common vmoptions line changes
    only `common.vmoptions`.

    A classic-loader launch also reads the core classpath of the platform payload, as a file that the rule writes at
    analysis. The tool renders the `bootClassPathJarNames` of the launch from it, as production does.

    `dev_jvm_arguments` mark a dev distribution in `product-info.json`. The native launcher puts `additionalJvmArguments`
    last, so no vmoptions file overrides them. No absolute path belongs there: the action stays cacheable.
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
        "common_vm_options": attr.label(
            allow_single_file = True,
            default = Label("@community//bin:common.vmoptions"),
            doc = "The vmoptions lines of every product, between the memory lines and the product lines of the model.",
        ),
        "build_txt": attr.output(mandatory = True),
        "idea_properties_out": attr.output(mandatory = True),
        "vmoptions": attr.output(mandatory = True),
        "product_info": attr.output(mandatory = True),
        "frontend_vmoptions": attr.output(
            doc = "The vmoptions file of the embedded frontend. Set it when the launch model states `frontendVmOptions`, and only then.",
        ),
        "dev_jvm_arguments": attr.string_list(
            doc = "The JVM arguments of a dev distribution, such as `-Didea.use.dev.build.server=true`. The tool appends " +
                  "them to the `additionalJvmArguments` of the launch and of each custom command with a list of its own. " +
                  "Production states none. Name no absolute path here.",
        ),
        "platform_payload": attr.label(
            providers = [DevDistPlatformPayloadInfo],
            doc = "The platform payload of the distribution, for its core classpath. Set it for a classic-loader launch, and only then. " +
                  "A modular-loader launch states `platform-loader.jar` in the launch model.",
        ),
    },
)
