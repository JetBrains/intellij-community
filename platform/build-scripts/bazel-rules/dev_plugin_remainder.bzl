"""The execution chain of one complex plugin: the graph, the catalogue, the packed remainder and the component."""

load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@rules_java//java:defs.bzl", "JavaInfo")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load("//build:host_platforms.bzl", "HOST_PLATFORMS", "platform_parts")
load(":content_module_jar.bzl", "ContentModuleJarInfo", "library_entries", "module_output_jar")
load(":dev_dist_content.bzl", "DevDistContentInfo")
load(":dev_dist_plugin_descriptor.bzl", "DevDistPluginDescriptorInfo", "DevDistProductInfo", "dev_dist_neutral_product_transition")
load(":dev_plugin.bzl", "DevDistRuntimeLayoutInfo", "dev_dist_plugin_directory")
load(":dev_plugin_source_tree.bzl", "source_tree_entries", "source_tree_prefix")
load(":intellij_dev_dist.bzl", "DevDistPlacementInfo", "IntellijDevFragmentInfo")

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
        "root": "The path of the plugin directory that the packer writes, `<name>.plugin`. No artifact is at this path.",
        "outputs": """Dictionary from a destination below the plugin directory to its declared `File`. A file asset is a
        file, and a tree asset is a directory that holds every asset below it.""",
        "executables": "The destinations of `outputs` that the packer writes with the executable bit.",
        "metadata": "File containing the remainder file inventory and hashes.",
        "assets": "File containing the complete ordered asset table.",
        "classpath": "File containing the plugin classpath record.",
        "independent_artifacts": "Independent artifact references, excluded from the packing action.",
        "refused_modules": "The content modules the product's mode refuses. No jar of them is placed, see `--refused-module`.",
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
        "execution_version": attr.int(mandatory = True, values = [1, 2], doc = "Derived execution_version from the private owner record. The packer checks it against the plan file."),
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
    if type(version) != "int" or version not in [1, 2]:
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
    doc = "Resolved compiled inputs, library archives and library containers in the neutral product configuration.",
    fields = {
        "inputs": "The resolved artifact_inputs dictionary.",
        "archives": "The resolved archive_inputs dictionary: one library jar per artifact ID.",
        "libraries": "The resolved libraries dictionary: one library container target per library ID.",
    },
)

def _compiled_artifact_inputs_impl(ctx):
    return [
        DefaultInfo(files = depset()),
        _CompiledArtifactInputsInfo(inputs = ctx.attr.artifact_inputs, archives = ctx.attr.archive_inputs, libraries = ctx.attr.libraries),
    ]

_compiled_artifact_inputs = rule(
    implementation = _compiled_artifact_inputs_impl,
    # A rule transition: a Starlark transition on a label-keyed dict attribute crashes Bazel 9.1.
    cfg = _module_transition,
    attrs = {
        "artifact_inputs": attr.label_keyed_string_dict(allow_files = True),
        "archive_inputs": attr.label_keyed_string_dict(
            allow_files = [".jar"],
            doc = """The library jars the plan names jar by jar, mapped to stable artifact IDs. They are in the neutral
product configuration, like every library container. A simple plugin of the same product names such a jar by the same
label, and the content boundary accepts one file per label.""",
        ),
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
    jars in the same order. A member ID is `<library ID>/<jar basename>`: the plan file never states it, and the
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

def _is_one_jar(target, file):
    """Whether a catalogue input is one jar file and not a descriptor."""
    return DevDistPluginDescriptorInfo not in target and file.extension == "jar" and not file.is_directory

def _dev_plugin_artifact_catalogue_impl(ctx):
    artifacts = {}
    compiled = ctx.attr.compiled_inputs[_CompiledArtifactInputsInfo]
    for attribute, inputs in [("artifact_inputs", compiled.inputs), ("archive_inputs", compiled.archives), ("resource_inputs", ctx.attr.resource_inputs)]:
        for target, identifier in inputs.items():
            identifier = _catalogue_id(identifier)
            if identifier in artifacts:
                fail("duplicate catalogue ID: %s" % identifier)
            file = _artifact_file(target)
            if attribute != "artifact_inputs" and _KtJvmInfo in target:
                fail("compiled modules must use artifact_inputs, not %s: %s" % (attribute, target.label))
            if attribute == "archive_inputs" and not _is_one_jar(target, file):
                fail("archive_inputs must name one jar file: %s" % target.label)
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
    content_library_jars = []
    for target, identifier in compiled.libraries.items():
        identifier = _catalogue_id(identifier)
        libraries[identifier] = _library_members(ctx, identifier, target, artifacts, libraries, members_by_path)
        content_library_jars.append(library_entries(ctx, [target], attr_name = "libraries")[0])
    catalogue = _write_catalogue(ctx, artifacts, libraries)

    # The raw content: the compiled module jars, the library containers and the archives, all in the neutral product
    # configuration. A compiled input is a module target or the `<target>.jar` output file of one; both give the module's
    # own jar, whose owner is the module rule. An archive is one jar of a library the plan names jar by jar, because the
    # library shares another jar with a second library. It is keyed by its own label, the way `dev_plugin.bzl` carries a
    # jar file token. A descriptor or any other resource is no content. So a reference that lays the plugin out without
    # packing it declares neither.
    content_module_jars = []
    for target in compiled.inputs.keys():
        if DevDistPluginDescriptorInfo in target:
            continue
        jar = _artifact_file(target)
        if jar.extension == "jar" and not jar.is_directory:
            content_module_jars.append(jar)
    for target in compiled.archives.keys():
        content_library_jars.append(struct(label = str(target.label), jars = (_artifact_file(target),)))
    return [
        DefaultInfo(files = depset([catalogue])),
        DevPluginArtifactCatalogueInfo(
            catalogue = catalogue,
            artifacts = artifacts,
            libraries = libraries,
        ),
        DevDistContentInfo(module_jars = depset(content_module_jars), library_jars = depset(content_library_jars)),
    ]

_dev_plugin_artifact_catalogue = rule(
    implementation = _dev_plugin_artifact_catalogue_impl,
    attrs = {
        "compiled_inputs": attr.label(mandatory = True, providers = [_CompiledArtifactInputsInfo]),
        "resource_inputs": attr.label_keyed_string_dict(
            allow_files = True,
            doc = "Resources and descriptor providers in the catalogue's product configuration. A library jar is an archive input.",
        ),
        "source_tree_graph": attr.label(
            cfg = _module_transition,
            providers = [DevPluginGraphInfo],
            doc = "Optional graph target whose normalized source directories become resource artifacts.",
        ),
        "_allowlist_function_transition": attr.label(default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist")),
    },
)

def dev_plugin_artifact_catalogue(name, artifact_inputs = {}, archive_inputs = {}, libraries = {}, tags = [], **kwargs):
    """Binds neutral compiled inputs, library archives and library containers, and product-scoped resources, to the catalogue.

    Args:
        name: The target name.
        artifact_inputs: Compiled targets mapped to stable artifact IDs.
        archive_inputs: The library jars the plan names jar by jar, mapped to stable artifact IDs. Neutral like the
            compiled inputs.
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
        archive_inputs = archive_inputs,
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

def _remainder_providers(ctx, graph, execution_version, packed, metadata, assets, classpath, independent_artifacts, content, refused_modules):
    """The providers of a packed remainder. The component reads this one contract."""
    files = packed.outputs.values()
    return [
        DefaultInfo(
            files = depset(files),
            runfiles = ctx.runfiles(files = files, transitive_files = independent_artifacts),
        ),
        content,
        _new_remainder_info(
            graph = graph,
            execution_version = execution_version,
            root = packed.root,
            outputs = packed.outputs,
            executables = packed.executables,
            metadata = metadata,
            assets = assets,
            classpath = classpath,
            independent_artifacts = independent_artifacts,
            refused_modules = refused_modules,
        ),
        OutputGroupInfo(
            file_metadata = depset([metadata]),
            dev_dist_plugin_remainder = depset(files),
            dev_dist_plugin_assets = depset([assets]),
            dev_dist_plugin_classpath = depset([classpath]),
            dev_dist_plugin_independent_artifacts = independent_artifacts,
        ),
    ]

def _parents(path):
    """Every directory that holds `path`, the deepest first. The plugin root is the empty text."""
    parts = path.split("/")
    return ["/".join(parts[:index]) for index in range(len(parts) - 1, 0, -1)] + [""]

def _check_asset_destination(label, destination, kind):
    if destination != destination.strip() or "\\" in destination or any([part in ["", ".", ".."] for part in destination.split("/")]):
        if not (kind == "tree" and destination == ""):
            fail("%s: the %s asset '%s' is no safe destination below the plugin directory" % (label, kind, destination))

def _declare_remainder_outputs(ctx, metadata, refused_modules):
    """Declares one output per asset that the packer writes, below `<name>.plugin`.

    The packer omits a file asset whose every module the product mode refuses, so the rule omits it too. Bazel declares
    no output below a declared directory. So a tree holds every asset below it, and only the outermost tree is declared.

    Returns:
        `struct(root, outputs, executables)`, see `DevPluginRemainderInfo`.
    """
    label = ctx.label
    trees = {}
    for destination in ctx.attr.remainder_trees:
        _check_asset_destination(label, destination, "tree")
        if destination in trees:
            fail("%s: the tree asset '%s' is named twice" % (label, destination))
        trees[destination] = True
    for destination in ctx.attr.remainder_files:
        _check_asset_destination(label, destination, "file")
        if destination in trees:
            fail("%s: '%s' is a file asset and a tree asset" % (label, destination))
    for destination in ctx.attr.remainder_executables:
        if destination not in ctx.attr.remainder_files:
            fail("%s: the executable '%s' is no file asset" % (label, destination))

    def covered(destination):
        return any([parent in trees for parent in _parents(destination)])

    refused = {module: True for module in refused_modules}
    root_name = label.name + ".plugin"
    outputs = {}
    for destination in sorted(trees.keys()):
        if destination == "" or not covered(destination):
            outputs[destination] = ctx.actions.declare_directory(root_name + ("/" + destination if destination else ""))
    for destination, modules in ctx.attr.remainder_files.items():
        if modules and all([module in refused for module in modules]):
            continue
        if not covered(destination):
            outputs[destination] = ctx.actions.declare_file(root_name + "/" + destination)
    return struct(
        root = metadata.dirname + "/" + root_name,
        outputs = outputs,
        executables = [destination for destination in ctx.attr.remainder_executables if destination in outputs],
    )

_PACKER = attr.label(
    default = "//platform/build-scripts/bazel-rules:plugin_remainder_packer",
    executable = True,
    cfg = "exec",
)

def _dev_plugin_remainder_from_plan_impl(ctx):
    graph = ctx.attr.graph[DevPluginGraphInfo]
    projection = graph.projection
    execution_version = graph.execution_version
    artifact_catalogue = ctx.attr.artifact_catalogue
    descriptor_target = ctx.attr.descriptor
    reused_jars = _reused_jars(ctx)
    binding = _catalogue_binding(ctx, artifact_catalogue, reused_jars)
    classpath_descriptor = _descriptor_classpath_file(descriptor_target)

    # The content modules the product's mode refuses. The packer omits an asset whose every module is refused, the
    # runtime layout part omits the same assets, and a reused jar of a refused module is not placed.
    refused_modules = descriptor_target[DevDistPluginDescriptorInfo].mode_refused_content_modules
    independent_artifacts = depset([jar for module, jar in reused_jars.items() if module not in refused_modules])

    # The raw content of the whole plugin: the catalogue's compiled modules and libraries, plus what each reused content
    # module jar merged.
    catalogue_content = artifact_catalogue[DevDistContentInfo]
    reused_member_jars = []
    reused_library_jars = []
    for target in ctx.attr.independent_artifacts:
        info = target[ContentModuleJarInfo]
        reused_member_jars.extend(info.member_jars)
        reused_library_jars.extend(info.library_jars)
    content = DevDistContentInfo(
        module_jars = depset(reused_member_jars, transitive = [catalogue_content.module_jars]),
        library_jars = depset(reused_library_jars, transitive = [catalogue_content.library_jars]),
    )
    inputs = depset([projection, binding.catalogue, classpath_descriptor], transitive = [depset(binding.artifacts.values())])
    for source in inputs.to_list():
        for artifact in independent_artifacts.to_list():
            if _overlapping_artifacts(source, artifact):
                fail("remainder input %s overlaps independent artifact %s" % (source.path, artifact.path))

    metadata = ctx.actions.declare_file(ctx.label.name + ".file-metadata.json")
    packed = _declare_remainder_outputs(ctx, metadata, refused_modules)
    assets = ctx.actions.declare_file(ctx.label.name + ".assets.json")
    classpath = ctx.actions.declare_file(ctx.label.name + ".plugin-classpath.txt")
    arguments = ctx.actions.args()
    arguments.add(projection, format = "--projection=%s")
    arguments.add(binding.catalogue, format = "--input-catalogue=%s")
    arguments.add(classpath_descriptor, format = "--classpath-descriptor=%s")
    arguments.add(ctx.attr.plugin_directory, format = "--plugin-directory=%s")
    arguments.add(execution_version, format = "--execution-version=%s")
    arguments.add(packed.root, format = "--output-dir=%s")
    arguments.add(metadata, format = "--inventory=%s")
    arguments.add(assets, format = "--assets=%s")
    arguments.add(classpath, format = "--classpath=%s")
    arguments.add_all(reused_jars.keys(), format_each = "--independent-module=%s")
    arguments.add_all(refused_modules, format_each = "--refused-module=%s")
    ctx.actions.run(
        mnemonic = "PackDevPluginRemainder",
        executable = ctx.executable._packer,
        inputs = inputs,
        outputs = packed.outputs.values() + [metadata, assets, classpath],
        arguments = [arguments],
        progress_message = "Packing plugin remainder %{label} from its plan file",
    )

    # The layout part of the plugin, derived from the same plan file. Built only when a runtime module repository asks for
    # it, and apart from the packing action, so that it reads no packed byte.
    runtime_layout = ctx.actions.declare_file(ctx.label.name + ".runtime-layout.json")
    layout_arguments = ctx.actions.args()
    layout_arguments.add("plan-part")
    layout_arguments.add(projection, format = "--plan=%s")
    layout_arguments.add(binding.catalogue, format = "--catalogue=%s")
    layout_inputs = [projection, binding.catalogue]

    # A reused jar is not in the catalogue, so its own target states the libraries it merges.
    if reused_library_jars:
        independent_libraries = ctx.actions.declare_file(ctx.label.name + ".independent-libraries.json")
        ctx.actions.write(independent_libraries, json.encode({
            "version": 1,
            "libraries": [{"library": entry.label, "jars": [jar.path for jar in entry.jars]} for entry in reused_library_jars],
        }) + "\n")
        layout_arguments.add(independent_libraries, format = "--independent-libraries=%s")
        layout_inputs.append(independent_libraries)
    layout_arguments.add(descriptor_target[DevDistPluginDescriptorInfo].plugin_main_module, format = "--descriptor-module=%s")
    layout_arguments.add_all(refused_modules, format_each = "--refused-module=%s")
    layout_arguments.add(ctx.attr.plugin_directory, format = "--plugin-directory=%s")
    layout_arguments.add(classpath_descriptor.path, format = "--descriptor=%s")
    layout_arguments.add(runtime_layout, format = "--output=%s")
    ctx.actions.run(
        mnemonic = "DevDistRuntimeLayoutPart",
        executable = ctx.executable._runtime_layout,
        inputs = layout_inputs,
        outputs = [runtime_layout],
        arguments = [layout_arguments],
        progress_message = "Deriving the runtime layout part of %{label} from its plan file",
    )
    return _remainder_providers(ctx, ctx.attr.graph, execution_version, packed, metadata, assets, classpath, independent_artifacts, content, refused_modules) + [
        DevDistRuntimeLayoutInfo(
            part = runtime_layout,
            descriptor = classpath_descriptor,
            descriptor_module = descriptor_target[DevDistPluginDescriptorInfo].plugin_main_module,
        ),
    ]

dev_plugin_remainder_from_plan = rule(
    implementation = _dev_plugin_remainder_from_plan_impl,
    doc = """Packs the remainder of one complex plugin in one action.

The packer reads the plan file and the input catalogue in its `--projection` mode. It derives the recipe, runs the
operations, packs the remainder, and writes the asset table and the plugin classpath record. No recipe and no
prepared directory exist as a file.

The generator states the destination of each asset that the packer writes. So the rule declares one output per file
asset and one directory per outermost tree asset, and a home links each output at analysis.""",
    attrs = {
        "graph": attr.label(mandatory = True, providers = [DevPluginGraphInfo]),
        "descriptor": attr.label(
            mandatory = True,
            providers = [DevDistPluginDescriptorInfo],
            doc = """The produced descriptor target. The action reads its classpath descriptor. The primary descriptor reaches the action
as a catalogue artifact. The product reaches it through the configuration the consumer of the component sets.""",
        ),
        "plugin_directory": attr.string(mandatory = True),
        "remainder_files": attr.string_list_dict(
            doc = """Each file asset that the packer writes, keyed by its destination below the plugin directory. The value
is the module list of the asset. The rule omits a file whose every module the product mode refuses.""",
        ),
        "remainder_trees": attr.string_list(
            doc = "The destination of each tree asset that the packer writes. The empty text is the plugin directory itself.",
        ),
        "remainder_executables": attr.string_list(
            doc = "The destinations of `remainder_files` that the packer writes with the executable bit.",
        ),
        "artifact_catalogue": attr.label(
            mandatory = True,
            providers = [DevPluginArtifactCatalogueInfo],
            doc = "The catalogue. The action reads every artifact of it.",
        ),
        "independent_artifacts": attr.label_list(
            providers = [ContentModuleJarInfo],
            cfg = _module_transition,
            doc = """The `content_module_jar` targets whose jar the plugin reuses. The action receives each module name as
`--independent-module`, and the packer marks the asset with the plain module jar recipe of that module as independent.
Reset to the neutral product configuration like every compiled input: without the reset, the product configuration
reaches each jar's module and compiles it a second time. No input of the action may overlap a reused jar.""",
        ),
        "_packer": _PACKER,
        "_runtime_layout": attr.label(
            default = "//platform/build-scripts/bazel-rules:runtime_layout",
            executable = True,
            cfg = "exec",
        ),
        "_allowlist_function_transition": attr.label(default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist")),
    },
)

# The key of a native tree in `independent_destinations`. The plan file names the input of the tree the same way.
_NATIVE_TREE_PREFIX = "native-tree:"

def _place_reused(reused, destination, file):
    if destination in reused:
        fail("'%s' is the destination of both %s and %s" % (destination, reused[destination].path, file.path))
    reused[destination] = file

def _component_placement(files, trees, reused, executables):
    """The placement of a complex plugin: the remainder outputs, then each reused jar and native tree.

    A runfiles tree cannot write below a linked directory. So a reused jar or native tree below a remainder tree has no
    placement, and the function returns None. A launch that links a home then fails on the missing placement.
    """
    for destination in reused:
        if destination in files or destination in trees:
            return None
        for parent in _parents(destination):
            if parent in trees:
                return None
    placed_files = dict(files)
    placed_trees = dict(trees)
    for destination, file in reused.items():
        (placed_trees if file.is_directory else placed_files)[destination] = file
    return DevDistPlacementInfo(files = placed_files, trees = placed_trees, executables = executables)

def _dev_plugin_component_impl(ctx):
    product = ctx.attr._product_info[DevDistProductInfo]
    if not product.platform_prefix:
        fail("dev_plugin_component requires a product configuration: %s states no platform prefix, so no product asked for %s" % (
            ctx.attr._product_info.label,
            ctx.label,
        ))
    remainder = ctx.attr.remainder[DevPluginRemainderInfo]
    execution_version = _execution_version(remainder, "DevPluginRemainderInfo")
    plugin_directory = ctx.attr.plugin_directory

    def destination(relative):
        return plugin_directory + ("/" + relative if relative else "")

    # The placement is the remainder outputs, the reused jars and their native trees, each at its own destination.
    placed_files = {}
    placed_trees = {}
    for relative, file in remainder.outputs.items():
        (placed_trees if file.is_directory else placed_files)[destination(relative)] = file
    reused = {}
    independent = []
    payload = list(remainder.outputs.values())
    inputs = [remainder.metadata, remainder.assets, remainder.classpath]
    for key in ctx.attr.independent_destinations:
        _check_asset_destination(ctx.label, ctx.attr.independent_destinations[key], "independent")
    identifiers = {}
    declared = {file: True for file in remainder.independent_artifacts.to_list()}
    bound = {}
    refused = {module: True for module in remainder.refused_modules}
    for target in ctx.attr.independent_artifacts:
        info = target[ContentModuleJarInfo]

        # The run time excludes a refused module, so its reused jar is not placed.
        if info.module_name in refused:
            continue
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
        _place_reused(reused, destination(ctx.attr.independent_destinations.get(identifier, "lib/modules/%s.jar" % identifier)), jar)
        entry = {
            "artifact": identifier,
            "source": jar.path,
            "metadata": metadata.path,
            "relativePath": jar.basename,
        }

        # A natives jar has a tree per platform. The component takes the tree of its own platform, and the plan
        # places it in the `lib/` directory of the plugin.
        if info.native_trees:
            if not ctx.attr.target_platform:
                fail("independent artifact %s packs native files, so the component needs a target platform" % identifier)
            native = info.native_trees.get(ctx.attr.target_platform)
            if native == None:
                fail("independent artifact %s has no native tree for '%s'" % (identifier, ctx.attr.target_platform))
            payload.append(native.tree)
            inputs.append(native.metadata)
            entry["nativeTree"] = {"source": native.tree.path, "metadata": native.metadata.path}
            native_destination = ctx.attr.independent_destinations.get(_NATIVE_TREE_PREFIX + identifier, "lib/" + info.native_lib_dir)
            _place_reused(reused, destination(native_destination), native.tree)
        independent.append(entry)
    for source in declared:
        if source not in bound:
            fail("unbound independent artifact: %s" % source.path)
    for source in inputs:
        if source.is_directory:
            fail("component metadata must be a regular File: %s" % source.path)
        for artifact in payload:
            if _overlapping_artifacts(source, artifact):
                fail("component metadata %s overlaps payload artifact %s" % (source.path, artifact.path))

    collection = {
        "version": execution_version,
        "pluginDirectory": ctx.attr.plugin_directory,
        "remainder": {"directory": remainder.root, "metadata": remainder.metadata.path},
        "assets": remainder.assets.path,
        "classpath": remainder.classpath.path,
        "independent": independent,
    }
    if remainder.refused_modules:
        collection["refusedModules"] = remainder.refused_modules
    spec = ctx.actions.declare_file(ctx.label.name + ".collection.json")
    ctx.actions.write(spec, json.encode(collection) + "\n")
    manifest = ctx.actions.declare_file(ctx.label.name + ".component.json")
    classpath = ctx.actions.declare_file(ctx.label.name + ".plugin-classpath-part")
    outputs = [manifest, classpath]
    arguments = ctx.actions.args()
    arguments.add(spec, format = "--plugin-component=%s")
    arguments.add(manifest, format = "--component-manifest=%s")
    arguments.add(classpath, format = "--plugin-classpath-part=%s")
    arguments.add(ctx.attr.component_name, format = "--kind=%s")
    arguments.add(product.platform_prefix, format = "--platform-prefix=%s")
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

    placement = _component_placement(placed_files, placed_trees, reused, [destination(relative) for relative in remainder.executables])
    payload = depset(payload)
    return [
        DefaultInfo(files = depset([manifest, classpath]), runfiles = ctx.runfiles(transitive_files = payload)),
        # The raw content of the plugin, forwarded from the remainder: `dev_dist_plugin_content` unions it per product.
        ctx.attr.remainder[DevDistContentInfo],
        ctx.attr.remainder[DevDistRuntimeLayoutInfo],
        IntellijDevFragmentInfo(
            name = ctx.attr.component_name,
            payload = payload,
            manifest = manifest,
            plugin_classpath_part = classpath,
            plugin_classpath_prefix = None,
        ),
        OutputGroupInfo(
            dev_dist_plugin_outputs = depset([manifest, classpath], transitive = [payload]),
            dev_dist_plugin_assets = depset([remainder.assets]),
            dev_dist_plugin_classpath = depset([classpath]),
            file_metadata = depset([remainder.metadata] + [file for file in inputs if file not in [remainder.metadata, remainder.assets, remainder.classpath]]),
            trace_spans = depset(spans),
        ),
    ] + ([placement] if placement else [])

dev_plugin_component = rule(
    implementation = _dev_plugin_component_impl,
    attrs = {
        "remainder": attr.label(mandatory = True, providers = [DevPluginRemainderInfo, DevDistRuntimeLayoutInfo]),
        "independent_artifacts": attr.label_list(
            providers = [ContentModuleJarInfo],
            cfg = _module_transition,
            doc = """The same targets and the same reset as `dev_plugin_remainder_from_plan.independent_artifacts`, so both rules
see one `File` per reused jar. The module name of each target is its artifact ID, the key the asset rows of the
remainder use.""",
        ),
        "plugin_directory": attr.string(mandatory = True),
        "independent_destinations": attr.string_dict(
            doc = """The destination below the plugin directory of a reused jar that is not `lib/modules/<module>.jar`,
keyed by the module. A native tree states its destination under `native-tree:<module>`. Without it, the tree is at
`lib/<native_lib_dir>`.""",
        ),
        "component_name": attr.string(mandatory = True),
        "target_platform": attr.string(doc = "A `HOST_PLATFORMS` entry, or empty for a component that serves every platform."),
        "_product_info": attr.label(
            doc = "The product, read through the flag the consumer's transition sets. The default states no product, and the rule fails on it.",
            default = Label("//build:dev_dist_product_info"),
            providers = [DevDistProductInfo],
        ),
        "_trace_spans": attr.label(default = "//platform/build-scripts/bazel-rules:trace_spans", providers = [BuildSettingInfo]),
        "_collector": attr.label(default = "//platform/build-scripts/bazel-rules:dev_dist_collector", executable = True, cfg = "exec"),
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

# The repositories whose jar-file labels the generator records for a library jar, as `isRecordedJarFileLabel` of
# `devDistPluginBuildPlan.kt` lists them. The empty name is the checkout, which a label without `//` names too.
_LIBRARY_JAR_REPOSITORIES = ["", "@community", "@lib", "@ultimate_lib"]

def _is_library_jar_label(label):
    """Whether a label names a jar file of the checkout or of a `lib` repository. The generator records a library jar
    under such a label."""
    repository = label.split("//")[0] if "//" in label else ""
    return label.endswith(".jar") and repository in _LIBRARY_JAR_REPOSITORIES

def _split_library_jars(resource_inputs):
    """The resource inputs in two dicts: the library jars the plan names jar by jar, then every other resource.

    A downloaded file is a label of a download repository, and the descriptor is a rule target. The catalogue checks
    that each archive is one jar file.
    """
    archives = {label: identifier for label, identifier in resource_inputs.items() if _is_library_jar_label(label)}
    resources = {label: identifier for label, identifier in resource_inputs.items() if label not in archives}
    return archives, resources

def _resolve_slot(main_module, value, values):
    """Resolves a destination that is one `{platform:<name>}` slot from the chain's `platform_values`."""
    if not value.startswith(_TOKEN_PREFIX):
        if _TOKEN_PREFIX in value:
            fail("%s states '%s', and a slot must be the whole destination" % (main_module, value))
        return value
    slot = value[len(_TOKEN_PREFIX) + 1:-1] if value.startswith(_TOKEN_PREFIX + ":") and value.endswith("}") else None
    if slot == None or slot not in values:
        fail("%s states '%s', and the chain has no value for it" % (main_module, value))
    return values[slot]

def platform_values_error(main_module, platforms, platform_values, platform_plans = False):
    """Returns why `platform_values` and `platform_plans` do not fit `platforms`, or None when they do.

    `dev_dist_complex_plugin` fails with the message at load time. `platform_plans` needs `platforms` and excludes
    `platform_values`, because a plan file per platform has no slot. A non-empty `platform_values` needs `platforms`.
    Its keys are exactly `platforms`. Every platform states the same slot names. Every value stands as a whole JSON
    string leaf.

    Args:
        main_module: The plugin's main module, named in the message.
        platforms: The `platforms` argument of the call.
        platform_values: The `platform_values` argument of the call.
        platform_plans: The `platform_plans` argument of the call.

    Returns:
        The message, or None.
    """
    if platform_plans and not platforms:
        return "%s states platform_plans without platforms" % main_module
    if platform_plans and platform_values:
        return "%s states platform_values and platform_plans, but a plan file per platform has no slot" % main_module
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

def dev_dist_complex_plugin(
        main_module,
        descriptor,
        execution_version,
        platforms = None,
        platform_values = {},
        platform_plans = False,
        plan_class = "",
        chain_class = "",
        plan_package = "",
        directory_name = "",
        artifact_inputs = {},
        resource_inputs = {},
        libraries = {},
        source_tree_targets = {},
        source_tree_prefixes = {},
        optional_source_trees = [],
        independent_artifacts = [],
        remainder_files = {},
        remainder_trees = [],
        remainder_executables = [],
        independent_destinations = {},
        tags = [],
        visibility = ["//visibility:public"]):
    """Declares the execution chains of one complex plugin: one chain per platform it is bundled on, or one for all.

    One call serves every product whose rendered call is the same. The call states no product. The consumer of the
    component sets the product through `dev_dist_product_info_transition`, and the descriptor and the collector read it
    there.

    The generator states the facts that vary per plugin. The macro derives everything that follows from them: the chain
    stem `<main module>[.<chain class>][_<platform>]`, the component name, the plan file label
    `<plan_package>:<main module>[.<plan class>][.<platform>].dev-plan.json`, the plugin directory, and the
    descriptor's catalogue entry `descriptor:<main module>`. A `{platform}` token in a label or an ID is replaced by the
    chain's platform, so a plugin whose platform layouts differ only in that token is one call. A plan file holds the
    same token and `{platform:<name>}` slots as whole string leaves. The graph of each chain resolves them from
    `platform_values`. The `.<platform>` part of the plan file label follows from `platform_plans` alone. Each chain is
    one `dev_dist_complex_plugin_variant`.

    Args:
        main_module: The plugin's main module. It is the component name and the plan file stem.
        descriptor: The produced descriptor target. It may hold the platform token.
        execution_version: The execution version derived from the projection assets.
        platforms: The `HOST_PLATFORMS` entries the plugin is bundled on, one chain each, or `None` for one chain
            that serves every platform.
        platform_values: The value of each plan file slot per platform, `{platform: {slot name: value}}`. A non-empty
            dict needs `platforms`, names every one of them, and states the same slot names on each. A folded plan
            file without a slot states nothing.
        platform_plans: True when the call names one plan file per platform, because the fold of the plugin's records
            was refused. A folded or neutral plugin states nothing. With True, each chain reads
            `<main module>[.<plan class>].<platform>.dev-plan.json`. Otherwise every chain reads
            `<main module>[.<plan class>].dev-plan.json`. True needs `platforms` and excludes `platform_values`.
        plan_class: The name of a plan text that differs from the baseline text, the first product that states it.
            Empty for the baseline text.
        chain_class: The name of a call that differs from the baseline call, the first product that states it. Empty
            for the baseline call. It keeps the chain stems of two calls of one plugin apart.
        plan_package: The package that holds the plan file, as an absolute label such as
            `@community//plugins/kotlin/plugin`. Empty for a plan file in the package of the call.
        directory_name: The layout's explicit directory name, or empty for the one derived from the main module.
        artifact_inputs: Compiled targets mapped to stable artifact IDs.
        resource_inputs: Resource targets mapped to stable artifact IDs, without the descriptor. A label that names a
            jar file of the checkout or of a `lib` repository is a library jar the plan names jar by jar. The chain
            reads it in the neutral product configuration.
        libraries: Library container targets mapped to stable library IDs. The catalogue expands each to its member
            jars.
        source_tree_targets: Declared source targets keyed by the artifact ID of each normalized directory.
        source_tree_prefixes: Repository-relative source prefix keyed by the source tree artifact ID.
        optional_source_trees: Source tree IDs that may have no files and then materialize as empty directories.
        independent_artifacts: The `content_module_jar` targets whose jar the plugin reuses.
        remainder_files: Each file asset that the packer writes, keyed by its destination, with the modules of the asset.
            A destination may be a `{platform:<name>}` slot.
        remainder_trees: The destination of each tree asset that the packer writes. A destination may be a slot.
        remainder_executables: The destinations of `remainder_files` with the executable mode.
        independent_destinations: The destination of a reused jar outside `lib/modules/<module>.jar`, keyed by the
            module, and the destination of each native tree, keyed by `native-tree:<module>`.
        tags: Tags for every target of every chain. `manual` is added.
        visibility: The visibility of every component. Public by default, because the product's dist is in another
            package. The other targets of a chain keep the package default.
    """
    if platforms == []:
        fail("%s states no platform; state None for a plugin that serves every platform" % main_module)
    for platform in platforms or []:
        if platform not in HOST_PLATFORMS:
            fail("%s names platform '%s', which is not one of %s" % (main_module, platform, HOST_PLATFORMS))
    error = platform_values_error(main_module, platforms, platform_values, platform_plans)
    if error:
        fail(error)
    descriptor_id = "descriptor:" + main_module
    plan_stem = plan_package + ":" + main_module + ("." + plan_class if plan_class else "")
    chain_stem = main_module + ("." + chain_class if chain_class else "")
    for platform in platforms or [None]:
        projection = plan_stem + ("." + platform if platform and platform_plans else "") + ".dev-plan.json"
        chain_descriptor = _for_platform(descriptor, platform)
        chain_resources = _dict_for_platform(resource_inputs, platform, "resource_inputs")
        if chain_descriptor in chain_resources:
            fail("%s states its descriptor %s in resource_inputs; the macro adds that entry" % (main_module, chain_descriptor))
        chain_resources[chain_descriptor] = descriptor_id
        values = platform_values[platform] if platform_values else {}
        dev_dist_complex_plugin_variant(
            name = chain_stem + ("_" + platform if platform else ""),
            projection = projection,
            execution_version = execution_version,
            descriptor = chain_descriptor,
            plugin_directory = dev_dist_plugin_directory(main_module, directory_name),
            component_name = main_module,
            target_platform = platform,
            platform_values = values,
            source_tree_targets = _dict_for_platform(source_tree_targets, platform, "source_tree_targets"),
            source_tree_prefixes = _dict_for_platform(source_tree_prefixes, platform, "source_tree_prefixes"),
            optional_source_trees = optional_source_trees,
            artifact_inputs = _dict_for_platform(artifact_inputs, platform, "artifact_inputs"),
            resource_inputs = chain_resources,
            libraries = _dict_for_platform(libraries, platform, "libraries"),
            independent_artifacts = [_for_platform(label, platform) for label in independent_artifacts],
            remainder_files = {_resolve_slot(main_module, key, values): modules for key, modules in remainder_files.items()},
            remainder_trees = [_resolve_slot(main_module, destination, values) for destination in remainder_trees],
            remainder_executables = [_resolve_slot(main_module, destination, values) for destination in remainder_executables],
            independent_destinations = {key: _resolve_slot(main_module, value, values) for key, value in independent_destinations.items()},
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
        target_platform = None,
        platform_values = {},
        source_tree_targets = {},
        source_tree_prefixes = {},
        optional_source_trees = [],
        artifact_inputs = {},
        resource_inputs = {},
        libraries = {},
        independent_artifacts = [],
        remainder_files = {},
        remainder_trees = [],
        remainder_executables = [],
        independent_destinations = {},
        tags = [],
        visibility = ["//visibility:public"]):
    """Declares the execution chain of one complex plugin variant, every argument stated.

    `dev_dist_complex_plugin` derives these arguments; this form is for a test that pins one of them. A chain is four
    `manual` targets: `<name>_graph`, `<name>_catalogue`, `<name>_remainder` and `<name>_component`. Only the consumer
    of the component states the product, so no target of the chain builds on its own. The `<name>_remainder` is a
    `dev_plugin_remainder_from_plan`. Its action executes the operations from the plan file.
    `DEV_DIST_PLUGIN_COMPONENTS` names the component. The macro merges the declarations only. The actions and their
    cache policies stay separate.

    Args:
        name: The chain stem, `<main module>[.<chain class>][_<platform>]`.
        projection: The plan file label, in this package or in another one. The graph resolves it for
            `target_platform`.
        execution_version: The execution version derived from the projection assets.
        descriptor: The produced descriptor target.
        plugin_directory: The plugin directory in the distribution, `plugins/<directory name>`.
        component_name: The component kind, the plugin's main module.
        target_platform: A `HOST_PLATFORMS` entry, or `None` for a component that serves every platform.
        platform_values: The value of each `{platform:<name>}` slot of the plan file for `target_platform`, keyed by
            name. Empty for a chain that serves every platform.
        source_tree_targets: Declared source targets keyed by the artifact ID of each normalized directory. One
            target may serve two IDs with different prefixes.
        source_tree_prefixes: Repository-relative source prefix keyed by the source tree artifact ID.
        optional_source_trees: Source tree IDs that may have no files and then materialize as empty directories.
        artifact_inputs: Compiled targets mapped to stable artifact IDs.
        resource_inputs: Resource and descriptor targets mapped to stable artifact IDs. A label that names a jar file
            of the checkout or of a `lib` repository is an archive input of the catalogue.
        libraries: Library container targets mapped to stable library IDs.
        independent_artifacts: The `content_module_jar` targets whose jar the plugin reuses. Both rules read the jar
            and the module name from `ContentModuleJarInfo`; the module name is the artifact ID of the reused jar.
        remainder_files: Each file asset that the packer writes, keyed by its destination, with the modules of the asset.
        remainder_trees: The destination of each tree asset that the packer writes.
        remainder_executables: The destinations of `remainder_files` with the executable mode.
        independent_destinations: The destination of a reused jar outside `lib/modules/<module>.jar`, keyed by the
            module, and the destination of each native tree, keyed by `native-tree:<module>`.
        tags: Tags for every target of the chain. `manual` is added.
        visibility: The visibility of `<name>_component`. The graph, the catalogue and the remainder keep the package
            default.
    """
    graph = name + "_graph"
    catalogue = name + "_catalogue"
    remainder = name + "_remainder"
    tags = tags + ["manual"]
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
    archives, resources = _split_library_jars(resource_inputs)
    dev_plugin_artifact_catalogue(
        name = catalogue,
        artifact_inputs = artifact_inputs,
        archive_inputs = archives,
        resource_inputs = resources,
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
        remainder_files = remainder_files,
        remainder_trees = remainder_trees,
        remainder_executables = remainder_executables,
        independent_artifacts = independent_artifacts,
        tags = tags,
    )
    dev_plugin_component(
        name = name + "_component",
        remainder = ":" + remainder,
        independent_artifacts = independent_artifacts,
        independent_destinations = independent_destinations,
        plugin_directory = plugin_directory,
        component_name = component_name,
        target_platform = target_platform,
        tags = tags,
        visibility = visibility,
    )
