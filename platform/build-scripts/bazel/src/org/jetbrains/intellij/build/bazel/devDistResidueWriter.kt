// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Writes the content half of every plugin's `dev-dist.yaml`, read off the checked-in `plugin-content.yaml`.
 *
 * The migration writer of this arc. `derivePluginContentCandidacy` and `derivePluginContent` state a plugin's content
 * from the project model, and this states what is left - the `PluginLayout` decisions the model cannot reach. The
 * checked-in report is the authority it reads them from, because that report is the verification record of what the real
 * distribution build packs.
 *
 * Idempotent, and it has to be run to a fixed point. Some rows the second pass writes need the repo-global candidacy
 * fold, which is folded over the residues the first pass wrote - so a first run over a tree with no residue writes the
 * candidacy rows, and the second run adds the rows that depend on them. A third run writes nothing.
 *
 * A plugin whose residue would be empty gets no file, and an existing empty one is deleted. So an absent file always
 * means pure convention.
 */
internal fun writeDevDistResidues(moduleList: ModuleList, context: BazelBuildFileGenerator): DevDistResidueWriteResult {
  var written = 0
  var deleted = 0
  var unchanged = 0
  val rowsPerField = LinkedHashMap<String, Int>()
  val pluginsPerField = LinkedHashMap<String, Int>()
  // The fold over the checked-in reports, with no override in play. It is the authority for every row this writes: the
  // reports are the verification record of what the real distribution build packs, and the checked-in `BUILD.bazel` was
  // written with this fold.
  val reportCandidates = foldPluginContentCandidacy(
    reports = moduleList.allModules.mapNotNull { it.pluginContentReport },
    overrides = emptyMap(),
  )
  for (module in moduleList.community + moduleList.ultimate) {
    if (module.pluginContentReport == null) {
      continue
    }
    val file = module.contentRoots.firstOrNull()?.resolve(DEV_DIST_RESIDUE_FILE_NAME) ?: continue
    val section = synthesizeContentResidue(
      module = module,
      moduleList = moduleList,
      context = context,
      reportCandidates = reportCandidates,
    )
    for ((field, rows) in contentResidueFieldRows(section)) {
      rowsPerField.merge(field, rows, Int::plus)
      pluginsPerField.merge(field, 1, Int::plus)
    }
    val text = composeDevDistResidueText(mainModule = module.module.name, content = section, existing = file)
    val before = if (Files.isRegularFile(file)) file.readText() else null
    when {
      text == null && before == null -> Unit
      text == null -> {
        file.deleteIfExists()
        deleted++
      }
      text == before -> unchanged++
      else -> {
        Files.createDirectories(file.parent)
        file.writeText(text)
        written++
      }
    }
  }
  writePluginContentPopulation(moduleList = moduleList, context = context)
  return DevDistResidueWriteResult(
    written = written,
    deleted = deleted,
    unchanged = unchanged,
    rowsPerField = rowsPerField,
    pluginsPerField = pluginsPerField,
  )
}

/**
 * Writes the content population off the checked-in reports, so the converter can stop probing for them.
 *
 * The migration writer of `PLUGIN_CONTENT_POPULATION_FILE_NAME`, and the same transitional role
 * [writeDevDistResidues] has. The population is a product question, so `plugin-model-tool` owns this file once it states
 * the answer; [checkPluginContentPopulation] fails the run while the two disagree.
 */
private fun writePluginContentPopulation(moduleList: ModuleList, context: BazelBuildFileGenerator) {
  val names = (moduleList.community + moduleList.ultimate)
    .filter { it.pluginContentReportFile != null }
    .map { it.module.name }
    .sorted()
  val text = buildString {
    append(POPULATION_HEADER)
    for (name in names) {
      append(name).append('\n')
    }
  }
  val file = (context.ultimateRoot?.resolve("community") ?: context.communityRoot)
    .resolve("build/$PLUGIN_CONTENT_POPULATION_FILE_NAME")
  if (!Files.isRegularFile(file) || file.readText() != text) {
    file.writeText(text)
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
)

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
): ContentResidueSection? {
  val entries = module.pluginContentReport.orEmpty()
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
  val derived = derivePluginContent(module = module, moduleList = moduleList, context = context, residue = residueOf(section))
  val reportMembers = reportMemberNames(module)
  val extraMembers = (reportMembers - derived.memberNames.toSet()).sorted()
  val withExtras = residueOf(section.copy(extraMembers = extraMembers))
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
  val libraries = missingLibraries(
    module = module,
    moduleList = moduleList,
    context = context,
    residue = residueOf(section.copy(extraMembers = extraMembers, rawMembers = rawMembers.sorted())),
  )

  val result = section.copy(
    extraMembers = extraMembers,
    rawMembers = rawMembers.sorted(),
    libraries = libraries,
  )
  return result.takeIf { contentResidueFieldRows(it).isNotEmpty() }
}

/** [contentResidueOf] for a section this run composed rather than read from a file. */
private fun residueOf(section: ContentResidueSection): PluginContentResidue {
  return PluginContentResidue(
    extraMembers = section.extraMembers.toSet(),
    libRootJars = section.libRootJars.toSet(),
    rawMembers = section.rawMembers.toSet(),
    vetoedMembers = section.vetoedMembers.toSet(),
    separateJars = section.separateJars.toSet(),
    mergedLibraries = section.mergedLibraries.mapValues { it.value.toSet() },
    libraries = section.libraries.mapTo(LinkedHashSet()) { RecordedLibrary(name = it.name, ownerModule = it.module) },
  )
}

/**
 * The libraries the report records and the derivation, with [residue] in play, still does not reach.
 *
 * Stating the members is what closes most of these: a library an extra member declares is reached by the member walk as
 * soon as the member is stated. So this asks the derivation what it produced rather than copying the report's whole
 * recorded set, which would put a row in the file for every library a member already declares.
 */
private fun missingLibraries(
  module: ModuleDescriptor,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
  residue: PluginContentResidue,
): List<ResidueLibraryRow> {
  val projected = computePluginContent(module = module, moduleList = moduleList, context = context)
  val projectedLabels = projected.content?.libraryContainerLabels.orEmpty().toSet()
  if (projectedLabels.isEmpty()) {
    return emptyList()
  }
  val derived = derivePluginContent(module = module, moduleList = moduleList, context = context, residue = residue)
  val derivedLabels = derived.result.content?.libraryContainerLabels.orEmpty().toSet()
  if (projectedLabels.all { it in derivedLabels }) {
    return emptyList()
  }
  // One candidate row per recorded library, filtered by whether adding it changes the label set. The report records a
  // library by (name, owning module), which is the pair the converter looks one up by; a label would carry the artifact
  // version instead - see [computeLibraryContainerLabels].
  val rows = ArrayList<ResidueLibraryRow>()
  var current = residue
  for (recorded in recordedLibraries(entries = module.pluginContentReport.orEmpty(), handedOver = emptySet()).sortedWith(
    compareBy({ it.ownerModule ?: "" }, { it.name })
  )) {
    val candidate = PluginContentResidue(
      extraMembers = current.extraMembers,
      libRootJars = current.libRootJars,
      rawMembers = current.rawMembers,
      vetoedMembers = current.vetoedMembers,
      separateJars = current.separateJars,
      mergedLibraries = current.mergedLibraries,
      libraries = current.libraries + recorded,
    )
    val grown = derivePluginContent(module = module, moduleList = moduleList, context = context, residue = candidate)
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
private fun reportMemberNames(module: ModuleDescriptor): Set<String> {
  val result = LinkedHashSet<String>()
  for (entry in module.pluginContentReport.orEmpty()) {
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
  put("extra_members", section.extraMembers.size)
  put("lib_root_jars", section.libRootJars.size)
  put("raw_members", section.rawMembers.size)
  put("vetoed_members", section.vetoedMembers.size)
  put("separate_jars", section.separateJars.size)
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
 */
private fun composeDevDistResidueText(mainModule: String, content: ContentResidueSection?, existing: Path): String? {
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
  check(mainModule.isNotEmpty())
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
