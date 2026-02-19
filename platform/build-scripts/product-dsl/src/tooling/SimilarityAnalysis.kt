// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.tooling

import com.intellij.platform.pluginGraph.PluginGraph
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.productLayout.traversal.collectModuleSetDirectModuleNames
import org.jetbrains.intellij.build.productLayout.traversal.collectProductModuleSetNames
import org.jetbrains.intellij.build.productLayout.traversal.isModuleSetTransitivelyNested

/**
 * Detects overlapping or redundant module sets.
 * CRITICAL FIX: Filters out intentional nested set inclusions (e.g., libraries ⊃ `libraries.core`).
 * ENHANCED: Now checks TRANSITIVE nested relationships (e.g., essential → libraries → `libraries.core`).
 * Only reports actual duplications, not designed composition patterns.
 *
 * @param allModuleSets List of all module sets with metadata
 * @param pluginGraph Plugin graph for module set traversal
 * @param minOverlapPercent Minimum overlap percentage (0-100) to include in results
 * @return List of overlapping module set pairs sorted by overlap percentage (descending)
 */
internal suspend fun detectModuleSetOverlap(
  allModuleSets: List<ModuleSetMetadata>,
  pluginGraph: PluginGraph,
  minOverlapPercent: Int = 50
): List<ModuleSetOverlap> {
  return coroutineScope {
    val comparisons = ArrayList<Deferred<ModuleSetOverlap?>>()

    for (i in allModuleSets.indices) {
      for (j in i + 1 until allModuleSets.size) {
        comparisons.add(async {
          computeOverlap(allModuleSets[i], allModuleSets[j], pluginGraph, minOverlapPercent)
        })
      }
    }

    comparisons.mapNotNull { it.await() }.sortedByDescending { it.overlapPercent }
  }
}

/**
 * Computes overlap between two module sets.
 * Returns null if no overlap or if one set is a transitive parent of the other.
 */
private fun computeOverlap(
  ms1: ModuleSetMetadata,
  ms2: ModuleSetMetadata,
  pluginGraph: PluginGraph,
  minOverlapPercent: Int
): ModuleSetOverlap? {
  // Skip if one explicitly includes the other as a nested set (direct or transitive)
  if (isModuleSetTransitivelyNested(pluginGraph, ms1.moduleSet.name, ms2.moduleSet.name) ||
      isModuleSetTransitivelyNested(pluginGraph, ms2.moduleSet.name, ms1.moduleSet.name)) {
    return null  // Intentional composition via nesting, not duplication
  }

  // Calculate overlap based on direct modules only (not nested)
  val modules1 = collectModuleSetDirectModuleNames(pluginGraph, ms1.moduleSet.name)
  val modules2 = collectModuleSetDirectModuleNames(pluginGraph, ms2.moduleSet.name)

  val intersection = modules1.intersect(modules2)
  if (intersection.isEmpty()) return null

  val union = modules1.union(modules2)
  val overlapPercent = (intersection.size * 100) / union.size

  if (overlapPercent < minOverlapPercent) return null

  val relationship = when (intersection.size) {
    modules1.size -> "subset"  // ms1 ⊂ ms2
    modules2.size -> "superset"  // ms1 ⊃ ms2
    else -> "overlap"
  }

  return ModuleSetOverlap(
    moduleSet1 = ms1.moduleSet.name,
    moduleSet2 = ms2.moduleSet.name,
    location1 = ms1.location,
    location2 = ms2.location,
    relationship = relationship,
    overlapPercent = overlapPercent,
    sharedModules = intersection.size,
    totalModules1 = modules1.size,
    totalModules2 = modules2.size,
    recommendation = generateOverlapRecommendation(ms1, ms2, relationship, overlapPercent)
  )
}

/**
 * Generates recommendation for overlapping module sets.
 */
private fun generateOverlapRecommendation(
  ms1: ModuleSetMetadata,
  ms2: ModuleSetMetadata,
  relationship: String,
  overlapPercent: Int
): String {
  return when (relationship) {
    "subset" -> "${ms1.moduleSet.name} is fully contained in ${ms2.moduleSet.name}. Consider removing ${ms1.moduleSet.name}."
    "superset" -> "${ms2.moduleSet.name} is fully contained in ${ms1.moduleSet.name}. Consider removing ${ms2.moduleSet.name}."
    else -> if (overlapPercent >= 80) {
      "High overlap ($overlapPercent%). Review if modules should be reorganized."
    } else {
      "Moderate overlap ($overlapPercent%). Consider extracting shared modules."
    }
  }
}

/**
 * Analyzes similarity between products based on module set overlap.
 * Used to identify products with similar compositions for potential refactoring.
 *
 * OPTIMIZED: Uses parallel comparison for O(n²) product pairs.
 *
 * @param products List of all products
 * @param similarityThreshold Minimum similarity (0.0 to 1.0) to include in results
 * @return List of similar product pairs sorted by similarity (descending)
 */
internal suspend fun analyzeProductSimilarity(
  products: List<ProductSpec>,
  pluginGraph: PluginGraph,
  similarityThreshold: Double = 0.7
): List<ProductSimilarityPair> {
  return coroutineScope {
    val productsWithContent = products.filter { it.contentSpec != null }
    val comparisons = ArrayList<Deferred<ProductSimilarityPair?>>()

    for (i in productsWithContent.indices) {
      for (j in i + 1 until productsWithContent.size) {
        comparisons.add(async {
          computeProductSimilarity(productsWithContent[i], productsWithContent[j], pluginGraph, similarityThreshold)
        })
      }
    }

    comparisons.mapNotNull { it.await() }.sortedByDescending { it.similarity }
  }
}

/**
 * Computes similarity between two products.
 * Returns null if similarity is below threshold.
 */
private fun computeProductSimilarity(
  p1: ProductSpec,
  p2: ProductSpec,
  pluginGraph: PluginGraph,
  similarityThreshold: Double
): ProductSimilarityPair? {
  val sets1 = collectProductModuleSetNames(pluginGraph, p1.name)
  val sets2 = collectProductModuleSetNames(pluginGraph, p2.name)

  val shared = sets1.intersect(sets2)
  val union = sets1.union(sets2)
  val similarity = if (union.isNotEmpty()) shared.size.toDouble() / union.size else 0.0

  if (similarity < similarityThreshold) return null

  return ProductSimilarityPair(
    product1 = p1.name,
    product2 = p2.name,
    similarity = similarity,
    moduleSetSimilarity = similarity,
    sharedModuleSets = shared.toList().sorted(),
    uniqueToProduct1 = sets1.minus(sets2).toList().sorted(),
    uniqueToProduct2 = sets2.minus(sets1).toList().sorted()
  )
}
