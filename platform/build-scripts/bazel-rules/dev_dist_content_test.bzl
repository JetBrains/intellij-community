"""Analysis tests for the dev-distribution content rules: the prepacked plugin-content provider boundary, and what a
packing action declares."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("@rules_java//java:defs.bzl", "JavaInfo", "java_common")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load(":content_module_jar.bzl", "ContentModuleJarInfo", "content_module_jar", "content_module_jar_target_name")
load(":dev_dist_content.bzl", "DevDistContentInfo", "dev_dist_content_set", "dev_dist_plugin_content")
load(":intellij_dev_dist.bzl", "intellij_dev_build_inputs")

# An empty zip: the 22-byte end-of-central-directory record and nothing else. Octal escapes, because Bazel's Starlark
# rejects `\x`; every byte is below 0x80, so `ctx.actions.write` - which encodes as UTF-8 - reproduces it exactly.
#
# A fake module's jar has to be a *real* jar, because one of these fakes is the owner of a real `content_module_jar`
# target and `./build/dev-dist.cmd jars` builds every one of those in the repository. A few bytes of text there failed
# that gate with `11 bytes is too small to be a zip`, and the alternative - teaching the gate to skip this target -
# would have put an opt-out into the only check that holds the two producers of these bytes to each other.
_EMPTY_JAR = "PK\005\006" + ("\000" * 18)

def _fake_module_impl(ctx):
    """A module: one own output jar, and the `KtJvmInfo.module_name` that tells a module from a library container.

    Nothing about packing lives here. A module's `lib/` jar is a `content_module_jar` target of its own now - see
    `_fake_packed` - so a module fake is only what the `modules`/`content_modules`/`descriptor_module` attributes ask
    for. The jar's *content* is the one exception: see [_EMPTY_JAR].
    """
    module_jar = ctx.actions.declare_file(ctx.label.name + ".jar")
    ctx.actions.write(module_jar, _EMPTY_JAR)
    return [
        DefaultInfo(files = depset([module_jar])),
        _KtJvmInfo(
            all_output_jars = [module_jar],
            module_name = ctx.attr.module_name,
        ),
    ]

_fake_module = rule(
    implementation = _fake_module_impl,
    attrs = {
        "module_name": attr.string(mandatory = True),
    },
)

def _fake_packed_impl(ctx):
    """A `content_module_jar` target: the packed jar, the module it is named after, and the recipe inside it.

    The jar is named after the *target*, where the real rule names it after the module. That is the one deviation, and
    it is what makes the two-producers-one-relation case constructible: two real packing targets for one module name
    cannot share a package, since they would declare the same file, so the conflict this suite defends against is
    reachable only across packages. The record's placement is derived from `module_name` regardless of the file's own
    name, which is exactly the derivation `_completion_provider_test` pins.

    The recipe fields are filled because the real provider fills them - `dev_dist_platform_payload` reads all of them -
    and the member is the owner module's *own* jar, never the packed jar it was merged into.
    """
    packed_jar = ctx.actions.declare_file(ctx.label.name + ".jar")
    ctx.actions.write(packed_jar, ctx.attr.module_name)
    member_jar = ctx.actions.declare_file(ctx.label.name + "-member.jar")
    ctx.actions.write(member_jar, ctx.attr.module_name)
    return [
        DefaultInfo(files = depset([packed_jar])),
        # Tuples, not lists: these travel in a depset, whose elements must be immutable.
        ContentModuleJarInfo(
            jar = packed_jar,
            module_name = ctx.attr.module_name,
            member_jars = (member_jar,),
            member_modules = (ctx.attr.module_name,),
            library_jars = (),
        ),
    ]

_fake_packed = rule(
    implementation = _fake_packed_impl,
    attrs = {
        "module_name": attr.string(mandatory = True),
    },
)

def _fake_library_impl(ctx):
    """A library container: `JavaInfo` with [runtime_jar_count] runtime jars, and no `KtJvmInfo` module name.

    `neverlink` reproduces the `-provided` wrapper, whose `transitive_runtime_jars` is empty - the shape
    `_collect_libraries` must refuse rather than paper over, because for a local library behind such a wrapper the
    compile-time sets hold an *interface* jar.
    """
    jars = []
    for index in range(ctx.attr.runtime_jar_count):
        jar = ctx.actions.declare_file("%s-%d.jar" % (ctx.label.name, index))
        ctx.actions.write(jar, "%s:%d" % (ctx.label.name, index))
        jars.append(jar)

    # `compile_jar` is required per output, so each jar is announced as its own `JavaInfo` and they are merged - which is
    # also what the real multi-jar container is: a srcs-less `java_library` re-exporting one `jvm_import` per jar.
    infos = [JavaInfo(output_jar = jar, compile_jar = jar, neverlink = ctx.attr.neverlink) for jar in jars]
    return [
        DefaultInfo(files = depset(jars)),
        java_common.merge(infos) if infos else JavaInfo(output_jar = None, compile_jar = None),
    ]

_fake_library = rule(
    implementation = _fake_library_impl,
    attrs = {
        "neverlink": attr.bool(default = False),
        "runtime_jar_count": attr.int(default = 1),
    },
)

def _library_jars_test_impl(ctx):
    env = analysistest.begin(ctx)
    all_entries = analysistest.target_under_test(env)[DevDistContentInfo].library_jars.to_list()

    # The key is the container's own label, not a jar's owner. Selected by that key rather than by position: the rule
    # declares `@lib//:kotlin-stdlib` for every plugin, so the declared container is not the only entry.
    entries = [entry for entry in all_entries if entry.label.endswith(ctx.attr.expected_label_suffix)]
    asserts.equals(env, 1, len(entries), "keys: %s" % [entry.label for entry in all_entries])

    # Order is the container's, not sorted: the packer resolves a duplicated entry to its first source.
    asserts.equals(env, ctx.attr.expected_jars, [jar.basename for jar in entries[0].jars])
    return analysistest.end(env)

_library_jars_test = analysistest.make(
    _library_jars_test_impl,
    attrs = {
        "expected_jars": attr.string_list(mandatory = True),
        "expected_label_suffix": attr.string(mandatory = True),
    },
)

def _expected_failure_test_impl(ctx):
    """Asserts the target under test fails analysis, with [expected_message] in the failure.

    Every target tested through here must be tagged `manual`. `expect_failure` tolerates the failure only under this
    rule's own `analysis_test_transition`, which is where `--allow_analysis_failures` is set; the same target reached
    as a top-level target of a wildcard build is in the default configuration, and there its `fail()` aborts the
    whole build.
    """
    env = analysistest.begin(ctx)
    asserts.expect_failure(env, ctx.attr.expected_message)
    return analysistest.end(env)

_expected_failure_test = analysistest.make(
    _expected_failure_test_impl,
    expect_failure = True,
    attrs = {
        "expected_message": attr.string(mandatory = True),
    },
)

def _composed_provider_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    records = target[DevDistContentInfo].prepacked_plugin_jars.to_list()
    asserts.equals(env, 1, len(records))
    record = records[0]
    asserts.equals(env, "test.plugin", record.plugin_main_module)
    asserts.equals(env, "test.content", record.content_module)
    asserts.equals(env, "modules/test.content.jar", record.relative_output_file)
    return analysistest.end(env)

_composed_provider_test = analysistest.make(_composed_provider_test_impl)

def _completion_provider_test_impl(ctx):
    """A set that completes a cross-repository plugin produces the same record a plugin-content target would.

    The whole point of the completion is that `descriptor_module` is unnameable from the package that has to declare
    these members, so the plugin is named as a string instead - and nothing downstream may be able to tell.
    """
    env = analysistest.begin(ctx)
    records = analysistest.target_under_test(env)[DevDistContentInfo].prepacked_plugin_jars.to_list()
    asserts.equals(env, 1, len(records))
    asserts.equals(env, "test.plugin", records[0].plugin_main_module)
    asserts.equals(env, "test.content", records[0].content_module)
    asserts.equals(env, "modules/test.content.jar", records[0].relative_output_file)
    return analysistest.end(env)

_completion_provider_test = analysistest.make(_completion_provider_test_impl)

# The setting the two packing-output tests differ by, in the canonical form a transition needs. Written once: the same
# label is the rule's own `_trace_spans` default, and a test that named a different one would pass while asserting
# nothing.
_TRACE_SPANS = str(Label("//platform/build-scripts/bazel-rules:trace_spans"))

_PACKING_OUTPUTS_ATTRS = {
    "expected_outputs": attr.string_list(mandatory = True),
    "expected_span_files": attr.string_list(mandatory = True),
}

def _packing_outputs_test_impl(ctx):
    """What a packing action declares: the jar alone, or the jar and the span file beside it.

    This is the invariant the whole `trace_spans` gating rests on. Off has to mean *absent* - not an empty file, not an
    output nobody asks for - because these actions are the dev build itself, and a second declared output re-keys every
    one of the ~2 500 of them, so a measuring build would be measuring a different build. That was proved once by hand,
    with an `aquery` diff over 1 512 actions; this is what holds it.
    """
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    packing = [action for action in actions if action.mnemonic == "PackContentModuleJar"]
    asserts.equals(env, 1, len(packing), "mnemonics: %s" % [action.mnemonic for action in actions])
    asserts.equals(
        env,
        ctx.attr.expected_outputs,
        sorted([file.basename for file in packing[0].outputs.to_list()]),
    )

    # The output group is how a single jar's spans are asked for explicitly, and it has to exist in both states:
    # `--output_groups=+trace_spans` is part of the documented measuring command line, and requesting a group that a
    # target does not have is an error rather than an empty set.
    group = analysistest.target_under_test(env)[OutputGroupInfo].trace_spans.to_list()
    asserts.equals(env, ctx.attr.expected_span_files, [file.basename for file in group])
    return analysistest.end(env)

# Both tests pin the flag, neither reads it. Without `config_settings` this one would assert whatever `trace_spans`
# happened to be on the command line, so `bazel test ... --@community//platform/build-scripts/bazel-rules:trace_spans`
# would fail it - a test of the ambient configuration rather than of the rule.
_packing_outputs_test = analysistest.make(
    _packing_outputs_test_impl,
    attrs = _PACKING_OUTPUTS_ATTRS,
    config_settings = {_TRACE_SPANS: False},
)

_measuring_packing_outputs_test = analysistest.make(
    _packing_outputs_test_impl,
    attrs = _PACKING_OUTPUTS_ATTRS,
    config_settings = {_TRACE_SPANS: True},
)

def dev_dist_content_test_suite(name):
    _fake_module(
        name = name + "_descriptor",
        module_name = "test.plugin",
    )

    # Two packing targets for one module name, which is what a relation claimed by two producers is made of. A module
    # that packs nothing has no such target at all, so `prepacked_content_modules` cannot name one: the attribute's
    # `providers = [ContentModuleJarInfo]` gate refuses it before any rule code runs, and that is now the whole check -
    # there is no fake for it, because there is nothing left for a fake to reach.
    _fake_packed(
        name = name + "_content",
        module_name = "test.content",
    )
    _fake_packed(
        name = name + "_content_same_name",
        module_name = "test.content",
    )

    _fake_library(
        name = name + "_multi_jar_library",
        runtime_jar_count = 3,
    )
    _fake_library(
        name = name + "_provided_library",
        neverlink = True,
        runtime_jar_count = 1,
    )

    # A container expands to every runtime jar it holds, under one key, in the container's own order.
    dev_dist_plugin_content(
        name = name + "_multi_jar_library_content",
        descriptor_module = name + "_descriptor",
        libraries = [name + "_multi_jar_library"],
    )
    _library_jars_test(
        name = name + "_multi_jar_library_test",
        expected_jars = [
            # do not sort
            name + "_multi_jar_library-0.jar",
            name + "_multi_jar_library-1.jar",
            name + "_multi_jar_library-2.jar",
        ],
        expected_label_suffix = ":" + name + "_multi_jar_library",
        target_under_test = name + "_multi_jar_library_content",
    )

    # A `neverlink` container holds no runtime jar, so declaring it would declare nothing. Refused rather than resolved
    # through a compile-time set, which for a local library would be an interface jar.
    dev_dist_plugin_content(
        name = name + "_provided_library_content",
        descriptor_module = name + "_descriptor",
        libraries = [name + "_provided_library"],
        tags = ["manual"],
    )
    _expected_failure_test(
        name = name + "_provided_library_test",
        expected_message = "contributes no runtime jars",
        target_under_test = name + "_provided_library_content",
    )

    dev_dist_plugin_content(
        name = name + "_plugin_content",
        descriptor_module = name + "_descriptor",
        prepacked_content_modules = [name + "_content"],
    )
    dev_dist_plugin_content(
        name = name + "_same_plugin_content",
        descriptor_module = name + "_descriptor",
        prepacked_content_modules = [name + "_content"],
    )
    dev_dist_content_set(
        name = name + "_composed_content",
        deps = [
            name + "_plugin_content",
            name + "_same_plugin_content",
        ],
    )
    _composed_provider_test(
        name = name + "_composed_provider_test",
        target_under_test = name + "_composed_content",
    )

    dev_dist_plugin_content(
        name = name + "_conflicting_plugin_content",
        descriptor_module = name + "_descriptor",
        prepacked_content_modules = [name + "_content_same_name"],
    )
    dev_dist_content_set(
        name = name + "_conflicting_content",
        deps = [
            name + "_plugin_content",
            name + "_conflicting_plugin_content",
        ],
    )
    intellij_dev_build_inputs(
        name = name + "_conflicting_inputs",
        content = name + "_conflicting_content",
        tags = ["manual"],
    )
    _expected_failure_test(
        name = name + "_conflicting_relation_test",
        expected_message = "is provided by conflicting records",
        target_under_test = name + "_conflicting_inputs",
    )

    dev_dist_content_set(
        name = name + "_completion_content",
        prepacked_content_modules = [name + "_content"],
        prepacked_plugin_main_module = "test.plugin",
    )
    _completion_provider_test(
        name = name + "_completion_provider_test",
        target_under_test = name + "_completion_content",
    )

    # A relation with no plugin has no key, so the two attributes are required together rather than defaulted.
    dev_dist_content_set(
        name = name + "_unnamed_completion_content",
        prepacked_content_modules = [name + "_content"],
        tags = ["manual"],
    )
    _expected_failure_test(
        name = name + "_unnamed_completion_test",
        expected_message = "must be set together",
        target_under_test = name + "_unnamed_completion_content",
    )

    # A packing target of its own, so the action under test is the real one rather than a fake of it. It needs nothing
    # but an owner module: a jar with no library and no other member is still a `PackContentModuleJar` action, and what
    # is asserted is the shape of its output set, not its recipe. Both tests run against this one target - the only
    # difference between them is the value of `trace_spans` their transition sets.
    _fake_module(
        name = name + "_packed_owner",
        module_name = "test.packed",
    )

    # A real packing target, and `./build/dev-dist.cmd jars` builds it along with the other ~2 500. It packs, because
    # `_fake_module`'s jar is a real (empty) jar - see [_EMPTY_JAR] - and lands in that gate's "packed, not in this
    # distribution" bucket, where a target no distribution composes belongs.
    content_module_jar(module = ":" + name + "_packed_owner")
    _packing_outputs_test(
        name = name + "_packing_outputs_test",
        expected_outputs = ["test.packed.jar"],
        expected_span_files = [],
        target_under_test = content_module_jar_target_name(name + "_packed_owner"),
    )
    _measuring_packing_outputs_test(
        name = name + "_measuring_packing_outputs_test",
        expected_outputs = [
            "test.packed.jar",
            "test.packed.spans.json",
        ],
        expected_span_files = ["test.packed.spans.json"],
        target_under_test = content_module_jar_target_name(name + "_packed_owner"),
    )

    native.test_suite(
        name = name,
        tests = [
            name + "_packing_outputs_test",
            name + "_measuring_packing_outputs_test",
            name + "_multi_jar_library_test",
            name + "_provided_library_test",
            name + "_composed_provider_test",
            name + "_conflicting_relation_test",
            name + "_completion_provider_test",
            name + "_unnamed_completion_test",
        ],
    )
