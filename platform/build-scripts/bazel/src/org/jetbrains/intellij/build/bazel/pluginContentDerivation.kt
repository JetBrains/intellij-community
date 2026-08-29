// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.jetbrains.jps.model.module.JpsModuleDependency
import java.nio.file.Path

/** What [derivePluginContent] produced, with everything a comparison needs to say why. */
internal class DerivedPluginContent(
  @JvmField val result: PluginContentResult,
  /** The members the model states, without the main module - the counterpart of the report's member set. */
  @JvmField val memberNames: List<String>,
  /**
   * Where this producer offered to put each member's jar, by module name.
   *
   * The offer, not the verdict: [resolvePluginContent] applies the eligibility gate to it, exactly as it does to the
   * report's own map. Kept so that a comparison reads the relation off the producer instead of assuming the convention,
   * which stops holding as soon as a residue states a destination.
   */
  @JvmField val prepackedPaths: Map<String, String>,
  /** `false` when no production resource root of the main module holds `META-INF/plugin.xml`. */
  @JvmField val hasOwnDescriptor: Boolean,
  /** See [WalkedContentModules.unresolvedIncludes]. A non-empty list makes the plugin a hold-out. */
  @JvmField val unresolvedIncludes: List<String>,
  /** See [WalkedContentModules.selectiveIncludes]. A non-empty list makes the plugin a hold-out. */
  @JvmField val selectiveIncludes: List<String>,
  /** What [resolvePluginContent] would have printed. Collected rather than printed: this producer only measures. */
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
 * The replacement of [computePluginContent] at the emit site. It answers an empty result for a module the dev
 * distribution states no content for, which is the verdict [computePluginContent] reaches by finding no report beside
 * the module.
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
 * The second producer of a plugin's dev-distribution content: the project model instead of `plugin-content.yaml`.
 *
 * [computePluginContent] projects the checked-in report. This derives the same facts from what the model already states,
 * the way `pluginDescriptor.kt` derives a descriptor plan: the members come from the plugin's own resolved `<content>`
 * plus [residue], the jar of each member comes from [derivePluginContentCandidacy], and the libraries come from the
 * member walk. Both producers end in [resolvePluginContent], so a comparison of the two measures the facts and never the
 * label resolution.
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
  val candidacy = derivePluginContentCandidacy(module = module, moduleList = moduleList, context = context, residue = residue)
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
    hasOwnDescriptor = closure != null,
    unresolvedIncludes = walked.unresolvedIncludes,
    selectiveIncludes = walked.selectiveIncludes,
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
 * The descriptor arc reads the include's target out of `dev-dist-descriptor.yaml`, and that report exists beside 24
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
 * The `xi:include` targets `dev-dist-descriptor.yaml` states, by load path, for the plugin that has one.
 *
 * Every section is unioned. A section is one layout variant, and an include is a fact about the plugin's descriptor
 * rather than about a variant, so a row of any section answers the same load path.
 */
private fun descriptorResidueFiles(module: ModuleDescriptor, context: BazelBuildFileGenerator): Map<String, Path> {
  val report = module.pluginDescriptorReport ?: return emptyMap()
  val result = HashMap<String, Path>()
  for (section in report.values) {
    for (row in section?.descriptors.orEmpty()) {
      reportFile(row = row.path, context = context)?.let { result.putIfAbsent(row.loadPath, it) }
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

/**
 * Compares the two producers of every plugin's dev-distribution content, field by field, and reports what differs.
 *
 * ADR 0007 rule 5 is why this exists and why it evaluates both sides in code. An early reading of the descriptor
 * population matched a pattern over descriptor text and reported 150 of 173 where the true figure was 173 of 173, and
 * every exception was the pattern's own. So this asks [computePluginContent] and [derivePluginContent] for a
 * [PluginContentResult] each and compares the fields of both.
 *
 * A comparison answers whether two producers agree. It does not answer whether the request is right, and the
 * whole-distribution gates stay the authority on that.
 *
 * The deviations decide the residue schema: what the derivation cannot state is what `dev-dist.yaml` has to carry.
 */
internal fun comparePluginContentProducers(
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
  out: (String) -> Unit,
) {
  var compared = 0
  var identical = 0
  var differing = 0
  var closed = 0
  var unclosed = 0
  val residueRowsPerField = LinkedHashMap<String, Int>()
  val residuePluginsPerField = LinkedHashMap<String, Int>()
  val pluginsPerKind = LinkedHashMap<String, Int>()
  val rowsPerKind = LinkedHashMap<String, Int>()
  val holdOuts = LinkedHashMap<String, MutableList<String>>()
  val unclosedPlugins = ArrayList<String>()
  val remainingPerKind = LinkedHashMap<String, Int>()

  // Only the halves generation converts, and in generation's own order, so that every label rule here is the rule the
  // plugin's own `BUILD.bazel` was written with.
  for (module in moduleList.community + moduleList.ultimate) {
    if (module.pluginContentReport == null) {
      continue
    }
    val projected = computePluginContent(module = module, moduleList = moduleList, context = context)
    val derived = derivePluginContent(module = module, moduleList = moduleList, context = context)
    compared++

    val agree = isSameContent(projected = projected, derived = derived.result)
    val deviations = collectDeviations(module = module, projected = projected, derived = derived, moduleList = moduleList, context = context)
    if (agree) {
      identical++
    }
    else {
      differing++
    }
    if (agree && deviations.isEmpty()) {
      continue
    }
    out("${if (agree) "AGREE " else "DIFFER"} ${module.module.name}")
    // A difference the classification cannot name is a defect in the classification, not a finding about the plugin. It
    // is reported as its own kind so that the kind table adds up to the verdict.
    val named = deviations.ifEmpty {
      listOf(ContentDeviation(kind = "unclassified", detail = "the two results differ and no rule named the reason"))
    }
    for (warning in derived.warnings) {
      // What the projecting producer prints during generation, from the deriving one. A member or a library the
      // derivation names and this generator cannot label is a gap in the derivation, not a deviation of the plugin.
      out("  derivation $warning")
    }
    for (deviation in named) {
      pluginsPerKind.merge(deviation.kind, 1, Int::plus)
      rowsPerKind.merge(deviation.kind, deviation.rows, Int::plus)
      out("  ${deviation.kind}: ${deviation.detail}")
      if (deviation.isHoldOut) {
        holdOuts.computeIfAbsent(module.module.name) { ArrayList() }.add("${deviation.kind}, agrees today: $agree")
      }
    }
    if (!agree && deviations.none { it.isHoldOut }) {
      out("  report=${describe(projected.content)}")
      out("  model=${describe(derived.result.content)}")
    }

    // The second arm. It answers the question the first arm cannot: is the residue schema sufficient? The residue is
    // synthesized from the report here, which a checked-in `dev-dist.yaml` would state instead.
    val residue = synthesizeResidue(module = module, bare = derived, moduleList = moduleList, context = context)
    for ((field, rows) in residueFieldRows(residue)) {
      residueRowsPerField.merge(field, rows, Int::plus)
      residuePluginsPerField.merge(field, 1, Int::plus)
    }
    val withResidue = derivePluginContent(module = module, moduleList = moduleList, context = context, residue = residue)
    if (isSameContent(projected = projected, derived = withResidue.result)) {
      closed++
    }
    else {
      unclosed++
      unclosedPlugins.add(module.module.name)
      val remaining = collectDeviations(
        module = module,
        projected = projected,
        derived = withResidue,
        moduleList = moduleList,
        context = context,
      )
      for (deviation in remaining) {
        out("  UNCLOSED ${deviation.kind}: ${deviation.detail}")
        remainingPerKind.merge(deviation.kind, deviation.rows, Int::plus)
      }
    }
  }

  out("")
  out("compared=$compared identical=$identical differing=$differing")
  out("with the residue: closed=$closed unclosed=$unclosed")
  out("")
  out("residue fields, over the plugins that need one: plugins, rows")
  for ((field, plugins) in residuePluginsPerField.entries.sortedByDescending { it.value }) {
    out("  $plugins plugins, ${residueRowsPerField.get(field)} rows  $field")
  }
  if (unclosedPlugins.isNotEmpty()) {
    out("")
    out("plugins the residue does not close (${unclosedPlugins.size}): ${unclosedPlugins.sorted()}")
    out("what is left, by kind: rows")
    for ((kind, rows) in remainingPerKind.entries.sortedByDescending { it.value }) {
      out("  $rows rows  $kind")
    }
  }
  out("")
  out("deviation kinds: plugins reached, and residue rows the kind would need")
  for ((kind, plugins) in pluginsPerKind.entries.sortedByDescending { it.value }) {
    out("  $plugins plugins, ${rowsPerKind.get(kind)} rows  $kind")
  }
  out("")
  out("hold-outs (${holdOuts.size} plugins whose content the derivation cannot read):")
  for ((name, kinds) in holdOuts.entries.sortedBy { it.key }) {
    out("  $name ${kinds.distinct()}")
  }
}

/**
 * The residue a `dev-dist.yaml` beside [module] would have to hold, read off the report.
 *
 * Synthesized rather than checked in, because Phase 0 measures whether the schema is sufficient before any file lands.
 * The four fields come from the four deviation classes the first arm found, and nothing else is put in: a field the
 * measurement never fills would be a guess.
 *
 * The library field carries the report's whole recorded set rather than the rows the model misses. That closes the
 * plugin, which is what the arm proves; what such a field *costs* is measured separately, by the library rows the other
 * three fields leave behind. The two figures differ because a member walk reaches most recorded libraries on its own.
 */
private fun synthesizeResidue(
  module: ModuleDescriptor,
  bare: DerivedPluginContent,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
): PluginContentResidue {
  val entries = module.pluginContentReport.orEmpty()
  val reportMembers = reportMemberKinds(module)
  val extraMembers = reportMembers.all - bare.memberNames.toSet()
  val reportPaths = reportedPrepackedMemberPaths(entries)
  val libRootJars = LinkedHashSet<String>()
  val rawMembers = LinkedHashSet<String>()
  for (memberName in bare.memberNames.toSet() + extraMembers) {
    val member = moduleList.getModuleDescriptorOrNull(memberName) ?: continue
    if (!isPrepackedPluginContentModule(module = member, moduleList = moduleList, context = context)) {
      continue
    }
    val path = reportPaths.get(memberName)
    when {
      path == null -> rawMembers.add(memberName)
      !isConventionalPrepackedPath(moduleName = memberName, relativeOutputFile = path) -> libRootJars.add(memberName)
    }
  }
  return PluginContentResidue(
    extraMembers = extraMembers,
    libRootJars = libRootJars,
    rawMembers = rawMembers,
    libraries = recordedLibraries(entries = entries, handedOver = emptySet()),
  )
}

/** How many rows of each residue field one plugin needs. An empty field is left out, so a count is a real cost. */
private fun residueFieldRows(residue: PluginContentResidue): Map<String, Int> {
  val result = LinkedHashMap<String, Int>()
  if (residue.extraMembers.isNotEmpty()) {
    result.put("extra_members", residue.extraMembers.size)
  }
  if (residue.libRootJars.isNotEmpty()) {
    result.put("lib_root_jars", residue.libRootJars.size)
  }
  if (residue.rawMembers.isNotEmpty()) {
    result.put("raw_members", residue.rawMembers.size)
  }
  if (residue.libraries.isNotEmpty()) {
    result.put("libraries", residue.libraries.size)
  }
  return result
}

/** Whether the two producers state the same content, field by field. The verdict the counts are taken from. */
private fun isSameContent(projected: PluginContentResult, derived: PluginContentResult): Boolean {
  if (projected.crossRepositoryPrepackedModules != derived.crossRepositoryPrepackedModules) {
    return false
  }
  val left = projected.content
  val right = derived.content
  if (left == null || right == null) {
    return left == null && right == null
  }
  return left.contentModuleLabels == right.contentModuleLabels &&
         left.prepackedContentModuleLabels == right.prepackedContentModuleLabels &&
         left.prepackedJarDestinations == right.prepackedJarDestinations &&
         left.libraryContainerLabels == right.libraryContainerLabels
}

private fun describe(content: PluginContent?): String {
  if (content == null) {
    return "no target"
  }
  return "members=${content.contentModuleLabels}" +
         " prepacked=${content.prepackedContentModuleLabels}" +
         " destinations=${content.prepackedJarDestinations}" +
         " libraries=${content.libraryContainerLabels}"
}

/** One fact the two producers disagree about, with the kind the residue schema is grouped by. */
private class ContentDeviation(
  @JvmField val kind: String,
  @JvmField val detail: String,
  /** How many residue rows this deviation would need. One per member, per library or per destination. */
  @JvmField val rows: Int = 1,
  /** Whether the derivation read the plugin's content wrongly rather than incompletely. */
  @JvmField val isHoldOut: Boolean = false,
)

private fun collectDeviations(
  module: ModuleDescriptor,
  projected: PluginContentResult,
  derived: DerivedPluginContent,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
): List<ContentDeviation> {
  val result = ArrayList<ContentDeviation>()
  if (!derived.hasOwnDescriptor) {
    result.add(ContentDeviation(
      kind = "holdout/no-descriptor",
      detail = "no production resource root holds $PLUGIN_XML_LOAD_PATH, so the model states no content",
      isHoldOut = true,
    ))
    return result
  }
  if (derived.unresolvedIncludes.isNotEmpty()) {
    result.add(ContentDeviation(
      kind = "holdout/unresolved-include",
      detail = "${derived.unresolvedIncludes.size} includes: ${derived.unresolvedIncludes.sorted()}",
      rows = derived.unresolvedIncludes.size,
      isHoldOut = true,
    ))
  }
  if (derived.selectiveIncludes.isNotEmpty()) {
    result.add(ContentDeviation(
      kind = "holdout/selective-include",
      detail = derived.selectiveIncludes.sorted().toString(),
      rows = derived.selectiveIncludes.size,
      isHoldOut = true,
    ))
  }
  // The classification continues past a hold-out on purpose. An unresolved include hides part of the closure, and the
  // member rows below are what measures the size of that hole - they are also the rows a residue would state instead of
  // the include. The hold-out flag already says the derivation could not read the plugin.
  val reportMembers = reportMemberKinds(module)
  addMemberDeviations(reportMembers = reportMembers, derived = derived, out = result)
  addRelationDeviations(
    module = module,
    reportMembers = reportMembers,
    derived = derived,
    moduleList = moduleList,
    context = context,
    out = result,
  )
  addLibraryDeviations(projected = projected, derived = derived, out = result)

  if (projected.crossRepositoryPrepackedModules != derived.result.crossRepositoryPrepackedModules) {
    result.add(ContentDeviation(
      kind = "cross-repository-prepacked",
      detail = "the report states ${projected.crossRepositoryPrepackedModules}," +
               " the model states ${derived.result.crossRepositoryPrepackedModules}",
      rows = (projected.crossRepositoryPrepackedModules.toSet() - derived.result.crossRepositoryPrepackedModules.toSet()).size,
    ))
  }
  return result
}

/** Which of the report's three member keys names each member; see [addMemberDeviations] for why the keys are kept apart. */
private class ReportMembers(
  @JvmField val packedIntoAJar: Set<String>,
  @JvmField val contentModules: Set<String>,
  @JvmField val ownsALibraryJar: Set<String>,
) {
  @JvmField val all: Set<String> = LinkedHashSet<String>().also {
    it.addAll(packedIntoAJar)
    it.addAll(contentModules)
    it.addAll(ownsALibraryJar)
  }
}

private fun reportMemberKinds(module: ModuleDescriptor): ReportMembers {
  val entries = module.pluginContentReport.orEmpty()
  val packedIntoAJar = LinkedHashSet<String>()
  val contentModules = LinkedHashSet<String>()
  val ownsALibraryJar = LinkedHashSet<String>()
  for (entry in entries) {
    entry.modules.mapTo(packedIntoAJar) { it.name }
    entry.contentModules.mapTo(contentModules) { it.moduleName }
    entry.module?.let(ownsALibraryJar::add)
  }
  val moduleName = module.module.name
  packedIntoAJar.remove(moduleName)
  contentModules.remove(moduleName)
  ownsALibraryJar.remove(moduleName)
  return ReportMembers(packedIntoAJar = packedIntoAJar, contentModules = contentModules, ownsALibraryJar = ownsALibraryJar)
}

/**
 * The member-set deviations, split by which of the report's keys names the member.
 *
 * The split is the deliverable. A member the report names in a jar's `modules:` is a `withModule` call of the plugin's
 * layout, a member it names as an entry's `module:` owns a library jar taken out of its own, and a member it names in
 * `contentModules:` is one the plugin's own `<content>` should have named. The three need different residue rows.
 */
private fun addMemberDeviations(
  reportMembers: ReportMembers,
  derived: DerivedPluginContent,
  out: MutableList<ContentDeviation>,
) {
  val derivedMembers = derived.memberNames.toSet()
  val onlyInReport = reportMembers.all - derivedMembers
  val onlyInModel = derivedMembers - reportMembers.all

  fun report(kind: String, names: Collection<String>) {
    if (names.isNotEmpty()) {
      out.add(ContentDeviation(kind = kind, detail = names.sorted().toString(), rows = names.size))
    }
  }

  report("member-only-in-report/packed-into-a-jar", onlyInReport.filter { it in reportMembers.packedIntoAJar })
  report("member-only-in-report/owns-a-library-jar",
         onlyInReport.filter { it !in reportMembers.packedIntoAJar && it in reportMembers.ownsALibraryJar })
  report("member-only-in-report/content-module",
         onlyInReport.filter { it !in reportMembers.packedIntoAJar && it !in reportMembers.ownsALibraryJar })
  report("member-only-in-model", onlyInModel)
}

/**
 * Where the two producers put the jar of a member they both have.
 *
 * Kept apart from the member counts above, because a label that moves between `content_modules` and
 * `prepacked_content_modules` is one relation stated two ways and not two members. The derivation offers every member
 * the conventional path and lets the eligibility gate decide, so it can only ever over-prepack; the reason the report
 * disagrees is what the residue row has to carry, and [explainReportRelation] reads it off the report.
 */
private fun addRelationDeviations(
  module: ModuleDescriptor,
  reportMembers: ReportMembers,
  derived: DerivedPluginContent,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
  out: MutableList<ContentDeviation>,
) {
  val entries = module.pluginContentReport.orEmpty()
  val reportPaths = reportedPrepackedMemberPaths(entries)
  val vetoedMembers = coPackedElsewhere(entries)
  val deviationsByKind = LinkedHashMap<String, MutableList<String>>()
  for (memberName in derived.memberNames) {
    if (memberName !in reportMembers.all) {
      continue
    }
    val member = moduleList.getModuleDescriptorOrNull(memberName) ?: continue
    if (!isPrepackedPluginContentModule(module = member, moduleList = moduleList, context = context)) {
      // Neither producer hands this member over, so there is nothing to disagree about.
      continue
    }
    val reportPath = reportPaths.get(memberName)
    val modelPath = derived.prepackedPaths.get(memberName)
    when {
      reportPath == modelPath -> continue
      reportPath == null -> {
        val reason = explainReportRelation(entries = entries, memberName = memberName, vetoedMembers = vetoedMembers)
        deviationsByKind.computeIfAbsent("relation/report-keeps-it-raw/$reason") { ArrayList() }.add(memberName)
      }
      modelPath == null -> deviationsByKind.computeIfAbsent("relation/model-keeps-it-raw") { ArrayList() }.add(memberName)
      else -> deviationsByKind.computeIfAbsent("relation/destination") { ArrayList() }
        .add("$memberName -> $reportPath (the model states $modelPath)")
    }
  }
  for ((kind, members) in deviationsByKind) {
    out.add(ContentDeviation(kind = kind, detail = members.sorted().toString(), rows = members.size))
  }
}

/**
 * Why the report hands no jar of [memberName] over, in the words a residue row would need.
 *
 * One reason per member, and the first that applies. The order is the order [reportedPrepackedMemberPaths] and
 * [simplePluginContentEntry] apply their refusals in, so the reason named here is the one that really decided.
 */
private fun explainReportRelation(entries: List<RecipeEntry>, memberName: String, vetoedMembers: Set<String>): String {
  if (memberName in vetoedMembers) {
    return "co-packed-in-another-jar"
  }
  val naming = entries.filter { entry -> entry.contentModules.any { it.moduleName == memberName } }
  if (naming.isEmpty()) {
    return "no-jar-of-its-own"
  }
  val destinations = naming.mapNotNull { simplePluginContentEntry(it)?.relativeOutputFile }.distinct()
  if (destinations.size > 1) {
    return "two-destinations"
  }
  val entry = naming.first()
  return when {
    entry.contentModules.size > 1 -> "shares-a-jar-with-content-modules"
    entry.modules.isNotEmpty() -> "shares-a-jar-with-a-module"
    entry.projectLibraries.isNotEmpty() -> "its-jar-holds-a-project-library"
    entry.library != null || entry.module != null -> "its-jar-is-a-library-jar"
    entry.os != null || entry.arch != null || entry.libc != null -> "its-jar-is-per-operating-system"
    entry.name == "lib/$memberName.jar" -> "at-lib-root-and-merges-libraries"
    else -> "not-a-self-named-jar (${entry.name})"
  }
}

/**
 * The libraries only the report names.
 *
 * The report is the one statement of a library the layout packs from somewhere no member and no member's dependency
 * declares it, so this direction is a residue row per library. The other direction would be a library the model derives
 * and the distribution does not pack, which is a fragment declaring an input it never reads.
 */
private fun addLibraryDeviations(
  projected: PluginContentResult,
  derived: DerivedPluginContent,
  out: MutableList<ContentDeviation>,
) {
  val projectedLabels = projected.content?.libraryContainerLabels.orEmpty()
  val derivedLabels = derived.result.content?.libraryContainerLabels.orEmpty().toSet()
  val onlyInReport = projectedLabels.filterNot { it in derivedLabels }
  if (onlyInReport.isNotEmpty()) {
    out.add(ContentDeviation(kind = "library-only-in-report", detail = onlyInReport.sorted().toString(), rows = onlyInReport.size))
  }
  val onlyInModel = derivedLabels - projectedLabels.toSet()
  if (onlyInModel.isNotEmpty()) {
    out.add(ContentDeviation(kind = "library-only-in-model", detail = onlyInModel.sorted().toString(), rows = onlyInModel.size))
  }
}
