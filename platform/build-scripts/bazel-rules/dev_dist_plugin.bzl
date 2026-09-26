"""Declare one plugin through the descriptor and packing rules."""

load("//build:jps_target_derivation.bzl", "module_rule_label")
load(":content_module_jar.bzl", "content_module_jar_target_name")
load(":dev_dist_embedded_product_descriptor.bzl", "dev_dist_embedded_product_descriptor")
load(":dev_dist_frontend_application_info.bzl", "dev_dist_frontend_application_info")
load(":dev_dist_plugin_descriptor.bzl", "dev_dist_plugin_descriptor", "dev_dist_plugin_descriptor_target_name")
load(":dev_plugin.bzl", "dev_dist_plugin_directory", "dev_plugin", "is_library_token")

_SHARED_LEAF_ATTRS = ["tags", "visibility"]
_MAX_REPORTED_STALE_MODULES = 20

# What a packed plugin component's target is called, after the main module. Public through
# `dev_dist_plugin_component_target_name`, because the generated product map names the target.
_DEV_PLUGIN_SUFFIX = "_dev_plugin"

def dev_dist_plugin_component_target_name(main_module):
    """The packed component's target name - `"intellij.json"` gives `"intellij.json_dev_plugin"`."""
    return main_module + _DEV_PLUGIN_SUFFIX

def _content_module_jar_label(module_label):
    return module_label.rpartition(":")[0] + ":" + content_module_jar_target_name(module_label)

def _module_labels(names, module_targets, stale):
    result = []
    for name in names:
        label = module_rule_label(name, module_targets)
        if label == None:
            stale[name] = True
            continue
        result.append(label)
    return result

def _warn_stale(main_module, stale):
    if stale:
        names = sorted(stale)
        remainder = len(names) - _MAX_REPORTED_STALE_MODULES
        print("WARN: dev_dist_plugin '%s' omits missing modules: %s%s. Regenerate the dev sections." % (
            main_module,
            ", ".join(names[:_MAX_REPORTED_STALE_MODULES]),
            " (and %d more)" % remainder if remainder > 0 else "",
        ))

def dev_dist_plugin(
        main_module,
        module_targets,
        descriptor_index = {},
        descriptor_modules = [],
        content_modules = [],
        descriptor = "",
        variants = [],
        embedded_descriptor_source = "",
        embedded_descriptor = "",
        embedded_descriptor_module = "",
        embedded_descriptors = {},
        embedded_library_descriptors = {},
        embedded_modules = [],
        embedded_separate_jar = [],
        frontend_application_info = "",
        frontend_product_application_info = "",
        frontend_build_number = "",
        jars = {},
        module_jar_paths = {},
        classpath_jars = [],
        files = {},
        file_prefixes = {},
        executable_files = [],
        **descriptor_attrs):
    """Declare plugin modules and derive their build targets.

    Args:
        main_module: The main JPS module. It identifies every leaf.
        module_targets: The production map from the applicable JPS bridge. Entries contain one jar output label. The
            bridge's own `dev_dist_plugin` binds it, so a generated section states neither map.
        descriptor_index: The conventional descriptor index from that bridge. A content-only plugin ignores it.
        descriptor_modules: The exact conventional descriptor modules selected by the generator.
        content_modules: The complete ordered list of plugin member JPS module names.
        descriptor: The main descriptor path inside the main module's package.
        variants: Layout variants. An empty list selects the common variant.
        jars: The packed jars of a simple plugin, keyed by destination relative to the plugin directory and valued by
            source tokens in merge order. A token is a JPS module name or a library container label. A content module
            no jar merges ships its own `content_module_jar` jar as `lib/modules/<module>.jar`. Empty for a
            plugin the plan driven chain packs.
        module_jar_paths: The destination of a reused content module jar when it is not `lib/modules/<module>.jar`,
            keyed by module name.
        classpath_jars: The classpath order of every jar of a simple plugin, when the default order is not the plan's
            order. The default order is the `jars` keys, then the reused content module jars in `content_modules` order.
        files: The plain copies of a simple plugin, keyed by destination relative to the plugin directory and valued by
            the label of the source: a source file, the package's `:dev_dist_resources` filegroup or another target that
            produces regular files. A destination `file_prefixes` names copies every file of the label below the prefix
            to `<destination>/<relative path>`; any other destination copies the one file the label produces. A copied
            file is not on the plugin classpath. Requires `jars`.
        file_prefixes: The repository-relative prefix of each directory copy in `files`, keyed by destination.
        executable_files: The single-file destinations of `files` the distribution marks executable. This is the mode
            `withResource*` gives a file.
        embedded_descriptor_source: The direct label of an embedded product descriptor.
        embedded_descriptor: The embedded product descriptor path inside its module's package.
        embedded_descriptor_module: The JPS module that owns the embedded product descriptor.
        embedded_descriptors: Exact descriptor targets mapped to resolver load paths.
        embedded_library_descriptors: Ordered Java containers mapped to space-separated resolver load paths.
        embedded_modules: The embedded descriptor search scope by JPS module name.
        embedded_separate_jar: Embedded content modules packed into separate jars.
        frontend_application_info: The application info template of the embedded frontend. Stated together with the two
            other `frontend_` labels by the plugin that packs the JetBrains Client, and empty for every other plugin.
        frontend_product_application_info: The application info of the product the frontend takes its names and version from.
        frontend_build_number: The build number file the frontend build number is stamped from.
        **descriptor_attrs: Other descriptor attributes. Shared leaf attributes are refused.
    """
    if not main_module or type(module_targets) != "dict":
        fail("dev_dist_plugin requires a main module and a JPS target map")
    shared = [key for key in _SHARED_LEAF_ATTRS if key in descriptor_attrs]
    if shared:
        fail("dev_dist_plugin: %s states shared leaf attributes: %s" % (main_module, shared))
    if embedded_descriptor_source and (embedded_descriptor or embedded_descriptor_module):
        fail("dev_dist_plugin: %s states both direct and module-relative embedded descriptor sources" % main_module)
    if bool(embedded_descriptor) != bool(embedded_descriptor_module):
        fail("dev_dist_plugin: %s must state both embedded_descriptor and embedded_descriptor_module" % main_module)
    if not embedded_descriptor_source and not embedded_descriptor and (embedded_descriptors or embedded_library_descriptors or embedded_modules or embedded_separate_jar):
        fail("dev_dist_plugin: %s states embedded descriptor inputs without an embedded descriptor" % main_module)
    frontend_labels = [frontend_application_info, frontend_product_application_info, frontend_build_number]
    if any(frontend_labels) and not all(frontend_labels):
        fail("dev_dist_plugin: %s states some of the three frontend application info labels, and a frontend states all of them" % main_module)
    if frontend_application_info and not embedded_descriptor_source and not embedded_descriptor:
        fail("dev_dist_plugin: %s states a frontend application info without an embedded descriptor" % main_module)
    if jars and variants:
        fail("dev_dist_plugin: %s states jars and layout variants, and a packed plugin has one layout" % main_module)
    if jars and not descriptor:
        fail("dev_dist_plugin: %s states jars and no descriptor" % main_module)
    if (files or file_prefixes or executable_files) and not jars:
        fail("dev_dist_plugin: %s states copied files and no jars, and a copy belongs to a packed plugin" % main_module)
    for destination in file_prefixes:
        if destination not in files:
            fail("dev_dist_plugin: %s states a file prefix for '%s', which `files` does not copy" % (main_module, destination))
    for destination in executable_files:
        if destination not in files:
            fail("dev_dist_plugin: %s marks '%s' executable, which `files` does not copy" % (main_module, destination))
        if destination in file_prefixes:
            fail("dev_dist_plugin: %s marks the directory copy '%s' executable, and only a single file has a stated mode" % (main_module, destination))

    if not descriptor:
        if variants or descriptor_attrs or descriptor_modules:
            fail("dev_dist_plugin: %s states descriptor attributes and no descriptor" % main_module)
        if not content_modules:
            fail("dev_dist_plugin: %s states neither content modules nor a descriptor" % main_module)

    unresolved = []
    stale = {}
    owner = module_rule_label(main_module, module_targets)
    if owner == None:
        _warn_stale(main_module, {main_module: True})
        return
    descriptor_module = ":" + owner.rpartition(":")[2]

    # Nothing reads the labels. The resolution fills `stale`, and the `if jars and not stale` guard below reads it.
    _module_labels(content_modules, module_targets, stale)
    embedded_owner = None
    if embedded_descriptor_module:
        embedded_owner = module_rule_label(embedded_descriptor_module, module_targets)
        if embedded_owner == None:
            stale[embedded_descriptor_module] = True

    # The packed component's inputs. A module a jar names is resolved like a content module, and a library token is
    # passed through as the label it is. A stale name here skips the whole component below, so a plugin never ships with
    # a jar missing.
    packed_modules = {}
    packed_libraries = []
    for tokens in jars.values():
        for token in tokens:
            if is_library_token(token):
                if token not in packed_libraries:
                    packed_libraries.append(token)
                continue
            label = module_rule_label(token, module_targets)
            if label == None:
                stale[token] = True
            elif label not in packed_modules:
                packed_modules[label] = token
    _warn_stale(main_module, stale)
    descriptors = dict(descriptor_attrs.get("descriptors", {}))
    for name in descriptor_modules:
        label = descriptor_index.get(name)
        if label == None:
            unresolved.append(name)
        elif label not in descriptors:
            descriptors[label] = name + ".xml"
    if descriptors:
        descriptor_attrs["descriptors"] = {label: descriptors[label] for label in sorted(descriptors)}

    if descriptor:
        for variant in variants if variants else [""]:
            dev_dist_plugin_descriptor(
                main_module = main_module,
                descriptor_module = descriptor_module,
                descriptor = descriptor,
                variant = variant,
                unresolved_descriptor_modules = unresolved,
                **descriptor_attrs
            )
    if jars and not stale:
        # A content module no jar merges is reused from its own packing target. Its label follows from the module's
        # label, and a module without such a target fails when Bazel analyses the component.
        content_module_jars = []
        for name in content_modules:
            label = module_rule_label(name, module_targets)
            if label not in packed_modules and label != None:
                content_module_jars.append(_content_module_jar_label(label))
        dev_plugin(
            name = dev_dist_plugin_component_target_name(main_module),
            main_module = main_module,
            descriptor = ":" + dev_dist_plugin_descriptor_target_name(main_module),
            plugin_directory = dev_dist_plugin_directory(main_module, descriptor_attrs.get("directory_name", "")),
            modules = packed_modules,
            libraries = packed_libraries,
            content_module_jars = content_module_jars,
            jars = jars,
            module_jar_paths = module_jar_paths,
            classpath_jars = classpath_jars,
            files = files,
            file_prefixes = file_prefixes,
            executable_files = executable_files,
            visibility = ["//visibility:public"],
        )
    if embedded_descriptor_source or embedded_owner != None:
        dev_dist_embedded_product_descriptor(
            main_module = main_module,
            source_module = embedded_owner,
            source = embedded_descriptor_source if embedded_descriptor_source else embedded_descriptor,
            descriptors = embedded_descriptors,
            library_descriptors = embedded_library_descriptors,
            modules = ([embedded_descriptor_module] if embedded_descriptor_module else []) + [name for name in embedded_modules if name != embedded_descriptor_module],
            separate_jar = embedded_separate_jar,
        )
    if frontend_application_info:
        dev_dist_frontend_application_info(
            main_module = main_module,
            client_application_info = frontend_application_info,
            product_application_info = frontend_product_application_info,
            build_number = frontend_build_number,
        )
