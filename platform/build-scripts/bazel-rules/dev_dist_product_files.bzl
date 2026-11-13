"""The launch files of a product, rendered by the tool `product-files` from the launch model of the product."""

load(":dev_dist_build_date.bzl", "DEV_DIST_PINNED_BUILD_DATE_IN_SECONDS")
load(":dev_dist_content.bzl", "DevDistPlatformPayloadInfo")
load(":dev_dist_tool_info.bzl", "dev_dist_tool_inputs")

def _resources_directory(os, language_server):
    """The directory of `product-info.json` and `build.txt` in the home, with a trailing `/`, or `""` for the home.

    The production writers own the rule. `MacDistributionBuilder.productInfoPathPrefix` is `Resources/`, and `""` for a
    language server. `LinuxDistributionBuilder` and `WindowsDistributionBuilder` write both files at the root.
    """
    if os not in ("darwin", "linux", "windows"):
        fail("'%s' is not an OS token of platform_parts" % os)
    if os == "darwin" and not language_server:
        return "Resources/"
    return ""

def product_info_destination(os, language_server):
    """The path of `product-info.json` in a distribution, for an OS token of `platform_parts`.

    `MacDistributionBuilder.writeProductInfoFile` writes `Resources/product-info.json`, and the root for a language
    server. `LinuxDistributionBuilder.writeProductJsonFile` and `WindowsDistributionBuilder.writeProductJsonFile` write
    the root. The tool renders `vmOptionsFilePath` and `launcherPath` relative to the directory of this path, as
    production does. The path is relative to the home, see `home_prefix`.
    """
    return _resources_directory(os, language_server) + "product-info.json"

def build_txt_destination(os, language_server):
    """The path of `build.txt` in a distribution, for an OS token of `platform_parts`.

    `MacDistributionBuilder` writes `build.txt` beside `product-info.json`, so a macOS app has `Resources/build.txt`.
    `LinuxDistributionBuilder` and `WindowsDistributionBuilder` write it at the root. The path is relative to the home,
    see `home_prefix`.
    """
    return _resources_directory(os, language_server) + "build.txt"

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

# The files of the macOS app template below `Contents`, which `//platform/build-scripts:mac_app_template` holds. The
# rule checks this list against the template at analysis, so a new language fails until this list names it.
MAC_APP_FILES = [
    "Info.plist",
    "Resources/en.lproj/InfoPlist.strings",
    "Resources/ja.lproj/InfoPlist.strings",
    "Resources/ko.lproj/InfoPlist.strings",
    "Resources/zh-Hans.lproj/InfoPlist.strings",
    "Resources/zh-Hant.lproj/InfoPlist.strings",
]

def mac_app_icon_roles(icons):
    """The `mac_app_icons` value of a plan's `mac_bundle.icons`: each label with its roles, as `<role>:<name>,...`.

    `icons` maps an icon file name to `struct(release, eap)`. The release and the EAP icon can be one label.
    """
    roles = {}
    for name, labels in icons.items():
        for role, label in [("release", labels.release), ("eap", labels.eap)]:
            roles[label] = roles[label] + "," + role + ":" + name if label in roles else role + ":" + name
    return roles

def _mac_app_args(ctx, args, inputs, outputs):
    """Adds the macOS app template and icons to the action, or writes the declared files empty on another platform.

    A distribution of another OS places none of these files, so no build reads the empty files.
    """
    mac_files = ctx.outputs.mac_app_files
    template = ctx.files.mac_app_template
    if not mac_files:
        if template or ctx.attr.mac_app_icons:
            fail("%s: mac_app_template and mac_app_icons need mac_app_files" % ctx.label)
        return
    if not template:
        if ctx.attr.mac_app_icons:
            fail("%s: mac_app_icons needs mac_app_template" % ctx.label, attr = "mac_app_icons")
        for file in mac_files:
            ctx.actions.write(file, "")
        return
    info_plists = [file for file in mac_files if file.basename == MAC_APP_FILES[0]]
    if len(info_plists) != 1:
        fail("%s: mac_app_files must name one %s" % (ctx.label, MAC_APP_FILES[0]), attr = "mac_app_files")
    contents = info_plists[0].dirname
    icon_names = {}
    for target, roles in ctx.attr.mac_app_icons.items():
        files = target[DefaultInfo].files.to_list()
        if len(files) != 1:
            fail("%s: the icon %s must be one file" % (ctx.label, target.label), attr = "mac_app_icons")
        inputs.append(files[0])
        for entry in roles.split(","):
            role, _, name = entry.partition(":")
            if role not in ("release", "eap") or not name:
                fail("%s: '%s' is not <release|eap>:<name>" % (ctx.label, entry), attr = "mac_app_icons")
            args.add("--mac-bundle-%s=%s=%s" % ("icon" if role == "release" else "eap-icon", name, files[0].path))
            icon_names["Resources/" + name] = True
    declared = sorted([file.path[len(contents) + 1:] for file in mac_files])
    found = sorted([file.path.rpartition("/Contents/")[2] for file in template] + icon_names.keys())
    if declared != found:
        fail("%s: the macOS app template and icons hold %s, but mac_app_files names %s; update MAC_APP_FILES" % (ctx.label, found, declared))
    args.add_all(template, format_each = "--mac-bundle-template=%s")
    args.add("--mac-bundle-out=" + contents)
    inputs.extend(template)
    outputs.extend(mac_files)

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
    _mac_app_args(ctx, args, inputs, outputs)
    ctx.actions.run(
        executable = ctx.executable.tool,
        tools = dev_dist_tool_inputs(ctx.attr.tool),
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
    and `build_txt_destination` state the paths of `product-info.json` and `build.txt`. The action reads the launch
    model, the application info sources, `build.txt`, `OpenedPackages.txt`, one `idea.properties` and
    `common.vmoptions`, and nothing else. So it does not read the project model. The launch model states no fact of the
    application info and no build number. The action derives them from the declared sources. So an edit of the version,
    the suffix, the release date or the EAP flag changes no generated file. An edit of a common vmoptions line changes
    only `common.vmoptions`.

    A classic-loader launch also reads the core classpath of the platform payload, as a file that the rule writes at
    analysis. The tool renders the `bootClassPathJarNames` of the launch from it, as production does.

    A product with a macOS app also gets `Info.plist`, the `InfoPlist.strings` files of `MAC_APP_FILES` and the icons of
    the app. On macOS the action reads the template and writes them. The year of the copyright line is the year of the
    pinned build date. The action takes the release or the EAP variant of each icon by the EAP flag of the application
    info, so the macOS icons follow the flag too.
    """,
    implementation = _dev_dist_product_files_impl,
    attrs = {
        "tool": attr.label(
            default = Label("//platform/build-scripts/bazel-rules:product_files"),
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
        "mac_app_files": attr.output_list(
            doc = "The files of `MAC_APP_FILES` and `Resources/<icon>` for each icon, below one `Contents` directory, in any order. " +
                  "Set them for a product with a macOS app.",
        ),
        "mac_app_icons": attr.label_keyed_string_dict(
            allow_files = True,
            doc = "The icons of the macOS app, with the value of `mac_app_icon_roles`. Set it on macOS for a product with `mac_app_files`.",
        ),
        "mac_app_template": attr.label(
            allow_files = True,
            doc = "The macOS app template, `//platform/build-scripts:mac_app_template`. Set it on macOS for a product with `mac_app_files`.",
        ),
        "platform_payload": attr.label(
            providers = [DevDistPlatformPayloadInfo],
            doc = "The platform payload of the distribution, for its core classpath. Set it for a classic-loader launch, and only then. " +
                  "A modular-loader launch states `platform-loader.jar` in the launch model.",
        ),
    },
)
