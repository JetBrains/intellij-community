"""The packing labels of the `lib/` jars of one split product, from the generated tables.

Data only: this file loads nothing, so a unit test can read it without analysis.
"""

# The payload key of the platform `lib/` jars in the generated fragment inputs.
_PLATFORM_LIB_FRAGMENT = "platform_lib"

def dev_dist_packed_labels(fragment_inputs, module_sets, product, product_mode):
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
    run writes both tables, so the bootstrap tolerance of the bridge does not apply here.
    """
    inputs = fragment_inputs.get(product)
    if inputs == None:
        fail("No generated dev-distribution fragment inputs for product '%s'" % product)
    payload = inputs.get(_PLATFORM_LIB_FRAGMENT)
    if payload == None:
        fail("The generated dev-distribution fragment inputs of '%s' have no '%s' payload" % (product, _PLATFORM_LIB_FRAGMENT))

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
        fail("Module set nesting did not settle for the '%s' payload of product '%s'" % (_PLATFORM_LIB_FRAGMENT, product))
    if stale:
        fail("The dev-distribution plan of '%s' references unknown module sets: %s. Regenerate the dev-distribution tables." % (product, ", ".join(sorted(stale))))
    return struct(packed = sorted(packed.keys()), module_system_loaded = sorted(module_system_loaded.keys()))
