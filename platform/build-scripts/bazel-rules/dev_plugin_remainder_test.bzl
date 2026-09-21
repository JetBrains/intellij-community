"""Focused provider wiring tests for generated plugin components."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("@rules_java//java:defs.bzl", "JavaInfo", "java_common")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load(":content_module_jar.bzl", "ContentModuleJarInfo", "content_module_jar", "content_module_jar_target_name")
load(":dev_dist_plugin_descriptor.bzl", "DevDistPluginDescriptorInfo", "DevDistProductInfo", "dev_dist_plugin_descriptor", "dev_dist_plugin_descriptor_target_name", "dev_dist_product_info", "dev_dist_product_info_transition")
load(":dev_plugin_remainder.bzl", "DevPluginArtifactCatalogueInfo", "DevPluginGraphInfo", "DevPluginRemainderInfo", "dev_dist_complex_plugin", "dev_dist_complex_plugin_variant", "dev_plugin_artifact_catalogue", "dev_plugin_component", "dev_plugin_file_graph", "dev_plugin_remainder_from_plan", "plan_product_error", "platform_values_error")
load(":intellij_dev_dist.bzl", "IntellijDevFragmentInfo")

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
    doc = "A library container: the shape the library generator emits for a multi-jar library.",
)

def _file_impl(ctx):
    output = ctx.actions.declare_file(ctx.label.name + ".json")
    ctx.actions.write(output, ctx.attr.content)
    return [DefaultInfo(files = depset([output]))]

_file = rule(
    implementation = _file_impl,
    attrs = {"content": attr.string(default = "{}\n")},
)

def _product_scoped_file_impl(ctx):
    product = ctx.attr._product_info[DevDistProductInfo]
    if product.release_date != ctx.attr.release_date or product.release_version != ctx.attr.release_version:
        fail("expected product info, got %s/%s" % (product.release_date, product.release_version))
    output = ctx.actions.declare_file(ctx.label.name + ".xml")
    ctx.actions.write(output, product.release_date + "/" + product.release_version + "\n")
    return [DefaultInfo(files = depset([output]))]

_product_scoped_file = rule(
    implementation = _product_scoped_file_impl,
    attrs = {
        "release_date": attr.string(mandatory = True),
        "release_version": attr.string(mandatory = True),
        "_product_info": attr.label(
            default = Label("//build:dev_dist_product_info"),
            providers = [DevDistProductInfo],
        ),
    },
)

def _source_files_impl(ctx):
    first = ctx.actions.declare_file(ctx.label.name + ".source-root/first.txt")
    second = ctx.actions.declare_file(ctx.label.name + ".source-root/nested/second.txt")
    outside = ctx.actions.declare_file(ctx.label.name + ".outside.txt")
    for output in [first, second, outside]:
        ctx.actions.write(output, output.basename + "\n")
    return [DefaultInfo(files = depset([first, second, outside]))]

_source_files = rule(implementation = _source_files_impl)

def _graph_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    graph = target[DevPluginGraphInfo]
    asserts.equals(env, ctx.file.projection, graph.projection)
    asserts.equals(env, ctx.attr.execution_version, graph.execution_version)
    asserts.equals(env, {}, graph.source_trees)
    asserts.equals(env, [ctx.file.projection], target[DefaultInfo].files.to_list())
    asserts.equals(env, [], analysistest.target_actions(env))
    return analysistest.end(env)

_graph_test = analysistest.make(
    _graph_test_impl,
    attrs = {
        "projection": attr.label(mandatory = True, allow_single_file = True),
        "execution_version": attr.int(mandatory = True),
    },
)

def _graph_resolution_test_impl(ctx):
    """A platform chain resolves its plan file in one TemplateExpand action. The substitutions are the quoted platform
    for `"{platform}"` and one quoted value per `platform_values` slot. The consumer reads the resolved file as its
    `--projection`."""
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    graph = target[DevPluginGraphInfo]
    resolved = graph.projection
    asserts.false(env, resolved.is_source)
    asserts.equals(env, target.label.name + ".plan.json", resolved.basename)
    asserts.equals(env, ctx.attr.execution_version, graph.execution_version)
    asserts.equals(env, {}, graph.source_trees)
    asserts.equals(env, [resolved], target[DefaultInfo].files.to_list())
    actions = analysistest.target_actions(env)
    asserts.equals(env, ["TemplateExpand"], [action.mnemonic for action in actions])
    asserts.equals(env, [ctx.file.projection], actions[0].inputs.to_list())
    asserts.equals(env, [resolved], actions[0].outputs.to_list())
    substitutions = {'"{platform}"': '"%s"' % ctx.attr.platform}
    for slot, value in ctx.attr.platform_values.items():
        substitutions['"{platform:%s}"' % slot] = '"%s"' % value
    asserts.equals(env, substitutions, actions[0].substitutions)
    consumers = [action for action in ctx.attr.consumer.actions if action.mnemonic == "PackDevPluginRemainder"]
    asserts.equals(env, 1, len(consumers))
    asserts.true(env, "--projection=" + resolved.path in consumers[0].argv)
    asserts.true(env, resolved in consumers[0].inputs.to_list())
    return analysistest.end(env)

_graph_resolution_test = analysistest.make(
    _graph_resolution_test_impl,
    attrs = {
        "projection": attr.label(mandatory = True, allow_single_file = True, doc = "The source plan file."),
        "platform": attr.string(mandatory = True),
        "platform_values": attr.string_dict(),
        "execution_version": attr.int(mandatory = True),
        "consumer": attr.label(mandatory = True, doc = "The from-plan remainder target of the chain."),
    },
)

def _source_tree_graph_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    graph = target[DevPluginGraphInfo]
    source_tree = graph.source_trees.get(ctx.attr.source_tree_id)
    asserts.true(env, source_tree != None)
    asserts.true(env, source_tree.is_directory)
    asserts.equals(env, [ctx.file.projection, source_tree], target[DefaultInfo].files.to_list())
    actions = analysistest.target_actions(env)
    asserts.equals(env, ["DevPluginSourceTreeArchive", "DevPluginSourceTreeExtract"], [action.mnemonic for action in actions])
    archive = actions[0]
    extract = actions[1]
    resources = {file.basename: file for file in ctx.attr.resources[DefaultInfo].files.to_list()}
    for basename in ctx.attr.included:
        asserts.true(env, resources[basename] in archive.inputs.to_list())
    outside = [file for basename, file in resources.items() if basename not in ctx.attr.included]
    for file in outside:
        asserts.false(env, file in archive.inputs.to_list())
    asserts.true(env, archive.outputs.to_list()[0] in extract.inputs.to_list())
    asserts.equals(env, [source_tree], extract.outputs.to_list())
    return analysistest.end(env)

_source_tree_graph_test = analysistest.make(
    _source_tree_graph_test_impl,
    attrs = {
        "projection": attr.label(mandatory = True, allow_single_file = True),
        "included": attr.string_list(mandatory = True),
        "resources": attr.label(mandatory = True),
        "source_tree_id": attr.string(mandatory = True),
    },
)

def _optional_source_tree_graph_test_impl(ctx):
    """An optional source tree is a directory artifact in both of its states. With files below the prefix, the zipper
    archives exactly those files, as a required tree does. With none, the rule writes an empty archive and the resource
    files reach no action. The extract action reads a different archive in each state, so the two states have
    different action keys, and the tree is the same catalogue artifact either way."""
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    graph = target[DevPluginGraphInfo]
    source_tree = graph.source_trees.get(ctx.attr.source_tree_id)
    asserts.true(env, source_tree != None)
    asserts.true(env, source_tree.is_directory)
    asserts.equals(env, [ctx.file.projection, source_tree], target[DefaultInfo].files.to_list())
    actions = analysistest.target_actions(env)
    resources = ctx.attr.resources[DefaultInfo].files.to_list()
    extract = actions[1]
    if ctx.attr.included:
        asserts.equals(env, ["DevPluginSourceTreeArchive", "DevPluginSourceTreeExtract"], [action.mnemonic for action in actions])
        archive = actions[0]
        asserts.equals(
            env,
            sorted(ctx.attr.included),
            sorted([file.basename for file in archive.inputs.to_list() if file in resources]),
        )
        asserts.true(env, archive.outputs.to_list()[0] in extract.inputs.to_list())
    else:
        asserts.equals(env, ["FileWrite", "DevPluginSourceTreeExtract"], [action.mnemonic for action in actions])
        written = actions[0]
        asserts.equals(env, _EMPTY_JAR, written.content)
        for action in actions:
            for file in resources:
                asserts.false(env, file in action.inputs.to_list())
        asserts.true(env, written.outputs.to_list()[0] in extract.inputs.to_list())
    asserts.equals(env, [source_tree], extract.outputs.to_list())
    return analysistest.end(env)

_optional_source_tree_graph_test = analysistest.make(
    _optional_source_tree_graph_test_impl,
    attrs = {
        "projection": attr.label(mandatory = True, allow_single_file = True),
        "included": attr.string_list(doc = "The basenames of the resource files below the prefix. Empty for the absent state."),
        "resources": attr.label(mandatory = True),
        "source_tree_id": attr.string(mandatory = True),
    },
)

def _shared_source_tree_graph_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    graph = target[DevPluginGraphInfo]
    identifiers = ["shared-source-tree-first", "shared-source-tree-second"]
    asserts.equals(env, identifiers, sorted(graph.source_trees.keys()))
    first = graph.source_trees[identifiers[0]]
    second = graph.source_trees[identifiers[1]]
    asserts.true(env, first.is_directory)
    asserts.true(env, second.is_directory)
    asserts.false(env, first == second)
    actions = analysistest.target_actions(env)
    asserts.equals(env, 2, len([action for action in actions if action.mnemonic == "DevPluginSourceTreeArchive"]))
    asserts.equals(env, 2, len([action for action in actions if action.mnemonic == "DevPluginSourceTreeExtract"]))
    return analysistest.end(env)

_shared_source_tree_graph_test = analysistest.make(_shared_source_tree_graph_test_impl)

def _source_tree_catalogue_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    catalogue = target[DevPluginArtifactCatalogueInfo]
    source_tree = ctx.attr.graph[DevPluginGraphInfo].source_trees[ctx.attr.source_tree_id]
    asserts.equals(env, source_tree, catalogue.artifacts[ctx.attr.source_tree_id])
    asserts.equals(env, [catalogue.catalogue], target[DefaultInfo].files.to_list())
    return analysistest.end(env)

_source_tree_catalogue_test = analysistest.make(
    _source_tree_catalogue_test_impl,
    attrs = {
        "graph": attr.label(mandatory = True, providers = [DevPluginGraphInfo]),
        "source_tree_id": attr.string(default = "source-tree"),
    },
)

def _library_catalogue_test_impl(ctx):
    """The catalogue expands a library container to one member row per jar, in the container's order, under
    `<library ID>/<jar basename>`. A jar two libraries share is one row, under the ID of the first library."""
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    catalogue = target[DevPluginArtifactCatalogueInfo]
    jars = ctx.files.jars
    members = ["@lib//:two/" + jar.basename for jar in jars]
    asserts.equals(env, {"@lib//:two": members, "@lib//:shared": [members[0]]}, catalogue.libraries)
    asserts.equals(env, ["raw"] + members, catalogue.artifacts.keys())
    for member, jar in zip(members, jars):
        asserts.equals(env, jar.short_path, catalogue.artifacts[member].short_path)
    asserts.equals(env, [catalogue.catalogue], target[DefaultInfo].files.to_list())
    return analysistest.end(env)

_library_catalogue_test = analysistest.make(
    _library_catalogue_test_impl,
    attrs = {"jars": attr.label_list(mandatory = True, allow_files = [".jar"], doc = "The member jars in container order.")},
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

def _component_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    remainder = ctx.attr.remainder[DevPluginRemainderInfo]
    fragment = target[IntellijDevFragmentInfo]
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic == "CollectDevPluginComponent"]
    asserts.equals(env, 1, len(actions))
    action = actions[0]
    asserts.equals(env, "plugin-test", fragment.name)
    asserts.equals(env, [remainder.directory], fragment.payload.to_list())
    for input_file in [remainder.metadata, remainder.assets, remainder.classpath]:
        asserts.true(env, input_file in action.inputs.to_list())
    asserts.true(env, "--kind=plugin-test" in action.argv)
    asserts.true(env, "--platform-prefix=idea" in action.argv)
    platform_arguments = [argument for argument in action.argv if argument.startswith("--os=") or argument.startswith("--arch=")]
    if ctx.attr.neutral:
        asserts.true(env, "--platform-neutral" in action.argv)
        asserts.equals(env, [], platform_arguments)
    else:
        asserts.false(env, "--platform-neutral" in action.argv)
        asserts.equals(env, ["--os=linux", "--arch=x64"], platform_arguments)
    return analysistest.end(env)

_component_test = analysistest.make(
    _component_test_impl,
    attrs = {
        "remainder": attr.label(mandatory = True, providers = [DevPluginRemainderInfo]),
        "neutral": attr.bool(doc = "Whether the component states no target platform."),
    },
)

def _derived_remainder_test_impl(ctx):
    """The derived form binds the platform-substituted resource, adds the descriptor under the derived ID, and states
    the plugin directory from the main module."""
    env = analysistest.begin(ctx)
    asserts.equals(env, 1, len(ctx.attr.catalogue))
    asserts.equals(env, 1, len(ctx.attr.raw))
    catalogue = ctx.attr.catalogue[0][DevPluginArtifactCatalogueInfo]
    asserts.equals(env, ctx.attr.raw[0][DefaultInfo].files.to_list()[0], catalogue.artifacts["raw"])
    asserts.equals(env, ["raw", ctx.attr.descriptor_id], catalogue.artifacts.keys())
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic == "PackDevPluginRemainder"]
    asserts.equals(env, 1, len(actions))
    asserts.true(env, "--plugin-directory=" + ctx.attr.plugin_directory in actions[0].argv)
    return analysistest.end(env)

_derived_remainder_test = analysistest.make(
    _derived_remainder_test_impl,
    attrs = {
        "catalogue": attr.label(
            mandatory = True,
            cfg = dev_dist_product_info_transition,
            providers = [DevPluginArtifactCatalogueInfo],
        ),
        "raw": attr.label(
            mandatory = True,
            allow_single_file = True,
            cfg = dev_dist_product_info_transition,
        ),
        "product_info": attr.label(mandatory = True, providers = [DevDistProductInfo]),
        "descriptor_id": attr.string(mandatory = True),
        "plugin_directory": attr.string(mandatory = True),
        "_allowlist_function_transition": attr.label(default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist")),
    },
)

def _remainder_from_plan_test_impl(ctx):
    """A chain packs the remainder from the plan file in one Go action. The action reads the plan file, the input
    catalogue, the classpath descriptor and every catalogue artifact. It writes the remainder, the inventory, the
    asset table and the classpath record, and the provider hands all four to the component."""
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    remainder = target[DevPluginRemainderInfo]
    graph = ctx.attr.graph[DevPluginGraphInfo]
    asserts.equals(env, 1, len(ctx.attr.catalogue))
    asserts.equals(env, 1, len(ctx.attr.descriptor))
    asserts.equals(env, 1, len(ctx.attr.raw))
    catalogue = ctx.attr.catalogue[0][DevPluginArtifactCatalogueInfo]
    descriptor_target = ctx.attr.descriptor[0]
    descriptor_info = descriptor_target[DevDistPluginDescriptorInfo]
    descriptor = descriptor_info.descriptor
    classpath_descriptor = descriptor_info.classpath_descriptor
    raw = ctx.attr.raw[0][DefaultInfo].files.to_list()[0]
    asserts.equals(env, ["descriptor", "raw"], catalogue.artifacts.keys())
    asserts.equals(env, descriptor, catalogue.artifacts["descriptor"])

    # The classpath descriptor is always a file of its own. An ordinary plugin gets it from the primary action as its
    # reserialized second output. A plugin that embeds no content module gets it from a second action.
    asserts.true(env, descriptor != classpath_descriptor)
    asserts.equals(env, [descriptor], descriptor_target[DefaultInfo].files.to_list())
    descriptor_actions = descriptor_info._declaration.actions
    asserts.equals(env, 2 if ctx.attr.separate_classpath_descriptor else 1, len(descriptor_actions))
    primary_actions = [candidate for candidate in descriptor_actions if descriptor in candidate.outputs]
    classpath_actions = [candidate for candidate in descriptor_actions if classpath_descriptor in candidate.outputs]
    asserts.equals(env, 1, len(primary_actions))
    asserts.equals(env, 1, len(classpath_actions))
    primary_embed = [value for flag, value in primary_actions[0].parameters if flag == "--embed-content-modules"]
    classpath_embed = [value for flag, value in classpath_actions[0].parameters if flag == "--embed-content-modules"]
    primary_reserialize = [value for flag, value in primary_actions[0].parameters if flag == "--reserialize-before-content-embedding"]
    classpath_reserialize = [value for flag, value in classpath_actions[0].parameters if flag == "--reserialize-before-content-embedding"]
    reserialized_output = [value for flag, value in primary_actions[0].parameters if flag == "--reserialized-output"]
    asserts.equals(env, ["false" if ctx.attr.separate_classpath_descriptor else "true"], primary_embed)
    asserts.equals(env, ["true"], classpath_embed)
    asserts.equals(env, ["false"], primary_reserialize)
    asserts.equals(env, ["true" if ctx.attr.separate_classpath_descriptor else "false"], classpath_reserialize)
    asserts.equals(env, [] if ctx.attr.separate_classpath_descriptor else [classpath_descriptor], reserialized_output)
    actions = analysistest.target_actions(env)
    asserts.equals(env, ["PackDevPluginRemainder"], [action.mnemonic for action in actions])
    action = actions[0]
    asserts.equals(env, ctx.attr.graph, remainder.graph)
    asserts.equals(env, graph.execution_version, remainder.execution_version)
    asserts.true(env, remainder.directory.is_directory)
    asserts.equals(env, [], remainder.independent_artifacts.to_list())
    packer = action.argv[0]
    asserts.true(env, packer.split("/")[-1].startswith("plugin-remainder-packer"))

    # The tool inputs are the packer and its runfiles tree, both below the executable's path.
    expected_inputs = [graph.projection, catalogue.catalogue, classpath_descriptor, descriptor, raw]
    asserts.equals(
        env,
        sorted([file.path for file in expected_inputs]),
        sorted([file.path for file in action.inputs.to_list() if not file.path.startswith(packer)]),
    )
    asserts.equals(env, [remainder.directory, remainder.metadata, remainder.assets, remainder.classpath], action.outputs.to_list())
    asserts.equals(env, [
        "--projection=" + graph.projection.path,
        "--input-catalogue=" + catalogue.catalogue.path,
        "--classpath-descriptor=" + classpath_descriptor.path,
        "--plugin-directory=plugins/test",
        "--execution-version=%d" % graph.execution_version,
        "--output-dir=" + remainder.directory.path,
        "--inventory=" + remainder.metadata.path,
        "--assets=" + remainder.assets.path,
        "--classpath=" + remainder.classpath.path,
    ], action.argv[1:])
    asserts.equals(env, [remainder.directory], target[DefaultInfo].files.to_list())
    groups = target[OutputGroupInfo]
    asserts.equals(env, [remainder.directory], groups.dev_dist_plugin_remainder.to_list())
    asserts.equals(env, [remainder.metadata], groups.file_metadata.to_list())
    asserts.equals(env, [remainder.assets], groups.dev_dist_plugin_assets.to_list())
    asserts.equals(env, [remainder.classpath], groups.dev_dist_plugin_classpath.to_list())
    return analysistest.end(env)

_remainder_from_plan_test = analysistest.make(
    _remainder_from_plan_test_impl,
    attrs = {
        "graph": attr.label(mandatory = True, providers = [DevPluginGraphInfo]),
        "catalogue": attr.label(
            mandatory = True,
            cfg = dev_dist_product_info_transition,
            providers = [DevPluginArtifactCatalogueInfo],
        ),
        "descriptor": attr.label(
            mandatory = True,
            cfg = dev_dist_product_info_transition,
            providers = [DevDistPluginDescriptorInfo],
        ),
        "raw": attr.label(
            mandatory = True,
            allow_single_file = True,
            cfg = dev_dist_product_info_transition,
        ),
        "product_info": attr.label(mandatory = True, providers = [DevDistProductInfo]),
        "separate_classpath_descriptor": attr.bool(),
        "_allowlist_function_transition": attr.label(default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist")),
    },
)

def _check_chain_shape(chain, component_visibility = ["//visibility:public"]):
    """Fails at load time when the targets of a chain differ from the one chain shape.

    A chain has four targets: the graph, the catalogue, a `dev_plugin_remainder_from_plan` and the component. No chain
    declares a preparation target. The component states `component_visibility`, public unless the call names another.
    """
    expected = {
        "graph": "dev_plugin_file_graph",
        "catalogue": "_dev_plugin_artifact_catalogue",
        "catalogue_compiled_inputs": "_compiled_artifact_inputs",
        "remainder": "dev_plugin_remainder_from_plan",
        "component": "dev_plugin_component",
    }
    for suffix, kind in expected.items():
        rule = native.existing_rule(chain + "_" + suffix)
        if rule == None or rule["kind"] != kind:
            fail("chain %s declares %s_%s as %s; expected %s" % (chain, chain, suffix, rule["kind"] if rule else None, kind))
    if native.existing_rule(chain + "_preparation") != None:
        fail("chain %s declares a preparation target" % chain)

    # `existing_rule` reports a canonical label, `@@//visibility:public`. The comparison drops the repository.
    visibility = ["//" + label.split("//", 1)[1] for label in native.existing_rule(chain + "_component").get("visibility", [])]
    if visibility != component_visibility:
        fail("chain %s declares %s_component with the visibility %s; expected %s" % (chain, chain, visibility, component_visibility))

def _plan(variant, layout_signature, destination):
    """The plan file of a fixture: one jar packed from a module-filter operation over the raw input. An analysis test
    runs no action, so the packer never reads it. The content states what the chain would pack."""
    return json.encode({
        "version": 1,
        "plugin": "test.plugin",
        "variant": variant,
        "layoutSignature": layout_signature,
        "assets": [{
            "destination": destination,
            "recipe": {
                "sources": [
                    {"input": "module-filter:raw:output", "kind": "prepared", "filter": "prepared"},
                    {"input": "descriptor", "kind": "file", "filter": "none", "entry": "META-INF/plugin.xml", "options": ["patch"]},
                ],
                "writer": {"manifest": "drop", "mergeEntities": True},
            },
        }],
        "preparations": [{"id": "module-filter:raw", "inputs": ["raw"], "outputs": ["module-filter:raw:output"], "modelSignature": "0" * 64}],
        "operations": [{"id": "module-filter:raw", "input": {"artifact": "raw"}, "output": "module-filter:raw:output", "manifest": "keep", "excludes": ["drop/**"]}],
    }) + "\n"

_PLAN = _plan("", "0" * 64, "lib/test.jar")

# The folded form of the same plan: the variant is the chain's platform, and the two leaves that differ per platform
# are slots. The graph of each chain resolves them from the call's `platform_values`.
_FOLDED_PLAN = _plan("{platform}", "{platform:layoutSignature}", "{platform:destination}")

def _check_platform_values_refusals(main_module):
    """Fails at load time when `platform_values_error` accepts a shape or a value the macro must refuse, or refuses a
    dict that fits its platforms."""
    platforms = ["linux_x64", "darwin_aarch64"]
    values = {"linux_x64": {"layoutSignature": "1" * 64}, "darwin_aarch64": {"layoutSignature": "2" * 64}}
    for call_platforms, platform_values, expected in [
        (None, values, "platform_values without platforms"),
        (["linux_x64"], values, "but its platforms are"),
        (platforms, {"linux_x64": {"layoutSignature": "1" * 64}, "darwin_aarch64": {"destination": "lib/test.jar"}}, "states the slots"),
        (platforms, {"linux_x64": {"layoutSignature": 'a"b'}, "darwin_aarch64": {"layoutSignature": "b"}}, "needs JSON escaping"),
        (platforms, {"linux_x64": {"layoutSignature": "{platform}"}, "darwin_aarch64": {"layoutSignature": "b"}}, "holds a token"),
    ]:
        error = platform_values_error(main_module, call_platforms, platform_values)
        if error == None or expected not in error:
            fail("platform_values_error accepted %s with platforms %s: %s" % (platform_values, call_platforms, error))
    for accepted in [{}, values]:
        error = platform_values_error(main_module, platforms, accepted)
        if error != None:
            fail("platform_values_error refused %s: %s" % (accepted, error))

def _check_plan_product_refusals(main_module):
    """Fails at load time when `plan_product_error` accepts a plan product that is not the product, or refuses the
    empty one, the product itself, or the product's case-safe plan name."""
    for refused in ["server", "ideacommunity", "Idea_community"]:
        error = plan_product_error(main_module, "Idea", refused)
        if error == None or "is not its product" not in error:
            fail("plan_product_error accepted the plan product '%s' for 'Idea': %s" % (refused, error))
    for accepted in ["", "Idea", "idea_community"]:
        error = plan_product_error(main_module, "Idea", accepted)
        if error != None:
            fail("plan_product_error refused '%s': %s" % (accepted, error))

def _reused_component_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    remainder = ctx.attr.remainder[DevPluginRemainderInfo]
    content = ctx.attr.content_jar[ContentModuleJarInfo]
    fragment = target[IntellijDevFragmentInfo]
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic == "CollectDevPluginComponent"]
    asserts.equals(env, 1, len(actions))

    # The macro derives the remainder and the component from one list of owner targets, so the reused content module
    # jar reaches the payload beside the remainder.
    payload = fragment.payload.to_list()
    asserts.equals(env, 2, len(payload))
    asserts.true(env, remainder.directory in payload)
    asserts.true(env, content.jar.short_path in [file.short_path for file in payload])
    asserts.equals(env, [content.jar.short_path], [file.short_path for file in remainder.independent_artifacts.to_list()])

    # The module name is the key of the reused jar: the component writes it as the artifact of the collection row.
    spec_actions = [action for action in analysistest.target_actions(env) if action.mnemonic == "FileWrite"]
    asserts.equals(env, 1, len(spec_actions))
    spec = json.decode(spec_actions[0].content)
    asserts.equals(env, [content.module_name], [row["artifact"] for row in spec["independent"]])
    asserts.equals(env, [content.jar.path], [row["source"] for row in spec["independent"]])
    return analysistest.end(env)

_reused_component_test = analysistest.make(
    _reused_component_test_impl,
    attrs = {
        "remainder": attr.label(mandatory = True, providers = [DevPluginRemainderInfo]),
        "content_jar": attr.label(mandatory = True, providers = [ContentModuleJarInfo]),
    },
)

def _reused_remainder_test_impl(ctx):
    """The remainder action names each reused module to the packer, and no other input of it overlaps the reused jar."""
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    content = ctx.attr.content_jar[ContentModuleJarInfo]
    remainder = target[DevPluginRemainderInfo]
    asserts.equals(env, [content.jar.short_path], [file.short_path for file in remainder.independent_artifacts.to_list()])
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic == "PackDevPluginRemainder"]
    asserts.equals(env, 1, len(actions))
    asserts.equals(env, ["--independent-module=" + content.module_name], [argument for argument in actions[0].argv if argument.startswith("--independent-module=")])
    asserts.false(env, content.jar.short_path in [file.short_path for file in actions[0].inputs.to_list()])
    return analysistest.end(env)

_reused_remainder_test = analysistest.make(
    _reused_remainder_test_impl,
    attrs = {"content_jar": attr.label(mandatory = True, providers = [ContentModuleJarInfo])},
)

def dev_plugin_remainder_test_suite(name):
    """Declares the focused generated-component tests.

    Args:
        name: The test suite name.
    """
    projection = name + "_projection"
    descriptor_source = name + "_descriptor_source"
    raw = name + "_raw"
    for target in [projection, raw]:
        _file(name = target)
    product_info = name + "_product_info"
    release_date = "20260101"
    release_version = "2026300"
    dev_dist_product_info(
        name = product_info,
        release_date = release_date,
        release_version = release_version,
    )
    _product_scoped_file(
        name = descriptor_source,
        release_date = release_date,
        release_version = release_version,
        tags = ["manual"],
    )
    normal_main_module = "test.%s.normal" % name
    normal_descriptor = dev_dist_plugin_descriptor_target_name(normal_main_module)
    dev_dist_plugin_descriptor(
        main_module = normal_main_module,
        descriptor_module = ":" + descriptor_source,
        descriptor = descriptor_source,
    )
    scrambled_main_module = "test.%s.scrambled" % name
    scrambled_descriptor = dev_dist_plugin_descriptor_target_name(scrambled_main_module)
    dev_dist_plugin_descriptor(
        main_module = scrambled_main_module,
        descriptor_module = ":" + descriptor_source,
        descriptor = descriptor_source,
        embed_content_modules = False,
    )

    graph = name + "_graph"
    dev_plugin_file_graph(
        name = graph,
        projection = ":" + projection,
        execution_version = 1,
    )
    graph_test = graph + "_test"
    _graph_test(
        name = graph_test,
        target_under_test = ":" + graph,
        projection = ":" + projection,
        execution_version = 1,
    )

    source_files = name + "_source_files"
    _source_files(name = source_files)
    resources = name + "_resources"
    native.filegroup(name = resources, srcs = [":" + source_files])
    source_tree_graph = name + "_source_tree_graph"
    dev_plugin_file_graph(
        name = source_tree_graph,
        projection = ":" + projection,
        execution_version = 2,
        source_tree_targets = {"source-tree": ":" + resources},
        source_tree_prefixes = {"source-tree": native.package_name() + "/" + source_files + ".source-root"},
    )
    source_tree_graph_test = source_tree_graph + "_test"
    _source_tree_graph_test(
        name = source_tree_graph_test,
        target_under_test = ":" + source_tree_graph,
        projection = ":" + projection,
        included = ["first.txt", "second.txt"],
        resources = ":" + resources,
        source_tree_id = "source-tree",
    )
    root_source_tree_graph = name + "_root_source_tree_graph"
    dev_plugin_file_graph(
        name = root_source_tree_graph,
        projection = ":" + projection,
        execution_version = 2,
        source_tree_targets = {"root-source-tree": ":" + resources},
        source_tree_prefixes = {"root-source-tree": ""},
    )
    root_source_tree_graph_test = root_source_tree_graph + "_test"
    _source_tree_graph_test(
        name = root_source_tree_graph_test,
        target_under_test = ":" + root_source_tree_graph,
        projection = ":" + projection,
        included = ["first.txt", "second.txt", source_files + ".outside.txt"],
        resources = ":" + resources,
        source_tree_id = "root-source-tree",
    )

    # One target under two IDs: the ID-keyed attribute needs no alias for that.
    shared_source_tree_graph = name + "_shared_source_tree_graph"
    dev_plugin_file_graph(
        name = shared_source_tree_graph,
        projection = ":" + projection,
        execution_version = 2,
        source_tree_targets = {
            "shared-source-tree-first": ":" + resources,
            "shared-source-tree-second": ":" + resources,
        },
        source_tree_prefixes = {
            "shared-source-tree-first": native.package_name() + "/" + source_files + ".source-root",
            "shared-source-tree-second": native.package_name() + "/" + source_files + ".source-root",
        },
    )
    shared_source_tree_graph_test = shared_source_tree_graph + "_test"
    _shared_source_tree_graph_test(
        name = shared_source_tree_graph_test,
        target_under_test = ":" + shared_source_tree_graph,
    )
    source_tree_catalogue = name + "_source_tree_catalogue"
    dev_plugin_artifact_catalogue(
        name = source_tree_catalogue,
        source_tree_graph = ":" + source_tree_graph,
    )
    source_tree_catalogue_test = source_tree_catalogue + "_test"
    _source_tree_catalogue_test(
        name = source_tree_catalogue_test,
        target_under_test = ":" + source_tree_catalogue,
        graph = ":" + source_tree_graph,
    )

    # A library reaches the catalogue as its version-free container. The rule expands it to its member jars.
    library_first = name + "_library_first"
    library_second = name + "_library_second"
    for jar in [library_first, library_second]:
        _fixture_module(name = jar, module_name = "test." + jar)
    _fixture_library(name = name + "_two_library", jars = [":" + library_second, ":" + library_first])
    _fixture_library(name = name + "_shared_library", jars = [":" + library_second])
    library_catalogue = name + "_library_catalogue"
    dev_plugin_artifact_catalogue(
        name = library_catalogue,
        resource_inputs = {":" + raw: "raw"},
        libraries = {
            ":" + name + "_two_library": "@lib//:two",
            ":" + name + "_shared_library": "@lib//:shared",
        },
    )
    library_catalogue_test = library_catalogue + "_test"
    _library_catalogue_test(
        name = library_catalogue_test,
        target_under_test = ":" + library_catalogue,
        jars = [":" + library_second, ":" + library_first],
    )

    # An optional source tree: the same target and ID shape as a required tree, present with files below the prefix
    # and absent with none. A required tree with no file below its prefix fails, and an optional ID must name a tree.
    absent_prefix = native.package_name() + "/" + source_files + ".absent"
    optional_source_tree_tests = []
    for suffix, prefix, included in [
        ("present", native.package_name() + "/" + source_files + ".source-root", ["first.txt", "second.txt"]),
        ("absent", absent_prefix, []),
    ]:
        optional_graph = name + "_optional_" + suffix + "_source_tree_graph"
        dev_plugin_file_graph(
            name = optional_graph,
            projection = ":" + projection,
            execution_version = 2,
            source_tree_targets = {"optional-tree": ":" + resources},
            source_tree_prefixes = {"optional-tree": prefix},
            optional_source_trees = ["optional-tree"],
        )
        optional_graph_test = optional_graph + "_test"
        _optional_source_tree_graph_test(
            name = optional_graph_test,
            target_under_test = ":" + optional_graph,
            projection = ":" + projection,
            included = included,
            resources = ":" + resources,
            source_tree_id = "optional-tree",
        )
        optional_source_tree_tests.append(optional_graph_test)
        optional_catalogue = name + "_optional_" + suffix + "_source_tree_catalogue"
        dev_plugin_artifact_catalogue(
            name = optional_catalogue,
            source_tree_graph = ":" + optional_graph,
        )
        optional_catalogue_test = optional_catalogue + "_test"
        _source_tree_catalogue_test(
            name = optional_catalogue_test,
            target_under_test = ":" + optional_catalogue,
            graph = ":" + optional_graph,
            source_tree_id = "optional-tree",
        )
        optional_source_tree_tests.append(optional_catalogue_test)
    for suffix, prefix, optional_source_trees, expected_message in [
        ("required_absent", absent_prefix, [], "has no declared File below"),
        ("undeclared_optional", absent_prefix, ["optional-tree", "missing-tree"], "optional source tree IDs are not declared source trees"),
    ]:
        refused_source_tree_graph = name + "_" + suffix + "_source_tree_graph"
        dev_plugin_file_graph(
            name = refused_source_tree_graph,
            projection = ":" + projection,
            execution_version = 2,
            source_tree_targets = {"optional-tree": ":" + resources},
            source_tree_prefixes = {"optional-tree": prefix},
            optional_source_trees = optional_source_trees,
            tags = ["manual"],
        )
        refused_source_tree_graph_test = refused_source_tree_graph + "_test"
        _expected_failure_test(
            name = refused_source_tree_graph_test,
            target_under_test = ":" + refused_source_tree_graph,
            expected_message = expected_message,
        )
        optional_source_tree_tests.append(refused_source_tree_graph_test)

    unsafe_source_tree_graph = name + "_unsafe_source_tree_graph"
    dev_plugin_file_graph(
        name = unsafe_source_tree_graph,
        projection = ":" + projection,
        execution_version = 2,
        source_tree_targets = {"source-tree": ":" + resources},
        source_tree_prefixes = {"source-tree": "../unsafe"},
        tags = ["manual"],
    )
    unsafe_source_tree_graph_test = unsafe_source_tree_graph + "_test"
    _expected_failure_test(
        name = unsafe_source_tree_graph_test,
        target_under_test = ":" + unsafe_source_tree_graph,
        expected_message = "unsafe repository-relative prefix",
    )

    # The explicit rules: a remainder over a normal descriptor and over a descriptor that embeds no content module.
    plan_file = name + "_plan"
    _file(name = plan_file, content = _PLAN)
    plan_graph = name + "_plan_graph"
    dev_plugin_file_graph(
        name = plan_graph,
        projection = ":" + plan_file,
        execution_version = 1,
    )
    remainders = {}
    remainder_tests = []
    for suffix, descriptor, separate_classpath_descriptor in [
        ("normal", normal_descriptor, False),
        ("scrambled", scrambled_descriptor, True),
    ]:
        catalogue = name + "_" + suffix + "_catalogue"
        dev_plugin_artifact_catalogue(
            name = catalogue,
            resource_inputs = {
                ":" + descriptor: "descriptor",
                ":" + raw: "raw",
            },
        )
        remainder = name + "_" + suffix + "_remainder"
        remainders[suffix] = remainder
        dev_plugin_remainder_from_plan(
            name = remainder,
            graph = ":" + plan_graph,
            artifact_catalogue = ":" + catalogue,
            descriptor = ":" + descriptor,
            plugin_directory = "plugins/test",
            product_info = ":" + product_info,
            tags = ["manual"],
        )
        remainder_test = remainder + "_test"
        remainder_tests.append(remainder_test)
        _remainder_from_plan_test(
            name = remainder_test,
            target_under_test = ":" + remainder,
            graph = ":" + plan_graph,
            catalogue = ":" + catalogue,
            descriptor = ":" + descriptor,
            product_info = ":" + product_info,
            raw = ":" + raw,
            separate_classpath_descriptor = separate_classpath_descriptor,
        )

    remainder = remainders["normal"]

    component = name + "_component"
    dev_plugin_component(
        name = component,
        remainder = ":" + remainder,
        plugin_directory = "plugins/test",
        component_name = "plugin-test",
        platform_prefix = "idea",
        target_platform = "linux_x64",
        tags = ["manual"],
    )
    component_test = component + "_test"
    _component_test(
        name = component_test,
        target_under_test = ":" + component,
        remainder = ":" + remainder,
    )

    neutral_component = name + "_neutral_component"
    dev_plugin_component(
        name = neutral_component,
        remainder = ":" + remainder,
        plugin_directory = "plugins/test",
        component_name = "plugin-test",
        platform_prefix = "idea",
        tags = ["manual"],
    )
    neutral_component_test = neutral_component + "_test"
    _component_test(
        name = neutral_component_test,
        target_under_test = ":" + neutral_component,
        remainder = ":" + remainder,
        neutral = True,
    )

    # The explicit form declares the four targets of a chain under the generator's names. The component takes the
    # visibility the call states.
    member = name + "_member"
    _fixture_module(name = member, module_name = "test.%s.member" % name)
    content_module_jar(module = ":" + member)
    content_jar = content_module_jar_target_name(member)
    complex = name + "_complex"
    dev_dist_complex_plugin_variant(
        name = complex,
        projection = ":" + projection,
        execution_version = 1,
        descriptor = ":" + normal_descriptor,
        plugin_directory = "plugins/test",
        component_name = "plugin-test",
        platform_prefix = "idea",
        product_info = ":" + product_info,
        target_platform = "linux_x64",
        resource_inputs = {
            ":" + normal_descriptor: "descriptor",
            ":" + raw: "raw",
        },
        independent_artifacts = [":" + content_jar],
        tags = ["manual"],
        visibility = ["//visibility:private"],
    )
    _check_chain_shape(complex, component_visibility = ["//visibility:private"])
    complex_graph_test = complex + "_graph_test"
    _graph_resolution_test(
        name = complex_graph_test,
        target_under_test = ":" + complex + "_graph",
        projection = ":" + projection,
        platform = "linux_x64",
        execution_version = 1,
        consumer = ":" + complex + "_remainder",
    )
    complex_component_test = complex + "_component_test"
    _reused_component_test(
        name = complex_component_test,
        target_under_test = ":" + complex + "_component",
        remainder = ":" + complex + "_remainder",
        content_jar = ":" + content_jar,
    )
    complex_remainder_test = complex + "_remainder_test"
    _reused_remainder_test(
        name = complex_remainder_test,
        target_under_test = ":" + complex + "_remainder",
        content_jar = ":" + content_jar,
    )

    # A chain over a plan file with a module-filter operation. The Go packer executes the operation in the remainder
    # action. The same action writes the asset table and the classpath record the component reads.
    plan_chain = name + "_plan_chain"
    dev_dist_complex_plugin_variant(
        name = plan_chain,
        projection = ":" + plan_file,
        execution_version = 1,
        descriptor = ":" + normal_descriptor,
        plugin_directory = "plugins/test",
        component_name = "plugin-test",
        platform_prefix = "idea",
        product_info = ":" + product_info,
        target_platform = "linux_x64",
        resource_inputs = {
            ":" + normal_descriptor: "descriptor",
            ":" + raw: "raw",
        },
        tags = ["manual"],
    )
    _check_chain_shape(plan_chain)
    plan_chain_graph_test = plan_chain + "_graph_test"
    _graph_resolution_test(
        name = plan_chain_graph_test,
        target_under_test = ":" + plan_chain + "_graph",
        projection = ":" + plan_file,
        platform = "linux_x64",
        execution_version = 1,
        consumer = ":" + plan_chain + "_remainder",
    )
    plan_chain_remainder_test = plan_chain + "_remainder_test"
    _remainder_from_plan_test(
        name = plan_chain_remainder_test,
        target_under_test = ":" + plan_chain + "_remainder",
        graph = ":" + plan_chain + "_graph",
        catalogue = ":" + plan_chain + "_catalogue",
        descriptor = ":" + normal_descriptor,
        raw = ":" + raw,
        product_info = ":" + product_info,
    )
    plan_chain_component_test = plan_chain + "_component_test"
    _component_test(
        name = plan_chain_component_test,
        target_under_test = ":" + plan_chain + "_component",
        remainder = ":" + plan_chain + "_remainder",
    )

    # The derived form: one call, one chain per platform, the plan label `<main module>.<platform>.dev-plan.json` in
    # the package of the call and the descriptor entry derived, and the platform token substituted in a label.
    derived_module = "test.%s.derived" % name
    derived_platforms = ["linux_x64", "darwin_aarch64"]
    for platform in derived_platforms:
        _file(name = name + "_derived_raw_" + platform)
        _file(name = derived_module + "." + platform + ".dev-plan.json")
    dev_dist_complex_plugin(
        main_module = derived_module,
        product = "idea",
        product_info = ":" + product_info,
        descriptor = ":" + normal_descriptor,
        execution_version = 1,
        platforms = derived_platforms,
        resource_inputs = {":" + name + "_derived_raw_{platform}": "raw"},
        tags = ["manual"],
    )
    derived_tests = []
    for platform in derived_platforms:
        stem = "idea_" + platform + "_" + derived_module
        _check_chain_shape(stem)
        derived_test = stem + "_test"
        _derived_remainder_test(
            name = derived_test,
            target_under_test = ":" + stem + "_remainder",
            catalogue = ":" + stem + "_catalogue",
            raw = ":" + name + "_derived_raw_" + platform,
            product_info = ":" + product_info,
            descriptor_id = "descriptor:" + derived_module,
            plugin_directory = "plugins/" + derived_module.replace(".", "-"),
        )
        derived_tests.append(derived_test)
        derived_graph_test = stem + "_graph_test"
        _graph_resolution_test(
            name = derived_graph_test,
            target_under_test = ":" + stem + "_graph",
            projection = ":" + derived_module + "." + platform + ".dev-plan.json",
            platform = platform,
            execution_version = 1,
            consumer = ":" + stem + "_remainder",
        )
        derived_tests.append(derived_graph_test)

    # The homed form: `plan_package` names the package that holds the plan file, and the chain stays in the package of
    # the call. The plan home exports the file, as the community section of a cross-half plugin does.
    plan_home_module = "test.%s.plan_home" % name
    plan_home_package = "//" + native.package_name() + "/plan-home"
    plan_home_projection = plan_home_package + ":" + plan_home_module + ".linux_x64.dev-plan.json"
    plan_home_raw = name + "_plan_home_raw"
    _file(name = plan_home_raw)
    dev_dist_complex_plugin(
        main_module = plan_home_module,
        product = "idea",
        plan_package = plan_home_package,
        product_info = ":" + product_info,
        descriptor = ":" + normal_descriptor,
        execution_version = 1,
        platforms = ["linux_x64"],
        resource_inputs = {":" + plan_home_raw: "raw"},
        tags = ["manual"],
    )
    plan_home_stem = "idea_linux_x64_" + plan_home_module
    _check_chain_shape(plan_home_stem)
    plan_home_test = plan_home_stem + "_graph_test"
    _graph_resolution_test(
        name = plan_home_test,
        target_under_test = ":" + plan_home_stem + "_graph",
        projection = plan_home_projection,
        platform = "linux_x64",
        execution_version = 1,
        consumer = ":" + plan_home_stem + "_remainder",
    )

    # The folded form: one call, one plan file with tokens, one chain per platform, and the values of each platform on
    # the call. The graph of each chain resolves the plan file, and the from-plan remainder reads the resolved file.
    folded_module = "test.%s.folded" % name
    folded_platforms = ["linux_x64", "darwin_aarch64"]
    folded_values = {
        "linux_x64": {"destination": "lib/linux-x64/test.jar", "layoutSignature": "1" * 64},
        "darwin_aarch64": {"destination": "lib/darwin-aarch64/test.jar", "layoutSignature": "2" * 64},
    }
    _check_platform_values_refusals(folded_module)
    folded_projection = folded_module + ".dev-plan.json"
    _file(name = folded_projection, content = _FOLDED_PLAN)
    for platform in folded_platforms:
        _file(name = name + "_folded_raw_" + platform)
    dev_dist_complex_plugin(
        main_module = folded_module,
        product = "idea",
        product_info = ":" + product_info,
        descriptor = ":" + normal_descriptor,
        execution_version = 1,
        platforms = folded_platforms,
        platform_values = folded_values,
        resource_inputs = {":" + name + "_folded_raw_{platform}": "raw"},
        tags = ["manual"],
    )
    folded_tests = []
    for platform in folded_platforms:
        stem = "idea_" + platform + "_" + folded_module
        _check_chain_shape(stem)
        folded_test = stem + "_graph_test"
        _graph_resolution_test(
            name = folded_test,
            target_under_test = ":" + stem + "_graph",
            projection = ":" + folded_projection,
            platform = platform,
            platform_values = folded_values[platform],
            execution_version = 1,
            consumer = ":" + stem + "_remainder",
        )
        folded_tests.append(folded_test)

    # The product form: `plan_product` names the product in the plan file stem, `<main module>.<plan_product>`, before
    # the platform of a refused fold and alone for a folded plan. The descriptor entry stays `descriptor:<main module>`.
    _check_plan_product_refusals(name)
    product_module = "test.%s.product" % name
    product_platforms = ["linux_x64", "darwin_aarch64"]
    for platform in product_platforms:
        _file(name = name + "_product_raw_" + platform)
        _file(name = product_module + ".idea." + platform + ".dev-plan.json")
    dev_dist_complex_plugin(
        main_module = product_module,
        product = "idea",
        plan_product = "idea",
        product_info = ":" + product_info,
        descriptor = ":" + normal_descriptor,
        execution_version = 1,
        platforms = product_platforms,
        resource_inputs = {":" + name + "_product_raw_{platform}": "raw"},
        tags = ["manual"],
    )
    product_tests = []
    for platform in product_platforms:
        stem = "idea_" + platform + "_" + product_module
        _check_chain_shape(stem)
        product_graph_test = stem + "_graph_test"
        _graph_resolution_test(
            name = product_graph_test,
            target_under_test = ":" + stem + "_graph",
            projection = ":" + product_module + ".idea." + platform + ".dev-plan.json",
            platform = platform,
            execution_version = 1,
            consumer = ":" + stem + "_remainder",
        )
        product_tests.append(product_graph_test)
        product_remainder_test = stem + "_remainder_test"
        _derived_remainder_test(
            name = product_remainder_test,
            target_under_test = ":" + stem + "_remainder",
            catalogue = ":" + stem + "_catalogue",
            raw = ":" + name + "_product_raw_" + platform,
            product_info = ":" + product_info,
            descriptor_id = "descriptor:" + product_module,
            plugin_directory = "plugins/" + product_module.replace(".", "-"),
        )
        product_tests.append(product_remainder_test)
    product_folded_module = "test.%s.product_folded" % name
    product_folded_projection = product_folded_module + ".idea.dev-plan.json"
    _file(name = product_folded_projection, content = _FOLDED_PLAN)
    for platform in product_platforms:
        _file(name = name + "_product_folded_raw_" + platform)
    dev_dist_complex_plugin(
        main_module = product_folded_module,
        product = "idea",
        plan_product = "idea",
        product_info = ":" + product_info,
        descriptor = ":" + normal_descriptor,
        execution_version = 1,
        platforms = product_platforms,
        platform_values = folded_values,
        resource_inputs = {":" + name + "_product_folded_raw_{platform}": "raw"},
        tags = ["manual"],
    )
    for platform in product_platforms:
        stem = "idea_" + platform + "_" + product_folded_module
        _check_chain_shape(stem)
        product_folded_test = stem + "_graph_test"
        _graph_resolution_test(
            name = product_folded_test,
            target_under_test = ":" + stem + "_graph",
            projection = ":" + product_folded_projection,
            platform = platform,
            platform_values = folded_values[platform],
            execution_version = 1,
            consumer = ":" + stem + "_remainder",
        )
        product_tests.append(product_folded_test)

    # The graph refuses a slot value on a chain that serves every platform, and a value that cannot stand as a whole
    # JSON string leaf.
    refused_graph_tests = []
    for suffix, platform, platform_values, expected_message in [
        ("neutral_values", "", {"layoutSignature": "0" * 64}, "serves every platform"),
        ("quoted_value", "linux_x64", {"destination": 'lib/"test".jar'}, "needs JSON escaping"),
        ("token_value", "linux_x64", {"destination": "lib/{platform}/test.jar"}, "holds a token"),
    ]:
        refused_graph = name + "_" + suffix + "_graph"
        dev_plugin_file_graph(
            name = refused_graph,
            projection = ":" + projection,
            execution_version = 1,
            platform = platform,
            platform_values = platform_values,
            tags = ["manual"],
        )
        refused_graph_test = refused_graph + "_test"
        _expected_failure_test(
            name = refused_graph_test,
            target_under_test = ":" + refused_graph,
            expected_message = expected_message,
        )
        refused_graph_tests.append(refused_graph_test)

    native.test_suite(
        name = name,
        tests = [
            graph_test,
            source_tree_graph_test,
            root_source_tree_graph_test,
            shared_source_tree_graph_test,
            source_tree_catalogue_test,
        ] + optional_source_tree_tests + [
            library_catalogue_test,
            unsafe_source_tree_graph_test,
        ] + remainder_tests + [
            component_test,
            neutral_component_test,
            complex_graph_test,
            complex_component_test,
            complex_remainder_test,
            plan_chain_graph_test,
            plan_chain_remainder_test,
            plan_chain_component_test,
            plan_home_test,
        ] + derived_tests + folded_tests + product_tests + refused_graph_tests,
    )
