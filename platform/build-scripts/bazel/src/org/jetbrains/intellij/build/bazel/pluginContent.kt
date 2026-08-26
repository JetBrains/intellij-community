// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.file.Path
import java.util.TreeMap
import java.util.TreeSet
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * What a plugin contributes to a distribution, as Bazel labels: its member modules and the library jars they need.
 *
 * Membership, not packaging. `dev_dist_plugin_content` says it once, beside the plugin, so that a dev-distribution fragment
 * declares the plugin as one dep instead of restating the plugin's module and library *names* in a generated per-product
 * table; how those jars are laid out inside the plugin directory stays with `PluginLayout` and `JarPackager`.
 *
 * The modules come from the checked-in `plugin-content.yaml` rather than from the plugin descriptor, which settles the
 * two things a descriptor cannot answer: `xi:include`, which [parsePluginXmlContent] deliberately does not follow, is
 * already resolved in the report, and a report entry is a jar that some product really ships.
 */
internal class PluginContent(
  /** Raw member modules except the main module, which `dev_dist_plugin_content` takes as `descriptor_module`. */
  @JvmField val contentModuleLabels: List<String>,
  /**
   * Eligible content-module target labels whose jar this plugin puts at the conventional path.
   *
   * These modules are still members of the plugin, but their jar bytes bypass the fragment and reach the composed
   * distribution through the packed-plugin-jars component.
   *
   * No path: the rule derives `modules/<module>.jar` from the module name it already reads off the target. Writing it
   * here put one copy of that rule into each of the 2 030 relations checked in when the derivation replaced it, and a
   * Maven-style renaming of a module rewrote both. The set is larger now; `dev_dist_content.bzl` states the current
   * figure. [prepackedJarDestinations] is the other shape.
   */
  @JvmField val prepackedContentModuleLabels: List<String>,
  /**
   * The same hand-off where this plugin puts the jar somewhere else: the packing target's label to the `lib/`-relative
   * path.
   *
   * An `embedded` content module the layout does not pack into the plugin jar gets `lib/<module>.jar`, which is a jar
   * this generator reproduces as faithfully as the conventional one - the destination is the only difference, and it is
   * the relation's rather than the module's. Only the deviation is written; see `_collect_prepacked`.
   */
  @JvmField val prepackedJarDestinations: Map<String, String>,
  /**
   * One label per library - the `jvm_import`, `java_library` or `java_import` target that *groups* its jars, and not the
   * per-jar `copy_file`/`exports_files` labels those jars have.
   *
   * `dev_dist_plugin_content.libraries` keys its manifest by this container and expands it through
   * `JavaInfo.transitive_runtime_jars`, so the checked-in label carries no artifact version and a Maven bump leaves
   * every plugin `BUILD.bazel` alone. It is the same label `bazel-targets.json` records as `LibraryDescription.target`
   * and that `BazelBuildInputs.resolveAllIfDeclared` is asked for.
   */
  @JvmField val libraryContainerLabels: List<String>,
)

/**
 * What reading a plugin's content report produced: the target to emit, and what only the other repository can name.
 *
 * Two fields rather than one because they have different lifetimes. [content] is `null` when the report resolves to
 * nothing this package can declare, and such a plugin deliberately gets no target - see [computePluginContent]. The
 * cross-repository half has to outlive that decision: a community plugin whose *only* extra content is a
 * prepack-eligible ultimate member earns no target here and still needs its jar packed.
 */
internal class PluginContentResult(
  @JvmField val content: PluginContent?,
  /**
   * The prepack-eligible members dropped because they are ultimate modules and this would be a community target.
   *
   * Module *names*, not labels, and deliberately: the labels are unreachable from here - that is the whole reason these
   * are dropped - and the completion set in `//build/dev-dist-content` that names them already resolves a name to a
   * label through `bazel-targets.json`, for the raw members it has always completed. Recording them is what keeps a
   * cross-repository member on the packed path instead of silently demoting a plugin's whole ultimate half to
   * `JarPackager`.
   */
  @JvmField val crossRepositoryPrepackedModules: List<String>,
)

internal const val PLUGIN_CONTENT_REPORT_FILE_NAME: String = "plugin-content.yaml"

/**
 * The one library `dev_dist_plugin_content` declares implicitly, so a content target never names it.
 *
 * Its presence in a plugin's `libraries` said nothing about the plugin. The converter puts it into a module's
 * `runtime_deps` itself and JPS declares it for nobody, so whether it reached the attribute depended on whether some
 * member's JPS model or the layout report happened to mention it - true for 250 of 408 content targets, false for the
 * other 158, and describing neither group.
 */
private const val KOTLIN_STDLIB_LABEL = "@lib//:kotlin-stdlib"

private val EMPTY_PLUGIN_CONTENT_RESULT = PluginContentResult(content = null, crossRepositoryPrepackedModules = emptyList())

/**
 * The name of the target that declares [module]'s dev-distribution content, in the plugin's own package.
 *
 * Its own function because it is written in two places that must agree: the target in the plugin's `BUILD.bazel` and
 * the label `build/bazel-targets.json` records for the plan generator to depend on.
 */
internal fun pluginContentTargetName(module: ModuleDescriptor): String = "${module.targetName}_dev_content"

/**
 * Writes [module]'s content declaration into the plugin's own `BUILD.bazel`.
 *
 * A target of its own, next to the main module's `jvm_library` rather than attributes on it, because membership is a
 * property of the *(plugin, module)* pair and not of a module: 275 of 2647 content modules are content of more than one
 * plugin (`intellij.platform.commercial.verifier` of 45), which an attribute on the module could not express, and 126
 * plugins have a content module that depends back on the plugin's main module in the JPS model - naming those modules in
 * an attribute of the main module's own target would make a quarter of the repo's plugins a target-graph cycle.
 *
 * Public visibility, unlike `ij_plugin`: a dev-distribution content set lives in `//build/dev-dist-content` and every
 * product's set depends on the plugins it bundles by label.
 */
internal fun BuildFile.emitPluginContent(module: ModuleDescriptor, content: PluginContent) {
  load("@community//platform/build-scripts/bazel-rules:dev_dist_content.bzl", "dev_dist_plugin_content")
  target("dev_dist_plugin_content") {
    // Neither `name` nor `visibility`: `dev_dist_plugin_content` is a macro that derives the first from
    // `descriptor_module` - the same `${module.targetName}_dev_content` that `pluginContentTargetName` writes into
    // `bazel-targets.json` - and defaults the second to public, which all 408 of these targets were.
    //
    // Emitted in the order the Starlark formatter sorts them - alphabetical - so that a regeneration needs no reformat.
    if (content.contentModuleLabels.isNotEmpty()) {
      option("content_modules", content.contentModuleLabels)
    }
    // The rule puts the descriptor module into the content itself, so it is deliberately not in `content_modules`.
    option("descriptor_module", ":${module.targetName}")
    if (content.libraryContainerLabels.isNotEmpty()) {
      option("libraries", content.libraryContainerLabels)
    }
    if (content.prepackedContentModuleLabels.isNotEmpty()) {
      option("prepacked_content_modules", content.prepackedContentModuleLabels)
    }
    if (content.prepackedJarDestinations.isNotEmpty()) {
      option("prepacked_jars", LinkedHashMap(content.prepackedJarDestinations))
    }
  }
}

/**
 * Reads the content report beside [module] and resolves it into Bazel labels.
 *
 * [PluginContentResult.content] is `null` when [module] is not a plugin main module with a report, or when the report
 * resolves to nothing beyond the main module itself; [PluginContentResult.crossRepositoryPrepackedModules] is filled
 * either way.
 *
 * The empty case earns no target: `dev_dist_plugin_content` collects `descriptor_module` exactly the way it collects a
 * `content_modules` entry, so a target with neither content modules nor libraries contributes precisely what naming the
 * main module in the fragment's own `modules` contributes - a label, a load statement and a level of indirection saying
 * nothing. The consumer resolves the plugin through its main module target instead, which is why nothing is recorded for
 * it either.
 *
 * Anything the report names and this generator cannot label is dropped from the result with a warning rather than
 * failing the run or dropping the whole plugin: an under-declared content target surfaces at assembly time as "not
 * declared in the explicit input manifest", naming the jar, whereas no target at all would silently take the plugin out
 * of every fragment that depends on it.
 */
internal fun computePluginContent(module: ModuleDescriptor, moduleList: ModuleList, context: BazelBuildFileGenerator): PluginContentResult {
  val entries = module.pluginContentReport ?: return EMPTY_PLUGIN_CONTENT_RESULT
  val moduleName = module.module.name

  // The report does not record its own main module - only its location does, and a directory is the first content root
  // of two modules five times over (`plugins/RefactorX`, `contrib/flex`, ...), the second being a legacy `PLUGIN_MODULE`
  // artifact definition. The report is the tiebreak: a plugin's main module is packed into one of the plugin's jars and
  // the legacy module into none. It also drops the two stale reports that no module owns any more.
  val memberNames = LinkedHashSet<String>()
  for (entry in entries) {
    entry.modules.mapTo(memberNames) { it.name }
    entry.contentModules.mapTo(memberNames) { it.moduleName }
    // A bare library jar taken out of a module's own jar still names that module, and the module is a member: its jar
    // has to be declared like any other member's. Dropping this key is what took `intellij.java.debugger.agent.holder`
    // and its `debugger-agent` module library out of the java fragment - and it also made this reader disagree with
    // `indexPluginContentReports` in the plan generator, which does read it, about how many members a report has.
    entry.module?.let(memberNames::add)
  }
  if (!memberNames.remove(moduleName)) {
    return EMPTY_PLUGIN_CONTENT_RESULT
  }

  val contentModuleLabels = ArrayList<String>()
  val prepackedContentModuleLabels = ArrayList<String>()
  val prepackedJarDestinations = TreeMap<String, String>()
  val crossRepositoryPrepackedModules = ArrayList<String>()
  val members = ArrayList<ModuleDescriptor>()
  members.add(module)
  val prepackedMemberPaths = prepackedMemberPaths(moduleName = moduleName, entries = entries)
  // The members this plugin really handed over, whichever repository packs them. What is inside their jars is what this
  // target must stop declaring; see [recordedLibraries].
  val handedOver = HashSet<String>()
  for (memberName in memberNames) {
    val member = moduleList.getModuleDescriptorOrNull(memberName)
    if (member == null || moduleList.skippedModules.contains(memberName)) {
      // A module this generator does not convert - a standalone Bazel project, or one the report outlived - has no label.
      println("WARN: $moduleName content target: no Bazel target for member module $memberName")
      continue
    }
    val relativeOutputFile = prepackedMemberPaths.get(memberName)
      ?.takeIf { isPrepackedPluginContentModule(module = member, moduleList = moduleList, context = context) }
    if (module.isCommunity && !member.isCommunity) {
      // `getBazelDependencyLabel` fails outright on this edge, and a main-repository label is unreachable from the
      // community repository anyway. The member is still packed - the completion set in `//build/dev-dist-content` sees
      // both repositories and names it there - so what this target records is which half completes it: a prepack-eligible
      // member keeps its packed jar, and anything else stays a raw input the completion declares.
      //
      // Only at the conventional path. A completion carries a name, and `dev_dist_content_set` derives the destination
      // from it, so a plugin that puts the jar elsewhere has nowhere to say so. No report needs that today, which is
      // why this is a veto rather than a second attribute on the set.
      if (relativeOutputFile != null && isConventionalPrepackedPath(moduleName = memberName, relativeOutputFile = relativeOutputFile)) {
        crossRepositoryPrepackedModules.add(memberName)
        handedOver.add(memberName)
      }
      else if (relativeOutputFile != null) {
        println("WARN: $moduleName content target: ultimate module $memberName keeps being packed by JarPackager," +
                " because a cross-repository hand-off cannot state its `lib/$relativeOutputFile` destination")
      }
      else {
        println("WARN: $moduleName content target: community plugin packs ultimate module $memberName")
      }
      continue
    }

    if (relativeOutputFile != null) {
      // The member's packing target, not its `jvm_library`: the jar is that target's own output, and both prepacked
      // attributes gate on `ContentModuleJarInfo`, which only it provides.
      val label = contentModuleJarLabel(module = member, dependent = module, context = context)
      if (isConventionalPrepackedPath(moduleName = memberName, relativeOutputFile = relativeOutputFile)) {
        prepackedContentModuleLabels.add(label)
      }
      else {
        prepackedJarDestinations.put(label, relativeOutputFile)
      }
      handedOver.add(memberName)
    }
    else {
      members.add(member)
      contentModuleLabels.add(context.getBazelDependencyLabel(member, module))
    }
  }

  val libraryContainerLabels = computeLibraryContainerLabels(
    module = module,
    members = members,
    recordedLibraries = recordedLibraries(entries = entries, handedOver = handedOver),
    context = context,
  )
  // Sorted: these are sets of inputs, not merge orders, so a stable order keeps a regeneration free of diff noise.
  val crossRepository = crossRepositoryPrepackedModules.distinct().sorted()
  if (contentModuleLabels.isEmpty() &&
      prepackedContentModuleLabels.isEmpty() &&
      prepackedJarDestinations.isEmpty() &&
      libraryContainerLabels.isEmpty()) {
    return PluginContentResult(content = null, crossRepositoryPrepackedModules = crossRepository)
  }

  return PluginContentResult(
    content = PluginContent(
      contentModuleLabels = contentModuleLabels.distinct().sorted(),
      prepackedContentModuleLabels = prepackedContentModuleLabels.distinct().sorted(),
      prepackedJarDestinations = prepackedJarDestinations,
      libraryContainerLabels = libraryContainerLabels,
    ),
    crossRepositoryPrepackedModules = crossRepository,
  )
}

/**
 * Where this report puts the jar of each member it hands over, by module name, relative to the plugin's `lib/`.
 *
 * The two vetoes of a hand-off that one entry cannot see are applied here. [coPackedElsewhere] is the first. The second
 * is this report naming one module at two destinations: one packed jar cannot satisfy both, and a relation is keyed by
 * *(plugin, module)*, so there is no second relation to carry the second path. Both are fail-open - the module stays a
 * raw member and `JarPackager` keeps packing every jar it is in.
 */
private fun prepackedMemberPaths(moduleName: String, entries: List<RecipeEntry>): Map<String, String> {
  val coPacked = coPackedElsewhere(entries)
  val paths = HashMap<String, String>()
  val conflicting = HashSet<String>()
  for (entry in entries) {
    val simple = simplePluginContentEntry(entry) ?: continue
    if (simple.moduleName in coPacked) {
      continue
    }
    val previous = paths.put(simple.moduleName, simple.relativeOutputFile)
    if (previous != null && previous != simple.relativeOutputFile) {
      println("WARN: $moduleName content target: ${simple.moduleName} keeps being packed by JarPackager, because this" +
              " report puts its jar at both `lib/$previous` and `lib/${simple.relativeOutputFile}`")
      conflicting.add(simple.moduleName)
    }
  }
  paths.keys.removeAll(conflicting)
  return paths
}

/**
 * The libraries a plugin fragment has to declare, as **container** target labels.
 *
 * The container is the `jvm_import`, `java_library` or `java_import` that groups a library's jars - the label a module
 * already names in its `deps`/`runtime_deps`/`exports` ([libraryTargetLabel]) - and not the per-jar `copy_file` outputs
 * ([libraryJarTargets]). The distinction is the whole reason this list is cheap to check in: a per-jar label **carries
 * the artifact version**, so a Maven bump rewrote every plugin `BUILD.bazel` that named the library; a container label
 * does not, so a bump now touches only the library's own package. The rule expands the container back into its ordered
 * jars through `JavaInfo.transitive_runtime_jars` (`_collect_libraries` in `dev_dist_content.bzl`), and the container is
 * also what `build/bazel-targets.json` already records as `LibraryDescription.target` - which is the key
 * `BazelModuleOutputProvider.findLibraryRoots` asks the manifest for, so the two ends agree by construction.
 *
 * Two sources, unioned, because they answer two different questions:
 *
 * 1. the JPS library dependencies of every member module. That is the set the assembler resolves against the manifest,
 *    so declaring less fails the fragment;
 * 2. the libraries the report records, which is what the distribution really packs into the plugin's `lib/`. A member's
 *    dependency is not necessarily where a packed library is declared, so this is not implied by the first.
 *
 * **Scope-blind, TEST included.** A member declares every `<orderEntry type="library">` and every `module-library`,
 * whatever its scope, because the report does not state which of them a fragment must resolve. A blanket
 * `productionOnly()` once took `jmc-flightrecorder-writer` of `intellij.profiler.ultimate` out of the java fragment's
 * manifest. That module became prepacked on 2026-08-26 and left this walk, so no current member is named for it.
 * There is no second scope rule to keep this one in step with: the dependency frontier that had the other half is gone,
 * so the walk is one level of one thing.
 * PROVIDED is declared for the same reason - it is compile-only rather than test-only, and the layout can still pack
 * such a library. Note that a PROVIDED dependency is written as the `-provided` variant
 * ([libraryDependencyLabel]) while this list names the plain container, which is what keeps the manifest key
 * scope-independent and keeps a `neverlink` target - whose `transitive_runtime_jars` is empty - out of the declaration.
 */
private fun computeLibraryContainerLabels(
  module: ModuleDescriptor,
  members: List<ModuleDescriptor>,
  recordedLibraries: Set<RecordedLibrary>,
  context: BazelBuildFileGenerator,
): List<String> {
  val libraries = LinkedHashSet<Library>()
  // The modules whose *whole* declared library set is in `libraries`, which is what lets an unnamed recorded library be
  // recognised as already covered - see below.
  val collected = HashSet<String>()
  for (member in members) {
    if (collected.add(member.module.name)) {
      collectDeclaredLibraries(module = member, libraries = libraries, context = context)
    }
  }

  // What the walk above cannot reach: a library the layout packs from somewhere no member and no member's direct
  // dependency declares it. A name the walk already reached needs no second lookup.
  //
  // The lookup is keyed by (name, owning module), not by name alone, because that is how the converter keys a library
  // and how the report records one. A recorded library is a *module* library of the module it is recorded under far more
  // often than it is a project library - `debugger-agent` of `intellij.java.debugger.agent.holder`, `jshell-frontend` of
  // `intellij.java.jshell.execution`, `jmc-flightrecorder` of `intellij.profiler.ultimate` - so the module identity is
  // tried first and the project-library index second. Looking only in the project-library index is what turned every one
  // of those into a "no Bazel target" warning, i.e. into a jar missing from a fragment manifest.
  val walkedNames = libraries.mapTo(HashSet()) { it.target.jpsName }
  for (recorded in recordedLibraries) {
    if (recorded.name in walkedNames) {
      continue
    }
    val library = recorded.ownerModule?.let { context.getLibraryByJpsIdentity(jpsName = recorded.name, moduleLibraryModuleName = it) }
                  ?: context.getLibraryByJpsIdentity(jpsName = recorded.name, moduleLibraryModuleName = null)
    if (library == null) {
      // An *unnamed* `<orderEntry type="module-library">`, which the report keys by jar file name (`libwebp.jar`,
      // `socketio.jar`) while the converter keys it by declaration index (`#`, `#2`). There is no name to look up, and
      // none is needed: the walk took every library its owning module declares, this one included. Anything else is a
      // library no module declares, which is a real gap and says so.
      if (recorded.ownerModule == null || recorded.ownerModule !in collected) {
        println("WARN: ${module.module.name} content target: no Bazel target for library `${recorded.name}`" +
                (recorded.ownerModule?.let { " of module $it" } ?: ""))
      }
      continue
    }
    libraries.add(library)
  }

  val nameableRepositories = nameableRepositories(module = module, context = context)
  val labels = TreeSet<String>()
  for (library in libraries) {
    val label = libraryTargetLabel(
      library = library,
      communityRoot = context.communityRoot,
      ultimateRoot = context.ultimateRoot,
      // Decides between `//` and `@community//` for a local library under the community root - the same distinction
      // `getBazelDependencyLabel` makes for a module.
      isCommunityDependent = module.isCommunity,
    )
    if (nameableRepositories != null && labelRepository(label) !in nameableRepositories) {
      // Same edge as the ultimate content module above, and the same resolution: the ultimate side of the distribution
      // declares this library, so dropping it here is what keeps the community target analyzable.
      println("WARN: ${module.module.name} content target: library $label is outside the community repository")
      continue
    }
    if (label == KOTLIN_STDLIB_LABEL) {
      // The rule declares it for every plugin, so naming it here says nothing - see `_collect_libraries`.
      continue
    }
    labels.add(label)
  }
  return labels.toList()
}

/**
 * The modules this report packs somewhere *besides* the self-named entry that would hand them over.
 *
 * [simplePluginContentEntry] judges one entry in isolation, which is not enough to decide a hand-off: a module
 * whose own entry is simple can still be a `modules:` member of another jar in the same plugin, and `JarPackager` packs
 * that jar from the module's raw output. Handing the module off takes its raw jar out of the fragment's declaration
 * while leaving that second jar to be packed, so the assembly resolves an input nobody declared.
 *
 * `intellij.gateway.core` is the case in the model: its own `lib/modules/intellij.gateway.core.jar` is simple, and the
 * gateway layout also packs it into `lib/gateway-standalone/gateway.core.jar` (`cwmLayout.kt:197`). It built only
 * because the retired dependency frontier happened to declare the jar - the plugin's own declaration never did.
 *
 * Fail-open like every other veto: the relation stays raw and `JarPackager` keeps both jars. Declaring the module in
 * both halves instead would buy nothing, since the raw jar it would have to re-declare is what re-keys the fragment.
 */
private fun coPackedElsewhere(entries: List<RecipeEntry>): Set<String> {
  val result = HashSet<String>()
  for (entry in entries) {
    if (simplePluginContentEntry(entry) != null) {
      continue
    }
    entry.modules.mapTo(result) { it.name }
    entry.contentModules.mapTo(result) { it.moduleName }
  }
  return result
}

/**
 * The label repositories a `dev_dist_plugin_content` in [module]'s own package may name, or `null` when every repository
 * the project has is nameable.
 *
 * The library counterpart of the community/ultimate module guard in [computePluginContent]: `community/MODULE.bazel`
 * declares the community library repository and knows nothing of ultimate, so an ultimate jar label in a community
 * package is a target Bazel cannot resolve. Derived from the same container mapping the converter labels libraries
 * with, rather than from a repository name written out here.
 */
private fun nameableRepositories(module: ModuleDescriptor, context: BazelBuildFileGenerator): Set<String>? {
  if (!module.isCommunity) {
    return null
  }
  // "" is the repo of a `//`-relative label, which inside the community repository is the community repository itself.
  return setOf("", "@community", context.getLibraryContainer(isCommunity = true).repoLabel)
}

/** The repository part of a Bazel label - `@lib` of `@lib//:foo.jar`, and `""` of a repo-relative `//lib:foo.jar`. */
private fun labelRepository(label: String): String = label.substringBefore("//")

/**
 * Every library [module] declares itself, as the converted [Library] the jar labels are computed from.
 *
 * Every scope, TEST included: dropping a member's TEST-scope library once took `jmc-flightrecorder-writer` of
 * `intellij.profiler.ultimate` out of the java fragment's manifest. That module became prepacked on 2026-08-26 and left
 * this walk, so the rule now rests on the argument rather than on a current case. Only members are walked, so there is
 * no second scope rule to separate this from; the walk over a member's *direct dependencies* is gone, and with it the
 * keys it cost - `jmock`, a TEST-scope project library of `intellij.platform.lang`, reached 369 of the 475 content
 * targets that way and was packed by none of them.
 */
private fun collectDeclaredLibraries(
  module: ModuleDescriptor,
  libraries: MutableCollection<Library>,
  context: BazelBuildFileGenerator,
) {
  for (element in module.module.dependenciesList.dependencies) {
    if (element !is JpsLibraryDependency) {
      continue
    }

    val parentReference = element.libraryReference.parentReference
    if (parentReference.resolve() is JpsGlobal) {
      // An application-level library is a local development setting, never part of a distribution.
      continue
    }

    val jpsLibrary = element.library ?: continue
    val owner = (parentReference as? JpsModuleReference)?.moduleName
    // Collected while the modules' dependencies were converted, so an unknown identity means a library this walk can
    // see and the converter did not - which would make the label unresolvable.
    val library = context.getLibraryByJpsIdentity(jpsName = jpsLibrary.name, moduleLibraryModuleName = owner)
    if (library == null) {
      println("WARN: ${module.module.name} declares library `${jpsLibrary.name}`, which has no Bazel target")
      continue
    }
    libraries.add(library)
  }
}

/**
 * One library a content report records, with the module it is recorded under.
 *
 * [ownerModule] is what makes the record resolvable. A library recorded under a member **is** that member's module
 * library. `writeModuleLibraries` in `ProjectStructureMapping.kt` writes only a `ModuleLibraryFileEntry` of that module
 * there. A project library is hoisted to the jar level as `projectLibraries:` instead, and [ownerModule] is then `null`.
 * The merge *order* interleaves the two kinds as the `.iml` declares them. The report does not.
 *
 * The lookup in [computeLibraryContainerLabels] still tries the project-library index when the module identity misses.
 * That is defensive rather than a second shape: the converter keys an *unnamed* module library by declaration index and
 * the report keys it by jar file name, so neither index holds it, and the warning below is the honest outcome.
 */
private data class RecordedLibrary(@JvmField val name: String, @JvmField val ownerModule: String?)

/**
 * Every library the report records, however it records it: per member module, hoisted to the jar, or as a jar of its own.
 *
 * Except a library merged into a jar this plugin no longer packs. [handedOver] names those members, and their libraries
 * are inside the jar their own `content_module_jar` target produces - so declaring them here would leave the fragment
 * with a declared input its assembly never reads, and a declared-but-unread input still re-keys the action. Only the
 * record is dropped: a library another member declares is still reached by the member walk in
 * [computeLibraryContainerLabels].
 */
private fun recordedLibraries(entries: List<RecipeEntry>, handedOver: Set<String>): Set<RecordedLibrary> {
  val result = LinkedHashSet<RecordedLibrary>()
  for (entry in entries) {
    for (reportModule in entry.modules) {
      reportModule.libraries.keys.mapTo(result) { RecordedLibrary(name = it, ownerModule = reportModule.name) }
    }
    for (reportModule in entry.contentModules) {
      // [RecipeModule.moduleName], not `name`: this is looked up as a module, and a `moduleName/descriptorName` key is not one.
      val reportModuleName = reportModule.moduleName
      if (reportModuleName in handedOver) {
        continue
      }
      reportModule.libraries.keys.mapTo(result) { RecordedLibrary(name = it, ownerModule = reportModuleName) }
    }
    entry.projectLibraries.mapTo(result) { RecordedLibrary(name = it.name, ownerModule = null) }
    // `module:` present means the jar is a library taken out of *that* module's jar, so the library is one of its own.
    entry.library?.let { result.add(RecordedLibrary(name = it, ownerModule = entry.module)) }
  }
  return result
}

/**
 * [ModuleDescriptor.pluginContentReportFile] as a path inside the module's own Bazel package, so that it can be
 * exported and named by a label. `null` when the module has no report, or when the report is outside the package -
 * `../` is not a label.
 */
internal fun pluginContentReportPackagePath(module: ModuleDescriptor): String? {
  val file = module.pluginContentReportFile ?: return null
  return file.relativeTo(module.bazelBuildFileDir).invariantSeparatorsPathString.takeIf { !it.startsWith("../") }
}

/**
 * Parses [ModuleDescriptor.pluginContentReportFile]; reached only through [ModuleDescriptor.pluginContentReport].
 *
 * Unlike a `module-content.yaml`, a plugin's report has one entry per jar or file of the plugin, and most plugins have
 * several - so every entry is read, not just a single one.
 */
internal fun parsePluginContentReport(file: Path?): List<RecipeEntry>? {
  if (file == null) {
    return null
  }

  val text = file.readText()
  if (text.isBlank()) {
    return null
  }

  return recipeYaml.decodeFromString(ListSerializer(RecipeEntry.serializer()), text).takeIf { it.isNotEmpty() }
}
