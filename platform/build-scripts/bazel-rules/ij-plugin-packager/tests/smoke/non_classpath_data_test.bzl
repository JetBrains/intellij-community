"""Tests for attributes in non-classpath data mappings."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("@rules_pkg//pkg:mappings.bzl", "pkg_attributes", "pkg_filegroup", "pkg_files")
load("//platform/build-scripts/bazel-rules:ij_plugin_module.bzl", "PluginModuleInfo", "ij_plugin_module")

def _attribute_test_impl(ctx):
    env = analysistest.begin(ctx)
    if ctx.attr.expected_failure:
        asserts.expect_failure(env, ctx.attr.expected_failure)
    else:
        asserts.true(env, PluginModuleInfo in analysistest.target_under_test(env))
    return analysistest.end(env)

_attribute_test = analysistest.make(
    _attribute_test_impl,
    expect_failure = True,
    attrs = {"expected_failure": attr.string()},
)

def _mode_test_impl(ctx):
    env = analysistest.begin(ctx)
    data = analysistest.target_under_test(env)[PluginModuleInfo].non_classpath_data
    asserts.equals(env, [ctx.attr.expected_mode], [entry.mode for entry in data])
    return analysistest.end(env)

_mode_test = analysistest.make(
    _mode_test_impl,
    attrs = {"expected_mode": attr.string(mandatory = True)},
)

def non_classpath_data_attribute_tests(name):
    """Checks the modes and rejects other attributes and bad modes in direct mappings and nested groups."""
    tests = []
    unsupported_mode = "specifies unsupported mode"
    cases = [
        ("default", {}, "", "0644"),
        ("explicit-default", {"mode": "0644"}, "", "0644"),
        ("mode", {"mode": "0755"}, "", "0755"),
        ("short-mode", {"mode": "600"}, "", "0600"),
        ("setuid-mode", {"mode": "4755"}, unsupported_mode, ""),
        ("non-octal-mode", {"mode": "0999"}, unsupported_mode, ""),
        ("symbolic-mode", {"mode": "rwx"}, unsupported_mode, ""),
        ("user", {"user": "root"}, "specifies unsupported attribute 'user'", ""),
        ("group", {"group": "root"}, "specifies unsupported attribute 'group'", ""),
        ("uid", {"uid": 0}, "specifies unsupported attribute 'uid'", ""),
        ("gid", {"gid": 0}, "specifies unsupported attribute 'gid'", ""),
        ("custom", {"custom": "value"}, "specifies unsupported attribute 'custom'", ""),
    ]
    for case_name, attributes, expected_failure, expected_mode in cases:
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
                expected_failure = expected_failure,
                target_under_test = ":" + prefix + "-module_plugin_module",
            )
            tests.append(":" + test_name)
            if expected_mode:
                mode_test_name = prefix + "-mode-test"
                _mode_test(
                    name = mode_test_name,
                    expected_mode = expected_mode,
                    target_under_test = ":" + prefix + "-module_plugin_module",
                )
                tests.append(":" + mode_test_name)
    native.test_suite(
        name = name,
        tests = tests,
    )
