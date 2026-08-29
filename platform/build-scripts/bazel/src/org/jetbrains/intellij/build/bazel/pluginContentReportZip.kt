// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * One plugin of a distribution build's content report, narrowed to what packing and membership need.
 *
 * The narrow mirror of `com.intellij.platform.distributionContent.PluginContentReport`, for the reason [RecipeEntry]
 * gives: that class lives in the platform, and this generator is a standalone Bazel module that gets the platform as
 * published Maven artifacts. `ContentReportSchemaTest` fails on a rename of any field here.
 *
 * [os] and [arch] are the report's own selectors, and they are separate from [RecipeEntry.os]. A build writes one plugin
 * per target platform where the plugin's layout differs by one, so a plugin can appear several times under one
 * [mainModule].
 */
@Serializable
internal data class ReportedPlugin(
  val mainModule: String = "",
  val os: String? = null,
  val arch: String? = null,
  val content: List<RecipeEntry> = emptyList(),
)

/** The two entries of a `content-report.zip` that carry a per-plugin layout. */
private val PLUGIN_REPORT_ENTRIES = listOf("bundled-plugins.yaml", "non-bundled-plugins.yaml")

/**
 * Every plugin of one or more distribution builds' content reports, by main module name.
 *
 * The report zip is the verification record of what a real distribution build packs, and it is the authority both residue
 * modes read. `DistributionJARsBuilder` writes it to `<artifacts>/content-report.zip`, and `readContentReportZip` of
 * `contentChecker.kt` is the platform-side reader of the same four entries.
 *
 * ### Why this takes several files
 *
 * One build reports one product, and no product packs the whole population. The largest, IDEA Ultimate, reported 472
 * plugins of a 516-line population, and 'All Packaging Tests' produces one zip per product. So the authority for the
 * residue and the population is the **union over the products**, and a run given one product's zip can state only part of
 * it. [readPluginContentReportZips] takes the whole set, and `--content-report=` may be repeated or name a directory.
 *
 * ### Why a union, and not a comparison
 *
 * A plugin's entries are **unioned**, whether they come from two target-platform variants of one product or from two
 * products. That is the same rule `mergePerOsPluginContent` of `contentChecker.kt` applies, and this reaches it without a
 * second implementation of the union: every read the converter does over a plugin's entries is a set fold, so
 * concatenating the entries and dropping the duplicates is the whole operation. [RecipeEntry] declares no size and no file
 * list, so its structural equality already ignores the fields that normalization drops.
 *
 * The union is what makes several products safe to combine. A member one product packs beside another content module and a
 * second product packs alone yields both entries, and the candidacy fold then vetoes the member for every plugin. The
 * veto is the conservative answer, so adding a product can only take a packing target away, never invent one.
 *
 * A plugin whose entries genuinely disagree is reported. A plugin that simply appears in several products with the same
 * layout is not, because the duplicates collapse and there is nothing to decide.
 *
 * ### Why this refuses rather than reads less
 *
 * `recipeYaml` runs with `strictMode = false`, so a renamed field of the platform's `PluginContentReport` would decode as
 * the default instead of failing, and every plugin would arrive with no main module. The population file and the residues
 * are written from this map, so a quiet shrink here would empty the population and take 356 content leaves with it.
 */
internal fun readPluginContentReportZips(files: List<Path>): Map<String, List<RecipeEntry>> {
  check(files.isNotEmpty()) { "No content report named. Pass --content-report=<zip or directory> at least once" }
  val plugins = ArrayList<Pair<Path, ReportedPlugin>>()
  for (file in files) {
    val read = readOneReportZip(file)
    check(read.isNotEmpty()) {
      "$file names no plugin. A distribution build packs plugins, so the zip is not one of its reports"
    }
    for (plugin in read) {
      plugins.add(file to plugin)
    }
  }

  val result = LinkedHashMap<String, MutableList<RecipeEntry>>()
  val layouts = LinkedHashMap<String, MutableList<List<RecipeEntry>>>()
  for ((file, plugin) in plugins) {
    check(plugin.mainModule.isNotEmpty()) {
      "$file reports a plugin with no main module. The platform declares `PluginContentReport.mainModule` without a" +
      " default, so a report cannot omit it, and `ReportedPlugin` no longer matches the shape the build writes"
    }
    layouts.getOrPut(plugin.mainModule) { ArrayList() }.add(plugin.content)
    result.getOrPut(plugin.mainModule) { ArrayList() }.addAll(plugin.content)
  }
  for ((mainModule, reported) in layouts) {
    val distinct = reported.distinct()
    if (distinct.size > 1) {
      println(
        "WARN: $mainModule is reported with ${distinct.size} differing layouts across ${files.size} report(s)." +
        " The layouts are unioned, and a jar they disagree about vetoes its member instead of being interpreted"
      )
    }
  }
  return result.mapValues { it.value.distinct() }
}

/** Every plugin one report zip names, in the order the two entries hold them. */
private fun readOneReportZip(file: Path): List<ReportedPlugin> {
  val plugins = ArrayList<ReportedPlugin>()
  ZipFile(file.toFile()).use { zip ->
    for (name in PLUGIN_REPORT_ENTRIES) {
      val entry = zip.getEntry(name) ?: error("$file holds no $name; it is not a distribution content report")
      val text = zip.getInputStream(entry).use { it.readBytes().toString(StandardCharsets.UTF_8) }
      if (text.isBlank()) {
        continue
      }
      plugins.addAll(recipeYaml.decodeFromString(ListSerializer(ReportedPlugin.serializer()), text))
    }
  }
  return plugins
}
