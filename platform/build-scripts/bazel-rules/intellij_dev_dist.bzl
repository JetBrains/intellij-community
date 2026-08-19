"""Builds independently cacheable fragments of a dev-mode IDE distribution.

A fragment names itself and the slice it owns; the assembler decides ownership from the layout it computed, so the
fragments of one distribution partition it exactly instead of following lists someone maintains. See
`org.jetbrains.intellij.build.dev.DevBuildFragment`.

Split assembly deliberately supports only builds with scrambling disabled. Platform co-scrambling and per-plugin
scrambling require both component layouts in one process.
"""

load("@community//build:project_model_manifest.bzl", "write_project_model_manifest")
load("//build:dev_launch_dependencies.bzl", "platform_parts")

IntellijDevBuildInputsInfo = provider(
    doc = "The exact Bazel inputs and label-to-path manifest made available to one dev-build fragment.",
    fields = {
        "files": "The input files named by the manifest.",
        "manifest": "The logical Bazel input label to execution path manifest.",
    },
)

def _dev_build_inputs_impl(ctx):
    entries = {}
    for target in ctx.attr.inputs:
        logical_key = str(target.label)
        files = target[DefaultInfo].files.to_list()
        if len(files) != 1:
            fail("%s: %s must provide exactly one file, got %s" % (ctx.label, target.label, files))
        file = files[0]
        previous = entries.get(logical_key)
        if previous == None:
            entries[logical_key] = file
        elif previous != file:
            fail("%s: logical input '%s' is provided by both %s and %s" % (
                ctx.label,
                logical_key,
                previous.owner,
                target.label,
            ))

    lines = []
    files = []
    for logical_key in sorted(entries.keys()):
        file = entries[logical_key]
        lines.append("%s\t%s" % (logical_key, file.path))
        files.append(file)
    manifest = ctx.actions.declare_file(ctx.label.name + ".bazel-inputs")
    ctx.actions.write(manifest, "\n".join(lines) + "\n")

    return [
        DefaultInfo(files = depset([manifest])),
        IntellijDevBuildInputsInfo(files = depset(files), manifest = manifest),
    ]

intellij_dev_build_inputs = rule(
    doc = "Groups a fragment's derived raw labels behind one typed, validated input boundary.",
    implementation = _dev_build_inputs_impl,
    attrs = {
        # Inputs are direct generated labels, never aliases: configured Target.label is the manifest key. The Kotlin
        # resolver adds apparent-repository aliases for canonical external-repository labels.
        "inputs": attr.label_list(allow_files = True),
    },
)

IntellijDevFragmentInfo = provider(
    fields = {
        "name": "The fragment name, which is also the `kind` of its manifest.",
        "home": "The fragment tree.",
        "manifest": "The fragment manifest.",
        "plugin_classpath_part": "This fragment's plugin-classpath records, or None if it built no plugin.",
        "plugin_classpath_prefix": "The plugin-classpath prefix, or None if another fragment produces it.",
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

def _project_model_tree_impl(ctx):
    tree = ctx.actions.declare_directory(ctx.label.name + ".tree")
    project_files = ctx.files.project_model_files + ctx.files.extra_project_files
    manifest = write_project_model_manifest(ctx, ctx.label.name + ".project.manifest", project_files, ctx.attr.mode)

    args = ctx.actions.args()
    args.add("--project-manifest=" + manifest.path)
    args.add("--output-dir=" + tree.path)
    ctx.actions.run(
        inputs = project_files + [manifest],
        outputs = [tree],
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
    },
)

# The selector values `DevDistMain` accepts, mirrored here so a typo in a BUILD file fails at analysis time.
_PLATFORM_SELECTORS = ["", "all", "core", "content-modules", "remaining-content-modules"]

_PLUGIN_SELECTORS = ["", "all", "named", "remaining"]

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
        args.add_all(ctx.attr.content_module_sets, format_each = "--content-module-set=%s")
        args.add_all(ctx.attr.claimed_content_module_sets, format_each = "--claimed-content-module-set=%s")
    if ctx.attr.platform_resources:
        args.add("--platform-resources")

    plugin_classpath_part = None
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

    plugin_classpath_prefix = None
    if ctx.attr.produces_plugin_classpath_prefix:
        plugin_classpath_prefix = ctx.actions.declare_file(ctx.label.name + ".plugin-classpath-prefix")
        args.add("--plugin-classpath-prefix=" + plugin_classpath_prefix.path)
        outputs.append(plugin_classpath_prefix)

    args.add_all(ctx.files.preloaded_manifests, format_each = "--preloaded-manifest=%s")
    args.add("--preloaded-only")

    ctx.actions.run(
        inputs = depset(
            direct = [
                project_tree,
                bazel_inputs_manifest,
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
        IntellijDevFragmentInfo(
            name = ctx.attr.fragment_name,
            home = home,
            manifest = component_manifest,
            plugin_classpath_part = plugin_classpath_part,
            plugin_classpath_prefix = plugin_classpath_prefix,
        ),
    ]

intellij_dev_fragment = rule(
    doc = """One independently cacheable slice of a dev distribution.

    What the fragment owns is a selector, not a file list: `platform` picks the `lib/` jars by what the plugin model
    says about them, `plugins` picks bundled plugin directories by main module, and the `remaining*` selectors are the
    exact complement of what their siblings claimed, so nothing is silently left out of the composition.
    """,
    implementation = _fragment_impl,
    attrs = {
        "assembler": attr.label(executable = True, cfg = "exec", mandatory = True),
        # Pinned so the fragments of one distribution agree and an assembly does not carry the wall clock into its
        # outputs: it dates archive entries and the `.SNAPSHOT` plugin version suffix, both of which would otherwise
        # differ between fragments assembled minutes apart. It is deliberately *not* the product build date - a dev
        # distribution stamps none, so that the IDE resolves its build time at startup and no EAP expiration period can
        # run out on a cached distribution (see `computeAppInfoXml`). It used to be a far-future date chosen to outrun
        # that period, which is what made every dev IDE start expired: a build date over a day ahead of the wall clock
        # is expired too.
        "build_date_seconds": attr.string(default = "1767225600"),  # 2026-01-01T00:00:00Z
        "mode": attr.string(default = "ultimate", values = ["community", "ultimate"]),
        "platform_prefix": attr.string(mandatory = True),
        "target_platform": attr.string(default = ""),
        "fragment_name": attr.string(mandatory = True, doc = "Identifies this fragment in its manifest, its mnemonic and the composer's completeness check."),
        "platform": attr.string(default = "", values = _PLATFORM_SELECTORS, doc = "Which `lib/` jars this fragment owns; empty means none."),
        "content_module_sets": attr.string_list(doc = "For platform = 'content-modules': the module sets whose content-module jars this fragment owns."),
        "claimed_content_module_sets": attr.string_list(doc = "For platform = 'remaining-content-modules': the module sets the sibling fragments own."),
        "platform_resources": attr.bool(default = False, doc = "Whether this fragment owns `bin`, the product metadata, the launchers and the copied product files."),
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
    },
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
            "components": [
                {
                    "root": fragment.home.path,
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
    ctx.actions.run(
        inputs = [composition_spec] + [file for fragment in fragments for file in [fragment.home, fragment.manifest]] + parts + prefixes,
        outputs = [home, ide_config, fingerprint],
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
        OutputGroupInfo(
            fingerprint = depset([fingerprint]),
            home = depset([home]),
            ide_config = depset([ide_config]),
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
    },
)
