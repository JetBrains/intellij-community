"""Builds independently cacheable fragments of a dev-mode IDE distribution.

A fragment names itself and the slice it owns; the assembler decides ownership from the layout it computed, so the
fragments of one distribution partition it exactly instead of following lists someone maintains. See
`org.jetbrains.intellij.build.dev.DevBuildFragment`.

Split assembly deliberately supports only builds with scrambling disabled. Platform co-scrambling and per-plugin
scrambling require both component layouts in one process.
"""

load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@community//build:project_model_manifest.bzl", "write_project_model_manifest")
load("//build:dev_launch_dependencies.bzl", "platform_parts")
load(":dev_dist_content.bzl", "DevDistContentInfo", "DevDistPlatformPayloadInfo")
load(":dev_dist_plugin_descriptor.bzl", "DEV_DIST_DESCRIPTOR_KEY_PREFIX", "DevDistPluginDescriptorSetInfo")

# Pinned so the fragments of one distribution agree and an assembly does not carry the wall clock into its outputs. It
# dates archive entries and the `.SNAPSHOT` plugin version suffix, and both would otherwise differ between fragments
# assembled minutes apart.
#
# Deliberately *not* the product build date. A dev distribution stamps none, so the IDE resolves its build time at
# startup and no EAP expiration period can run out on a cached distribution. See `computeAppInfoXml`. A far-future date
# chosen to outrun that period makes every dev IDE start expired, because a build date over a day ahead of the wall
# clock is expired too.
#
# `dev_dist_plugin_descriptor` stamps the same date, and it reads the product's own value out of the generated plan.
# `dev_dist_plugin_descriptor_helpers_test` compares the plan's value against this constant.
DEV_DIST_PINNED_BUILD_DATE_IN_SECONDS = "1767225600"  # 2026-01-01T00:00:00Z

IntellijDevBuildInputsInfo = provider(
    doc = "The exact Bazel inputs and label-to-path manifest made available to one dev-build fragment.",
    fields = {
        "files": "The input files named by the manifest.",
        "manifest": "The logical Bazel input label to execution path manifest.",
        "inputs_origin": "The sidecar naming which half of the declaration each manifest key came from.",
        "prepacked_plugin_jars": "The typed prepacked plugin-jar records carried by this content.",
        "prepacked_plugin_jars_plan": "The relation-only plan passed to JarPackager; it contains no jar paths.",
        "patched_descriptors": "The produced plugin descriptors this fragment reads instead of computing them.",
    },
)

# How strongly a declaration half demands its key, lowest first, for a key more than one half names.
#
# `descriptor` is highest, so a key some other half also names keeps that half's name. It cannot happen today - a
# descriptor key is in a namespace of its own, see `DEV_DIST_DESCRIPTOR_KEY_PREFIX` - and the rank states what would
# happen if it ever did.
_ORIGIN_RANK = {"raw": 0, "member": 1, "library": 2, "descriptor": 3}

def _add_input_entry(ctx, entries, origins, logical_key, files, source, origin):
    """Record one manifest entry, failing when two different file lists claim the same logical key.

    Same key, same files is the normal case - two content targets naming the same module, say - and deduplicates.

    [files] is a *list* because one key does not always mean one file: a module or a raw input is one jar, but a library
    is keyed by the container target that groups its jars (see `_collect_libraries` in `dev_dist_content.bzl`) and a
    multi-jar library has several, in an order the packer depends on. The manifest still holds one line per file, which
    is what keeps `wc -l` counting files for `dev_dist_unused_inputs_test.bzl`. The origin sidecar stays one line per
    *key* and is read as a lookup rather than positionally (`tallyOrigins` in `//build/dev-dist` keeps the manifest as
    a list of pairs and the origins as a `Map`), so a repeated key resolves to the one origin it was recorded under.

    [origin] is written to a sidecar only, never to the manifest: it answers "which half of the declaration asked for
    this key", which is what turns `.unused-inputs` from a count into an attributable measurement.
    """
    previous_origin = origins.get(logical_key)
    if previous_origin == None or _ORIGIN_RANK[origin] < _ORIGIN_RANK[previous_origin]:
        origins[logical_key] = origin

    previous = entries.get(logical_key)
    if previous == None:
        entries[logical_key] = files
    elif previous != files:
        fail("%s: logical input '%s' is provided by both %s and %s" % (
            ctx.label,
            logical_key,
            [file.owner for file in previous],
            source,
        ))

def _prepacked_by_relation(ctx, entries):
    """Index prepacked plugin-jar records by their *(plugin, content module)* relation.

    Same relation, same placement and same jar is the normal case - a content module shared by two plugins, or a
    community half and the completion set naming the same member - and deduplicates. Anything else is two producers
    disagreeing about where one jar goes, which the composer would only catch if the paths happened to collide.
    """
    result = {}
    for entry in entries:
        key = (entry.plugin_main_module, entry.content_module)
        previous = result.get(key)
        if previous != None and (previous.relative_output_file != entry.relative_output_file or previous.jar != entry.jar):
            fail("%s: prepacked plugin relation %s/%s is provided by conflicting records" % (
                ctx.label,
                entry.plugin_main_module,
                entry.content_module,
            ))
        result[key] = entry
    return result

def _dev_build_inputs_impl(ctx):
    entries = {}
    origins = {}
    prepacked_plugin_jars = []
    patched_descriptors = []

    for target in ctx.attr.inputs:
        files = target[DefaultInfo].files.to_list()
        if len(files) != 1:
            fail("%s: %s must provide exactly one file, got %s" % (ctx.label, target.label, files))
        _add_input_entry(ctx, entries, origins, str(target.label), (files[0],), target.label, "raw")

    # A raw input a payload asked for on behalf of named modules, kept only while one of those modules is still declared.
    # This is where a handed-over `lib/` jar stops costing declarations: the module whose bytes are now in that jar is
    # not declared, so neither its own output, nor its libraries, nor - through `declared_modules` - anything only its
    # dependencies needed, stays in the manifest. The value is module *names* because a payload is written in names, and
    # a name is the one key that means the same thing to the repository rule that wrote it and to analysis here.
    if ctx.attr.owned_inputs:
        declared = {name: True for name in ctx.attr.platform_payload[DevDistPlatformPayloadInfo].declared_modules.to_list()}
        for target, owners in ctx.attr.owned_inputs.items():
            files = target[DefaultInfo].files.to_list()
            if len(files) != 1:
                fail("%s: %s must provide exactly one file, got %s" % (ctx.label, target.label, files))
            if not [owner for owner in owners.split(" ") if owner in declared]:
                continue
            _add_input_entry(ctx, entries, origins, str(target.label), (files[0],), target.label, "raw")

    if ctx.attr.content:
        content = ctx.attr.content[DevDistContentInfo]

        # The keys must be byte-identical to the ones the generated name table produced, because `DevDistMain` asks
        # for exactly those strings through `BazelBuildInputs.resolve` and an unknown key is a hard error there.
        #
        # A module's production label is the *rule* label plus `.jar` (`compute_module_targets`,
        # `@community//build:jps_target_derivation.bzl:410-420`), and the rule's jar output is `%{name}.jar`
        # (`jvm-rules/rules/common-attrs.bzl:129-132`) - so the jar file's owner is the rule and `owner + ".jar"`
        # reproduces the label. `jps_dynamic_deps_ultimate.bzl:567-570` states the same identity from the other side.
        for jar in content.module_jars.to_list():
            _add_input_entry(ctx, entries, origins, str(jar.owner) + ".jar", (jar,), jar.owner, "member")

        # A library is keyed by its *container* target, whose label is not derivable from the jars: a Maven library's
        # per-jar target is a `copy_file` output, so a file's owner is the `...jar_copy` rule (`lib.kt:408-423`), and the
        # container is a third label again. The key is what `build/bazel-targets.json` records as
        # `LibraryDescription.target`, because that is what `findLibraryRoots` asks for; `DevDistContentInfo` carries it
        # with the container's ordered jars.
        for entry in content.library_jars.to_list():
            _add_input_entry(ctx, entries, origins, entry.label, entry.jars, entry.label, "library")

        prepacked_plugin_jars = content.prepacked_plugin_jars.to_list()

    if ctx.attr.patched_descriptors:
        # A produced descriptor is a *file* a fragment reads, so it travels in the manifest and not in a relation-only
        # plan: the plan holds no path by design. The key is the plugin's main module, which is the one string that means
        # the same thing on both sides - `KtJvmInfo.module_name` names the plugin here, and `PluginLayout.mainModule`
        # names it in the assembly. The descriptor target's own label would not do: its name segment comes from the
        # module *target*, which the Kotlin side cannot derive.
        patched_descriptors = ctx.attr.patched_descriptors[DevDistPluginDescriptorSetInfo].descriptors.to_list()
        for record in patched_descriptors:
            _add_input_entry(
                ctx,
                entries,
                origins,
                DEV_DIST_DESCRIPTOR_KEY_PREFIX + record.plugin_main_module,
                (record.descriptor,),
                ctx.attr.patched_descriptors.label,
                "descriptor",
            )

    lines = []
    files = []
    for logical_key in sorted(entries.keys()):
        # One line per file, so a multi-jar library repeats its key. `ExplicitBazelInputResolver.load` collects the
        # repeats into one ordered list; keeping the file as the unit is what lets `wc -l` and the origin sidecar go on
        # meaning what they meant.
        for file in entries[logical_key]:
            lines.append("%s\t%s" % (logical_key, file.path))
            files.append(file)
    manifest = ctx.actions.declare_file(ctx.label.name + ".bazel-inputs")

    # An empty declaration writes an empty file rather than a lone newline, for the same reason `writeUnusedInputs`
    # does: `wc -l` is what reads this pair, and a blank line would report one declared input that does not exist.
    # Unchanged for every non-empty manifest, so no fragment is re-keyed by this.
    ctx.actions.write(manifest, ("\n".join(lines) + "\n") if lines else "")

    # A sidecar, deliberately not a third column in the manifest: `ExplicitBazelInputResolver.load` splits on the first
    # tab and takes the rest of the line as the path, and the manifest is an action input - changing its bytes would
    # re-key every fragment to carry a measurement. Nothing declares this file as an input, so it re-keys nothing.
    inputs_origin = ctx.actions.declare_file(ctx.label.name + ".bazel-inputs-origin")
    origin_lines = ["%s\t%s" % (logical_key, origins[logical_key]) for logical_key in sorted(origins.keys())]
    ctx.actions.write(inputs_origin, ("\n".join(origin_lines) + "\n") if origin_lines else "")

    prepacked_by_key = _prepacked_by_relation(ctx, prepacked_plugin_jars)

    plan_lines = []
    for key in sorted(prepacked_by_key.keys()):
        entry = prepacked_by_key[key]
        for value in [entry.plugin_main_module, entry.content_module, entry.relative_output_file]:
            if "\t" in value or "\n" in value:
                fail("%s: prepacked plugin plan value contains a tab or newline: %s" % (ctx.label, value))
        plan_lines.append("%s\t%s\t%s" % (entry.plugin_main_module, entry.content_module, entry.relative_output_file))
    prepacked_plan = ctx.actions.declare_file(ctx.label.name + ".prepacked-plugin-jars")
    ctx.actions.write(prepacked_plan, ("\n".join(plan_lines) + "\n") if plan_lines else "")

    return [
        DefaultInfo(files = depset([manifest, prepacked_plan, inputs_origin])),
        IntellijDevBuildInputsInfo(
            files = depset(files),
            manifest = manifest,
            inputs_origin = inputs_origin,
            prepacked_plugin_jars = depset(prepacked_by_key.values()),
            prepacked_plugin_jars_plan = prepacked_plan,
            # Published as well as written into the manifest, because the manifest is a file and an analysis test cannot
            # read one. `//build:idea_dev_plugins_descriptor_declaration_test` asserts this field against the plan, and
            # that assertion is the only guard against a dropped declaration: both producers write the same bytes, so
            # every byte gate in the repository stays green when a fragment silently goes back to computing the text.
            patched_descriptors = depset(patched_descriptors),
        ),
    ]

intellij_dev_build_inputs = rule(
    doc = "Groups a fragment's derived raw labels behind one typed, validated input boundary.",
    implementation = _dev_build_inputs_impl,
    attrs = {
        # Inputs are direct generated labels, never aliases: configured Target.label is the manifest key. The Kotlin
        # resolver adds apparent-repository aliases for canonical external-repository labels.
        #
        # This half keeps carrying the raw sources - the project-model tree's files, plugin descriptors, build modules -
        # which are files a generator names one by one and no provider can aggregate.
        "inputs": attr.label_list(allow_files = True),
        # The jars, aggregated from the graph instead of from a generated name list. Both halves land in one manifest
        # under the same key convention, so nothing on the Kotlin side can tell where an entry came from.
        "content": attr.label(providers = [DevDistContentInfo]),
        # Set on the fragment that owns `lib/`, together with `owned_inputs`. The reference target takes the same split
        # from the other side - it declares the packed halves through `content` and packs them itself - so one target
        # drives both and they cannot disagree about which side a module is on.
        "platform_payload": attr.label(
            providers = [DevDistPlatformPayloadInfo],
            doc = "Decides which `owned_inputs` survive, through its `declared_modules`.",
        ),
        # Deliberately separate from `inputs`, which stays unconditional. What belongs there is everything a fragment
        # reads for a reason no payload module owns: the project-model tree's files, the plugin descriptors, the
        # payload's own library entries, and the build modules - every fragment loads the product properties before it
        # packs anything, so those jars are classpath rather than content even for the two of them
        # (`intellij.platform.dependencies`, `intellij.platform.buildScripts.downloader`) that a `lib/` jar also holds.
        "owned_inputs": attr.label_keyed_string_dict(
            allow_files = True,
            doc = "Raw input to the space-separated names of the payload modules that asked for it.",
        ),
        # One label and not a list: which plugins this fragment lays out is the plan's partition, and the set target in
        # `//build/dev-dist-descriptors` is where that partition is applied. See `DevDistPluginDescriptorSetInfo`.
        "patched_descriptors": attr.label(
            providers = [DevDistPluginDescriptorSetInfo],
            doc = "The produced descriptors of this fragment's plugins, or unset for a fragment that patches its own.",
        ),
    },
)

IntellijDevFragmentInfo = provider(
    fields = {
        "name": "The fragment name, which is also the `kind` of its manifest.",
        "home": "The fragment tree, or None for a component whose manifest names files that already exist.",
        "manifest": "The fragment manifest.",
        "payload": "The already-existing files a `home`-less component's manifest names, or None; the composer stages them.",
        "plugin_classpath_part": "This fragment's plugin-classpath records, or None if it built no plugin.",
        "plugin_classpath_prefix": "The plugin-classpath prefix, or None if another fragment produces it.",
        "inputs_manifest": "The label-to-path manifest of the fragment's declared Bazel inputs.",
        "unused_inputs": "The declared inputs the assembly never resolved - declared minus these is what it used.",
        "prepacked_plugin_jars": "The prepacked jar records this fragment hands to a collector without consuming.",
        "prepacked_plugin_jars_placement": "The assembler-validated placement manifest, or None for a non-plugin component.",
    },
)

IntellijDevDistInfo = provider(
    fields = {
        "fingerprint": "The content fingerprint of the composed IDE distribution.",
        "home": "The composed IDE home directory.",
        "ide_config": "The config file used by PreBuiltDevMain.",
        "stamp_inputs": "Small declared inputs whose contents identify the fragments composed into the distribution.",
    },
)

IntellijProjectModelTreeInfo = provider(
    fields = {
        "tree": "The checkout-shaped project model tree.",
    },
)

# Not `local`, which is what these actions used to say. Verified against the shipped `JetBrains/9.1.0-jb` binary rather
# than the documentation: `Spawns.mayBeCached` is `!containsKey("no-cache") && !containsKey("local")`, and
# `RemoteExecutionService.getRead/WriteCachePolicy` gates **`--disk_cache`** on exactly that - so `local` was throwing
# away the local disk cache in order to keep gigabytes off the shared remote one. This says only the second thing.
# Fragment outputs stay in the local disk cache: they are too large for the shared cache, and remote execution is out of
# scope until the inputs are represented by platform-independent plans. Network access is blocked as a second line of
# defence behind `--preloaded-only`; an accidentally incomplete preload set must fail instead of poisoning the cache.
_LOCAL_DISK_CACHE_ONLY = {
    "block-network": "1",
    "no-remote-cache": "1",
    "no-remote-exec": "1",
}

# The switch that turns span output on, carried by every rule here that runs a packaging tool.
#
# A private label attribute read through `BuildSettingInfo` rather than a `select()` on a public one, so the value is
# read once in the implementation and the two branches sit next to each other: declare the file and name it on the
# command line, or do neither. The label is written bare and resolves in the repository this `.bzl` belongs to - the
# same thing `content_module_jar.bzl` relies on for its `_packer = "//build/content-module-packer"` - so an ultimate
# build reaches `@community//platform/build-scripts/bazel-rules:trace_spans` without this file naming a repository.
_TRACE_SPANS_ATTR = {
    "_trace_spans": attr.label(
        default = "//platform/build-scripts/bazel-rules:trace_spans",
        providers = [BuildSettingInfo],
    ),
}

# The switch that turns the executed packaging recipe on, carried by the one rule here that runs the assembler.
#
# A sibling of the above with the same shape rather than a reuse of it: a recipe and a measurement are wanted at
# different times, and `trace_spans` has a protocol - hold it constant across a comparison, read it only from a cold
# run - that a recipe request must not drag a build into. See the flag's own comment in this package's `BUILD.bazel`.
_DEV_DIST_PLANS_ATTR = {
    "_dev_dist_plans": attr.label(
        default = "//platform/build-scripts/bazel-rules:dev_dist_plans",
        providers = [BuildSettingInfo],
    ),
}

# The third flag of this shape, for the plugin descriptors a fragment patched. See the flag's own comment in this
# package's `BUILD.bazel` for why it is not the recipe's flag, and why "patched" is in the name.
_DEV_DIST_PATCHED_DESCRIPTORS_ATTR = {
    "_dev_dist_patched_descriptors": attr.label(
        default = "//platform/build-scripts/bazel-rules:dev_dist_patched_descriptors",
        providers = [BuildSettingInfo],
    ),
}

def _declare_side_output(flag, ctx, args, base, suffix, option):
    """Declare one side output of an action and tell its tool where to write it - or do neither.

    Returns the `File`, or None when `flag` is off. A side output is pure: nothing declares it as an input and no
    provider another rule reads carries it, so the only trace of it in an action is the extra declared output and the
    extra argument - and with the flag off there is neither. That is what lets a request for one of these files leave
    every action key, argument list and declared output set exactly as it was.

    **Append the returned file to `outputs`; never prepend it.** `dev-dist trace` joins a span file to its action by the
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

def _declare_plan(ctx, args, base):
    """This fragment's executed packaging recipe, or None when `dev_dist_plans` is off."""
    return _declare_side_output(ctx.attr._dev_dist_plans, ctx, args, base, ".plan.yaml", "--plan")

def _declare_patched_descriptors(ctx, args, base):
    """The plugin descriptors this fragment patched, or None when `dev_dist_patched_descriptors` is off."""
    return _declare_side_output(
        ctx.attr._dev_dist_patched_descriptors,
        ctx,
        args,
        base,
        ".patched-descriptors.json",
        "--patched-descriptors",
    )

def _side_output_group(own, dependencies, group_name):
    """One side output group: this target's own files of that group, plus those of the targets it composes.

    Always present and empty when the group's flag is off, so `--output_groups=+<group_name>` is a valid request either
    way.

    Propagation is only where the graph makes a file otherwise unreachable, and it is the caller that knows where that
    is. A fragment carries its project model tree's spans, because no dist rule depends on that tree. A dist carries
    every group of every fragment it composes. Nothing else propagates: a side output of an action whose output the
    build already needs is written by that action anyway, since Bazel runs an action for any of its outputs and requires
    all of them.

    Call it through one of the three wrappers below, never directly. The group's name has to be the same on both sides,
    and a mismatch analyses, passes, and produces a silently empty group.

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

def _plans_output_group(own, dependencies):
    return _side_output_group(own, dependencies, "dev_dist_plans")

def _patched_descriptors_output_group(own, dependencies):
    return _side_output_group(own, dependencies, "dev_dist_patched_descriptors")

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
        # No execution requirements: this one is hermetic. It reads its manifest and the execroot-relative sources that
        # manifest names, writes only under its output directory, and consults no environment variable, no home
        # directory and no network - so it may be sandboxed, and both caches may keep it.
        mnemonic = "IntellijProjectModelTree",
        progress_message = "Materializing the project model tree %s" % ctx.label,
    )
    return [
        DefaultInfo(files = depset([tree])),
        # Not in `IntellijProjectModelTreeInfo`: that provider is what a fragment reads to declare an *input*, and a
        # span file must never become one. The fragment reaches this group instead.
        OutputGroupInfo(trace_spans = _spans_output_group([spans], [])),
        IntellijProjectModelTreeInfo(tree = tree),
    ]

intellij_project_model_tree = rule(
    doc = """The checkout-shaped JPS project model tree that dev-distribution fragments read.

    One tree per product and target platform, shared by every fragment of it. A fragment used to build its own, and at
    7 432 file copies that cost as much as the assembly itself - affordable once, not once per fragment.

    It carries the union of what the fragments need, so a file only one of them reads (the OS natives of the resources
    fragment, say) now invalidates all of them. Those change far less often than the model does, and the model was
    already invalidating every fragment: project files are inputs no fragment can prune, unlike the module jars.
    """,
    implementation = _project_model_tree_impl,
    attrs = {
        "materializer": attr.label(executable = True, cfg = "exec", mandatory = True),
        "mode": attr.string(default = "ultimate", values = ["community", "ultimate"]),
        "project_model_files": attr.label_list(allow_files = True, mandatory = True),
        "extra_project_files": attr.label_list(allow_files = True),
    } | _TRACE_SPANS_ATTR,
)

# The selector values `DevDistMain` accepts, mirrored here so a typo in a BUILD file fails at analysis time.
_PLATFORM_SELECTORS = ["", "except", "only"]

_PLUGIN_SELECTORS = ["", "named", "remaining"]

def _add_target_platform_args(args, target_platform):
    if target_platform:
        target_parts = platform_parts(target_platform)
        args.add("--os=" + ("macos" if target_parts.os == "darwin" else target_parts.os))
        args.add("--arch=" + target_parts.arch)

def _mnemonic(fragment_name):
    """A CamelCase mnemonic, so `bazel --profile` and `--strategy` can name these actions."""
    return "IntellijDev" + "".join([part.capitalize() for part in fragment_name.replace("-", "_").replace(".", "_").split("_")])

def _fragment_impl(ctx):
    if not ctx.attr.platform and not ctx.attr.platform_resources and not ctx.attr.plugins:
        fail("%s selects nothing: set platform, platform_resources or plugins" % ctx.label)
    if not ctx.files.preloaded_manifests:
        fail("%s must declare at least one preloaded download manifest" % ctx.label)

    home = ctx.actions.declare_directory(ctx.label.name + ".home")
    component_manifest = ctx.actions.declare_file(ctx.label.name + ".component.json")
    scratch = ctx.actions.declare_directory(ctx.label.name + ".scratch")

    # Which declared inputs the assembly never resolved. It used to be `unused_inputs_list`, pruning the action key
    # after the fact - which can skip a re-run in one output base but never produce a disk- or remote-cache hit, since
    # the key is computed over the full declared set before the action runs. Narrowing what is *declared* replaced it.
    # The file stays as the measurement of how honest a declaration is: declared minus unused is what a fragment used.
    unused_inputs = ctx.actions.declare_file(ctx.label.name + ".unused-inputs")
    outputs = [home, component_manifest, scratch, unused_inputs]

    project_tree = ctx.attr.project_model_tree[IntellijProjectModelTreeInfo].tree
    build_inputs = ctx.attr.build_inputs[IntellijDevBuildInputsInfo]
    bazel_inputs_manifest = build_inputs.manifest

    args = ctx.actions.args()
    args.add("--project-dir=" + project_tree.path)
    args.add("--output-dir=" + home.path)
    args.add("--component-manifest=" + component_manifest.path)
    args.add("--scratch-dir=" + scratch.path)

    # Whatever an assembly still wants to download or extract goes here rather than into the checkout, where the cache
    # used to live: the project tree is shared and read-only now, and a cache no action declares is not an input.
    # Everything a fragment actually reads is declared - see `ijent_binaries` and the preloaded archives - so this
    # should stay empty, and it is cleaned on success.
    args.add("--download-cache-dir=" + scratch.path + "/download-cache")
    args.add("--clean-scratch-on-success")
    if ctx.files.ijent_binaries:
        # The unpacked archive, handed over as a directory: without it the build extracts the preloaded tar.gz itself.
        args.add("--ijent-binaries-dir=" + ctx.files.ijent_binaries[0].dirname)
    args.add("--fragment=" + ctx.attr.fragment_name)
    args.add("--build-date-seconds=" + ctx.attr.build_date_seconds)
    args.add("--platform-prefix=" + ctx.attr.platform_prefix)
    args.add("--bazel-targets-json=" + ctx.file.bazel_targets_json.path)
    args.add("--bazel-inputs-manifest=" + bazel_inputs_manifest.path)
    args.add("--unused-inputs=" + unused_inputs.path)
    _add_target_platform_args(args, ctx.attr.target_platform)

    if ctx.attr.platform:
        args.add("--platform=" + ctx.attr.platform)
        if ctx.attr.platform_payload:
            # Empty for a `lib/`-owning fragment of a product whose payload packs nothing per module: `except` then owns
            # all of `lib/`, which is what it meant before any of these jars were handed over.
            args.add_all(
                ctx.attr.platform_payload[DevDistPlatformPayloadInfo].packed_jar_names,
                format_each = "--platform-jar=%s",
            )
    if ctx.attr.platform_resources:
        args.add("--platform-resources")

    plugin_classpath_part = None
    prepacked_plugin_jars_placement = None
    if ctx.attr.plugins:
        args.add("--plugins=" + ctx.attr.plugins)
        args.add_all(ctx.attr.plugin_main_modules, format_each = "--plugin=%s")
        args.add_all(ctx.attr.claimed_plugin_main_modules, format_each = "--claimed-plugin=%s")
        args.add_all(ctx.attr.additional_modules, format_each = "--additional-module=%s")
        args.add_all(ctx.attr.test_output_modules, format_each = "--test-output-module=%s")

        # The count in `plugin-classpath.txt` spans the whole distribution, so a fragment can only produce its records
        # and the composer assembles the file.
        plugin_classpath_part = ctx.actions.declare_file(ctx.label.name + ".plugin-classpath-part")
        args.add("--plugin-classpath-part=" + plugin_classpath_part.path)
        outputs.append(plugin_classpath_part)

        prepacked_plugin_jars_placement = ctx.actions.declare_file(ctx.label.name + ".prepacked-plugin-jars-placement")
        args.add("--prepacked-plugin-jars=" + build_inputs.prepacked_plugin_jars_plan.path)
        args.add("--prepacked-plugin-jars-placement=" + prepacked_plugin_jars_placement.path)
        outputs.append(prepacked_plugin_jars_placement)

    plugin_classpath_prefix = None
    if ctx.attr.produces_plugin_classpath_prefix:
        plugin_classpath_prefix = ctx.actions.declare_file(ctx.label.name + ".plugin-classpath-prefix")
        args.add("--plugin-classpath-prefix=" + plugin_classpath_prefix.path)
        outputs.append(plugin_classpath_prefix)

    args.add_all(ctx.files.preloaded_manifests, format_each = "--preloaded-manifest=%s")
    args.add("--preloaded-only")

    spans = _declare_spans(ctx, args, ctx.label.name)
    if spans:
        outputs.append(spans)

    plan = _declare_plan(ctx, args, ctx.label.name)
    if plan:
        outputs.append(plan)

    patched_descriptors = _declare_patched_descriptors(ctx, args, ctx.label.name)
    if patched_descriptors:
        outputs.append(patched_descriptors)

    ctx.actions.run(
        inputs = depset(
            direct = [
                project_tree,
                bazel_inputs_manifest,
                build_inputs.prepacked_plugin_jars_plan,
                ctx.file.bazel_targets_json,
            ] + ctx.files.preloaded_downloads + ctx.files.preloaded_manifests + ctx.files.ijent_binaries,
            transitive = [build_inputs.files],
        ),
        outputs = outputs,
        executable = ctx.executable.assembler,
        arguments = [args],
        execution_requirements = _LOCAL_DISK_CACHE_ONLY,
        mnemonic = _mnemonic(ctx.attr.fragment_name),
        progress_message = "Assembling %s dev fragment %s" % (ctx.attr.platform_prefix, ctx.label),
    )
    return [
        DefaultInfo(files = depset([home, component_manifest])),
        # The three files `./build/dev-dist.cmd inputs` joins, in one group: declared keys with their paths, the
        # ones the assembly never resolved, and which half of the declaration asked for each. Requesting the group runs
        # the assembly, which is the point - `used` is only knowable from a real assembly.
        OutputGroupInfo(
            declared_inputs = depset([bazel_inputs_manifest, build_inputs.inputs_origin, unused_inputs]),
            # This fragment's spans and the shared project model tree's. The tree is deliberately carried here: it is a
            # dependency of every fragment and of no distribution, so this is the only path by which one request for a
            # dist's spans can reach it.
            trace_spans = _spans_output_group([spans], [ctx.attr.project_model_tree]),
            # This fragment's executed recipe. Nothing is propagated into it: the tree runs no assembler.
            dev_dist_plans = _plans_output_group([plan], []),
            # The plugin descriptors this fragment patched, on the same terms as the recipe.
            dev_dist_patched_descriptors = _patched_descriptors_output_group([patched_descriptors], []),
        ),
        IntellijDevFragmentInfo(
            name = ctx.attr.fragment_name,
            home = home,
            payload = None,
            manifest = component_manifest,
            plugin_classpath_part = plugin_classpath_part,
            plugin_classpath_prefix = plugin_classpath_prefix,
            inputs_manifest = bazel_inputs_manifest,
            unused_inputs = unused_inputs,
            prepacked_plugin_jars = build_inputs.prepacked_plugin_jars,
            prepacked_plugin_jars_placement = prepacked_plugin_jars_placement,
        ),
    ]

intellij_dev_fragment = rule(
    doc = """One independently cacheable slice of a dev distribution.

    What the fragment owns is a selector over names, not a file list: `platform` owns `lib/` jars by an explicit
    generated jar-name set - every jar `except` the named ones, or `only` them - `plugins` picks bundled plugin
    directories by main module, and the `remaining` selector is the exact complement of what its siblings claimed,
    so nothing is silently left out of the composition.
    """,
    implementation = _fragment_impl,
    attrs = {
        "assembler": attr.label(executable = True, cfg = "exec", mandatory = True),
        "build_date_seconds": attr.string(default = DEV_DIST_PINNED_BUILD_DATE_IN_SECONDS),
        "platform_prefix": attr.string(mandatory = True),
        "target_platform": attr.string(default = ""),
        "fragment_name": attr.string(mandatory = True, doc = "Identifies this fragment in its manifest, its mnemonic and the composer's completeness check."),
        "platform": attr.string(default = "", values = _PLATFORM_SELECTORS, doc = "Which `lib/` jars this fragment owns - all `except` the packed ones, or `only` those; empty means none."),
        "platform_resources": attr.bool(default = False, doc = "Whether this fragment owns `bin`, the product metadata, the launchers and the copied product files."),
        "platform_payload": attr.label(
            providers = [DevDistPlatformPayloadInfo],
            doc = "The `lib/` jar file names `platform` reads, as the target that derives them: with `except`, the " +
                  "jars another component provides and this fragment must therefore not pack; with `only`, the jars " +
                  "it packs and nothing else. `intellij_dev_packed_jars_component` reads the same provider, so an " +
                  "`except` fragment and it partition the `lib/` jars exactly; the composer fails on a path both " +
                  "provide. The fragment still reports those jars' core-classpath entries - deciding that needs the " +
                  "platform layout, which the packer does not have.",
        ),
        "plugins": attr.string(default = "", values = _PLUGIN_SELECTORS, doc = "Which bundled plugins this fragment owns; empty means none."),
        "plugin_main_modules": attr.string_list(doc = "For plugins = 'named': the main modules of the plugins this fragment owns."),
        "claimed_plugin_main_modules": attr.string_list(doc = "For plugins = 'remaining': the main modules the sibling fragments own."),
        "produces_plugin_classpath_prefix": attr.bool(default = False, doc = "Whether this fragment writes the `plugin-classpath.txt` prefix; exactly one fragment of a distribution does."),
        "additional_modules": attr.string_list(),
        "test_output_modules": attr.string_list(),
        "project_model_tree": attr.label(providers = [IntellijProjectModelTreeInfo], mandatory = True, doc = "The materialized project model tree this fragment reads, shared with the other fragments of its product."),
        "bazel_targets_json": attr.label(allow_single_file = True, mandatory = True),
        "build_inputs": attr.label(providers = [IntellijDevBuildInputsInfo], mandatory = True),
        "preloaded_downloads": attr.label_list(allow_files = True),
        "preloaded_manifests": attr.label_list(allow_files = True),
        "ijent_binaries": attr.label_list(allow_files = True, doc = "The unpacked IJent binaries the assembly bundles at `lib/ijent/`, so that it extracts nothing itself."),
    } | _TRACE_SPANS_ATTR | _DEV_DIST_PLANS_ATTR | _DEV_DIST_PATCHED_DESCRIPTORS_ATTR,
)

def _packed_jars_component_impl(ctx):
    component_manifest = ctx.actions.declare_file(ctx.label.name + ".component.json")

    # Which jars these are is read off the graph by `dev_dist_platform_payload`, not from a list something had to
    # keep in step. A module that stops packing a jar stops being collected here in the same analysis that stops
    # producing it, so the old "handed over but sets no `content_module_jar`" staleness cannot arise.
    jars = ctx.attr.platform_payload[DevDistPlatformPayloadInfo].packed_jars.to_list()

    jar_list = ctx.actions.args()
    jar_list.set_param_file_format("multiline")
    jar_list.use_param_file("--jars-file=%s", use_always = True)
    jar_list.add_all(jars)

    args = ctx.actions.args()
    args.add("--component-manifest=" + component_manifest.path)
    args.add("--kind=" + ctx.attr.component_name)
    args.add("--platform-prefix=" + ctx.attr.platform_prefix)
    _add_target_platform_args(args, ctx.attr.target_platform)

    # The manifest is the primary output, so it is also what names the span file: `dev-dist trace` joins a span file to
    # its action by the primary output's stem, and `X.component.json`'s stem is `X.component`.
    spans = _declare_spans(ctx, args, ctx.label.name + ".component")

    ctx.actions.run(
        inputs = depset(jars),
        outputs = [component_manifest] + ([spans] if spans else []),
        executable = ctx.executable.collector,
        arguments = [args, jar_list],
        execution_requirements = _LOCAL_DISK_CACHE_ONLY,
        mnemonic = "IntellijDevPackedJars",
        progress_message = "Naming %d packed %s jars for %%{label}" % (len(jars), ctx.attr.platform_prefix),
    )
    return [
        # The jars come along with the manifest, so that building this component alone produces everything it names.
        DefaultInfo(files = depset([component_manifest] + jars)),
        OutputGroupInfo(trace_spans = _spans_output_group([spans], [])),
        IntellijDevFragmentInfo(
            name = ctx.attr.component_name,
            home = None,
            payload = depset(jars),
            manifest = component_manifest,
            plugin_classpath_part = None,
            plugin_classpath_prefix = None,
            # Not a declared-input boundary: this component reads the jars it collects and nothing else, so there is no
            # over-declaration to measure. `dev_dist_unused_inputs_report_test` skips a component that reports neither.
            inputs_manifest = None,
            unused_inputs = None,
            prepacked_plugin_jars = depset(),
            prepacked_plugin_jars_placement = None,
        ),
    ]

intellij_dev_packed_jars_component = rule(
    doc = """The distribution component made of the `lib/` jars `jvm_library` packed itself.

    Every other component is assembled by evaluating the product layout, which declares the shared project-model tree
    and is therefore re-keyed by any `.iml` edit. These jars are packed by actions that declare only the jars they
    merge, and this rule does no more than name them `lib/<module>.jar` and inventory them - so an unrelated model edit
    leaves both the packing and this collection untouched.

    It declares no tree: its one output is the manifest, which names each jar where its packer left it, and the
    composer copies from there. A tree here would hold a second copy of every jar for no purpose other than being
    copied out of again.

    Which jars those are comes from `dev_dist_platform_payload`, which asks the payload's targets; the sibling
    fragment is handed the same provider and stops packing exactly these jars, so ownership cannot overlap.
    """,
    implementation = _packed_jars_component_impl,
    attrs = {
        "collector": attr.label(executable = True, cfg = "exec", mandatory = True),
        "component_name": attr.string(mandatory = True, doc = "Identifies this component in its manifest and in the composer's completeness check."),
        "platform_prefix": attr.string(mandatory = True),
        "target_platform": attr.string(default = ""),
        "platform_payload": attr.label(
            providers = [DevDistPlatformPayloadInfo],
            mandatory = True,
            doc = "The payload's packed `lib/` jars, derived from the graph - see `dev_dist_platform_payload`.",
        ),
    } | _TRACE_SPANS_ATTR,
)

def _packed_plugin_jars_component_impl(ctx):
    component_manifest = ctx.actions.declare_file(ctx.label.name + ".component.json")

    placement_manifests = []
    entries = []
    for target in ctx.attr.fragments:
        fragment = target[IntellijDevFragmentInfo]
        if fragment.prepacked_plugin_jars_placement:
            placement_manifests.append(fragment.prepacked_plugin_jars_placement)
        entries.extend(fragment.prepacked_plugin_jars.to_list())
    records = _prepacked_by_relation(ctx, entries)

    metadata_lines = []
    jars = []
    for key in sorted(records.keys()):
        entry = records[key]
        metadata_lines.append("%s\t%s\t%s\t%s" % (
            entry.plugin_main_module,
            entry.content_module,
            entry.relative_output_file,
            entry.jar.path,
        ))
        jars.append(entry.jar)
    metadata = ctx.actions.declare_file(ctx.label.name + ".prepacked-plugin-jars")
    ctx.actions.write(metadata, ("\n".join(metadata_lines) + "\n") if metadata_lines else "")

    args = ctx.actions.args()
    args.add("--component-manifest=" + component_manifest.path)
    args.add("--kind=" + ctx.attr.component_name)
    args.add("--platform-prefix=" + ctx.attr.platform_prefix)
    args.add("--plugin-jars-file=" + metadata.path)
    args.add_all(placement_manifests, format_each = "--plugin-placement=%s")
    _add_target_platform_args(args, ctx.attr.target_platform)

    # See the sibling rule: the span file's name follows the primary output, which is now the manifest.
    spans = _declare_spans(ctx, args, ctx.label.name + ".component")

    ctx.actions.run(
        inputs = depset(direct = [metadata] + jars + placement_manifests),
        outputs = [component_manifest] + ([spans] if spans else []),
        executable = ctx.executable.collector,
        arguments = [args],
        execution_requirements = _LOCAL_DISK_CACHE_ONLY,
        mnemonic = "IntellijDevPackedPluginJars",
        progress_message = "Naming packed plugin content jars for %{label}",
    )
    return [
        DefaultInfo(files = depset([component_manifest] + jars)),
        # Only this action's own spans: the fragments it reads placement manifests from are composed by the same
        # distribution, which carries them already.
        OutputGroupInfo(trace_spans = _spans_output_group([spans], [])),
        IntellijDevFragmentInfo(
            name = ctx.attr.component_name,
            home = None,
            payload = depset(jars),
            manifest = component_manifest,
            plugin_classpath_part = None,
            plugin_classpath_prefix = None,
            inputs_manifest = None,
            unused_inputs = None,
            prepacked_plugin_jars = depset(),
            prepacked_plugin_jars_placement = None,
        ),
    ]

intellij_dev_packed_plugin_jars_component = rule(
    doc = "Names plugin content-module jars packed by `jvm_library` at their JarPackager-validated destinations, in " +
          "a manifest the composer copies from - this rule declares no tree of its own.",
    implementation = _packed_plugin_jars_component_impl,
    attrs = {
        "collector": attr.label(executable = True, cfg = "exec", mandatory = True),
        "component_name": attr.string(mandatory = True),
        "platform_prefix": attr.string(mandatory = True),
        "target_platform": attr.string(default = ""),
        "fragments": attr.label_list(providers = [IntellijDevFragmentInfo], mandatory = True),
    } | _TRACE_SPANS_ATTR,
)

def _compose(ctx, fragment_targets):
    home = ctx.actions.declare_directory(ctx.label.name + ".dist")
    ide_config = ctx.actions.declare_file(ctx.label.name + ".ide.config")
    fingerprint = ctx.actions.declare_file(ctx.label.name + ".fingerprint")
    fragments = [target[IntellijDevFragmentInfo] for target in fragment_targets]

    prefixes = [fragment.plugin_classpath_prefix for fragment in fragments if fragment.plugin_classpath_prefix]
    parts = [fragment.plugin_classpath_part for fragment in fragments if fragment.plugin_classpath_part]
    stamp_inputs = [fragment.manifest for fragment in fragments] + parts + prefixes
    if parts and len(prefixes) != 1:
        fail("%s: exactly one fragment must set produces_plugin_classpath_prefix, got %d" % (ctx.label, len(prefixes)))

    composition_spec = ctx.actions.declare_file(ctx.label.name + ".composition.json")
    ctx.actions.write(
        composition_spec,
        json.encode({
            "version": 1,
            "expectedFragments": ctx.attr.expect_fragments,
            "additionalModules": ctx.attr.additional_modules,
            "components": [
                {
                    # None for a component that declares no tree: its manifest names each file where it already is, and
                    # the composer copies from there - see `writeSourcedDevBuildComponentManifest`.
                    "root": fragment.home.path if fragment.home else None,
                    "manifest": fragment.manifest.path,
                    "pluginClasspathPart": fragment.plugin_classpath_part.path if fragment.plugin_classpath_part else None,
                }
                for fragment in fragments
            ],
            "pluginClasspathPrefix": prefixes[0].path if prefixes else None,
        }),
    )

    args = ctx.actions.args()
    args.add("--composition-spec=" + composition_spec.path)
    args.add("--output-dir=" + home.path)
    args.add("--ide-config=" + ide_config.path)
    args.add("--fingerprint=" + fingerprint.path)
    spans = _declare_spans(ctx, args, ctx.label.name)
    ctx.actions.run(
        # A tree-less component's files are staged individually instead of as its tree: they are the very files its
        # manifest names, at the execution-root paths the manifest recorded, which is what makes them findable from
        # inside this action. The count staged is the same either way - a TreeArtifact input is staged file by file too -
        # but this action's key now lists them one by one where it listed one tree digest.
        inputs = depset(
            direct = [composition_spec] +
                     [fragment.manifest for fragment in fragments] +
                     [fragment.home for fragment in fragments if fragment.home] +
                     parts + prefixes,
            transitive = [fragment.payload for fragment in fragments if fragment.payload],
        ),
        outputs = [home, ide_config, fingerprint] + ([spans] if spans else []),
        executable = ctx.executable.composer,
        arguments = [args],
        # Composed distributions are large and intended for local consumption. Until a producer and delivery policy
        # exist, the local disk cache is the only intended cache.
        execution_requirements = {"no-remote-cache": "1"},
        mnemonic = "IntellijDevDistCompose",
        progress_message = "Composing dev distribution %s" % ctx.label,
    )
    return [
        DefaultInfo(files = depset([home, ide_config, fingerprint])),
        IntellijDevDistInfo(
            home = home,
            ide_config = ide_config,
            fingerprint = fingerprint,
            stamp_inputs = depset(stamp_inputs),
        ),
        # Read by `intellij_dev_dist_config`, which needs the single-file label `$(rlocationpath ...)` takes - which a
        # dist target, with three outputs, is not. Not reachable through `IntellijDevDistInfo`: the consumers are
        # `filegroup`s and `$(location)` expansions in `data`, not rules that could ask for a provider.
        OutputGroupInfo(
            ide_config = depset([ide_config]),
            # Every span file of this distribution in one request: the composition's own, each fragment's, and - through
            # the fragments - the shared project model tree's. This is the group a measuring run asks for.
            trace_spans = _spans_output_group([spans], fragment_targets),
            # Every fragment's executed recipe in one request. The composition itself has none: it places files, it
            # does not pack them.
            dev_dist_plans = _plans_output_group([], fragment_targets),
            # Every fragment's patched plugin descriptors in one request. The composition patches none, for the reason
            # above.
            dev_dist_patched_descriptors = _patched_descriptors_output_group([], fragment_targets),
        ),
    ]

def _compose_fragments_impl(ctx):
    return _compose(ctx, ctx.attr.fragments)

intellij_dev_fragments_dist = rule(
    implementation = _compose_fragments_impl,
    attrs = {
        "composer": attr.label(executable = True, cfg = "exec", mandatory = True),
        "fragments": attr.label_list(providers = [IntellijDevFragmentInfo], mandatory = True),
        "expect_fragments": attr.string_list(
            doc = "The fragment names this distribution is supposed to be made of, stated independently of `fragments` so that a fragment missing from that list fails composition instead of thinning the IDE.",
        ),
        "additional_modules": attr.string_list(
            doc = "The plugin modules this distribution declares it contains, for `DevIdeConfig`. What the " +
                  "distribution was configured with, not what any one fragment assembled: a module the product " +
                  "bundles is packed by a shared plugin fragment, and a consumer asking for it can only find out " +
                  "from here.",
        ),
    } | _TRACE_SPANS_ATTR,
)
