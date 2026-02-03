// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.tooling

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.productLayout.traversal.ModuleSetTraversal
import org.jetbrains.intellij.build.productLayout.traversal.ModuleSetTraversalCache

/**
 * Index for O(1) lookup of products by module set name.
 * Built once and reused across all analysis functions.
 */
internal class ProductModuleSetIndex(products: List<ProductSpec>) {
  private val productsByModuleSet: Map<String, List<ProductSpec>>

  init {
    val index = HashMap<String, MutableList<ProductSpec>>()
    for (product in products) {
      for (msRef in (product.contentSpec?.moduleSets ?: emptyList())) {
        index.computeIfAbsent(msRef.moduleSet.name) { ArrayList() }.add(product)
      }
    }
    productsByModuleSet = index
  }

  /** O(1) lookup of products using a module set */
  fun getProductsUsing(moduleSetName: String): List<ProductSpec> =
    productsByModuleSet.get(moduleSetName) ?: emptyList()
}

/**
 * Suggests module set unification opportunities based on overlap, similarity, and usage patterns.
 * 
 * Strategies:
 * - merge: Combine overlapping module sets (especially subsets/supersets)
 * - inline: Inline rarely used small module sets directly into products
 * - factor: Extract common patterns from similar products
 * - split: Split oversized module sets for better maintainability
 * 
 * @param allModuleSets All module sets with metadata
 * @param products All products
 * @param overlaps Pre-calculated module set overlaps
 * @param similarityPairs Pre-calculated product similarity pairs
 * @param maxSuggestions Maximum number of suggestions to return
 * @param strategy Filter by strategy: "merge", "inline", "factor", "split", or "all"
 * @return List of suggestions sorted by priority
 */
internal suspend fun suggestModuleSetUnification(
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  overlaps: List<ModuleSetOverlap>,
  similarityPairs: List<ProductSimilarityPair>,
  maxSuggestions: Int = 10,
  strategy: String = "all"
): List<UnificationSuggestion> = coroutineScope {
  // Build index for O(1) product lookups
  val productIndex = ProductModuleSetIndex(products)

  // Build cache for O(1) module name lookups
  val cache = ModuleSetTraversalCache(allModuleSets.map { it.moduleSet })

  // Run all 4 strategies in parallel
  val mergeJob = async {
    if (strategy == "merge" || strategy == "all") computeMergeSuggestions(overlaps) else emptyList()
  }

  val inlineJob = async {
    if (strategy == "inline" || strategy == "all") computeInlineSuggestions(allModuleSets, productIndex, cache) else emptyList()
  }

  val factorJob = async {
    if (strategy == "factor" || strategy == "all") computeFactorSuggestions(similarityPairs) else emptyList()
  }

  val splitJob = async {
    if (strategy == "split" || strategy == "all") computeSplitSuggestions(allModuleSets, cache) else emptyList()
  }

  // Await all and flatten
  val suggestions = mergeJob.await() + inlineJob.await() + factorJob.await() + splitJob.await()

  // Remove duplicates and sort by priority
  val uniqueSuggestions = ArrayList<UnificationSuggestion>()
  val seen = HashSet<String>()
  for (suggestion in suggestions) {
    val key = listOf(suggestion.strategy, suggestion.moduleSet1, suggestion.moduleSet2, suggestion.moduleSet).toString()
    if (!seen.contains(key)) {
      seen.add(key)
      uniqueSuggestions.add(suggestion)
    }
  }

  // Sort by priority: high > medium > low
  val priorityOrder = mapOf("high" to 3, "medium" to 2, "low" to 1)
  uniqueSuggestions.sortByDescending { priorityOrder.get(it.priority) ?: 0 }

  uniqueSuggestions.take(maxSuggestions)
}

/** Strategy 1: Merge overlapping module sets */
private fun computeMergeSuggestions(overlaps: List<ModuleSetOverlap>): List<UnificationSuggestion> {
  return overlaps.mapNotNull { overlap ->
    when {
      overlap.relationship == "subset" || overlap.relationship == "superset" -> UnificationSuggestion(
        priority = "high",
        strategy = "merge",
        type = overlap.relationship,
        moduleSet = null,
        moduleSet1 = overlap.moduleSet1,
        moduleSet2 = overlap.moduleSet2,
        products = null,
        sharedModuleSets = null,
        reason = overlap.recommendation,
        impact = UnificationImpact(moduleSetsSaved = 1, overlapPercent = overlap.overlapPercent)
      )
      overlap.overlapPercent >= 80 -> UnificationSuggestion(
        priority = "medium",
        strategy = "merge",
        type = "high-overlap",
        moduleSet = null,
        moduleSet1 = overlap.moduleSet1,
        moduleSet2 = overlap.moduleSet2,
        products = null,
        sharedModuleSets = null,
        reason = overlap.recommendation,
        impact = UnificationImpact(overlapPercent = overlap.overlapPercent)
      )
      else -> null
    }
  }
}

/** Strategy 2: Find rarely used module sets (inline candidates) */
private fun computeInlineSuggestions(
  allModuleSets: List<ModuleSetMetadata>,
  productIndex: ProductModuleSetIndex,
  cache: ModuleSetTraversalCache
): List<UnificationSuggestion> {
  return allModuleSets.mapNotNull { msEntry ->
    val usedByProducts = productIndex.getProductsUsing(msEntry.moduleSet.name)
    val totalModuleCount = cache.getModuleNames(msEntry.moduleSet).size

    if (usedByProducts.size <= 1 && totalModuleCount <= 5) {
      UnificationSuggestion(
        priority = "low",
        strategy = "inline",
        type = null,
        moduleSet = msEntry.moduleSet.name,
        moduleSet1 = null,
        moduleSet2 = null,
        products = null,
        sharedModuleSets = null,
        reason = "Used by only ${usedByProducts.size} product(s) and contains only $totalModuleCount modules. Consider inlining into the product directly.",
        impact = UnificationImpact(
          moduleSetsSaved = 1,
          moduleCount = totalModuleCount,
          affectedProducts = usedByProducts.map { it.name }
        )
      )
    } else null
  }
}

/** Strategy 3: Find common patterns (factoring opportunities) */
private fun computeFactorSuggestions(similarityPairs: List<ProductSimilarityPair>): List<UnificationSuggestion> {
  return similarityPairs.mapNotNull { pair ->
    if (pair.sharedModuleSets.size >= 3) {
      UnificationSuggestion(
        priority = "medium",
        strategy = "factor",
        type = null,
        moduleSet = null,
        moduleSet1 = null,
        moduleSet2 = null,
        products = listOf(pair.product1, pair.product2),
        sharedModuleSets = pair.sharedModuleSets,
        reason = "Products ${pair.product1} and ${pair.product2} share ${pair.sharedModuleSets.size} module sets (${(pair.similarity * 100).toInt()}% similarity). Consider creating a common base.",
        impact = UnificationImpact(similarity = pair.similarity, sharedModuleSets = pair.sharedModuleSets.size)
      )
    } else null
  }
}

/** Strategy 4: Split large module sets */
private fun computeSplitSuggestions(
  allModuleSets: List<ModuleSetMetadata>,
  cache: ModuleSetTraversalCache
): List<UnificationSuggestion> {
  return allModuleSets.mapNotNull { msEntry ->
    val totalModuleCount = cache.getModuleNames(msEntry.moduleSet).size
    if (totalModuleCount > 200) {
      UnificationSuggestion(
        priority = "low",
        strategy = "split",
        type = null,
        moduleSet = msEntry.moduleSet.name,
        moduleSet1 = null,
        moduleSet2 = null,
        products = null,
        sharedModuleSets = null,
        reason = "Module set contains $totalModuleCount modules. Consider splitting into smaller, more focused sets for better maintainability.",
        impact = UnificationImpact(moduleCount = totalModuleCount)
      )
    } else null
  }
}

/**
 * Helper function to find all products that use a specific module set.
 * 
 * @param products List of all products
 * @param moduleSetName Name of the module set to search for
 * @return List of products that reference the module set
 */
fun findProductsUsingModuleSet(
  products: List<ProductSpec>,
  moduleSetName: String
): List<ProductSpec> {
  return products.filter { p ->
    p.contentSpec?.moduleSets?.any { it.moduleSet.name == moduleSetName } == true
  }
}

/**
 * Analyzes which products use a specific module set, distinguishing direct from indirect usage.
 * Direct usage = product directly references the module set in its top-level configuration
 * Indirect usage = product includes another module set that nests the target module set
 * 
 * @param moduleSetName Name of the module set to analyze
 * @param products All products
 * @param allModuleSets All module sets with metadata (includes directNestedSets)
 * @return Analysis result with direct/indirect usage and inclusion chains
 */
internal fun analyzeProductUsage(
  moduleSetName: String,
  products: List<ProductSpec>,
  allModuleSets: List<ModuleSetMetadata>,
  cache: ModuleSetTraversalCache,
): ProductUsageAnalysis {
  val moduleSetsList = allModuleSets.map { it.moduleSet }
  val directUsage = ArrayList<ProductUsageEntry>()
  val indirectUsage = ArrayList<ProductUsageEntry>()
  
  for (product in products) {
    val topLevelSets = product.contentSpec?.moduleSets?.map { it.moduleSet.name } ?: emptyList()
    
    // Check if product directly references the target module set
    if (topLevelSets.contains(moduleSetName)) {
      directUsage.add(ProductUsageEntry(
        product = product.name,
        usageType = "direct",
        inclusionChain = null
      ))
    }
    else {
      // Check if any top-level set transitively includes the target
      for (topLevelSet in topLevelSets) {
        if (cache.isTransitivelyNested(topLevelSet, moduleSetName)) {
          // Build the inclusion chain
          val chain = ModuleSetTraversal.buildInclusionChain(topLevelSet, moduleSetName, moduleSetsList)
          indirectUsage.add(ProductUsageEntry(
            product = product.name,
            usageType = "indirect",
            inclusionChain = chain
          ))
          break  // Only record once per product
        }
      }
    }
  }
  
  return ProductUsageAnalysis(
    moduleSet = moduleSetName,
    directUsage = directUsage,
    indirectUsage = indirectUsage,
    totalProducts = directUsage.size + indirectUsage.size
  )
}

/**
 * Analyzes the impact of merging, moving, or inlining module sets.
 * Checks for violations, calculates size impact, and provides recommendations.
 * 
 * @param sourceSet Source module set name
 * @param targetSet Target module set name (null for inline operation)
 * @param operation Operation type (MERGE, MOVE, or INLINE)
 * @param allModuleSets All module sets with metadata
 * @param products All products
 * @return Impact analysis result with violations, size metrics, and recommendation
 */
internal fun analyzeMergeImpact(
  sourceSet: String,
  targetSet: String?,
  operation: MergeOperation,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  cache: ModuleSetTraversalCache,
): MergeImpactResult {
  val operationStr = operation.name.lowercase()
  // O(1) lookup for metadata by name
  val metadataByName = allModuleSets.associateBy { it.moduleSet.name }
  // Find source module set
  val sourceEntry = metadataByName.get(sourceSet)
  if (sourceEntry == null) {
    return MergeImpactResult(
      operation = operationStr,
      sourceSet = sourceSet,
      targetSet = targetSet,
      productsUsingSource = emptyList(),
      productsUsingTarget = emptyList(),
      productsThatWouldChange = emptyList(),
      sizeImpact = SizeImpact(
        sourceModuleCount = 0,
        targetModuleCount = 0,
        newModulesToTarget = 0,
        duplicateModules = 0,
        resultingModuleCount = 0
      ),
      violations = listOf(MergeViolation(
        type = "notFound",
        severity = "error",
        message = "Source module set '$sourceSet' not found"
      )),
      recommendation = "ERROR: Cannot analyze - source module set not found",
      safe = false
    )
  }
  
  // Find target module set (if applicable)
  var targetEntry: ModuleSetMetadata? = null
  if (targetSet != null) {
    targetEntry = metadataByName.get(targetSet)
    if (targetEntry == null) {
      return MergeImpactResult(
        operation = operationStr,
        sourceSet = sourceSet,
        targetSet = targetSet,
        productsUsingSource = emptyList(),
        productsUsingTarget = emptyList(),
        productsThatWouldChange = emptyList(),
        sizeImpact = SizeImpact(
          sourceModuleCount = 0,
          targetModuleCount = 0,
          newModulesToTarget = 0,
          duplicateModules = 0,
          resultingModuleCount = 0
        ),
        violations = listOf(MergeViolation(
          type = "notFound",
          severity = "error",
          message = "Target module set '$targetSet' not found"
        )),
        recommendation = "ERROR: Cannot analyze - target module set not found",
        safe = false
      )
    }
  }
  
  // Validate that source and target are different
  if (targetSet != null && sourceSet == targetSet) {
    return MergeImpactResult(
      operation = operationStr,
      sourceSet = sourceSet,
      targetSet = targetSet,
      productsUsingSource = emptyList(),
      productsUsingTarget = emptyList(),
      productsThatWouldChange = emptyList(),
      sizeImpact = SizeImpact(
        sourceModuleCount = 0,
        targetModuleCount = 0,
        newModulesToTarget = 0,
        duplicateModules = 0,
        resultingModuleCount = 0
      ),
      violations = listOf(MergeViolation(
        type = "validation",
        severity = "error",
        message = "Source and target cannot be the same module set: '$sourceSet'"
      )),
      recommendation = "ERROR: Source and target must be different module sets",
      safe = false
    )
  }
  
  // Find products using source
  val productsUsingSource = findProductsUsingModuleSet(products, sourceSet)
  
  // Find products using target
  val productsUsingTarget = if (targetSet != null) {
    findProductsUsingModuleSet(products, targetSet)
  } else {
    emptyList()
  }
  
  // Calculate module changes
  val sourceModules = cache.getModuleNames(sourceEntry.moduleSet)
  val targetModules = if (targetEntry != null) {
    cache.getModuleNames(targetEntry.moduleSet)
  }
  else {
    emptySet()
  }
  
  val newModules = sourceModules.minus(targetModules)
  val duplicateModules = sourceModules.intersect(targetModules)
  
  // Check for community/ultimate violations
  val violations = ArrayList<MergeViolation>()
  if (operation == MergeOperation.MERGE && targetEntry != null) {
    val sourceLocation = sourceEntry.location
    val targetLocation = targetEntry.location
    
    if (sourceLocation == ModuleLocation.ULTIMATE && targetLocation == ModuleLocation.COMMUNITY) {
      violations.add(MergeViolation(
        type = "location",
        severity = "error",
        message = "Cannot merge ultimate module set \"$sourceSet\" into community module set \"$targetSet\"",
        fix = "Move \"$targetSet\" to ultimate directory, or extract community modules from \"$sourceSet\""
      ))
    }
    
    // Check if any community products would gain ultimate modules
    val communityProductsUsingTarget = productsUsingTarget.filter { p ->
      val productSets = p.contentSpec?.moduleSets?.map { it.moduleSet.name } ?: emptyList()
      !productSets.contains("commercialIdeBase") && !productSets.contains("ide.ultimate")
    }
    
    if (sourceLocation == ModuleLocation.ULTIMATE && communityProductsUsingTarget.isNotEmpty()) {
      violations.add(MergeViolation(
        type = "community-uses-ultimate",
        severity = "error",
        message = "Merging ultimate set \"$sourceSet\" into \"$targetSet\" would expose ultimate modules to ${communityProductsUsingTarget.size} community products",
        affectedProducts = communityProductsUsingTarget.map { it.name },
        fix = "Remove \"$targetSet\" from community products, or split ultimate modules from \"$sourceSet\""
      ))
    }
  }
  
  // Calculate size impact
  val sizeImpact = SizeImpact(
    sourceModuleCount = sourceModules.size,
    targetModuleCount = targetModules.size,
    newModulesToTarget = newModules.size,
    duplicateModules = duplicateModules.size,
    resultingModuleCount = targetModules.size + newModules.size
  )
  
  // Generate recommendation
  val recommendation = when {
    violations.isNotEmpty() -> "NOT RECOMMENDED: Operation would introduce violations. See violations for details."
    operation == MergeOperation.MERGE && duplicateModules.isNotEmpty() -> 
      "CAUTION: ${duplicateModules.size} modules already exist in target. Merge would create no duplicates, but review if modules serve the same purpose."
    operation == MergeOperation.MERGE && newModules.isNotEmpty() -> 
      "SAFE TO MERGE: Would add ${newModules.size} new modules to \"$targetSet\". ${productsUsingTarget.size} products using target would gain these modules."
    operation == MergeOperation.INLINE -> 
      "SAFE TO INLINE: ${productsUsingSource.size} products using \"$sourceSet\" would directly include ${sourceModules.size} modules instead."
    else -> "Operation appears safe based on current analysis."
  }
  
  return MergeImpactResult(
    operation = operationStr,
    sourceSet = sourceSet,
    targetSet = targetSet,
    productsUsingSource = productsUsingSource.map { it.name },
    productsUsingTarget = productsUsingTarget.map { it.name },
    productsThatWouldChange = if (operation == MergeOperation.MERGE) {
      productsUsingTarget.map { it.name }
    } else {
      productsUsingSource.map { it.name }
    },
    sizeImpact = sizeImpact,
    violations = violations,
    recommendation = recommendation,
    safe = violations.isEmpty()
  )
}
