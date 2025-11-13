// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRule

internal data class ContentBuildData(
  @JvmField val contentBlocks: List<ContentBlock>,
  @JvmField val moduleToSetChainMapping: Map<String, List<String>>,
  @JvmField val aliasToSource: Map<String, String>,
  @JvmField val moduleToIncludeDependencies: Map<String, Boolean>,
)

/**
 * Builds content blocks and module-to-set chain mapping in a single hierarchical traversal.
 * This optimized version eliminates redundant tree walking by computing all results simultaneously.
 *
 * @param spec The product modules specification
 * @param collectModuleSetAliases Whether to collect module set aliases during traversal (for inlineModuleSets mode)
 * @return ContentBuildData containing all computed mappings
 */
internal fun buildContentBlocksAndChainMapping(
  spec: ProductModulesContentSpec,
  collectModuleSetAliases: Boolean = false
): ContentBuildData {
  val contentBlocks = mutableListOf<ContentBlock>()
  val moduleToChain = mutableMapOf<String, List<String>>()
  val moduleToSets = mutableMapOf<String, MutableList<String>>()
  val moduleToIncludeDeps = mutableMapOf<String, Boolean>()
  val aliasToSource = if (collectModuleSetAliases) mutableMapOf<String, String>() else null
  val processedSets = HashSet<String>()
  val contentBlockByName = HashMap<String, ContentBlock>()

  fun traverse(moduleSet: ModuleSet, chain: List<String>, overrides: Map<String, ModuleLoadingRule>) {
    val setName = "$MODULE_SET_PREFIX${moduleSet.name}"
    
    // Check if already processed
    val alreadyProcessed = !processedSets.add(setName)
    if (alreadyProcessed) {
      // If already processed, but now we have overrides, update the existing content block
      if (overrides.isNotEmpty()) {
        val existingBlock = contentBlockByName[moduleSet.name]
        if (existingBlock != null) {
          // Check if existing block has any loading attributes
          val hasExistingOverrides = existingBlock.modules.any { it.loading != null }
          if (!hasExistingOverrides) {
            // Reuse the filtered module list from existing block (already filtered by first pass)
            val updatedModules = mutableListOf<ModuleWithLoading>()
            for (existingModule in existingBlock.modules) {
              val effectiveLoading = overrides[existingModule.name] ?: existingModule.loading
              updatedModules.add(ModuleWithLoading(existingModule.name, effectiveLoading, existingModule.includeDependencies))
            }
            
            // Create new content block with overrides and replace the old one
            val updatedBlock = ContentBlock(moduleSet.name, updatedModules)
            val oldBlockIndex = contentBlocks.indexOf(existingBlock)
            if (oldBlockIndex >= 0) {
              contentBlocks[oldBlockIndex] = updatedBlock
              contentBlockByName[moduleSet.name] = updatedBlock
            }
            // Don't re-add to moduleToSets or moduleToChain - already there from first pass
          }
          // else: Both have overrides, keep the first one (shouldn't happen in practice)
        }
      }
      // Already processed, don't reprocess
      return
    }
    // Collect module set alias if requested
    if (aliasToSource != null && moduleSet.alias != null) {
      validateAndRecordAlias(
        alias = moduleSet.alias,
        source = "module set '${moduleSet.name}'",
        aliasToSource = aliasToSource
      )
    }
    
    val currentChain = chain + setName
    // Build content block and track chains/duplicates in single pass
    val modulesWithLoading = mutableListOf<ModuleWithLoading>()
    for (module in moduleSet.modules) {
      // Track for duplicate detection
      moduleToSets.computeIfAbsent(module.name) { mutableListOf() }.add(moduleSet.name)
      // Track chain
      moduleToChain[module.name] = currentChain
      // Track includeDependencies flag
      if (module.includeDependencies) {
        moduleToIncludeDeps[module.name] = true
      }
      // Build loading info - apply overrides from module set
      val effectiveLoading = overrides[module.name] ?: module.loading
      modulesWithLoading.add(ModuleWithLoading(module.name, effectiveLoading, module.includeDependencies))
    }

    if (modulesWithLoading.isNotEmpty()) {
      val block = ContentBlock(moduleSet.name, modulesWithLoading)
      contentBlocks.add(block)
      contentBlockByName[moduleSet.name] = block
    }

    // Recursively process nested sets (no override cascading - each set must be referenced directly for overrides)
    for (nestedSet in moduleSet.nestedSets) {
      traverse(nestedSet, currentChain, emptyMap())
    }
  }

  // Process all top-level module sets
  for (moduleSetWithOverrides in spec.moduleSets) {
    traverse(moduleSetWithOverrides.moduleSet, emptyList(), moduleSetWithOverrides.loadingOverrides)
  }

  // Validate that all overridden modules exist as direct modules in their respective module sets
  for (moduleSetWithOverrides in spec.moduleSets) {
    validateModuleSetOverrides(moduleSetWithOverrides)
  }

  // Check for duplicates and FAIL if found
  validateNoDuplicateModules(moduleToSets)

  // Add additional modules if any
  val additionalModulesWithLoading = mutableListOf<ModuleWithLoading>()
  for (module in spec.additionalModules) {
    additionalModulesWithLoading.add(ModuleWithLoading(module.name, module.loading, module.includeDependencies))
    // Track includeDependencies flag
    if (module.includeDependencies) {
      moduleToIncludeDeps[module.name] = true
    }
  }

  if (additionalModulesWithLoading.isNotEmpty()) {
    contentBlocks.add(ContentBlock(ADDITIONAL_MODULES_BLOCK, additionalModulesWithLoading))
  }

  return ContentBuildData(
    contentBlocks = contentBlocks,
    moduleToSetChainMapping = moduleToChain,
    aliasToSource = aliasToSource ?: emptyMap(),
    moduleToIncludeDependencies = moduleToIncludeDeps,
  )
}

/**
 * Collects and validates product-level aliases and merges with module set aliases.
 * Checks for duplicates between product-level and module set aliases.
 *
 * @param spec The product modules specification
 * @param moduleSetAliases Aliases collected from module sets during traversal
 * @return List of validated unique aliases
 */
internal fun collectAndValidateAliases(
  spec: ProductModulesContentSpec,
  moduleSetAliases: Map<String, String>
): List<String> {
  val allAliases = HashMap(moduleSetAliases)

  // Collect product-level aliases and check for conflicts with module set aliases
  for (alias in spec.productModuleAliases) {
    validateAndRecordAlias(
      alias = alias,
      source = "product level",
      aliasToSource = allAliases
    )
  }

  return allAliases.keys.sorted()
}
