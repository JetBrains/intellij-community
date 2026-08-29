// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.jetbrains.jps.model.module.JpsModuleDependency
import java.nio.file.Path

/** What [derivePluginContent] produced: the leaf itself, plus the two facts the residue writer states rows from. */
internal class DerivedPluginContent(
  @JvmField val result: PluginContentResult,
  /** The members the model states, without the main module - the counterpart of the report's member set. */
  @JvmField val memberNames: List<String>,
  /**
   * Where this producer offered to put each member's jar, by module name.
   *
   * The offer, not the verdict: [resolvePluginContent] applies the eligibility gate to it, exactly as it does to the
   * report's own map. Kept so that the residue writer reads the relation off the producer instead of assuming the
   * convention, which stops holding as soon as a residue states a destination.
   */
  @JvmField val prepackedPaths: Map<String, String>,
  /** What [resolvePluginContent] would have printed. Collected rather than printed: the writer only measures. */
  @JvmField val warnings: List<String>,
)

/**
 * What the model cannot answer about one plugin's content, which is what a `dev-dist.yaml` beside the plugin would hold.
 *
 * [PluginContentResidue.NONE] means pure convention, and the Phase-0 measurement is what the fields are: every one of
 * them is a class the comparison found and no field is speculative. A plugin layout decides all four, and evaluating a
 * product layout is the work this generator exists to keep out of a fragment action.
 */
internal class PluginContentResidue(
  /** See [ContentResidueSection.extraMembers]. */
  @JvmField val extraMembers: Set<String> = emptySet(),
  /** See [ContentResidueSection.libRootJars]. */
  @JvmField val libRootJars: Set<String> = emptySet(),
  /** See [ContentResidueSection.rawMembers]. */
  @JvmField val rawMembers: Set<String> = emptySet(),
  /** See [ContentResidueSection.vetoedMembers]. */
  @JvmField val vetoedMembers: Set<String> = emptySet(),
  /** See [ContentResidueSection.separateJars]. */
  @JvmField val separateJars: Set<String> = emptySet(),
  /** See [ContentResidueSection.mergedLibraries]. */
  @JvmField val mergedLibraries: Map<String, Set<String>> = emptyMap(),
  /** See [ContentResidueSection.libraries]. */
  @JvmField val libraries: Set<RecordedLibrary> = emptySet(),
) {
  companion object {
    @JvmField val NONE: PluginContentResidue = PluginContentResidue()
  }
}

/**
 * [derivePluginContent] as the generator asks it: the result only, and nothing for a module outside the population.
 *
 * The one producer at the emit site. It answers an empty result for a module the dev distribution states no content for.
 *
 * The two plugins Phase 0 of this arc held out need no branch here, and both are worth naming. `intellij.lombok` keeps
 * its `META-INF/plugin.xml` in `community/plugins/lombok/plugin/resources/`, which belongs to another module, so the
 * derivation reads no closure for it and its whole membership is a stated one. `intellij.platform.ui.webview.jcef` is a
 * content module with a residue beside it, and its descriptor is `intellij.platform.ui.webview.jcef.xml` rather than a
 * `plugin.xml`; it states nothing, which is what both producers said about it before. So a hold-out is a residue row and
 * not a name on a list, which is what keeps every plugin on one code path.
 */
internal fun computeDerivedPluginContent(
  module: ModuleDescriptor,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
): PluginContentResult {
  if (!isDevDistContentPlugin(module = module, context = context)) {
    return EMPTY_PLUGIN_CONTENT_RESULT
  }
  val derived = derivePluginContent(module = module, moduleList = moduleList, context = context)
  for (warning in derived.warnings) {
    println(warning)
  }
  return derived.result
}

/**
 * The producer of a plugin's dev-distribution content, from the project model.
 *
 * It derives the facts the way `pluginDescriptor.kt` derives a descriptor plan: the members come from the plugin's own
 * resolved `<content>` plus [residue], the jar of each member comes from [derivePluginContentCandidacy], and the
 * libraries come from the member walk. [computePluginContent] projects a distribution build's report through the same
 * [resolvePluginContent] body, so the residue writer's comparison measures the facts and never the label resolution.
 *
 * One derivation of the jar path, not two. [derivePluginContentCandidacy] reproduces `computeOutputJarPath`, and the
 * repo-global fold it feeds is what [resolvePluginContent] then gates every relation on. Offering a member a path this
 * function invented instead would let the two disagree, and the disagreement would read as a plugin's deviation.
 */
internal fun derivePluginContent(
  module: ModuleDescriptor,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
  residue: PluginContentResidue = contentResidueOf(module),
): DerivedPluginContent {
  val moduleName = module.module.name
  val closure = derivePluginContentClosure(module = module, moduleList = moduleList, context = context)
  val walked = closure ?: EMPTY_WALKED_CONTENT_MODULES
  // The same key the report's `contentModules:` reader takes, so a module shipped under another descriptor names one
  // member on both sides; see [RecipeModule.moduleName].
  val memberNames = walked.moduleNames.mapTo(LinkedHashSet()) { it.substringBeforeLast('/') }
  memberNames.addAll(residue.extraMembers)
  memberNames.remove(moduleName)

  // Where this plugin puts each member's jar, from the one derivation of that question. A member with no offer is packed
  // into a jar of the plugin's that this generator does not pack, so it stays a raw member - which is also what the
  // report path does with it. `resolvePluginContent` applies the eligibility gate to what is left.
  //
  // The closure is handed over rather than walked a second time. Every plugin of the population pays one descriptor
  // parse per include round, so a second walk here would double that cost for all 516 of them.
  val candidacy = derivePluginContentCandidacy(
    module = module,
    moduleList = moduleList,
    context = context,
    residue = residue,
    closure = closure,
  )
  val prepackedPaths = candidacy.offers.asSequence()
    .filterNot { it.moduleName in residue.rawMembers }
    .filter { it.moduleName in memberNames }
    .associate { it.moduleName to it.relativeOutputFile }
  val warnings = ArrayList<String>()
  val result = resolvePluginContent(
    module = module,
    memberNames = memberNames,
    prepackedMemberPaths = prepackedPaths,
    // A handed-over member's libraries are inside the jar its own packing target produces, so the residue's rows are
    // filtered the way [reportedPrepackedMemberPaths]'s reader filters the report's.
    recordedLibrariesOf = { handedOver ->
      residue.libraries.filterTo(LinkedHashSet()) { it.ownerModule == null || it.ownerModule !in handedOver }
    },
    moduleList = moduleList,
    context = context,
    warn = warnings::add,
  )
  return DerivedPluginContent(
    result = result,
    memberNames = memberNames.toList(),
    prepackedPaths = prepackedPaths,
    warnings = warnings,
  )
}

/**
 * The plugin's own resolved `<content>`, or `null` when no production resource root of [module] holds `META-INF/plugin.xml`.
 *
 * The one entry point for both readers of a plugin's closure: [derivePluginContent] takes the members from it, and
 * [derivePluginContentCandidacy] takes the members and their loading rules. A plugin with no descriptor of its own is a
 * hold-out for both, and `null` says so rather than an empty closure.
 */
internal fun derivePluginContentClosure(
  module: ModuleDescriptor,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
): WalkedContentModules? {
  val descriptor = descriptorFiles(module = module, loadPath = PLUGIN_XML_LOAD_PATH).firstOrNull() ?: return null
  return walkPluginContentClosure(module = module, descriptor = descriptor, moduleList = moduleList, context = context)
}

/**
 * [walkContentModules] over [descriptor], with every `xi:include` resolved from the project model.
 *
 * The descriptor arc reads the include's target out of the residue's `descriptor:` part, which exists beside 24
 * plugins. This population is 20 times larger, so the residue answers few of its includes and a convention has to
 * answer the rest. Two probes do it, and neither scans a directory:
 *
 * 1. a load path that names a module descriptor names its module too, by the longest dotted prefix that is a module of
 *    this project - `intellij.database.dialects.base.xml` under `intellij.database.dialects.base`, then
 *    `intellij.database.dialects`, and so on;
 * 2. any other load path - `META-INF/xxx.xml` - is looked for in the production resource roots of the plugin's own
 *    members and of the modules the main module depends on, which is where a plugin keeps the descriptor it includes.
 *
 * The second probe needs the member set, and the member set needs the include. So the walk repeats while it learns a
 * new module to probe, and [MAX_INCLUDE_ROUNDS] bounds it. Every round re-reads the descriptor, and only a plugin with
 * an unfollowed include pays for a second one.
 */
private fun walkPluginContentClosure(
  module: ModuleDescriptor,
  descriptor: Path,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
): WalkedContentModules {
  val resolver = ConventionIncludeResolver(
    mainModule = module,
    moduleList = moduleList,
    residue = descriptorResidueFiles(module = module, context = context),
  )
  var walked = walkContentModules(descriptor = descriptor, resolveInclude = resolver::resolve)
  repeat(MAX_INCLUDE_ROUNDS) {
    if (walked.unresolvedIncludes.isEmpty() || !resolver.learnMembers(walked.moduleNames)) {
      return walked
    }
    walked = walkContentModules(descriptor = descriptor, resolveInclude = resolver::resolve)
  }
  return walked
}

/**
 * How many times [walkPluginContentClosure] repeats its walk.
 *
 * One round per level of includes that hides the member holding the next level's file. Three is a bound and not a
 * measured depth: the loop also stops as soon as a round learns no member, which is what ends it for every plugin of
 * this project.
 */
private const val MAX_INCLUDE_ROUNDS: Int = 3

/**
 * The `xi:include` targets the `descriptor:` part of `dev-dist.yaml` states, by load path, for the plugin that has one.
 *
 * Every section is unioned. A section is one layout variant, and an include is a fact about the plugin's descriptor
 * rather than about a variant, so a row of any section answers the same load path.
 */
private fun descriptorResidueFiles(module: ModuleDescriptor, context: BazelBuildFileGenerator): Map<String, Path> {
  val result = HashMap<String, Path>()
  for (section in descriptorResidueOf(module).values) {
    for (row in section?.descriptors.orEmpty()) {
      residueRowFile(row = row.path, context = context)?.let { result.putIfAbsent(row.loadPath, it) }
    }
  }
  return result
}

/** Resolves an `xi:include` load path against the residue first, then the two conventions; see [walkPluginContentClosure]. */
private class ConventionIncludeResolver(
  mainModule: ModuleDescriptor,
  private val moduleList: ModuleList,
  private val residue: Map<String, Path>,
) {
  private val searchModules = LinkedHashSet<ModuleDescriptor>()

  init {
    searchModules.add(mainModule)
    // A `META-INF/xxx.xml` include names no module, and the file sits in a module the plugin main module depends on -
    // `intellij.go.plugin` includes `META-INF/openapi.xml` of `intellij.go`. The dependency is the only statement of
    // that relation the model has, so the probe set is seeded with it rather than waiting for the walk to find the
    // module as a member. One level: the descriptor a plugin includes belongs to a module the plugin names itself.
    for (element in mainModule.module.dependenciesList.dependencies) {
      if (element is JpsModuleDependency) {
        moduleList.getModuleDescriptorOrNull(element.moduleReference.moduleName)?.let { searchModules.add(it) }
      }
    }
  }

  fun resolve(loadPath: String): Path? {
    residue.get(loadPath)?.let {
      return it
    }
    declaringModuleOf(loadPath)?.let { declaring ->
      descriptorFiles(module = declaring, loadPath = loadPath).firstOrNull()?.let {
        return it
      }
    }
    for (member in searchModules) {
      descriptorFiles(module = member, loadPath = loadPath).firstOrNull()?.let {
        return it
      }
    }
    return null
  }

  /** Adds the modules [moduleNames] declares to the probe set, and answers whether the set grew. */
  fun learnMembers(moduleNames: List<String>): Boolean {
    val before = searchModules.size
    for (name in moduleNames) {
      moduleList.getModuleDescriptorOrNull(name.substringBeforeLast('/'))?.let { searchModules.add(it) }
    }
    return searchModules.size > before
  }

  /** The module a module-descriptor load path names, by the longest dotted prefix that is a module of this project. */
  private fun declaringModuleOf(loadPath: String): ModuleDescriptor? {
    if (!loadPath.endsWith(DESCRIPTOR_FILE_SUFFIX)) {
      return null
    }
    var name = loadPath.removeSuffix(DESCRIPTOR_FILE_SUFFIX)
    while (name.isNotEmpty()) {
      moduleList.getModuleDescriptorOrNull(name)?.let {
        return it
      }
      name = name.substringBeforeLast('.', missingDelimiterValue = "")
    }
    return null
  }
}

private const val DESCRIPTOR_FILE_SUFFIX: String = ".xml"
