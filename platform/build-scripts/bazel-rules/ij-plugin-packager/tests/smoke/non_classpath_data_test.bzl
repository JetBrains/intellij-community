"""Tests for attributes in non-classpath data mappings."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("@rules_pkg//pkg:mappings.bzl", "pkg_attributes", "pkg_filegroup", "pkg_files")
load("//platform/build-scripts/bazel-rules:ij_plugin_module.bzl", "PluginModuleInfo", "ij_plugin_module")

def _attribute_test_impl(ctx):
    env = analysistest.begin(ctx)
    if ctx.attr.expected_attribute:
        asserts.expect_failure(env, "specifies unsupported attribute '%s'" % ctx.attr.expected_attribute)
    else:
        asserts.true(env, PluginModuleInfo in analysistest.target_under_test(env))
    return analysistest.end(env)

_attribute_test = analysistest.make(
    _attribute_test_impl,
    expect_failure = True,
    attrs = {"expected_attribute": attr.string()},
)

def non_classpath_data_attribute_tests(name):
    """Checks default attributes and rejects other attributes in direct mappings and nested groups."""
    tests = []
    cases = [
        ("default", {}, ""),
        ("explicit-default", {"mode": "0644"}, ""),
        ("mode", {"mode": "0755"}, "mode"),
        ("user", {"user": "root"}, "user"),
        ("group", {"group": "root"}, "group"),
        ("uid", {"uid": 0}, "uid"),
        ("gid", {"gid": 0}, "gid"),
        ("custom", {"custom": "value"}, "custom"),
    ]
    for case_name, attributes, expected_attribute in cases:
        for grouped in [False, True]:
            prefix = name + "-" + case_name + ("-group" if grouped else "-files")
            pkg_files(
                name = prefix + "-data",
                srcs = ["descriptor/data/first.txt"],
                attributes = pkg_attributes(**attributes),
                tags = ["manual"],
            )
            data_target = prefix + "-data"
            if grouped:
                for suffix in ["-inner-group", "-outer-group"]:
                    group_name = prefix + suffix
                    pkg_filegroup(
                        name = group_name,
                        srcs = [":" + data_target],
                        tags = ["manual"],
                    )
                    data_target = group_name
            ij_plugin_module(
                name = prefix + "-module",
                module_name = "intellij.test",
                non_classpath_data = [":" + data_target],
                tags = ["manual"],
            )
            test_name = prefix + "-test"
            _attribute_test(
                name = test_name,
                expected_attribute = expected_attribute,
                target_under_test = ":" + prefix + "-module_plugin_module",
            )
            tests.append(":" + test_name)
    native.test_suite(
        name = name,
        tests = tests,
    )
