// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Writes the content half of every plugin's `dev-dist.yaml`, read off a distribution build's content report.
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
 * A plugin whose residue would be empty gets no file, and an existing empty one is deleted. So an absent file always
 * means pure convention.
 *
 * A plugin the reports do not hold is left alone, file and all. A build reports the products it built, so a plugin
 * outside them says nothing about its own residue, and deleting that residue would silently change the plugin's leaves.
 * That is the first rule that makes a partial run safe: it can correct a plugin the reports cover, and it can do nothing
 * at all to one they do not.
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
  for (module in moduleList.community + moduleList.ultimate) {
    val entries = reports.get(module.module.name) ?: continue
    val file = module.contentRoots.firstOrNull()?.resolve(DEV_DIST_RESIDUE_FILE_NAME) ?: continue
    val section = synthesizeContentResidue(
      module = module,
      moduleList = moduleList,
      context = context,
      reportCandidates = reportCandidates,
      entries = entries,
    )
    for ((field, rows) in contentResidueFieldRows(section)) {
      rowsPerField.merge(field, rows, Int::plus)
      pluginsPerField.merge(field, 1, Int::plus)
    }
    val text = composeDevDistResidueText(content = section, existing = file)
    val before = if (Files.isRegularFile(file)) file.readText() else null
    when {
      text == null && before == null -> Unit
      text == before -> unchanged++
      else -> divergent.add(DevDistResidueDivergence(mainModule = module.module.name, file = file, before = before, after = text))
    }
  }
  val population = foldPluginContentPopulation(moduleList = moduleList, context = context, reports = reports)
  // The direction rule gates a write alone. A verify run writes nothing, so holding a change back there would only hide
  // it from the reader; `reportStaleDevDistResidues` states the partial read instead.
  val (skipped, applied) = when {
    verify || !population.partial -> emptyList<DevDistResidueDivergence>() to divergent
    else -> divergent.partition { !residueChangeAddsOnly(it) }
  }
  if (!verify) {
    for (divergence in applied) {
      if (divergence.after == null) {
        divergence.file.deleteIfExists()
      }
      else {
        Files.createDirectories(divergence.file.parent)
        divergence.file.writeText(divergence.after)
      }
    }
    population.write()
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
 * Whole rows, and not a text comparison. The header is the same text in every file and the fields are sorted, so a set
 * difference of the rows states the change. A plugin whose file has to go counts as a removal of all of it.
 */
internal fun residueChangeAddsOnly(divergence: DevDistResidueDivergence): Boolean {
  val after = residueRows(divergence.after) ?: return false
  return residueRows(divergence.before).orEmpty().all { it in after }
}

/**
 * The rows of one residue file, each under the field that holds it, or `null` when the plugin has no file.
 *
 * A row states one `PluginLayout` decision. A comment and a blank line state none, and a comment sits at the indent of
 * the field it explains, so neither is a row.
 *
 * The field is part of the row, because a member name stands in several fields and means a different decision in each.
 * Without it, a name that moves from `extra_members` to `raw_members` would read as no change at all, and
 * [residueChangeAddsOnly] would let a partial read apply the move.
 *
 * A `libraries` row spans two lines, `- name:` and then `module:`, and the two state one decision together. The deeper
 * line joins the row above it. Two rows of their own would let two libraries swap their owning modules while the row
 * set stands still, and [residueChangeAddsOnly] would call that swap an addition.
 */
private fun residueRows(text: String?): Set<String>? {
  if (text == null) {
    return null
  }
  val rows = LinkedHashSet<String>()
  var field = ""
  // The member key of `merged_libraries`, which is the one field that nests. Empty under every other field.
  var member = ""
  var item: String? = null
  var itemIndent = 0
  for (line in text.lineSequence()) {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
      continue
    }
    val indent = line.length - line.trimStart().length
    // The second line of a `libraries` row: deeper than the row above, and no key or list marker of its own.
    if (item != null && indent > itemIndent && !trimmed.startsWith("- ") && !trimmed.endsWith(":")) {
      rows.remove(item)
      item = "$item $trimmed"
      rows.add(item)
      continue
    }
    item = null
    val prefix = if (member.isEmpty()) field else "$field $member"
    when {
      // A key with nothing after it opens a field of the section, or one member key of `merged_libraries`. It carries
      // the rows under it and is no row of its own, so a field that goes takes every one of its rows with it.
      trimmed.endsWith(":") -> {
        if (indent > FIELD_INDENT) {
          member = trimmed
        }
        else {
          field = trimmed
          member = ""
        }
      }
      trimmed.startsWith("- ") -> {
        item = "$prefix ${trimmed.removePrefix("- ")}"
        itemIndent = indent
        rows.add(item)
      }
      // A key with a value of its own: a member that merges no library at all.
      else -> rows.add("$prefix $trimmed")
    }
  }
  return rows
}

/** How far a field of the `content:` section is indented. A deeper key is one member of `merged_libraries`. */
private const val FIELD_INDENT: Int = 2

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

private const val POPULATION_HEADER: String = """# Generated - do not edit.
#
# Which modules a `dev_dist_plugin` states dev-distribution content for, one plugin main module per line.
#
# The JPS-to-Bazel converter derives a plugin's content from the project model and needs the population, which
# is a product question it cannot fold for itself. One line per plugin keeps that reader independent of
# Starlark, and it states no deviation: a deviation is a fact about one plugin, and it sits beside that plugin
# in `dev-dist.yaml`.
#
# The layout variant is not here. A plugin's membership does not depend on it, which is what separates this
# file from `dev_dist_plugin_descriptor_population.txt`.
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
 * [before] is `null` when the plugin has no file yet, and [after] is `null` when the derivation reproduces the report on
 * its own and the file has to go.
 */
internal class DevDistResidueDivergence(
  @JvmField val mainModule: String,
  @JvmField val file: Path,
  @JvmField val before: String?,
  @JvmField val after: String?,
)

/**
 * What a reader has to change in one plugin's residue, as the rows that enter and leave.
 *
 * Rows and not a unified diff. Every row of the file is one `PluginLayout` decision, the fields are sorted, and the
 * header is the same text in every file - so the set difference states the whole change. Each row carries its field, so
 * a row states which decision it is and not only which module.
 */
internal fun devDistResidueDivergenceReport(divergence: DevDistResidueDivergence): String {
  val before = residueRows(divergence.before).orEmpty()
  val after = residueRows(divergence.after).orEmpty()
  val builder = StringBuilder()
  builder.append(divergence.mainModule)
  when {
    divergence.before == null -> builder.append("  (no residue file yet)")
    divergence.after == null -> builder.append("  (the file has to go - the derivation needs no residue)")
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
 * the authority for them, because they are what the candidacy fold reads. The last three state membership, and they are
 * read off a derivation that already has the first four, so a row is written only where the members and the libraries
 * still differ.
 */
private fun synthesizeContentResidue(
  module: ModuleDescriptor,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
  reportCandidates: Map<String, Set<String>>,
  entries: List<RecipeEntry>,
): ContentResidueSection? {
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
  val withExtras = section.copy(extraMembers = extraMembers).toResidue()
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
  val withMembership = section.copy(extraMembers = extraMembers, rawMembers = rawMembers.sorted())
  val result = withMembership.copy(
    libraries = missingLibraries(
      module = module,
      moduleList = moduleList,
      context = context,
      section = withMembership,
      entries = entries,
    )
  )
  return result.takeIf { contentResidueFieldRows(it).isNotEmpty() }
}

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
  // The order [composeDevDistResidueText] writes the fields in, so a reader meets them once.
  put("extra_members", section.extraMembers.size)
  put("lib_root_jars", section.libRootJars.size)
  put("separate_jars", section.separateJars.size)
  put("raw_members", section.rawMembers.size)
  put("vetoed_members", section.vetoedMembers.size)
  put("merged_libraries", section.mergedLibraries.size)
  put("libraries", section.libraries.size)
  return result
}

/**
 * The whole text of one plugin's `dev-dist.yaml`, or `null` when the plugin needs no file at all.
 *
 * The `descriptor:` part of [existing] is kept verbatim. The two parts have two producers - `plugin-model-tool` writes
 * the descriptor deviations and this writes the content ones - so each has to leave the other's key alone. A file that
 * holds only a `descriptor:` part therefore survives a run of this writer untouched.
 *
 * The split is the line `descriptor:`, and neither producer may put a comment on the other's side of it.
 * `renderDescriptorPart` of `devDistDescriptorResidue.kt` states the same rule from its end, and
 * [DEV_DIST_RESIDUE_HEADER] is the one text both producers write - byte for byte, or the regeneration reaches no fixed
 * point, because each tool would rewrite what the other just wrote.
 */
private fun composeDevDistResidueText(content: ContentResidueSection?, existing: Path): String? {
  val descriptorPart = existingDescriptorPart(existing)
  if (content == null && descriptorPart == null) {
    return null
  }
  val builder = StringBuilder()
  builder.append(DEV_DIST_RESIDUE_HEADER)
  if (content != null) {
    builder.append("content:\n")
    appendNames(builder, "extra_members", content.extraMembers, EXTRA_MEMBERS_COMMENT)
    appendNames(builder, "lib_root_jars", content.libRootJars, LIB_ROOT_JARS_COMMENT)
    appendNames(builder, "separate_jars", content.separateJars, SEPARATE_JARS_COMMENT)
    appendNames(builder, "raw_members", content.rawMembers, RAW_MEMBERS_COMMENT)
    appendNames(builder, "vetoed_members", content.vetoedMembers, VETOED_MEMBERS_COMMENT)
    if (content.mergedLibraries.isNotEmpty()) {
      builder.append(MERGED_LIBRARIES_COMMENT)
      builder.append("  merged_libraries:\n")
      for ((member, libraries) in content.mergedLibraries) {
        if (libraries.isEmpty()) {
          builder.append("    ${quote(member)}: []\n")
          continue
        }
        builder.append("    ${quote(member)}:\n")
        for (library in libraries) {
          builder.append("    - ${quote(library)}\n")
        }
      }
    }
    if (content.libraries.isNotEmpty()) {
      builder.append(LIBRARIES_COMMENT)
      builder.append("  libraries:\n")
      for (row in content.libraries) {
        builder.append("  - name: ${quote(row.name)}\n")
        row.module?.let { builder.append("    module: ${quote(it)}\n") }
      }
    }
  }
  descriptorPart?.let(builder::append)
  return builder.toString()
}

/** The `descriptor:` block of an existing file, verbatim, or `null` when the file has none. */
private fun existingDescriptorPart(file: Path): String? {
  if (!Files.isRegularFile(file)) {
    return null
  }
  val text = file.readText()
  val start = text.indexOf("\ndescriptor:\n")
  if (start < 0) {
    return if (text.startsWith("descriptor:\n")) text else null
  }
  return text.substring(start + 1)
}

private fun appendNames(builder: StringBuilder, field: String, names: List<String>, comment: String) {
  if (names.isEmpty()) {
    return
  }
  builder.append(comment)
  builder.append("  $field:\n")
  for (name in names) {
    builder.append("  - ${quote(name)}\n")
  }
}

private fun quote(value: String): String = "\"$value\""

private const val DEV_DIST_RESIDUE_HEADER: String = """# Generated - do not edit.
#
# What this plugin's dev-distribution leaves need that the convention does not give.
#
# `dev_dist_plugin` in this plugin's own `BUILD.bazel` states the plugin's content and its patched descriptor.
# The JPS-to-Bazel converter derives both from the project model: the members come from the plugin's own
# `<content>`, the jar of each member from the loading rule and the member's own descriptor, the libraries from
# what the members declare, and the descriptor of every content module from its module's resource roots.
#
# This file is the remainder, so a plugin with no such file is pure convention. Every row states one
# `PluginLayout` decision, and evaluating a product layout is the work the converter exists to keep out of a
# fragment action.
#
# Two parts, two producers. `content:` is one section for the plugin, because membership does not depend on the
# layout variant. `descriptor:` is keyed by `<main module>` or `<main module>/<variant>`, because a descriptor
# deviation is a fact about one variant. Each producer rewrites only its own part.
#
# No row is a Bazel label, and no row is a path. A label carries the artifact version of a library, and a path
# restates a rule the rule already derives.
"""

private const val EXTRA_MEMBERS_COMMENT: String = """  # Modules the layout packs that this plugin's `<content>` does not name - a `PluginLayout.withModule` call.
"""

private const val LIB_ROOT_JARS_COMMENT: String = """  # Members whose jar goes to `lib/<module>.jar` where the derivation says `lib/modules/<module>.jar`.
"""

private const val SEPARATE_JARS_COMMENT: String = """  # Members that get a jar of their own where the derivation packs them into the plugin's main jar.
"""

private const val RAW_MEMBERS_COMMENT: String = """  # Members this plugin does not hand over, because a second jar of this plugin holds the module too.
"""

private const val VETOED_MEMBERS_COMMENT: String = """  # Members this plugin packs beside another content module, which takes the packing target away from every
  # plugin that ships the module.
"""

private const val MERGED_LIBRARIES_COMMENT: String = """  # The module libraries a member's jar really merges, where the layout excluded some of them. The whole set,
  # so one row states the jar rather than a patch of it.
"""

private const val LIBRARIES_COMMENT: String = """  # Libraries the layout packs that no member declares. A row with no `module` is a project library.
"""
