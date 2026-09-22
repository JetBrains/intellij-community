"""Analysis tests for direct dev distribution packaging."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts", "unittest")
load("@rules_java//java:defs.bzl", "JavaInfo", "java_common")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load(":content_module_jar.bzl", "ContentModuleJarInfo", "DevDistPlatformJarInfo")
load(":content_module_jar_test.bzl", "content_module_jar_test_suite")
load(":dev_dist_content.bzl", "DevDistContentInfo", "DevDistPlatformPayloadInfo", "dev_dist_platform_payload", "dev_dist_plugin_content")
load(":dev_dist_plugin.bzl", "dev_dist_plugin")
load(":dev_dist_plugin_descriptor.bzl", "DevDistPluginDescriptorSetInfo", "dev_dist_plugin_descriptor_target_name", "dev_dist_product_info")
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
_ZIPPER = attr.label(default = "@bazel_tools//tools/zip:zipper", executable = True, cfg = "exec")

# Materializes an empty tree artifact. A shell action needs bash, which a Windows build agent does not have, so the
# tree is an empty archive that the zipper extracts.
def _empty_tree(ctx, tree):
    archive = ctx.actions.declare_file(ctx.label.name + ".empty.zip")
    ctx.actions.write(archive, _EMPTY_JAR)
    ctx.actions.run(
        executable = ctx.executable._zipper,
        arguments = ["x", archive.path, "-d", tree.path],
        inputs = [archive],
        outputs = [tree],
        mnemonic = "DevDistContentTestEmptyTree",
    )

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

# A platform jar that names a subdirectory of `lib/`, or that writes a native tree beside itself. A content module jar
# does neither.
def _fake_platform_jar_impl(ctx):
    jar = ctx.actions.declare_file(ctx.label.name + ".jar")
    metadata = ctx.actions.declare_file(ctx.label.name + ".metadata.json")
    ctx.actions.write(jar, _EMPTY_JAR)
    ctx.actions.write(metadata, "{}")
    native_tree = None
    if ctx.attr.native_lib_dir:
        native_tree = ctx.actions.declare_directory(ctx.label.name + "/native")
        _empty_tree(ctx, native_tree)
    return [
        DefaultInfo(files = depset([jar] + ([native_tree] if native_tree else []))),
        DevDistPlatformJarInfo(
            jar = jar,
            metadata = metadata,
            relative_path = ctx.attr.destination,
            member_jars = (),
            member_modules = (),
            library_jars = (),
            native_tree = native_tree,
            native_lib_dir = ctx.attr.native_lib_dir,
        ),
    ]

_fake_platform_jar = rule(
    implementation = _fake_platform_jar_impl,
    attrs = {
        "destination": attr.string(mandatory = True),
        "native_lib_dir": attr.string(),
        "_zipper": _ZIPPER,
    },
)

# A plugin component as `dev_dist_plugin_content` sees it: the raw module jars of its members and one library container.
def _fake_content_impl(ctx):
    jars = []
    for module in ctx.attr.modules:
        jars.extend(module[_KtJvmInfo].all_output_jars)
    libraries = []
    if ctx.attr.library:
        libraries.append(struct(
            label = str(ctx.attr.library.label),
            jars = tuple(ctx.attr.library[JavaInfo].transitive_runtime_jars.to_list()),
        ))
    return [
        DefaultInfo(files = depset()),
        DevDistContentInfo(module_jars = depset(jars), library_jars = depset(libraries)),
    ]

_fake_content = rule(
    implementation = _fake_content_impl,
    attrs = {
        "modules": attr.label_list(providers = [_KtJvmInfo]),
        "library": attr.label(providers = [JavaInfo]),
    },
)

def _plugin_content_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    content = target[DevDistContentInfo]

    # By short path, as sets: `plugins` are configured for the product and the test's own attributes are not, so one
    # jar arrives as two `File` objects, and a jar two plugins share is one entry or two by configuration alone.
    expected_jars = {}
    expected_libraries = {}
    for plugin in ctx.attr.plugins:
        plugin_content = plugin[DevDistContentInfo]
        for jar in plugin_content.module_jars.to_list():
            expected_jars[jar.short_path] = True
        for entry in plugin_content.library_jars.to_list():
            expected_libraries[entry.label] = True
    asserts.equals(env, sorted(expected_jars.keys()), sorted({jar.short_path: True for jar in content.module_jars.to_list()}.keys()))
    asserts.equals(env, sorted(expected_libraries.keys()), sorted({entry.label: True for entry in content.library_jars.to_list()}.keys()))
    asserts.equals(env, [], target[DefaultInfo].files.to_list())
    asserts.equals(env, [], analysistest.target_actions(env))
    return analysistest.end(env)

_plugin_content_test = analysistest.make(
    _plugin_content_test_impl,
    attrs = {
        "plugins": attr.label_list(mandatory = True, providers = [DevDistContentInfo], doc = "Every content target the union is expected to cover."),
    },
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
    natives = ctx.attr.natives[DevDistPlatformJarInfo]

    # The destination, not the file name: this is the set the owning fragment must not pack, and a nested jar whose
    # base name reached it would leave both producers writing the same jar to two places.
    asserts.equals(env, sorted([packed.relative_path, nested.relative_path, natives.relative_path]), payload.packed_jar_names)

    # Jars only, because the byte gate reads this set. The native tree travels in the jar's record. A jar without one
    # says so with `None` and an empty directory. A content module jar is one, and its provider has no such field.
    asserts.equals(env, [packed.jar, nested.jar, natives.jar], payload.packed_jars.to_list())
    records = {record.jar: record for record in payload.packed_metadata.to_list()}
    asserts.equals(
        env,
        struct(jar = packed.jar, metadata = packed.metadata, relative_path = packed.relative_path, native_tree = None, native_lib_dir = ""),
        records[packed.jar],
    )
    asserts.equals(
        env,
        struct(jar = nested.jar, metadata = nested.metadata, relative_path = nested.relative_path, native_tree = None, native_lib_dir = ""),
        records[nested.jar],
    )
    asserts.equals(
        env,
        struct(jar = natives.jar, metadata = natives.metadata, relative_path = natives.relative_path, native_tree = natives.native_tree, native_lib_dir = natives.native_lib_dir),
        records[natives.jar],
    )
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
        "natives": attr.label(mandatory = True, providers = [DevDistPlatformJarInfo]),
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
    _empty_tree(ctx, tree)
    return [
        DefaultInfo(files = depset([executable, ctx.outputs.data]), executable = executable),
        IntellijProjectModelTreeInfo(tree = tree),
    ]

_tool_fixture = rule(
    implementation = _tool_fixture_impl,
    attrs = {"_zipper": _ZIPPER},
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
    natives = ctx.attr.natives[DevDistPlatformJarInfo]
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic == "IntellijDevPackedJars"]
    asserts.equals(env, 1, len(actions))
    if actions:
        for file in payload:
            asserts.false(env, file in actions[0].inputs.to_list())
    asserts.equals(env, None, component.home)
    asserts.equals(env, sorted([component.manifest] + payload), sorted(target[DefaultInfo].files.to_list()))

    # The tree is in the payload beside its jar, so the composer places it. Both files the collector reads name it as a
    # `tree`: the destinations under `lib/<native_lib_dir>/`, the catalogue under the tree's own name. A jar entry has
    # no `tree` key. Both files are sorted by source.
    asserts.true(env, natives.native_tree in payload)
    written = {
        action.outputs.to_list()[0].basename: json.decode(action.content)
        for action in analysistest.target_actions(env)
        if action.mnemonic == "FileWrite"
    }
    destinations = written[target.label.name + ".jars.json"]
    catalogue = written[target.label.name + ".metadata-catalogue.json"]
    asserts.true(env, {"source": natives.jar.path, "relativePath": natives.relative_path} in destinations)
    asserts.true(env, {"source": natives.native_tree.path, "relativePath": natives.native_lib_dir, "tree": True} in destinations)
    asserts.true(env, {"source": natives.jar.path, "metadata": natives.metadata.path, "relativePath": natives.jar.basename} in catalogue)
    asserts.true(env, {"source": natives.native_tree.path, "metadata": natives.metadata.path, "relativePath": "native", "tree": True} in catalogue)
    for entries in [destinations, catalogue]:
        asserts.equals(env, len(payload), len(entries))
        asserts.equals(env, sorted([entry["source"] for entry in entries]), [entry["source"] for entry in entries])
    return analysistest.end(env)

_packed_component_test = analysistest.make(
    _packed_component_test_impl,
    attrs = {"natives": attr.label(mandatory = True, providers = [DevDistPlatformJarInfo])},
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
    packed = name + "_packed"
    _fake_module(name = packed_owner, module_name = "test.packed")
    _fake_module(name = raw_owner, module_name = "test.raw")
    _fake_packed(name = packed, member = ":" + packed_owner, library = ":" + library)
    nested = name + "_nested"
    _fake_platform_jar(name = nested, destination = "ext/nested.jar")
    natives = name + "_natives"
    _fake_platform_jar(name = natives, destination = "natives.jar", native_lib_dir = "jna")
    payload = name + "_payload"
    dev_dist_platform_payload(
        name = payload,
        modules = [":" + packed_owner, ":" + raw_owner],
        packed = [":" + packed, ":" + nested, ":" + natives],
    )
    tests.append(name + "_platform_payload_test")
    _platform_payload_test(
        name = tests[-1],
        target_under_test = ":" + payload,
        packed = ":" + packed,
        nested = ":" + nested,
        natives = ":" + natives,
        expected_declared_modules = ["test.raw"],
    )

    # One owner per `lib/<dir>/`: two trees in one directory are refused where both jars are still named.
    duplicate_natives = [name + "_duplicate_natives_first", name + "_duplicate_natives_second"]
    for duplicate in duplicate_natives:
        _fake_platform_jar(name = duplicate, destination = duplicate + ".jar", native_lib_dir = "shared")
    duplicate_payload = name + "_duplicate_natives_payload"
    dev_dist_platform_payload(
        name = duplicate_payload,
        modules = [":" + raw_owner],
        packed = [":" + duplicate for duplicate in duplicate_natives],
        tags = ["manual"],
    )
    tests.append(duplicate_payload + "_test")
    _expected_failure_test(
        name = tests[-1],
        target_under_test = ":" + duplicate_payload,
        expected_message = "lib/shared/ receives the native tree of both",
    )

    # The union of the bundled plugins' raw content. `plugins` go through the product transition, `deps` come as they
    # are, and both land in one provider. A target without `DevDistContentInfo` is refused at the attribute.
    product_info = name + "_product_info"
    dev_dist_product_info(
        name = product_info,
        release_date = "20260101",
        release_version = "2026300",
        platform_prefix = "idea",
    )
    second_library = name + "_second_library"
    _fake_library(name = second_library)
    plugin_contents = [name + "_plugin_content_first", name + "_plugin_content_second", name + "_plugin_content_frontend"]
    _fake_content(name = plugin_contents[0], modules = [":" + packed_owner], library = ":" + library)
    _fake_content(name = plugin_contents[1], modules = [":" + packed_owner, ":" + raw_owner], library = ":" + second_library)
    _fake_content(name = plugin_contents[2], modules = [":" + raw_owner])
    plugin_content = name + "_plugin_content"
    dev_dist_plugin_content(
        name = plugin_content,
        plugins = [":" + plugin_contents[0], ":" + plugin_contents[1]],
        deps = [":" + plugin_contents[2]],
        product_info = ":" + product_info,
    )
    tests.append(plugin_content + "_test")
    _plugin_content_test(
        name = tests[-1],
        target_under_test = ":" + plugin_content,
        plugins = [":" + content for content in plugin_contents],
    )
    refused_plugin_content = name + "_refused_plugin_content"
    dev_dist_plugin_content(
        name = refused_plugin_content,
        plugins = [":" + raw_owner],
        product_info = ":" + product_info,
        tags = ["manual"],
    )
    tests.append(refused_plugin_content + "_test")
    _expected_failure_test(
        name = tests[-1],
        target_under_test = ":" + refused_plugin_content,
        expected_message = "does not have mandatory providers",
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
    _packed_component_test(name = tests[-1], target_under_test = ":" + packed_component, natives = ":" + natives)

    empty_inputs = name + "_empty_inputs"
    intellij_dev_build_inputs(name = empty_inputs)
    tests.append(empty_inputs + "_test")
    _manifest_test(name = tests[-1], target_under_test = ":" + empty_inputs, expected_suffix = ".bazel-inputs")

    tests.extend(_plugin_macro_tests(name))
    content_module_jar_test_suite(name = name + "_production")
    tests.append(name + "_production")
    native.test_suite(name = name, tests = tests)
