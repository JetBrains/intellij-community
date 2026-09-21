"""The execution chain of one complex plugin: the graph, the catalogue, the packed remainder and the component."""

load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@rules_java//java:defs.bzl", "JavaInfo")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load("//build:dev_launch_dependencies.bzl", "HOST_PLATFORMS", "platform_parts")
load(":content_module_jar.bzl", "ContentModuleJarInfo", "library_entries", "module_output_jar")
load(":dev_dist_plugin_descriptor.bzl", "DevDistPluginDescriptorInfo", "DevDistProductInfo", "dev_dist_neutral_product_transition", "dev_dist_product_info_transition")
load(":dev_plugin.bzl", "dev_dist_plugin_directory")
load(":dev_plugin_source_tree.bzl", "source_tree_entries", "source_tree_prefix")
load(":intellij_dev_dist.bzl", "IntellijDevFragmentInfo")

def _graph_info_init(**_kwargs):
    fail("DevPluginGraphInfo must come from dev_plugin_file_graph")

DevPluginGraphInfo, _new_graph_info = provider(
    doc = "The plan file, its execution version and the normalized source trees. The packer checks them.",
    fields = {
        "projection": "Immutable File: the plan file of the plugin, resolved for the chain's platform.",
        "execution_version": "Version derived from the projection assets.",
        "source_trees": "Dictionary from stable artifact IDs to normalized directory Files made from declared source Files.",
    },
    init = _graph_info_init,
)

def _remainder_info_init(**_kwargs):
    fail("DevPluginRemainderInfo must come from dev_plugin_remainder_from_plan")

DevPluginRemainderInfo, _new_remainder_info = provider(
    doc = """One packed plugin remainder with its inventory, the asset table and the classpath record.

    `dev_plugin_remainder_from_plan` writes all of them in the packing action.""",
    fields = {
        "graph": "The graph target of the chain.",
        "execution_version": "The execution version of the graph.",
        "directory": "Directory File containing only the remainder outputs.",
        "metadata": "File containing the remainder file inventory and hashes.",
        "assets": "File containing the complete ordered asset table.",
        "classpath": "File containing the plugin classpath record.",
        "independent_artifacts": "Independent artifact references, excluded from the packing action.",
    },
    init = _remainder_info_init,
)

DevPluginArtifactCatalogueInfo = provider(
    doc = "Execution roots bound to generated stable IDs without reading their files.",
    fields = {
        "catalogue": "File containing the versioned artifact catalogue.",
        "artifacts": "Dictionary from stable artifact IDs to declared Files. A library member is `<library ID>/<jar basename>`.",
        "libraries": "Dictionary from stable library IDs to the ordered member artifact IDs the rule expanded from the container.",
    },
)

def _declare_source_trees(ctx):
    by_id = {}
    for identifier, target in ctx.attr.source_tree_targets.items():
        by_id[_catalogue_id(identifier)] = target
    if sorted(by_id.keys()) != sorted(ctx.attr.source_tree_prefixes.keys()):
        fail("source tree IDs and prefix IDs differ: trees=%s, prefixes=%s" % (
            sorted(by_id.keys()),
            sorted(ctx.attr.source_tree_prefixes.keys()),
        ))
    optional = {identifier: True for identifier in ctx.attr.optional_source_trees}
    if any([identifier not in by_id for identifier in optional]):
        fail("optional source tree IDs are not declared source trees: %s" % sorted(optional.keys()))

    result = {}
    for index, identifier in enumerate(sorted(by_id.keys())):
        target = by_id[identifier]
        prefix = source_tree_prefix(ctx.attr.source_tree_prefixes[identifier], identifier)
        entries = source_tree_entries(target[DefaultInfo].files.to_list(), prefix, identifier, target.label, optional = identifier in optional)

        archive = ctx.actions.declare_file(ctx.label.name + ".source-tree-%d.zip" % index)
        directory = ctx.actions.declare_directory(ctx.label.name + ".source-tree-%d" % index)
        args = ctx.actions.args()
        inputs = []
        for entry in sorted(entries.keys()):
            file = entries[entry]
            inputs.append(file)
            args.add("%s=%s" % (entry, file.path))
        args.use_param_file("@%s", use_always = True)
        args.set_param_file_format("multiline")
        if entries:
            ctx.actions.run(
                executable = ctx.executable._zipper,
                arguments = ["c", archive.path, args],
                inputs = inputs,
                outputs = [archive],
                mnemonic = "DevPluginSourceTreeArchive",
                progress_message = "Normalizing source tree %s" % identifier,
            )
        else:
            ctx.actions.write(archive, "PK\005\006" + ("\000" * 18))
        ctx.actions.run(
            executable = ctx.executable._zipper,
            arguments = ["x", archive.path, "-d", directory.path],
            inputs = [archive],
            outputs = [directory],
            mnemonic = "DevPluginSourceTreeExtract",
            progress_message = "Materializing source tree %s" % identifier,
        )
        result[identifier] = directory
    return result

def _properties_value(value):
    return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("=", "\\=").replace(":", "\\:").replace(" ", "\\ ")

def _dev_jupyter_frontend_impl(ctx):
    resources = ctx.actions.declare_directory(ctx.label.name + "/jupyter-web") if ctx.attr.operation == "RESOURCES_AND_LICENSES" else None
    if ctx.attr.skip:
        if resources == None:
            return [DefaultInfo(files = depset())]

        # An empty archive extracted by the zipper, not a shell `mkdir`: a Windows build agent has no bash.
        archive = ctx.actions.declare_file(ctx.label.name + ".omitted.zip")
        ctx.actions.write(archive, "PK\005\006" + ("\000" * 18))
        ctx.actions.run(
            executable = ctx.executable._zipper,
            arguments = ["x", archive.path, "-d", resources.path],
            inputs = [archive],
            outputs = [resources],
            mnemonic = "JupyterFrontendOmitted",
        )
        return [DefaultInfo(files = depset([resources]))]
    temporary = ctx.actions.declare_directory(ctx.label.name + ".temporary")
    metadata = ctx.actions.declare_file(ctx.label.name + ".licenses.json")
    cache_key = ctx.actions.declare_file(ctx.label.name + ".cache-key")
    parameters = dict(ctx.attr.parameters)
    parameters.update({
        "allowLocal": str(ctx.attr.allow_local).lower(),
        "operation": ctx.attr.operation,
        "remoteConfiguration": ctx.file.remote_configuration.path,
        "licenseMetadataFile": metadata.path,
        "cacheKeyFile": cache_key.path,
        "temporaryDirectory": temporary.path,
        "frontendDirectory": temporary.path + ".unused-frontend",
    })
    if resources:
        parameters["targetDirectory"] = resources.dirname
    inputs = [ctx.file.remote_configuration]
    materialized_inputs = None
    if ctx.file.local_configuration:
        parameters["localConfiguration"] = ctx.file.local_configuration.path
        inputs.append(ctx.file.local_configuration)
    if ctx.file.archive:
        parameters["archiveFile"] = ctx.file.archive.path
        inputs.append(ctx.file.archive)
    else:
        materialized_inputs = ctx.actions.declare_directory(ctx.label.name + ".inputs")
        parameters["materializedInputsDirectory"] = materialized_inputs.path
        if ctx.attr.operation == "LICENSES_ONLY":
            for file in ctx.attr.source_tree_targets["frontend"][DefaultInfo].files.to_list():
                if file.short_path.endswith("/resources/jupyter-web/licenses.json"):
                    parameters["localLicenseFile"] = file.path
                    inputs.append(file)
        else:
            trees = _declare_source_trees(ctx)
            parameters["frontendDirectory"] = trees["frontend"].path
            inputs.extend(trees.values())
            if "tools" in trees:
                root = trees["tools"].path
                parameters["localInputsRoot"] = root
                if ctx.file.local_parameters:
                    parameters["localToolsFile"] = ctx.file.local_parameters.path
                    inputs.append(ctx.file.local_parameters)
                for key, relative in ctx.attr.local_paths.items():
                    parameters["path." + key] = relative
            if ctx.files.native_tools:
                parameters["nativeInputsRoot"] = ctx.files.native_tools[0].dirname
                inputs.extend(ctx.files.native_tools)
    request = ctx.actions.declare_file(ctx.label.name + ".properties")
    ctx.actions.write(request, "\n".join([key + "=" + _properties_value(parameters[key]) for key in sorted(parameters)]) + "\n")
    outputs = [metadata, cache_key, temporary]
    if materialized_inputs:
        outputs.append(materialized_inputs)
    if ctx.attr.operation == "RESOURCES_AND_LICENSES":
        outputs.append(resources)
    ctx.actions.run(
        executable = ctx.executable.preparer,
        tools = [ctx.attr.preparer[DefaultInfo].files_to_run],
        arguments = [request.path],
        inputs = depset(inputs + [request]),
        outputs = outputs,
        mnemonic = "JupyterFrontendPreparation",
        progress_message = "Preparing Jupyter frontend %{label}",
        execution_requirements = {"block-network": "1", "no-remote-exec": "1"} if ctx.files.native_tools else {"block-network": "1"},
    )
    return [
        DefaultInfo(files = depset([resources] if ctx.attr.operation == "RESOURCES_AND_LICENSES" else [metadata])),
        OutputGroupInfo(licenses = depset([metadata]), cache_key = depset([cache_key])),
    ]

dev_jupyter_frontend = rule(
    implementation = _dev_jupyter_frontend_impl,
    attrs = {
        "preparer": attr.label(mandatory = True, executable = True, cfg = "exec"),
        "remote_configuration": attr.label(allow_single_file = True),
        "local_configuration": attr.label(allow_single_file = True),
        "archive": attr.label(allow_single_file = True),
        "allow_local": attr.bool(default = True),
        "skip": attr.bool(),
        "operation": attr.string(default = "RESOURCES_AND_LICENSES", values = ["RESOURCES_AND_LICENSES", "LICENSES_ONLY"]),
        "parameters": attr.string_dict(),
        "local_parameters": attr.label(allow_single_file = True),
        "native_tools": attr.label_list(allow_files = True),
        "local_paths": attr.string_dict(),
        "source_tree_targets": attr.string_keyed_label_dict(allow_files = True),
        "source_tree_prefixes": attr.string_dict(),
        "optional_source_trees": attr.string_list(),
        "_zipper": attr.label(default = "@bazel_tools//tools/zip:zipper", executable = True, cfg = "exec"),
    },
    doc = "Runs typed Jupyter preparation with resolved inputs. Skipping contributes an empty resource tree and no licenses.",
)

def _dev_debugger_egg_impl(ctx):
    if sorted(ctx.attr.source_tree_targets.keys()) != ["metadata", "pydev"]:
        fail("debugger egg source tree IDs must be exactly metadata and pydev")
    file_name = ctx.attr.file_name
    if file_name in ["", ".", ".."] or any([char in file_name for char in ["/", "\\", ":", "\000"]]):
        fail("debugger egg file_name must be a single safe file name: %r" % file_name)
    source_trees = _declare_source_trees(ctx)
    egg = ctx.actions.declare_file(ctx.label.name + "/" + file_name)
    temporary = ctx.actions.declare_directory(ctx.label.name + ".temporary")
    ctx.actions.run(
        executable = ctx.executable.preparer,
        tools = [ctx.attr.preparer[DefaultInfo].files_to_run],
        arguments = [
            source_trees["pydev"].path,
            source_trees["metadata"].path,
            ctx.attr.build_number,
            egg.path,
            temporary.path,
        ],
        inputs = source_trees.values(),
        outputs = [egg, temporary],
        mnemonic = "DebuggerEggPreparation",
        progress_message = "Preparing debugger egg %{label}",
    )
    return [DefaultInfo(files = depset([egg]))]

dev_debugger_egg = rule(
    implementation = _dev_debugger_egg_impl,
    attrs = {
        "source_tree_targets": attr.string_keyed_label_dict(
            mandatory = True,
            allow_files = True,
            doc = "Declared source targets keyed by pydev and metadata.",
        ),
        "source_tree_prefixes": attr.string_dict(
            mandatory = True,
            doc = "Repository-relative source prefixes keyed by pydev and metadata.",
        ),
        "optional_source_trees": attr.string_list(),
        "build_number": attr.string(mandatory = True),
        "file_name": attr.string(default = "pydevd-pycharm.egg"),
        "preparer": attr.label(mandatory = True, executable = True, cfg = "exec"),
        "_zipper": attr.label(
            default = "@bazel_tools//tools/zip:zipper",
            executable = True,
            cfg = "exec",
        ),
    },
    doc = "Prepares a debugger egg from two declared source trees and a build number.",
)

# A plan file names the chain's platform as a whole quoted JSON string leaf. `"{platform}"` is the chain's
# `HOST_PLATFORMS` id. `"{platform:<name>}"` is a slot, and the call states its value in `platform_values`. The same
# `{platform}` token stands in a generated label or ID, where the macro substitutes it.
_PLATFORM_TOKEN = "{platform}"
_PLATFORM_SLOT_TOKEN = "{platform:%s}"
_TOKEN_PREFIX = "{platform"

def _quoted(text):
    return '"%s"' % text

def _platform_value_error(slot, value):
    """Returns why a slot value cannot stand as a whole JSON string leaf, or None when it can."""
    if json.encode(value) != _quoted(value):
        return "the value of %s needs JSON escaping: %r" % (_PLATFORM_SLOT_TOKEN % slot, value)
    if _TOKEN_PREFIX in value:
        return "the value of %s holds a token: %r" % (_PLATFORM_SLOT_TOKEN % slot, value)
    return None

def _resolve_projection(ctx):
    """Resolves the plan file for the chain's platform in one TemplateExpand action.

    A chain without a platform serves every platform, states no slot value, and hands the source file on.
    """
    platform = ctx.attr.platform
    values = ctx.attr.platform_values
    if not platform:
        if values:
            fail("%s states platform_values %s, but serves every platform" % (ctx.label, sorted(values.keys())))
        return ctx.file.projection
    substitutions = {_quoted(_PLATFORM_TOKEN): _quoted(platform)}
    for slot in sorted(values.keys()):
        error = _platform_value_error(slot, values[slot])
        if error:
            fail("%s: %s" % (ctx.label, error))
        substitutions[_quoted(_PLATFORM_SLOT_TOKEN % slot)] = _quoted(values[slot])
    resolved = ctx.actions.declare_file(ctx.label.name + ".plan.json")
    ctx.actions.expand_template(template = ctx.file.projection, output = resolved, substitutions = substitutions)
    return resolved

def _dev_plugin_file_graph_impl(ctx):
    source_trees = _declare_source_trees(ctx)
    projection = _resolve_projection(ctx)
    return [
        DefaultInfo(files = depset([projection] + source_trees.values())),
        _new_graph_info(
            projection = projection,
            execution_version = ctx.attr.execution_version,
            source_trees = source_trees,
        ),
    ]

dev_plugin_file_graph = rule(
    implementation = _dev_plugin_file_graph_impl,
    attrs = {
        "projection": attr.label(mandatory = True, allow_single_file = True, doc = "The plan file. It may hold `\"{platform}\"` and `\"{platform:<name>}\"` as whole string leaves."),
        "platform": attr.string(
            default = "",
            values = HOST_PLATFORMS + [""],
            doc = "The `HOST_PLATFORMS` entry the chain serves, or empty for a chain that serves every platform. A set platform resolves the plan file.",
        ),
        "platform_values": attr.string_dict(
            doc = "The value of each `{platform:<name>}` slot of the plan file for this chain's platform, keyed by name. Empty for a chain that serves every platform.",
        ),
        "execution_version": attr.int(mandatory = True, values = [1, 2, 3], doc = "Derived execution_version from the private owner record. The packer checks it against the plan file."),
        "source_tree_targets": attr.string_keyed_label_dict(
            allow_files = True,
            doc = "Declared source targets keyed by the stable artifact ID of each normalized directory. One target may serve two IDs with different prefixes.",
        ),
        "source_tree_prefixes": attr.string_dict(
            doc = "Repository-relative source prefix keyed by the matching source tree artifact ID.",
        ),
        "optional_source_trees": attr.string_list(
            doc = "Source tree IDs that may have no files and then materialize as empty directories.",
        ),
        "_zipper": attr.label(
            default = "@bazel_tools//tools/zip:zipper",
            executable = True,
            cfg = "exec",
        ),
    },
)

def _execution_version(info, name):
    version = getattr(info, "execution_version", None)
    if type(version) != "int" or version not in [1, 2, 3]:
        fail("%s has an invalid execution version: %r" % (name, version))
    graph = getattr(info, "graph", None)
    if type(graph) != "Target" or DevPluginGraphInfo not in graph:
        fail("%s.graph must provide DevPluginGraphInfo" % name)
    if version != graph[DevPluginGraphInfo].execution_version:
        fail("%s has a stale execution version" % name)
    return version

def _overlapping_artifacts(first, second):
    return (
        first.path == second.path or
        (first.is_directory and second.path.startswith(first.path + "/")) or
        (second.is_directory and first.path.startswith(second.path + "/"))
    )

# The reset of the product flag, shared with `dev_plugin.bzl`.
_module_transition = dev_dist_neutral_product_transition

def _transitioned_target(value, attribute, optional = False):
    if optional and not value:
        return None
    if type(value) != "list" or len(value) != 1:
        fail("%s must resolve to exactly one configured target" % attribute)
    return value[0]

_CompiledArtifactInputsInfo = provider(
    doc = "Resolved compiled inputs and library containers in the neutral product configuration.",
    fields = {
        "inputs": "The resolved artifact_inputs dictionary.",
        "libraries": "The resolved libraries dictionary: one library container target per library ID.",
    },
)

def _compiled_artifact_inputs_impl(ctx):
    return [DefaultInfo(files = depset()), _CompiledArtifactInputsInfo(inputs = ctx.attr.artifact_inputs, libraries = ctx.attr.libraries)]

_compiled_artifact_inputs = rule(
    implementation = _compiled_artifact_inputs_impl,
    cfg = _module_transition,
    attrs = {
        "artifact_inputs": attr.label_keyed_string_dict(allow_files = True),
        "libraries": attr.label_keyed_string_dict(
            providers = [JavaInfo],
            doc = "Library containers mapped to stable library IDs. The catalogue expands each to its member jars.",
        ),
        "_allowlist_function_transition": attr.label(default = "@bazel_tools//tools/allowlists/function_transition_allowlist"),
    },
)

def _descriptor_primary_file(target):
    """Selects the descriptor provider's generated File."""
    descriptor = getattr(target[DevDistPluginDescriptorInfo], "descriptor", None)
    if type(descriptor) != "File" or descriptor.is_directory or descriptor.is_source:
        fail("descriptor provider %s requires a generated regular primary File" % target.label)
    return descriptor

def _descriptor_classpath_file(target):
    """Selects the embedded descriptor used for classpath generation."""
    descriptor = getattr(target[DevDistPluginDescriptorInfo], "classpath_descriptor", None)
    if type(descriptor) != "File" or descriptor.is_directory or descriptor.is_source:
        fail("descriptor provider %s requires a generated regular classpath File" % target.label)
    return descriptor

def _artifact_file(target, compiled_module = True):
    if DevDistPluginDescriptorInfo in target:
        if _KtJvmInfo in target:
            fail("artifact input %s has ambiguous module and descriptor providers" % target.label)
        return _descriptor_primary_file(target)
    if compiled_module and _KtJvmInfo in target:
        jar = module_output_jar(target)
        if jar == None:
            fail("module %s has no own output jar" % target.label)
        return jar
    files = target[DefaultInfo].files.to_list()
    if len(files) != 1:
        fail("artifact input %s must provide exactly one File, got %d" % (target.label, len(files)))
    return files[0]

def _catalogue_id(value):
    if not value or value != value.strip() or any([character in value for character in ["\n", "\r", "\t"]]):
        fail("invalid catalogue ID: %r" % value)
    return value

def _write_catalogue(ctx, artifacts, libraries):
    record = {
        "version": 1,
        "artifacts": [{"id": identifier, "kind": "directory" if file.is_directory else "file", "root": file.path} for identifier, file in artifacts.items()],
    }
    if libraries:
        record["libraries"] = [{"id": identifier, "files": [{"artifact": member} for member in members]} for identifier, members in libraries.items()]
    catalogue = ctx.actions.declare_file(ctx.label.name + ".input-catalogue.json")
    ctx.actions.write(catalogue, json.encode(record) + "\n")
    return catalogue

def _validate_catalogue_root(file, artifacts):
    if (
        type(file) != "File" or "\\" in file.path or
        any([part in ["", ".", ".."] for part in file.path.split("/")])
    ):
        fail("catalogue roots must be Files with paths inside the execution root: %s" % file)
    for existing in artifacts.values():
        if file.path == existing.path:
            fail("duplicate catalogue root: %s" % file.path)
        if _overlapping_artifacts(file, existing):
            fail("overlapping catalogue roots: %s and %s" % (file.path, existing.path))

def _library_members(ctx, identifier, target, artifacts, libraries, members_by_path):
    """Registers the member jars of one library container and returns their artifact IDs in merge order.

    `library_entries()` expands the container the way `content_module_jar` does, so a complex plugin merges the same
    jars in the same order. A member ID is `<library ID>/<jar basename>`: the plan file never states it, and the Go
    packer reads it from the catalogue only. Two libraries can share a jar. The jar is one artifact then, under the ID
    of the library that named it first, and both member lists name that ID.
    """
    if identifier in artifacts or identifier in libraries:
        fail("duplicate catalogue ID: %s" % identifier)
    members = []
    for jar in library_entries(ctx, [target], attr_name = "libraries")[0].jars:
        member = members_by_path.get(jar.path)
        if member == None:
            member = _catalogue_id(identifier + "/" + jar.basename)
            if member in artifacts or member in libraries:
                fail("duplicate catalogue ID: %s" % member)
            if jar.is_directory:
                fail("library %s requires jar files, got directory %s" % (identifier, jar.path))
            _validate_catalogue_root(jar, artifacts)
            artifacts[member] = jar
            members_by_path[jar.path] = member
        members.append(member)
    return members

def _dev_plugin_artifact_catalogue_impl(ctx):
    artifacts = {}
    compiled = ctx.attr.compiled_inputs[_CompiledArtifactInputsInfo]
    for attribute, inputs in [("artifact_inputs", compiled.inputs), ("resource_inputs", ctx.attr.resource_inputs)]:
        for target, identifier in inputs.items():
            identifier = _catalogue_id(identifier)
            if identifier in artifacts:
                fail("duplicate catalogue ID: %s" % identifier)
            file = _artifact_file(target)
            if attribute == "resource_inputs" and _KtJvmInfo in target:
                fail("compiled modules must use artifact_inputs, not resource_inputs: %s" % target.label)
            _validate_catalogue_root(file, artifacts)
            artifacts[identifier] = file
    source_tree_graph = _transitioned_target(ctx.attr.source_tree_graph, "source_tree_graph", optional = True)
    if source_tree_graph != None:
        for identifier, file in source_tree_graph[DevPluginGraphInfo].source_trees.items():
            identifier = _catalogue_id(identifier)
            if identifier in artifacts:
                fail("duplicate catalogue ID: %s" % identifier)
            if type(file) != "File" or not file.is_directory:
                fail("source tree %s must be a directory File" % identifier)
            _validate_catalogue_root(file, artifacts)
            artifacts[identifier] = file
    libraries = {}
    members_by_path = {}
    for target, identifier in compiled.libraries.items():
        identifier = _catalogue_id(identifier)
        libraries[identifier] = _library_members(ctx, identifier, target, artifacts, libraries, members_by_path)
    catalogue = _write_catalogue(ctx, artifacts, libraries)
    return [
        DefaultInfo(files = depset([catalogue])),
        DevPluginArtifactCatalogueInfo(
            catalogue = catalogue,
            artifacts = artifacts,
            libraries = libraries,
        ),
    ]

_dev_plugin_artifact_catalogue = rule(
    implementation = _dev_plugin_artifact_catalogue_impl,
    attrs = {
        "compiled_inputs": attr.label(mandatory = True, providers = [_CompiledArtifactInputsInfo]),
        "resource_inputs": attr.label_keyed_string_dict(
            allow_files = True,
            doc = "Resources and descriptor providers in the catalogue's product configuration.",
        ),
        "source_tree_graph": attr.label(
            cfg = _module_transition,
            providers = [DevPluginGraphInfo],
            doc = "Optional graph target whose normalized source directories become resource artifacts.",
        ),
        "_allowlist_function_transition": attr.label(default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist")),
    },
)

def dev_plugin_artifact_catalogue(name, artifact_inputs = {}, libraries = {}, tags = [], **kwargs):
    """Binds neutral compiled inputs, library containers and product-scoped resources to the catalogue.

    Args:
        name: The target name.
        artifact_inputs: Compiled targets mapped to stable artifact IDs.
        libraries: Library container targets mapped to stable library IDs. The catalogue expands each to its member
            jars, so no label here carries a version.
        tags: Additional tags for the catalogue target.
        **kwargs: Product-scoped catalogue rule attributes.
    """
    compiled_inputs = name + "_compiled_inputs"
    compiled_attrs = {"testonly": kwargs["testonly"]} if "testonly" in kwargs else {}
    _compiled_artifact_inputs(
        name = compiled_inputs,
        artifact_inputs = artifact_inputs,
        libraries = libraries,
        tags = ["manual"],
        visibility = ["//visibility:private"],
        target_compatible_with = kwargs.get("target_compatible_with", []),
        **compiled_attrs
    )
    _dev_plugin_artifact_catalogue(
        name = name,
        compiled_inputs = ":" + compiled_inputs,
        tags = tags + ["manual"],
        **kwargs
    )

def _reused_jars(ctx):
    """The reused jars of the chain, keyed by their module name. A module named twice fails."""
    jars = {}
    for target in ctx.attr.independent_artifacts:
        info = target[ContentModuleJarInfo]
        if type(info.jar) != "File" or info.jar.is_directory:
            fail("independent artifact %s requires a regular jar file" % target.label)
        if info.module_name in jars:
            fail("independent module %s is named twice" % info.module_name)
        jars[info.module_name] = info.jar
    return jars

def _catalogue_binding(ctx, artifact_catalogue, reused_jars):
    """The catalogue provider, after the check that no catalogue artifact overlaps a reused jar."""
    binding = artifact_catalogue[DevPluginArtifactCatalogueInfo]
    for identifier, file in binding.artifacts.items():
        for independent in reused_jars.values():
            if _overlapping_artifacts(file, independent):
                fail("catalogue artifact %s overlaps independent artifact %s" % (identifier, independent.path))
    return binding

def _remainder_providers(ctx, graph, execution_version, directory, metadata, assets, classpath, independent_artifacts):
    """The providers of a packed remainder. The component reads this one contract."""
    return [
        DefaultInfo(
            files = depset([directory]),
            runfiles = ctx.runfiles(files = [directory], transitive_files = independent_artifacts),
        ),
        _new_remainder_info(
            graph = graph,
            execution_version = execution_version,
            directory = directory,
            metadata = metadata,
            assets = assets,
            classpath = classpath,
            independent_artifacts = independent_artifacts,
        ),
        OutputGroupInfo(
            file_metadata = depset([metadata]),
            dev_dist_plugin_remainder = depset([directory]),
            dev_dist_plugin_assets = depset([assets]),
            dev_dist_plugin_classpath = depset([classpath]),
            dev_dist_plugin_independent_artifacts = independent_artifacts,
        ),
    ]

_PACKER = attr.label(
    default = "//build/content-module-packer/plugin-remainder-packer",
    executable = True,
    cfg = "exec",
)

def _dev_plugin_remainder_from_plan_impl(ctx):
    graph = ctx.attr.graph[DevPluginGraphInfo]
    projection = graph.projection
    execution_version = graph.execution_version
    artifact_catalogue = _transitioned_target(ctx.attr.artifact_catalogue, "artifact_catalogue")
    descriptor_target = _transitioned_target(ctx.attr.descriptor, "descriptor")
    reused_jars = _reused_jars(ctx)
    binding = _catalogue_binding(ctx, artifact_catalogue, reused_jars)
    classpath_descriptor = _descriptor_classpath_file(descriptor_target)
    independent_artifacts = depset(reused_jars.values())
    inputs = depset([projection, binding.catalogue, classpath_descriptor], transitive = [depset(binding.artifacts.values())])
    for source in inputs.to_list():
        for artifact in independent_artifacts.to_list():
            if _overlapping_artifacts(source, artifact):
                fail("remainder input %s overlaps independent artifact %s" % (source.path, artifact.path))

    directory = ctx.actions.declare_directory(ctx.label.name + ".plugin")
    metadata = ctx.actions.declare_file(ctx.label.name + ".file-metadata.json")
    assets = ctx.actions.declare_file(ctx.label.name + ".assets.json")
    classpath = ctx.actions.declare_file(ctx.label.name + ".plugin-classpath.txt")
    arguments = ctx.actions.args()
    arguments.add(projection, format = "--projection=%s")
    arguments.add(binding.catalogue, format = "--input-catalogue=%s")
    arguments.add(classpath_descriptor, format = "--classpath-descriptor=%s")
    arguments.add(ctx.attr.plugin_directory, format = "--plugin-directory=%s")
    arguments.add(execution_version, format = "--execution-version=%s")
    arguments.add(directory.path, format = "--output-dir=%s")
    arguments.add(metadata, format = "--inventory=%s")
    arguments.add(assets, format = "--assets=%s")
    arguments.add(classpath, format = "--classpath=%s")
    arguments.add_all(reused_jars.keys(), format_each = "--independent-module=%s")
    ctx.actions.run(
        mnemonic = "PackDevPluginRemainder",
        executable = ctx.executable._packer,
        inputs = inputs,
        outputs = [directory, metadata, assets, classpath],
        arguments = [arguments],
        progress_message = "Packing plugin remainder %{label} from its plan file",
    )
    return _remainder_providers(ctx, ctx.attr.graph, execution_version, directory, metadata, assets, classpath, independent_artifacts)

dev_plugin_remainder_from_plan = rule(
    implementation = _dev_plugin_remainder_from_plan_impl,
    doc = """Packs the remainder of one complex plugin in one Go action.

The packer reads the plan file and the input catalogue in its `--projection` mode. It derives the recipe, runs the
operations, packs the remainder, and writes the asset table and the plugin classpath record. No recipe and no
prepared directory exist as a file.""",
    attrs = {
        "graph": attr.label(mandatory = True, providers = [DevPluginGraphInfo]),
        "descriptor": attr.label(
            mandatory = True,
            cfg = dev_dist_product_info_transition,
            providers = [DevDistPluginDescriptorInfo],
            doc = "The produced descriptor target. The action reads its classpath descriptor. The primary descriptor reaches the action as a catalogue artifact.",
        ),
        "plugin_directory": attr.string(mandatory = True),
        "artifact_catalogue": attr.label(
            mandatory = True,
            cfg = dev_dist_product_info_transition,
            providers = [DevPluginArtifactCatalogueInfo],
            doc = "The catalogue. The action reads every artifact of it.",
        ),
        "product_info": attr.label(mandatory = True, providers = [DevDistProductInfo]),
        "independent_artifacts": attr.label_list(
            providers = [ContentModuleJarInfo],
            cfg = _module_transition,
            doc = """The `content_module_jar` targets whose jar the plugin reuses. The action receives each module name as
`--independent-module`, and the packer marks the asset with the plain module jar recipe of that module as independent.
Reset to the neutral product configuration like every compiled input: without the reset, the product configuration
reaches each jar's module and compiles it a second time. No input of the action may overlap a reused jar.""",
        ),
        "_packer": _PACKER,
        "_allowlist_function_transition": attr.label(default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist")),
    },
)

def _dev_plugin_component_impl(ctx):
    remainder = ctx.attr.remainder[DevPluginRemainderInfo]
    execution_version = _execution_version(remainder, "DevPluginRemainderInfo")
    if not remainder.directory.is_directory:
        fail("plugin remainder must provide a directory artifact")
    independent = []
    payload = [remainder.directory]
    inputs = [remainder.metadata, remainder.assets, remainder.classpath]
    identifiers = {}
    declared = {file: True for file in remainder.independent_artifacts.to_list()}
    bound = {}
    for target in ctx.attr.independent_artifacts:
        info = target[ContentModuleJarInfo]
        identifier = _catalogue_id(info.module_name)
        if identifier in identifiers:
            fail("duplicate independent artifact ID: %s" % identifier)
        identifiers[identifier] = True
        jar = info.jar
        metadata = info.metadata
        if type(jar) != "File" or jar.is_directory or type(metadata) != "File" or metadata.is_directory:
            fail("independent artifacts require a regular jar and metadata file")
        if jar not in declared:
            fail("independent artifact %s is not owned by this plugin" % identifier)
        bound[jar] = True
        bound[metadata] = True
        payload.append(jar)
        inputs.append(metadata)
        independent.append({
            "artifact": identifier,
            "source": jar.path,
            "metadata": metadata.path,
            "relativePath": jar.basename,
        })
    for source in declared:
        if source not in bound:
            fail("unbound independent artifact: %s" % source.path)
    for source in inputs:
        if source.is_directory:
            fail("component metadata must be a regular File: %s" % source.path)
        for artifact in payload:
            if _overlapping_artifacts(source, artifact):
                fail("component metadata %s overlaps payload artifact %s" % (source.path, artifact.path))

    spec = ctx.actions.declare_file(ctx.label.name + ".collection.json")
    ctx.actions.write(spec, json.encode({
        "version": execution_version,
        "pluginDirectory": ctx.attr.plugin_directory,
        "remainder": {"directory": remainder.directory.path, "metadata": remainder.metadata.path},
        "assets": remainder.assets.path,
        "classpath": remainder.classpath.path,
        "independent": independent,
    }) + "\n")
    manifest = ctx.actions.declare_file(ctx.label.name + ".component.json")
    classpath = ctx.actions.declare_file(ctx.label.name + ".plugin-classpath-part")
    outputs = [manifest, classpath]
    arguments = ctx.actions.args()
    arguments.add(spec, format = "--plugin-component=%s")
    arguments.add(manifest, format = "--component-manifest=%s")
    arguments.add(classpath, format = "--plugin-classpath-part=%s")
    arguments.add(ctx.attr.component_name, format = "--kind=%s")
    arguments.add(ctx.attr.platform_prefix, format = "--platform-prefix=%s")
    if ctx.attr.target_platform:
        platform = platform_parts(ctx.attr.target_platform)
        arguments.add("macos" if platform.os == "darwin" else platform.os, format = "--os=%s")
        arguments.add(platform.arch, format = "--arch=%s")
    else:
        # A component with no target platform serves every platform. The manifest then states no os and no arch.
        arguments.add("--platform-neutral")
    spans = []
    if ctx.attr._trace_spans[BuildSettingInfo].value:
        trace = ctx.actions.declare_file(ctx.label.name + ".component.spans.json")
        spans.append(trace)
        outputs.append(trace)
        arguments.add(trace, format = "--trace-file=%s")
    ctx.actions.run(
        mnemonic = "CollectDevPluginComponent",
        executable = ctx.executable._collector,
        inputs = depset([spec] + inputs),
        outputs = outputs,
        arguments = [arguments],
        execution_requirements = {"block-network": "1", "no-remote-cache": "1", "no-remote-exec": "1"},
        progress_message = "Collecting plugin component metadata %{label}",
    )
    payload = depset(payload)
    return [
        DefaultInfo(files = depset([manifest, classpath]), runfiles = ctx.runfiles(transitive_files = payload)),
        IntellijDevFragmentInfo(
            name = ctx.attr.component_name,
            home = None,
            payload = payload,
            manifest = manifest,
            plugin_classpath_part = classpath,
            plugin_classpath_prefix = None,
            inputs_manifest = None,
            unused_inputs = None,
        ),
        OutputGroupInfo(
            dev_dist_plugin_outputs = depset([manifest, classpath], transitive = [payload]),
            dev_dist_plugin_assets = depset([remainder.assets]),
            dev_dist_plugin_classpath = depset([classpath]),
            file_metadata = depset([remainder.metadata] + [file for file in inputs if file not in [remainder.metadata, remainder.assets, remainder.classpath]]),
            trace_spans = depset(spans),
        ),
    ]

dev_plugin_component = rule(
    implementation = _dev_plugin_component_impl,
    attrs = {
        "remainder": attr.label(mandatory = True, providers = [DevPluginRemainderInfo]),
        "independent_artifacts": attr.label_list(
            providers = [ContentModuleJarInfo],
            cfg = _module_transition,
            doc = """The same targets and the same reset as `dev_plugin_remainder_from_plan.independent_artifacts`, so both rules
see one `File` per reused jar. The module name of each target is its artifact ID, the key the asset rows of the
remainder use.""",
        ),
        "plugin_directory": attr.string(mandatory = True),
        "component_name": attr.string(mandatory = True),
        "platform_prefix": attr.string(mandatory = True),
        "target_platform": attr.string(doc = "A `HOST_PLATFORMS` entry, or empty for a component that serves every platform."),
        "_trace_spans": attr.label(default = "//platform/build-scripts/bazel-rules:trace_spans", providers = [BuildSettingInfo]),
        "_collector": attr.label(default = "//build/content-module-packer/dev-dist-collector", executable = True, cfg = "exec"),
        "_allowlist_function_transition": attr.label(default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist")),
    },
)

def _for_platform(value, platform):
    """Substitutes the platform token in one generated string, or refuses it for a chain that serves every platform."""
    if platform == None:
        if _PLATFORM_TOKEN in value:
            fail("'%s' names a platform, but the chain serves every platform" % value)
        return value
    return value.replace(_PLATFORM_TOKEN, platform)

def _dict_for_platform(values, platform, what):
    result = {_for_platform(key, platform): _for_platform(value, platform) for key, value in values.items()}
    if len(result) != len(values):
        fail("two %s entries name the same key on %s: %s" % (what, platform, sorted(values.keys())))
    return result

def platform_values_error(main_module, platforms, platform_values):
    """Returns why `platform_values` does not fit `platforms`, or None when it does.

    `dev_dist_complex_plugin` fails with the message at load time. A non-empty dict needs `platforms`. Its keys are
    exactly `platforms`. Every platform states the same slot names. Every value stands as a whole JSON string leaf.

    Args:
        main_module: The plugin's main module, named in the message.
        platforms: The `platforms` argument of the call.
        platform_values: The `platform_values` argument of the call.

    Returns:
        The message, or None.
    """
    if not platform_values:
        return None
    if not platforms:
        return "%s states platform_values without platforms" % main_module
    if sorted(platform_values.keys()) != sorted(platforms):
        return "%s states platform_values for %s, but its platforms are %s" % (main_module, sorted(platform_values.keys()), sorted(platforms))
    slots = sorted(platform_values[platforms[0]].keys())
    for platform in platforms:
        platform_slots = sorted(platform_values[platform].keys())
        if platform_slots != slots:
            return "%s states the slots %s on %s but %s on %s" % (main_module, slots, platforms[0], platform_slots, platform)
        for slot in slots:
            error = _platform_value_error(slot, platform_values[platform][slot])
            if error:
                return "%s on %s: %s" % (main_module, platform, error)
    return None

def plan_product_error(main_module, product, plan_product):
    """Returns why `plan_product` does not fit `product`, or None when it does.

    `dev_dist_complex_plugin` fails with the message at load time. A non-empty `plan_product` is the product itself, or
    its case-safe plan name: the case-folded product key, an underscore and a suffix, such as `idea_community` for
    `Idea`. The generator assigns a plan name to a product whose key collides with another key on a case-insensitive
    file system. The plan file of a divergent product is named by that name, and a chain never reads the plan file of
    another product.

    Args:
        main_module: The plugin's main module, named in the message.
        product: The `product` argument of the call.
        plan_product: The `plan_product` argument of the call.

    Returns:
        The message, or None.
    """
    if plan_product and plan_product != product and not plan_product.startswith(product.lower() + "_"):
        return "%s names the plan product '%s', which is not its product '%s'" % (main_module, plan_product, product)
    return None

def dev_dist_complex_plugin(
        main_module,
        product,
        product_info,
        descriptor,
        execution_version,
        platforms = None,
        platform_values = {},
        plan_product = "",
        plan_package = "",
        directory_name = "",
        artifact_inputs = {},
        resource_inputs = {},
        libraries = {},
        source_tree_targets = {},
        source_tree_prefixes = {},
        optional_source_trees = [],
        independent_artifacts = [],
        tags = [],
        visibility = ["//visibility:public"]):
    """Declares the execution chains of one complex plugin: one chain per platform it is bundled on, or one for all.

    The generator states the facts that vary per plugin. The macro derives everything that follows from them: the chain
    stem `<product>[_<platform>]_<main module>`, the component name, the plan file label
    `<plan_package>:<main module>[.<plan_product>][.<platform>].dev-plan.json`, the plugin directory, and the
    descriptor's catalogue entry `descriptor:<main module>`, which every product shares. A `{platform}` token in a
    label or an ID is replaced by the chain's platform, so a plugin whose platform layouts differ only in that token is
    one call. A plan file holds the same token and `{platform:<name>}` slots as whole string leaves. The graph of each
    chain resolves them from `platform_values`. Each chain is one `dev_dist_complex_plugin_variant`.

    Args:
        main_module: The plugin's main module. It is the component name and the plan file stem.
        product: The product's platform prefix, the first element of every chain stem.
        plan_product: The product in the plan file name, `<main module>.<plan_product>[.<platform>].dev-plan.json`,
            for a product whose plan text differs from the baseline product's: the product, or its case-safe plan name,
            see `plan_product_error`. Empty for a plan file the product shares with the baseline product.
        plan_package: The package that holds the plan file, as an absolute label such as
            `@community//plugins/kotlin/plugin`. Empty for a plan file in the package of the call.
        product_info: The product info target that configures the descriptor and the catalogue.
        descriptor: The produced descriptor target. It may hold the platform token.
        execution_version: The execution version derived from the projection assets.
        platforms: The `HOST_PLATFORMS` entries the plugin is bundled on, one chain each, or `None` for one chain
            that serves every platform.
        platform_values: The value of each plan file slot per platform, `{platform: {slot name: value}}`. A non-empty
            dict needs `platforms`, names every one of them, states the same slot names on each, and names the plan
            file `<main module>[.<plan_product>].dev-plan.json`. An empty dict with `platforms` names one plan file
            per chain, `<main module>[.<plan_product>].<platform>.dev-plan.json`.
        directory_name: The layout's explicit directory name, or empty for the one derived from the main module.
        artifact_inputs: Compiled targets mapped to stable artifact IDs.
        resource_inputs: Resource targets mapped to stable artifact IDs, without the descriptor.
        libraries: Library container targets mapped to stable library IDs. The catalogue expands each to its member
            jars.
        source_tree_targets: Declared source targets keyed by the artifact ID of each normalized directory.
        source_tree_prefixes: Repository-relative source prefix keyed by the source tree artifact ID.
        optional_source_trees: Source tree IDs that may have no files and then materialize as empty directories.
        independent_artifacts: The `content_module_jar` targets whose jar the plugin reuses.
        tags: Tags for every target of every chain.
        visibility: The visibility of every component. Public by default, because the product's dist is in another
            package. The other targets of a chain keep the package default.
    """
    if platforms == []:
        fail("%s states no platform; state None for a plugin that serves every platform" % main_module)
    for platform in platforms or []:
        if platform not in HOST_PLATFORMS:
            fail("%s names platform '%s', which is not one of %s" % (main_module, platform, HOST_PLATFORMS))
    error = platform_values_error(main_module, platforms, platform_values)
    if error:
        fail(error)
    error = plan_product_error(main_module, product, plan_product)
    if error:
        fail(error)
    descriptor_id = "descriptor:" + main_module
    plan_stem = plan_package + ":" + main_module + ("." + plan_product if plan_product else "")
    for platform in platforms or [None]:
        projection = plan_stem + ("." + platform if platform and not platform_values else "") + ".dev-plan.json"
        chain_descriptor = _for_platform(descriptor, platform)
        chain_resources = _dict_for_platform(resource_inputs, platform, "resource_inputs")
        if chain_descriptor in chain_resources:
            fail("%s states its descriptor %s in resource_inputs; the macro adds that entry" % (main_module, chain_descriptor))
        chain_resources[chain_descriptor] = descriptor_id
        dev_dist_complex_plugin_variant(
            name = "_".join([product] + ([platform] if platform else []) + [main_module]),
            projection = projection,
            execution_version = execution_version,
            descriptor = chain_descriptor,
            plugin_directory = dev_dist_plugin_directory(main_module, directory_name),
            component_name = main_module,
            platform_prefix = product,
            product_info = product_info,
            target_platform = platform,
            platform_values = platform_values[platform] if platform_values else {},
            source_tree_targets = _dict_for_platform(source_tree_targets, platform, "source_tree_targets"),
            source_tree_prefixes = _dict_for_platform(source_tree_prefixes, platform, "source_tree_prefixes"),
            optional_source_trees = optional_source_trees,
            artifact_inputs = _dict_for_platform(artifact_inputs, platform, "artifact_inputs"),
            resource_inputs = chain_resources,
            libraries = _dict_for_platform(libraries, platform, "libraries"),
            independent_artifacts = [_for_platform(label, platform) for label in independent_artifacts],
            tags = tags,
            visibility = visibility,
        )

def dev_dist_complex_plugin_variant(
        name,
        projection,
        execution_version,
        descriptor,
        plugin_directory,
        component_name,
        platform_prefix,
        product_info,
        target_platform = None,
        platform_values = {},
        source_tree_targets = {},
        source_tree_prefixes = {},
        optional_source_trees = [],
        artifact_inputs = {},
        resource_inputs = {},
        libraries = {},
        independent_artifacts = [],
        tags = [],
        visibility = ["//visibility:public"]):
    """Declares the execution chain of one complex plugin variant, every argument stated.

    `dev_dist_complex_plugin` derives these arguments; this form is for a test that pins one of them. A chain is four
    targets: `<name>_graph`, `<name>_catalogue`, `<name>_remainder` and `<name>_component`. The `<name>_remainder` is a
    `dev_plugin_remainder_from_plan`. Its Go action executes the operations from the plan file.
    `DEV_DIST_PLUGIN_COMPONENTS` names the component. The macro merges the declarations only. The actions and their
    cache policies stay separate.

    Args:
        name: The chain stem, `<product>[_<platform>]_<main module>`.
        projection: The plan file label, in this package or in another one. The graph resolves it for
            `target_platform`.
        execution_version: The execution version derived from the projection assets.
        descriptor: The produced descriptor target.
        plugin_directory: The plugin directory in the distribution, `plugins/<directory name>`.
        component_name: The component kind, the plugin's main module.
        platform_prefix: The product's platform prefix.
        product_info: The product info target that configures the descriptor and the catalogue.
        target_platform: A `HOST_PLATFORMS` entry, or `None` for a component that serves every platform.
        platform_values: The value of each `{platform:<name>}` slot of the plan file for `target_platform`, keyed by
            name. Empty for a chain that serves every platform.
        source_tree_targets: Declared source targets keyed by the artifact ID of each normalized directory. One
            target may serve two IDs with different prefixes.
        source_tree_prefixes: Repository-relative source prefix keyed by the source tree artifact ID.
        optional_source_trees: Source tree IDs that may have no files and then materialize as empty directories.
        artifact_inputs: Compiled targets mapped to stable artifact IDs.
        resource_inputs: Resource and descriptor targets mapped to stable artifact IDs.
        libraries: Library container targets mapped to stable library IDs.
        independent_artifacts: The `content_module_jar` targets whose jar the plugin reuses. Both rules read the jar
            and the module name from `ContentModuleJarInfo`; the module name is the artifact ID of the reused jar.
        tags: Tags for every target of the chain.
        visibility: The visibility of `<name>_component`. The graph, the catalogue and the remainder keep the package
            default.
    """
    graph = name + "_graph"
    catalogue = name + "_catalogue"
    remainder = name + "_remainder"
    dev_plugin_file_graph(
        name = graph,
        projection = projection,
        platform = target_platform or "",
        platform_values = platform_values,
        execution_version = execution_version,
        source_tree_targets = source_tree_targets,
        source_tree_prefixes = source_tree_prefixes,
        optional_source_trees = optional_source_trees,
        tags = tags,
    )
    dev_plugin_artifact_catalogue(
        name = catalogue,
        artifact_inputs = artifact_inputs,
        resource_inputs = resource_inputs,
        source_tree_graph = ":" + graph if source_tree_targets else None,
        libraries = libraries,
        tags = tags,
    )
    dev_plugin_remainder_from_plan(
        name = remainder,
        graph = ":" + graph,
        artifact_catalogue = ":" + catalogue,
        descriptor = descriptor,
        plugin_directory = plugin_directory,
        product_info = product_info,
        independent_artifacts = independent_artifacts,
        tags = tags,
    )
    dev_plugin_component(
        name = name + "_component",
        remainder = ":" + remainder,
        independent_artifacts = independent_artifacts,
        plugin_directory = plugin_directory,
        component_name = component_name,
        platform_prefix = platform_prefix,
        target_platform = target_platform,
        tags = tags,
        visibility = visibility,
    )
