"""Declare one plugin through the existing content, jar, and descriptor rules."""

load("//build:jps_target_derivation.bzl", "module_rule_label")
load(":content_module_jar.bzl", "content_module_jar_target_name", "dev_dist_plugin_jar", "dev_dist_plugin_jar_target_name")
load(":dev_dist_content.bzl", "dev_dist_plugin_content")
load(":dev_dist_plugin_descriptor.bzl", "dev_dist_plugin_descriptor")

_SHARED_LEAF_ATTRS = ["tags", "visibility"]
_MAX_REPORTED_STALE_MODULES = 20

def _module_labels(names, module_targets, stale, packed = False):
    result = []
    for name in names:
        label = module_rule_label(name, module_targets)
        if label == None:
            stale[name] = True
            continue
        if packed:
            label = label.rpartition(":")[0] + ":" + content_module_jar_target_name(label)
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
        jars = {},
        content_modules = [],
        libraries = [],
        prepacked_content_modules = [],
        prepacked_jars = {},
        descriptor = "",
        variants = [],
        **descriptor_attrs):
    """Expand one plugin declaration without changing its targets or action inputs.

    Args:
        main_module: The main JPS module. It identifies every leaf.
        module_targets: The production map from the applicable JPS bridge. Entries contain one jar output label.
        descriptor_index: The conventional descriptor index from that bridge.
        descriptor_modules: The exact conventional descriptor modules selected by the generator.
        jars: Jar destinations mapped to module names and optional library labels, in merge order.
        content_modules: JPS module names.
        libraries: Library labels passed to the content leaf.
        prepacked_content_modules: Module names whose content jars are already packed.
        prepacked_jars: Packed module names mapped to destinations.
        descriptor: The main descriptor path inside the main module's package.
        variants: Layout variants. An empty list selects the common variant.
        **descriptor_attrs: Other descriptor attributes. Shared leaf attributes are refused.
    """
    if not main_module or type(module_targets) != "dict":
        fail("dev_dist_plugin requires a main module and a JPS target map")
    shared = [key for key in _SHARED_LEAF_ATTRS if key in descriptor_attrs]
    if shared:
        fail("dev_dist_plugin: %s states shared leaf attributes: %s" % (main_module, shared))
    content = {
        "content_modules": content_modules,
        "libraries": libraries,
        "prepacked_content_modules": prepacked_content_modules,
        "prepacked_jars": prepacked_jars,
        "prepacked_layout_jars": [],
    }
    if not descriptor:
        if variants or descriptor_attrs or descriptor_modules or descriptor_index:
            fail("dev_dist_plugin: %s states descriptor attributes and no descriptor" % main_module)
        if not jars and not any(content.values()):
            fail("dev_dist_plugin: %s states neither content nor a descriptor" % main_module)

    unresolved = []
    for destination, jar in jars.items():
        if type(jar) != "dict" or [key for key in jar if key not in ["modules", "libraries"]]:
            fail("dev_dist_plugin: invalid jar declaration for '%s': %s" % (destination, jar))
        if not jar.get("modules") and not jar.get("libraries"):
            fail("dev_dist_plugin: jar '%s' states no modules or libraries" % destination)
    stale = {}
    owner = module_rule_label(main_module, module_targets)
    if owner == None:
        _warn_stale(main_module, {main_module: True})
        return
    descriptor_module = ":" + owner.rpartition(":")[2]
    content["content_modules"] = _module_labels(content_modules, module_targets, stale)
    content["prepacked_content_modules"] = _module_labels(prepacked_content_modules, module_targets, stale, packed = True)
    resolved_jars = {}
    for name, destination in prepacked_jars.items():
        labels = _module_labels([name], module_targets, stale, packed = True)
        if labels:
            resolved_jars[labels[0]] = destination
    content["prepacked_jars"] = resolved_jars
    layout_jars = []
    for destination in sorted(jars):
        jar = jars[destination]
        modules = _module_labels(jar.get("modules", []), module_targets, stale)
        jar_libraries = jar.get("libraries", [])
        if not modules and not jar_libraries:
            continue
        dev_dist_plugin_jar(
            relative_output_file = destination,
            plugin_main_module = main_module,
            modules = modules,
            libraries = jar_libraries,
        )
        layout_jars.append(":" + dev_dist_plugin_jar_target_name(destination))
    content["prepacked_layout_jars"] = sorted(layout_jars)
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

    if any(content.values()):
        dev_dist_plugin_content(descriptor_module = descriptor_module, **content)
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
