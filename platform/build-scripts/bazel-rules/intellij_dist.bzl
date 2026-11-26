"""Composes a dev-mode IDE distribution from components.

A component names the files it places, and the composer copies them into the distribution.

Split assembly deliberately supports only builds with scrambling disabled. Platform co-scrambling and per-plugin
scrambling require both component layouts in one process.
"""

load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@community//build:project_model_manifest.bzl", "write_project_model_manifest")
load("//build:host_platforms.bzl", "platform_parts")
load(":dist_content.bzl", "DistPlatformPayloadInfo")
load(":dist_plugin_descriptor.bzl", "dist_stamp_class_consumer_transition")

IntellijDevFragmentInfo = provider(
    doc = "A component that a distribution composes. Its manifest names each file where it already is.",
    fields = {
        "name": "The component name, which is also the `kind` of its manifest.",
        "manifest": "The component manifest.",
        "payload": "The already-existing files the manifest names, or None; the composer stages them.",
        "plugin_classpath_part": "This component's plugin-classpath records, or None if it built no plugin.",
        "plugin_classpath_prefix": "The plugin-classpath prefix, or None if another component produces it.",
    },
)

DistPlacementInfo = provider(
    doc = """Where a component places its files in a home, known at analysis.

    A launch target names these files in its home spec, where Bazel wrote them. `dev-home` sets the mode of each file
    from the layout, so a source file needs no executable copy. Every destination is in slash form, relative to the
    home. The composer checks the placement against the component manifest, see `placement.rs` of the composer.""",
    fields = {
        "files": "Dictionary from a file destination to its `File`.",
        "trees": "Dictionary from a directory destination to a directory `File` that holds every entry below it at the same relative path.",
        "executables": "The file destinations that need the executable bit.",
    },
)

IntellijDistInfo = provider(
    fields = {
        "fingerprint": "The content fingerprint of the composed IDE distribution.",
        "mac_binaries": "The `<name>.mac-binaries.json` of a full distribution: its Mach-O files below the home with their " +
                        "kind, which the macOS signing reads. The list is empty when the platform is not macOS. None for " +
                        "a local launch.",
        "home": "The output directory: the export `<name>.dist` or the local launch metadata `<name>.metadata`. The home of the IDE is this directory or its `home_prefix` child, see `dist_home_path`.",
        "home_prefix": "The directory below `home` that holds the home of the IDE: `Contents` for a macOS app, or `\"\"` for `home` itself. `home_prefix` of `dist_product_files.bzl` states the rule.",
        "ide_config": "The config file used by PreBuiltDevMain.",
        "runtime_files": "Component artifacts used directly by a local launch.",
        "placement": "The merged `DistPlacementInfo` of a local launch as `struct(files, trees, executables)`. None for a full distribution or a component without a placement.",
        "core_classpath": "The `core-classpath.txt` of a local launch as a file of its own, or None.",
        "plugin_classpath": "The `plugins/plugin-classpath.txt` of a local launch as a file of its own, or None.",
        "sibling_vm_options": "The sibling vmoptions file `<name>.dist.vmoptions` of an export, or None.",
    },
)

def dist_home_path(info):
    """The exec path of the home of the IDE in the `IntellijDistInfo` `info`: `home`, or its `home_prefix` child."""
    if info.home_prefix:
        return info.home.path + "/" + info.home_prefix
    return info.home.path

IntellijProjectModelTreeInfo = provider(
    fields = {
        "tree": "The checkout-shaped project model tree.",
    },
)

# The cache policy of the component collectors. `build/openspec/specs/cache/spec.md` states it.
# The policy does not use `local`. In the shipped `JetBrains/9.1.0-jb` binary, `Spawns.mayBeCached` tests both
# `no-cache` and `local`, and `RemoteExecutionService` gates `--disk_cache` on that test. So `local` also stops the disk
# cache.
# A collector writes one small manifest, and its payload stays in the cache of the action that produced it. The
# collectors use this policy too, because no measurement supports another policy for them yet.
_LOCAL_DISK_CACHE_ONLY = {
    "block-network": "1",
    "no-remote-cache": "1",
    "no-remote-exec": "1",
}

# What the scheduler reserves for one tool action, in CPUs and MiB. Without a `resource_set`, Bazel books an action at
# one CPU and 250 MiB and starts as many as `--jobs` allows.
def _small_tool_resources(_os, _inputs):
    """The materializer of the project model tree. It is a native binary with no JVM heap to reserve."""
    return {"cpu": 1, "memory": 256}

def _composer_resources(_os, _inputs):
    """The composer clones the files of an export on a pool of four threads."""
    return {"cpu": 4, "memory": 256}

# The switch that turns span output on, carried by every rule here that runs a packaging tool.
#
# A private label attribute read through `BuildSettingInfo` rather than a `select()` on a public one, so the value is
# read once in the implementation and the two branches sit next to each other: declare the file and name it on the
# command line, or do neither. The label is written bare and resolves in the repository this `.bzl` belongs to - the
# same thing `content_module_jar.bzl` relies on for its `_packer` default - so an ultimate
# build reaches `@community//platform/build-scripts/bazel-rules:trace_spans` without this file naming a repository.
_TRACE_SPANS_ATTR = {
    "_trace_spans": attr.label(
        default = "//platform/build-scripts/bazel-rules:trace_spans",
        providers = [BuildSettingInfo],
    ),
}

def _declare_side_output(flag, ctx, args, base, suffix, option):
    """Declare one side output of an action and tell its tool where to write it - or do neither.

    Returns the `File`, or None when `flag` is off. A side output is pure: nothing declares it as an input and no
    provider another rule reads carries it, so the only trace of it in an action is the extra declared output and the
    extra argument - and with the flag off there is neither. That is what lets a request for one of these files leave
    every action key, argument list and declared output set exactly as it was.

    **Append the returned file to `outputs`; never prepend it.** `dist trace` joins a span file to its action by the
    action's *primary* output, which is the first entry of the list handed to `ctx.actions.run`. A side output put first
    re-points the profile's `out` at that file, and then no output in the build matches any span file: every one comes
    back as "no action in this profile produced that output" with no other symptom - a report of nothing, from a build
    that measured everything. This has already cost this work one whole measurement. Appending also keeps the primary
    output, the action's identity in the profile and this file's own stem unchanged when a flag flips. Every caller in
    this file appends.

    Args:
        flag: the rule's flag attribute, already selected by the caller.
        ctx: the rule context, for the declaration.
        args: the tool's `Args`, which learns the option only when the flag is on.
        base: the declared file's name without `suffix` - normally the primary output's stem, which is what makes
          `<output stem>.spans.json` the name the span join looks for.
        suffix: what the declared file's name ends with, including the dot.
        option: the tool's option name, written as `option=<path>`.
    """
    if not flag[BuildSettingInfo].value:
        return None
    file = ctx.actions.declare_file(base + suffix)

    # A string, matching every neighbouring `args.add("--option=" + file.path)` in this file, where the same choice was
    # made for the tools' other path arguments. `content_module_jar.bzl` passes the `File` instead, because only that
    # form is rewritten by output path mapping - and it is a worker whose whole argument list is a param file, where
    # path mapping is the point. These actions are local-exec and not path-mapped, so the two forms are equivalent
    # here; the rule is "the `File` where path mapping can apply, the string where the file's neighbours use strings".
    args.add(option + "=" + file.path)
    return file

def _declare_spans(ctx, args, base):
    """This action's span file, or None when `trace_spans` is off.

    A cached action replays the span file its execution wrote, so a hit reports the timings of the build that produced
    it rather than of the build that asked. That is what makes these figures readable only from a cold run.
    """
    return _declare_side_output(ctx.attr._trace_spans, ctx, args, base, ".spans.json", "--trace-file")

def _side_output_group(own, dependencies, group_name):
    """One side output group: this target's own files of that group, plus those of the targets it composes.

    Always present and empty when the group's flag is off, so `--output_groups=+<group_name>` is a valid request either
    way.

    Propagation is only where the graph makes a file otherwise unreachable, and it is the caller that knows where that
    is. A dist carries every group of every component it composes. Nothing else propagates: a side output of an action whose output the
    build already needs is written by that action anyway, since Bazel runs an action for any of its outputs and requires
    all of them.

    The group's name has to be the same on both sides. A mismatch analyses, passes, and produces a silently empty
    group, so the span group goes through `_spans_output_group`.

    Args:
        own: this target's own files, which may hold None for a flag that is off.
        dependencies: the targets to propagate the same group from.
        group_name: the group's name.
    """
    return depset(
        direct = [file for file in own if file != None],
        transitive = [
            getattr(target[OutputGroupInfo], group_name)
            for target in dependencies
            if OutputGroupInfo in target and hasattr(target[OutputGroupInfo], group_name)
        ],
    )

def _spans_output_group(own, dependencies):
    return _side_output_group(own, dependencies, "trace_spans")

def _project_model_tree_impl(ctx):
    tree = ctx.actions.declare_directory(ctx.label.name + ".tree")
    project_files = ctx.files.project_model_files + ctx.files.extra_project_files
    manifest = write_project_model_manifest(ctx, ctx.label.name + ".project.manifest", project_files, ctx.attr.mode)

    args = ctx.actions.args()
    args.add("--project-manifest=" + manifest.path)
    args.add("--output-dir=" + tree.path)
    spans = _declare_spans(ctx, args, ctx.label.name)
    ctx.actions.run(
        inputs = project_files + [manifest],
        outputs = [tree] + ([spans] if spans else []),
        executable = ctx.executable.materializer,
        arguments = [args],
        # Hermetic. It reads its manifest and the execroot-relative sources that manifest names, writes only under its
        # output directory, and consults no environment variable, no home directory and no network, so it may be
        # sandboxed and the disk cache may keep it. The remote cache may not. The output is tens of thousands of small
        # files, and every model edit re-keys it. Its consumer is the runtime module facts action, which runs locally. A
        # remote hit would download the whole tree to save a few seconds of local copying.
        execution_requirements = {"no-remote-cache": "1"},
        resource_set = _small_tool_resources,
        mnemonic = "IntellijProjectModelTree",
        progress_message = "Materializing the project model tree %s" % ctx.label,
    )
    return [
        DefaultInfo(files = depset([tree])),
        # Not in `IntellijProjectModelTreeInfo`. A consumer reads that provider to declare an *input*, and a span file
        # must never become one. A measuring run asks for this group of the tree target.
        OutputGroupInfo(trace_spans = _spans_output_group([spans], [])),
        IntellijProjectModelTreeInfo(tree = tree),
    ]

intellij_project_model_tree = rule(
    doc = """The checkout-shaped JPS project model tree.

    Only the runtime module facts action reads the tree. No other action of a distribution reads it. A consuming repository can declare one tree for many products and share it with every consumer of those
    products. One tree per consumer would repeat about 7 400 file copies for each one.

    The tree carries the union of what its consumers need. A file that only one consumer reads, for example the branding
    of one product, therefore re-keys every consumer. Such files change less often than the model, and every model edit
    already re-keys every consumer.
    """,
    implementation = _project_model_tree_impl,
    attrs = {
        "materializer": attr.label(executable = True, cfg = "exec", mandatory = True),
        "mode": attr.string(default = "ultimate", values = ["community", "ultimate"]),
        "project_model_files": attr.label_list(allow_files = True, mandatory = True),
        "extra_project_files": attr.label_list(allow_files = True),
    } | _TRACE_SPANS_ATTR,
)

def _add_target_platform_args(args, target_platform):
    if target_platform:
        target_parts = platform_parts(target_platform)
        args.add("--os=" + ("macos" if target_parts.os == "darwin" else target_parts.os))
        args.add("--arch=" + target_parts.arch)

_COLLECTOR_ATTRS = {
    "collector": attr.label(executable = True, cfg = "exec", mandatory = True),
    "component_name": attr.string(mandatory = True, doc = "Identifies the component in its manifest and completeness check."),
    "platform_prefix": attr.string(mandatory = True, doc = "The `build/dev-build.json` key of the product. Only the progress message names it."),
    "target_platform": attr.string(default = ""),
} | _TRACE_SPANS_ATTR

def _collect_component(ctx, files, collection_args, inputs, mnemonic, progress_message, placement, plugin_classpath_prefix = None, main_class = ""):
    component_manifest = ctx.actions.declare_file(ctx.label.name + ".component.json")
    args = ctx.actions.args()
    args.add("--component-manifest=" + component_manifest.path)
    args.add("--kind=" + ctx.attr.component_name)
    _add_target_platform_args(args, ctx.attr.target_platform)
    if main_class:
        args.add("--main-class=" + main_class)

    # The manifest is the primary output, so it is also what names the span file: `dist trace` joins a span file to
    # its action by the primary output's stem, and `X.component.json`'s stem is `X.component`.
    spans = _declare_spans(ctx, args, ctx.label.name + ".component")

    ctx.actions.run(
        inputs = depset(inputs),
        outputs = [component_manifest] + ([spans] if spans else []),
        executable = ctx.executable.collector,
        arguments = [args] + collection_args,
        execution_requirements = _LOCAL_DISK_CACHE_ONLY,
        mnemonic = mnemonic,
        progress_message = progress_message,
    )
    return [
        DefaultInfo(files = depset([component_manifest] + files), runfiles = ctx.runfiles(files = files)),
        OutputGroupInfo(trace_spans = _spans_output_group([spans], [])),
        IntellijDevFragmentInfo(
            name = ctx.attr.component_name,
            payload = depset(files),
            manifest = component_manifest,
            plugin_classpath_part = None,
            plugin_classpath_prefix = plugin_classpath_prefix,
        ),
        placement,
    ]

def _packed_sources(record):
    """The files one packed record places: the jar, and the native tree beside it when the jar has one.

    A jar entry keeps the shape it had before a tree could stand beside one, with no `tree` key. A tree entry says
    `tree`, because the collector and the composer place a directory by its members. The tree's inventory is in the
    metadata of the action that wrote it, not in the jar's.
    """
    sources = [struct(file = record.jar, tree = False, metadata = record.metadata)]
    if record.native_tree:
        sources.append(struct(file = record.native_tree, tree = True, metadata = record.native_metadata))
    return sources

def _metadata_catalogue(ctx, records):
    catalogue = ctx.actions.declare_file(ctx.label.name + ".metadata-catalogue.json")
    by_source = {}
    for record in records:
        for source in _packed_sources(record):
            previous = by_source.get(source.file.path)
            if previous != None and previous.metadata != source.metadata:
                fail("%s: conflicting metadata for %s" % (ctx.label, source.file.path))
            by_source[source.file.path] = struct(file = source.file, metadata = source.metadata, tree = source.tree)
    ctx.actions.write(catalogue, json.encode([
        {
            "source": source,
            "metadata": by_source[source].metadata.path,
            # The source's own base name, and not the destination the jar declares. This is the key of the packing
            # action's inventory, which `content-module-packer` writes under the output's base name. For the tree that
            # is `native`.
            "relativePath": by_source[source].file.basename,
        } | ({"tree": True} if by_source[source].tree else {})
        for source in sorted(by_source.keys())
    ]))
    return catalogue

def _jar_destinations(ctx, records):
    """Where each packed jar goes within the plugin's `lib/`, which its own file name states only when it is flat.

    A native tree goes to `lib/<native_lib_dir>/`, as a `tree` entry. A jar that the payload puts on the core classpath
    is a `coreClassPath` entry. Sorted by source, so the file the action reads is the same file for the same set of jars.
    """
    by_source = {}
    for record in records:
        for source in _packed_sources(record):
            relative_path = record.native_lib_dir if source.tree else record.relative_path
            entry = {"source": source.file.path, "relativePath": relative_path} | ({"tree": True} if source.tree else {})
            if not source.tree and record.core_classpath:
                entry["coreClassPath"] = True
            previous = by_source.get(source.file.path)
            if previous != None and previous != entry:
                fail("%s: %s is placed at both %s and %s" % (ctx.label, source.file.path, previous["relativePath"], relative_path))
            by_source[source.file.path] = entry
    return [by_source[source] for source in sorted(by_source.keys())]

def _names_destination(pattern, destination):
    """Whether `pattern` names `destination`: the same path, or with a final `/` the directory that holds it directly."""
    if pattern.endswith("/"):
        return destination.startswith(pattern) and "/" not in destination[len(pattern):]
    return pattern == destination

def _place_unpacked_archives(ctx, files, records, destinations, executables):
    """Places every file of each `unpacked_archives` target at `<directory>/<path in the archive>`.

    The path in the archive is the label name of the file. The BUILD file of a `extracted_download_repo` exports each
    file by its path in the root package, so the name is the path below the archive root. When the archive has an
    executable list, a record also states `executableList` and `archivePath`. The collector then refuses an archive file
    that the executable list names and the record does not mark. An archive without a list gets no such check, as a
    Windows runtime. Returns the executable lists, which are inputs of the collector.
    """
    patterns = ctx.attr.unpacked_archive_executables
    used_patterns = {}
    lists = {}
    for directory, target in ctx.attr.unpacked_archive_executable_lists.items():
        outputs = target.files.to_list()
        if len(outputs) != 1 or outputs[0].is_directory:
            fail("%s: %s must provide exactly one ordinary file" % (ctx.label, target.label))
        lists[directory] = outputs[0]
    archive_directories = {}
    for target, directory in ctx.attr.unpacked_archives.items():
        if not directory or directory.startswith("/") or directory.endswith("/"):
            fail("%s: %s needs a relative directory without a final '/', not '%s'" % (ctx.label, target.label, directory))
        if directory in archive_directories:
            fail("%s: two unpacked_archives go to '%s'" % (ctx.label, directory))
        archive_directories[directory] = True
        executable_list = lists.get(directory)
        for file in target[DefaultInfo].files.to_list():
            if file.is_directory:
                fail("%s: %s provides the directory %s; an unpacked archive provides ordinary files" % (ctx.label, target.label, file.path))
            if not file.is_source:
                fail("%s: %s provides the generated file %s; an unpacked archive provides source files" % (ctx.label, target.label, file.path))
            relative_path = directory + "/" + file.owner.name
            if relative_path in destinations:
                fail("%s: duplicate destination: %s" % (ctx.label, relative_path))
            destinations[relative_path] = file
            matched = [pattern for pattern in patterns if _names_destination(pattern, relative_path)]
            for pattern in matched:
                used_patterns[pattern] = True
            executable = bool(matched)
            if executable:
                executables.append(relative_path)
            files.append(file)
            record = {
                "source": file.path,
                "relativePath": relative_path,
                "executable": executable,
            }
            if executable_list:
                record["executableList"] = executable_list.path
                record["archivePath"] = file.owner.name
            records.append(record)
    for pattern in patterns:
        if pattern not in used_patterns:
            fail("%s: unpacked_archive_executables names '%s', which matches no file of unpacked_archives" % (ctx.label, pattern))
    for directory in lists:
        if directory not in archive_directories:
            fail("%s: unpacked_archive_executable_lists names '%s', which is no directory of unpacked_archives" % (ctx.label, directory))
    return lists.values()

def _packed_jars_component_impl(ctx):
    if ctx.attr.plugin_classpath_prefix and not ctx.attr.platform_payload:
        fail("%s: plugin_classpath_prefix needs platform_payload" % ctx.label)
    if ctx.attr.platform_payload:
        if ctx.attr.files or ctx.attr.executable_files or ctx.attr.unpacked_archives or ctx.attr.unpacked_archive_executable_lists:
            fail("%s: files, executable_files and unpacked_archives cannot be combined with platform_payload" % ctx.label)
        jars = ctx.attr.platform_payload[DistPlatformPayloadInfo].packed_jars.to_list()
        records = ctx.attr.platform_payload[DistPlatformPayloadInfo].packed_metadata.to_list()
        trees = [record.native_tree for record in records if record.native_tree]
        catalogue = _metadata_catalogue(ctx, records)
        jar_list = ctx.actions.declare_file(ctx.label.name + ".jars.json")
        destinations = _jar_destinations(ctx, records)
        ctx.actions.write(jar_list, json.encode(destinations))
        files_by_path = {file.path: file for file in jars + trees}
        placed_files = {}
        placed_trees = {}
        for entry in destinations:
            placed = placed_trees if entry.get("tree") else placed_files
            placed["lib/" + entry["relativePath"]] = files_by_path[entry["source"]]
        args = ctx.actions.args()
        args.add("--metadata-catalogue=" + catalogue.path)
        args.add("--jars-file=" + jar_list.path)
        return _collect_component(
            ctx,
            # The trees with the jars, so they reach the payload the composer places. Neither is an input of the
            # collector, which reads the metadata and nothing else.
            files = jars + trees,
            collection_args = [args],
            inputs = [catalogue, jar_list] + [source.metadata for record in records for source in _packed_sources(record)],
            mnemonic = "IntellijDevPackedJars",
            progress_message = "Naming %d packed %s jars and %d native trees for %%{label}" % (len(jars), ctx.attr.platform_prefix, len(trees)),
            placement = DistPlacementInfo(files = placed_files, trees = placed_trees, executables = []),
            plugin_classpath_prefix = ctx.file.plugin_classpath_prefix,
            main_class = ctx.attr.main_class,
        )

    if not ctx.attr.files and not ctx.attr.executable_files and not ctx.attr.unpacked_archives:
        fail("%s: either platform_payload or nonempty files, executable_files or unpacked_archives is required" % ctx.label)
    if ctx.attr.unpacked_archive_executables and not ctx.attr.unpacked_archives:
        fail("%s: unpacked_archive_executables needs unpacked_archives" % ctx.label)
    files = []
    records = []
    destinations = {}
    executables = []
    for placed, executable in [(ctx.attr.files, False), (ctx.attr.executable_files, True)]:
        for relative_path, target in placed.items():
            outputs = target[DefaultInfo].files.to_list()
            if len(outputs) != 1 or outputs[0].is_directory:
                fail("%s: %s must provide exactly one ordinary file" % (ctx.label, target.label))
            if relative_path in destinations:
                fail("%s: duplicate destination: %s" % (ctx.label, relative_path))
            destinations[relative_path] = outputs[0]
            if executable:
                executables.append(relative_path)
            files.append(outputs[0])
            records.append({
                "source": outputs[0].path,
                "relativePath": relative_path,
                "executable": executable,
            })
    executable_lists = _place_unpacked_archives(ctx, files, records, destinations, executables)
    metadata = ctx.actions.declare_file(ctx.label.name + ".files.json")
    ctx.actions.write(metadata, json.encode(records))
    args = ctx.actions.args()
    args.add("--files-file=" + metadata.path)
    return _collect_component(
        ctx,
        files = files,
        collection_args = [args],
        inputs = [metadata] + executable_lists + files,
        mnemonic = "IntellijDevFiles",
        progress_message = "Naming distribution files for %{label}",
        placement = DistPlacementInfo(files = destinations, trees = {}, executables = executables),
        main_class = ctx.attr.main_class,
    )

intellij_packed_jars_component = rule(
    doc = """Collect packed platform jars or explicitly placed files into a distribution component.

    Set platform_payload to collect its packed jars at lib/<filename>, and the native tree of a content module jar at
    lib/<native_lib_dir>/. The manifest marks the jars that the payload puts on the core classpath. Alternatively, set files and executable_files to map distribution paths to source labels,
    so one source can go to several paths; the composer gives a file of executable_files the executable bit. The modes cannot be combined. The action reads
    only these sources and writes one manifest. The composer copies the files directly from their sources.

    The file mode also takes unpacked_archives: each file of an extracted repository goes below one directory, at its
    path in the archive. unpacked_archive_executables names the destinations that get the executable bit. The collector
    refuses an archive file with an executable bit that this list does not name, on macOS and Linux. It reads the bit
    from the executable list of the archive in unpacked_archive_executable_lists, never from the file on disk.
    """,
    implementation = _packed_jars_component_impl,
    attrs = _COLLECTOR_ATTRS | {
        "platform_payload": attr.label(
            providers = [DistPlatformPayloadInfo],
            doc = "The payload's packed `lib/` jars, derived from the graph - see `dist_platform_payload`.",
        ),
        "files": attr.string_keyed_label_dict(allow_files = True, doc = "Maps each distribution path to its source label. The composer copies the file as is."),
        "executable_files": attr.string_keyed_label_dict(allow_files = True, doc = "Maps each distribution path to its source label. The composer copies the file with the executable bit."),
        "unpacked_archives": attr.label_keyed_string_dict(
            allow_files = True,
            doc = "Maps the `:files` filegroup of a `extracted_download_repo` to a distribution directory. " +
                  "Each file goes to `<directory>/<path in the archive>`.",
        ),
        "unpacked_archive_executables": attr.string_list(
            doc = "The destinations of `unpacked_archives` that get the executable bit. A value with a final `/` names " +
                  "a directory, and the files directly in it are executable. Each value must name a file.",
        ),
        "unpacked_archive_executable_lists": attr.string_keyed_label_dict(
            allow_files = True,
            doc = "Maps a directory of `unpacked_archives` to the executable list of its archive, " +
                  "`executables.json` of a `extracted_download_repo` with `executable_list`. " +
                  "A directory without a list gets no check of the executable bits.",
        ),
        "plugin_classpath_prefix": attr.label(
            allow_single_file = True,
            doc = "The `plugin-classpath.txt` prefix that `dist_product_descriptor` writes. Only with `platform_payload`.",
        ),
        "main_class": attr.string(
            doc = "The IDE main class the component declares, from the launch model. The `platform_resources` component states it. Empty for none.",
        ),
    },
)

def _runfile_path(ctx, file):
    path = file.short_path
    return path[3:] if path.startswith("../") else ctx.workspace_name + "/" + path

def _binding_input(member):
    return None

def _add_source_bindings(args, fragment, anchor):
    def source_binding(source, expander):
        return json.encode({
            "component": fragment.manifest.path,
            "source": source.path,
            "anchorRelativePath": _binding_relative_path(source, anchor),
            "type": "directory" if source.is_directory else "symlink" if source.is_symlink else "file",
            "members": [member.tree_relative_path for member in expander.expand(source)] if source.is_directory else [],
        })

    sources = fragment.payload if fragment.payload else depset()
    args.add_all(sources, map_each = _binding_input, expand_directories = True)
    args.add_all(sources, map_each = source_binding, expand_directories = False, allow_closure = True)

def _binding_relative_path(source, anchor):
    source_parts = source.path.split("/")
    anchor_parts = anchor.dirname.split("/")
    common = 0
    for index in range(min(len(source_parts), len(anchor_parts))):
        if source_parts[index] != anchor_parts[index]:
            break
        common += 1
    return "/".join([".."] * (len(anchor_parts) - common) + source_parts[common:])

def _parents(path):
    """Every directory that holds `path`, the deepest first."""
    parts = path.split("/")
    return ["/".join(parts[:index]) for index in range(len(parts) - 1, 0, -1)]

def _merge_placements(ctx, fragment_targets):
    """The merged placement of every component, or None when a component states none.

    A destination belongs to one component. No file and no directory lies below a tree, because a runfiles tree cannot
    write into a linked directory.
    """
    files = {}
    trees = {}
    executables = []
    owners = {}
    for target in fragment_targets:
        if DistPlacementInfo not in target:
            return None
        placement = target[DistPlacementInfo]
        kind = target[IntellijDevFragmentInfo].name
        for into, entries in [(files, placement.files), (trees, placement.trees)]:
            for destination, file in entries.items():
                if destination in owners:
                    fail("%s: '%s' is placed by both %s and %s" % (ctx.label, destination, owners[destination], kind))
                owners[destination] = kind
                into[destination] = file
        executables.extend(placement.executables)
    for destination in owners:
        for parent in _parents(destination):
            if parent in trees:
                fail("%s: '%s' of %s is below '%s', which %s links as a whole" % (ctx.label, destination, owners[destination], parent, owners[parent]))
    return struct(files = files, trees = trees, executables = executables)

# The token of a sibling vmoptions line that stands for the absolute path of the home of the export.
_EXPORT_TOKEN = "${EXPORT}"

def _sibling_vm_options(ctx, home):
    """Writes the sibling vmoptions file of the export `home`, or returns None when the rule states no line.

    The file is `<name>.dist.vmoptions`, the Toolbox-style name next to the export. The native launcher finds it on
    every OS. On macOS the home is `Contents`, and the launcher strips that level first. A file write runs on the local
    host, so no shared cache holds its absolute paths. No action reads the file.
    """
    lines = ctx.attr.sibling_vm_options
    if not lines:
        return None
    if ctx.attr.local_launch:
        fail("%s: a local launch has no export, so it takes no sibling_vm_options" % ctx.label, attr = "sibling_vm_options")
    export = None
    if ctx.attr.output_base:
        export = "%s/execroot/%s/%s" % (ctx.attr.output_base, ctx.workspace_name, home.path)
        if ctx.attr.home_prefix:
            export += "/" + ctx.attr.home_prefix
    resolved = []
    for line in lines:
        if not line or "\n" in line or "\r" in line:
            fail("%s: a sibling vmoptions line must be one JVM option, got %s" % (ctx.label, repr(line)), attr = "sibling_vm_options")
        if _EXPORT_TOKEN in line:
            if export == None:
                fail("%s: '%s' names %s, so output_base is required" % (ctx.label, line, _EXPORT_TOKEN), attr = "output_base")
            line = line.replace(_EXPORT_TOKEN, export)
        resolved.append(line)
    file = ctx.actions.declare_file(ctx.label.name + ".dist.vmoptions")
    ctx.actions.write(file, "".join([line + "\n" for line in resolved]))
    return file

def _compose(ctx, fragment_targets):
    local_launch = ctx.attr.local_launch
    home = ctx.actions.declare_directory(ctx.label.name + (".metadata" if local_launch else ".dist"))
    sibling_vm_options = _sibling_vm_options(ctx, home)
    ide_config = ctx.actions.declare_file(ctx.label.name + ".ide.config")
    fingerprint = ctx.actions.declare_file(ctx.label.name + ".fingerprint")
    mac_binaries = None if local_launch else ctx.actions.declare_file(ctx.label.name + ".mac-binaries.json")
    fragments = [target[IntellijDevFragmentInfo] for target in fragment_targets]
    component_files = depset(transitive = [fragment.payload for fragment in fragments if fragment.payload])
    runtime_files = component_files if local_launch else depset()

    prefixes = [fragment.plugin_classpath_prefix for fragment in fragments if fragment.plugin_classpath_prefix]
    parts = [fragment.plugin_classpath_part for fragment in fragments if fragment.plugin_classpath_part]
    if parts and len(prefixes) != 1:
        fail("%s: exactly one component must provide the plugin-classpath prefix, got %d" % (ctx.label, len(prefixes)))

    # A local launch also writes the two metadata files as files of their own, and checks the placement of a launcher
    # that links its home in the runfiles tree.
    placement = _merge_placements(ctx, fragment_targets) if local_launch else None
    core_classpath = ctx.actions.declare_file(ctx.label.name + ".core-classpath.txt") if local_launch else None
    plugin_classpath = ctx.actions.declare_file(ctx.label.name + ".plugin-classpath.txt") if local_launch and parts else None

    source_bindings = None
    if not local_launch:
        source_bindings = ctx.actions.declare_file(ctx.label.name + ".source-bindings.jsonl")
        bindings = ctx.actions.args()
        bindings.set_param_file_format("multiline")
        for fragment in fragments:
            _add_source_bindings(bindings, fragment, source_bindings)
        ctx.actions.write(source_bindings, bindings)

    composition_spec = ctx.actions.declare_file(ctx.label.name + ".composition.json")
    ctx.actions.write(
        composition_spec,
        json.encode({
            "version": 1,
            "platformPrefix": ctx.attr.platform_prefix,
            "expectedFragments": ctx.attr.expect_fragments,
            "additionalModules": ctx.attr.additional_modules,
            "bundledModules": ctx.attr.bundled_modules,
            "components": [
                {
                    # The manifest names each file where it already is, and the composer copies from there.
                    "manifest": fragment.manifest.path,
                    "pluginClasspathPart": fragment.plugin_classpath_part.path if fragment.plugin_classpath_part else None,
                }
                for fragment in fragments
            ],
            "pluginClasspathPrefix": prefixes[0].path if prefixes else None,
            "sourceRunfiles": {file.path: _runfile_path(ctx, file) for file in runtime_files.to_list()} if local_launch else None,
            "sourceDirectoryRunfiles": {file.path: _runfile_path(ctx, file) for file in component_files.to_list() if file.is_directory},
            "sourceBindings": source_bindings.path if source_bindings else None,
            "placement": {
                "files": {destination: file.path for destination, file in placement.files.items()},
                "trees": {destination: tree.path for destination, tree in placement.trees.items()},
                "executables": placement.executables,
            } if placement else None,
        }),
    )

    args = ctx.actions.args()
    args.add("--composition-spec=" + composition_spec.path)
    args.add("--output-dir=" + home.path)
    args.add("--ide-config=" + ide_config.path)
    args.add("--fingerprint=" + fingerprint.path)
    if mac_binaries:
        args.add("--mac-binaries=" + mac_binaries.path)
    if ctx.attr.home_prefix:
        args.add("--home-prefix=" + ctx.attr.home_prefix)
    metadata_files = [file for file in [core_classpath, plugin_classpath, mac_binaries] if file]
    if core_classpath:
        args.add("--core-classpath-file=" + core_classpath.path)
    if plugin_classpath:
        args.add("--plugin-classpath-file=" + plugin_classpath.path)
    spans = _declare_spans(ctx, args, ctx.label.name)
    ctx.actions.run(
        inputs = depset(
            direct = [composition_spec] + ([source_bindings] if source_bindings else []) +
                     [fragment.manifest for fragment in fragments] + parts + prefixes,
            transitive = [fragment.payload for fragment in fragments if fragment.payload] if not local_launch else [],
        ),
        outputs = [home, ide_config, fingerprint] + metadata_files + ([spans] if spans else []),
        executable = ctx.executable.composer,
        arguments = [args],
        # Composed distributions are large and intended for local consumption. Until a producer and delivery policy
        # exist, the local disk cache is the only intended cache.
        execution_requirements = {"no-remote-cache": "1"},
        resource_set = _composer_resources,
        mnemonic = "IntellijDevLaunchMetadata" if local_launch else "IntellijDistCompose",
        progress_message = "Composing dev launch metadata %s" % ctx.label if local_launch else "Composing distribution %s" % ctx.label,
    )
    return [
        DefaultInfo(
            # The sibling vmoptions file is no runfile: a consumer of the export reads the export, not the local file.
            files = depset([home, ide_config, fingerprint] + [file for file in [mac_binaries, sibling_vm_options] if file]),
            runfiles = ctx.runfiles(files = [home, ide_config, fingerprint], transitive_files = runtime_files),
        ),
        IntellijDistInfo(
            home = home,
            home_prefix = ctx.attr.home_prefix,
            ide_config = ide_config,
            fingerprint = fingerprint,
            mac_binaries = mac_binaries,
            runtime_files = runtime_files,
            placement = placement,
            core_classpath = core_classpath,
            plugin_classpath = plugin_classpath,
            sibling_vm_options = sibling_vm_options,
        ),
        # Read by `intellij_dist_config`, which needs the single-file label `$(rlocationpath ...)` takes - which a
        # dist target, with three outputs, is not. Not reachable through `IntellijDistInfo`: the consumers are
        # `filegroup`s and `$(location)` expansions in `data`, not rules that could ask for a provider.
        OutputGroupInfo(
            ide_config = depset([ide_config]),
            # The complete production plugin payload, without opening component archives or directories.
            dist_plugin_outputs = _side_output_group([], fragment_targets, "dist_plugin_outputs"),
            # Every span file of this distribution in one request: the composition's own and each component's. This is the
            # group a measuring run asks for.
            trace_spans = _spans_output_group([spans], fragment_targets),
        ),
    ]

def _compose_fragments_impl(ctx):
    if ctx.attr.plugin_components and not ctx.attr.stamp_class:
        fail("%s composes plugin components and states no stamp_class" % ctx.label, attr = "stamp_class")

    # The plugin components follow the platform components. The composer checks the set of kinds, not their order.
    return _compose(ctx, ctx.attr.fragments + ctx.attr.plugin_components)

intellij_fragments_dist = rule(
    implementation = _compose_fragments_impl,
    attrs = {
        "composer": attr.label(executable = True, cfg = "exec", mandatory = True),
        "fragments": attr.label_list(providers = [IntellijDevFragmentInfo], mandatory = True),
        "platform_prefix": attr.string(
            mandatory = True,
            doc = """The `build/dev-build.json` key of the product. The composer writes it as `platform.prefix` of `DevIdeConfig`,
and the fingerprint hashes it. No component manifest states a product, so a plugin component serves every product.""",
        ),
        "plugin_components": attr.label_list(
            providers = [IntellijDevFragmentInfo],
            cfg = dist_stamp_class_consumer_transition,
            doc = """The plugin components of this product, composed after the components of `fragments`.

Transitioned onto `stamp_class`. Each component keeps only its stamp class, so one component target per plugin serves
every product of one stamp class.""",
        ),
        "stamp_class": attr.string(
            doc = "The mode of the product, the value of `dist_stamp_class` for `plugin_components`. Required when that list is not empty.",
        ),
        "_allowlist_function_transition": attr.label(default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist")),
        "local_launch": attr.bool(default = False, doc = "Compose metadata that refers to component runfiles instead of copying their contents."),
        "expect_fragments": attr.string_list(
            doc = "The component names of this distribution, stated independently of `fragments`. A component missing from that list then fails composition and does not thin the IDE.",
        ),
        "additional_modules": attr.string_list(
            doc = "The plugin modules this distribution declares it contains, for `DevIdeConfig`. What the " +
                  "distribution was configured with, not what any one component assembled. A shared plugin component " +
                  "packs a module that the product bundles, so only this attribute tells a consumer that the " +
                  "distribution contains it.",
        ),
        "bundled_modules": attr.string_list(
            doc = "The main modules of the bundled plugin components of `plugin_components`, for `DevIdeConfig`. The " +
                  "composer writes them as `bundled.modules`, so a consumer that requests a bundled plugin finds it " +
                  "without an `additional_modules` entry. Each one must be the name of a plugin component.",
        ),
        "home_prefix": attr.string(
            doc = "The directory below the output that holds the home of the IDE: `Contents` for a macOS app, or empty " +
                  "for the output itself. `home_prefix` of `dist_product_files.bzl` states the value. The composer " +
                  "writes every file below it, and `DevIdeConfig` names it as the home. A local launch takes it too.",
        ),
        "sibling_vm_options": attr.string_list(
            doc = "The lines of `<name>.dist.vmoptions`, the sibling vmoptions file next to the export. Each line is one " +
                  "JVM option. `${EXPORT}` in a line stands for the absolute path of the home of the export. The native " +
                  "launcher reads the file as the user vmoptions file of a bare start. A local launch refuses the attribute.",
        ),
        "output_base": attr.string(
            doc = "The absolute output base of the workspace. The rule needs it when a line of `sibling_vm_options` names `${EXPORT}`.",
        ),
    } | _TRACE_SPANS_ATTR,
)
