"""Expands the compact component tables that the plan generator writes into `DIST_PLUGIN_COMPONENTS`.

Data only: this file loads only the host platforms, so a unit test can read it without analysis.
"""

load(":host_platforms.bzl", "HOST_PLATFORMS")

# The tiers that a product of the compact tables can state, in the order of `DIST_PLUGIN_COMPONENTS`.
_TIERS = ["bundled", "additional"]

def _platform_labels(stem):
    return {platform: "%s_%s_component" % (stem, platform) for platform in sorted(HOST_PLATFORMS)}

def _check_overrides(table_name, overrides, products):
    for product, components in overrides.items():
        tiers = products.get(product)
        if tiers == None:
            fail("%s names the product '%s', which products does not have" % (table_name, product))
        main_modules = {main_module: True for main_modules in tiers.values() for main_module in main_modules}
        for main_module in components:
            if main_module not in main_modules:
                fail("%s names the plugin '%s' of '%s', which no tier of that product has" % (table_name, main_module, product))

def plugin_components(labels, platform_stems, products, product_labels = {}, product_platform_stems = {}):
    """Returns `DIST_PLUGIN_COMPONENTS`: the component of every plugin by product key, tier and main module.

    Most plugins have one component in every product, so the tables state it once per plugin. A product states only
    the main modules of each tier, in tier order. The bundled tier is in composition order. An override table states
    the component of a plugin in one product when it differs.

    The value of a plugin in the result is one label, or a dict from host platform to label. A platform stem
    `<package>:<stem>` stands for the label `<package>:<stem>_<platform>_component` of each `HOST_PLATFORMS` entry, in
    sorted order.

    Args:
        labels: the main module to the label of the component of every product, or to a dict from host platform to
            label.
        platform_stems: the main module to the platform stem of the component of every product.
        products: the product key to its tiers, `bundled` and `additional`, in this order. A tier is a list of main
            modules. A product states only the tiers that the result has for it.
        product_labels: the product key to the main module to a label or a dict, for a plugin whose component in that
            product differs.
        product_platform_stems: the product key to the main module to a platform stem, for a plugin whose component
            in that product differs.

    Returns:
        The product key to the tier to the main module to the value, in the order of `products`.
    """
    _check_overrides("product_labels", product_labels, products)
    _check_overrides("product_platform_stems", product_platform_stems, products)
    result = {}
    for product, tiers in products.items():
        own_labels = product_labels.get(product, {})
        own_platform_stems = product_platform_stems.get(product, {})
        expanded = {}
        for tier, main_modules in tiers.items():
            if tier not in _TIERS:
                fail("The product '%s' states the tier '%s', which is not one of %s" % (product, tier, _TIERS))
            components = {}
            for main_module in main_modules:
                if main_module in components:
                    fail("The tier '%s' of '%s' names the plugin '%s' twice" % (tier, product, main_module))
                if main_module in own_labels:
                    components[main_module] = own_labels[main_module]
                elif main_module in own_platform_stems:
                    components[main_module] = _platform_labels(own_platform_stems[main_module])
                elif main_module in labels:
                    components[main_module] = labels[main_module]
                elif main_module in platform_stems:
                    components[main_module] = _platform_labels(platform_stems[main_module])
                else:
                    fail("The tier '%s' of '%s' names the plugin '%s', which no table gives a component" % (tier, product, main_module))
            expanded[tier] = components
        result[product] = expanded
    return result
