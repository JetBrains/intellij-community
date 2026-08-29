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

* Per-module *provider rules* - the "The input boundary" section of `build/dev-build-architecture.md` rejects
  sidecars that duplicate a fact across every module. The aspect adds no targets and duplicates nothing.
* Membership as attributes on the plugin main module's own `jvm_library`, the way packing was carried before
  `content_module_jar` became a target of its own for reasons of its own.
  It cannot work in either direction: 126 plugins have a content module that depends back on their main module
  through non-test JPS deps (372 direct edges - the split-mode `intellij.markdown.backend` -> `intellij.markdown`
  pattern), so the edge would be a target-graph cycle; and 275 content modules belong to two or more plugins (one to
  45), so membership is a property of the (plugin, module) *relation* and has no single module to live on.
"""

load("@rules_java//java:defs.bzl", "JavaInfo")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load(":content_module_jar.bzl", "ContentModuleJarInfo")

DevDistContentInfo = provider(
    doc = "The module and library jars one slice of a dev distribution is made of.",
    fields = {
        # Bare `File`s, because the only consumer - `intellij_dev_build_inputs` - keys a module jar by
        # `str(file.owner) + ".jar"` and needs nothing else from the target that produced it.
        "module_jars": "depset of File: the jars of the modules this content declares as its own members.",
        # Not bare `File`s, unlike the module halves: a library's manifest key is the container target's label, which is
        # not derivable from the files. See `_collect_libraries`.
        "library_jars": "depset of struct(label, jars): `label` being the container target's own label.",
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

def _library_entry(target, ctx_label_owner):
    """One `struct(label, jars)` for a library container, deduped first-wins within the container.

    The same expansion as `_collect_libraries` and as the packer's own `_library_jars`
    (`jvm-rules/rules/impl/content-module-jar.bzl`): `transitive_runtime_jars` is the only `JavaInfo` set correct for all
    three shapes the library generator emits, and the label is the container's so that it carries no artifact version.
    """
    jars = []
    seen = {}
    for jar in target[JavaInfo].transitive_runtime_jars.to_list():
        if jar.path not in seen:
            seen[jar.path] = True
            jars.append(jar)
    if not jars:
        fail(
            "%s: library container %s contributes no runtime jars, so it would merge nothing. " % (ctx_label_owner, target.label) +
            "A `-provided` target is `neverlink` and never will - name the library itself.",
        )

    # A tuple, not the list: a depset element must be immutable, and a struct holding a list is not.
    return struct(label = str(target.label), jars = tuple(jars))

def _dev_dist_module_aspect_impl(target, ctx):
    jar = _module_jar(target)
    if jar != None:
        return [_DevDistModuleInfo(own = depset([jar]))]

    # A target with no jar of its own is either a library, whose `exports` are libraries too and contribute nothing, or a
    # pass-through wrapper standing for the module it re-exports - see `_EXPORT_ATTR`.
    #
    # Defensively: the aspect is propagated over whatever the visited rule calls `exports`, and a rule may not have the
    # attribute at all, or may declare it as something other than a label list.
    exported = []
    exports = getattr(ctx.rule.attr, _EXPORT_ATTR, None)
    if type(exports) == "list":
        for dep in exports:
            if _DevDistModuleInfo in dep:
                exported.append(dep[_DevDistModuleInfo].own)

    return [_DevDistModuleInfo(own = depset(transitive = exported))]

_dev_dist_module_aspect = aspect(
    doc = "Reads the distribution jar a module target stands for.",
    implementation = _dev_dist_module_aspect_impl,
    attr_aspects = [_EXPORT_ATTR],
    provides = [_DevDistModuleInfo],
)

DevDistPlatformPayloadInfo = provider(
    doc = "What a product's `lib/`-owning payload contains, split by which producer packs each jar.",
    fields = {
        "packed_jars": "depset of File: the `lib/<module>.jar`s a `content_module_jar` target packed.",
        "packed_jar_names": "list of string: their `lib/` file names, sorted - the jar-name exclusion set.",
        "declared_modules": """depset of string: the payload modules whose inputs a fragment still declares.

        The payload minus everything a packed jar already holds, plus the dependency closure of the seeds that survive
        that subtraction. `intellij_dev_build_inputs` keeps an `owned_inputs` entry when any module that contributed it
        is in here, which is what removes a handed-over module's jar, its libraries and its dependencies at once.""",
    },
)

def _declared_modules(ctx, packed_members):
    """The payload modules a fragment still declares, and the closure of the seeds among them.

    A faithful move of one loop from fetch time to analysis time. A repository rule used to prune the payload with a
    checked-in table of packed module names before walking `modules_with_dependencies`, and the walk is the reason the
    table could not simply be dropped: a handed-over module's *dependencies* are not declared either, because the module
    itself is not, and by the time a payload has been flattened into labels nothing remembers which module asked for
    which input. So the walk happens here, where the packing answer is a provider.

    Measured 2026-08-22 for `idea`: 155 of the 156 seeds are packed - `intellij.platform.core` is the one that is not -
    so declaring every seed's closure would take `platform_lib` from 83 declared inputs to 430.
    """
    declared = {name: True for name in ctx.attr.modules_by_name if name not in packed_members}

    frontier = [name for name in ctx.attr.seeds if name not in packed_members]
    reached = {}
    for _ in range(len(ctx.attr.module_deps) + 1):
        if not frontier:
            break
        next_frontier = []
        for name in frontier:
            if name in reached:
                continue
            reached[name] = True
            deps = ctx.attr.module_deps.get(name, "")
            if deps:
                next_frontier.extend(deps.split(" "))
        frontier = next_frontier
    if frontier:
        fail("%s: the module dependency closure did not settle" % ctx.label)

    for name in reached.keys():
        if name not in packed_members:
            declared[name] = True
    return declared

def _dev_dist_platform_payload_impl(ctx):
    packed_jars = []
    packed_member_jars = []
    packed_member_names = []
    packed_library_jars = []
    for target in ctx.attr.packed:
        info = target[ContentModuleJarInfo]
        packed_jars.append(info.jar)
        packed_member_jars.extend(info.member_jars)
        packed_member_names.extend(info.member_modules)
        packed_library_jars.extend(info.library_jars)

    packed = depset(packed_jars)
    owner_by_name = {}
    for jar in packed.to_list():
        previous = owner_by_name.get(jar.basename)
        if previous != None and previous != jar:
            # Two producers for one `lib/` path. `mergeDevBuildComponent` would catch it during a compose, but only as a
            # colliding path; here the two owning modules can still be named.
            fail("%s: %s is packed by both %s and %s" % (ctx.label, jar.basename, previous.owner, jar.owner))
        owner_by_name[jar.basename] = jar

    if not owner_by_name:
        fail("%s: no module in this payload packs a `lib/` jar, which cannot be right for a platform payload" % ctx.label)

    packed_members = {name: True for name in packed_member_names}

    return [
        DevDistPlatformPayloadInfo(
            packed_jars = packed,
            packed_jar_names = sorted(owner_by_name.keys()),
            declared_modules = depset(_declared_modules(ctx, packed_members).keys()),
        ),
        # The reference target's whole declaration: it packs the handed-over jars the `JarPackager` way, so what it reads
        # is exactly what is inside them - the member module jars and the libraries merged into them. Ordinary content,
        # so it arrives through the same boundary every plugin fragment uses rather than through a second mechanism.
        DevDistContentInfo(
            module_jars = depset(packed_member_jars),
            library_jars = depset(packed_library_jars),
            prepacked_plugin_jars = depset(),
        ),
    ]

dev_dist_platform_payload = rule(
    doc = """The payload of the fragment that owns `lib/`, and which of its jars another producer already packed.

    This is the one intersection that decides jar ownership within `lib/`, and it is a **question asked of the graph**.
    It used to be a set intersection at *fetch* time: `jpsModelToBazel` wrote every module that packs a jar to a
    generated `build/dev_dist_content_module_jars.bzl` - 2 524 names, 18 of which said anything the module's own
    `jvm_library` did not already say - and the repository rule intersected that table with the payload, because a
    repository rule cannot see providers. The table was checked in, so every branch that added or renamed a platform
    module rewrote a line of it.

    Nothing needs to be told any more. The payload arrives whole and unfiltered, `packed` names the packing targets that
    stand beside its modules, and everything the intersection used to produce comes out of one provider so the answers
    cannot disagree:

    * `packed_jars` go to `intellij_dev_packed_jars_component`, which composes them in;
    * `packed_jar_names` go to the owning fragment as the jars it must **not** pack, and to the reference target as the
      jars it packs and nothing else;
    * `declared_modules` is what the owning fragment still declares - see `_declared_modules`;
    * `DevDistContentInfo` is the other side of the same split, and is the reference target's whole declaration.

    A stale set is no longer a thing that can happen: a module that stops packing a jar stops appearing here in the same
    analysis that stops producing it.
    """,
    implementation = _dev_dist_platform_payload_impl,
    attrs = {
        "modules": attr.label_list(
            doc = "The payload's own modules, as their `jvm_library` targets - the dependency edge that makes this " +
                  "target stand for the platform this product assembles.",
            providers = [_KtJvmInfo],
            mandatory = True,
        ),
        "packed": attr.label_list(
            doc = "The `content_module_jar` targets of those payload modules that own a `lib/` jar. One per jar - a " +
                  "module that packs none has no such target, so this list *is* the handover set.",
            providers = [ContentModuleJarInfo],
            mandatory = True,
        ),
        "modules_by_name": attr.string_list(
            doc = "The same modules by JPS module name, which is the key `declared_modules` and `owned_inputs` share.",
            mandatory = True,
        ),
        # Names, not targets. Whether a jar is handed over is decided over `modules` alone - a module the payload reaches
        # only through a dependency is not part of the platform this product assembles, and the fetch-time intersection
        # did not hand its jar over either - so the closure is walked to find out what a surviving seed still needs, and
        # for nothing else. `owned_inputs` already carries these modules' inputs keyed by the same names.
        "seeds": attr.string_list(
            doc = "The payload modules declared with their dependencies - `modules_with_dependencies` in the plan.",
        ),
        "module_deps": attr.string_dict(
            doc = "Module name to its direct production module dependencies, space separated, over the closure above.",
        ),
    },
)

def _collect_modules(targets, module_jars):
    for target in targets:
        module_jars.append(target[_DevDistModuleInfo].own)

def _collect_libraries(ctx, library_jars):
    """Turn the declared library *container* labels into `struct(label, jars)` entries.

    The label is the container target's - the `jvm_import`, `java_library` or `java_import` that groups a library's jars
    and that a module already names in its `deps`/`runtime_deps`/`exports` - and the jars are what its `JavaInfo` holds.
    Not the individual jar targets, and that is the point: a Maven library's per-jar target is the `copy_file` output
    `@lib//:<group>/<artifact>-<version>.jar` (`generateMavenLib`, `lib.kt:408-423`), so **its label carries the
    version** and a version bump rewrote every plugin `BUILD.bazel` that named it. The container's label does not
    (`libraryTargetLabel`, `lib.kt:336-344`), so a bump now rewrites only the library's own package. It is also exactly
    what `build/bazel-targets.json` already records as `LibraryDescription.target`, which is the key
    `BazelModuleOutputProvider.findLibraryRoots` asks for.

    `transitive_runtime_jars` for the same reason `content_module_jar`'s own `libraries` uses it
    (`content_module_jar.bzl`, `_library_entries`): it is the only `JavaInfo` set correct for all three shapes
    the library generator emits. Measured on real containers, the alternatives are not - `full_compile_jars` adds the
    container's own empty output jar, and its *position* is not even stable (before the real jar for
    `@lib//:studio-platform-provided`, after it for `@lib//:kotlinc-kotlin-compiler-fe10-provided`), while
    `compile_jars` hands back an **ijar** for a local `java_import` library.

    Order is load-bearing and preserved: the packer resolves an entry offered by several sources to the first, and a
    multi-jar container's `exports` carry a `# do not sort` comment for that reason. Dedup is first-wins, matching
    `content_module_jar`'s `libraries`.
    """
    for target in ctx.attr.libraries:
        if JavaInfo not in target:
            fail("%s: library container %s provides no JavaInfo" % (ctx.label, target.label))

        # A `neverlink` `-provided` wrapper has no runtime jars and never will, and `_library_entry` refuses one. The
        # generator never emits one here - `libraryTargetLabel` returns the plain container and `libraryDependencyLabel`
        # is what adds the suffix - so that fires only on a hand-written entry, and naming the library itself is the fix.
        # Do not reach for another `JavaInfo` set to paper over it: for a local library behind such a wrapper,
        # `compile_jars` and `transitive_compile_time_jars` both yield the *interface* jar.
        library_jars.append(depset([_library_entry(target, ctx.label)]))

    # Every plugin needs it, and no plugin says anything by naming it. The Bazel converter puts `@lib//:kotlin-stdlib`
    # into a module's `runtime_deps` implicitly and JPS declares it for nobody, so whether it reached a plugin's
    # `libraries` was decided by whether some member's JPS model or the layout report happened to mention it - true for
    # 250 of 408 content targets and false for the rest, describing nothing about either group. It is a fact of the
    # toolchain, so it lives on the rule. Costs no manifest entry either: the key is the container label, so a fragment
    # that already had it gains nothing to resolve.
    library_jars.append(depset([_library_entry(ctx.attr._kotlin_stdlib, ctx.label)]))

_KOTLIN_STDLIB_ATTR = attr.label(
    doc = "The Kotlin standard library, declared for every plugin - see `_collect_libraries`.",
    default = "@lib//:kotlin-stdlib",
    providers = [[JavaInfo]],
)

def _collect_library_jars(ctx, library_jars):
    """The per-jar half, for labels a *repository rule* produced rather than a generator writing a BUILD file.

    Only test plugins use this. They are outside the content population, so the dynamic JPS-to-Bazel bridge resolves
    their library *names* into labels while loading (`DEV_DIST_ON_DEMAND_PLUGIN_LIBRARY_TARGETS`), and what it derives from
    a library XML is the jar file labels - `jps_library_derivation.bzl` mirrors `makeJarTarget`, not
    `libraryTargetLabel`, and a container's target name comes from the library's *name* rather than from its jars.

    Keeping these per-jar is deliberate rather than a gap. The reason `libraries` names containers is that a per-jar
    label carries the artifact version and is **checked into a plugin's `BUILD.bazel`**, so a Maven bump rewrote hundreds
    of files. These labels are checked into nothing: the bridge regenerates them from the library XML on every load, so
    a version bump rewrites them for free and there is no churn to remove. Mirroring the container derivation in
    Starlark would buy nothing and add a second place for the two to disagree.

    So the key here stays the jar target's own label, exactly as `libraries` used to work: one file per key.
    """
    for target in ctx.attr.library_jars:
        files = target[DefaultInfo].files.to_list()
        if len(files) != 1:
            fail("%s: library jar %s must provide exactly one file, got %s" % (ctx.label, target.label, files))
        library_jars.append(depset([struct(label = str(target.label), jars = (files[0],))]))

def _conventional_prepacked_path(content_module):
    """Where a plugin puts a handed-off content module's jar unless its report says otherwise.

    `simplePluginContentEntry` in `contentModuleJar.kt` accepts a plugin entry at `lib/modules/<module>.jar` or at
    `lib/<module>.jar`. The second destination belongs to an `embedded` content module that gets a jar of its own. The
    first is 2 262 of the 2 463 accepted entries, so it stays derived and the other is declared per relation. See
    `prepacked_jars`.
    """
    return "modules/" + content_module + ".jar"

def _collect_prepacked(label, plugin_main_module, prepacked_content_modules, prepacked_jars, prepacked_plugin_jars):
    """Turn the two prepacked attributes into typed *(plugin, module, path, jar)* records.

    Shared by `dev_dist_plugin_content` and `dev_dist_content_set` because a plugin split across the repository boundary
    is declared in two places: the community half names what a community package can name, and the completion set in
    `//build/dev-dist-content` - the one package that sees both repositories - names the ultimate members. Both halves
    hand the collector the same kind of record, so nothing downstream can tell which side a jar came from.

    The destination travels on the *relation* and not on the packed jar, because one packed jar reaches two of them. 14
    candidate modules are placed under `lib/modules/` by one plugin and directly in `lib/` by another. Both destinations
    copy the same bytes.

    The conventional destination stays derived for the same reason it always was. Declaring it as well would put one
    copy of one rule into each of the 2 132 conventional relations that are checked in. It would also make a *generated*
    path the thing `JarPackager.validatePrepackedPluginContentHandoff` compares the layout against. That check keeps its
    value either way, because it asserts that the layout put the jar where the relation says it goes.

    Args:
        label: the target being analyzed, for a failure message.
        plugin_main_module: the plugin these relations belong to.
        prepacked_content_modules: targets whose jar goes to the conventional path.
        prepacked_jars: target to `lib/`-relative path, for a plugin that places the jar somewhere else.
        prepacked_plugin_jars: the list of depsets to append the records to.
    """
    for target in prepacked_content_modules:
        info = target[ContentModuleJarInfo]
        prepacked_plugin_jars.append(depset([struct(
            plugin_main_module = plugin_main_module,
            content_module = info.module_name,
            relative_output_file = _conventional_prepacked_path(info.module_name),
            jar = info.jar,
        )]))

    for target, relative_output_file in prepacked_jars.items():
        info = target[ContentModuleJarInfo]
        if relative_output_file == _conventional_prepacked_path(info.module_name):
            # One way to say one thing. A relation that restates the convention would be a checked-in copy of a derived
            # rule, and a later change to the rule would then reach some relations and not others.
            fail("%s: %s is the conventional path of %s, so name the target in `prepacked_content_modules` instead" %
                 (label, relative_output_file, info.module_name))

        # The only destination the report shape accepts besides the derived one, so it is the only one worth declaring.
        # A path naming another module would place a jar the layout does not want there, and
        # `JarPackager.validatePrepackedPluginContentHandoff` would find it a whole distribution build later. This also
        # refuses a path that leaves the plugin, which `PrepackedPluginContentCollector` can only report by module name.
        if relative_output_file != info.module_name + ".jar":
            fail("%s: '%s' is not the own jar name of %s" % (label, relative_output_file, info.module_name))
        prepacked_plugin_jars.append(depset([struct(
            plugin_main_module = plugin_main_module,
            content_module = info.module_name,
            relative_output_file = relative_output_file,
            jar = info.jar,
        )]))

# The provider gate is the whole check. It used to be `_KtJvmInfo` plus three `fail`s in `_collect_prepacked` - "has no
# `content_module_jar` output", "must have exactly one", "has no module name" - because the label named a module and the
# jar was an output group that might or might not be there. The label names the packing target now, so a module that
# packs no jar has no label to name here and Bazel refuses the attribute before any of those could fire.
_PREPACKED_CONTENT_MODULES_ATTR = attr.label_list(
    doc = "Content modules handed to their own `content_module_jar` target instead of being packed by the fragment.",
    providers = [ContentModuleJarInfo],
)

_PREPACKED_JARS_ATTR = attr.label_keyed_string_dict(
    doc = """The same hand-off, for a content module this plugin does not place at the conventional path.

The key is the module's `content_module_jar` target and the value is where this plugin puts the jar, relative to the
plugin's `lib/`. Declared rather than derived because the path belongs to the *relation*: one packed jar is placed under
`lib/modules/` by one plugin and directly in `lib/` by another. Naming the conventional path here is refused - see
`_collect_prepacked`.""",
    providers = [ContentModuleJarInfo],
)

def _dev_dist_plugin_content_impl(ctx):
    module_jars = []
    library_jars = []
    prepacked_plugin_jars = []

    _collect_modules([ctx.attr.descriptor_module], module_jars)
    _collect_modules(ctx.attr.content_modules, module_jars)
    _collect_libraries(ctx, library_jars)

    _collect_prepacked(
        label = ctx.label,
        plugin_main_module = ctx.attr.descriptor_module[_KtJvmInfo].module_name,
        prepacked_content_modules = ctx.attr.prepacked_content_modules,
        prepacked_jars = ctx.attr.prepacked_jars,
        prepacked_plugin_jars = prepacked_plugin_jars,
    )

    return [DevDistContentInfo(
        module_jars = depset(transitive = module_jars),
        library_jars = depset(transitive = library_jars),
        prepacked_plugin_jars = depset(transitive = prepacked_plugin_jars),
    )]

_dev_dist_plugin_content = rule(
    doc = """Which jars a dev distribution must have on hand to assemble one plugin.

    One target per plugin, in the plugin's own package, generated from the project model: the plugin's own `<content>`
    with every `xi:include` followed, plus the `dev-dist.yaml` residue beside the plugin for what the model cannot
    reach. The fragment that owns the plugin deps on it, directly or through a `dev_dist_content_set`, and gets its
    declared inputs from the provider.

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
            doc = "The plugin's libraries, as the container targets that group their jars - see `_collect_libraries`.",
            providers = [[JavaInfo]],
        ),
        "prepacked_content_modules": _PREPACKED_CONTENT_MODULES_ATTR,
        "prepacked_jars": _PREPACKED_JARS_ATTR,
        "_kotlin_stdlib": _KOTLIN_STDLIB_ATTR,
    },
)

def dev_dist_plugin_content(descriptor_module, name = None, visibility = ["//visibility:public"], **kwargs):
    """`_dev_dist_plugin_content` with the two attributes that are the same for every plugin filled in.

    A content target is named after the module that carries the plugin descriptor, and every one of them is public
    because the fragment that packs the plugin is in another package. Both were generated into 408 checked-in
    `BUILD.bazel` files, 816 lines that said the same thing every time.
    """
    _dev_dist_plugin_content(
        name = name if name else descriptor_module.lstrip(":") + "_dev_content",
        descriptor_module = descriptor_module,
        visibility = visibility,
        **kwargs
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
    _collect_library_jars(ctx, library_jars)

    # A relation needs the plugin it belongs to, and a set has no `descriptor_module` to read it from - so a set that
    # completes a plugin names it. Required together: a relation without a plugin has no key, and a plugin name with no
    # relation is a set that says it completes something and then does not.
    if bool(ctx.attr.prepacked_plugin_main_module) != bool(ctx.attr.prepacked_content_modules):
        fail("%s: `prepacked_plugin_main_module` and `prepacked_content_modules` must be set together" % ctx.label)
    if ctx.attr.prepacked_content_modules:
        # No `prepacked_jars` here. A completion carries a member of the *other* repository, and the converter records
        # such a member only when its plugin places the jar at the conventional path - `computePluginContent` in
        # `pluginContent.kt` keeps the rest raw, with a warning. No report in this repository needs the other case
        # today, so the attribute would be one nothing writes.
        _collect_prepacked(
            label = ctx.label,
            plugin_main_module = ctx.attr.prepacked_plugin_main_module,
            prepacked_content_modules = ctx.attr.prepacked_content_modules,
            prepacked_jars = {},
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
            doc = "Libraries no dep covers, as the container targets that group their jars - see `_collect_libraries`.",
            providers = [[JavaInfo]],
        ),
        "library_jars": attr.label_list(
            doc = "Individual library jar files, for the test-plugin payload only - see `_collect_library_jars`.",
            allow_files = True,
        ),
        "prepacked_content_modules": _PREPACKED_CONTENT_MODULES_ATTR,
        "_kotlin_stdlib": _KOTLIN_STDLIB_ATTR,
        "prepacked_plugin_main_module": attr.string(
            doc = """The plugin `prepacked_content_modules` belongs to, for a set that completes one across the repository split.

            `dev_dist_plugin_content` reads this from its `descriptor_module`; a set has no such attribute, and the
            module it would name is in the other repository for exactly the plugins that need this.""",
        ),
    },
)
