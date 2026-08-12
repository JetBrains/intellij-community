"""Assembles a dev-mode IDE distribution as a Bazel output, instead of at launch time.

A dev launch is build-and-run in one JVM today: every `bazel run` of an `intellij_dev_binary` recomputes the
whole layout before the IDE's `main` is reached, whether or not anything changed. The ingredients are already
Bazel inputs - module jars through `bazel-targets.json`, external archives through `preloaded_download_repos` -
so what is left is to make the assembled home an output too, and let the action cache decide when to redo it.

The assembler is `org.jetbrains.intellij.build.devServer.DevDistMain`, run as an ordinary `java_binary`. Three
consequences of that shape are worth knowing before changing anything here:

* **The jars must be runfiles of the assembler, not inputs of this action.** The `rules_java` stub exports
  `JAVA_RUNFILES` even when it is invoked from an action, so `BazelRunfiles.isRunningFromBazel` is true inside
  and every module and library jar is resolved by label through the runfiles tree
  (`BazelModuleOutputProvider`), as is `bazel-targets.json` (`ArchivedCompilationContextUtil`). Action inputs
  are in no runfiles tree. Hence the [assembler] attribute, and the ~14,000 jars hanging off that binary's
  `data`.
* **...which is also why it is `cfg = "target"`.** An exec transition would rebuild every one of those jars
  into a second output directory.
* **Everything else is a plain input.** The project model arrives as a manifest the assembler materializes
  into a checkout-shaped tree, and the preloaded archives are named by absolute manifest path, which
  `PreloadedDownloads` accepts verbatim. Neither needs to be a runfile, so neither forces the assembler to be
  per product - one binary per repository serves every product in it, and what makes a product is the
  arguments below.
"""

load("@community//build:project_model_manifest.bzl", "write_project_model_manifest")

IntellijDevDistInfo = provider(
    doc = "An assembled dev-mode IDE distribution.",
    fields = {
        "home": "The IDE home directory, as a tree artifact.",
        "ide_config": "The `PreBuiltDevMain` config file naming that home and the IDE's main class.",
    },
)

def _intellij_dev_dist_impl(ctx):
    home = ctx.actions.declare_directory(ctx.label.name + ".dist")
    ide_config = ctx.actions.declare_file(ctx.label.name + ".ide.config")

    project_files = ctx.files.project_model_files + ctx.files.extra_project_files
    manifest = write_project_model_manifest(ctx, ctx.label.name + ".project.manifest", project_files, ctx.attr.mode)

    args = ctx.actions.args()
    args.add("--project-manifest=" + manifest.path)
    args.add("--output-dir=" + home.path)
    args.add("--ide-config=" + ide_config.path)

    # Build scratch is not part of the distribution: `temp` alone is ~200 MB, and a tree artifact holding it
    # would make Bazel hash 200 MB of intermediates. An undeclared sibling in `bazel-out` is the right home for
    # it - the assembler clears it at the start of every build, and `bazel clean` disposes of it.
    args.add("--scratch-dir=" + home.path + ".scratch")
    args.add("--platform-prefix=" + ctx.attr.platform_prefix)
    if ctx.attr.target_os:
        args.add("--os=" + ctx.attr.target_os)
    args.add_all(ctx.attr.additional_modules, format_each = "--additional-module=%s")
    args.add_all(ctx.files.preloaded_manifests, format_each = "--preloaded-manifest=%s")
    if ctx.attr.preloaded_only:
        args.add("--preloaded-only")
    if ctx.attr.pack_test_sources:
        args.add("--pack-test-sources")

    ctx.actions.run(
        inputs = depset(project_files + [manifest] + ctx.files.preloaded_downloads + ctx.files.preloaded_manifests),
        outputs = [home, ide_config],
        executable = ctx.executable.assembler,
        arguments = [args],
        # `local` covers sandboxing and remote execution; `no-remote-cache` is spelled out because the
        # distribution is ~4 GB per product and this repository configures a shared remote cache by default.
        execution_requirements = {"local": "1", "no-remote-cache": "1"},
        mnemonic = "IntellijDevDist",
        progress_message = "Assembling %s dev distribution %s" % (ctx.attr.platform_prefix, ctx.label),
    )

    return [
        DefaultInfo(files = depset([home, ide_config])),
        IntellijDevDistInfo(home = home, ide_config = ide_config),
        # A launcher needs the config file's runfiles path, and `$(rlocationpath ...)` takes a label naming exactly one
        # file - which this target is not, and which a `declare_file` output cannot be. These groups are how a
        # `filegroup(output_group = ...)` gets a single-file label to hand it.
        OutputGroupInfo(ide_config = depset([ide_config]), home = depset([home])),
    ]

intellij_dev_dist = rule(
    doc = "Assembles a dev-mode IDE distribution into a tree artifact, plus the config file that launches it.",
    implementation = _intellij_dev_dist_impl,
    attrs = {
        "assembler": attr.label(
            doc = "The `DevDistMain` binary carrying this repository's module and library jars as runfiles. " +
                  "`cfg = \"target\"` on purpose - see the module docstring.",
            executable = True,
            cfg = "target",
            mandatory = True,
        ),
        "mode": attr.string(
            doc = "Which repository root the materialized project tree is shaped as.",
            default = "ultimate",
            values = ["community", "ultimate"],
        ),
        "platform_prefix": attr.string(
            doc = "Selects the product, as `-Didea.platform.prefix` does for a dev launch (e.g. 'idea', 'GoLand').",
            mandatory = True,
        ),
        "target_os": attr.string(
            doc = "The OS the distribution is for. Empty means the host's, which is what a dev launch wants. " +
                  "Naming another one cross-assembles: the assembler already takes `--os`, and the per-platform " +
                  "archive repositories are declared for every platform rather than only the host's, so the " +
                  "caller has only to hand [preloaded_downloads] that platform's set instead of a host `select`.",
            default = "",
            values = ["", "linux", "macos", "windows"],
        ),
        "additional_modules": attr.string_list(
            doc = "Plugin modules included on top of the product's own, as `-Dadditional.modules` does for a dev launch.",
            default = [],
        ),
        "project_model_files": attr.label_list(
            doc = "The JPS project model: every `.iml`, every plugin descriptor, and the `.idea` root files.",
            allow_files = True,
            mandatory = True,
        ),
        "extra_project_files": attr.label_list(
            doc = "Checkout files the build reads that the project model does not name - `dev-build.json`, " +
                  "`build.txt`, `idea.properties`, the dependency properties, `OpenedPackages.txt`. A product " +
                  "whose `copyAdditionalFiles` reaches for something else adds it here, and the assembler fails " +
                  "loudly rather than producing a thinner distribution.",
            allow_files = True,
            default = [],
        ),
        "preloaded_downloads": attr.label_list(
            doc = "Archives the assembly would otherwise download, already fetched.",
            allow_files = True,
            default = [],
        ),
        "preloaded_manifests": attr.label_list(
            doc = "The manifests describing [preloaded_downloads], one per fetching repository.",
            allow_files = True,
            default = [],
        ),
        "preloaded_only": attr.bool(
            doc = "Makes [preloaded_manifests] the complete inventory, so an undeclared URL fails instead of " +
                  "reaching the network. Set from a `select` over the platforms where the set was measured.",
            default = False,
        ),
        "pack_test_sources": attr.bool(
            doc = "Lets [additional_modules] name a plugin whose content is test compilation output - a lambda test " +
                  "plugin, a fixture plugin. Such a distribution needs an [assembler] carrying the test jars as " +
                  "runfiles too, which is a different binary: putting them on the shared one would make every " +
                  "product distribution compile the repository's test targets.",
            default = False,
        ),
    },
)
