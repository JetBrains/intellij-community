"""Checks `platform_jar_order` against goldens: the two lists around the sorted range, and the refused inputs."""

load("@bazel_skylib//lib:unittest.bzl", "asserts", "unittest")
load(":dev_dist_platform_jar_order.bzl", "platform_jar_order")

def _jar(destination, *modules):
    return struct(destination = destination, member_modules = tuple(modules))

# Sorted by destination, as the payload states its layout.
_ENTRIES = [
    _jar("app.jar", "intellij.platform.ide"),
    _jar("atk.jar"),
    _jar("ext/platform-main.jar", "intellij.platform.main"),
    _jar("fleet.kernel.jar", "fleet.kernel"),
    _jar("nio-fs.jar", "intellij.platform.core.nio.fs"),
    _jar("util-8.jar", "intellij.platform.util.jdkEx"),
    _jar("xml.jar", "intellij.xml"),
]

def _order_test_impl(ctx):
    env = unittest.begin(ctx)
    result = platform_jar_order(_ENTRIES, first = ["nio-fs.jar", "util-8.jar"], last = ["ext/platform-main.jar", "atk.jar"])
    asserts.equals(env, None, result.error)
    asserts.equals(
        env,
        ["nio-fs.jar", "util-8.jar", "fleet.kernel.jar", "app.jar", "xml.jar", "ext/platform-main.jar", "atk.jar"],
        result.destinations,
    )
    return unittest.end(env)

_order_test = unittest.make(_order_test_impl)

def _smallest_member_test_impl(ctx):
    """A jar with several modules sorts by its smallest member, whatever the merge order of its members."""
    env = unittest.begin(ctx)
    entries = [
        _jar("b.jar", "test.b"),
        _jar("residual.jar", "test.z", "test.a"),
    ]
    result = platform_jar_order(entries, first = [], last = [])
    asserts.equals(env, None, result.error)
    asserts.equals(env, ["residual.jar", "b.jar"], result.destinations)
    return unittest.end(env)

_smallest_member_test = unittest.make(_smallest_member_test_impl)

def _refusal_test_impl(ctx):
    env = unittest.begin(ctx)
    for first, last, expected in [
        (["missing.jar"], ["atk.jar"], "the platform jar order names missing.jar, but the payload does not pack it"),
        (["nio-fs.jar"], ["nio-fs.jar", "atk.jar"], "the platform jar order names nio-fs.jar twice"),
        ([], [], "atk.jar has no module and so no sort key, but the platform jar order does not name it in last"),
    ]:
        result = platform_jar_order(_ENTRIES, first = first, last = last)
        asserts.equals(env, expected, result.error, "first %s, last %s" % (first, last))
        asserts.equals(env, [], result.destinations)
    return unittest.end(env)

_refusal_test = unittest.make(_refusal_test_impl)

def dev_dist_platform_jar_order_test_suite(name):
    unittest.suite(
        name,
        _order_test,
        _smallest_member_test,
        _refusal_test,
    )
