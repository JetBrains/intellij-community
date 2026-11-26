"""Unit tests for `plugin_components`, which expands the compact component tables of the plan generator.

A plugin takes its component from the tables of every product, unless an override table of the product states another
one. A platform stem expands to one label per host platform, in sorted order. The result keeps the order of the
products, of the tiers and of the main modules of a tier.
"""

load("@bazel_skylib//lib:unittest.bzl", "asserts", "unittest")
load(":dist_plugin_components.bzl", "plugin_components")

_STEM_LABELS = {
    "darwin_aarch64": "//p:b_darwin_aarch64_component",
    "darwin_x64": "//p:b_darwin_x64_component",
    "linux_aarch64": "//p:b_linux_aarch64_component",
    "linux_x64": "//p:b_linux_x64_component",
    "windows_aarch64": "//p:b_windows_aarch64_component",
    "windows_x64": "//p:b_windows_x64_component",
}

_WINDOWS_LABELS = {
    "windows_aarch64": "//w:w_windows_aarch64_component",
    "windows_x64": "//w:w_windows_x64_component",
}

def _shared_tables_test_impl(ctx):
    env = unittest.begin(ctx)
    actual = plugin_components(
        labels = {"a": "//p:a_dev_plugin", "w": _WINDOWS_LABELS},
        platform_stems = {"b": "//p:b"},
        products = {
            "z": {"bundled": ["b", "a"], "additional": ["w"]},
            "c": {"additional": ["a"]},
        },
    )
    expected = {
        "z": {
            "bundled": {"b": _STEM_LABELS, "a": "//p:a_dev_plugin"},
            "additional": {"w": _WINDOWS_LABELS},
        },
        "c": {"additional": {"a": "//p:a_dev_plugin"}},
    }
    asserts.equals(env, expected, actual)

    # A Starlark dict equality ignores the order, and the bundled tier is the composition order.
    asserts.equals(env, ["z", "c"], list(actual.keys()))
    asserts.equals(env, ["bundled", "additional"], list(actual["z"].keys()))
    asserts.equals(env, ["b", "a"], list(actual["z"]["bundled"].keys()))
    asserts.equals(env, sorted(_STEM_LABELS.keys()), list(actual["z"]["bundled"]["b"].keys()))
    return unittest.end(env)

shared_tables_test = unittest.make(_shared_tables_test_impl)

def _product_overrides_test_impl(ctx):
    env = unittest.begin(ctx)
    actual = plugin_components(
        labels = {"a": "//p:a_dev_plugin"},
        platform_stems = {"b": "//p:b"},
        products = {
            "x": {"bundled": ["a", "b"], "additional": []},
            "y": {"bundled": ["a", "b"], "additional": []},
        },
        product_labels = {"y": {"b": "//p:b.Y_component"}},
        product_platform_stems = {"y": {"a": "//p:b"}},
    )
    asserts.equals(env, {"a": "//p:a_dev_plugin", "b": _STEM_LABELS}, actual["x"]["bundled"])
    asserts.equals(env, {"a": _STEM_LABELS, "b": "//p:b.Y_component"}, actual["y"]["bundled"])
    asserts.equals(env, {}, actual["y"]["additional"])
    return unittest.end(env)

product_overrides_test = unittest.make(_product_overrides_test_impl)

def dist_plugin_components_test_suite(name):
    unittest.suite(name, shared_tables_test, product_overrides_test)
