@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.devDist

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX

/**
 * Manifest facts from the original sources, before preparation changes their representation.
 * [originalMeaningfulSourceCount] counts original sources. A null count defers counting module patch entries until preparation.
 * [sourceManifestPolicies] declares the ordered policies the preparation action returns on its execution sources.
 * The action must preserve these policies, including source-specific exceptions to the jar's default.
 * A single-meaningful-source policy uses the jar's decision after preparation resolves all source counts.
 */
@ApiStatus.Internal
data class PluginSymbolicPreparedSourceManifest(
  @JvmField val originalMeaningfulSourceCount: Int?,
  @JvmField val sourceManifestPolicies: List<String>,
) {
  init {
    require(originalMeaningfulSourceCount == null || originalMeaningfulSourceCount >= 0) { "An original meaningful-source count cannot be negative" }
    require(sourceManifestPolicies.all { it in setOf("keep", "drop", "coverage-agent", "rewrite-boot-class-path", "single-meaningful-source") }) {
      "Prepared sources require concrete source-specific manifest policies"
    }
    require(originalMeaningfulSourceCount != null || sourceManifestPolicies == listOf("keep")) { "Module patches require the keep policy" }
  }
}

/**
 * [libraryFileCounts] is the member count of each library by its id. A `library` source counts one meaningful source per
 * member, the way the packer counts the member files the catalogue lists.
 */
internal fun resolvePluginSymbolicManifest(
  asset: PluginPackingAsset,
  preparedSourceManifests: Map<String, PluginSymbolicPreparedSourceManifest>,
  libraryFileCounts: Map<String, Int>,
  reportGap: (PluginSymbolicLayoutGap) -> Unit,
): PluginPackingAsset {
  val recipe = asset.recipe ?: return asset
  if (recipe.sources.none { it.kind == "prepared" }) return asset
  var complete = true
  var deferred = false
  var meaningfulSources = 0L
  for (source in recipe.sources) {
    val facts = if (source.kind == "prepared") preparedSourceManifests.get(source.input) else null
    if (facts != null && (facts.originalMeaningfulSourceCount == null || "single-meaningful-source" in facts.sourceManifestPolicies)) {
      deferred = true
      continue
    }
    val count = when {
      source.kind == "prepared" -> preparedSourceManifests.get(source.input)?.originalMeaningfulSourceCount
      "lib-module" in source.options ||
      (source.kind in setOf("module", "directory") && source.input.startsWith(LIB_MODULE_PREFIX)) -> 0
      source.kind == "library" -> libraryFileCounts.get(source.input)?.takeIf { it > 0 }
      source.kind in setOf("module", "archive", "zip", "directory", "file") -> 1
      else -> null
    }
    if (count == null) {
      complete = false
      reportGap(PluginSymbolicLayoutGap(
        "prepared-manifest:${source.input}",
        "Jar '${asset.destination}' requires the original meaningful-source count and concrete preparation manifest policies",
      ))
    }
    else {
      meaningfulSources += count
    }
  }
  if (complete && deferred) {
    return asset.copy(recipe = recipe.copy(sources = recipe.sources.map { source ->
      if (source.kind != "prepared") source
      else {
        val facts = preparedSourceManifests.getValue(source.input)
        source.copy(preparedManifest = PreparedSourceManifestRecipe(
          originalMeaningfulSourceCount = facts.originalMeaningfulSourceCount, sourceManifestPolicies = facts.sourceManifestPolicies,
        ))
      }
    }))
  }
  if (!complete || recipe.writer.manifest != "single-meaningful-source") return asset
  return asset.copy(recipe = recipe.copy(writer = recipe.writer.copy(manifest = if (meaningfulSources == 1L) "keep" else "drop")))
}
