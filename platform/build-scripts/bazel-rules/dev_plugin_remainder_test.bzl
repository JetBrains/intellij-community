"""Focused provider wiring tests for generated plugin components."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load(":content_module_jar.bzl", "ContentModuleJarInfo", "content_module_jar", "content_module_jar_target_name")
load(":dev_dist_plugin_descriptor.bzl", "DevDistPluginDescriptorInfo", "DevDistProductInfo", "dev_dist_plugin_descriptor", "dev_dist_plugin_descriptor_target_name", "dev_dist_product_info", "dev_dist_product_info_transition")
load(":dev_plugin_remainder.bzl", "DevPluginArtifactCatalogueInfo", "DevPluginGraphInfo", "DevPluginPreparationInfo", "DevPluginRemainderInfo", "dev_dist_complex_plugin", "dev_dist_complex_plugin_variant", "dev_plugin_artifact_catalogue", "dev_plugin_component", "dev_plugin_file_graph", "dev_plugin_preparation", "dev_plugin_remainder", "plan_product_error", "platform_values_error")
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
    consumers = [action for action in ctx.attr.consumer.actions if action.mnemonic in ["PrepareDevPlugin", "PackDevPluginRemainder"]]
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
        "consumer": attr.label(mandatory = True, doc = "The preparation or the from-plan remainder target of the chain."),
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
    source_tree = ctx.attr.graph[DevPluginGraphInfo].source_trees["source-tree"]
    asserts.equals(env, source_tree, catalogue.artifacts["source-tree"])
    asserts.equals(env, [catalogue.catalogue], target[DefaultInfo].files.to_list())
    return analysistest.end(env)

_source_tree_catalogue_test = analysistest.make(
    _source_tree_catalogue_test_impl,
    attrs = {"graph": attr.label(mandatory = True, providers = [DevPluginGraphInfo])},
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

def _preparation_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    preparation = target[DevPluginPreparationInfo]
    graph = ctx.attr.graph[DevPluginGraphInfo]
    asserts.equals(env, 1, len(ctx.attr.catalogue))
    asserts.equals(env, 1, len(ctx.attr.descriptor))
    asserts.equals(env, 1, len(ctx.attr.raw))
    catalogue_target = ctx.attr.catalogue[0]
    descriptor_target = ctx.attr.descriptor[0]
    descriptor_info = descriptor_target[DevDistPluginDescriptorInfo]
    descriptor = descriptor_info.descriptor
    classpath_descriptor = descriptor_info.classpath_descriptor
    raw = ctx.attr.raw[0][DefaultInfo].files.to_list()[0]
    catalogue = catalogue_target[DevPluginArtifactCatalogueInfo]
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic == "PrepareDevPlugin"]
    asserts.equals(env, 1, len(actions))
    action = actions[0]
    asserts.equals(env, ctx.attr.graph, preparation.graph)
    asserts.equals(env, graph.execution_version, preparation.execution_version)

    # The remainder reads every catalogue artifact that is not preparation-only, the descriptor included.
    asserts.equals(env, [descriptor, raw], preparation.remainder_inputs.to_list())

    # The classpath descriptor is always a file of its own. An ordinary plugin gets it from the primary action as its
    # reserialized second output. A plugin that embeds no content module gets it from a second action.
    asserts.true(env, descriptor != classpath_descriptor)
    asserts.equals(env, [descriptor], descriptor_target[DefaultInfo].files.to_list())
    asserts.equals(env, descriptor, catalogue.artifacts["descriptor"])
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
    for input_file in [graph.projection, catalogue.catalogue, descriptor, classpath_descriptor, raw]:
        asserts.true(env, input_file in action.inputs.to_list())
    asserts.true(env, "--projection=" + graph.projection.path in action.argv)

    # The plan file is the one committed input: no second plan and no recipe file reach the preparer.
    asserts.equals(env, [], [argument for argument in action.argv if argument.startswith("--expected-plan=") or argument.startswith("--preparation-recipe=")])
    asserts.true(env, "--descriptor=" + classpath_descriptor.path in action.argv)
    asserts.false(env, "--descriptor=" + descriptor.path in action.argv)
    asserts.equals(env, [], [argument for argument in action.argv if argument.startswith("--classpath-descriptor-is-ready")])
    asserts.true(env, "--execution-version=%d" % graph.execution_version in action.argv)
    return analysistest.end(env)

_preparation_test = analysistest.make(
    _preparation_test_impl,
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

def _remainder_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    remainder = target[DevPluginRemainderInfo]
    preparation = ctx.attr.preparation[DevPluginPreparationInfo]
    actions = analysistest.target_actions(env)
    asserts.equals(env, 1, len(actions))
    asserts.equals(env, "PackDevPluginRemainder", actions[0].mnemonic)
    asserts.equals(env, preparation.graph, remainder.graph)
    asserts.equals(env, preparation.execution_version, remainder.execution_version)
    asserts.true(env, remainder.directory.is_directory)
    for input_file in [preparation.recipe, preparation.catalogue, preparation.prepared_entries] + preparation.remainder_inputs.to_list():
        asserts.true(env, input_file in actions[0].inputs.to_list())
    asserts.equals(env, [], [argument for argument in actions[0].argv if argument.startswith("--expected-plan=")])
    return analysistest.end(env)

_remainder_test = analysistest.make(
    _remainder_test_impl,
    attrs = {"preparation": attr.label(mandatory = True, providers = [DevPluginPreparationInfo])},
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

def _derived_preparation_test_impl(ctx):
    """The derived form binds the platform-substituted resource, adds the descriptor under the derived ID, states the
    plugin directory from the main module, and states the preparer choice."""
    env = analysistest.begin(ctx)
    asserts.equals(env, 1, len(ctx.attr.catalogue))
    asserts.equals(env, 1, len(ctx.attr.raw))
    catalogue = ctx.attr.catalogue[0][DevPluginArtifactCatalogueInfo]
    asserts.equals(env, ctx.attr.raw[0][DefaultInfo].files.to_list()[0], catalogue.artifacts["raw"])
    asserts.equals(env, ["raw", ctx.attr.descriptor_id], catalogue.artifacts.keys())
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic == "PrepareDevPlugin"]
    asserts.equals(env, 1, len(actions))
    asserts.true(env, "--plugin-directory=" + ctx.attr.plugin_directory in actions[0].argv)
    asserts.true(env, "--callback-preparation=false" in actions[0].argv)
    return analysistest.end(env)

_derived_preparation_test = analysistest.make(
    _derived_preparation_test_impl,
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

def _preparation_kind_test_impl(ctx):
    """The macro chooses the preparer from `preparation`: the callback preparer for `callback`, the plain preparer for
    `plain`. Both read the callback input, and the remainder reads every catalogue artifact."""
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    preparation = target[DevPluginPreparationInfo]
    asserts.equals(env, 1, len(ctx.attr.catalogue))
    asserts.equals(env, 1, len(ctx.attr.descriptor))
    asserts.equals(env, 1, len(ctx.attr.raw))
    catalogue = ctx.attr.catalogue[0][DevPluginArtifactCatalogueInfo]
    descriptor = ctx.attr.descriptor[0][DevDistPluginDescriptorInfo].descriptor
    raw = ctx.attr.raw[0][DefaultInfo].files.to_list()[0]
    asserts.equals(env, ["descriptor", "raw"], catalogue.artifacts.keys())
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic == "PrepareDevPlugin"]
    asserts.equals(env, 1, len(actions))
    action = actions[0]
    callback = ctx.attr.preparation == "callback"
    asserts.true(env, "--callback-preparation=%s" % ("true" if callback else "false") in action.argv)
    asserts.true(env, action.argv[0].split("/")[-1].startswith("plugin-callback-preparer" if callback else "plugin-preparer"))
    callback_inputs = [argument for argument in action.argv if argument.startswith("--callback-input=")]
    asserts.equals(env, [descriptor, raw], preparation.remainder_inputs.to_list())
    asserts.equals(env, ["--callback-input=" + raw.path], callback_inputs)
    asserts.true(env, raw in action.inputs.to_list())
    return analysistest.end(env)

_preparation_kind_test = analysistest.make(
    _preparation_kind_test_impl,
    attrs = {
        "preparation": attr.string(mandatory = True, values = ["plain", "callback"]),
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
        "_allowlist_function_transition": attr.label(default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist")),
    },
)

def _remainder_from_plan_test_impl(ctx):
    """A `none` chain packs the remainder from the plan file in one Go action. The action reads the plan file, the
    input catalogue, the classpath descriptor and every catalogue artifact. It writes the remainder, the inventory,
    the asset table and the classpath record, and the provider hands all four to the component."""
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    remainder = target[DevPluginRemainderInfo]
    graph = ctx.attr.graph[DevPluginGraphInfo]
    asserts.equals(env, 1, len(ctx.attr.catalogue))
    asserts.equals(env, 1, len(ctx.attr.descriptor))
    asserts.equals(env, 1, len(ctx.attr.raw))
    catalogue = ctx.attr.catalogue[0][DevPluginArtifactCatalogueInfo]
    descriptor_info = ctx.attr.descriptor[0][DevDistPluginDescriptorInfo]
    raw = ctx.attr.raw[0][DefaultInfo].files.to_list()[0]
    asserts.equals(env, ["descriptor", "raw"], catalogue.artifacts.keys())
    asserts.equals(env, descriptor_info.descriptor, catalogue.artifacts["descriptor"])
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
    expected_inputs = [graph.projection, catalogue.catalogue, descriptor_info.classpath_descriptor, descriptor_info.descriptor, raw]
    asserts.equals(
        env,
        sorted([file.path for file in expected_inputs]),
        sorted([file.path for file in action.inputs.to_list() if not file.path.startswith(packer)]),
    )
    asserts.equals(env, [remainder.directory, remainder.metadata, remainder.assets, remainder.classpath], action.outputs.to_list())
    asserts.equals(env, [
        "--projection=" + graph.projection.path,
        "--input-catalogue=" + catalogue.catalogue.path,
        "--classpath-descriptor=" + descriptor_info.classpath_descriptor.path,
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
        "_allowlist_function_transition": attr.label(default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist")),
    },
)

def _check_chain_shape(chain, preparation):
    """Fails at load time when the targets of a chain differ from the shape its preparation kind states.

    A `plain` or `callback` chain has five targets and a `dev_plugin_remainder`. A `none` chain has four: no
    preparation target, and a `dev_plugin_remainder_from_plan` in place of the remainder.
    """
    expected = {
        "graph": "dev_plugin_file_graph",
        "catalogue": "_dev_plugin_artifact_catalogue",
        "catalogue_compiled_inputs": "_compiled_artifact_inputs",
        "component": "dev_plugin_component",
    }
    if preparation == "none":
        expected["remainder"] = "dev_plugin_remainder_from_plan"
    else:
        expected["preparation"] = "dev_plugin_preparation"
        expected["remainder"] = "dev_plugin_remainder"
    for suffix, kind in expected.items():
        rule = native.existing_rule(chain + "_" + suffix)
        if rule == None or rule["kind"] != kind:
            fail("%s chain %s declares %s_%s as %s; expected %s" % (preparation, chain, chain, suffix, rule["kind"] if rule else None, kind))
    if preparation == "none" and native.existing_rule(chain + "_preparation") != None:
        fail("none chain %s declares a preparation target" % chain)

def _none_plan(variant, layout_signature, destination):
    """The plan file of a `none` fixture: one jar packed from a module-filter operation over the raw input. An analysis
    test runs no action, so the packer never reads it. The content states what the chain would pack."""
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

_NONE_PLAN = _none_plan("", "0" * 64, "lib/test.jar")

# The folded form of the same plan: the variant is the chain's platform, and the two leaves that differ per platform
# are slots. The graph of each chain resolves them from the call's `platform_values`.
_FOLDED_PLAN = _none_plan("{platform}", "{platform:layoutSignature}", "{platform:destination}")

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
    empty one or the product itself."""
    error = plan_product_error(main_module, "idea", "server")
    if error == None or "is not its product" not in error:
        fail("plan_product_error accepted the plan product 'server' for 'idea': %s" % error)
    for accepted in ["", "idea"]:
        error = plan_product_error(main_module, "idea", accepted)
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

    # The macro derives the preparation list and the component map from one list of owner targets, so the reused
    # content module jar reaches the payload beside the remainder.
    payload = fragment.payload.to_list()
    asserts.equals(env, 2, len(payload))
    asserts.true(env, remainder.directory in payload)
    asserts.true(env, content.jar.short_path in [file.short_path for file in payload])
    asserts.equals(env, [content.jar.short_path], [file.short_path for file in remainder.independent_artifacts.to_list()])
    return analysistest.end(env)

_reused_component_test = analysistest.make(
    _reused_component_test_impl,
    attrs = {
        "remainder": attr.label(mandatory = True, providers = [DevPluginRemainderInfo]),
        "content_jar": attr.label(mandatory = True, providers = [ContentModuleJarInfo]),
    },
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

    preparations = {}
    preparation_tests = []
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
        preparation = name + "_" + suffix + "_preparation"
        preparations[suffix] = preparation
        dev_plugin_preparation(
            name = preparation,
            graph = ":" + graph,
            artifact_catalogue = ":" + catalogue,
            descriptor = ":" + descriptor,
            plugin_directory = "plugins/test",
            product_info = ":" + product_info,
            callback_input_ids = ["raw"],
            tags = ["manual"],
        )
        preparation_test = preparation + "_test"
        preparation_tests.append(preparation_test)
        _preparation_test(
            name = preparation_test,
            target_under_test = ":" + preparation,
            graph = ":" + graph,
            catalogue = ":" + catalogue,
            descriptor = ":" + descriptor,
            product_info = ":" + product_info,
            raw = ":" + raw,
            separate_classpath_descriptor = separate_classpath_descriptor,
        )

    preparation = preparations["normal"]

    remainder = name + "_remainder"
    dev_plugin_remainder(name = remainder, preparation = ":" + preparation, tags = ["manual"])
    remainder_test = remainder + "_test"
    _remainder_test(
        name = remainder_test,
        target_under_test = ":" + remainder,
        preparation = ":" + preparation,
    )

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

    # The explicit form declares the five targets of a chain under the generator's names.
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
        preparation = "plain",
        target_platform = "linux_x64",
        resource_inputs = {
            ":" + normal_descriptor: "descriptor",
            ":" + raw: "raw",
        },
        callback_input_ids = ["raw"],
        independent_artifacts = [":" + content_jar],
        tags = ["manual"],
    )
    _check_chain_shape(complex, "plain")
    complex_graph_test = complex + "_graph_test"
    _graph_resolution_test(
        name = complex_graph_test,
        target_under_test = ":" + complex + "_graph",
        projection = ":" + projection,
        platform = "linux_x64",
        execution_version = 1,
        consumer = ":" + complex + "_preparation",
    )
    complex_preparation_test = complex + "_preparation_test"
    _preparation_test(
        name = complex_preparation_test,
        target_under_test = ":" + complex + "_preparation",
        graph = ":" + complex + "_graph",
        catalogue = ":" + complex + "_catalogue",
        descriptor = ":" + normal_descriptor,
        product_info = ":" + product_info,
        raw = ":" + raw,
    )
    complex_remainder_test = complex + "_remainder_test"
    _remainder_test(
        name = complex_remainder_test,
        target_under_test = ":" + complex + "_remainder",
        preparation = ":" + complex + "_preparation",
    )
    complex_component_test = complex + "_component_test"
    _reused_component_test(
        name = complex_component_test,
        target_under_test = ":" + complex + "_component",
        remainder = ":" + complex + "_remainder",
        content_jar = ":" + content_jar,
    )
    complex_preparation_kind_test = complex + "_preparation_kind_test"
    _preparation_kind_test(
        name = complex_preparation_kind_test,
        target_under_test = ":" + complex + "_preparation",
        preparation = "plain",
        catalogue = ":" + complex + "_catalogue",
        descriptor = ":" + normal_descriptor,
        raw = ":" + raw,
        product_info = ":" + product_info,
    )

    # A `callback` chain keeps the five targets and runs the callback preparer over its callback inputs.
    callback_chain = name + "_callback"
    dev_dist_complex_plugin_variant(
        name = callback_chain,
        projection = ":" + projection,
        execution_version = 1,
        descriptor = ":" + normal_descriptor,
        plugin_directory = "plugins/test",
        component_name = "plugin-test",
        platform_prefix = "idea",
        product_info = ":" + product_info,
        preparation = "callback",
        target_platform = "linux_x64",
        resource_inputs = {
            ":" + normal_descriptor: "descriptor",
            ":" + raw: "raw",
        },
        callback_input_ids = ["raw"],
        tags = ["manual"],
    )
    _check_chain_shape(callback_chain, "callback")
    callback_preparation_kind_test = callback_chain + "_preparation_kind_test"
    _preparation_kind_test(
        name = callback_preparation_kind_test,
        target_under_test = ":" + callback_chain + "_preparation",
        preparation = "callback",
        catalogue = ":" + callback_chain + "_catalogue",
        descriptor = ":" + normal_descriptor,
        raw = ":" + raw,
        product_info = ":" + product_info,
    )

    # A `none` chain declares four targets. The Go packer executes the module-filter operation of the plan file in the
    # remainder action. The same action writes the asset table and the classpath record the component reads.
    none_chain = name + "_none"
    none_projection = none_chain + "_projection"
    _file(name = none_projection, content = _NONE_PLAN)
    dev_dist_complex_plugin_variant(
        name = none_chain,
        projection = ":" + none_projection,
        execution_version = 1,
        descriptor = ":" + normal_descriptor,
        plugin_directory = "plugins/test",
        component_name = "plugin-test",
        platform_prefix = "idea",
        product_info = ":" + product_info,
        preparation = "none",
        target_platform = "linux_x64",
        resource_inputs = {
            ":" + normal_descriptor: "descriptor",
            ":" + raw: "raw",
        },
        tags = ["manual"],
    )
    _check_chain_shape(none_chain, "none")
    none_graph_test = none_chain + "_graph_test"
    _graph_resolution_test(
        name = none_graph_test,
        target_under_test = ":" + none_chain + "_graph",
        projection = ":" + none_projection,
        platform = "linux_x64",
        execution_version = 1,
        consumer = ":" + none_chain + "_remainder",
    )
    none_remainder_test = none_chain + "_remainder_test"
    _remainder_from_plan_test(
        name = none_remainder_test,
        target_under_test = ":" + none_chain + "_remainder",
        graph = ":" + none_chain + "_graph",
        catalogue = ":" + none_chain + "_catalogue",
        descriptor = ":" + normal_descriptor,
        raw = ":" + raw,
        product_info = ":" + product_info,
    )
    none_component_test = none_chain + "_component_test"
    _component_test(
        name = none_component_test,
        target_under_test = ":" + none_chain + "_component",
        remainder = ":" + none_chain + "_remainder",
    )

    # The derived form: one call, one chain per platform, the plan label and the descriptor entry derived, and the
    # platform token substituted in a label.
    derived_module = "test.%s.derived" % name
    derived_platforms = ["linux_x64", "darwin_aarch64"]
    for platform in derived_platforms:
        _file(name = name + "_derived_raw_" + platform)
        _file(name = "plugin-plans/" + derived_module + "." + platform + ".json")
    dev_dist_complex_plugin(
        main_module = derived_module,
        product = "idea",
        product_info = ":" + product_info,
        descriptor = ":" + normal_descriptor,
        execution_version = 1,
        preparation = "plain",
        platforms = derived_platforms,
        resource_inputs = {":" + name + "_derived_raw_{platform}": "raw"},
        callback_input_ids = ["raw"],
        tags = ["manual"],
    )
    derived_tests = []
    for platform in derived_platforms:
        stem = "idea_" + platform + "_" + derived_module
        derived_test = stem + "_test"
        _derived_preparation_test(
            name = derived_test,
            target_under_test = ":" + stem + "_preparation",
            catalogue = ":" + stem + "_catalogue",
            raw = ":" + name + "_derived_raw_" + platform,
            product_info = ":" + product_info,
            descriptor_id = "descriptor:" + derived_module,
            plugin_directory = "plugins/" + derived_module.replace(".", "-"),
        )
        derived_tests.append(derived_test)

    # The folded form: one call, one plan file with tokens, one chain per platform, and the values of each platform on
    # the call. The graph of each chain resolves the plan file, and the from-plan remainder reads the resolved file.
    folded_module = "test.%s.folded" % name
    folded_platforms = ["linux_x64", "darwin_aarch64"]
    folded_values = {
        "linux_x64": {"destination": "lib/linux-x64/test.jar", "layoutSignature": "1" * 64},
        "darwin_aarch64": {"destination": "lib/darwin-aarch64/test.jar", "layoutSignature": "2" * 64},
    }
    _check_platform_values_refusals(folded_module)
    folded_projection = "plugin-plans/" + folded_module + ".json"
    _file(name = folded_projection, content = _FOLDED_PLAN)
    for platform in folded_platforms:
        _file(name = name + "_folded_raw_" + platform)
    dev_dist_complex_plugin(
        main_module = folded_module,
        product = "idea",
        product_info = ":" + product_info,
        descriptor = ":" + normal_descriptor,
        execution_version = 1,
        preparation = "none",
        platforms = folded_platforms,
        platform_values = folded_values,
        resource_inputs = {":" + name + "_folded_raw_{platform}": "raw"},
        tags = ["manual"],
    )
    folded_tests = []
    for platform in folded_platforms:
        stem = "idea_" + platform + "_" + folded_module
        _check_chain_shape(stem, "none")
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
        _file(name = "plugin-plans/" + product_module + ".idea." + platform + ".json")
    dev_dist_complex_plugin(
        main_module = product_module,
        product = "idea",
        plan_product = "idea",
        product_info = ":" + product_info,
        descriptor = ":" + normal_descriptor,
        execution_version = 1,
        preparation = "plain",
        platforms = product_platforms,
        resource_inputs = {":" + name + "_product_raw_{platform}": "raw"},
        callback_input_ids = ["raw"],
        tags = ["manual"],
    )
    product_tests = []
    for platform in product_platforms:
        stem = "idea_" + platform + "_" + product_module
        product_graph_test = stem + "_graph_test"
        _graph_resolution_test(
            name = product_graph_test,
            target_under_test = ":" + stem + "_graph",
            projection = ":plugin-plans/" + product_module + ".idea." + platform + ".json",
            platform = platform,
            execution_version = 1,
            consumer = ":" + stem + "_preparation",
        )
        product_tests.append(product_graph_test)
        product_preparation_test = stem + "_preparation_test"
        _derived_preparation_test(
            name = product_preparation_test,
            target_under_test = ":" + stem + "_preparation",
            catalogue = ":" + stem + "_catalogue",
            raw = ":" + name + "_product_raw_" + platform,
            product_info = ":" + product_info,
            descriptor_id = "descriptor:" + product_module,
            plugin_directory = "plugins/" + product_module.replace(".", "-"),
        )
        product_tests.append(product_preparation_test)
    product_folded_module = "test.%s.product_folded" % name
    product_folded_projection = "plugin-plans/" + product_folded_module + ".idea.json"
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
        preparation = "none",
        platforms = product_platforms,
        platform_values = folded_values,
        resource_inputs = {":" + name + "_product_folded_raw_{platform}": "raw"},
        tags = ["manual"],
    )
    for platform in product_platforms:
        stem = "idea_" + platform + "_" + product_folded_module
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
            unsafe_source_tree_graph_test,
        ] + preparation_tests + [
            remainder_test,
            component_test,
            neutral_component_test,
            complex_graph_test,
            complex_preparation_test,
            complex_remainder_test,
            complex_component_test,
            complex_preparation_kind_test,
            callback_preparation_kind_test,
            none_graph_test,
            none_remainder_test,
            none_component_test,
        ] + derived_tests + folded_tests + product_tests + refused_graph_tests,
    )
