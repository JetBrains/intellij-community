"""The declarations of a split dev distribution: the platform set, the distribution and the launcher of a row.

Each half of the repository calls `intellij_dev_dist_declarations` once with its own tables and re-exports the macros
of the result. The code here reads no table directly, so it names no target of the other half.

A community product is a key that both registries state with one product class. The community half plans it, and the
ultimate half writes no row of it. A distribution of a community product in the ultimate checkout composes the
community platform set and the community bundled plugins, with the additional plugins of its own rows. The ultimate
tables name the community products and the community tables in `community`, see `_community_product`.
"""

load("//platform/build-scripts/bazel-rules:content_module_jar.bzl", "dev_dist_platform_jar")
load("//platform/build-scripts/bazel-rules:dev_dist_content.bzl", "dev_dist_platform_payload")
load("//platform/build-scripts/bazel-rules:dev_dist_product_files.bzl", "dev_dist_product_files")
load("//platform/build-scripts/bazel-rules:dev_dist_runtime_module_repository.bzl", "dev_dist_runtime_layout_parts", "dev_dist_runtime_module_repository")
load("//platform/build-scripts/bazel-rules:intellij_dev_dist.bzl", "intellij_dev_fragments_dist", "intellij_dev_packed_jars_component")
load(":dev_launch_dependencies.bzl", "HOST_PLATFORMS", "platform_parts")
load(":intellij_dev.bzl", "intellij_dev_dist_config", "intellij_dev_launcher_binary")

def _plugin_component_entries(platform_prefix, tier, entries):
    """Reads one tier of the generated component map as `struct(main_module, label, labels)` entries.

    The map keys a plugin by its main module. The value is the label of the one component that serves every
    platform, or a dict from a `HOST_PLATFORMS` entry to the component that serves it.
    """
    if type(entries) != "dict":
        fail("Generated dev-plugin component tier '%s/%s' must map main modules to labels" % (platform_prefix, tier))
    result = []
    for main_module, value in entries.items():
        if type(value) == "string":
            result.append(struct(main_module = main_module, label = value, labels = None))
        elif type(value) == "dict":
            result.append(struct(main_module = main_module, label = "", labels = value))
        else:
            fail("Generated dev-plugin component '%s' in tier '%s/%s' must be a label or a platform-to-label dict" % (main_module, platform_prefix, tier))
    return result

# The two tiers of `tables.plugin_components[product]`, in the order the catalogue reads them.
_PLUGIN_COMPONENT_TIERS = ["bundled", "additional"]

def _community_product(tables, product):
    """The community tables when the community half plans `product`, else `None`.

    `tables.community` is `None` for the community half. For the ultimate half it is a struct with these fields:

    - `products`: the community products, `DEV_DIST_COMMUNITY_PRODUCTS` of the ultimate plan.
    - `plans`: the community `DEV_DIST_PLANS`.
    - `product(key)`: the community product entry, with its `platform_set`.
    - `build_package`: the package of the community platform sets, `@community//build`.
    - `bundled_components(product)`: the bundled tier of the community component map, with every label spelled for
      the ultimate checkout.
    - `product_info_label(product)`: the label of the community product info of `product`.
    """
    community = tables.community
    if community == None or product not in community.products:
        return None
    if product in tables.plans:
        fail("Product '%s' is a community product, and the plan of this half has a row of it; run plugin-model-tool" % product)
    return community

def _component_tiers(tables, platform_prefix):
    """The two tiers of the component map of one product, before the checks of `_plugin_component_catalogue`.

    For a community product, the bundled tier is the community one, and the own map states the additional tier only.
    """
    tiers = tables.plugin_components.get(platform_prefix)
    if tiers == None:
        fail("No generated dev-plugin components for product '%s'" % platform_prefix)
    if type(tiers) != "dict":
        fail("Generated dev-plugin components for '%s' must be a dict of tiers" % platform_prefix)
    community = _community_product(tables, platform_prefix)
    if community == None:
        return tiers
    if sorted(tiers.keys()) != ["additional"]:
        fail("Generated dev-plugin components for the community product '%s' must have exactly the tier ['additional'], found %s" % (
            platform_prefix,
            sorted(tiers.keys()),
        ))
    return {"bundled": community.bundled_components(platform_prefix), "additional": tiers["additional"]}

def _plugin_component_catalogue(tables, platform_prefix):
    """Returns the generated component map of one product after checking it.

    The map has two tiers, `bundled` and `additional`, each keyed by the plugin's main module. A plugin is in one tier
    only. It has one component label that serves every platform, or one label per `HOST_PLATFORMS` entry that has
    the plugin. The bundled tier is in composition order: a distribution composes its neutral entries in list order,
    then its per-platform entries in list order. The bundled tier of a community product is the community one, see
    `_component_tiers`.
    """
    tiers = _component_tiers(tables, platform_prefix)
    if sorted(tiers.keys()) != sorted(_PLUGIN_COMPONENT_TIERS):
        fail("Generated dev-plugin components for '%s' must have exactly the tiers %s, found %s" % (
            platform_prefix,
            _PLUGIN_COMPONENT_TIERS,
            sorted(tiers.keys()),
        ))
    if not tiers["bundled"]:
        fail("Generated dev-plugin components for '%s' name no bundled plugin" % platform_prefix)
    by_tier = {}
    modules = {}
    labels = {}
    for tier in _PLUGIN_COMPONENT_TIERS:
        entries = _plugin_component_entries(platform_prefix, tier, tiers[tier])
        by_tier[tier] = entries
        for entry in entries:
            main_module = entry.main_module
            label = entry.label
            platform_labels = entry.labels
            if not main_module or bool(label) == (platform_labels != None):
                fail("Invalid generated dev-plugin component in tier '%s/%s': %s" % (platform_prefix, tier, entry))
            entry_labels = [label]
            if platform_labels != None:
                if type(platform_labels) != "dict" or not platform_labels:
                    fail("Generated dev-plugin component '%s' of '%s' must map platforms to labels" % (main_module, platform_prefix))
                for platform in platform_labels:
                    if platform not in HOST_PLATFORMS:
                        fail("Generated dev-plugin component '%s' of '%s' names platform '%s', which is not one of %s" % (
                            main_module,
                            platform_prefix,
                            platform,
                            HOST_PLATFORMS,
                        ))
                entry_labels = platform_labels.values()
            if main_module in modules:
                fail("Plugin '%s' is both a bundled and an additional component of '%s'" % (main_module, platform_prefix))
            for entry_label in entry_labels:
                if not entry_label:
                    fail("Generated dev-plugin component '%s' of '%s' states an empty label" % (main_module, platform_prefix))
                if entry_label in labels:
                    fail("Generated component label '%s' is not unique for '%s'" % (entry_label, platform_prefix))
                labels[entry_label] = tier
            modules[main_module] = tier
    return struct(
        bundled = by_tier["bundled"],
        additional = by_tier["additional"],
        tiers = modules,
    )

def _product_descriptor(tables, product):
    """The label of the classpath descriptor that `dev_dist_product_descriptor` writes for `product`.

    That is the descriptor of the plugin-classpath prefix, which the platform payload names. It embeds the descriptor of
    every content module, a scrambled one too, as the core plugin of the runtime module repository needs.
    """
    inputs = tables.fragment_inputs.get(product, {}).get(tables.platform_lib_fragment)
    prefix = getattr(inputs, "plugin_classpath_prefix", "") if inputs else ""
    if not prefix.endswith(".plugin-classpath-prefix"):
        fail("The plan of '%s' names no product descriptor, so its runtime module repository has no core plugin descriptor" % product)
    return prefix.removesuffix(".plugin-classpath-prefix") + ".classpath.xml"

def _plugin_components(target_platform, entries, selected_modules = None):
    """Selects the generated components of the given catalogue entries.

    The result lists the neutral entries in entry order, then the per-platform entries in entry order. A neutral
    component is listed directly. A per-platform component takes the label of `target_platform` when one is set, and
    a `select` over the host platform otherwise. A per-platform plugin absent from a platform contributes nothing on
    that platform. `selected_modules` keeps the entries of the named main modules only.
    """
    if selected_modules != None:
        selected = {module: True for module in selected_modules}
        entries = [entry for entry in entries if entry.main_module in selected]

    neutral = [entry for entry in entries if entry.labels == None]
    per_platform = [entry for entry in entries if entry.labels != None]
    labels = [entry.label for entry in neutral]
    modules = [entry.main_module for entry in neutral]
    if not per_platform:
        return struct(labels = labels, modules = modules)

    if target_platform:
        if target_platform not in HOST_PLATFORMS:
            fail("'%s' is not one of %s" % (target_platform, HOST_PLATFORMS))
        present = [entry for entry in per_platform if target_platform in entry.labels]
        return struct(
            labels = labels + [entry.labels[target_platform] for entry in present],
            modules = modules + [entry.main_module for entry in present],
        )

    def by_host(field):
        return select({
            "@community//build:host_" + platform: [
                entry.labels[platform] if field == "label" else getattr(entry, field)
                for entry in per_platform
                if platform in entry.labels
            ]
            for platform in HOST_PLATFORMS
        })

    return struct(
        labels = labels + by_host("label"),
        modules = modules + by_host("main_module"),
    )

# The `DevMainKt` property of a run configuration that asks for the runtime module repository. A row states it as an
# attribute, because the row composes another distribution. The plan generator (`devDistRunConfigurations.kt`) takes
# it out of `jvm_flags`.
_GENERATE_RUNTIME_MODULE_REPOSITORY_FLAG = "-Dintellij.build.generate.runtime.module.repository=true"

# `JetBrainsClientProperties.configureRootModuleForModularLoader` reads the property while the layout is built, so it
# changes the platform and the bundled plugins of a frontend. A split platform set is built without the row's flags,
# so a row states its root module as the `rootModule` of its own `build/dev-build.json` key instead.
_ROOT_MODULE_OVERRIDE_FLAG_PREFIX = "-Didea.jbclient.root.module.override="

# The JPS output directories below `out/` of the two halves. A dev launch builds no JPS output, so a launcher flag that
# names one names a file that no build writes. `devDistRunConfigurations.kt` refuses the same directories in a row.
_JPS_OUTPUT_DIRECTORIES = ["classes", "production", "test"]
_WORKSPACE_OUT = "{BUILD_WORKSPACE_DIRECTORY}/out/"

def _names_jps_output(flag):
    """Whether `flag` names a JPS output directory below `${BUILD_WORKSPACE_DIRECTORY}/out/`."""
    for rest in flag.split(_WORKSPACE_OUT)[1:]:
        segment = rest.partition("/")[0].partition(":")[0].partition(";")[0].partition(" ")[0].partition("\"")[0]
        if segment in _JPS_OUTPUT_DIRECTORIES:
            return True
    return False

# The component that provides the `lib/` jars the packer produces. The composer checks the exact set of composed
# kinds, so this name is part of the distribution's contract.
PACKED_JARS_COMPONENT = "platform_packed_content_modules"

# The component that places the platform's declared dist files: the files of the unpacked dev-launch archives that
# `PlatformLayout.withDistFiles` names, at their distribution paths. The generated plan states them per host platform.
PLATFORM_ASSETS_COMPONENT = "platform_assets"

# The component of `bin/idea.properties`, the vmoptions file, `bin/product-info.json` and `build.txt`, which the product
# files action renders from the launch model of the product.
_PLATFORM_RESOURCES_FRAGMENT = "platform_resources"

def _fragment_suffix(fragment_name):
    """The target suffix of a platform fragment, `<platform set>_<suffix>`."""
    return fragment_name[len("platform_"):]

def _plan(tables, product):
    """The generated plan of `product`, or a failure that names the product. The plan of a community product is the community one."""
    community = _community_product(tables, product)
    plan = (community.plans if community != None else tables.plans).get(product)
    if plan == None:
        fail("No generated dev-distribution plan for product '%s'" % product)
    return plan

def _product_info_label(tables, product):
    """The label of the product info of `product`: the community one for a community product."""
    community = _community_product(tables, product)
    if community != None:
        return community.product_info_label(product)
    return tables.product_info_label(product)

def _platform_fragment_layout(tables, product):
    """The ordered platform fragments of one product, as `struct(suffix, fragment_name)` entries.

    `suffix` names the target, `<platform set>_<suffix>`. `fragment_name` is the composer's name for it. The order is
    the composition order: `platform_resources`, then the packed content-module jars, then the platform assets. This is
    the one owner of that list; the declaring macro and the label lookup both read it.
    """
    plan = _plan(tables, product)
    layout = [
        struct(suffix = _fragment_suffix(_PLATFORM_RESOURCES_FRAGMENT), fragment_name = _PLATFORM_RESOURCES_FRAGMENT),
        struct(suffix = PACKED_JARS_COMPONENT, fragment_name = PACKED_JARS_COMPONENT),
    ]
    if plan.platform_assets:
        layout.append(struct(suffix = "assets", fragment_name = PLATFORM_ASSETS_COMPONENT))
    return layout

def _has_runtime_module_repository(tables, product):
    """Whether the plan of `product` has the runtime module repository fragment.

    The generator states it for a product with a row that asks for the repository. `_platform_fragment_layout` never
    lists the fragment.
    """
    return getattr(_plan(tables, product), "runtime_module_repository", False)

def _modular_loader(tables, product):
    """Whether the generated plan says `product` starts through the modular loader, which reads the repository at every start."""
    return getattr(_plan(tables, product), "modular_loader", False)

def _platform_fragments(tables, product, runtime_module_repository = False):
    """The platform fragment labels of one split product, for a distribution declared outside the build package.

    Returns `struct(fragments, fragment_names)` in composition order, with absolute labels into `tables.build_package`.
    The product's platform set must be declared there through the platform set macro. For a community product, the
    labels name the community platform set in the community build package. With `runtime_module_repository`, or for a
    product the plan marks `modular_loader`, the list ends with the `platform_runtime_module_repository` component; a
    product whose plan has none fails.
    """
    community = _community_product(tables, product)
    build_package = community.build_package if community != None else tables.build_package
    platform_set = (community.product if community != None else tables.product)(product).platform_set
    layout = _platform_fragment_layout(tables, product)
    fragments = ["%s:%s_%s" % (build_package, platform_set, entry.suffix) for entry in layout]
    fragment_names = [entry.fragment_name for entry in layout]
    if runtime_module_repository or _modular_loader(tables, product):
        if not _has_runtime_module_repository(tables, product):
            fail("The generated dev-distribution plan of '%s' has no runtime module repository fragment: no run configuration of the product asks for one" % product)
        fragments.append("%s:%s_%s" % (build_package, platform_set, _fragment_suffix(tables.runtime_module_repository_fragment)))
        fragment_names.append(tables.runtime_module_repository_fragment)
    return struct(
        fragments = fragments,
        fragment_names = fragment_names,
    )

def _platform_set(tables, product, name, target_platform, visibility):
    """The platform set of one split product. The factory result documents it as `platform_set`."""
    if _community_product(tables, product) != None:
        fail("Product '%s' is a community product, so the community half declares its platform set; compose it through platform_fragments" % product)
    name = name or tables.product(product).platform_set
    plan = _plan(tables, product)

    def per_platform(value_of):
        # `value_of` the target platform, or a `select` of it over the host platforms when the set has no target platform.
        if target_platform:
            return value_of(target_platform)
        return select({"@community//build:host_" + platform: value_of(platform) for platform in HOST_PLATFORMS})

    # The packing targets of the `lib/` jars. The `dev_dist_platform_payload` target below reads them once and answers
    # every consumer from one provider, so the component that composes the jars in, the runtime module repository and
    # the reference target cannot disagree.
    platform_lib_payload = tables.platform_lib_payload(product)

    # The `plugins/plugin-classpath.txt` prefix that `dev_dist_product_descriptor` writes. The packed-jars component
    # carries it to the composer.
    plugin_classpath_prefix = tables.fragment_inputs[product][tables.platform_lib_fragment].plugin_classpath_prefix

    def runtime_module_repository_component():
        # The runtime module repository from Bazel facts, see `dev_dist_runtime_module_repository`. The component places
        # its two files in `modules/`. Returns the label of the component.
        catalogue = _plugin_component_catalogue(tables, product)
        repository = name + "_runtime_module_repository_files"
        dev_dist_runtime_layout_parts(
            name = repository + "_parts",
            plugins = _plugin_components(target_platform, catalogue.bundled).labels,
            product_info = tables.product_info_label(product),
            tags = ["manual"],
        )

        # The embedded frontend is a frontend-only plugin of the product, and so are the plugins that only the frontend
        # bundles. Their components are the frontend product's. They are configured for this product, as the Kotlin
        # fragment lays them out with the build context of this product.
        frontend_attrs = {}
        frontend = getattr(plan, "embedded_frontend", "")
        if frontend:
            frontend_catalogue = _plugin_component_catalogue(tables, frontend)
            frontend_order = tables.platform_jar_orders[frontend]
            bundled = {entry.main_module: True for entry in catalogue.bundled}
            frontend_only = [entry.main_module for entry in frontend_catalogue.bundled if entry.main_module not in bundled]
            dev_dist_runtime_layout_parts(
                name = repository + "_frontend_parts",
                plugins = _plugin_components(target_platform, frontend_catalogue.bundled, selected_modules = frontend_only).labels,
                product_info = tables.product_info_label(product),
                tags = ["manual"],
            )
            frontend_attrs = {
                "frontend_platform_payload": "%s:%s_platform_payload" % (tables.build_package, tables.product(frontend).platform_set),
                "frontend_core_module": tables.plans[frontend].application_info_module,
                "frontend_core_descriptor": _product_descriptor(tables, frontend),
                "frontend_first_jars": frontend_order.first,
                "frontend_last_jars": frontend_order.last,
                "frontend_plugins": ":" + repository + "_frontend_parts",
            }
        order = tables.platform_jar_orders[product]
        dev_dist_runtime_module_repository(
            name = repository,
            platform_payload = ":" + name + "_platform_payload",
            core_module = plan.application_info_module,
            core_descriptor = _product_descriptor(tables, product),
            first_jars = order.first,
            last_jars = order.last,
            plugins = ":" + repository + "_parts",
            ide_properties = [":%s_product_files/idea.properties" % name],
            project_model_tree = tables.project_model_tree,
            bazel_targets_json = tables.bazel_targets_json,
            tags = ["manual"],
            **frontend_attrs
        )
        component = name + "_" + _fragment_suffix(tables.runtime_module_repository_fragment)
        intellij_dev_packed_jars_component(
            name = component,
            visibility = visibility,
            tags = ["manual"],
            collector = tables.collector,
            component_name = tables.runtime_module_repository_fragment,
            platform_prefix = product,
            target_platform = target_platform,
            files = {
                ":%s.home/modules/module-descriptors.dat" % repository: "modules/module-descriptors.dat",
                ":%s.home/modules/module-descriptors.jar" % repository: "modules/module-descriptors.jar",
            },
        )
        return ":" + component

    def packed_jars_component():
        # The `content_module_jar` targets of the payload and one `dev_dist_platform_jar` target per residual jar. The
        # component composes their jars in, and the reference target packs the same jars and nothing else.
        residual_jars = []
        residual_jar_targets = {}
        for destination, jar in getattr(tables.fragment_inputs[product][tables.platform_lib_fragment], "residual_jars", {}).items():
            # A destination can name a subdirectory of `lib/`, and a target name cannot hold a `/`. Two destinations
            # that differ only in a separator would collapse to one target, which Bazel reports as a duplicate rule in
            # a generated file - so name the pair here, where the destinations are still readable.
            target_name = name + "_platform_jar_" + destination.replace("/", "_")
            if target_name in residual_jar_targets:
                fail("%s and %s both need the target '%s'" % (residual_jar_targets[target_name], destination, target_name))
            residual_jar_targets[target_name] = destination
            dev_dist_platform_jar(
                name = target_name,
                relative_output_file = destination,
                modules = getattr(jar, "modules", []),
                libraries = getattr(jar, "libraries", []),
                # The application-info module jar: the product descriptor and the stamped application info.
                patches = getattr(jar, "patches", {}),
                patched_module = getattr(jar, "patched_module", ""),
            )
            residual_jars.append(":" + target_name)

        # A content module jar whose presigned library carries native files has a tree per platform. The payload takes
        # the tree of the one platform the set is for, chosen the way `_native_bin_files` chooses the OS of `bin/`.
        dev_dist_platform_payload(
            name = name + "_platform_payload",
            packed = platform_lib_payload.packed + residual_jars,
            # The packed jars the module system loads. The payload puts every other direct child of `lib/` on the core
            # classpath.
            module_system_loaded = platform_lib_payload.module_system_loaded,
            native_platform = per_platform(lambda platform: platform),
        )

        intellij_dev_packed_jars_component(
            name = name + "_" + PACKED_JARS_COMPONENT,
            visibility = visibility,
            collector = tables.collector,
            component_name = PACKED_JARS_COMPONENT,
            platform_prefix = product,
            target_platform = target_platform,
            platform_payload = ":" + name + "_platform_payload",
            plugin_classpath_prefix = plugin_classpath_prefix,
        )

    def product_files_component():
        # `build.txt`, `bin/idea.properties`, the vmoptions file and `bin/product-info.json`. The product files action
        # renders them from the launch model the plan generator writes for the product, so the component reads no project
        # model and starts no JVM. The action reads the application info sources and `build.txt` for the version, the
        # names and the build number. The rendered files have fixed names, and the component maps each one to its path in
        # the distribution. The component also declares the main class of the launch model.
        files = name + "_product_files"
        application_info = tables.application_infos[product]
        dev_dist_product_files(
            name = files,
            tags = ["manual"],
            model = tables.launch_models[product],
            application_info = application_info.source,
            host_application_info = getattr(application_info, "host", None),
            replacements = getattr(application_info, "replacements", []),
            idea_properties = plan.launch.idea_properties,
            platform = per_platform(lambda platform: platform),
            build_txt = files + "/build.txt",
            idea_properties_out = files + "/idea.properties",
            vmoptions = files + "/vmoptions",
            product_info = files + "/product-info.json",
        )

        def placed(platform):
            return {
                ":%s/build.txt" % files: "build.txt",
                ":%s/idea.properties" % files: "bin/idea.properties",
                ":%s/vmoptions" % files: "bin/" + plan.launch.vmoptions[platform_parts(platform).os],
                ":%s/product-info.json" % files: "bin/product-info.json",
            }

        intellij_dev_packed_jars_component(
            name = name + "_" + _fragment_suffix(_PLATFORM_RESOURCES_FRAGMENT),
            visibility = visibility,
            tags = ["manual"],
            collector = tables.collector,
            component_name = _PLATFORM_RESOURCES_FRAGMENT,
            platform_prefix = product,
            target_platform = target_platform,
            files = per_platform(placed),
            main_class = plan.launch.main_class,
        )

    def assets_component():
        # The generated plan states each file as `struct(source, path, executable)` per host platform. The rule takes
        # the files as two label-to-path dicts, one per executable bit.
        def asset_files(entries, executable):
            return {entry.source: entry.path for entry in entries if entry.executable == executable}

        def selected(executable):
            return per_platform(lambda platform: asset_files(plan.platform_assets.get(platform, []), executable))

        intellij_dev_packed_jars_component(
            name = name + "_assets",
            visibility = visibility,
            tags = ["manual"],
            collector = tables.collector,
            component_name = PLATFORM_ASSETS_COMPONENT,
            platform_prefix = product,
            target_platform = target_platform,
            files = selected(False),
            executable_files = selected(True),
        )

    # Declared in the layout's order, which is the composition order the returned lists state.
    layout = _platform_fragment_layout(tables, product)
    for entry in layout:
        if entry.fragment_name == _PLATFORM_RESOURCES_FRAGMENT:
            product_files_component()
        elif entry.fragment_name == PACKED_JARS_COMPONENT:
            packed_jars_component()
        elif entry.fragment_name == PLATFORM_ASSETS_COMPONENT:
            assets_component()
        else:
            fail("Platform fragment layout of '%s' names the unknown component '%s'" % (product, entry.fragment_name))

    # Declared after the layout and returned apart from it: only a row that asks for the runtime module repository
    # composes the component. Every row is a host row, so a set for an explicit target platform declares it only for a
    # product that needs it at every start.
    runtime_module_repository = None
    if _has_runtime_module_repository(tables, product) and (not target_platform or _modular_loader(tables, product)):
        runtime_module_repository = runtime_module_repository_component()

    return struct(
        fragments = [":" + name + "_" + entry.suffix for entry in layout],
        fragment_names = [entry.fragment_name for entry in layout],
        runtime_module_repository = runtime_module_repository,
    )

def _declare_fragments_dist(
        tables,
        name,
        visibility,
        platform_fragments,
        platform_fragment_names,
        product,
        additional_modules,
        target_platform,
        create_local_launch,
        catalogue):
    """Declares the distribution `name` and, with `create_local_launch`, `<name>_launch` over `catalogue`.

    `catalogue` is `_plugin_component_catalogue(tables, product)`. A caller that declares several distributions of one
    product reads it once and passes it to each.
    """
    bundled_components = _plugin_components(target_platform, catalogue.bundled)

    # The distribution composes every bundled component, and the additional components its `additional_modules`
    # name. A bundled module that `additional_modules` also names is composed once, as a bundled component.
    #
    # Only the assembly is split this way. What the distribution *declares* it contains stays the whole
    # `additional_modules` below, because that is what a consumer compares its own needs against: a claimed
    # module is in the distribution just as much as an unclaimed one, and declaring only the unclaimed ones
    # once made every AIR UI lane refuse to launch over a plugin that was sitting in the IDE.
    additional_component_modules = []
    for module in additional_modules:
        tier = catalogue.tiers.get(module)
        if tier == None:
            fail("Additional plugin module '%s' of %s has no generated component" % (module, name))
        if tier == "bundled":
            continue
        additional_component_modules.append(module)
    additional_components = _plugin_components(
        target_platform,
        catalogue.additional,
        selected_modules = additional_component_modules,
    )

    # The plugin components are declared apart from the fragments: the dist rule configures them for the product, and
    # the platform fragments keep their configuration. Every plugin of the distribution is a component.
    fragments = list(platform_fragments)
    plugin_components = bundled_components.labels + additional_components.labels

    # A plugin component's fragment name is its main module (`component_name` in `dev_plugin_component`).
    expect_fragments = (
        list(platform_fragment_names) +
        bundled_components.modules +
        additional_components.modules
    )

    for local_launch in [False, True] if create_local_launch else [False]:
        intellij_dev_fragments_dist(
            name = name + ("_launch" if local_launch else ""),
            visibility = visibility,
            tags = ["manual"],
            composer = tables.composer,
            fragments = fragments,
            plugin_components = plugin_components,
            product_info = _product_info_label(tables, product),
            expect_fragments = expect_fragments,
            additional_modules = additional_modules,
            local_launch = local_launch,
        )

def _check_plan(tables, name, product):
    """Fails when the generated plan cannot serve the launcher `name` of `product`.

    The one place a run configuration reads the generator's switch. The split distributions of a generator half
    write a plan entry for a listed product and nothing for the others. A community product has its plan entry in the
    community half. The message names the recipe: the map to edit and the tool run that writes the plan. The generator
    stops on a module that a run configuration names and that it cannot plan. A module outside the plugin components of
    the product fails in the distribution macro.
    """
    if _community_product(tables, product) == None and product not in tables.plans:
        fail("%s: product '%s' has no generated dev-distribution plan; add it to the split distributions of its generator half and run plugin-model-tool" % (name, product))

def _distribution_name(names):
    """The distribution name of a group of rows: the shortest row name, ties in alphabetical order."""
    return sorted(names, key = lambda name: (len(name), name))[0]

def _distribution_groups(rows):
    """The rows of `DEV_RUN_CONFIGURATIONS` grouped by the distribution they share.

    The result maps `(product, additional_modules, runtime_module_repository)` to the row names, in row order.
    """
    groups = {}
    for name, row in rows.items():
        key = (row.product, tuple(getattr(row, "additional_modules", [])), getattr(row, "runtime_module_repository", False))
        groups.setdefault(key, []).append(name)
    return groups

def _declare_run_distribution(tables, name, product, additional_modules, runtime_module_repository, catalogue, visibility):
    """The distribution of one or more launchers: `<name>_dist`, `<name>_dist_launch`, and the pair a launcher reads.

    `<name>_distribution` is the self-contained `_dist` on Windows and the metadata-only `_dist_launch` elsewhere, which a
    launcher links into a local home from the component runfiles. `<name>_ide_config` is its config file. A distribution
    with `runtime_module_repository` composes the `platform_runtime_module_repository` component of its product, which
    places the `modules/` files of `dev_dist_runtime_module_repository`.
    """
    _check_plan(tables, name, product)
    platform = _platform_fragments(tables, product, runtime_module_repository = runtime_module_repository)
    _declare_fragments_dist(
        tables = tables,
        name = name + "_dist",
        visibility = visibility,
        platform_fragments = platform.fragments,
        platform_fragment_names = platform.fragment_names,
        product = product,
        additional_modules = additional_modules,
        target_platform = "",
        create_local_launch = True,
        catalogue = catalogue,
    )
    dist = "//%s:%s_dist" % (native.package_name(), name)
    native.alias(
        name = name + "_distribution",
        actual = select({
            "@platforms//os:windows": dist,
            "//conditions:default": dist + "_launch",
        }),
        tags = ["manual"],
        visibility = ["//visibility:private"],
    )
    intellij_dev_dist_config(
        name = name + "_ide_config",
        dist = "//%s:%s_distribution" % (native.package_name(), name),
        tags = ["manual"],
        visibility = ["//visibility:private"],
    )

def _before_run(tables, name, jvm_flags, compile_clion_backend_before_run):
    """The `before_run_main_class` and `before_run_runtime_deps` of a launcher, from the `before_run` hook of the tables.

    Without the hook, a launcher gets no before-run step, and a launcher that asks for one fails.
    """
    if tables.before_run != None:
        return tables.before_run(name, jvm_flags, compile_clion_backend_before_run)
    if compile_clion_backend_before_run:
        fail("%s: compile_clion_backend_before_run needs the before_run hook, and the tables of this half have none" % name)
    return struct(before_run_main_class = "", before_run_runtime_deps = [])

def _declare_run_launcher(
        tables,
        name,
        distribution,
        product,
        additional_modules,
        jvm_flags,
        env,
        data,
        program_args,
        compile_clion_backend_before_run,
        visibility):
    """The launcher `name`, the launcher over the distribution `_declare_run_distribution` declared as `distribution`."""
    for flag in jvm_flags:
        if flag.startswith("-Dadditional.modules="):
            fail("%s: pass the modules of '%s' as additional_modules, not in jvm_flags" % (name, flag))
        if flag == _GENERATE_RUNTIME_MODULE_REPOSITORY_FLAG:
            fail("%s: state '%s' as the attribute of intellij_dev_run_configuration, not in jvm_flags" % (name, flag))
        if flag.startswith(_ROOT_MODULE_OVERRIDE_FLAG_PREFIX):
            fail("%s: state the root module as `rootModule` of a build/dev-build.json key, not as '%s'" % (name, flag))
        if _names_jps_output(flag):
            fail(("%s: '%s' names a JPS output below out/, which a dev launch does not build. State a file of the " +
                  "distribution, such as runtime_module_repository = True for the runtime module repository, or a path " +
                  "outside out/classes, out/production and out/test") % (name, flag))

    before_run = _before_run(tables, name, jvm_flags, compile_clion_backend_before_run)
    if tables.launcher_jvm_flags != None:
        jvm_flags = tables.launcher_jvm_flags(product, additional_modules, jvm_flags)

    # The distribution states its prefix: the launcher reads `-Didea.platform.prefix` from `product-info.json`, as a
    # production launcher does, and a caller's value would win over it.
    intellij_dev_launcher_binary(
        name = name,
        visibility = visibility,
        dist = "//%s:%s_distribution" % (native.package_name(), distribution),
        ide_config = "//%s:%s_ide_config" % (native.package_name(), distribution),
        jvm_flags = jvm_flags,
        env = env,
        data = data,
        program_args = program_args,
        before_run_main_class = before_run.before_run_main_class,
        before_run_runtime_deps = before_run.before_run_runtime_deps,
    )

def _run_configurations(tables, rows):
    """The launchers of `DEV_RUN_CONFIGURATIONS`. The factory result documents it as `run_configurations`."""
    catalogues = {}
    for key, names in _distribution_groups(rows).items():
        product, additional_modules, runtime_module_repository = key
        if product not in catalogues:
            catalogues[product] = _plugin_component_catalogue(tables, product)
        distribution = _distribution_name(names)
        _declare_run_distribution(
            tables = tables,
            name = distribution,
            product = product,
            additional_modules = list(additional_modules),
            runtime_module_repository = runtime_module_repository,
            catalogue = catalogues[product],
            visibility = None,
        )
        for name in names:
            row = rows[name]
            _declare_run_launcher(
                tables = tables,
                name = name,
                distribution = distribution,
                product = product,
                additional_modules = additional_modules,
                jvm_flags = getattr(row, "jvm_flags", []),
                env = getattr(row, "env", {}),
                data = [],
                program_args = getattr(row, "program_args", []),
                compile_clion_backend_before_run = getattr(row, "compile_clion_backend_before_run", False),
                visibility = None,
            )

def _run_configuration(
        tables,
        name,
        visibility,
        product,
        additional_modules,
        jvm_flags,
        env,
        data,
        program_args,
        generate_runtime_module_repository,
        compile_clion_backend_before_run):
    """One hand-written launcher over a distribution of its own. The factory result documents it as `run_configuration`."""
    _declare_run_distribution(
        tables = tables,
        name = name,
        product = product,
        additional_modules = additional_modules,
        runtime_module_repository = generate_runtime_module_repository,
        catalogue = _plugin_component_catalogue(tables, product),
        visibility = visibility,
    )
    _declare_run_launcher(
        tables = tables,
        name = name,
        distribution = name,
        product = product,
        additional_modules = additional_modules,
        jvm_flags = jvm_flags,
        env = env,
        data = data,
        program_args = program_args,
        compile_clion_backend_before_run = compile_clion_backend_before_run,
        visibility = visibility,
    )

_RUN_CONFIGURATION_DOC = """One dev launcher written by hand, `bazel run //<package>:<name>`, over a distribution of its own.

    It declares what `run_configurations` declares for one generated row: the distribution `<name>_dist` with
    `<name>_dist_launch`, and `<name>`, an `intellij_dev_launcher` over it. The rows of `.idea/runConfigurations` do not
    use this macro: `dev_server_run_configurations.bzl` passes them to `run_configurations`, which lets rows share a
    distribution.

    A launcher the generated plan cannot serve fails at load time (`check_plan`): a product that the split
    distributions of its generator half do not name. A module of `additional_modules` without a
    generated component fails in the distribution macro.
    """

_RUN_CONFIGURATION_ATTRS = {
    "product": attr.string(mandatory = True, configurable = False, doc = "The build/dev-build.json key of the product; for a frontend, the base product's key plus 'JetBrainsClient'; for a frontend debug wrapper, a key with a `rootModule`."),
    "additional_modules": attr.string_list(default = [], configurable = False, doc = "Plugin modules included on top of the product's own, the -Dadditional.modules of a run configuration."),
    "jvm_flags": attr.string_list(default = [], configurable = False, doc = "Additional JVM flags, without -Dadditional.modules and without the two properties the boolean attributes state."),
    "env": attr.string_dict(default = {}, configurable = False, doc = "Environment variables to set when running the launcher."),
    "data": attr.label_list(default = [], doc = "Extra data dependencies of the launcher."),
    "program_args": attr.string_list(default = [], configurable = False, doc = "Program arguments of the launcher."),
    "generate_runtime_module_repository": attr.bool(default = False, configurable = False, doc = "The -Dintellij.build.generate.runtime.module.repository=true of a run configuration. The distribution composes the `platform_runtime_module_repository` component of its product."),
    "compile_clion_backend_before_run": attr.bool(default = False, configurable = False, doc = "The before-run step of a run configuration. The `before_run` hook of the tables declares it. Without the hook, the attribute fails."),
}

def intellij_dev_dist_declarations(tables):
    """Binds the declarations of a split dev distribution to the generated tables of one half.

    Call it once at load time of a `.bzl` file, and assign each macro of the result to a global of that file. Bazel
    names the symbolic macro `run_configuration` after that global.

    `tables` is a struct with these fields:

    - `plans`, `launch_models`: the generated plan tables, keyed by product.
    - `platform_jar_orders`: the generated platform jar orders, keyed by product. Each entry is `struct(first, last)`,
      the platform jars before and after the sorted range.
    - `application_infos`: the generated application info sources, keyed by product. Each entry is
      `struct(source, host, replacements)`, where `host` and `replacements` are present only when set.
    - `fragment_inputs`: the generated input table, keyed by product.
    - `plugin_components`: the generated component catalogue, keyed by product.
    - `product(key)`: the product entry, with its `platform_set`. It fails for an unknown product.
    - `platform_lib_fragment`, `runtime_module_repository_fragment`: the payload names of the input tables.
    - `platform_lib_payload(product)`: the `struct(packed, module_system_loaded)` of the packing targets of the `lib/`
      jars, as `dev_dist_packed_labels` returns it.
    - `bazel_targets_json`, `project_model_tree`, `collector`, `composer`: the labels of the shared inputs and tools.
    - `build_package`: the package that declares the platform sets, for example `//build`.
    - `product_info_label(product)`: the label of the product info of `product`.
    - `launcher_jvm_flags(product, additional_modules, jvm_flags)`: the `jvm_flags` of a launcher, or `None`.
    - `before_run(name, jvm_flags, compile_clion_backend_before_run)`: the before-run step of a launcher, or `None`.
    - `community`: the community products and the community tables that a distribution of one reads, or `None`.
      See `_community_product` for the fields.

    Returns a struct of the macros and the helpers below.
    """

    def platform_set(product, name = None, target_platform = "", visibility = None):
        """The platform set of one split product: its components for one target platform, which its distributions share.

        `product` is the `build/dev-build.json` key and a product entry. The community rules take it as `platform_prefix`.
        `name` defaults to the entry's `platform_set`. A second set of the same product for another target platform
        names it. `visibility` is the visibility of every component of the set. A community product fails: the
        community half declares its set, and a distribution of it reads the set through `platform_fragments`.

        The set declares only what a distribution composes. A reference of a gate is declared elsewhere, with the same
        `product`, `name` and `target_platform`.

        Returns a struct of the component labels in composition order. `runtime_module_repository` is the label of the
        `platform_runtime_module_repository` component when the plan has one, else `None`. It is kept out of
        `fragments`: only a row that asks for the repository composes it, or every row of a product the plan marks
        `modular_loader`. A set with a `target_platform` has none unless the product is `modular_loader`, because every
        row is a host row.
        """
        return _platform_set(tables, product, name, target_platform, visibility)

    def platform_fragments(product, runtime_module_repository = False):
        """The platform fragment labels of one split product. See `_platform_fragments`."""
        return _platform_fragments(tables, product, runtime_module_repository = runtime_module_repository)

    def declare_fragments_dist(
            name,
            visibility,
            platform_fragments,
            platform_fragment_names,
            product,
            additional_modules,
            target_platform,
            create_local_launch,
            catalogue):
        """Declares the distribution `name` and, with `create_local_launch`, `<name>_launch` over `catalogue`."""
        _declare_fragments_dist(
            tables = tables,
            name = name,
            visibility = visibility,
            platform_fragments = platform_fragments,
            platform_fragment_names = platform_fragment_names,
            product = product,
            additional_modules = additional_modules,
            target_platform = target_platform,
            create_local_launch = create_local_launch,
            catalogue = catalogue,
        )

    def check_plan(name, product):
        """Fails when the generated plan cannot serve the launcher `name` of `product`. See `_check_plan`."""
        _check_plan(tables, name, product)

    def run_configurations(rows):
        """The launchers of `DEV_RUN_CONFIGURATIONS`, the rows the plan generator writes into `dev_server_run_configurations.bzl`.

        `rows` maps a launcher name to `struct(product, additional_modules?, jvm_flags?, program_args?, env?,
        runtime_module_repository?, compile_clion_backend_before_run?)`. Rows with the same product,
        `additional_modules` and `runtime_module_repository` share one distribution, named by `distribution_name`, and
        each row is one launcher over it. A launch-time flag therefore never splits a distribution, and two rows of one
        product compose once. The plugin catalogue of a product is read once for all its distributions.
        """
        _run_configurations(tables, rows)

    def run_configuration_impl(
            name,
            visibility,
            product,
            additional_modules,
            jvm_flags,
            env,
            data,
            program_args,
            generate_runtime_module_repository,
            compile_clion_backend_before_run):
        _run_configuration(
            tables,
            name = name,
            visibility = visibility,
            product = product,
            additional_modules = additional_modules,
            jvm_flags = jvm_flags,
            env = env,
            data = data,
            program_args = program_args,
            generate_runtime_module_repository = generate_runtime_module_repository,
            compile_clion_backend_before_run = compile_clion_backend_before_run,
        )

    return struct(
        platform_set = platform_set,
        platform_fragments = platform_fragments,
        declare_fragments_dist = declare_fragments_dist,
        check_plan = check_plan,
        run_configurations = run_configurations,
        run_configuration = macro(
            doc = _RUN_CONFIGURATION_DOC,
            implementation = run_configuration_impl,
            attrs = _RUN_CONFIGURATION_ATTRS,
        ),
        distribution_name = _distribution_name,
        distribution_groups = _distribution_groups,
        plugin_component_catalogue = lambda product: _plugin_component_catalogue(tables, product),
        plugin_components = _plugin_components,
        fragment_suffix = _fragment_suffix,
        has_runtime_module_repository = lambda product: _has_runtime_module_repository(tables, product),
        modular_loader = lambda product: _modular_loader(tables, product),
        product_descriptor = lambda product: _product_descriptor(tables, product),
        project_model_tree = tables.project_model_tree,
    )
