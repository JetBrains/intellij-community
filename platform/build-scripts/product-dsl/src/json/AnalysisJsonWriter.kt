// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.json

import kotlinx.serialization.Serializable
import org.jetbrains.intellij.build.productLayout.tooling.MergeImpactResult
import org.jetbrains.intellij.build.productLayout.tooling.ModulePathsResult
import org.jetbrains.intellij.build.productLayout.tooling.ModuleSetOverlap
import org.jetbrains.intellij.build.productLayout.tooling.ProductSimilarityPair
import org.jetbrains.intellij.build.productLayout.tooling.ProductUsageAnalysis
import org.jetbrains.intellij.build.productLayout.tooling.UnificationSuggestion
import org.jetbrains.intellij.build.productLayout.traversal.DependencyPathResult
import org.jetbrains.intellij.build.productLayout.traversal.ModuleDependenciesResult
import org.jetbrains.intellij.build.productLayout.traversal.ModuleOwnersResult
import org.jetbrains.intellij.build.productLayout.traversal.ModuleReachabilityResult
import tools.jackson.core.JsonGenerator

/**
 * Writes product similarity analysis to JSON.
 * Includes similar product pairs and summary statistics.
 */
internal fun writeProductSimilarityAnalysis(
  gen: JsonGenerator,
  pairs: List<ProductSimilarityPair>,
  threshold: Double
) {
  @Serializable
  data class Wrapper(
    val pairs: List<ProductSimilarityPair>,
    val totalPairs: Int,
    val threshold: Double,
    val summary: String
  )
  
  val wrapper = Wrapper(
    pairs = pairs,
    totalPairs = pairs.size,
    threshold = threshold,
    summary = "Found ${pairs.size} product pairs with ≥${(threshold * 100).toInt()}% similarity"
  )
  gen.writeRawValue(kotlinxJson.encodeToString(wrapper))
}

/**
 * Writes module set overlap analysis to JSON.
 * Includes overlapping module set pairs and summary statistics.
 * Note: Intentional nested set inclusions are already filtered out during analysis.
 */
internal fun writeModuleSetOverlapAnalysis(
  gen: JsonGenerator,
  overlaps: List<ModuleSetOverlap>,
  minPercent: Int
) {
  @Serializable
  data class Wrapper(
    val overlaps: List<ModuleSetOverlap>,
    val count: Int,
    val summary: String
  )
  
  val wrapper = Wrapper(
    overlaps = overlaps,
    count = overlaps.size,
    summary = "Found ${overlaps.size} module set pairs with ≥$minPercent% overlap (excluding intentional nesting)"
  )
  gen.writeRawValue(kotlinxJson.encodeToString(wrapper))
}

/**
 * Writes module set unification suggestions to JSON.
 * Includes suggestions for merge, inline, factor, and split strategies.
 */
internal fun writeUnificationSuggestions(
  gen: JsonGenerator,
  suggestions: List<UnificationSuggestion>
) {
  @Serializable
  data class Wrapper(
    val suggestions: List<UnificationSuggestion>,
    val totalSuggestions: Int,
    val summary: String
  )
  
  val wrapper = Wrapper(
    suggestions = suggestions,
    totalSuggestions = suggestions.size,
    summary = "Found ${suggestions.size} unification opportunities"
  )
  gen.writeRawValue(kotlinxJson.encodeToString(wrapper))
}

/**
 * Writes merge impact analysis to JSON.
 * Includes products affected, size impact, violations, and recommendation.
 */
internal fun writeMergeImpactAnalysis(
  gen: JsonGenerator,
  impact: MergeImpactResult
) {
  gen.writeRawValue(kotlinxJson.encodeToString(impact))
}

/**
 * Writes module paths result to JSON.
 * Includes all paths from module to products and summary information.
 */
internal fun writeModulePathsResult(
  gen: JsonGenerator,
  result: ModulePathsResult
) {
  gen.writeRawValue(kotlinxJson.encodeToString(result))
}

/**
 * Writes module dependencies result to JSON.
 * Includes JPS module dependencies for a given module.
 */
internal fun writeModuleDependenciesResult(
  gen: JsonGenerator,
  result: ModuleDependenciesResult
) {
  gen.writeRawValue(kotlinxJson.encodeToString(result))
}

/**
 * Writes module owners result to JSON.
 * Includes owning plugins (production/test depending on filter).
 */
internal fun writeModuleOwnersResult(
  gen: JsonGenerator,
  result: ModuleOwnersResult
) {
  gen.writeRawValue(kotlinxJson.encodeToString(result))
}

/**
 * Writes module reachability result to JSON.
 * Includes satisfied and missing dependencies within a module set context.
 */
internal fun writeModuleReachabilityResult(
  gen: JsonGenerator,
  result: ModuleReachabilityResult
) {
  gen.writeRawValue(kotlinxJson.encodeToString(result))
}

/**
 * Writes dependency path result to JSON.
 * Includes the transitive dependency path from one module to another.
 */
internal fun writeDependencyPathResult(
  gen: JsonGenerator,
  result: DependencyPathResult
) {
  gen.writeRawValue(kotlinxJson.encodeToString(result))
}

/**
 * Writes product usage analysis to JSON.
 * Includes direct and indirect usage with inclusion chains.
 */
internal fun writeProductUsageAnalysis(
  gen: JsonGenerator,
  result: ProductUsageAnalysis
) {
  gen.writeRawValue(kotlinxJson.encodeToString(result))
}
