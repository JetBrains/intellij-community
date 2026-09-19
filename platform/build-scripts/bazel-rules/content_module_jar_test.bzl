"""Checks the one packing action of a content module jar: its flag file, its outputs and its provider."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("@rules_java//java:defs.bzl", "JavaInfo", "java_common")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
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

    # The flag file in grammar order. The fixture merges one meaningful source, so the manifest is kept. Without natives
    # the jar rejects a native entry. With them the same action writes the tree beside the jar, and the three lines
    # name it. The rejection is absent then, because the packer refuses the pair.
    expected = ["output=" + info.jar.path, "metadata-file=" + info.metadata.path, "keep-manifest=true", "merge-entities=true"]
    outputs = [info.jar, info.metadata]
    if ctx.attr.native_lib:
        asserts.true(env, info.native_tree.is_directory)
        asserts.equals(env, "native", info.native_tree.basename)
        asserts.true(env, info.native_tree.path.endswith("/" + target.label.name + "/native"), info.native_tree.path)
        asserts.equals(env, ctx.attr.native_lib_dir, info.native_lib_dir)
        expected += [
            "native-tree=" + info.native_tree.path,
            "native-variant=" + ctx.attr.native_platform,
            "native-lib=" + ctx.attr.native_lib,
        ]
        outputs.append(info.native_tree)
    else:
        asserts.equals(env, None, info.native_tree)
        asserts.equals(env, "", info.native_lib_dir)
        expected.append("reject-native-entries=true")
    expected += ["module=" + jar.path for jar in info.member_jars]
    asserts.equals(env, expected, action.argv[1:])
    asserts.equals(env, outputs, action.outputs.to_list())
    asserts.equals(env, [info.jar] + ([info.native_tree] if ctx.attr.native_lib else []), target[DefaultInfo].files.to_list())
    return analysistest.end(env)

_platform_jar_test = analysistest.make(
    _platform_jar_test_impl,
    attrs = {
        "destination": attr.string(mandatory = True),
        "member_modules": attr.string_list(),
        "native_lib": attr.string(),
        "native_lib_dir": attr.string(),
        "native_platform": attr.string(),
    },
    config_settings = {_TRACE_SPANS: False},
)

def _platform_jar_failure_test_impl(ctx):
    env = analysistest.begin(ctx)
    asserts.expect_failure(env, ctx.attr.expected_message)
    return analysistest.end(env)

_platform_jar_failure_test = analysistest.make(
    _platform_jar_failure_test_impl,
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
    # survive both the rule and the provider rather than collapsing to the jar's own name. The `natives` case is a jar
    # whose presigned library carries native files: the same action writes the tree, and the provider carries it.
    for case, destination, native_lib, native_lib_dir, native_platform in [
        ("flat", "platform-flat.jar", "", "", ""),
        ("nested", "ext/platform-nested.jar", "", "", ""),
        ("natives", "platform-natives.jar", "jna", "jna", "linux_x64"),
    ]:
        platform_jar = name + "_platform_" + case
        dev_dist_platform_jar(
            name = platform_jar,
            relative_output_file = destination,
            modules = [":" + first],
            native_lib = native_lib,
            native_lib_dir = native_lib_dir,
            native_platform = native_platform,
            tags = ["manual"],
        )
        _platform_jar_test(
            name = platform_jar + "_test",
            target_under_test = ":" + platform_jar,
            destination = destination,
            member_modules = ["test." + first],
            native_lib = native_lib,
            native_lib_dir = native_lib_dir,
            native_platform = native_platform,
        )
        tests.append(platform_jar + "_test")

    # The natives mode is all three attributes or none, the platform is a `HOST_PLATFORMS` token and the directory is
    # one name under `lib/`. Each is refused at analysis, where the jar is still named, rather than in a distribution.
    for case, native_lib, native_lib_dir, native_platform, expected_message in [
        ("partial", "jna", "", "", "native_lib_dir, native_platform"),
        ("bad_platform", "jna", "jna", "linux_riscv", "'linux_riscv' is not one of"),
        ("bad_dir", "jna", "jna/x64", "linux_x64", "is not one directory name"),
    ]:
        platform_jar = name + "_platform_natives_" + case
        dev_dist_platform_jar(
            name = platform_jar,
            relative_output_file = "platform-natives.jar",
            modules = [":" + first],
            native_lib = native_lib,
            native_lib_dir = native_lib_dir,
            native_platform = native_platform,
            tags = ["manual"],
        )
        _platform_jar_failure_test(
            name = platform_jar + "_test",
            target_under_test = ":" + platform_jar,
            expected_message = expected_message,
        )
        tests.append(platform_jar + "_test")
    native.test_suite(name = name, tests = tests)
