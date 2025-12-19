// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.tooling

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
 * Location of a module or module set in the repository.
 * Using enum instead of String for type safety.
 */
 enum class ModuleLocation {
  /** Module/set is in community/ directory */
  COMMUNITY,
  /** Module/set is in ultimate/ directory */
  ULTIMATE,
  /** Module/set location could not be determined */
  UNKNOWN;

  companion object {
    fun fromPath(path: String): ModuleLocation = when {
      path.contains("/community/") -> COMMUNITY
      path.contains("/ultimate/") -> ULTIMATE
      else -> UNKNOWN
    }
  }
}

/**
 * Represents the result of a parsing operation that may fail.
 * Used for operations like parsing modules.xml where partial results may be available.
 */
internal sealed class ParseResult<out T> {
  data class Success<T>(@JvmField val value: T) : ParseResult<T>()
  data class Failure<T>(@JvmField val error: String, @JvmField val partial: T? = null) : ParseResult<T>()
}

/**
 * Metadata about a module set including its location and source file.
 *
 * @param moduleSet The module set instance
 * @param location The location category (COMMUNITY or ULTIMATE)
 * @param sourceFile The Kotlin source file path relative to project root where this module set is defined
 * @param directNestedSets Names of directly nested module sets (immediate children only, not transitive)
 */
data class ModuleSetMetadata(
  @JvmField val moduleSet: ModuleSet,
  @JvmField val location: ModuleLocation,
  @JvmField val sourceFile: String,
  @JvmField val directNestedSets: List<String> = emptyList()
)

/**
 * JSON filter for selective analysis output.
 * Supports various filter types: products, moduleSets, composition, duplicates, mergeImpact, modulePaths, 
 * moduleDependencies, moduleReachability, dependencyPath, productUsage, or specific items.
 */
@Serializable
data class JsonFilter(
  @JvmField val filter: String,  // "products", "moduleSets", "composition", "duplicates", "mergeImpact", "modulePaths", "product", "moduleSet", "moduleDependencies", "moduleReachability", "dependencyPath", "productUsage"
  @JvmField val value: String? = null,  // Product/module set name when filter is "product" or "moduleSet"
  @JvmField val module: String? = null,  // Module name for "modulePaths", "moduleDependencies" filters
  @JvmField val moduleSet: String? = null,  // Module set name for "moduleReachability" or "productUsage" filter
  @JvmField val fromModule: String? = null,  // Starting module for "dependencyPath" filter
  @JvmField val toModule: String? = null,  // Target module for "dependencyPath" filter
  @JvmField val source: String? = null,  // Source module set name for "mergeImpact" filter
  @JvmField val target: String? = null,  // Target module set name for "mergeImpact" filter (null for inline operation)
  @JvmField val operation: String? = null,  // Operation type for "mergeImpact": "merge", "move", or "inline" (default: "merge")
  @JvmField val includeDuplicates: Boolean = false,  // Include duplicate xi:include detection in output (for future unification)
  @JvmField val includeTransitive: Boolean = false  // Include ALL transitive dependencies in moduleDependencies filter (BFS traversal)
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
  @JvmField val name: String,
  @JvmField val className: String?,
  @JvmField val sourceFile: String,
  @JvmField val pluginXmlPath: String?,
  @JvmField val contentSpec: ProductModulesContentSpec?,
  @JvmField val buildModules: List<String>,
  @JvmField val category: ProductCategory = ProductCategory.BACKEND,  // Product architecture category
  @JvmField val totalModuleCount: Int = 0,      // All modules including from module sets (deduplicated)
  @JvmField val directModuleCount: Int = 0,     // Just additionalModules count
  @JvmField val moduleSetCount: Int = 0         // Number of module sets included
)

/**
 * Module location information from .idea/modules.xml.
 * 
 * @param location Module location enum
 * @param imlPath Absolute path to the .iml file
 */
internal data class ModuleLocationInfo(
  @JvmField val location: ModuleLocation,
  @JvmField val imlPath: String?
)

/**
 * Violation when a community product uses ultimate modules.
 */
@Serializable
internal data class CommunityProductViolation(
  @JvmField val product: String,
  @JvmField val productFile: String,
  @JvmField val moduleSet: String,
  @JvmField val moduleSetFile: String,
  @JvmField val ultimateModules: List<String>,
  @JvmField val communityModulesCount: Int,
  @JvmField val unknownModulesCount: Int,
  @JvmField val totalModulesCount: Int
)

/**
 * Violation when a module set is in the wrong location (community vs. ultimate).
 */
@Serializable
internal data class ModuleSetLocationViolation(
  @JvmField val moduleSet: String,
  @JvmField val file: String,
  @JvmField val issue: String,  // "community_contains_ultimate" or "ultimate_contains_only_community"
  @JvmField val ultimateModules: List<String>? = null,
  @JvmField val communityModules: List<String>? = null,
  @JvmField val communityModulesCount: Int? = null,
  @JvmField val ultimateModulesCount: Int? = null,
  @JvmField val unknownModulesCount: Int,
  @JvmField val suggestion: String
)

/**
 * Similarity between two products based on module set overlap.
 * Used for identifying merge candidates and refactoring opportunities.
 */
@Serializable
internal data class ProductSimilarityPair(
  @JvmField val product1: String,
  @JvmField val product2: String,
  @JvmField val similarity: Double,
  @JvmField val moduleSetSimilarity: Double,
  @JvmField val sharedModuleSets: List<String>,
  @JvmField val uniqueToProduct1: List<String>,
  @JvmField val uniqueToProduct2: List<String>
)

/**
 * Overlap between two module sets.
 * Correctly identifies intentional nested set inclusions vs. actual duplications.
 * Intentional nesting (e.g., libraries include `libraries.core`) is filtered out.
 */
@Serializable
internal data class ModuleSetOverlap(
  @JvmField val moduleSet1: String,
  @JvmField val moduleSet2: String,
  @JvmField val location1: ModuleLocation,
  @JvmField val location2: ModuleLocation,
  @JvmField val relationship: String,  // "overlap", "subset", "superset"
  @JvmField val overlapPercent: Int,
  @JvmField val sharedModules: Int,
  @JvmField val totalModules1: Int,
  @JvmField val totalModules2: Int,
  @JvmField val recommendation: String
)

/**
 * Impact metrics for unification suggestions.
 * Contains various metrics depending on the strategy type.
 */
@Serializable
internal data class UnificationImpact(
  @JvmField val moduleSetsSaved: Int? = null,
  @JvmField val overlapPercent: Int? = null,
  @JvmField val moduleCount: Int? = null,
  @JvmField val affectedProducts: List<String>? = null,
  @JvmField val similarity: Double? = null,
  @JvmField val sharedModuleSets: Int? = null
)

/**
 * Suggestion for module set unification (merge, inline, factor, split).
 * Generated by analyzing overlap, product similarity, and module set usage patterns.
 */
@Serializable
internal data class UnificationSuggestion(
  @JvmField val priority: String,  // "high", "medium", "low"
  @JvmField val strategy: String,  // "merge", "inline", "factor", "split"
  @JvmField val type: String?,  // For merge: "subset", "superset", "high-overlap"
  @JvmField val moduleSet: String?,  // For inline/split: single module set
  @JvmField val moduleSet1: String?,  // For merge: first module set
  @JvmField val moduleSet2: String?,  // For merge: second module set
  @JvmField val products: List<String>?,  // For factor: products with shared sets
  @JvmField val sharedModuleSets: List<String>?,  // For factor: shared module sets
  @JvmField val reason: String,
  @JvmField val impact: UnificationImpact
)

/**
 * File reference in a module path trace.
 * Points to either a module set file or a product file that includes a module.
 */
@Serializable
internal data class PathFileReference(
  @JvmField val type: String,  // "module-set" or "product"
  @JvmField val path: String?,  // Absolute file path
  @JvmField val name: String,  // Name of the module set or product
  @JvmField val note: String  // Description like "contains module" or "includes module set"
)

/**
 * A single path showing how a module reaches a product.
 * Either directly or through module set(s).
 */
@Serializable
internal data class ModulePath(
  @JvmField val type: String,  // "direct" or "module-set"
  @JvmField val path: String,  // Human-readable path string like "module → set → product"
  @JvmField val files: List<PathFileReference>  // File references involved in this path
)

/**
 * Complete tracing result for a module showing all paths to products.
 */
@Serializable
internal data class ModulePathsResult(
  @JvmField val module: String,
  @JvmField val paths: List<ModulePath>,
  @JvmField val moduleSets: List<String>,  // Module sets that contain this module
  @JvmField val products: List<String>  // Products that include this module (directly or indirectly)
)

/**
 * Validation violation during merge impact analysis.
 * Indicates issues that would be introduced by the operation.
 */
@Serializable
internal data class MergeViolation(
  @JvmField val type: String,  // "location", "community-uses-ultimate", "notFound", "validation"
  @JvmField val severity: String,  // "error", "warning"
  @JvmField val message: String,
  @JvmField val affectedProducts: List<String>? = null,
  @JvmField val fix: String? = null
)

/**
 * Size impact metrics for merge operations.
 * Shows how module counts change as a result of the operation.
 */
@Serializable
internal data class SizeImpact(
  @JvmField val sourceModuleCount: Int,
  @JvmField val targetModuleCount: Int,
  @JvmField val newModulesToTarget: Int,
  @JvmField val duplicateModules: Int,
  @JvmField val resultingModuleCount: Int
)

/**
 * Impact analysis result for merging, moving, or inlining module sets.
 * Used to assess safety and predict consequences of refactoring.
 */
@Serializable
internal data class MergeImpactResult(
  @JvmField val operation: String,  // "merge", "move", "inline"
  @JvmField val sourceSet: String,
  @JvmField val targetSet: String?,
  @JvmField val productsUsingSource: List<String>,
  @JvmField val productsUsingTarget: List<String>,
  @JvmField val productsThatWouldChange: List<String>,
  @JvmField val sizeImpact: SizeImpact,
  @JvmField val violations: List<MergeViolation>,
  @JvmField val recommendation: String,
  @JvmField val safe: Boolean
)

/**
 * Product usage information showing how a product uses a module set.
 */
@Serializable
internal data class ProductUsageEntry(
  @JvmField val product: String,
  @JvmField val usageType: String,  // "direct" or "indirect"
  @JvmField val inclusionChain: List<String>?  // For indirect: chain of module sets from product to target
)

/**
 * Analysis of which products use a specific module set.
 * Distinguishes direct usage (top-level reference) from indirect usage (nested within other module sets).
 */
@Serializable
internal data class ProductUsageAnalysis(
  @JvmField val moduleSet: String,
  @JvmField val directUsage: List<ProductUsageEntry>,  // Products that directly reference this module set
  @JvmField val indirectUsage: List<ProductUsageEntry>,  // Products that use it transitively through other sets
  @JvmField val totalProducts: Int
)

