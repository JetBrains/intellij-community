"""What a dev distribution contains, expressed as Bazel deps instead of a generated table of names.

A dev-dist fragment used to learn its inputs from `build/dev_dist_fragment_inputs.bzl` - a generated list of module and
library *names* that a repository rule re-derived into labels at loading time, with a fail-open "drop it with a warning"
contract for a name the model no longer had. The names were restating what the graph already knows: a plugin's content
is checked in beside the plugin.

These rules say the same thing as labels. `dev_dist_plugin_content` is the leaf - one per plugin, in the plugin's own
package - and `dev_dist_content_set` composes leaves and modules into whatever a product bundles; `deps` *is* the
nesting, so a shared set is a label reused rather than a name list copied. A wrong label is then an analysis error at
the label instead of a silently thinner distribution, and `bazel query 'deps(...)'` answers "what is in this fragment?".

Two shapes were measured and rejected before settling on a sidecar rule plus an aspect:

* Per-module *provider rules* - the "Four mechanisms at the boundary" section of `build/dev-build-architecture.md`
  rejects sidecars that duplicate a fact across every module. The aspect adds no targets and duplicates nothing.
* Membership as attributes on the plugin main module's own `jvm_library`, the way `content_module_jar` carries packing.
  It cannot work in either direction: 126 plugins have a content module that depends back on their main module
  through non-test JPS deps (372 direct edges - the split-mode `intellij.markdown.backend` -> `intellij.markdown`
  pattern), so the edge would be a target-graph cycle; and 275 content modules belong to two or more plugins (one to
  45), so membership is a property of the (plugin, module) *relation* and has no single module to live on.
"""

load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")

DevDistContentInfo = provider(
    doc = "The module and library jars one slice of a dev distribution is made of.",
    fields = {
        # Bare `File`s, because the only consumer - `intellij_dev_build_inputs` - keys a module jar by
        # `str(file.owner) + ".jar"` and needs nothing else from the target that produced it.
        "module_jars": "depset of File: the jars of the modules this content declares as its own members.",
        # Not bare `File`s, unlike the module halves: a library jar's manifest key is the declared label, which is not
        # derivable from the file. See `_collect_libraries`.
        "library_jars": "depset of struct(label, jar): library jars, `label` being the jar target's own label.",
        "prepacked_plugin_jars": "depset of struct(plugin_main_module, content_module, relative_output_file, jar).",
    },
)

_DevDistModuleInfo = provider(
    doc = "The aspect's per-target answer: the module jar this target stands for.",
    fields = {
        "own": """depset of File: the module jar this target *stands for*.

        Its own jar when it is a module; the `own` of what it re-exports when it is a pass-through wrapper around one
        (see `_EXPORT_ATTR`); empty when it is a library.""",
    },
)

# The attribute a target re-exports through, and therefore the one a target with no jar of its own can *stand for* a
# module through - and, since the dependency frontier was retired, the only attribute this aspect propagates over.
#
# `jvm_provided_library` is why this is needed. A production `scope="PROVIDED"` module dependency is not emitted as the
# dependency's own label: `BazelBuildFileGenerator.kt:1028-1042` wraps it in a `jvm_provided_library(name =
# "<seg>_provided", lib = <module>)`, whose rule (`community/build/jvm-rules/rules/provided-library.bzl`) re-exports the
# module through `exports` and provides a `KotlinInfo` carrying nothing but `exported_compiler_plugins` - no
# `module_name`, so `_module_jar` sees a library. There are 839 such wrappers across 352 generated `BUILD.bazel` files
# (479 files `load` the rule; 127 of them never call it), and the retired name-table path declared every one of the
# modules behind them:
# `community/build/jps_target_derivation.bzl:132-139` takes every `<orderEntry type="module">` whose scope is not
# `TEST`, PROVIDED included.
#
# The wrapper therefore has to be transparent: its `own` becomes what it re-exports, so a content target naming a
# `PROVIDED` module through its wrapper still declares that module's jar.
#
# Stated structurally rather than by rule kind, which is `_rule` for that macro (the private variable the rule object is
# assigned to) and would be both unreadable and ambiguous. Structurally it is also strictly safer: a real module always
# has a jar of its own, so the pass-through can only ever apply to a target that would otherwise contribute nothing,
# and it cannot deepen anything.
#
# `own` is either `[jar]` or the union of its `exports`' `own`, so the recursion terminates only at jar-bearing targets
# and every element of every `own` is some module's `all_output_jars[0]` - it can never widen into a closure. Today the
# only kind that passes anything through is the wrapper above (364 instances, fan-out 1, all wrapping
# `_jvm_library_jps`; `provided` holds only module labels, so a wrapper never wraps a wrapper). The 217 jarless
# `java_library` targets with `exports` in the closure export no modules at all, so their pass-through is a no-op.
_EXPORT_ATTR = "exports"

def _module_jar(target):
    """This target's distribution jar if it is a module, or None if it is a library.

    A module is told from a library by `KtJvmInfo.module_name`: `jvm_library` always sets it (`jvm-rules/rules/impl/
    associates.bzl:24-27,49-55` derives one from the label when the attribute is absent), while `jvm_import` builds a
    `KtJvmInfo` carrying only `exported_compiler_plugins` (`jvm-rules/rules/import.bzl:14-16`), so the field is unset
    and `hasattr` is False. A `java_library`/`java_import` library container provides no `KtJvmInfo` at all.

    The jar is `KtJvmInfo.all_output_jars[0]` and deliberately not `DefaultInfo.files`, which also carries
    `kotlin_cri_storage_file` (`jvm-rules/rules/library.bzl:17-21`) - a second file that is not a jar and must not
    reach a distribution.
    """
    if _KtJvmInfo not in target:
        return None
    info = target[_KtJvmInfo]
    if not hasattr(info, "module_name") or not info.module_name:
        return None
    if not hasattr(info, "all_output_jars") or not info.all_output_jars:
        fail("%s has a module name ('%s') but produced no output jar" % (target.label, info.module_name))
    return info.all_output_jars[0]

def _dev_dist_module_aspect_impl(target, ctx):
    exported = []

    # Defensively: the aspect is propagated over whatever the visited rule calls `exports`, and a rule may not have the
    # attribute at all, or may declare it as something other than a label list.
    exports = getattr(ctx.rule.attr, _EXPORT_ATTR, None)
    if type(exports) == "list":
        for dep in exports:
            if _DevDistModuleInfo in dep:
                exported.append(dep[_DevDistModuleInfo].own)

    jar = _module_jar(target)
    return [_DevDistModuleInfo(
        # A target with a jar of its own is that module and nothing else; one without is either a library, whose
        # `exports` are libraries too and contribute nothing, or a pass-through wrapper standing for the module it
        # re-exports - see `_EXPORT_ATTR`.
        own = depset([jar]) if jar != None else depset(transitive = exported),
    )]

_dev_dist_module_aspect = aspect(
    doc = "Reads the distribution jar a module target stands for.",
    implementation = _dev_dist_module_aspect_impl,
    attr_aspects = [_EXPORT_ATTR],
    provides = [_DevDistModuleInfo],
)

def _collect_modules(targets, module_jars):
    for target in targets:
        module_jars.append(target[_DevDistModuleInfo].own)

def _collect_libraries(ctx, library_jars):
    """Turn the declared library jar labels into `struct(label, jar)` entries.

    The label is taken from the *target*, not from `jar.owner`, and that is not a stylistic choice. A Maven library's
    jar target is the `copy_file` output `@lib//:<group>/<artifact>-<version>.jar` generated by `generateMavenLib`
    (`lib.kt:408-423`), so the file's owner is the copying rule `...jar_copy` - a different label. The label the
    distribution asks for is the one recorded as `jarTargets` in `build/bazel-targets.json` and resolved through
    `BazelBuildInputs.resolve` (`BazelModuleOutputProvider.kt:195`), which is exactly this target's label.
    """
    for target in ctx.attr.libraries:
        files = target[DefaultInfo].files.to_list()
        if len(files) != 1:
            fail("%s: library jar %s must provide exactly one file, got %s" % (ctx.label, target.label, files))
        library_jars.append(depset([struct(label = str(target.label), jar = files[0])]))

def _collect_prepacked(ctx, plugin_main_module, prepacked_plugin_jars):
    """Turn `prepacked_content_modules` into typed *(plugin, module, path, jar)* records.

    Shared by `dev_dist_plugin_content` and `dev_dist_content_set` because a plugin split across the repository boundary
    is declared in two places: the community half names what a community package can name, and the completion set in
    `//build/dev-dist-content` - the one package that sees both repositories - names the ultimate members. Both halves
    hand the collector the same kind of record, so nothing downstream can tell which side a jar came from.
    """
    for target, relative_output_file in ctx.attr.prepacked_content_modules.items():
        output_groups = target[OutputGroupInfo]
        if not hasattr(output_groups, "content_module_jar"):
            fail("%s: prepacked content module %s has no `content_module_jar` output" % (ctx.label, target.label))
        jars = output_groups.content_module_jar.to_list()
        if len(jars) != 1:
            fail("%s: prepacked content module %s must have exactly one `content_module_jar` output, got %s" % (
                ctx.label,
                target.label,
                jars,
            ))
        content_module = target[_KtJvmInfo].module_name
        if not content_module:
            fail("%s: prepacked content target %s has no module name" % (ctx.label, target.label))
        prepacked_plugin_jars.append(depset([struct(
            plugin_main_module = plugin_main_module,
            content_module = content_module,
            relative_output_file = relative_output_file,
            jar = jars[0],
        )]))

_PREPACKED_CONTENT_MODULES_ATTR = attr.label_keyed_string_dict(
    doc = "Content modules handed to their `content_module_jar` output, mapped to the path below plugin `lib/`.",
    providers = [_KtJvmInfo],
)

def _dev_dist_plugin_content_impl(ctx):
    module_jars = []
    library_jars = []
    prepacked_plugin_jars = []

    _collect_modules([ctx.attr.descriptor_module], module_jars)
    _collect_modules(ctx.attr.content_modules, module_jars)
    _collect_libraries(ctx, library_jars)

    _collect_prepacked(
        ctx,
        plugin_main_module = ctx.attr.descriptor_module[_KtJvmInfo].module_name,
        prepacked_plugin_jars = prepacked_plugin_jars,
    )

    return [DevDistContentInfo(
        module_jars = depset(transitive = module_jars),
        library_jars = depset(transitive = library_jars),
        prepacked_plugin_jars = depset(transitive = prepacked_plugin_jars),
    )]

dev_dist_plugin_content = rule(
    doc = """Which jars a dev distribution must have on hand to assemble one plugin.

    One target per plugin, in the plugin's own package, generated from the `plugin-content.yaml` checked in beside the
    plugin's main module - the fully resolved content report, so `xi:include` is already followed and the libraries need
    no descriptor archaeology. The fragment that owns the plugin deps on it, directly or through a
    `dev_dist_content_set`, and gets its declared inputs from the provider.

    The main module is a member alongside the content modules: it is `descriptor_module` only so that the plugin it
    describes is identified by the same target that carries its descriptor, and the distribution needs its jar as much
    as any other. `module_jars` therefore includes it.
    """,
    implementation = _dev_dist_plugin_content_impl,
    attrs = {
        "descriptor_module": attr.label(
            doc = "The plugin's main module - the one whose resources carry META-INF/plugin.xml.",
            aspects = [_dev_dist_module_aspect],
            providers = [_KtJvmInfo],
            mandatory = True,
        ),
        "content_modules": attr.label_list(
            doc = "The modules the plugin descriptor registers as `<content>`.",
            aspects = [_dev_dist_module_aspect],
            providers = [_KtJvmInfo],
        ),
        "libraries": attr.label_list(
            doc = "The plugin's library jars, as individual jar targets - see `_collect_libraries`.",
            allow_files = True,
        ),
        "prepacked_content_modules": _PREPACKED_CONTENT_MODULES_ATTR,
    },
)

def _dev_dist_content_set_impl(ctx):
    module_jars = []
    library_jars = []
    prepacked_plugin_jars = []

    for dep in ctx.attr.deps:
        info = dep[DevDistContentInfo]
        module_jars.append(info.module_jars)
        library_jars.append(info.library_jars)
        prepacked_plugin_jars.append(info.prepacked_plugin_jars)

    _collect_modules(ctx.attr.modules, module_jars)
    _collect_libraries(ctx, library_jars)

    # A relation needs the plugin it belongs to, and a set has no `descriptor_module` to read it from - so a set that
    # completes a plugin names it. Required together: a relation without a plugin has no key, and a plugin name with no
    # relation is a set that says it completes something and then does not.
    if bool(ctx.attr.prepacked_plugin_main_module) != bool(ctx.attr.prepacked_content_modules):
        fail("%s: `prepacked_plugin_main_module` and `prepacked_content_modules` must be set together" % ctx.label)
    if ctx.attr.prepacked_content_modules:
        _collect_prepacked(
            ctx,
            plugin_main_module = ctx.attr.prepacked_plugin_main_module,
            prepacked_plugin_jars = prepacked_plugin_jars,
        )

    return [DevDistContentInfo(
        module_jars = depset(transitive = module_jars),
        library_jars = depset(transitive = library_jars),
        prepacked_plugin_jars = depset(transitive = prepacked_plugin_jars),
    )]

dev_dist_content_set = rule(
    doc = """A composition of dev-distribution content: other content targets, plus what none of them covers.

    `deps` is the nesting and the sharing at once - a set written once is referenced by label from every product that
    bundles it, instead of its member names being copied into each product's generated payload. `modules` and
    `libraries` are the remainder a product states for itself.
    """,
    implementation = _dev_dist_content_set_impl,
    attrs = {
        "deps": attr.label_list(
            doc = "The content targets this one includes - plugin content, or other sets.",
            providers = [DevDistContentInfo],
        ),
        "modules": attr.label_list(
            doc = "Modules no dep covers.",
            aspects = [_dev_dist_module_aspect],
            providers = [_KtJvmInfo],
        ),
        "libraries": attr.label_list(
            doc = "Library jars no dep covers, as individual jar targets - see `_collect_libraries`.",
            allow_files = True,
        ),
        "prepacked_content_modules": _PREPACKED_CONTENT_MODULES_ATTR,
        "prepacked_plugin_main_module": attr.string(
            doc = """The plugin `prepacked_content_modules` belongs to, for a set that completes one across the repository split.

            `dev_dist_plugin_content` reads this from its `descriptor_module`; a set has no such attribute, and the
            module it would name is in the other repository for exactly the plugins that need this.""",
        ),
    },
)
