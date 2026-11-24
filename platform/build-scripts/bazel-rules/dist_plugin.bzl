"""Declare one plugin through the descriptor and packing rules."""

load(":content_module_jar.bzl", "content_module_jar_target_name")
load(":dev_plugin.bzl", "dev_plugin", "dist_plugin_directory", "is_library_token")
load(":dist_frontend_application_info.bzl", "dist_frontend_application_info")
load(":dist_plugin_descriptor.bzl", "dist_plugin_descriptor", "dist_plugin_descriptor_target_name")
load(":jvm_runtime_jars.bzl", "jvm_runtime_jars")

_SHARED_LEAF_ATTRS = ["tags", "visibility"]
_MAX_REPORTED_STALE_MODULES = 20

# The runtime jars of the plugin modules, for a build-scripts binary that packs the plugin.
_RUNTIME_JARS_SUFFIX = "_runtime_jars"

# What a packed plugin component's target is called, after the main module. Public through
# `dist_plugin_component_target_name`, because the generated product map names the target.
_DEV_PLUGIN_SUFFIX = "_dev_plugin"

def dist_plugin_component_target_name(main_module):
    """The packed component's target name - `"intellij.json"` gives `"intellij.json_dev_plugin"`."""
    return main_module + _DEV_PLUGIN_SUFFIX

def resolve_module_labels(main_module, names, module_labels):
    """The labels of `names` from the `module_labels` of a `dist_plugin` call, and the problems of that map.

    A name without a label is stale: so is a name whose label names no package and no target. A label of a module that
    `names` does not hold is extra. Both come from a section that the generator has not written again yet, for example
    after a merge. The macro skips a stale name with a warning, so the package still loads and the generator can repair
    it.

    Args:
        main_module: the main module of the call, which the messages name.
        names: the module names that the call names: the main module, the content modules and the module tokens of `jars`.
        module_labels: the `module_labels` of the call.

    Returns:
        `struct(labels, stale, messages)`: the label of each name that has one, the stale names in the order of `names`,
        and one warning for the stale names and one for the extra labels.
    """
    named = {name: True for name in names}
    labels = {}
    stale = []
    for name in named:
        label = module_labels.get(name)
        if type(label) == "string" and ":" in label:
            labels[name] = label
        else:
            stale.append(name)
    extra = sorted([name for name in module_labels if name not in named])
    messages = []
    if stale:
        shown = stale[:_MAX_REPORTED_STALE_MODULES]
        remainder = len(stale) - len(shown)
        messages.append("WARN: dist_plugin '%s' omits modules without a label in `module_labels`: %s%s. Regenerate the dev sections." % (
            main_module,
            ", ".join(shown),
            " (and %d more)" % remainder if remainder > 0 else "",
        ))
    if extra:
        messages.append("WARN: dist_plugin '%s' states labels of modules that it does not name: %s. Regenerate the dev sections." % (
            main_module,
            ", ".join(extra),
        ))
    return struct(labels = labels, stale = stale, messages = messages)

def _content_module_jar_label(module_label):
    return module_label.rpartition(":")[0] + ":" + content_module_jar_target_name(module_label)

def dist_plugin(
        main_module,
        module_labels = {},
        content_modules = [],
        descriptor = "",
        variants = [],
        frontend_application_info = "",
        frontend_product_application_info = "",
        jars = {},
        module_jar_paths = {},
        content_module_jar_labels = {},
        classpath_jars = [],
        files = {},
        file_prefixes = {},
        executables = [],
        tree_files = [],
        directory_name = "",
        **descriptor_attrs):
    """Declare plugin modules and derive their build targets.

    Args:
        main_module: The main JPS module. It identifies every leaf.
        module_labels: The rule label of each module that the call names, keyed by module name: the main module, the
            content modules and the module tokens of `jars`. The generator copies each label from the targets JSON. A
            name without a label is stale: the macro warns and omits it, see `resolve_module_labels`. A section of an
            older generator states no map, so its package still loads, and the generator can write the section again.
        content_modules: The complete ordered list of plugin member JPS module names.
        descriptor: The main descriptor label, package-relative.
        variants: Layout variants. An empty list selects the common variant.
        jars: The packed jars of a simple plugin, keyed by destination relative to the plugin directory and valued by
            source tokens in merge order. A token is a JPS module name or a library container label. A content module
            no jar merges ships its own `content_module_jar` jar as `lib/modules/<module>.jar`. Empty for a
            plugin the plan driven chain packs. A test-only module is declared in a cross-half `dev_plugin`.
        module_jar_paths: The destination of a reused content module jar when it is not `lib/modules/<module>.jar`,
            keyed by module name.
        content_module_jar_labels: The `content_module_jar` label of a reused content module whose call is not in the
            package of the module, keyed by module name. The generator states it for a call it relocates. Any other
            reused module takes the label the package of the module gives.
        classpath_jars: The classpath order of every jar of a simple plugin, when the default order is not the plan's
            order. The default order is the `jars` keys, then the reused content module jars in `content_modules` order.
        files: The plain copies of a simple plugin, keyed by destination relative to the plugin directory and valued by
            the label of the source: a source file, the package's `:dist_resources` filegroup, another target that
            produces regular files, or a target that produces one directory artifact. A destination `file_prefixes`
            names copies every file of the label below the prefix to `<destination>/<relative path>`. A destination
            `tree_files` names copies every file of the directory artifact in the same way. Any other destination copies
            the one file the label produces. A copied file is not on the plugin classpath. Requires `jars`.
        file_prefixes: The repository-relative prefix of each source tree copy in `files`, keyed by destination.
        executables: The files the plugin ships with the executable bit, as `java.nio` globs relative to the plugin
            directory. The collector marks each file of the component that a pattern matches. Requires `jars`.
        tree_files: The destinations of `files` whose label produces one directory artifact. Each copied file gets the
            mode 0644.
        frontend_application_info: The application info template of the embedded frontend. Stated together with the other
            `frontend_` label by the plugin that packs the JetBrains Client, and empty for every other plugin. A
            standalone `dist_embedded_product_descriptor` call declares the embedded descriptor of that plugin.
        frontend_product_application_info: The application info of the product the frontend takes its names and version from.
        directory_name: The plugin directory, when the layout does not take the derived one. Only the packed component
            reads it.
        **descriptor_attrs: Other descriptor attributes. Shared leaf attributes are refused.
    """
    if not main_module or type(module_labels) != "dict":
        fail("dist_plugin requires a main module and a dict of module labels")
    shared = [key for key in _SHARED_LEAF_ATTRS if key in descriptor_attrs]
    if shared:
        fail("dist_plugin: %s states shared leaf attributes: %s" % (main_module, shared))
    frontend_labels = [frontend_application_info, frontend_product_application_info]
    if any(frontend_labels) and not all(frontend_labels):
        fail("dist_plugin: %s states one of the two frontend application info labels, and a frontend states both" % main_module)
    if jars and variants:
        fail("dist_plugin: %s states jars and layout variants, and a packed plugin has one layout" % main_module)
    if jars and not descriptor:
        fail("dist_plugin: %s states jars and no descriptor" % main_module)
    if (files or file_prefixes or tree_files) and not jars:
        fail("dist_plugin: %s states copied files and no jars, and a copy belongs to a packed plugin" % main_module)
    if executables and not jars:
        fail("dist_plugin: %s states executables and no jars, and a complex plugin states them on its chain" % main_module)
    for destination in file_prefixes:
        if destination not in files:
            fail("dist_plugin: %s states a file prefix for '%s', which `files` does not copy" % (main_module, destination))
    for destination in tree_files:
        if destination not in files or destination in file_prefixes:
            fail("dist_plugin: %s names '%s' in `tree_files`, which `files` does not copy as one directory" % (main_module, destination))
    jar_tokens = [token for tokens in jars.values() for token in tokens]
    for module in content_module_jar_labels:
        if module not in content_modules or module in jar_tokens:
            fail("dist_plugin: %s states a content_module_jar label for '%s', which it does not reuse" % (main_module, module))

    if not descriptor:
        if variants or descriptor_attrs:
            fail("dist_plugin: %s states descriptor attributes and no descriptor" % main_module)
        if not content_modules:
            fail("dist_plugin: %s states neither content modules nor a descriptor" % main_module)

    jar_modules = [token for token in jar_tokens if not is_library_token(token)]
    resolved = resolve_module_labels(main_module, [main_module] + content_modules + jar_modules, module_labels)
    for message in resolved.messages:
        print(message)
    owner = resolved.labels.get(main_module)
    if owner == None:
        return
    content_module_labels = [resolved.labels[name] for name in content_modules if name in resolved.labels]

    # A plugin outside every product has no packed component. Its build-scripts binary names this target as data.
    jvm_runtime_jars(
        name = main_module + _RUNTIME_JARS_SUFFIX,
        modules = [owner] + content_module_labels,
        tags = ["manual"],
        visibility = ["//visibility:public"],
    )

    # The packed component's inputs. A module a jar names takes its label from `module_labels`, and a library token is
    # passed through as the label it is. A stale name skips the whole component below, so a plugin never ships with a
    # module missing.
    packed_modules = {}
    packed_libraries = []
    for token in jar_tokens:
        if is_library_token(token):
            if token not in packed_libraries:
                packed_libraries.append(token)
            continue
        label = resolved.labels.get(token)
        if label != None and label not in packed_modules:
            packed_modules[label] = token

    if descriptor:
        for variant in variants if variants else [""]:
            dist_plugin_descriptor(
                main_module = main_module,
                descriptor = descriptor,
                variant = variant,
                **descriptor_attrs
            )
    if jars and not resolved.stale:
        # A content module no jar merges is reused from its own packing target. Its label follows from the module's
        # label unless `content_module_jar_labels` states it, and a module without such a target fails when Bazel
        # analyses the component.
        content_module_jars = []
        for name in content_modules:
            label = resolved.labels[name]
            if label not in packed_modules:
                content_module_jars.append(content_module_jar_labels.get(name) or _content_module_jar_label(label))
        dev_plugin(
            name = dist_plugin_component_target_name(main_module),
            main_module = main_module,
            descriptor = ":" + dist_plugin_descriptor_target_name(main_module),
            plugin_directory = dist_plugin_directory(main_module, directory_name),
            modules = packed_modules,
            libraries = packed_libraries,
            content_module_jars = content_module_jars,
            jars = jars,
            module_jar_paths = module_jar_paths,
            classpath_jars = classpath_jars,
            files = files,
            file_prefixes = file_prefixes,
            executables = executables,
            tree_files = tree_files,
            # The stamp class of the component follows from the facts of its descriptor, so the call states them once.
            stamp_class_modes = sorted([
                mode
                for mode, modules in descriptor_attrs.get("mode_refused_content_modules", {}).items()
                if modules
            ]),
            embed_content_modules = descriptor_attrs.get("embed_content_modules", True),
            visibility = ["//visibility:public"],
        )
    if frontend_application_info:
        dist_frontend_application_info(
            main_module = main_module,
            client_application_info = frontend_application_info,
            product_application_info = frontend_product_application_info,
        )
