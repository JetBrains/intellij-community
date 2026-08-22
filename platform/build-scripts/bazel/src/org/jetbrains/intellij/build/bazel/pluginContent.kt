// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.file.Path
import java.util.TreeSet
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
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
   * Eligible content-module target label to its path relative to the plugin's `lib/` directory.
   *
   * These modules are still members of the plugin, but their jar bytes bypass the fragment and reach the composed
   * distribution through the packed-plugin-jars component.
   */
  @JvmField val prepackedContentModuleLabels: Map<String, String>,
  /**
   * One label per library jar - the `copy_file` output of a Maven jar, the `exports_files` entry of a local one - and
   * not the `jvm_import` target that groups them: `dev_dist_plugin_content.libraries` keys its manifest by the jar target's
   * own label, which is what `bazel-targets.json` records as `jarTargets` and what `BazelBuildInputs.resolve` asks for.
   */
  @JvmField val libraryJarLabels: List<String>,
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

private const val PLUGIN_CONTENT_REPORT_FILE_NAME = "plugin-content.yaml"

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
    option("name", pluginContentTargetName(module))
    // Emitted in the order the Starlark formatter sorts them - alphabetical after `name` - so that a regeneration needs
    // no reformat.
    if (content.contentModuleLabels.isNotEmpty()) {
      option("content_modules", content.contentModuleLabels)
    }
    // The rule puts the descriptor module into the content itself, so it is deliberately not in `content_modules`.
    option("descriptor_module", ":${module.targetName}")
    if (content.libraryJarLabels.isNotEmpty()) {
      option("libraries", content.libraryJarLabels)
    }
    if (content.prepackedContentModuleLabels.isNotEmpty()) {
      option("prepacked_content_modules", LinkedHashMap(content.prepackedContentModuleLabels))
    }
    visibility(arrayOf("//visibility:public"))
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
  val entries = readPluginContentReport(module) ?: return EMPTY_PLUGIN_CONTENT_RESULT
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
  val prepackedContentModuleLabels = LinkedHashMap<String, String>()
  val crossRepositoryPrepackedModules = ArrayList<String>()
  val members = ArrayList<ModuleDescriptor>()
  members.add(module)
  val prepackedMemberNames = entries.mapNotNull(::simplePluginContentModuleName).toSet() - coPackedElsewhere(entries)
  for (memberName in memberNames) {
    val member = moduleList.getModuleDescriptorOrNull(memberName)
    if (member == null || moduleList.skippedModules.contains(memberName)) {
      // A module this generator does not convert - a standalone Bazel project, or one the report outlived - has no label.
      println("WARN: $moduleName content target: no Bazel target for member module $memberName")
      continue
    }
    val isPrepacked = memberName in prepackedMemberNames && isPrepackedPluginContentModule(module = member, context = context)
    if (module.isCommunity && !member.isCommunity) {
      // `getBazelDependencyLabel` fails outright on this edge, and a main-repository label is unreachable from the
      // community repository anyway. The member is still packed - the completion set in `//build/dev-dist-content` sees
      // both repositories and names it there - so what this target records is which half completes it: a prepack-eligible
      // member keeps its packed jar, and anything else stays a raw input the completion declares.
      if (isPrepacked) {
        crossRepositoryPrepackedModules.add(memberName)
      }
      else {
        println("WARN: $moduleName content target: community plugin packs ultimate module $memberName")
      }
      continue
    }

    val label = context.getBazelDependencyLabel(member, module)
    if (isPrepacked) {
      prepackedContentModuleLabels.put(label, "modules/$memberName.jar")
    }
    else {
      members.add(member)
      contentModuleLabels.add(label)
    }
  }

  val libraryJarLabels = computeLibraryJarLabels(
    module = module,
    members = members,
    recordedLibraries = recordedLibraries(entries),
    context = context,
  )
  // Sorted: these are sets of inputs, not merge orders, so a stable order keeps a regeneration free of diff noise.
  val crossRepository = crossRepositoryPrepackedModules.distinct().sorted()
  if (contentModuleLabels.isEmpty() && prepackedContentModuleLabels.isEmpty() && libraryJarLabels.isEmpty()) {
    return PluginContentResult(content = null, crossRepositoryPrepackedModules = crossRepository)
  }

  return PluginContentResult(
    content = PluginContent(
      contentModuleLabels = contentModuleLabels.distinct().sorted(),
      prepackedContentModuleLabels = prepackedContentModuleLabels.toSortedMap(),
      libraryJarLabels = libraryJarLabels,
    ),
    crossRepositoryPrepackedModules = crossRepository,
  )
}

/**
 * The library jars a plugin fragment has to declare, as jar target labels.
 *
 * Two sources, unioned, because they answer two different questions:
 *
 * 1. the JPS dependencies of every member module *and of each member's direct module dependencies*, which is what the
 *    retired plugin payload of `build/jps_dynamic_deps_ultimate.bzl:601-686` declared - `_add_module_input_targets(...,
 *    include_libraries = True)` for the members and for one level of their dependencies. That is the set the assembler
 *    resolves against the manifest, so declaring less fails the fragment; nothing in the graph supplies it, because the
 *    aspect deliberately does not walk `deps` for library jars (a `jvm_import`'s file is owned by an `http_file` repo,
 *    so the key would not be the one the assembler asks for);
 * 2. the libraries the report records, which is what the distribution really packs into the plugin's `lib/`. A member's
 *    dependency is not necessarily where a packed library is declared, so this is not implied by the first.
 *
 * Scope-blind for a **member**, TEST-filtered on the **frontier**. The retired payload was scope-blind on both, and this
 * reproduced it exactly so that the move from a name table to targets stayed a mechanical migration; the asymmetry is
 * the first deliberate narrowing on top of it, and it is where the whole cost was. A member's own TEST-scope library
 * can still be packed - `jmc-flightrecorder-writer` of `intellij.profiler.ultimate` is one, and dropping it took its
 * jar out of the java fragment's manifest - so members keep declaring every `<orderEntry type="library">` and every
 * `module-library` whatever its scope. A member's *direct dependency* is a different case: this plugin does not pack
 * that module, so a library it declares for its own tests cannot be a distribution input here. Scope-blindness there
 * put `jmock` - TEST-scope on `intellij.platform.lang` - into 369 of the 475 content targets, packed by none of them.
 *
 * PROVIDED stays declared on both halves. It is compile-only rather than test-only, the layout can still pack such a
 * library, and the hard-failure asymmetry says to narrow one clause at a time and measure.
 */
private fun computeLibraryJarLabels(
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

  val projectRoot = context.ultimateRoot ?: context.communityRoot
  val nameableRepositories = nameableRepositories(module = module, context = context)
  val labels = TreeSet<String>()
  for (library in libraries) {
    val jarLabels = libraryJarTargets(
      library = library,
      communityRoot = context.communityRoot,
      ultimateRoot = context.ultimateRoot,
      // The repo the labels are written into, which is what decides between `//` and `@community//` for a local
      // library under the community root - the same distinction `getBazelDependencyLabel` makes for a module.
      projectRoot = if (module.isCommunity) context.communityRoot else projectRoot,
    )
    val unreachable = jarLabels.filter { nameableRepositories != null && labelRepository(it) !in nameableRepositories }
    if (unreachable.isNotEmpty()) {
      // Same edge as the ultimate content module above, and the same resolution: the ultimate side of the distribution
      // declares these jars, so dropping them here is what keeps the community target analyzable.
      for (label in unreachable) {
        println("WARN: ${module.module.name} content target: library jar $label is outside the community repository")
      }
      continue
    }
    labels.addAll(jarLabels)
  }
  return labels.toList()
}

/**
 * The modules this report packs somewhere *besides* their own `lib/modules/<module>.jar` entry.
 *
 * [simplePluginContentModuleName] judges one entry in isolation, which is not enough to decide a hand-off: a module
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
    if (simplePluginContentModuleName(entry) != null) {
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
 * Every scope, TEST included: a member's own TEST-scope library can still be packed - `jmc-flightrecorder-writer` of
 * `intellij.profiler.ultimate` is one, and dropping it once took the jar out of the java fragment's manifest. Only
 * members are walked, so there is no second scope rule to separate this from; the walk over a member's *direct
 * dependencies* is gone, and with it the keys it cost - `jmock`, a TEST-scope project library of
 * `intellij.platform.lang`, reached 369 of the 475 content targets that way and was packed by none of them.
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
 * [ownerModule] is what makes the record resolvable, and it is *not* a claim that the library is a module library: a
 * report interleaves a module's project libraries with its module libraries exactly as the `.iml` declares them, so the
 * module is a hint to try first, not a key. It is `null` only where the report itself has no module to offer - a project
 * library hoisted to the jar level as `projectLibraries:`.
 */
private data class RecordedLibrary(@JvmField val name: String, @JvmField val ownerModule: String?)

/** Every library the report records, however it records it: per member module, hoisted to the jar, or as a jar of its own. */
private fun recordedLibraries(entries: List<RecipeEntry>): Set<RecordedLibrary> {
  val result = LinkedHashSet<RecordedLibrary>()
  for (entry in entries) {
    for (reportModule in entry.modules) {
      reportModule.libraries.keys.mapTo(result) { RecordedLibrary(name = it, ownerModule = reportModule.name) }
    }
    for (reportModule in entry.contentModules) {
      // [RecipeModule.moduleName], not `name`: this is looked up as a module, and a `moduleName/descriptorName` key is not one.
      reportModule.libraries.keys.mapTo(result) { RecordedLibrary(name = it, ownerModule = reportModule.moduleName) }
    }
    entry.projectLibraries.mapTo(result) { RecordedLibrary(name = it.name, ownerModule = null) }
    // `module:` present means the jar is a library taken out of *that* module's jar, so the library is one of its own.
    entry.library?.let { result.add(RecordedLibrary(name = it, ownerModule = entry.module)) }
  }
  return result
}

/**
 * The content report file of the plugin whose main module is [module], if the module has one.
 *
 * The report sits in the module's first content root, which is where the content-report writer puts it
 * (`contentChecker.kt` resolves `module.contentRootsList.urls.first()`), and is the same rule [readRecipe] follows.
 *
 * Existence only, deliberately. The Bazel side probes for exactly these files
 * (`_find_plugin_content_report_rel_path` in `@community//build:jps_model.bzl`) so that the hermetic
 * `bazel-targets.json` run is handed the same reports the full-checkout run reads, and it cannot parse YAML. Both
 * sides therefore have to agree only on *which file is a plugin's report*, which [JpsModuleToBazelTargetsOnly]
 * asserts; whether that report then yields a content target is this side's business alone.
 */
internal fun pluginContentReportFile(module: ModuleDescriptor): Path? {
  return module.contentRoots.firstOrNull()?.resolve(PLUGIN_CONTENT_REPORT_FILE_NAME)?.takeIf { it.isRegularFile() }
}

/**
 * [pluginContentReportFile] as a path inside the module's own Bazel package, so that it can be exported and named by a
 * label. `null` when the module has no report, or when the report is outside the package - `../` is not a label.
 */
internal fun pluginContentReportPackagePath(module: ModuleDescriptor): String? {
  val file = pluginContentReportFile(module) ?: return null
  return file.relativeTo(module.bazelBuildFileDir).invariantSeparatorsPathString.takeIf { !it.startsWith("../") }
}

/**
 * The report of the plugin whose main module is [module], parsed, if there is one.
 *
 * Unlike a `module-content.yaml`, a plugin's report has one entry per jar or file of the plugin, and most plugins have
 * several - so every entry is read, not just a single one.
 */
private fun readPluginContentReport(module: ModuleDescriptor): List<RecipeEntry>? {
  val file: Path = pluginContentReportFile(module) ?: return null
  val text = file.readText()
  if (text.isBlank()) {
    return null
  }

  return recipeYaml.decodeFromString(ListSerializer(RecipeEntry.serializer()), text).takeIf { it.isNotEmpty() }
}
