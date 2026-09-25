"""Checks the one packing action of a content module jar: its flag file, its outputs and its provider."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("@rules_java//java:defs.bzl", "JavaInfo", "java_common")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load("//build:dev_launch_dependencies.bzl", "HOST_PLATFORMS")
load(
    ":content_module_jar.bzl",
    "ContentModuleJarInfo",
    "DevDistPlatformJarInfo",
    "content_module_jar",
    "content_module_jar_target_name",
    "dev_dist_platform_jar",
)

_TRACE_SPANS = str(Label("//platform/build-scripts/bazel-rules:trace_spans"))
_EMPTY_JAR = "PK\005\006" + ("\000" * 18)

def _fixture_module_impl(ctx):
    jar = ctx.outputs.jar
    ctx.actions.write(jar, _EMPTY_JAR)
    return [
        DefaultInfo(files = depset([jar])),
        _KtJvmInfo(all_output_jars = [jar], module_name = ctx.attr.module_name),
    ]

_fixture_module = rule(
    implementation = _fixture_module_impl,
    attrs = {"module_name": attr.string(mandatory = True)},
    outputs = {"jar": "%{name}.jar"},
)

def _fixture_library_impl(ctx):
    jars = ctx.files.jars
    return [
        DefaultInfo(files = depset(jars)),
        java_common.merge([JavaInfo(output_jar = jar, compile_jar = jar) for jar in jars]),
    ]

_fixture_library = rule(
    implementation = _fixture_library_impl,
    attrs = {"jars": attr.label_list(allow_files = [".jar"])},
)

def _packing_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    info = target[ContentModuleJarInfo]
    groups = target[OutputGroupInfo]
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic == "PackContentModuleJar"]
    asserts.equals(env, 1, len(actions))
    action = actions[0]

    # One action writes the shipped jar. DefaultInfo, the provider and the metadata group name its outputs. The file is
    # `<target>.production.jar`; the destination is derived from the module name, not from the file.
    asserts.equals(env, [info.jar], target[DefaultInfo].files.to_list())
    asserts.equals(env, [info.metadata], groups.file_metadata.to_list())
    asserts.equals(env, target.label.name + ".production.jar", info.jar.basename)
    asserts.equals(env, target.label.name + ".production.metadata.json", info.metadata.basename)
    asserts.equals(env, info.module_name + ".jar", info.relative_path)
    asserts.equals(env, ctx.attr.member_modules, list(info.member_modules))
    asserts.equals(env, [module[_KtJvmInfo].all_output_jars[0].short_path for module in ctx.attr.modules], [jar.short_path for jar in info.member_jars])
    asserts.equals(env, [str(library.label) for library in ctx.attr.libraries], [entry.label for entry in info.library_jars])
    for library, entry in zip(ctx.attr.libraries, info.library_jars):
        asserts.equals(env, [jar.short_path for jar in library[JavaInfo].transitive_runtime_jars.to_list()], [jar.short_path for jar in entry.jars])
    library_jars_by_path = {jar.short_path: jar for entry in info.library_jars for jar in entry.jars}
    coverage_sources = [jar.short_path for jar in ctx.files.coverage_sources]
    spans = groups.trace_spans.to_list()

    # The flag file in grammar order: the output, its metadata, the optional trace file, the flags of the jar, then
    # the module outputs and then the libraries. A coverage source is followed by its manifest mode.
    expected = ["output=" + info.jar.path, "metadata-file=" + info.metadata.path]
    expected += ["trace-file=" + file.path for file in spans]
    if ctx.attr.keep_manifest:
        expected.append("keep-manifest=true")
    expected.append("merge-entities=true")
    for jar in info.member_jars:
        expected.append("module=" + jar.path)
        if jar.short_path in coverage_sources:
            expected.append("source-manifest=coverage-agent")
    for expected_jar in ctx.files.library_jars:
        jar = library_jars_by_path[expected_jar.short_path]
        expected.append("library=" + jar.path)
        if jar.short_path in coverage_sources:
            expected.append("source-manifest=coverage-agent")
    asserts.equals(env, expected, action.argv[1:])
    asserts.equals(env, [info.jar, info.metadata] + spans, action.outputs.to_list())
    asserts.equals(env, 1 if ctx.attr.spans else 0, len(spans))
    if spans:
        asserts.equals(env, target.label.name + ".production.spans.json", spans[0].basename)
    asserts.equals(env, {}, info.native_trees)
    asserts.equals(env, "", info.native_lib_dir)
    return analysistest.end(env)

_PACKING_ATTRS = {
    "modules": attr.label_list(providers = [_KtJvmInfo]),
    "member_modules": attr.string_list(),
    "libraries": attr.label_list(providers = [JavaInfo]),
    "library_jars": attr.label_list(allow_files = [".jar"]),
    "coverage_sources": attr.label_list(allow_files = [".jar"]),
    "keep_manifest": attr.bool(),
    "spans": attr.bool(),
}

_packing_test = analysistest.make(_packing_test_impl, attrs = _PACKING_ATTRS, config_settings = {_TRACE_SPANS: False})
_packing_spans_test = analysistest.make(_packing_test_impl, attrs = _PACKING_ATTRS, config_settings = {_TRACE_SPANS: True})

def _coverage_no_agent_test_impl(ctx):
    env = analysistest.begin(ctx)
    asserts.expect_failure(env, "intellij-coverage-agent")
    return analysistest.end(env)

_coverage_no_agent_test = analysistest.make(_coverage_no_agent_test_impl, expect_failure = True)

def _natives_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    info = target[ContentModuleJarInfo]
    groups = target[OutputGroupInfo]
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic == "PackContentModuleJar"]

    # One jar action and one tree action per platform. The jar is the one `DefaultInfo` names, and it only reserves
    # the natives, so it does not depend on the platform.
    asserts.equals(env, 1 + len(HOST_PLATFORMS), len(actions))
    asserts.equals(env, [info.jar], target[DefaultInfo].files.to_list())
    asserts.equals(env, ctx.attr.native_lib_dir, info.native_lib_dir)
    asserts.equals(env, sorted(HOST_PLATFORMS), sorted(info.native_trees.keys()))
    library_jars = [jar for entry in info.library_jars for jar in entry.jars]
    jar_action = [action for action in actions if info.jar in action.outputs.to_list()]
    asserts.equals(env, 1, len(jar_action))
    jar_argv = jar_action[0].argv[1:]
    asserts.true(env, "native-lib=" + ctx.attr.native_lib in jar_argv, str(jar_argv))
    asserts.false(env, [line for line in jar_argv if line.startswith("native-tree=") or line.startswith("native-variant=")], str(jar_argv))

    # A tree action packs the libraries alone into a scratch jar and writes the tree of its platform beside it.
    for platform in HOST_PLATFORMS:
        native = info.native_trees[platform]
        asserts.true(env, native.tree.is_directory)
        asserts.equals(env, "native", native.tree.basename)
        asserts.true(env, native.tree.path.endswith("/" + target.label.name + ".native_" + platform + "/native"), native.tree.path)
        asserts.equals(env, [native.tree, native.metadata], getattr(groups, "native_tree_" + platform).to_list())
        tree_action = [action for action in actions if native.tree in action.outputs.to_list()]
        asserts.equals(env, 1, len(tree_action))
        outputs = tree_action[0].outputs.to_list()
        scratch = [file for file in outputs if file.basename == "scratch.jar"]
        asserts.equals(env, 1, len(scratch))
        expected = ["output=" + scratch[0].path, "metadata-file=" + native.metadata.path]
        if len(library_jars) == 1:
            expected.append("keep-manifest=true")
        expected += [
            "native-tree=" + native.tree.path,
            "native-variant=" + platform,
            "native-lib=" + ctx.attr.native_lib,
        ]
        expected += ["library=" + jar.path for jar in library_jars]
        asserts.equals(env, expected, tree_action[0].argv[1:])
        asserts.equals(env, [scratch[0], native.metadata, native.tree], outputs)
    return analysistest.end(env)

_natives_test = analysistest.make(
    _natives_test_impl,
    attrs = {
        "native_lib": attr.string(mandatory = True),
        "native_lib_dir": attr.string(mandatory = True),
    },
    config_settings = {_TRACE_SPANS: False},
)

def _selected_output_test_impl(ctx):
    env = analysistest.begin(ctx)
    info = ctx.attr.owner[ContentModuleJarInfo]
    expected = info.metadata if ctx.attr.metadata else info.jar
    asserts.equals(env, [expected], analysistest.target_under_test(env)[DefaultInfo].files.to_list())
    return analysistest.end(env)

_selected_output_test = analysistest.make(
    _selected_output_test_impl,
    attrs = {
        "owner": attr.label(providers = [ContentModuleJarInfo]),
        "metadata": attr.bool(),
    },
)

def _platform_jar_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    info = target[DevDistPlatformJarInfo]
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic == "PackContentModuleJar"]
    asserts.equals(env, 1, len(actions))
    action = actions[0]
    asserts.equals(env, ctx.attr.destination, info.relative_path)
    asserts.true(env, info.jar.path.endswith("/" + target.label.name + "/" + ctx.attr.destination), info.jar.path)
    asserts.equals(env, ctx.attr.member_modules, list(info.member_modules))

    # The flag file in grammar order. The fixture merges one meaningful source, so the manifest is kept. A residual jar
    # rejects a native entry, because a presigned library packs as a `content_module_jar`.
    expected = ["output=" + info.jar.path, "metadata-file=" + info.metadata.path, "keep-manifest=true", "merge-entities=true", "reject-native-entries=true"]
    expected += ["module=" + jar.path for jar in info.member_jars]
    asserts.equals(env, expected, action.argv[1:])
    asserts.equals(env, [info.jar, info.metadata], action.outputs.to_list())
    asserts.equals(env, [info.jar], target[DefaultInfo].files.to_list())
    return analysistest.end(env)

_platform_jar_test = analysistest.make(
    _platform_jar_test_impl,
    attrs = {
        "destination": attr.string(mandatory = True),
        "member_modules": attr.string_list(),
    },
    config_settings = {_TRACE_SPANS: False},
)

def _natives_failure_test_impl(ctx):
    env = analysistest.begin(ctx)
    asserts.expect_failure(env, ctx.attr.expected_message)
    return analysistest.end(env)

_natives_failure_test = analysistest.make(
    _natives_failure_test_impl,
    expect_failure = True,
    attrs = {"expected_message": attr.string(mandatory = True)},
)

def content_module_jar_test_suite(name):
    """Covers source order, first-wins deduplication, meaningful sources, the coverage policy and output selection."""
    tests = []
    first = name + "_first"
    second = "intellij-coverage-agent-" + name
    for source in [first, second]:
        _fixture_module(name = source, module_name = "test." + source)
    _fixture_library(name = name + "_single_library", jars = [":" + first])
    _fixture_library(name = name + "_library", jars = [":" + second, ":" + first])
    _fixture_library(name = name + "_overlapping_library", jars = [":" + first, ":" + second])

    # `coverage` says the module name selects the coverage policy, so the agent-named jar gets its manifest mode. The
    # `unrelated_name` case merges the same jar under a name that selects nothing, and expects no mode.
    for case, module_name, before, after, libraries, library_jars, keep_manifest, coverage in [
        ("single", "test.production.single", [], [], [], [], True, False),
        ("wrapper", "intellij.libraries.production.single", [], [], [name + "_single_library"], [first], True, False),
        ("wrapper_multi", "intellij.libraries.production.multi", [], [], [name + "_library"], [second, first], False, False),
        ("ordered", "test.production.ordered", [first], [second], [name + "_library", name + "_overlapping_library"], [second, first], False, False),
        ("coverage", "intellij.platform.coverage.agent", [], [], [name + "_overlapping_library"], [first, second], False, True),
        ("coverage_name", "test.intellij.platform.coverage.agent.extra", [], [], [name + "_overlapping_library"], [first, second], False, True),
        ("unrelated_name", "test.production.unrelated", [], [], [name + "_overlapping_library"], [first, second], False, False),
    ]:
        owner = name + "_" + case
        _fixture_module(name = owner, module_name = module_name)
        content_module_jar(
            module = ":" + owner,
            modules_before = [":" + member for member in before],
            modules_after = [":" + member for member in after],
            libraries = [":" + library for library in libraries],
        )
        target = content_module_jar_target_name(owner)
        for suffix, test_rule, spans in [("_test", _packing_test, False), ("_spans_test", _packing_spans_test, True)]:
            test_name = owner + suffix
            test_rule(
                name = test_name,
                target_under_test = target,
                modules = [":" + member for member in before + [owner] + after],
                member_modules = ["test." + member for member in before] + [module_name] + ["test." + member for member in after],
                libraries = [":" + library for library in libraries],
                library_jars = [":" + jar for jar in library_jars],
                coverage_sources = [":" + second] if coverage else [],
                keep_manifest = keep_manifest,
                spans = spans,
            )
            tests.append(test_name)

    # A module whose name selects the coverage policy must merge a jar named after the agent. Otherwise the rule fails
    # at analysis instead of shipping a manifest it did not rewrite.
    missing = name + "_coverage_no_agent"
    _fixture_module(name = missing, module_name = "test.intellij.platform.coverage.agent.missing")
    content_module_jar(module = ":" + missing, libraries = [":" + name + "_single_library"])
    _coverage_no_agent_test(name = missing + "_test", target_under_test = content_module_jar_target_name(missing))
    tests.append(missing + "_test")

    # The predeclared outputs stay label-addressable: the plan files and the plugin chain name the jar by its label.
    selected_owner = content_module_jar_target_name(name + "_single")
    for suffix, output_suffix, metadata in [
        ("jar_label", ".production.jar", False),
        ("metadata_label", ".production.metadata.json", True),
    ]:
        selected = name + "_" + suffix
        native.filegroup(name = selected, srcs = [":" + selected_owner + output_suffix], tags = ["manual"])
        _selected_output_test(name = selected + "_test", target_under_test = selected, owner = ":" + selected_owner, metadata = metadata)
        tests.append(selected + "_test")

    # A platform jar states its own destination, and it may name a subdirectory of the plugin's `lib/`. The three
    # residual jars of `idea` do - `ext/platform-main.jar` and the two `frontend-split/` jars - so the destination must
    # survive both the rule and the provider rather than collapsing to the jar's own name.
    for case, destination in [("flat", "platform-flat.jar"), ("nested", "ext/platform-nested.jar")]:
        platform_jar = name + "_platform_" + case
        dev_dist_platform_jar(
            name = platform_jar,
            relative_output_file = destination,
            modules = [":" + first],
            tags = ["manual"],
        )
        _platform_jar_test(
            name = platform_jar + "_test",
            target_under_test = ":" + platform_jar,
            destination = destination,
            member_modules = ["test." + first],
        )
        tests.append(platform_jar + "_test")

    # A content module jar with a presigned library reserves its natives, and one action per platform writes the tree.
    natives_owner = name + "_natives"
    _fixture_module(name = natives_owner, module_name = "intellij.libraries.natives")
    content_module_jar(module = ":" + natives_owner, libraries = [":" + name + "_single_library"], native_lib = "jna", native_lib_dir = "jna")
    _natives_test(
        name = natives_owner + "_test",
        target_under_test = content_module_jar_target_name(natives_owner),
        native_lib = "jna",
        native_lib_dir = "jna",
    )
    tests.append(natives_owner + "_test")

    # The natives mode is both attributes or none, and the directory is one name under `lib/`. Each is refused at
    # analysis, where the jar is still named, rather than in a distribution.
    for case, native_lib, native_lib_dir, expected_message in [
        ("partial", "jna", "", "needs both native_lib and native_lib_dir"),
        ("bad_dir", "jna", "jna/x64", "is not one directory name"),
    ]:
        owner = name + "_natives_" + case
        _fixture_module(name = owner, module_name = "intellij.libraries.natives." + case)
        content_module_jar(module = ":" + owner, libraries = [":" + name + "_single_library"], native_lib = native_lib, native_lib_dir = native_lib_dir)
        _natives_failure_test(
            name = owner + "_test",
            target_under_test = content_module_jar_target_name(owner),
            expected_message = expected_message,
        )
        tests.append(owner + "_test")
    native.test_suite(name = name, tests = tests)
