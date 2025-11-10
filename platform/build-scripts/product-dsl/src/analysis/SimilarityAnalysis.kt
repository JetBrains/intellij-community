// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.analysis

import org.jetbrains.intellij.build.productLayout.ModuleSet

/**
 * Recursively collects all nested set names (direct + transitive) from a module set.
 *
 * For example, if essential includes libraries, and libraries includes libraries.core,
 * this returns {"libraries", "libraries.core", ...} for essential.
 *
 * @param allModuleSets All module sets to search in
 * @param startSetName The module set to start collecting from
 * @param visited Set of already visited module sets to prevent infinite recursion
 * @return Set of all nested set names (direct and transitive)
 */
fun collectAllNestedSetNames(
  allModuleSets: List<ModuleSet>,
  startSetName: String,
  visited: MutableSet<String> = mutableSetOf()
): Set<String> {
  if (visited.contains(startSetName)) return emptySet()
  visited.add(startSetName)

  val startSet = allModuleSets.firstOrNull { it.name == startSetName } ?: return emptySet()
  val result = mutableSetOf<String>()

  for (nestedSet in startSet.nestedSets) {
    result.add(nestedSet.name)
    // Recursively collect nested sets from this nested set
    result.addAll(collectAllNestedSetNames(allModuleSets, nestedSet.name, visited))
  }

  return result
}

/**
 * Detects overlapping or redundant module sets.
 * CRITICAL FIX: Filters out intentional nested set inclusions (e.g., libraries ⊃ libraries.core).
 * ENHANCED: Now checks TRANSITIVE nested relationships (e.g., essential → libraries → libraries.core).
 * Only reports actual duplications, not designed composition patterns.
 *
 * @param allModuleSets List of all module sets with metadata
 * @param minOverlapPercent Minimum overlap percentage (0-100) to include in results
 * @return List of overlapping module set pairs sorted by overlap percentage (descending)
 */
fun detectModuleSetOverlap(
  allModuleSets: List<ModuleSetMetadata>,
  minOverlapPercent: Int = 50
): List<ModuleSetOverlap> {
  val overlaps = mutableListOf<ModuleSetOverlap>()
  val moduleSetsList = allModuleSets.map { it.moduleSet }

  for (i in allModuleSets.indices) {
    for (j in i + 1 until allModuleSets.size) {
      val ms1 = allModuleSets[i]
      val ms2 = allModuleSets[j]

      // ✅ CRITICAL FIX: Skip if one explicitly includes the other as a nested set
      // This prevents false positives like "libraries overlaps with libraries.core"
      // when libraries explicitly includes libraries.core by design
      //
      // ✅ ENHANCED: Now checks TRANSITIVE relationships too!
      // Example: essential → libraries → libraries.core
      // This prevents false positive for "essential overlaps with libraries.core"
      val ms1AllNestedSetNames = collectAllNestedSetNames(moduleSetsList, ms1.moduleSet.name)
      val ms2AllNestedSetNames = collectAllNestedSetNames(moduleSetsList, ms2.moduleSet.name)

      if (ms1AllNestedSetNames.contains(ms2.moduleSet.name) ||
          ms2AllNestedSetNames.contains(ms1.moduleSet.name)) {
        continue  // Intentional composition via nesting (direct or transitive), not duplication!
      }
      
      // Calculate overlap based on direct modules only (not nested)
      val modules1 = ms1.moduleSet.modules.map { it.name }.toSet()
      val modules2 = ms2.moduleSet.modules.map { it.name }.toSet()
      
      val intersection = modules1.intersect(modules2)
      if (intersection.isEmpty()) continue
      
      val union = modules1.union(modules2)
      val overlapPercent = (intersection.size * 100) / union.size
      
      if (overlapPercent >= minOverlapPercent) {
        val relationship = when {
          intersection.size == modules1.size -> "subset"  // ms1 ⊂ ms2
          intersection.size == modules2.size -> "superset"  // ms1 ⊃ ms2
          else -> "overlap"
        }
        
        overlaps.add(ModuleSetOverlap(
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
        ))
      }
    }
  }
  
  return overlaps.sortedByDescending { it.overlapPercent }
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
 * @param products List of all products
 * @param similarityThreshold Minimum similarity (0.0 to 1.0) to include in results
 * @return List of similar product pairs sorted by similarity (descending)
 */
fun analyzeProductSimilarity(
  products: List<ProductSpec>,
  similarityThreshold: Double = 0.7
): List<ProductSimilarityPair> {
  val pairs = mutableListOf<ProductSimilarityPair>()
  val productsWithContent = products.filter { it.contentSpec != null }
  
  for (i in productsWithContent.indices) {
    for (j in i + 1 until productsWithContent.size) {
      val p1 = productsWithContent[i]
      val p2 = productsWithContent[j]
      
      val sets1 = p1.contentSpec!!.moduleSets.map { it.moduleSet.name }.toSet()
      val sets2 = p2.contentSpec!!.moduleSets.map { it.moduleSet.name }.toSet()
      
      val shared = sets1.intersect(sets2)
      val union = sets1.union(sets2)
      val similarity = if (union.isNotEmpty()) shared.size.toDouble() / union.size else 0.0
      
      if (similarity >= similarityThreshold) {
        pairs.add(ProductSimilarityPair(
          product1 = p1.name,
          product2 = p2.name,
          similarity = similarity,
          moduleSetSimilarity = similarity,
          sharedModuleSets = shared.toList().sorted(),
          uniqueToProduct1 = sets1.minus(sets2).toList().sorted(),
          uniqueToProduct2 = sets2.minus(sets1).toList().sorted()
        ))
      }
    }
  }
  
  return pairs.sortedByDescending { it.similarity }
}
