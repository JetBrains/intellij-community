"""The runtime module repository of a dev distribution, `modules/module-descriptors.dat` and `.jar`, from Bazel facts.

The layout of the repository is derived in Bazel. The platform payload states what each platform jar merges, and each
plugin component states the layout part of its own jars, see `DevDistRuntimeLayoutInfo`. The plan generator states
only the platform jars before and after the sorted range, see `dev_dist_platform_jar_order.bzl`. `runtime-layout` joins
the parts into the `RuntimeModuleRepositoryLayout`, and `runtime_module_repository_generator` writes the repository
from it, the project model and the plugin descriptors.
"""

load(":dev_dist_content.bzl", "DevDistPlatformPayloadInfo")
load(":dev_dist_platform_jar_order.bzl", "platform_jar_order")
load(":dev_dist_plugin_descriptor.bzl", "DEV_DIST_PRODUCT_INFO_ATTR", "dev_dist_product_info_transition")
load(":dev_plugin.bzl", "DevDistRuntimeLayoutInfo")
load(":intellij_dev_dist.bzl", "IntellijProjectModelTreeInfo")

DevDistRuntimeLayoutPartsInfo = provider(
    doc = "The layout parts of the plugin components of one product, configured for that product.",
    fields = {"plugins": "list of `DevDistRuntimeLayoutInfo`, in the order of the components."},
)

def _dev_dist_runtime_layout_parts_impl(ctx):
    plugins = [target[DevDistRuntimeLayoutInfo] for target in ctx.attr.plugins]
    return [
        DefaultInfo(files = depset([info.part for info in plugins] + [info.descriptor for info in plugins])),
        DevDistRuntimeLayoutPartsInfo(plugins = plugins),
    ]

dev_dist_runtime_layout_parts = rule(
    doc = """The layout parts of the plugin components of one product.

    `plugins` need the product configuration, which `product_info` sets on the way down, as `dev_dist_plugin_content`
    does. A product and its embedded frontend state their plugins in two targets, because each is configured for its own
    product.""",
    implementation = _dev_dist_runtime_layout_parts_impl,
    attrs = {
        "plugins": attr.label_list(
            doc = "The plugin components. Each one publishes `DevDistRuntimeLayoutInfo`.",
            cfg = dev_dist_product_info_transition,
            providers = [DevDistRuntimeLayoutInfo],
        ),
    } | DEV_DIST_PRODUCT_INFO_ATTR,
)

def _platform_part(ctx, name, payload, descriptor_module, first, last, attr):
    """Writes the layout part of a core plugin at analysis: the packed platform jars in the platform jar order."""
    layout = payload[DevDistPlatformPayloadInfo].layout
    order = platform_jar_order(layout, first = first, last = last)
    if order.error:
        fail("%s: %s" % (ctx.label, order.error), attr = attr)
    by_destination = {entry.destination: entry for entry in layout}
    part = ctx.actions.declare_file(ctx.label.name + "." + name + ".runtime-layout.json")
    ctx.actions.write(part, json.encode({
        "version": 1,
        "descriptorModule": descriptor_module,
        "directory": "",
        "order": "layout",
        "jars": [
            {
                "destination": "lib/" + entry.destination,
                "members": [{"module": module} for module in entry.member_modules] +
                           [{"library": library.label, "jars": [jar.path for jar in library.jars]} for library in entry.library_jars],
            }
            for entry in [by_destination[destination] for destination in order.destinations]
        ],
    }) + "\n")
    return part

def _dev_dist_runtime_module_repository_impl(ctx):
    if bool(ctx.attr.frontend_platform_payload) != bool(ctx.attr.frontend_core_module):
        fail("%s: frontend_platform_payload and frontend_core_module go together" % ctx.label, attr = "frontend_core_module")

    # The core plugin first, then the bundled plugins, then the frontend-only plugins, as the Kotlin fragment states them.
    parts = [_platform_part(ctx, "platform", ctx.attr.platform_payload, ctx.attr.core_module, ctx.attr.first_jars, ctx.attr.last_jars, "first_jars")]
    descriptors = {ctx.attr.core_module: ctx.file.core_descriptor}
    frontend_parts = []
    inputs = [ctx.file.core_descriptor]
    if ctx.attr.frontend_platform_payload:
        frontend_parts.append(_platform_part(
            ctx,
            "frontend-platform",
            ctx.attr.frontend_platform_payload,
            ctx.attr.frontend_core_module,
            ctx.attr.frontend_first_jars,
            ctx.attr.frontend_last_jars,
            "frontend_first_jars",
        ))
        descriptors[ctx.attr.frontend_core_module] = ctx.file.frontend_core_descriptor
        inputs.append(ctx.file.frontend_core_descriptor)

    # Sorted by descriptor module, as the Kotlin fragment states them. The order is in the bytes: a module that several
    # plugins include takes its ID from the last of them, see `generateRuntimePluginHeaders`.
    for plugins, destination in [(ctx.attr.plugins, parts), (ctx.attr.frontend_plugins, frontend_parts)]:
        infos = plugins[DevDistRuntimeLayoutPartsInfo].plugins if plugins else []
        for info in sorted(infos, key = lambda info: info.descriptor_module):
            if info.descriptor_module in descriptors:
                fail("%s: two plugins have the descriptor module '%s'" % (ctx.label, info.descriptor_module))
            descriptors[info.descriptor_module] = info.descriptor
            destination.append(info.part)
            inputs.append(info.descriptor)

    layout = ctx.actions.declare_file(ctx.label.name + ".runtime-module-repository-layout.json")
    layout_args = ctx.actions.args()
    layout_args.add_all(parts, format_each = "--part=%s")
    layout_args.add_all(frontend_parts, format_each = "--frontend-only-part=%s")
    layout_args.add(ctx.file.bazel_targets_json, format = "--bazel-targets=%s")
    layout_args.add(layout, format = "--output=%s")
    ctx.actions.run(
        mnemonic = "DevDistRuntimeLayout",
        executable = ctx.executable._runtime_layout,
        inputs = depset(parts + frontend_parts + inputs + [ctx.file.bazel_targets_json]),
        outputs = [layout],
        arguments = [layout_args],
        progress_message = "Assembling the runtime module repository layout of %{label}",
    )

    # Every plugin of the layout by its descriptor module, as `RuntimeModuleRepositoryMain` requires.
    descriptor_args = ["--descriptor=%s=%s" % (module, descriptor.path) for module, descriptor in descriptors.items()]

    project_tree = ctx.attr.project_model_tree[IntellijProjectModelTreeInfo].tree
    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("@%s", use_always = True)
    args.add("--project-dir=" + project_tree.path)
    args.add(layout, format = "--layout=%s")
    args.add_all(descriptor_args)
    args.add_all(ctx.files.ide_properties, format_each = "--ide-properties=%s")
    args.add("--output-dir=" + ctx.outputs.compact.dirname.removesuffix("/modules"))
    ctx.actions.run(
        mnemonic = "DevDistRuntimeModuleRepository",
        executable = ctx.executable._generator,
        inputs = depset([project_tree, layout] + inputs + ctx.files.ide_properties),
        outputs = [ctx.outputs.compact, ctx.outputs.jar],
        arguments = [args],
        # The project model tree is large and changes with every model edit, so this action uses the local disk cache
        # only, as the fragments that read the tree do.
        execution_requirements = {"block-network": "1", "no-remote-cache": "1", "no-remote-exec": "1"},
        progress_message = "Generating the runtime module repository of %{label}",
    )
    return [
        DefaultInfo(files = depset([ctx.outputs.compact, ctx.outputs.jar])),
        OutputGroupInfo(runtime_module_repository_layout = depset([layout])),
    ]

dev_dist_runtime_module_repository = rule(
    doc = """Writes `modules/module-descriptors.dat` and `modules/module-descriptors.jar` of a dev distribution.

The two files are predeclared outputs, `<name>.home/modules/module-descriptors.dat` and `.jar`, so a component places
them by label. The output group `runtime_module_repository_layout` holds the layout the repository is generated from.
""",
    implementation = _dev_dist_runtime_module_repository_impl,
    outputs = {
        "compact": "%{name}.home/modules/module-descriptors.dat",
        "jar": "%{name}.home/modules/module-descriptors.jar",
    },
    attrs = {
        "platform_payload": attr.label(mandatory = True, providers = [DevDistPlatformPayloadInfo], doc = "The payload of the platform `lib/` jars."),
        "core_module": attr.string(mandatory = True, doc = "The application-info module, which holds the descriptor of the core plugin."),
        "core_descriptor": attr.label(mandatory = True, allow_single_file = [".xml"], doc = "The product descriptor, with the content module descriptors inlined."),
        "first_jars": attr.string_list(doc = "The platform jars before the sorted range, from `DEV_DIST_PLATFORM_JAR_ORDERS`."),
        "last_jars": attr.string_list(doc = "The platform jars after the sorted range, with every library-only jar."),
        "plugins": attr.label(providers = [DevDistRuntimeLayoutPartsInfo], doc = "The layout parts of the bundled plugins."),
        "frontend_platform_payload": attr.label(providers = [DevDistPlatformPayloadInfo], doc = "The platform payload of the embedded frontend, or none."),
        "frontend_core_module": attr.string(doc = "The application-info module of the embedded frontend."),
        "frontend_core_descriptor": attr.label(allow_single_file = [".xml"], doc = "The product descriptor of the embedded frontend."),
        "frontend_first_jars": attr.string_list(doc = "The platform jars of the embedded frontend before the sorted range."),
        "frontend_last_jars": attr.string_list(doc = "The platform jars of the embedded frontend after the sorted range."),
        "frontend_plugins": attr.label(providers = [DevDistRuntimeLayoutPartsInfo], doc = "The layout parts of the plugins that only the embedded frontend bundles."),
        "ide_properties": attr.label_list(allow_files = True, doc = "The `idea.properties` of the product, which can suppress plugins."),
        "project_model_tree": attr.label(mandatory = True, providers = [IntellijProjectModelTreeInfo]),
        "bazel_targets_json": attr.label(mandatory = True, allow_single_file = [".json"]),
        "_runtime_layout": attr.label(default = "//platform/build-scripts/bazel-rules:runtime_layout", executable = True, cfg = "exec"),
        "_generator": attr.label(
            default = "//platform/build-scripts/runtime-module-repository:runtime_module_repository_generator",
            executable = True,
            cfg = "exec",
        ),
    },
)
