// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.analysis

import kotlinx.serialization.Serializable
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec

/**
 * Operation type for module set merge/move/inline analysis.
 * Using enum instead of String for type safety.
 */
internal enum class MergeOperation {
  /** Combine source modules into target module set */
  MERGE,
  /** Move source module set to a different location */
  MOVE,
  /** Remove module set and add modules directly to products */
  INLINE;
  
  companion object {
    fun fromString(value: String): MergeOperation = when (value.lowercase()) {
      "merge" -> MERGE
      "move" -> MOVE
      "inline" -> INLINE
      else -> error("Unknown merge operation: '$value'. Valid values: merge, move, inline")
    }
  }
}

/**
 * Represents the result of a parsing operation that may fail.
 * Used for operations like parsing modules.xml where partial results may be available.
 */
internal sealed class ParseResult<out T> {
  data class Success<T>(val value: T) : ParseResult<T>()
  data class Failure<T>(val error: String, val partial: T? = null) : ParseResult<T>()
}

/**
 * Metadata about a module set including its location and source file.
 *
 * @param moduleSet The module set instance
 * @param location The location category ("community" or "ultimate")
 * @param sourceFile The Kotlin source file path relative to project root where this module set is defined
 * @param directNestedSets Names of directly nested module sets (immediate children only, not transitive)
 */
data class ModuleSetMetadata(
  val moduleSet: ModuleSet,
  val location: String,
  val sourceFile: String,
  val directNestedSets: List<String> = emptyList()
)

/**
 * JSON filter for selective analysis output.
 * Supports various filter types: products, moduleSets, composition, duplicates, mergeImpact, modulePaths, 
 * moduleDependencies, moduleReachability, dependencyPath, productUsage, or specific items.
 */
@Serializable
data class JsonFilter(
  val filter: String,  // "products", "moduleSets", "composition", "duplicates", "mergeImpact", "modulePaths", "product", "moduleSet", "moduleDependencies", "moduleReachability", "dependencyPath", "productUsage"
  val value: String? = null,  // Product/module set name when filter is "product" or "moduleSet"
  val module: String? = null,  // Module name for "modulePaths", "moduleDependencies" filters
  val moduleSet: String? = null,  // Module set name for "moduleReachability" or "productUsage" filter
  val fromModule: String? = null,  // Starting module for "dependencyPath" filter
  val toModule: String? = null,  // Target module for "dependencyPath" filter
  val source: String? = null,  // Source module set name for "mergeImpact" filter
  val target: String? = null,  // Target module set name for "mergeImpact" filter (null for inline operation)
  val operation: String? = null,  // Operation type for "mergeImpact": "merge", "move", or "inline" (default: "merge")
  val includeDuplicates: Boolean = false,  // Include duplicate xi:include detection in output (for future unification)
  val includeTransitive: Boolean = false  // Include ALL transitive dependencies in moduleDependencies filter (BFS traversal)
)

/**
 * Product category based on architecture and module sets.
 */
@Serializable
enum class ProductCategory {
  /** Ultimate IDE products (uses ide.ultimate module set) */
  ULTIMATE,
  /** Community IDE products (uses ide.common module set) */
  COMMUNITY,
  /** Backend/specialized products (neither ultimate nor community) */
  BACKEND
}

/**
 * Product specification for JSON output.
 * Contains essential product metadata with source file paths for AI navigation,
 * plus complete ProductModulesContentSpec for full DSL representation.
 */
@Serializable
data class ProductSpec(
  val name: String,
  val className: String?,
  val sourceFile: String,
  val pluginXmlPath: String?,
  val contentSpec: ProductModulesContentSpec?,
  val buildModules: List<String>,
  val category: ProductCategory = ProductCategory.BACKEND,  // Product architecture category
  val totalModuleCount: Int = 0,      // All modules including from module sets
  val directModuleCount: Int = 0,     // Just additionalModules count
  val moduleSetCount: Int = 0,        // Number of module sets included
  val uniqueModuleCount: Int = 0      // Deduplicated module count
)

/**
 * Module location information from .idea/modules.xml.
 * 
 * @param location Module location: "community", "ultimate", or "unknown"
 * @param imlPath Absolute path to the .iml file
 */
internal data class ModuleLocationInfo(
  val location: String,  // "community", "ultimate", or "unknown"
  val imlPath: String?
)

/**
 * Violation when a community product uses ultimate modules.
 */
@Serializable
internal data class CommunityProductViolation(
  val product: String,
  val productFile: String,
  val moduleSet: String,
  val moduleSetFile: String,
  val ultimateModules: List<String>,
  val communityModulesCount: Int,
  val unknownModulesCount: Int,
  val totalModulesCount: Int
)

/**
 * Violation when a module set is in the wrong location (community vs. ultimate).
 */
@Serializable
internal data class ModuleSetLocationViolation(
  val moduleSet: String,
  val file: String,
  val issue: String,  // "community_contains_ultimate" or "ultimate_contains_only_community"
  val ultimateModules: List<String>? = null,
  val communityModules: List<String>? = null,
  val communityModulesCount: Int? = null,
  val ultimateModulesCount: Int? = null,
  val unknownModulesCount: Int,
  val suggestion: String
)

/**
 * Similarity between two products based on module set overlap.
 * Used for identifying merge candidates and refactoring opportunities.
 */
@Serializable
internal data class ProductSimilarityPair(
  val product1: String,
  val product2: String,
  val similarity: Double,
  val moduleSetSimilarity: Double,
  val sharedModuleSets: List<String>,
  val uniqueToProduct1: List<String>,
  val uniqueToProduct2: List<String>
)

/**
 * Overlap between two module sets.
 * Correctly identifies intentional nested set inclusions vs. actual duplications.
 * Intentional nesting (e.g., libraries include `libraries.core`) is filtered out.
 */
@Serializable
internal data class ModuleSetOverlap(
  val moduleSet1: String,
  val moduleSet2: String,
  val location1: String,
  val location2: String,
  val relationship: String,  // "overlap", "subset", "superset"
  val overlapPercent: Int,
  val sharedModules: Int,
  val totalModules1: Int,
  val totalModules2: Int,
  val recommendation: String
)

/**
 * Impact metrics for unification suggestions.
 * Contains various metrics depending on the strategy type.
 */
@Serializable
internal data class UnificationImpact(
  val moduleSetsSaved: Int? = null,
  val overlapPercent: Int? = null,
  val moduleCount: Int? = null,
  val affectedProducts: List<String>? = null,
  val similarity: Double? = null,
  val sharedModuleSets: Int? = null
)

/**
 * Suggestion for module set unification (merge, inline, factor, split).
 * Generated by analyzing overlap, product similarity, and module set usage patterns.
 */
@Serializable
internal data class UnificationSuggestion(
  val priority: String,  // "high", "medium", "low"
  val strategy: String,  // "merge", "inline", "factor", "split"
  val type: String?,  // For merge: "subset", "superset", "high-overlap"
  val moduleSet: String?,  // For inline/split: single module set
  val moduleSet1: String?,  // For merge: first module set
  val moduleSet2: String?,  // For merge: second module set
  val products: List<String>?,  // For factor: products with shared sets
  val sharedModuleSets: List<String>?,  // For factor: shared module sets
  val reason: String,
  val impact: UnificationImpact
)

/**
 * File reference in a module path trace.
 * Points to either a module set file or a product file that includes a module.
 */
@Serializable
internal data class PathFileReference(
  val type: String,  // "module-set" or "product"
  val path: String?,  // Absolute file path
  val name: String,  // Name of the module set or product
  val note: String  // Description like "contains module" or "includes module set"
)

/**
 * A single path showing how a module reaches a product.
 * Either directly or through module set(s).
 */
@Serializable
internal data class ModulePath(
  val type: String,  // "direct" or "module-set"
  val path: String,  // Human-readable path string like "module → set → product"
  val files: List<PathFileReference>  // File references involved in this path
)

/**
 * Complete tracing result for a module showing all paths to products.
 */
@Serializable
internal data class ModulePathsResult(
  val module: String,
  val paths: List<ModulePath>,
  val moduleSets: List<String>,  // Module sets that contain this module
  val products: List<String>  // Products that include this module (directly or indirectly)
)

/**
 * Validation violation during merge impact analysis.
 * Indicates issues that would be introduced by the operation.
 */
@Serializable
internal data class MergeViolation(
  val type: String,  // "location", "community-uses-ultimate", "notFound", "validation"
  val severity: String,  // "error", "warning"
  val message: String,
  val affectedProducts: List<String>? = null,
  val fix: String? = null
)

/**
 * Size impact metrics for merge operations.
 * Shows how module counts change as a result of the operation.
 */
@Serializable
internal data class SizeImpact(
  val sourceModuleCount: Int,
  val targetModuleCount: Int,
  val newModulesToTarget: Int,
  val duplicateModules: Int,
  val resultingModuleCount: Int
)

/**
 * Impact analysis result for merging, moving, or inlining module sets.
 * Used to assess safety and predict consequences of refactoring.
 */
@Serializable
internal data class MergeImpactResult(
  val operation: String,  // "merge", "move", "inline"
  val sourceSet: String,
  val targetSet: String?,
  val productsUsingSource: List<String>,
  val productsUsingTarget: List<String>,
  val productsThatWouldChange: List<String>,
  val sizeImpact: SizeImpact,
  val violations: List<MergeViolation>,
  val recommendation: String,
  val safe: Boolean
)

/**
 * Product usage information showing how a product uses a module set.
 */
@Serializable
internal data class ProductUsageEntry(
  val product: String,
  val usageType: String,  // "direct" or "indirect"
  val inclusionChain: List<String>?  // For indirect: chain of module sets from product to target
)

/**
 * Analysis of which products use a specific module set.
 * Distinguishes direct usage (top-level reference) from indirect usage (nested within other module sets).
 */
@Serializable
internal data class ProductUsageAnalysis(
  val moduleSet: String,
  val directUsage: List<ProductUsageEntry>,  // Products that directly reference this module set
  val indirectUsage: List<ProductUsageEntry>,  // Products that use it transitively through other sets
  val totalProducts: Int
)

