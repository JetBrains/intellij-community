// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeMap
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Writes every plugin's content residue into one central file, read off a distribution build's content report.
 *
 * `derivePluginContentCandidacy` and `derivePluginContent` state a plugin's content from the project model, and this
 * states what is left - the `PluginLayout` decisions the model cannot reach. The report zip is the authority it reads
 * them from, because that report is what a real distribution build packs. [readPluginContentReportZips] is the reader,
 * and `--content-report=<zip>` names the file.
 *
 * The report is never compared against the derivation this generator uses for generation. It is compared against a
 * derivation the residue is then written to correct, so a divergence says the derivation and the residue together no
 * longer reproduce the real distribution.
 *
 * Idempotent, and it has to be run to a fixed point. Some rows the second pass writes need the repo-global candidacy
 * fold, which is folded over the residues the first pass wrote - so a first run over a tree with no residue writes the
 * candidacy rows, and the second run adds the rows that depend on them. A third run writes nothing.
 *
 * A plugin whose residue would be empty gets no row. So an absent plugin always means pure convention.
 *
 * A plugin the reports do not hold keeps every row it has. A build reports the products it built, so a plugin outside
 * them says nothing about its own residue, and dropping its rows would silently change the plugin's leaves. That is the
 * first rule that makes a partial run safe: it can correct a plugin the reports cover, and it can do nothing at all to
 * one they do not.
 *
 * The second rule is the direction of the change. See [residueChangeAddsOnly]. It gates a write alone. A verify run
 * withholds every write, so it reports each divergence and holds nothing back.
 */
internal fun writeDevDistResidues(
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
  reports: Map<String, List<RecipeEntry>>,
  verify: Boolean = false,
): DevDistResidueWriteResult {
  var unchanged = 0
  val rowsPerField = LinkedHashMap<String, Int>()
  val pluginsPerField = LinkedHashMap<String, Int>()
  // Every change this pass would make, collected before any of it is applied. The direction rule below reads the whole
  // set, because a plugin the reports cover can still need a row only another product's report states.
  val divergent = ArrayList<DevDistResidueDivergence>()
  // The fold over the reports, with no override in play. It is the authority for every row this writes.
  val reportCandidates = foldPluginContentCandidacy(reports = reports.values.toList(), overrides = emptyMap())
  // What each covered plugin merges, for the central table below. A plugin with no row states no merged member.
  val extraMembers = TreeMap<String, List<String>>()
  // The other seven fields of each covered plugin, for the central table below.
  val contentResidues = TreeMap<String, ContentResidueSection>()
  for (module in moduleList.community + moduleList.ultimate) {
    val entries = reports.get(module.module.name) ?: continue
    val synthesized = synthesizeContentResidue(
      module = module,
      moduleList = moduleList,
      context = context,
      reportCandidates = reportCandidates,
      entries = entries,
    )
    val section = synthesized.section
    if (synthesized.extraMembers.isNotEmpty()) {
      extraMembers.put(module.module.name, synthesized.extraMembers)
      rowsPerField.merge("extra_members", synthesized.extraMembers.size, Int::plus)
      pluginsPerField.merge("extra_members", 1, Int::plus)
    }
    for ((field, rows) in contentResidueFieldRows(section)) {
      rowsPerField.merge(field, rows, Int::plus)
      pluginsPerField.merge(field, 1, Int::plus)
    }
    section?.let { contentResidues.put(module.module.name, it) }
    val after = section?.let { contentResidueRows(plugin = module.module.name, section = it) }
    val before = context.pluginContentResidue.get(module.module.name)
      ?.let { contentResidueRows(plugin = module.module.name, section = it) }
    when {
      after == null && before == null -> Unit
      after == before -> unchanged++
      else -> divergent.add(DevDistResidueDivergence(mainModule = module.module.name, before = before, after = after))
    }
  }
  val population = foldPluginContentPopulation(moduleList = moduleList, context = context, reports = reports)
  val extraMembersTable = foldPluginExtraMembers(context = context, reported = reports.keys, folded = extraMembers)
  // The direction rule gates a write alone. A verify run writes nothing, so holding a change back there would only hide
  // it from the reader; `reportStaleDevDistResidues` states the partial read instead.
  val (skipped, applied) = when {
    verify || !population.partial -> emptyList<DevDistResidueDivergence>() to divergent
    else -> divergent.partition { !residueChangeAddsOnly(it) }
  }
  val contentResidueTable = foldPluginContentResidue(
    context = context,
    reported = reports.keys,
    folded = contentResidues,
    skipped = skipped.mapTo(HashSet()) { it.mainModule },
  )
  if (!verify) {
    population.write()
    extraMembersTable.write()
    contentResidueTable.write()
  }
  return DevDistResidueWriteResult(
    // What the run applied, so that a held-back plugin is counted under `skipped` alone and never twice.
    written = applied.count { it.after != null },
    deleted = applied.count { it.after == null },
    unchanged = unchanged,
    rowsPerField = rowsPerField,
    pluginsPerField = pluginsPerField,
    divergent = divergent,
    populationDivergent = population.divergent,
    coveredPopulationCount = population.covered,
    unreadPopulationNames = population.kept,
    skippedPartialRemovals = skipped,
  )
}

/**
 * True when a change only adds rows to one plugin's residue, which is the one direction a partial read may write.
 *
 * A row that enters says a product really packs something the derivation does not reach, and the entry it rests on is a
 * fact of a build that ran. Reading more products can add such a row, never take one away, so an addition holds whatever
 * the unread products pack.
 *
 * A row that leaves says the opposite: no report needs it. That is a statement about every product, and a partial read
 * cannot make it. Measured on this tree, IDEA Ultimate's report alone asks to drop 8 plugins' `vetoed_members` and
 * `raw_members` rows, and the union of seven products keeps every one of them. So a removal waits for the products that
 * are missing, and this pass leaves the file exactly as it is.
 *
 * Whole rows, and not a text comparison. [contentResidueRows] sorts them, so a set difference of two row lists states
 * the change. A plugin whose rows all have to go counts as a removal of all of them.
 */
internal fun residueChangeAddsOnly(divergence: DevDistResidueDivergence): Boolean {
  val after = divergence.after?.toHashSet() ?: return false
  return divergence.before.orEmpty().all { it in after }
}


/**
 * Folds the content population off the distribution builds' content reports, and says whether the file is stale.
 *
 * The one producer of `PLUGIN_CONTENT_POPULATION_FILE_NAME`. The population is a product question, and a build's report is
 * the product's own answer: it names every plugin that build packed, bundled and non-bundled.
 *
 * Only the modules this run converts. A report names the plugins of one product family, so a name this project holds no
 * module for is a name no reader could match.
 *
 * ### Why a partial run can only add
 *
 * The population is the union over the products, and no one product's report holds it. So a run given fewer reports than
 * the population needs **keeps** the names it cannot speak for, and it says how many it kept. Writing only what the
 * supplied reports name would drop the plugins another product needs, and every dropped line takes that plugin's content
 * leaves with it.
 *
 * A name therefore leaves the population only on a run whose reports cover every name already in the file. That run has
 * seen every plugin the file claims, so a name it does not report is a real removal. `PluginContentPopulation.divergent`
 * is true only for such a run, which is what lets `--verify-dev-dist-residue` fail on a stale population without failing
 * on a partial read.
 */
private fun foldPluginContentPopulation(
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
  reports: Map<String, List<RecipeEntry>>,
): PluginContentPopulation {
  val reported = (moduleList.community + moduleList.ultimate)
    .filter { it.module.name in reports }
    .mapTo(LinkedHashSet()) { it.module.name }
  val file = (context.ultimateRoot?.resolve("community") ?: context.communityRoot)
    .resolve("build/$PLUGIN_CONTENT_POPULATION_FILE_NAME")
  val kept = (readPluginContentPopulation(file) - reported).sorted()
  if (kept.isNotEmpty()) {
    println(
      "WARN: the reports name ${reported.size} plugins, and '$PLUGIN_CONTENT_POPULATION_FILE_NAME' holds" +
      " ${kept.size} more. Those ${kept.size} lines are kept as they are, because no supplied report covers them." +
      " The population is the union over the products, so this run states part of it. Pass every product's report to" +
      " state the whole population and to let a removed plugin leave."
    )
  }
  val text = buildString {
    append(POPULATION_HEADER)
    for (name in (reported + kept).sorted()) {
      append(name).append('\n')
    }
  }
  return PluginContentPopulation(
    file = file,
    text = text,
    covered = reported.size,
    kept = kept,
    // A partial run cannot tell a stale population from an unread one, so only a covering run may call it stale.
    divergent = kept.isEmpty() && (!Files.isRegularFile(file) || file.readText() != text),
  )
}

/** The population one pass folded, ready to write, with whether the checked-in file already states it. */
private class PluginContentPopulation(
  @JvmField val file: Path,
  @JvmField val text: String,
  /** How many plugins of the population the supplied reports name. */
  @JvmField val covered: Int,
  /** The names the file holds that no supplied report covers. Empty when the reports cover the whole population. */
  @JvmField val kept: List<String>,
  @JvmField val divergent: Boolean,
) {
  /** True when the supplied reports do not cover every plugin the population already names. */
  val partial: Boolean
    get() = kept.isNotEmpty()

  fun write() {
    if (!Files.isRegularFile(file) || file.readText() != text) {
      file.writeText(text)
    }
  }
}

/**
 * Folds the merged members off the same reports, into the one content-residue field the monorepo reads.
 *
 * The partial-read rule is the population's, and for the same reason: a report names the plugins of one product, so a
 * run given fewer reports than the whole population **keeps** every plugin it cannot speak for. Writing only what the
 * supplied reports name would drop another product's merged members, and every dropped row takes a `withModule` call
 * with it. See [foldPluginContentPopulation] for the argument in full.
 *
 * A plugin a report covers and this run folds no member for loses its rows, which is a removal a covering report may
 * make. That is the one direction that differs from [residueChangeAddsOnly]: there the unit is a file and a partial
 * read holds a whole file back, here the unit is a plugin and a covered plugin is fully spoken for.
 */
private fun foldPluginExtraMembers(
  context: BazelBuildFileGenerator,
  reported: Set<String>,
  folded: Map<String, List<String>>,
): PluginExtraMembersTable {
  val file = (context.ultimateRoot?.resolve("community") ?: context.communityRoot)
    .resolve("build/$PLUGIN_EXTRA_MEMBERS_FILE_NAME")
  val rows = TreeMap<String, List<String>>()
  // Every plugin no supplied report covers, exactly as the file already states it.
  for ((plugin, members) in readPluginExtraMembers(file)) {
    if (plugin !in reported) {
      rows.put(plugin, members)
    }
  }
  rows.putAll(folded)
  val text = buildString {
    append(EXTRA_MEMBERS_HEADER)
    for ((plugin, members) in rows) {
      append('\n').append('[').append(plugin).append(']').append('\n')
      for (member in members) {
        append(member).append('\n')
      }
    }
  }
  return PluginExtraMembersTable(file = file, text = text)
}

/** The merged-member table one pass folded, ready to write. */
private class PluginExtraMembersTable(@JvmField val file: Path, @JvmField val text: String) {
  fun write() {
    if (!Files.isRegularFile(file) || file.readText() != text) {
      file.writeText(text)
    }
  }
}

/**
 * Folds the content residue off the reports, keeping every plugin this run cannot speak for.
 *
 * The partial-read rule is [foldPluginExtraMembers]'s, because the file is central for the same reason: one plugin's
 * rows leaving would take a `PluginLayout` decision with them, and a run given fewer reports than the population cannot
 * tell a removal from an absence.
 *
 * Two kinds of plugin keep their checked-in rows. One is a plugin no supplied report covers, exactly as
 * [foldPluginExtraMembers] keeps it. The other is [skipped], a plugin a report does cover whose change the direction
 * rule held back. The direction rule is per plugin and this file is one file, so a held-back plugin has to be written
 * back as it stands; otherwise the rule would hold a change back from the reader and drop it from the tree anyway.
 */
private fun foldPluginContentResidue(
  context: BazelBuildFileGenerator,
  reported: Set<String>,
  folded: Map<String, ContentResidueSection>,
  skipped: Set<String>,
): PluginContentResidueTable {
  val file = (context.ultimateRoot?.resolve("community") ?: context.communityRoot)
    .resolve("build/$PLUGIN_CONTENT_RESIDUE_FILE_NAME")
  val rows = TreeMap<String, ContentResidueSection>()
  for ((plugin, section) in readPluginContentResidue(file)) {
    if (plugin !in reported || plugin in skipped) {
      rows.put(plugin, section)
    }
  }
  for ((plugin, section) in folded) {
    if (plugin !in skipped) {
      rows.put(plugin, section)
    }
  }
  return PluginContentResidueTable(file = file, text = renderPluginContentResidue(rows))
}

/** The content-residue table one pass folded, ready to write. */
private class PluginContentResidueTable(@JvmField val file: Path, @JvmField val text: String) {
  fun write() {
    if (!Files.isRegularFile(file) || file.readText() != text) {
      file.writeText(text)
    }
  }
}

private const val EXTRA_MEMBERS_HEADER: String = """# Generated - do not edit.
#
# Modules a plugin's layout packs that the plugin's own `<content>` does not name, by plugin main module.
#
# One `PluginLayout.withModule` call per line, under the `[<plugin main module>]` that makes it. Nothing in the
# project model states these, which is why they are written down at all.
#
# The rest of a plugin's content residue sits on the plugin's own `dev_dist_plugin` call. This one field is
# central because it is the one field the monorepo reads: `readDevDistExtraMembers` of
# `community/platform/distribution-content/src/DevDistResidue.kt`, reached by the Patronus rule seeds and by the
# plugin-layout description the Rider and CLion packaging tests use. Neither runs the converter and neither can
# parse Starlark, so the field a third reader needs is the field that stays in a flat file.
"""

private const val POPULATION_HEADER: String = """# Generated - do not edit.
#
# Which modules a `dev_dist_plugin` states dev-distribution content for, one plugin main module per line.
#
# The JPS-to-Bazel converter derives a plugin's content from the project model and needs the population, which
# is a product question it cannot fold for itself. One line per plugin keeps that reader independent of
# Starlark, and it states no deviation: a deviation is a fact about one plugin, and it sits under that
# plugin's name in `dev_dist_plugin_content_residue.txt`.
#
# The layout variant is not here. A plugin's membership does not depend on it, which is what separates this
# file from the `descriptor_population` section of `dev_dist_plugin_model_tables.txt`.
"""

/** What one [writeDevDistResidues] pass did, so a caller can print the population the arc pays for. */
internal class DevDistResidueWriteResult(
  @JvmField val written: Int,
  @JvmField val deleted: Int,
  @JvmField val unchanged: Int,
  @JvmField val rowsPerField: Map<String, Int>,
  @JvmField val pluginsPerField: Map<String, Int>,
  /** Every plugin whose file this pass changed, in the order the pass reached them. */
  @JvmField val divergent: List<DevDistResidueDivergence> = emptyList(),
  /**
   * True when the checked-in population does not state what the reports fold to, and the reports cover every name the
   * file already holds. A partial read never sets it, because a partial read cannot tell a removal from an absence.
   */
  @JvmField val populationDivergent: Boolean = false,
  /** How many plugins of the population the supplied reports name. */
  @JvmField val coveredPopulationCount: Int = 0,
  /** The population names no supplied report covers, which is what makes the read partial. */
  @JvmField val unreadPopulationNames: List<String> = emptyList(),
  /**
   * The plugins whose change a partial read may not apply, because a row would leave. Empty on a covering read and on
   * every verify run. [residueChangeAddsOnly] holds the rule.
   */
  @JvmField val skippedPartialRemovals: List<DevDistResidueDivergence> = emptyList(),
) {
  /** True when the supplied reports do not cover every plugin the population names. */
  val partialReports: Boolean
    get() = unreadPopulationNames.isNotEmpty()
}

/**
 * One plugin whose checked-in residue does not state what the derivation needs.
 *
 * [before] is `null` when the plugin has no row yet, and [after] is `null` when the derivation reproduces the report on
 * its own and every row of the plugin has to go. Both are [contentResidueRows] output, so a row carries its field.
 */
internal class DevDistResidueDivergence(
  @JvmField val mainModule: String,
  @JvmField val before: List<String>?,
  @JvmField val after: List<String>?,
)

/**
 * What a reader has to change in one plugin's residue, as the rows that enter and leave.
 *
 * Rows and not a unified diff. Every row is one `PluginLayout` decision and [contentResidueRows] sorts them, so the
 * set difference states the whole change. Each row carries its field, so a row states which decision it is and not only
 * which module.
 */
internal fun devDistResidueDivergenceReport(divergence: DevDistResidueDivergence): String {
  val before = divergence.before.orEmpty().toSet()
  val after = divergence.after.orEmpty().toSet()
  val builder = StringBuilder()
  builder.append(divergence.mainModule)
  when {
    divergence.before == null -> builder.append("  (no residue row yet)")
    divergence.after == null -> builder.append("  (every row has to go - the derivation needs no residue)")
  }
  builder.append('\n')
  for (row in after - before) {
    builder.append("    + ").append(row).append('\n')
  }
  for (row in before - after) {
    builder.append("    - ").append(row).append('\n')
  }
  return builder.toString()
}

/**
 * The residue one plugin needs, or `null` when the derivation reproduces the report on its own.
 *
 * Two groups of rows, and the order matters. The first four state where a member's jar goes, and [reportCandidates] is
 * the authority for them, because they are what the candidacy fold reads. The last four state the membership, the jars
 * the layout names itself and the libraries. They are read off a derivation that already has the first four, so a row
 * is written only where the two sides still differ.
 */
private fun synthesizeContentResidue(
  module: ModuleDescriptor,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
  reportCandidates: Map<String, Set<String>>,
  entries: List<RecipeEntry>,
): SynthesizedContentResidue {
  val reportPaths = reportedPrepackedMemberPaths(entries)
  val libRootJars = ArrayList<String>()
  val separateJars = ArrayList<String>()
  val vetoedMembers = ArrayList<String>()
  val mergedLibraries = LinkedHashMap<String, List<String>>()

  // The offers the model makes with no residue at all. Each one is a claim about a jar, and the report fold is what says
  // whether the claim holds - it is the fold the checked-in `BUILD.bazel` was written with.
  val bareCandidacy = derivePluginContentCandidacy(
    module = module,
    moduleList = moduleList,
    context = context,
    residue = PluginContentResidue.NONE,
  )
  val offered = HashSet<String>()
  for (offer in bareCandidacy.offers) {
    offered.add(offer.moduleName)
    val recorded = reportCandidates.get(offer.moduleName)
    if (recorded == null) {
      // No report of the repository gives this module a jar of its own, so no packing target may serve it. The row is
      // written beside the plugin whose derivation makes the wrong offer, which is where a reader looks for the reason.
      vetoedMembers.add(offer.moduleName)
      continue
    }
    if (recorded != offer.libraries) {
      mergedLibraries.put(offer.moduleName, recorded.sorted())
    }
    val reportPath = reportPaths.get(offer.moduleName) ?: continue
    if (reportPath != offer.relativeOutputFile) {
      if (isConventionalPrepackedPath(moduleName = offer.moduleName, relativeOutputFile = reportPath)) {
        separateJars.add(offer.moduleName)
      }
      else {
        libRootJars.add(offer.moduleName)
      }
    }
  }
  // A member this plugin hands over and its model offers nothing for. Its own jar exists, so a row says where it goes,
  // and the library row states the jar when the member's own dependencies do not. A plugin with no closure of its own
  // reaches every one of its members this way.
  for ((memberName, reportPath) in reportPaths) {
    if (memberName in offered || memberName == module.module.name) {
      continue
    }
    val recorded = reportCandidates.get(memberName) ?: continue
    if (isConventionalPrepackedPath(moduleName = memberName, relativeOutputFile = reportPath)) {
      separateJars.add(memberName)
    }
    else {
      libRootJars.add(memberName)
    }
    val member = moduleList.getModuleDescriptorOrNull(memberName)
    val derived = member?.let { productionModuleLibraryNames(module = it, context = context) }
    if (recorded != derived) {
      mergedLibraries.put(memberName, recorded.sorted())
    }
  }

  val section = ContentResidueSection(
    libRootJars = libRootJars.sorted(),
    separateJars = separateJars.sorted(),
    vetoedMembers = vetoedMembers.sorted(),
    mergedLibraries = mergedLibraries.toSortedMap(),
  )
  // The membership rows, with the candidacy rows above already in play, so that the members and the libraries are read
  // off a derivation that puts every jar where this plugin really puts it.
  val derived = derivePluginContent(module = module, moduleList = moduleList, context = context, residue = section.toResidue())
  val reportMembers = reportMemberNames(module = module, entries = entries)
  val extraMembers = (reportMembers - derived.memberNames.toSet()).sorted()
  val withExtras = section.toResidue(extraMembers.toSet())
  val withMembers = derivePluginContent(module = module, moduleList = moduleList, context = context, residue = withExtras)

  val rawMembers = ArrayList<String>()
  for (memberName in withMembers.memberNames) {
    val member = moduleList.getModuleDescriptorOrNull(memberName) ?: continue
    if (!isPrepackedPluginContentModule(module = member, moduleList = moduleList, context = context)) {
      continue
    }
    if (memberName in withMembers.prepackedPaths && memberName !in reportPaths) {
      rawMembers.add(memberName)
    }
  }
  val withMembership = section.copy(
    rawMembers = rawMembers.sorted(),
    memberJars = synthesizeMemberJars(module = module, context = context, derived = withMembers, entries = entries),
  )
  val result = withMembership.copy(
    libraries = missingLibraries(
      module = module,
      moduleList = moduleList,
      context = context,
      section = withMembership,
      entries = entries,
    )
  )
  return SynthesizedContentResidue(
    section = result.takeIf { contentResidueFieldRows(it).isNotEmpty() },
    extraMembers = extraMembers,
  )
}

/**
 * What [synthesizeContentResidue] states for one plugin, split by where each half is written.
 *
 * [section] goes beside the plugin and [extraMembers] goes into the central table, because the merged members are the
 * one field the monorepo reads; see [readPluginExtraMembers]. Either half can be empty on its own.
 */
private class SynthesizedContentResidue(
  @JvmField val section: ContentResidueSection?,
  @JvmField val extraMembers: List<String>,
)

/** [memberJarRows] for one plugin, with the plugin's own main jar name read off the placement table. */
private fun synthesizeMemberJars(
  module: ModuleDescriptor,
  context: BazelBuildFileGenerator,
  derived: DerivedPluginContent,
  entries: List<RecipeEntry>,
): Map<String, List<String>> {
  return memberJarRows(
    mainJarName = pluginJarPlacementOf(mainModule = module.module.name, context = context).mainJarName,
    memberNames = derived.memberNames.toSet(),
    derivedJars = derived.memberPaths,
    entries = entries,
  )
}

/**
 * The jars the layout names for a member, by member, relative to the plugin's `lib/`.
 *
 * The one field whose value is a path, and it earns that: `PluginLayout.withModule(name, jarName)` states a free string
 * that no rule derives. [derivedJars] is where [deriveMemberJarPath] puts each member, with the membership rows already
 * in play, so this asks the derivation what is left rather than copying the report's whole jar list.
 *
 * **Set against set, and never containment.** A row states the member's whole jar set, because
 * `BaseLayout.checkNotExists` lets one plugin pack one module into several jars. `intellij.spring.customNs` sits in the
 * plugin's main jar and in `customNs/customNs.jar`, so its row names both, and a rule reading one jar at a time would
 * write half of it. The derivation's set is one jar, or [mainJarName] for a member it co-packs, which is what
 * [composeDerivedPluginJars] concludes for such a member. So the writer is the reader's inverse: a row exists exactly
 * where the reader would state another jar set.
 *
 * Two rules narrow which jars the report contributes:
 *
 * 1. only an entry under the plugin's `lib/`, because the plugin packs no member's jar anywhere else;
 * 2. only a member of [memberNames], because a row states a jar of this plugin's own member.
 *
 * A member no entry names gets no row. The reports say nothing about such a member, and a row has to rest on a jar a
 * build really packed. The report's set is the union over [entries], because two products can pack one member
 * differently and one row states both jars.
 */
internal fun memberJarRows(
  mainJarName: String,
  memberNames: Set<String>,
  derivedJars: Map<String, String>,
  entries: List<RecipeEntry>,
): Map<String, List<String>> {
  val reported = HashMap<String, MutableSet<String>>()
  for (entry in entries) {
    if (!entry.name.startsWith(LIB_ENTRY_PREFIX)) {
      continue
    }
    val path = entry.name.removePrefix(LIB_ENTRY_PREFIX)
    for (member in entry.modules + entry.contentModules) {
      if (member.moduleName in memberNames) {
        reported.computeIfAbsent(member.moduleName) { sortedSetOf() }.add(path)
      }
    }
  }
  val result = TreeMap<String, List<String>>()
  for ((memberName, paths) in reported) {
    if (paths != setOf(derivedJars.get(memberName) ?: mainJarName)) {
      result.put(memberName, paths.toList())
    }
  }
  return result
}

/** Where a report entry of one plugin states its jars. A plugin's own report names every path from its directory. */
private const val LIB_ENTRY_PREFIX: String = "lib/"

/**
 * The libraries the report records and the derivation, with [section] in play, still does not reach.
 *
 * Stating the members is what closes most of these: a library an extra member declares is reached by the member walk as
 * soon as the member is stated. So this asks the derivation what it produced rather than copying the report's whole
 * recorded set, which would put a row in the file for every library a member already declares.
 */
private fun missingLibraries(
  module: ModuleDescriptor,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
  section: ContentResidueSection,
  entries: List<RecipeEntry>,
): List<ResidueLibraryRow> {
  val projected = computePluginContent(module = module, moduleList = moduleList, context = context, entries = entries)
  val projectedLabels = projected.content?.libraryContainerLabels.orEmpty().toSet()
  if (projectedLabels.isEmpty()) {
    return emptyList()
  }
  val derived = derivePluginContent(module = module, moduleList = moduleList, context = context, residue = section.toResidue())
  val derivedLabels = derived.result.content?.libraryContainerLabels.orEmpty().toSet()
  if (projectedLabels.all { it in derivedLabels }) {
    return emptyList()
  }
  // One candidate row per recorded library, filtered by whether adding it changes the label set. The report records a
  // library by (name, owning module), which is the pair the converter looks one up by; a label would carry the artifact
  // version instead - see [computeLibraryContainerLabels].
  val rows = ArrayList<ResidueLibraryRow>()
  var current = section
  for (recorded in recordedLibraries(entries = entries, handedOver = emptySet()).sortedWith(
    compareBy({ it.ownerModule ?: "" }, { it.name })
  )) {
    val candidate = current.copy(libraries = current.libraries + ResidueLibraryRow(module = recorded.ownerModule, name = recorded.name))
    val grown = derivePluginContent(module = module, moduleList = moduleList, context = context, residue = candidate.toResidue())
    val grownLabels = grown.result.content?.libraryContainerLabels.orEmpty().toSet()
    if (grownLabels.size > derivedLabels.size && grownLabels.any { it in projectedLabels && it !in derivedLabels }) {
      rows.add(ResidueLibraryRow(module = recorded.ownerModule, name = recorded.name))
      current = candidate
    }
    if (projectedLabels.all { it in grownLabels }) {
      break
    }
  }
  return rows
}

/** Every member the report names, by any of its three keys, without the plugin's own main module. */
private fun reportMemberNames(module: ModuleDescriptor, entries: List<RecipeEntry>): Set<String> {
  val result = LinkedHashSet<String>()
  for (entry in entries) {
    entry.modules.mapTo(result) { it.name }
    entry.contentModules.mapTo(result) { it.moduleName }
    entry.module?.let(result::add)
  }
  result.remove(module.module.name)
  return result
}

/** How many rows of each field one plugin needs. An empty field is left out, so a count is a real cost. */
internal fun contentResidueFieldRows(section: ContentResidueSection?): Map<String, Int> {
  if (section == null) {
    return emptyMap()
  }
  val result = LinkedHashMap<String, Int>()
  fun put(field: String, rows: Int) {
    if (rows != 0) {
      result.put(field, rows)
    }
  }
  // A reader meets the seven fields once, in the order [ContentResidueSection] declares them.
  put("lib_root_jars", section.libRootJars.size)
  put("separate_jars", section.separateJars.size)
  put("member_jars", section.memberJars.size)
  put("raw_members", section.rawMembers.size)
  put("vetoed_members", section.vetoedMembers.size)
  put("merged_libraries", section.mergedLibraries.size)
  put("libraries", section.libraries.size)
  return result
}
