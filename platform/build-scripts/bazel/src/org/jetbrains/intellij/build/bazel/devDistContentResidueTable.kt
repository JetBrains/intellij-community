// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Every plugin's content residue, in one file under `community/build/`; see [ContentResidueSection] for the fields.
 *
 * Central, and not beside the plugin. The hermetic `bazel-targets.json` run materializes its project model out of
 * declared labels, and it reads the residue: a run that cannot see one states a `contentModuleJarTarget` the
 * full-checkout run does not. A residue beside the plugin needs one label per plugin for that, and a residue on the
 * plugin's own `dev_dist_plugin` call reaches that run by no route at all, because no `BUILD.bazel` is in the tree it
 * materializes. `@community//build:dev_dist_plugin_tables` names this file, so one declaration hands over every row.
 *
 * `dev_dist_plugin_extra_members.txt` keeps the eighth field, the merged members, and the split is by reader. That
 * field is the one the monorepo reads, through `readDevDistExtraMembers`, and its two callers parse neither Starlark
 * nor this table. This file has one reader, the converter, so the two files answer two different questions and each
 * stays as simple as its own readers need. [contentResidueOf] joins the halves.
 *
 * Its own file, and not a sixth section of [PLUGIN_MODEL_TABLES_FILE_NAME], because the producer differs.
 * `plugin-model-tool` writes all five of those sections. This file's producer is
 * `--write-dev-dist-residue --content-report=<zip>`, which reads a distribution build's content report, and
 * `plugin-model-tool` cannot read one. One writer per file is what stops either tool from stating a table it cannot
 * derive; [PLUGIN_CONTENT_POPULATION_FILE_NAME] is a separate file for the same reason and has the same producer.
 */
internal const val PLUGIN_CONTENT_RESIDUE_FILE_NAME: String = "dev_dist_plugin_content_residue.txt"

/**
 * The field vocabulary, by row field name, valued by the token count that follows the name.
 *
 * Nine names for seven fields. `merged_libraries` takes two names because a member that merges no library at all is a
 * real row, and a two-token row cannot state an empty set. `libraries` takes two because a row states an owning module
 * or states none, and a project library has none.
 *
 * One map, so the reader has one arity rule and a failure message can name every field it knows. A field this map does
 * not hold is a hard error, because [contentResidueRows] states the same vocabulary from the writing side and no
 * compiler pins the two to each other.
 */
private val CONTENT_RESIDUE_FIELD_ARITY: Map<String, Int> = mapOf(
  LIB_ROOT_JAR_FIELD to 1,
  MEMBER_JAR_FIELD to 2,
  MERGED_LIBRARY_FIELD to 2,
  MERGES_NO_LIBRARY_FIELD to 1,
  MODULE_LIBRARY_FIELD to 2,
  PROJECT_LIBRARY_FIELD to 1,
  RAW_MEMBER_FIELD to 1,
  SEPARATE_JAR_FIELD to 1,
  VETOED_MEMBER_FIELD to 1,
)

/** See [ContentResidueSection.libRootJars]. No plugin of this tree states one today. */
internal const val LIB_ROOT_JAR_FIELD: String = "lib_root_jar"

/** See [ContentResidueSection.memberJars]. The one field whose second token is a path. */
internal const val MEMBER_JAR_FIELD: String = "member_jar"

/** See [ContentResidueSection.mergedLibraries]. One row per library the member's jar really merges. */
internal const val MERGED_LIBRARY_FIELD: String = "merged_library"

/** See [ContentResidueSection.mergedLibraries]. The member's jar merges no library, which [MERGED_LIBRARY_FIELD] cannot say. */
internal const val MERGES_NO_LIBRARY_FIELD: String = "merges_no_library"

/** See [ContentResidueSection.libraries], for a library an owning module declares. No plugin states one today. */
internal const val MODULE_LIBRARY_FIELD: String = "module_library"

/** See [ContentResidueSection.libraries], for a project library, which has no owning module. */
internal const val PROJECT_LIBRARY_FIELD: String = "project_library"

/** See [ContentResidueSection.rawMembers]. */
internal const val RAW_MEMBER_FIELD: String = "raw_member"

/** See [ContentResidueSection.separateJars]. */
internal const val SEPARATE_JAR_FIELD: String = "separate_jar"

/** See [ContentResidueSection.vetoedMembers]. */
internal const val VETOED_MEMBER_FIELD: String = "vetoed_member"

/**
 * Reads the whole table, by plugin main module, or an empty map when no run has written it.
 *
 * Nothing rather than a failure for an absent file, the rule [readDevDistPluginModelTables] states: this generator's
 * own integration tests each build a throwaway project, and a repository rule that fails takes down the very tool that
 * regenerates the file.
 *
 * A **present** file is read strictly. An unknown field, a wrong token count and an empty token are all errors, because
 * each one drops a `PluginLayout` decision and a jar that differs from the distribution's is not noticed until class
 * load. A plugin stating two rows of the same fact is an error too.
 *
 * The reader sorts every field, so a section it answers equals the section [contentResidueRows] would write. Nothing
 * downstream depends on the order: [ContentResidueSection.toResidue] turns every field into a set.
 */
internal fun readPluginContentResidue(file: Path): Map<String, ContentResidueSection> {
  if (!file.exists()) {
    return emptyMap()
  }
  val rows = LinkedHashMap<String, ContentResidueRows>()
  for (raw in file.readText().lineSequence()) {
    val line = raw.trim()
    if (line.isEmpty() || line.startsWith('#')) {
      continue
    }
    parseContentResidueRow(file = file, row = line, result = rows)
  }
  return rows.mapValues { it.value.build() }
}

/**
 * The rows one plugin states, in the order [renderPluginContentResidue] writes them.
 *
 * The one place the seven fields cross from the section's shape into the file's, and the writer of the vocabulary
 * [CONTENT_RESIDUE_FIELD_ARITY] reads. Also the unit the direction rule compares, so a row has to state the whole
 * decision: the field is part of it, because one member name means a different decision under each field.
 *
 * Sorted, so that a set difference of two plugins' rows is the whole change between them and a regeneration writes no
 * diff noise.
 */
internal fun contentResidueRows(plugin: String, section: ContentResidueSection): List<String> {
  val rows = ArrayList<String>()
  fun row(field: String, vararg tokens: String) {
    rows.add((listOf(plugin, field) + tokens).joinToString("\t"))
  }
  for (member in section.libRootJars.sorted()) {
    row(LIB_ROOT_JAR_FIELD, member)
  }
  for ((member, jars) in section.memberJars.toSortedMap()) {
    for (jar in jars.sorted()) {
      row(MEMBER_JAR_FIELD, member, jar)
    }
  }
  for ((member, libraries) in section.mergedLibraries.toSortedMap()) {
    if (libraries.isEmpty()) {
      row(MERGES_NO_LIBRARY_FIELD, member)
    }
    for (library in libraries.sorted()) {
      row(MERGED_LIBRARY_FIELD, member, library)
    }
  }
  for (library in section.libraries.sortedWith(compareBy({ it.module ?: "" }, { it.name }))) {
    val module = library.module
    if (module == null) {
      row(PROJECT_LIBRARY_FIELD, library.name)
    }
    else {
      row(MODULE_LIBRARY_FIELD, module, library.name)
    }
  }
  for (member in section.rawMembers.sorted()) {
    row(RAW_MEMBER_FIELD, member)
  }
  for (member in section.separateJars.sorted()) {
    row(SEPARATE_JAR_FIELD, member)
  }
  for (member in section.vetoedMembers.sorted()) {
    row(VETOED_MEMBER_FIELD, member)
  }
  return rows
}

/** The whole file, with the plugins in name order and a blank line between them. */
internal fun renderPluginContentResidue(sections: Map<String, ContentResidueSection>): String {
  return buildString {
    append(CONTENT_RESIDUE_HEADER)
    for ((plugin, section) in sections.toSortedMap()) {
      val rows = contentResidueRows(plugin = plugin, section = section)
      if (rows.isEmpty()) {
        continue
      }
      append('\n')
      for (row in rows) {
        append(row).append('\n')
      }
    }
  }
}

/**
 * One plugin's rows, collected until the read ends.
 *
 * A plugin's fields arrive over several rows, so a mutable accumulator is what reads them and [build] hands the
 * immutable section on. The twin of `DescriptorResidueRows`.
 */
private class ContentResidueRows {
  @JvmField val libRootJars: MutableList<String> = ArrayList()
  @JvmField val memberJars: MutableMap<String, MutableList<String>> = LinkedHashMap()
  @JvmField val mergedLibraries: MutableMap<String, MutableList<String>> = LinkedHashMap()
  @JvmField val libraries: MutableList<ResidueLibraryRow> = ArrayList()
  @JvmField val rawMembers: MutableList<String> = ArrayList()
  @JvmField val separateJars: MutableList<String> = ArrayList()
  @JvmField val vetoedMembers: MutableList<String> = ArrayList()

  fun build(): ContentResidueSection = ContentResidueSection(
    libRootJars = libRootJars.sorted(),
    rawMembers = rawMembers.sorted(),
    vetoedMembers = vetoedMembers.sorted(),
    separateJars = separateJars.sorted(),
    memberJars = memberJars.toSortedMap().mapValues { it.value.sorted() },
    mergedLibraries = mergedLibraries.toSortedMap().mapValues { it.value.sorted() },
    libraries = libraries.sortedWith(compareBy({ it.module ?: "" }, { it.name })),
  )
}

/**
 * Reads one row into [result].
 *
 * `<plugin main module>`, the field name, then that field's own tokens, tab separated. Tabs, because a jar path of
 * `member_jar` and a plugin directory both hold a space today.
 */
private fun parseContentResidueRow(file: Path, row: String, result: MutableMap<String, ContentResidueRows>) {
  val fields = row.split('\t')
  if (fields.size < 2 || fields[0].isEmpty()) {
    error("$file: a row states `<plugin main module>\t<field>` and then that field's own tokens, got `$row`")
  }
  val field = fields[1]
  val arity = CONTENT_RESIDUE_FIELD_ARITY.get(field)
              ?: error("$file: `$field` is not a field of this file. It states ${CONTENT_RESIDUE_FIELD_ARITY.keys.joinToString { "`$it`" }}")
  val values = fields.drop(2)
  if (values.size != arity || values.any { it.isEmpty() }) {
    error("$file: the `$field` field takes $arity tokens after the field name, got `$row`")
  }
  val rows = result.computeIfAbsent(fields[0]) { ContentResidueRows() }
  when (field) {
    LIB_ROOT_JAR_FIELD -> rows.libRootJars.add(values[0])
    MEMBER_JAR_FIELD -> rows.memberJars.computeIfAbsent(values[0]) { ArrayList() }.add(values[1])
    MERGED_LIBRARY_FIELD -> rows.mergedLibraries.computeIfAbsent(values[0]) { ArrayList() }.add(values[1])
    // The member's key with no library under it. `computeIfAbsent` alone is the whole row, and a `merged_library` row
    // for the same member later fills the list, which is the same section either order gives.
    MERGES_NO_LIBRARY_FIELD -> rows.mergedLibraries.computeIfAbsent(values[0]) { ArrayList() }
    MODULE_LIBRARY_FIELD -> rows.libraries.add(ResidueLibraryRow(module = values[0], name = values[1]))
    PROJECT_LIBRARY_FIELD -> rows.libraries.add(ResidueLibraryRow(name = values[0]))
    RAW_MEMBER_FIELD -> rows.rawMembers.add(values[0])
    SEPARATE_JAR_FIELD -> rows.separateJars.add(values[0])
    VETOED_MEMBER_FIELD -> rows.vetoedMembers.add(values[0])
  }
}

private const val CONTENT_RESIDUE_HEADER: String = """# Generated - do not edit.
#
# What a plugin's dev-distribution content needs that the convention does not give, by plugin main module.
#
# The JPS-to-Bazel converter derives a plugin's content from the project model: the members come from the
# plugin's own `<content>`, the jar of each member from the loading rule and the member's own descriptor, the
# libraries from what the members declare. This file is the remainder. A plugin with no row is pure convention,
# and every row states one `PluginLayout` decision.
#
# `<plugin main module>`, a field name, then that field's own tokens, tab separated. Tabs, because a jar path
# and a plugin directory both hold a space today.
#
# Nine field names for seven fields. `merges_no_library` says that a member's jar merges no library at all,
# which `merged_library` cannot say. `project_library` and `module_library` split one field because a row
# states an owning module or states none.
#
# `member_jar` is the one field whose token is a path. `PluginLayout.withModule(name, jarName)` states a free
# string that no rule derives, so the row has to carry it. Every other token is a name. A row is never a Bazel
# label, because a label carries the artifact version of a library.
#
# Two fields have no row in this tree today, `lib_root_jar` and `module_library`. They are part of the
# vocabulary, so a reader who finds no row for one has found nothing wrong.
#
# This file has two peculiarities worth stating rather than leaving to a reader.
#
# It is not a sixth section of `dev_dist_plugin_model_tables.txt`, although the row shape is that file's
# `plugin_descriptor_residue` shape. The producer differs. `plugin-model-tool` writes all five sections of that
# file, and this file is written by `./build/jpsModelToBazel.cmd --write-dev-dist-residue
# --content-report=<zip>`, which reads a distribution build's content report. `plugin-model-tool` cannot read
# one. One writer per file is what stops either tool from stating a table it cannot derive, and
# `dev_dist_plugin_content_population.txt` is a separate file for that same reason and has this same producer.
#
# The eighth field of a plugin's content residue, its merged members, is in
# `dev_dist_plugin_extra_members.txt` and not here. The split is by reader. That field is the one the monorepo
# reads, through `readDevDistExtraMembers`, and neither of its callers runs the converter or parses this table.
# This file has one reader, the converter.
"""
