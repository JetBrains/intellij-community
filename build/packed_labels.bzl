"""The packing labels of the `lib/` jars of one split product, from the generated tables.

Data only: this file loads only the component names, so a unit test can read it without analysis.
"""

load(":component_names.bzl", "PLATFORM_LIB_COMPONENT")

def packed_labels(component_inputs, module_sets, product, product_mode):
    """Returns the packing labels of the `lib/` jars of `product`, from its `platform_lib` payload and its module sets.

    The result is `struct(packed, module_system_loaded)`. Both lists are sorted.

    `packed` is the handover set of the payload. The payload names the labels that no module set carries. Each module
    set that the payload references carries the labels of its members in `packed`. This function walks the referenced
    sets through `nested` and returns the union. The plan generator splits the set the same way (`sharePackedLabels`).

    `product_mode` is the mode of `product`, for example `frontend`. A set names in `mode_refused` the members that each
    mode refuses. The layout of a product of that mode places no jar of them, so this function skips their labels in
    both lists.

    `module_system_loaded` is the part of `packed` whose jars the module system loads. It is the union of the payload
    list and, for each walked set, the `packed` label of each member that the set names in `module_system_loaded`. The
    plan generator splits it the same way (`shareModuleSystemLoadedLabels`). A name without a `packed` label fails.

    A set name that `module_sets` does not have fails, and the message names the product and the sets. One generator
    run writes both tables, so a stale set name is a generator error. The failure stops the load of
    the `build` package, which the data of both generators reach. The IDE run configuration "Generate Product Layouts"
    loads no Bazel package, so it recovers after the converter.
    """
    inputs = component_inputs.get(product)
    if inputs == None:
        fail("No generated distribution component inputs for product '%s'" % product)
    payload = inputs.get(PLATFORM_LIB_COMPONENT)
    if payload == None:
        fail("The generated distribution component inputs of '%s' have no '%s' payload" % (product, PLATFORM_LIB_COMPONENT))

    packed = {label: True for label in getattr(payload, "packed_content_module_jars", [])}
    module_system_loaded = {label: True for label in getattr(payload, "module_system_loaded", [])}
    stale = []
    visited = {}
    frontier = list(getattr(payload, "module_sets", []))
    for _ in range(len(module_sets) + 1):
        if not frontier:
            break
        next_frontier = []
        for set_name in frontier:
            if set_name in visited:
                continue
            visited[set_name] = True
            module_set = module_sets.get(set_name)
            if module_set == None:
                stale.append(set_name)
                continue
            set_packed = getattr(module_set, "packed", {})
            refused = {module: True for module in getattr(module_set, "mode_refused", {}).get(product_mode, [])}
            for module, label in set_packed.items():
                if module not in refused:
                    packed[label] = True
            for module in getattr(module_set, "module_system_loaded", []):
                if module in refused:
                    continue
                label = set_packed.get(module)
                if label == None:
                    fail("Module set '%s' names '%s' as loaded by the module system, but has no packing label for it" % (set_name, module))
                module_system_loaded[label] = True
            next_frontier.extend(getattr(module_set, "nested", []))
        frontier = next_frontier
    if frontier:
        fail("Module set nesting did not settle for the '%s' payload of product '%s'" % (PLATFORM_LIB_COMPONENT, product))
    if stale:
        fail("The distribution plan of '%s' references unknown module sets: %s. Regenerate the tables with the converter and the Generate Product Layouts run configuration." % (product, ", ".join(sorted(stale))))
    return struct(packed = sorted(packed.keys()), module_system_loaded = sorted(module_system_loaded.keys()))
