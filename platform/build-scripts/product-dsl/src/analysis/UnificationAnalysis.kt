// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.analysis

import org.jetbrains.intellij.build.productLayout.collectAllModuleNames

/**
 * Suggests module set unification opportunities based on overlap, similarity, and usage patterns.
 * 
 * Strategies:
 * - merge: Combine overlapping module sets (especially subsets/supersets)
 * - inline: Inline rarely-used small module sets directly into products
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
fun suggestModuleSetUnification(
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  overlaps: List<ModuleSetOverlap>,
  similarityPairs: List<ProductSimilarityPair>,
  maxSuggestions: Int = 10,
  strategy: String = "all"
): List<UnificationSuggestion> {
  val suggestions = mutableListOf<UnificationSuggestion>()
  
  // Strategy 1: Merge overlapping module sets
  if (strategy == "merge" || strategy == "all") {
    for (overlap in overlaps) {
      if (overlap.relationship == "subset" || overlap.relationship == "superset") {
        suggestions.add(UnificationSuggestion(
          priority = "high",
          strategy = "merge",
          type = overlap.relationship,
          moduleSet = null,
          moduleSet1 = overlap.moduleSet1,
          moduleSet2 = overlap.moduleSet2,
          products = null,
          sharedModuleSets = null,
          reason = overlap.recommendation,
          impact = mapOf(
            "moduleSetsSaved" to 1,
            "overlapPercent" to overlap.overlapPercent
          )
        ))
      } else if (overlap.overlapPercent >= 80) {
        suggestions.add(UnificationSuggestion(
          priority = "medium",
          strategy = "merge",
          type = "high-overlap",
          moduleSet = null,
          moduleSet1 = overlap.moduleSet1,
          moduleSet2 = overlap.moduleSet2,
          products = null,
          sharedModuleSets = null,
          reason = overlap.recommendation,
          impact = mapOf("overlapPercent" to overlap.overlapPercent)
        ))
      }
    }
  }
  
  // Strategy 2: Find rarely-used module sets (inline candidates)
  if (strategy == "inline" || strategy == "all") {
    for (msEntry in allModuleSets) {
      val usedByProducts = products.filter { p ->
        p.contentSpec?.moduleSets?.any { it.moduleSet.name == msEntry.moduleSet.name } == true
      }
      
      // Use total module count (including nested sets) for inline candidate detection
      val totalModuleCount = collectAllModuleNames(msEntry.moduleSet).size
      if (usedByProducts.size <= 1 && totalModuleCount <= 5) {
        suggestions.add(UnificationSuggestion(
          priority = "low",
          strategy = "inline",
          type = null,
          moduleSet = msEntry.moduleSet.name,
          moduleSet1 = null,
          moduleSet2 = null,
          products = null,
          sharedModuleSets = null,
          reason = "Used by only ${usedByProducts.size} product(s) and contains only $totalModuleCount modules. Consider inlining into the product directly.",
          impact = mapOf(
            "moduleSetsSaved" to 1,
            "moduleCount" to totalModuleCount,
            "affectedProducts" to usedByProducts.map { it.name }
          )
        ))
      }
    }
  }
  
  // Strategy 3: Find common patterns (factoring opportunities)
  if (strategy == "factor" || strategy == "all") {
    for (pair in similarityPairs) {
      if (pair.sharedModuleSets.size >= 3) {
        suggestions.add(UnificationSuggestion(
          priority = "medium",
          strategy = "factor",
          type = null,
          moduleSet = null,
          moduleSet1 = null,
          moduleSet2 = null,
          products = listOf(pair.product1, pair.product2),
          sharedModuleSets = pair.sharedModuleSets,
          reason = "Products ${pair.product1} and ${pair.product2} share ${pair.sharedModuleSets.size} module sets (${(pair.similarity * 100).toInt()}% similarity). Consider creating a common base.",
          impact = mapOf(
            "similarity" to pair.similarity,
            "sharedModuleSets" to pair.sharedModuleSets.size
          )
        ))
      }
    }
  }
  
  // Strategy 4: Split large module sets
  if (strategy == "split" || strategy == "all") {
    for (msEntry in allModuleSets) {
      // Use total module count (including nested sets) for split suggestions
      val totalModuleCount = collectAllModuleNames(msEntry.moduleSet).size
      if (totalModuleCount > 200) {
        suggestions.add(UnificationSuggestion(
          priority = "low",
          strategy = "split",
          type = null,
          moduleSet = msEntry.moduleSet.name,
          moduleSet1 = null,
          moduleSet2 = null,
          products = null,
          sharedModuleSets = null,
          reason = "Module set contains $totalModuleCount modules. Consider splitting into smaller, more focused sets for better maintainability.",
          impact = mapOf("moduleCount" to totalModuleCount)
        ))
      }
    }
  }
  
  // Remove duplicates and sort by priority
  val uniqueSuggestions = mutableListOf<UnificationSuggestion>()
  val seen = mutableSetOf<String>()
  for (suggestion in suggestions) {
    val key = listOf(suggestion.strategy, suggestion.moduleSet1, suggestion.moduleSet2, suggestion.moduleSet).toString()
    if (!seen.contains(key)) {
      seen.add(key)
      uniqueSuggestions.add(suggestion)
    }
  }
  
  // Sort by priority: high > medium > low
  val priorityOrder = mapOf("high" to 3, "medium" to 2, "low" to 1)
  uniqueSuggestions.sortByDescending { priorityOrder[it.priority] ?: 0 }
  
  return uniqueSuggestions.take(maxSuggestions)
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
 * Analyzes the impact of merging, moving, or inlining module sets.
 * Checks for violations, calculates size impact, and provides recommendations.
 * 
 * @param sourceSet Source module set name
 * @param targetSet Target module set name (null for inline operation)
 * @param operation Operation type:
 *   - "merge" or "move": Combine source modules into target (treated identically - 
 *     both validate architectural constraints and analyze impact of combining sets)
 *   - "inline": Remove module set, add modules directly to products
 * @param allModuleSets All module sets with metadata
 * @param products All products
 * @return Impact analysis result with violations, size metrics, and recommendation
 */
fun analyzeMergeImpact(
  sourceSet: String,
  targetSet: String?,
  operation: String,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>
): MergeImpactResult {
  // Find source module set
  val sourceEntry = allModuleSets.firstOrNull { it.moduleSet.name == sourceSet }
  if (sourceEntry == null) {
    return MergeImpactResult(
      operation = operation,
      sourceSet = sourceSet,
      targetSet = targetSet,
      productsUsingSource = emptyList(),
      productsUsingTarget = emptyList(),
      productsThatWouldChange = emptyList(),
      sizeImpact = emptyMap(),
      violations = listOf(mapOf(
        "type" to "notFound",
        "severity" to "error",
        "message" to "Source module set '$sourceSet' not found"
      )),
      recommendation = "ERROR: Cannot analyze - source module set not found",
      safe = false
    )
  }
  
  // Find target module set (if applicable)
  var targetEntry: ModuleSetMetadata? = null
  if (targetSet != null) {
    targetEntry = allModuleSets.firstOrNull { it.moduleSet.name == targetSet }
    if (targetEntry == null) {
      return MergeImpactResult(
        operation = operation,
        sourceSet = sourceSet,
        targetSet = targetSet,
        productsUsingSource = emptyList(),
        productsUsingTarget = emptyList(),
        productsThatWouldChange = emptyList(),
        sizeImpact = emptyMap(),
        violations = listOf(mapOf(
          "type" to "notFound",
          "severity" to "error",
          "message" to "Target module set '$targetSet' not found"
        )),
        recommendation = "ERROR: Cannot analyze - target module set not found",
        safe = false
      )
    }
  }
  
  // Validate that source and target are different
  if (targetSet != null && sourceSet == targetSet) {
    return MergeImpactResult(
      operation = operation,
      sourceSet = sourceSet,
      targetSet = targetSet,
      productsUsingSource = emptyList(),
      productsUsingTarget = emptyList(),
      productsThatWouldChange = emptyList(),
      sizeImpact = emptyMap(),
      violations = listOf(mapOf(
        "type" to "validation",
        "severity" to "error",
        "message" to "Source and target cannot be the same module set: '$sourceSet'"
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
  
  // Calculate module changes (use collectAllModuleNames to include nested sets)
  val sourceModules = collectAllModuleNames(sourceEntry.moduleSet)
  val targetModules = if (targetEntry != null) {
    collectAllModuleNames(targetEntry.moduleSet)
  } else {
    emptySet()
  }
  
  val newModules = sourceModules.minus(targetModules)
  val duplicateModules = sourceModules.intersect(targetModules)
  
  // Check for community/ultimate violations
  val violations = mutableListOf<Map<String, Any>>()
  if (operation == "merge" && targetEntry != null) {
    val sourceLocation = sourceEntry.location
    val targetLocation = targetEntry.location
    
    if (sourceLocation == "ultimate" && targetLocation == "community") {
      violations.add(mapOf(
        "type" to "location",
        "severity" to "error",
        "message" to "Cannot merge ultimate module set \"$sourceSet\" into community module set \"$targetSet\"",
        "fix" to "Move \"$targetSet\" to ultimate directory, or extract community modules from \"$sourceSet\""
      ))
    }
    
    // Check if any community products would gain ultimate modules
    val communityProductsUsingTarget = productsUsingTarget.filter { p ->
      val productSets = p.contentSpec?.moduleSets?.map { it.moduleSet.name } ?: emptyList()
      !productSets.contains("commercialIdeBase") && !productSets.contains("ide.ultimate")
    }
    
    if (sourceLocation == "ultimate" && communityProductsUsingTarget.isNotEmpty()) {
      violations.add(mapOf(
        "type" to "community-uses-ultimate",
        "severity" to "error",
        "message" to "Merging ultimate set \"$sourceSet\" into \"$targetSet\" would expose ultimate modules to ${communityProductsUsingTarget.size} community products",
        "affectedProducts" to communityProductsUsingTarget.map { it.name },
        "fix" to "Remove \"$targetSet\" from community products, or split ultimate modules from \"$sourceSet\""
      ))
    }
  }
  
  // Calculate size impact
  val sizeImpact = mapOf(
    "sourceModuleCount" to sourceModules.size,
    "targetModuleCount" to targetModules.size,
    "newModulesToTarget" to newModules.size,
    "duplicateModules" to duplicateModules.size,
    "resultingModuleCount" to targetModules.size + newModules.size
  )
  
  // Generate recommendation
  val recommendation = when {
    violations.isNotEmpty() -> "NOT RECOMMENDED: Operation would introduce violations. See violations for details."
    operation == "merge" && duplicateModules.isNotEmpty() -> 
      "CAUTION: ${duplicateModules.size} modules already exist in target. Merge would create no duplicates, but review if modules serve the same purpose."
    operation == "merge" && newModules.isNotEmpty() -> 
      "SAFE TO MERGE: Would add ${newModules.size} new modules to \"$targetSet\". ${productsUsingTarget.size} products using target would gain these modules."
    operation == "inline" -> 
      "SAFE TO INLINE: ${productsUsingSource.size} products using \"$sourceSet\" would directly include ${sourceModules.size} modules instead."
    else -> "Operation appears safe based on current analysis."
  }
  
  return MergeImpactResult(
    operation = operation,
    sourceSet = sourceSet,
    targetSet = targetSet,
    productsUsingSource = productsUsingSource.map { it.name },
    productsUsingTarget = productsUsingTarget.map { it.name },
    productsThatWouldChange = if (operation == "merge") {
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
