"""Tests for the `packed_deps_entry_renaming` attribute of `ij_plugin_module`."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("@rules_java//java:defs.bzl", "java_library")
load("@rules_jvm//:jvm.bzl", "jvm_import")
load("//platform/build-scripts/bazel-rules:ij_plugin_module.bzl", "PluginModuleInfo", "ij_plugin_module")

_LIBRARY = "@lib//:libraries-assertj-core-assert_j"

def _failure_test_impl(ctx):
    env = analysistest.begin(ctx)
    asserts.expect_failure(env, ctx.attr.expected_failure)
    return analysistest.end(env)

_failure_test = analysistest.make(
    _failure_test_impl,
    expect_failure = True,
    attrs = {"expected_failure": attr.string(mandatory = True)},
)

def _provider_test_impl(ctx):
    env = analysistest.begin(ctx)
    info = analysistest.target_under_test(env)[PluginModuleInfo]
    asserts.equals(env, 1, len(info.packed_jar_entry_renamings))
    renaming = info.packed_jar_entry_renamings[0]
    asserts.equals(env, info.all_output_jars[1:], renaming.jars)
    asserts.equals(env, {"META-INF/LICENSE": "META-INF/LICENSE-library"}, renaming.renamed)
    asserts.equals(env, ["module-info.class"], renaming.skipped)
    return analysistest.end(env)

_provider_test = analysistest.make(_provider_test_impl)

# The JARs of the multi-JAR fixture library. Two of them have the same name without the version.
_FIXTURE_JARS = ["first-1.2.jar", "second-1.2.jar", "second-2.0.jar", "third.jar"]

def _jar_prefix_test_impl(ctx):
    env = analysistest.begin(ctx)
    groups = {}
    for renaming in analysistest.target_under_test(env)[PluginModuleInfo].packed_jar_entry_renamings:
        groups[",".join(sorted([jar.basename for jar in renaming.jars]))] = (renaming.renamed, renaming.skipped)
    asserts.equals(
        env,
        {
            ",".join(_FIXTURE_JARS): ({}, ["c.txt"]),
            "first-1.2.jar": ({"a.txt": "a-first.txt", "b.txt": "b-first.txt"}, []),
            "second-2.0.jar": ({"a.txt": "a-second.txt"}, []),
            "third.jar": ({}, ["a.txt"]),
        },
        groups,
    )
    return analysistest.end(env)

_jar_prefix_test = analysistest.make(_jar_prefix_test_impl)

def _multi_jar_fixture(name):
    """Defines a library with the JARs `_FIXTURE_JARS` and returns its label. The JARs are empty files, enough for analysis."""
    native.genrule(
        name = name + "-files",
        outs = [name + "/" + jar for jar in _FIXTURE_JARS],
        cmd = "touch $(OUTS)",
        tags = ["manual"],
    )
    imports = []
    for jar in _FIXTURE_JARS:
        import_name = name + "-" + jar
        jvm_import(
            name = import_name,
            jar = ":" + name + "/" + jar,
            tags = ["manual"],
        )
        imports.append(":" + import_name)
    java_library(
        name = name,
        exports = imports,
        tags = ["manual"],
    )
    return ":" + name

def packed_deps_entry_renaming_tests(name):
    """Checks the provider of a valid renaming and rejects bad renamings."""
    module_name = name + "-valid-module"
    ij_plugin_module(
        name = module_name,
        module_name = "intellij.test",
        packed_deps = [_LIBRARY],
        packed_deps_entry_renaming = {
            _LIBRARY: {
                "META-INF/LICENSE": "META-INF/LICENSE-library",
                "module-info.class": "$skip",
            },
        },
        tags = ["manual"],
    )
    _provider_test(
        name = name + "-valid-test",
        target_under_test = ":" + module_name + "_plugin_module",
    )
    tests = [":" + name + "-valid-test"]

    multi_jar_library = _multi_jar_fixture(name + "-multi-jar-library")
    jar_prefix_module_name = name + "-jar-prefix-module"
    ij_plugin_module(
        name = jar_prefix_module_name,
        module_name = "intellij.test",
        packed_deps = [multi_jar_library],
        packed_deps_entry_renaming = {
            multi_jar_library: {
                "c.txt": "$skip",
                "first-1.2.jar!/a.txt": "a-first.txt",
                # the name without the version selects the same JAR
                "first.jar!/b.txt": "b-first.txt",
                "second-2.0.jar!/a.txt": "a-second.txt",
                "third.jar!/a.txt": "$skip",
            },
        },
        tags = ["manual"],
    )
    _jar_prefix_test(
        name = name + "-jar-prefix-test",
        target_under_test = ":" + jar_prefix_module_name + "_plugin_module",
    )
    tests.append(":" + name + "-jar-prefix-test")

    unsupported_path = "specifies unsupported entry path"
    cases = [
        ("not-packed", [_LIBRARY], multi_jar_library, {"a.txt": "b.txt"}, "which is not in packed_deps"),
        ("colon-in-source", [_LIBRARY], _LIBRARY, {"a:b.txt": "b.txt"}, unsupported_path),
        ("comma-in-target", [_LIBRARY], _LIBRARY, {"a.txt": "b,c.txt"}, unsupported_path),
        ("empty-target", [_LIBRARY], _LIBRARY, {"a.txt": ""}, unsupported_path),
        ("absolute-source", [_LIBRARY], _LIBRARY, {"/a.txt": "b.txt"}, unsupported_path),
        ("directory-target", [_LIBRARY], _LIBRARY, {"a.txt": "b/"}, unsupported_path),
        ("same-path", [_LIBRARY], _LIBRARY, {"a.txt": "a.txt"}, "renames 'a.txt' to the same path"),
        ("unknown-jar", [multi_jar_library], multi_jar_library, {"fourth.jar!/a.txt": "b.txt"}, "matches 0 JARs of the label"),
        ("ambiguous-jar", [multi_jar_library], multi_jar_library, {"second.jar!/a.txt": "b.txt"}, "matches 2 JARs of the label"),
        ("empty-entry-after-jar", [multi_jar_library], multi_jar_library, {"third.jar!/": "b.txt"}, unsupported_path),
    ]
    for case_name, packed_deps, label, renaming, expected_failure in cases:
        prefix = name + "-" + case_name
        ij_plugin_module(
            name = prefix + "-module",
            module_name = "intellij.test",
            packed_deps = packed_deps,
            packed_deps_entry_renaming = {label: renaming},
            tags = ["manual"],
        )
        _failure_test(
            name = prefix + "-test",
            expected_failure = expected_failure,
            target_under_test = ":" + prefix + "-module_plugin_module",
        )
        tests.append(":" + prefix + "-test")
    native.test_suite(
        name = name,
        tests = tests,
    )
