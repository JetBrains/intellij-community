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
* Membership as attributes on the plugin main module's own `jvm_library`, the way `content_module_jar` carries packing.
  It cannot work in either direction: 126 plugins have a content module that depends back on their main module
  through non-test JPS deps (372 direct edges - the split-mode `intellij.markdown.backend` -> `intellij.markdown`
  pattern), so the edge would be a target-graph cycle; and 275 content modules belong to two or more plugins (one to
  45), so membership is a property of the (plugin, module) *relation* and has no single module to live on.
"""

load("@rules_java//java:defs.bzl", "JavaInfo")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")

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
        "packed_jars": """depset of File: the `lib/<module>.jar` this target packs itself, or empty.

        `jvm_library` packs it as an extra output when the module's checked-in `module-content.yaml` says it owns a
        self-named distribution jar, and exposes it in the `content_module_jar` output group. Whether a module owns one
        is therefore a fact of the graph, and this is how a dev distribution reads it instead of intersecting a
        generated name table.""",
        "packed_member_jars": """depset of File: the module jars whose bytes are inside `packed_jars`.

        The owner's own jar plus the own jar of every module merged into it - `content_module_jar_modules_before` and
        `_after`. A fragment that hands the packed jar over must stop declaring all of them: their output is in that jar
        and nowhere else. Empty whenever `packed_jars` is.""",
        "packed_member_modules": """depset of string: the same members by JPS module name.

        The names, because that is what a payload is written in: the fragment's declared inputs are pruned by asking
        which module contributed each one, and a module name is the one key that means the same thing on both sides of
        the loading/analysis boundary - a label does not, since a repository rule writes `@community//...` where an
        analysis-time `Label` reads back canonically.""",
        "packed_library_jars": """depset of struct(label, jars): the libraries merged into `packed_jars`.

        `content_module_jar_libraries`, expanded to the container's jars the way the packer expands it. Only the
        reference target needs them - it packs the same jars the `JarPackager` way - so they are the second half of what
        that target declares, beside `packed_member_jars`. Empty whenever `packed_jars` is.""",
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

def _packed_content_module_jar(target):
    """The `lib/<module>.jar` this target packs as an extra output of its own, or None.

    The presence of the `content_module_jar` output group *is* the answer: `content_module_jar_action`
    (`jvm-rules/rules/impl/content-module-jar.bzl`) registers the packing action only when the generator set
    `content_module_jar = True` from the module's recipe, and `library.bzl` adds the output group only when it did. So
    nothing has to be told which modules pack a jar - asking the target is asking the recipe.

    Deliberately not `DefaultInfo`: building a module must not pack its distribution jar, which is why the jar lives in
    an output group in the first place.

    An **empty** group is "packs nothing", not an error. `jvm_library` never writes one - `library.bzl` adds the group
    only when the action produced a jar - but this aspect visits whatever a payload names, and a group that exists and
    holds nothing says the same thing as no group at all. `_collect_prepacked` is the opposite case and is right to
    fail: there the relation was declared by hand, so an empty group means the jar it promised does not exist.
    """
    if OutputGroupInfo not in target:
        return None
    output_groups = target[OutputGroupInfo]
    if not hasattr(output_groups, "content_module_jar"):
        return None
    jars = output_groups.content_module_jar.to_list()
    if not jars:
        return None
    if len(jars) != 1:
        fail("%s must have at most one `content_module_jar` output, got %s" % (target.label, jars))
    return jars[0]

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

def _merged_library_jars(target, ctx):
    """The libraries merged into this target's packed jar, as declarable container entries.

    Read off `content_module_jar_libraries` for the same reason `_merged_module_jars` reads the module lists: the
    generator writes it from the recipe that decided the packing, so this asks that recipe instead of restating it.
    Not propagated over, for the same reason again.
    """
    return [
        _library_entry(dep, target.label)
        for dep in getattr(ctx.rule.attr, "content_module_jar_libraries", None) or []
    ]

def _merged_module_jars(target, ctx):
    """The own jars of the modules merged into this target's packed jar, plus its own.

    Read straight off the visited rule's `content_module_jar_modules_before`/`_after`, which the generator writes from
    the same recipe object that decides `content_module_jar` itself
    (`BazelBuildFileGenerator.kt`, one `if` for both) - so this asks the packing recipe rather than restating it.

    **Deliberately not propagated over.** These labels stay out of `attr_aspects`: the jar is all that is wanted from
    them, reading a non-propagated dep's provider is allowed, and propagating over module dependencies is exactly what
    retiring the dependency frontier removed. Widening `attr_aspects` here would start putting that back.
    """
    jars = [_module_jar(target)]
    names = [target[_KtJvmInfo].module_name]
    for attr_name in ["content_module_jar_modules_before", "content_module_jar_modules_after"]:
        for dep in getattr(ctx.rule.attr, attr_name, None) or []:
            jar = _module_jar(dep)
            if jar == None:
                fail("%s: %s is merged into a packed jar but is not a module" % (target.label, dep.label))
            jars.append(jar)
            names.append(dep[_KtJvmInfo].module_name)
    return struct(jars = jars, names = names)

def _dev_dist_module_aspect_impl(target, ctx):
    exported = []
    exported_packed = []
    exported_packed_members = []
    exported_packed_member_names = []
    exported_packed_libraries = []

    # Defensively: the aspect is propagated over whatever the visited rule calls `exports`, and a rule may not have the
    # attribute at all, or may declare it as something other than a label list.
    exports = getattr(ctx.rule.attr, _EXPORT_ATTR, None)
    if type(exports) == "list":
        for dep in exports:
            if _DevDistModuleInfo in dep:
                info = dep[_DevDistModuleInfo]
                exported.append(info.own)
                exported_packed.append(info.packed_jars)
                exported_packed_members.append(info.packed_member_jars)
                exported_packed_member_names.append(info.packed_member_modules)
                exported_packed_libraries.append(info.packed_library_jars)

    jar = _module_jar(target)
    packed_jar = _packed_content_module_jar(target)

    # All three answers branch on the same question, so a wrapper stays transparent for packing exactly as it is for
    # `own`: a target with a jar of its own is that module and nothing else; one without is either a library, whose
    # `exports` are libraries too and contribute nothing, or a pass-through wrapper standing for the module it
    # re-exports - see `_EXPORT_ATTR`.
    if jar != None:
        if packed_jar == None:
            return [_DevDistModuleInfo(
                own = depset([jar]),
                packed_jars = depset(),
                packed_member_jars = depset(),
                packed_member_modules = depset(),
                packed_library_jars = depset(),
            )]
        merged = _merged_module_jars(target, ctx)
        return [_DevDistModuleInfo(
            own = depset([jar]),
            packed_jars = depset([packed_jar]),
            packed_member_jars = depset(merged.jars),
            packed_member_modules = depset(merged.names),
            packed_library_jars = depset(_merged_library_jars(target, ctx)),
        )]

    if packed_jar != None:
        # `content_module_jar` needs `module_name` and merges `all_output_jars[0]`, so a packing target always has a jar
        # of its own. If that ever stops holding, the jar would be handed over while nothing stopped declaring its
        # source - fail here rather than ship a jar built from bytes a fragment also packed.
        fail("%s packs a distribution jar but stands for no module jar of its own" % target.label)

    return [_DevDistModuleInfo(
        own = depset(transitive = exported),
        packed_jars = depset(transitive = exported_packed),
        packed_member_jars = depset(transitive = exported_packed_members),
        packed_member_modules = depset(transitive = exported_packed_member_names),
        packed_library_jars = depset(transitive = exported_packed_libraries),
    )]

_dev_dist_module_aspect = aspect(
    doc = "Reads the distribution jar a module target stands for.",
    implementation = _dev_dist_module_aspect_impl,
    attr_aspects = [_EXPORT_ATTR],
    provides = [_DevDistModuleInfo],
)

DevDistPlatformPayloadInfo = provider(
    doc = "What a product's `lib/`-owning payload contains, split by which producer packs each jar.",
    fields = {
        "packed_jars": "depset of File: the `lib/<module>.jar`s `jvm_library` packed itself.",
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
    for target in ctx.attr.modules:
        info = target[_DevDistModuleInfo]
        packed_jars.append(info.packed_jars)
        packed_member_jars.append(info.packed_member_jars)
        packed_member_names.append(info.packed_member_modules)
        packed_library_jars.append(info.packed_library_jars)

    packed = depset(transitive = packed_jars)
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

    packed_members = {}
    for names in packed_member_names:
        for name in names.to_list():
            packed_members[name] = True

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
            module_jars = depset(transitive = packed_member_jars),
            library_jars = depset(transitive = packed_library_jars),
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

    Nothing needs to be told any more. The payload arrives whole and unfiltered, the aspect reads `content_module_jar`
    off each target, and everything the intersection used to produce comes out of one target so the answers cannot
    disagree:

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
            doc = "The payload's own modules, as their `jvm_library` targets - not their jar files, since an output " +
                  "group lives on the rule and a file label would give the module's own jar instead.",
            aspects = [_dev_dist_module_aspect],
            providers = [_KtJvmInfo],
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

    `transitive_runtime_jars` for the same reason `content_module_jar_libraries` uses it
    (`jvm-rules/rules/impl/content-module-jar.bzl:96-100`): it is the only `JavaInfo` set correct for all three shapes
    the library generator emits. Measured on real containers, the alternatives are not - `full_compile_jars` adds the
    container's own empty output jar, and its *position* is not even stable (before the real jar for
    `@lib//:studio-platform-provided`, after it for `@lib//:kotlinc-kotlin-compiler-fe10-provided`), while
    `compile_jars` hands back an **ijar** for a local `java_import` library.

    Order is load-bearing and preserved: the packer resolves an entry offered by several sources to the first, and a
    multi-jar container's `exports` carry a `# do not sort` comment for that reason. Dedup is first-wins, matching
    `content_module_jar_libraries`.
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

def _collect_library_jars(ctx, library_jars):
    """The per-jar half, for labels a *repository rule* produced rather than a generator writing a BUILD file.

    Only test plugins use this. They have no `plugin-content.yaml`, so the dynamic JPS-to-Bazel bridge resolves their
    library *names* into labels while loading (`DEV_DIST_ON_DEMAND_PLUGIN_LIBRARY_TARGETS`), and what it can derive from
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
            doc = "The plugin's libraries, as the container targets that group their jars - see `_collect_libraries`.",
            providers = [[JavaInfo]],
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
    _collect_library_jars(ctx, library_jars)

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
            doc = "Libraries no dep covers, as the container targets that group their jars - see `_collect_libraries`.",
            providers = [[JavaInfo]],
        ),
        "library_jars": attr.label_list(
            doc = "Individual library jar files, for the test-plugin payload only - see `_collect_library_jars`.",
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
