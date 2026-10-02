"""Derives the content rows of a product descriptor from the module-set table.

`dev_dist_product_descriptor` and `dev_dist_embedded_product_descriptor` share this. The descriptor writer composes the
`<idea-plugin>` element from the rows that `product_content_rows` returns.
"""

# The loading rule that a module row omits, as the Product DSL renderer omits it.
_DEFAULT_LOADING = "optional"

_INCLUDE_KINDS = ["required", "optional"]

def _loading(rule):
    return None if rule == None or rule == "" or rule == _DEFAULT_LOADING else rule

def _row(name, loading, required_if_available, private = False):
    row = name
    if private:
        row += ";private"
    if loading:
        row += ";loading=" + loading
    if required_if_available:
        row += ";required-if-available=" + required_if_available
    return row

def _module_set(module_set_table, set_name, referrer):
    module_set = module_set_table.get(set_name)
    if module_set == None:
        fail("%s names the module set '%s', which the module-set table does not have" % (referrer, set_name))
    return module_set

def _record_module(module_sources, module_name, source):
    if module_name in module_sources:
        fail("the module '%s' of %s is already in %s" % (module_name, source, module_sources[module_name]))
    module_sources[module_name] = source

def _record_alias(alias_sources, alias, source):
    if alias in alias_sources:
        fail("the alias '%s' of %s is already declared by %s" % (alias, source, alias_sources[alias]))
    alias_sources[alias] = source

def product_content_rows(
        module_sets,
        module_set_table,
        loading_overrides = {},
        content_modules = [],
        private_content_modules = [],
        content_module_loading = {},
        content_module_required_if_available = {},
        aliases = []):
    """Walks the module-set table of a product and returns the rows of its content.

    The walk is the one of `buildContentBlocksAndChainMapping` in `ContentBlockBuilder.kt`. It visits the sets of
    `module_sets` in pre-order. It processes a set once. The own `modules` of a set come first, then its `nested` sets in
    their order. A set with no own member yields no row. The alias of every reached set joins the aliases.

    An alias that two sources declare fails, and so does a module that two rows name. The Product DSL refuses both, and so
    does the descriptor writer.

    An override applies only to the own members of a top-level set. A nested set gets no override.

    The Kotlin twin has a quirk, and this walk keeps it. A set can be reached by nesting first and at top level later,
    with overrides. Then the overrides update its earlier rows in place. They do so only when none of the earlier rows
    carries a non-default loading.

    A row is `name[;loading=<rule>][;required-if-available=<module>]`. An additional row adds `;private` right after the
    name. The default loading, `optional`, gets no `loading` part, as in the renderer.

    Args:
        module_sets: the top-level set names, `intellij.moduleSets.<name>`, in DSL order.
        module_set_table: `DEV_DIST_MODULE_SETS` of the half. A set has `modules` and `nested`, and optionally
            `loading`, `required_if_available` and `alias`.
        loading_overrides: the loading rule of a member, which a top-level set states for its own members.
        content_modules: the additional modules, in DSL order.
        private_content_modules: the additional modules without a namespace.
        content_module_loading: the loading rule of an additional module.
        content_module_required_if_available: the `required-if-available` module of an additional module.
        aliases: the product aliases.

    Returns:
        `struct(aliases, content_rows, additional_rows, members, additional)`. `aliases` is sorted and includes the set
        aliases. `members` and `additional` are the module names of the rows, in row order.
    """
    rows = []
    rows_of_set = {}
    alias_sources = {}
    module_sources = {}
    for alias in aliases:
        _record_alias(alias_sources, alias, "the product")

    top_level_members = {}
    for set_name in module_sets:
        for module_name in _module_set(module_set_table, set_name, "module_sets").modules:
            top_level_members[module_name] = True
    for module_name in loading_overrides:
        if module_name not in top_level_members:
            fail("loading_overrides names '%s', which is not an own member of a set in module_sets" % module_name)

    # A set is expanded once, and a visit pushes at most its nested sets, so this bounds the visits of one top-level set.
    visit_limit = 1
    for module_set in module_set_table.values():
        visit_limit += len(module_set.nested)
    for top_level in module_sets:
        stack = [(top_level, True, "module_sets")]
        for _ in range(visit_limit):
            if not stack:
                break
            set_name, is_top_level, referrer = stack.pop()
            module_set = _module_set(module_set_table, set_name, referrer)
            overrides = {}
            if is_top_level:
                overrides = {name: loading_overrides[name] for name in module_set.modules if name in loading_overrides}
            if set_name in rows_of_set:
                existing = rows_of_set[set_name]
                if overrides and existing and not [row for row in existing if row["loading"]]:
                    for row in existing:
                        if row["name"] in overrides:
                            row["loading"] = _loading(overrides[row["name"]])
                continue
            own_rows = []
            rows_of_set[set_name] = own_rows
            alias = getattr(module_set, "alias", None)
            if alias:
                _record_alias(alias_sources, alias, "the module set '%s'" % set_name)
            set_loading = getattr(module_set, "loading", {})
            set_required_if_available = getattr(module_set, "required_if_available", {})
            for module_name in module_set.modules:
                _record_module(module_sources, module_name, "the module set '%s'" % set_name)
                rule = overrides[module_name] if module_name in overrides else set_loading.get(module_name)
                own_rows.append({
                    "name": module_name,
                    "loading": _loading(rule),
                    "required_if_available": set_required_if_available.get(module_name),
                })
            rows.extend(own_rows)
            stack.extend([(nested, False, "the module set '%s'" % set_name) for nested in reversed(module_set.nested)])
        if stack:
            fail("the walk of the module set '%s' did not end in %d visits" % (top_level, visit_limit))

    for module_name in content_modules:
        _record_module(module_sources, module_name, "content_modules")
    additional = {name: True for name in content_modules}
    for attr_name, names in [
        ("private_content_modules", private_content_modules),
        ("content_module_loading", content_module_loading),
        ("content_module_required_if_available", content_module_required_if_available),
    ]:
        for name in names:
            if name not in additional:
                fail("%s names '%s', which content_modules does not have" % (attr_name, name))
    private = {name: True for name in private_content_modules}
    return struct(
        aliases = sorted(alias_sources),
        content_rows = [_row(row["name"], row["loading"], row["required_if_available"]) for row in rows],
        additional_rows = [
            _row(
                name,
                _loading(content_module_loading.get(name)),
                content_module_required_if_available.get(name),
                private = name in private,
            )
            for name in content_modules
        ],
        members = [row["name"] for row in rows],
        additional = list(content_modules),
    )

def derived_descriptor_rows(module_names, descriptor_index, descriptors = {}, refused_content_modules = []):
    """Adds the conventional `descriptors` row of each module that the index knows to the explicit rows.

    The guards are the ones of `dev_dist_plugin_descriptor`. A name the index does not know is skipped. A refused name
    gets no row. An explicit row wins by label and by load path, because the descriptor writer refuses a load path that
    two rows declare.

    Args:
        module_names: the module names whose descriptors the content can reach.
        descriptor_index: the conventional descriptor label of each module, from the JPS bridge of this half.
        descriptors: the explicit rows, keyed by label and valued by load path.
        refused_content_modules: the modules that the content module filter of the product refuses.

    Returns:
        The rows keyed by label and valued by load path, sorted by label.
    """
    rows = dict(descriptors)
    stated_load_paths = {path: True for path in descriptors.values()}
    refused = {name: True for name in refused_content_modules}
    for module_name in module_names:
        if module_name in refused:
            continue
        label = descriptor_index.get(module_name)
        if label == None or label in rows or (module_name + ".xml") in stated_load_paths:
            continue
        rows[label] = module_name + ".xml"
    return {label: rows[label] for label in sorted(rows)}

def product_content_attributes(
        caller,
        aliases,
        includes,
        module_sets,
        module_set_table,
        loading_overrides,
        content_modules,
        private_content_modules,
        content_module_loading,
        content_module_required_if_available,
        descriptor_index,
        descriptors,
        refused_content_modules = []):
    """Returns the content attributes of a product descriptor rule: the rows of `product_content_rows` and the derived
    `descriptors` rows.

    A target that states no alias, no include, no set and no additional module fails, because the descriptor writer
    refuses a request without a composition flag.

    Args:
        caller: the macro name for a failure message.
        aliases: see `product_content_rows`.
        includes: the deprecated includes, href to `required` or `optional`, in order.
        module_sets: see `product_content_rows`.
        module_set_table: see `product_content_rows`.
        loading_overrides: see `product_content_rows`.
        content_modules: see `product_content_rows`.
        private_content_modules: see `product_content_rows`.
        content_module_loading: see `product_content_rows`.
        content_module_required_if_available: see `product_content_rows`.
        descriptor_index: see `derived_descriptor_rows`.
        descriptors: the explicit `descriptors` rows.
        refused_content_modules: see `derived_descriptor_rows`.

    Returns:
        A dict of rule attributes.
    """
    if not (aliases or includes or module_sets or content_modules):
        fail("%s states no content: no alias, no include, no module set and no additional module" % caller)
    for href, kind in includes.items():
        if kind not in _INCLUDE_KINDS:
            fail("%s: the include '%s' has the kind '%s'. The kind must be one of %s" % (caller, href, kind, _INCLUDE_KINDS))
    content = product_content_rows(
        module_sets = module_sets,
        module_set_table = module_set_table,
        loading_overrides = loading_overrides,
        content_modules = content_modules,
        private_content_modules = private_content_modules,
        content_module_loading = content_module_loading,
        content_module_required_if_available = content_module_required_if_available,
        aliases = aliases,
    )
    attributes = {
        "aliases": content.aliases,
        "includes": ["%s=%s" % (kind, href) for href, kind in includes.items()],
        "content_rows": content.content_rows,
        "additional_rows": content.additional_rows,
    }
    rows = derived_descriptor_rows(
        content.members + content.additional,
        descriptor_index,
        descriptors,
        refused_content_modules,
    )
    if rows:
        attributes["descriptors"] = rows
    return attributes
