"""Analysis tests for direct dev distribution packaging."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts", "unittest")
load("@rules_java//java:defs.bzl", "JavaInfo", "java_common")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load(":content_module_jar.bzl", "ContentModuleJarInfo", "DevDistPlatformJarInfo")
load(":content_module_jar_test.bzl", "content_module_jar_test_suite")
load(":dev_dist_content.bzl", "DevDistContentInfo", "DevDistPlatformPayloadInfo", "dev_dist_platform_payload")
load(":dev_dist_plugin.bzl", "dev_dist_plugin")
load(":dev_dist_plugin_descriptor.bzl", "DevDistPluginDescriptorSetInfo", "dev_dist_plugin_descriptor_target_name")
load(
    ":intellij_dev_dist.bzl",
    "IntellijDevBuildInputsInfo",
    "IntellijDevFragmentInfo",
    "IntellijProjectModelTreeInfo",
    "intellij_dev_build_inputs",
    "intellij_dev_fragment",
    "intellij_dev_fragments_dist",
    "intellij_dev_packed_jars_component",
)

_EMPTY_JAR = "PK\005\006" + ("\000" * 18)
_TRACE_SPANS = str(Label("//platform/build-scripts/bazel-rules:trace_spans"))

def _fake_module_impl(ctx):
    jar = ctx.actions.declare_file(ctx.label.name + ".jar")
    ctx.actions.write(jar, _EMPTY_JAR)
    return [
        DefaultInfo(files = depset([jar])),
        _KtJvmInfo(all_output_jars = [jar], module_name = ctx.attr.module_name),
    ]

_fake_module = rule(
    implementation = _fake_module_impl,
    attrs = {"module_name": attr.string(mandatory = True)},
)

def _fake_library_impl(ctx):
    jars = []
    infos = []
    for index in range(ctx.attr.jar_count):
        jar = ctx.actions.declare_file("%s-%d.jar" % (ctx.label.name, index))
        ctx.actions.write(jar, ctx.label.name)
        jars.append(jar)
        infos.append(JavaInfo(output_jar = jar, compile_jar = jar))
    return [
        DefaultInfo(files = depset(jars)),
        java_common.merge(infos),
    ]

_fake_library = rule(
    implementation = _fake_library_impl,
    attrs = {"jar_count": attr.int(default = 1)},
)

def _fake_packed_impl(ctx):
    jar = ctx.actions.declare_file(ctx.label.name + ".jar")
    metadata = ctx.actions.declare_file(ctx.label.name + ".metadata.json")
    ctx.actions.write(jar, _EMPTY_JAR)
    ctx.actions.write(metadata, "{}")
    member = ctx.attr.member[_KtJvmInfo]
    libraries = []
    if ctx.attr.library:
        libraries.append(struct(
            label = str(ctx.attr.library.label),
            jars = tuple(ctx.attr.library[JavaInfo].transitive_runtime_jars.to_list()),
        ))
    return [
        DefaultInfo(files = depset([jar])),
        ContentModuleJarInfo(
            jar = jar,
            metadata = metadata,
            module_name = member.module_name,
            relative_path = jar.basename,
            member_jars = tuple(member.all_output_jars),
            member_modules = (member.module_name,),
            library_jars = tuple(libraries),
        ),
    ]

_fake_packed = rule(
    implementation = _fake_packed_impl,
    attrs = {
        "library": attr.label(providers = [JavaInfo]),
        "member": attr.label(mandatory = True, providers = [_KtJvmInfo]),
    },
)

# A platform jar that names a subdirectory of `lib/`, which is the one thing a content module jar never does.
def _fake_platform_jar_impl(ctx):
    jar = ctx.actions.declare_file(ctx.label.name + ".jar")
    metadata = ctx.actions.declare_file(ctx.label.name + ".metadata.json")
    ctx.actions.write(jar, _EMPTY_JAR)
    ctx.actions.write(metadata, "{}")
    return [
        DefaultInfo(files = depset([jar])),
        DevDistPlatformJarInfo(
            jar = jar,
            metadata = metadata,
            relative_path = ctx.attr.destination,
            member_jars = (),
            member_modules = (),
            library_jars = (),
        ),
    ]

_fake_platform_jar = rule(
    implementation = _fake_platform_jar_impl,
    attrs = {"destination": attr.string(mandatory = True)},
)

def _fake_descriptor_set_impl(ctx):
    descriptor = ctx.actions.declare_file(ctx.label.name + ".xml")
    ctx.actions.write(descriptor, "<idea-plugin/>")
    return [DevDistPluginDescriptorSetInfo(descriptors = depset([struct(
        plugin_main_module = ctx.attr.main_module,
        descriptor = descriptor,
    )]))]

_fake_descriptor_set = rule(
    implementation = _fake_descriptor_set_impl,
    attrs = {"main_module": attr.string(mandatory = True)},
)

def _expected_failure_test_impl(ctx):
    env = analysistest.begin(ctx)
    asserts.expect_failure(env, ctx.attr.expected_message)
    return analysistest.end(env)

_expected_failure_test = analysistest.make(
    _expected_failure_test_impl,
    expect_failure = True,
    attrs = {"expected_message": attr.string(mandatory = True)},
)

def _platform_payload_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    payload = target[DevDistPlatformPayloadInfo]
    reference = target[DevDistContentInfo]
    packed = ctx.attr.packed[ContentModuleJarInfo]
    nested = ctx.attr.nested[DevDistPlatformJarInfo]

    # The destination, not the file name: this is the set the owning fragment must not pack, and a nested jar whose
    # base name reached it would leave both producers writing the same jar to two places.
    asserts.equals(env, sorted([packed.relative_path, nested.relative_path]), payload.packed_jar_names)
    asserts.equals(env, [packed.jar, nested.jar], payload.packed_jars.to_list())
    asserts.equals(env, sorted(ctx.attr.expected_declared_modules), sorted(payload.declared_modules.to_list()))
    asserts.equals(env, list(packed.member_jars), reference.module_jars.to_list())
    asserts.equals(env, list(packed.library_jars), reference.library_jars.to_list())
    return analysistest.end(env)

_platform_payload_test = analysistest.make(
    _platform_payload_test_impl,
    attrs = {
        "expected_declared_modules": attr.string_list(mandatory = True),
        "packed": attr.label(mandatory = True, providers = [ContentModuleJarInfo]),
        "nested": attr.label(mandatory = True, providers = [DevDistPlatformJarInfo]),
    },
)

def _build_inputs_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    info = target[IntellijDevBuildInputsInfo]
    expected = []
    for module in ctx.attr.modules:
        expected.extend(module[_KtJvmInfo].all_output_jars)
    expected.extend(ctx.attr.library[JavaInfo].transitive_runtime_jars.to_list())
    for file in expected:
        asserts.true(env, file in info.files.to_list(), file.path)
    descriptors = ctx.attr.descriptors[DevDistPluginDescriptorSetInfo].descriptors.to_list()
    asserts.equals(env, 1, len(info.patched_descriptors.to_list()))
    asserts.true(env, descriptors[0].descriptor in info.files.to_list())
    asserts.equals(env, sorted([info.manifest, info.inputs_origin]), sorted(target[DefaultInfo].files.to_list()))
    asserts.equals(env, 2, len(analysistest.target_actions(env)))
    return analysistest.end(env)

_build_inputs_test = analysistest.make(
    _build_inputs_test_impl,
    attrs = {
        "descriptors": attr.label(mandatory = True, providers = [DevDistPluginDescriptorSetInfo]),
        "library": attr.label(mandatory = True, providers = [JavaInfo]),
        "modules": attr.label_list(mandatory = True, providers = [_KtJvmInfo]),
    },
)

def _tool_fixture_impl(ctx):
    executable = ctx.actions.declare_file(ctx.label.name + ".sh")
    tree = ctx.actions.declare_directory(ctx.label.name + ".tree")
    ctx.actions.write(executable, "#!/bin/sh\nexit 0\n", is_executable = True)
    ctx.actions.write(ctx.outputs.data, "fixture")
    ctx.actions.run_shell(outputs = [tree], arguments = [tree.path], command = "mkdir -p \"$1\"")
    return [
        DefaultInfo(files = depset([executable, ctx.outputs.data]), executable = executable),
        IntellijProjectModelTreeInfo(tree = tree),
    ]

_tool_fixture = rule(
    implementation = _tool_fixture_impl,
    executable = True,
    outputs = {"data": "%{name}.data"},
)

def _fragment_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    fragment = target[IntellijDevFragmentInfo]
    inputs = ctx.attr.build_inputs[IntellijDevBuildInputsInfo]
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic.startswith("IntellijDev")]
    asserts.equals(env, 1, len(actions))
    if actions:
        action = actions[0]
        for file in inputs.files.to_list():
            asserts.true(env, file in action.inputs.to_list(), file.path)

    # A fragment builds no plugin, so it declares no plugin output: the packed plugin components own that group.
    asserts.false(env, hasattr(target[OutputGroupInfo], "dev_dist_plugin_outputs"))
    asserts.equals(env, None, fragment.plugin_classpath_part)
    asserts.equals(env, [fragment.home, fragment.manifest], target[DefaultInfo].files.to_list())
    return analysistest.end(env)

_fragment_test = analysistest.make(
    _fragment_test_impl,
    attrs = {
        "build_inputs": attr.label(mandatory = True, providers = [IntellijDevBuildInputsInfo]),
    },
    config_settings = {_TRACE_SPANS: False},
)

def _fake_component_impl(ctx):
    manifest = ctx.actions.declare_file(ctx.label.name + ".component.json")
    payload = ctx.actions.declare_file(ctx.label.name + ".payload")
    plugin_output = ctx.actions.declare_file(ctx.label.name + ".plugin-output")
    ctx.actions.write(manifest, "{}")
    ctx.actions.write(payload, ctx.label.name)
    ctx.actions.write(plugin_output, ctx.label.name)
    return [
        DefaultInfo(files = depset([manifest, plugin_output]), runfiles = ctx.runfiles(files = [payload])),
        OutputGroupInfo(dev_dist_plugin_outputs = depset([plugin_output])),
        IntellijDevFragmentInfo(
            name = ctx.attr.component_name,
            home = None,
            payload = depset([payload]),
            manifest = manifest,
            plugin_classpath_part = None,
            plugin_classpath_prefix = None,
            inputs_manifest = None,
            unused_inputs = None,
        ),
    ]

_fake_component = rule(
    implementation = _fake_component_impl,
    attrs = {"component_name": attr.string(mandatory = True)},
)

def _distribution_groups_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    groups = target[OutputGroupInfo]
    expected_production = [component[OutputGroupInfo].dev_dist_plugin_outputs.to_list()[0] for component in ctx.attr.production]
    asserts.equals(env, sorted(expected_production), sorted(groups.dev_dist_plugin_outputs.to_list()))
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic == "IntellijDevDistCompose"]
    asserts.equals(env, 1, len(actions))
    return analysistest.end(env)

_distribution_groups_test = analysistest.make(
    _distribution_groups_test_impl,
    attrs = {
        "production": attr.label_list(mandatory = True, providers = [IntellijDevFragmentInfo]),
    },
    config_settings = {_TRACE_SPANS: False},
)

def _packed_component_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    component = target[IntellijDevFragmentInfo]
    payload = component.payload.to_list()
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic == "IntellijDevPackedJars"]
    asserts.equals(env, 1, len(actions))
    if actions:
        for jar in payload:
            asserts.false(env, jar in actions[0].inputs.to_list())
    asserts.equals(env, None, component.home)
    asserts.equals(env, sorted([component.manifest] + payload), sorted(target[DefaultInfo].files.to_list()))
    return analysistest.end(env)

_packed_component_test = analysistest.make(
    _packed_component_test_impl,
    config_settings = {_TRACE_SPANS: False},
)

def _manifest_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    info = target[IntellijDevBuildInputsInfo]
    asserts.true(env, info.manifest.basename.endswith(ctx.attr.expected_suffix))
    asserts.equals(env, [info.manifest, info.inputs_origin], target[DefaultInfo].files.to_list())
    asserts.equals(env, 2, len(analysistest.target_actions(env)))
    return analysistest.end(env)

_manifest_test = analysistest.make(
    _manifest_test_impl,
    attrs = {"expected_suffix": attr.string(mandatory = True)},
)

def _declaration_test_impl(ctx):
    env = unittest.begin(ctx)
    asserts.equals(env, json.decode(ctx.attr.expected), json.decode(ctx.attr.actual))
    return unittest.end(env)

_declaration_test = unittest.make(
    _declaration_test_impl,
    attrs = {
        "actual": attr.string(mandatory = True),
        "expected": attr.string(mandatory = True),
    },
)

def _fake_descriptor_impl(ctx):
    descriptor = ctx.actions.declare_file(ctx.label.name + ".xml")
    ctx.actions.write(descriptor, "<idea-plugin/>")
    return [DefaultInfo(files = depset([descriptor]))]

_fake_descriptor = rule(implementation = _fake_descriptor_impl)

def _plugin_macro_tests(name):
    owner = name + "_macro_owner"
    member = name + "_macro_member"
    source = name + "_macro_descriptor"
    _fake_module(name = owner, module_name = "test.plugin")
    _fake_module(name = member, module_name = "test.member")
    _fake_descriptor(name = source)
    dev_dist_plugin(
        main_module = "test.plugin",
        module_targets = {
            "test.member": [":" + member + ".jar"],
            "test.plugin": [":" + owner + ".jar"],
        },
        content_modules = ["test.member"],
        descriptor = source,
    )
    descriptor = native.existing_rule(dev_dist_plugin_descriptor_target_name("test.plugin"))
    test = name + "_plugin_macro_test"
    _declaration_test(
        name = test,
        actual = json.encode([
            descriptor["main_module"],
            descriptor["descriptor"],
        ]),
        expected = json.encode([
            "test.plugin",
            ":" + source,
        ]),
    )

    # A plugin that states `jars` also declares a packed component: the main module and the merged modules go in as
    # `modules`, a content module no jar merges is reused from its own packing target, and the library token passes
    # through unchanged.
    packed_owner = name + "_macro_packed_owner"
    packed_split = name + "_macro_packed_split"
    packed_member = name + "_macro_packed_member"
    packed_source = name + "_macro_packed_descriptor"
    _fake_module(name = packed_owner, module_name = "intellij.test.packed")
    _fake_module(name = packed_split, module_name = "intellij.test.packed.split")
    _fake_module(name = packed_member, module_name = "intellij.test.packed.member")
    _fake_descriptor(name = packed_source)
    dev_dist_plugin(
        main_module = "intellij.test.packed",
        module_targets = {
            "intellij.test.packed": [":" + packed_owner + ".jar"],
            "intellij.test.packed.member": [":" + packed_member + ".jar"],
            "intellij.test.packed.split": [":" + packed_split + ".jar"],
        },
        content_modules = ["intellij.test.packed.split", "intellij.test.packed.member"],
        descriptor = packed_source,
        jars = {
            "lib/test-packed.jar": ["@lib//:fake", "intellij.test.packed", "intellij.test.packed.split"],
        },
        classpath_jars = ["lib/modules/intellij.test.packed.member.jar", "lib/test-packed.jar"],
    )
    component = native.existing_rule("intellij.test.packed_dev_plugin")
    inputs = native.existing_rule("intellij.test.packed_dev_plugin_inputs")
    packed_test = name + "_plugin_macro_packed_test"
    _declaration_test(
        name = packed_test,
        actual = json.encode([
            component["main_module"],
            component["plugin_directory"],
            component["descriptor"],
            component["inputs"],
            component["jars"],
            component["classpath_jars"],
            sorted(component["tags"]),
            sorted(inputs["modules"].items()),
            # The values only: `existing_rule` returns a label key in its canonical form.
            sorted(inputs["libraries"].values()),
            inputs["content_module_jars"],
        ]),
        expected = json.encode([
            "intellij.test.packed",
            "plugins/test-packed",
            ":" + dev_dist_plugin_descriptor_target_name("intellij.test.packed"),
            ":intellij.test.packed_dev_plugin_inputs",
            {"lib/test-packed.jar": ["@lib//:fake", "intellij.test.packed", "intellij.test.packed.split"]},
            ["lib/modules/intellij.test.packed.member.jar", "lib/test-packed.jar"],
            ["manual"],
            sorted([[":" + packed_owner, "intellij.test.packed"], [":" + packed_split, "intellij.test.packed.split"]]),
            ["@lib//:fake"],
            [":" + packed_member + "_content_module_jar"],
        ]),
    )

    # The stale-module case of the macro lives in `dev_plugin_test.bzl`: its warning must not print in a dist analysis,
    # and every dist loads this package for `:trace_spans`.
    return [test, packed_test]

def dev_dist_content_test_suite(name):
    library = name + "_library"
    _fake_library(name = library, jar_count = 2)
    tests = []

    packed_owner = name + "_packed_owner"
    raw_owner = name + "_raw_owner"
    dependency = name + "_dependency"
    packed = name + "_packed"
    _fake_module(name = packed_owner, module_name = "test.packed")
    _fake_module(name = raw_owner, module_name = "test.raw")
    _fake_module(name = dependency, module_name = "test.dependency")
    _fake_packed(name = packed, member = ":" + packed_owner, library = ":" + library)
    nested = name + "_nested"
    _fake_platform_jar(name = nested, destination = "ext/nested.jar")
    payload = name + "_payload"
    dev_dist_platform_payload(
        name = payload,
        modules = [":" + packed_owner, ":" + raw_owner, ":" + dependency],
        packed = [":" + packed, ":" + nested],
        modules_by_name = ["test.packed", "test.raw", "test.dependency"],
        seeds = ["test.raw"],
        module_deps = {"test.raw": "test.dependency"},
    )
    tests.append(name + "_platform_payload_test")
    _platform_payload_test(
        name = tests[-1],
        target_under_test = ":" + payload,
        packed = ":" + packed,
        nested = ":" + nested,
        expected_declared_modules = ["test.raw", "test.dependency"],
    )

    descriptors = name + "_descriptors"
    _fake_descriptor_set(name = descriptors, main_module = "test.plugin")
    inputs = name + "_inputs"
    intellij_dev_build_inputs(
        name = inputs,
        content = ":" + payload,
        patched_descriptors = ":" + descriptors,
    )
    tests.append(inputs + "_test")
    _build_inputs_test(
        name = tests[-1],
        target_under_test = ":" + inputs,
        descriptors = ":" + descriptors,
        library = ":" + library,
        modules = [":" + packed_owner],
    )

    fixture = name + "_tool"
    _tool_fixture(name = fixture, tags = ["manual"])
    fragment = name + "_fragment"
    intellij_dev_fragment(
        name = fragment,
        assembler = ":" + fixture,
        platform_prefix = "idea",
        target_platform = "linux_x64",
        fragment_name = "platform_resources",
        platform_resources = True,
        project_model_tree = ":" + fixture,
        bazel_targets_json = ":" + fixture + ".data",
        build_inputs = ":" + inputs,
        preloaded_manifests = [":" + fixture + ".data"],
        tags = ["manual"],
    )
    tests.append(fragment + "_test")
    _fragment_test(
        name = tests[-1],
        target_under_test = ":" + fragment,
        build_inputs = ":" + inputs,
    )

    production = [name + "_component_first", name + "_component_second"]
    for component in production:
        _fake_component(name = component, component_name = component)
    distribution = name + "_distribution"
    intellij_dev_fragments_dist(
        name = distribution,
        composer = ":" + fixture,
        fragments = [":" + component for component in production],
        expect_fragments = production,
        tags = ["manual"],
    )
    tests.append(distribution + "_test")
    _distribution_groups_test(
        name = tests[-1],
        target_under_test = ":" + distribution,
        production = [":" + component for component in production],
    )

    packed_component = name + "_packed_component"
    intellij_dev_packed_jars_component(
        name = packed_component,
        collector = ":" + fixture,
        component_name = "platform",
        platform_prefix = "idea",
        target_platform = "linux_x64",
        platform_payload = ":" + payload,
        tags = ["manual"],
    )
    tests.append(packed_component + "_test")
    _packed_component_test(name = tests[-1], target_under_test = ":" + packed_component)

    empty_inputs = name + "_empty_inputs"
    intellij_dev_build_inputs(name = empty_inputs)
    tests.append(empty_inputs + "_test")
    _manifest_test(name = tests[-1], target_under_test = ":" + empty_inputs, expected_suffix = ".bazel-inputs")

    tests.extend(_plugin_macro_tests(name))
    content_module_jar_test_suite(name = name + "_production")
    tests.append(name + "_production")
    native.test_suite(name = name, tests = tests)
