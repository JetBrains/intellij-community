// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.bazel

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * The one file `plugin-model-tool` hands this converter, beside the project model.
 *
 * Four tables, one per section, and the tool writes all four in one run: the layout exclusions that make a module
 * output unpackable, the candidacy answer a community-only checkout folds the other way, the (plugin, layout
 * variant) keys a `dev_dist_plugin_descriptor` target exists for, and where a plugin puts its own jars. They were three
 * files, and the merge is by producer.
 *
 * One file so that one Bazel declaration covers the whole hand-off. A table this converter reads and the hermetic
 * `bazel-targets.json` run does not declare is a table that run decides the other way, in silence: the population file
 * was such a table, the run answered the population question from the checked-in reports for months, and the visible
 * symptom was a shrink that looked like the intended shrink.
 * `build/decisions/0008-the-content-leaf-follows-the-descriptor-leaf.md` records it.
 * `@community//build:dev_dist_plugin_tables` is the declaration, and a new section needs no new one.
 *
 * `dev_dist_plugin_content_population.txt` stays its own file, because its producer is not this file's producer. The
 * residue-writing run of this converter states the population from a distribution build's `content-report.zip`, and
 * `plugin-model-tool` only reads it. One writer per file is what keeps a `plugin-model-tool` run from ever stating a
 * population it cannot derive. The same filegroup declares both files.
 *
 * A plain text file, because this reader has to stay independent of Starlark. Under `community/build/`, so a
 * community-only checkout reads the same file. A line naming a plugin that checkout does not have is a line it never
 * matches, which is the verdict the community half of an ultimate checkout reaches too.
 *
 * It states no deviation. A deviation is a fact about one plugin, and it sits beside that plugin in `dev-dist.yaml`.
 */
internal const val PLUGIN_MODEL_TABLES_FILE_NAME: String = "dev_dist_plugin_model_tables.txt"

/** Modules a product or plugin layout exclusion transforms; see [DevDistPluginModelTables.contentModuleJarVetoes]. */
internal const val CONTENT_VETOES_SECTION: String = "content_vetoes"

/** The candidacy rows of [DevDistPluginModelTables.contentCandidateOverrides]. */
internal const val CONTENT_CANDIDATE_OVERRIDES_SECTION: String = "content_candidate_overrides"

/** The keys of [DevDistPluginModelTables.descriptorPopulation]. */
internal const val DESCRIPTOR_POPULATION_SECTION: String = "descriptor_population"

/** The rows of [DevDistPluginModelTables.pluginJarPlacement]. */
internal const val PLUGIN_JAR_PLACEMENT_SECTION: String = "plugin_jar_placement"

/**
 * Where a plugin's own jars go, for the plugin whose layout does not take [pluginJarPlacementConvention].
 *
 * Two tokens, because `PluginLayout` decides the two independently. A plugin that renames its directory usually renames
 * its main jar with it, and `JavaEE` renames only the jar.
 */
internal class PluginJarPlacement(
  /** The plugin's directory under `plugins/`. */
  @JvmField val directory: String,
  /** The plugin's main jar, with the `.jar` suffix, under the plugin's `lib/`. */
  @JvmField val mainJarName: String,
)

/**
 * The placement `PluginLayout` gives a plugin that states none, from the main module name alone.
 *
 * `convertModuleNameToFileName` of `PluginLayout.kt` is the authority, and this spells it again because that file is in
 * the platform and this generator is a standalone Bazel module. `plugin-model-tool` compares a layout against a bare
 * `PluginLayout(mainModule)` rather than against a second spelling, so a change of the rule there makes a row appear for
 * every plugin instead of drifting in silence.
 *
 * 99 of the 109 plugins one dev-distribution fragment hands a content module over for take this, measured on
 * 2026-08-31. So the table states the exceptions and this states the rule.
 */
internal fun pluginJarPlacementConvention(mainModule: String): PluginJarPlacement {
  val directory = mainModule.removePrefix("intellij.").replace('.', '-')
  return PluginJarPlacement(directory = directory, mainJarName = "$directory.jar")
}

/**
 * What one read of [PLUGIN_MODEL_TABLES_FILE_NAME] produced.
 *
 * Four fields and one read. Each field used to be a file with a reader of its own, and each reader repeated the same
 * three line rules.
 */
internal class DevDistPluginModelTables(
  /**
   * Product and plugin layout transformations that make a raw module-output jar ineligible for direct hand-off.
   *
   * A layout excludes paths from these modules, so `PackContentModuleJar` cannot be handed the raw output.
   */
  @JvmField val contentModuleJarVetoes: Set<String>,
  /**
   * The candidacy answers a run cannot fold for itself, by module name. A `null` value is "not a candidate".
   *
   * The candidacy fold is an AND over every plugin of the project, and a community checkout does not hold the ultimate
   * ones. So a community-only run folds a different answer for a community module the ultimate half has an opinion
   * about, in both directions. A module only an ultimate plugin offers is not a candidate at all, and a module an
   * *ultimate* plugin vetoes is not vetoed. Either way that run states `content_module_jar` and
   * `prepacked_content_modules` attributes differing from the checked-in ones, which is what
   * `Assert Bazel Files Are In Sync With JPS Model (Community Only)` fails on.
   *
   * Only those modules are stated, not the whole set. This converter folds both halves and records the global answer
   * for the community modules they disagree about, in `bazel-targets.json`; `plugin-model-tool` only writes those rows
   * out. `communityOnlyCandidacyOverrideRows` is the one producer, and it states 8 rows today, where the whole set was
   * 1892. The sign is that answer, so `+` and `-` both occur. An override always agrees with what an ultimate run
   * folds for itself, by construction, so no run needs to know which kind of checkout it is in.
   *
   * That is why a `+` row also carries the merged library names, space separated after the module name. The fold agrees
   * on a library *set* and not only on a boolean, and the set of a module whose only report is in ultimate is another
   * thing a community-only run cannot see. Without it that run would state a `libraries` attribute the ultimate run
   * refuses, or refuse one the ultimate run states. A `-` row records no library, because a vetoed module has no jar.
   */
  @JvmField val contentCandidateOverrides: Map<String, Set<String>?>,
  /**
   * The layout variants a `dev_dist_plugin_descriptor` target exists for, by plugin main module.
   *
   * One `<main module>` or `<main module>/<variant>` per row. The empty string is the variant of a plugin whose one
   * layout serves every platform.
   *
   * The population is a product question. 516 of this project's modules are a plugin main module the dev distribution
   * states content for, and 173 keys are bundled by `idea`. So `plugin-model-tool` states the population and this
   * reads it.
   */
  @JvmField val descriptorPopulation: Map<String, List<String>>,
  /**
   * Where a plugin puts its own jars, by plugin main module, for the plugin that deviates from the convention.
   *
   * One row per deviating plugin: `<main module> <directory> <main jar name>`. A plugin absent from the table takes
   * [pluginJarPlacementConvention], which is what `PluginLayout` gives a plugin that renames neither.
   *
   * The two names are a `PluginLayout` decision and nothing in the project model states them. `directoryName` and
   * `getMainJarName()` are the fields, and the dev-distribution recipe needs both: a jar's own name in the recipe is
   * `plugins/<directory>/lib/<...>`, and the plugin's main jar is where every member the layout co-packs ends up.
   */
  @JvmField val pluginJarPlacement: Map<String, PluginJarPlacement>,
)

/** Every section empty, which is what an absent file answers. */
internal val EMPTY_PLUGIN_MODEL_TABLES: DevDistPluginModelTables = DevDistPluginModelTables(
  contentModuleJarVetoes = emptySet(),
  contentCandidateOverrides = emptyMap(),
  descriptorPopulation = emptyMap(),
  pluginJarPlacement = emptyMap(),
)

/**
 * Reads what `plugin-model-tool` stated, or nothing when no run has stated it.
 *
 * Nothing rather than a failure, because a project the tool has never run over is a real case and not a mistake: this
 * generator's own integration tests each build a throwaway project. Such a project has one plugin and no ultimate half,
 * so the candidacy fold reaches the same verdict with or without the file, and it needs no descriptor target and no
 * veto. What an absent file costs a real checkout is only the rows it would have corrected, which the sync assertion
 * then reports. Fail open here and fail closed in a BUILD file: a repository rule that fails takes down its own
 * regenerator.
 *
 * A **present** file is read strictly, because every way it can be damaged changes how a module is packed and a jar
 * that differs from the distribution's is not noticed until class-load time. An unknown section name and a row above
 * the first section header are both errors, and each names the file. A silently dropped section would state a whole
 * table the other way.
 *
 * Three line rules, once: drop a `#` row, drop a blank row, and take `[<name>]` as the section that follows.
 */
internal fun readDevDistPluginModelTables(file: Path): DevDistPluginModelTables {
  if (!file.exists()) {
    return EMPTY_PLUGIN_MODEL_TABLES
  }

  val vetoes = LinkedHashSet<String>()
  val overrides = HashMap<String, Set<String>?>()
  val descriptorPopulation = LinkedHashMap<String, MutableList<String>>()
  val jarPlacement = HashMap<String, PluginJarPlacement>()
  var section: String? = null
  for (raw in file.readText().lineSequence()) {
    val line = raw.trim()
    if (line.isEmpty() || line.startsWith('#')) {
      continue
    }
    if (line.startsWith('[') && line.endsWith(']')) {
      section = line.substring(1, line.length - 1)
      if (section !in PLUGIN_MODEL_TABLE_SECTIONS) {
        error("$file: `$section` is not a section of this file. It states ${PLUGIN_MODEL_TABLE_SECTIONS.joinToString { "`$it`" }}")
      }
      continue
    }
    when (section) {
      CONTENT_VETOES_SECTION -> vetoes.add(line)
      CONTENT_CANDIDATE_OVERRIDES_SECTION -> parseContentCandidateOverrideRow(file = file, row = line, result = overrides)
      DESCRIPTOR_POPULATION_SECTION -> descriptorPopulation.computeIfAbsent(line.substringBefore('/')) { ArrayList() }.add(line.substringAfter('/', ""))
      PLUGIN_JAR_PLACEMENT_SECTION -> parsePluginJarPlacementRow(file = file, row = line, result = jarPlacement)
      else -> error("$file: `$line` is above the first section header, so no section states it")
    }
  }
  return DevDistPluginModelTables(
    contentModuleJarVetoes = vetoes,
    contentCandidateOverrides = overrides,
    descriptorPopulation = descriptorPopulation,
    pluginJarPlacement = jarPlacement,
  )
}

/** Every section name, so the reader states one list and the failure message reads it. */
private val PLUGIN_MODEL_TABLE_SECTIONS: List<String> =
  listOf(CONTENT_VETOES_SECTION, CONTENT_CANDIDATE_OVERRIDES_SECTION, DESCRIPTOR_POPULATION_SECTION, PLUGIN_JAR_PLACEMENT_SECTION)

/**
 * Reads one row of [PLUGIN_JAR_PLACEMENT_SECTION] into [result].
 *
 * Three tab-separated tokens, and a row with any other count is a hard error. A two-token row would read the jar name as
 * absent, and the recipe would then name a jar the distribution does not hold.
 *
 * Tabs, unlike the space-separated candidacy rows: a plugin directory holds a space today, and
 * `intellij.javaee.jpa.jpb.model` puts its jars in `JPA Model`.
 */
private fun parsePluginJarPlacementRow(file: Path, row: String, result: MutableMap<String, PluginJarPlacement>) {
  val fields = row.split('\t')
  if (fields.size != 3 || fields.any { it.isEmpty() }) {
    error("$file: a row states `<main module>\t<directory>\t<main jar name>`, got `$row`")
  }
  val previous = result.put(fields[0], PluginJarPlacement(directory = fields[1], mainJarName = fields[2]))
  if (previous != null) {
    error("$file: `${fields[0]}` has two placement rows, and a plugin has one directory")
  }
}

/**
 * Reads one row of [CONTENT_CANDIDATE_OVERRIDES_SECTION] into [result].
 *
 * A row without a sign is a hard error, unlike a missing file: it would silently change how a module is packed. A `-`
 * row with a library is the same class of mistake read from the other side.
 */
private fun parseContentCandidateOverrideRow(file: Path, row: String, result: MutableMap<String, Set<String>?>) {
  val isCandidate = when (row.first()) {
    '+' -> true
    '-' -> false
    else -> error("$file: a row must start with `+` (a candidate) or `-` (not a candidate), got `$row`")
  }
  val fields = row.substring(1).split(' ').filter { it.isNotEmpty() }
  val moduleName = fields.firstOrNull() ?: error("$file: a row must name a module, got `$row`")
  if (isCandidate) {
    result.put(moduleName, fields.drop(1).toSet())
  }
  else {
    if (fields.size != 1) {
      error("$file: a `-` row records no library, got `$row`")
    }
    result.put(moduleName, null)
  }
}
