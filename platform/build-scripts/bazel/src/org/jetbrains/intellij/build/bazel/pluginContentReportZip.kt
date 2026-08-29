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
 * published Maven artifacts.
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
 * Every plugin of a distribution build's content report, by main module name.
 *
 * The report zip is the verification record of what a real distribution build packs, and it is the authority the residue
 * writer and the two comparison switches read. `DistributionJARsBuilder` writes it to
 * `<artifacts>/content-report.zip`, and `readContentReportZip` of `contentChecker.kt` is the platform-side reader of the
 * same four entries.
 *
 * A plugin's target-platform variants are **unioned**, not compared. That is the same rule `mergePerOsPluginContent` of
 * `contentChecker.kt` applies, and this reaches it without a second implementation of the union: every read the
 * converter does over a plugin's entries is a set fold, so concatenating the variants and dropping the duplicates is the
 * whole operation. [RecipeEntry] declares no size and no file list, so its structural equality already ignores the
 * fields that normalization drops.
 *
 * A plugin with several variants is reported. No plugin of the current build has one, so the branch that unions two
 * genuinely differing variants is untested, and the first build that exercises it says so rather than deciding in
 * silence.
 */
internal fun readPluginContentReportZip(file: Path): Map<String, List<RecipeEntry>> {
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

  val result = LinkedHashMap<String, MutableList<RecipeEntry>>()
  val variants = LinkedHashMap<String, Int>()
  for (plugin in plugins) {
    if (plugin.mainModule.isEmpty()) {
      continue
    }
    variants.merge(plugin.mainModule, 1, Int::plus)
    result.getOrPut(plugin.mainModule) { ArrayList() }.addAll(plugin.content)
  }
  for ((mainModule, count) in variants) {
    if (count > 1) {
      println(
        "WARN: $mainModule has $count target-platform variants in $file. The variants are unioned, and a jar the" +
        " variants disagree about vetoes its member instead of being interpreted"
      )
    }
  }
  return result.mapValues { it.value.distinct() }
}
