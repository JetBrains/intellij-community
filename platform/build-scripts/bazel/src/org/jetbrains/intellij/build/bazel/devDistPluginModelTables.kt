// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.bazel

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * The one file `plugin-model-tool` hands this converter, beside the project model.
 *
 * Three tables, one per section, and the tool writes all three in one run: the layout exclusions that make a module
 * output unpackable, the candidacy answer a community-only checkout folds the other way, and the (plugin, layout
 * variant) keys a `dev_dist_plugin_descriptor` target exists for. They were three files, and the merge is by producer.
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

/**
 * What one read of [PLUGIN_MODEL_TABLES_FILE_NAME] produced.
 *
 * Three fields and one read. Each field used to be a file with a reader of its own, and each reader repeated the same
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
)

/** Every section empty, which is what an absent file answers. */
internal val EMPTY_PLUGIN_MODEL_TABLES: DevDistPluginModelTables =
  DevDistPluginModelTables(contentModuleJarVetoes = emptySet(), contentCandidateOverrides = emptyMap(), descriptorPopulation = emptyMap())

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
  var section: String? = null
  for (raw in file.readText().lineSequence()) {
    val line = raw.trim()
    if (line.isEmpty() || line.startsWith('#')) {
      continue
    }
    if (line.startsWith('[') && line.endsWith(']')) {
      section = line.substring(1, line.length - 1)
      if (section != CONTENT_VETOES_SECTION && section != CONTENT_CANDIDATE_OVERRIDES_SECTION && section != DESCRIPTOR_POPULATION_SECTION) {
        error(
          "$file: `$section` is not a section of this file. It states `$CONTENT_VETOES_SECTION`," +
          " `$CONTENT_CANDIDATE_OVERRIDES_SECTION` and `$DESCRIPTOR_POPULATION_SECTION`"
        )
      }
      continue
    }
    when (section) {
      CONTENT_VETOES_SECTION -> vetoes.add(line)
      CONTENT_CANDIDATE_OVERRIDES_SECTION -> parseContentCandidateOverrideRow(file = file, row = line, result = overrides)
      DESCRIPTOR_POPULATION_SECTION -> descriptorPopulation.computeIfAbsent(line.substringBefore('/')) { ArrayList() }.add(line.substringAfter('/', ""))
      else -> error("$file: `$line` is above the first section header, so no section states it")
    }
  }
  return DevDistPluginModelTables(
    contentModuleJarVetoes = vetoes,
    contentCandidateOverrides = overrides,
    descriptorPopulation = descriptorPopulation,
  )
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
