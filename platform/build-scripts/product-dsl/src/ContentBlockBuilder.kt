// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * Builds content blocks and module-to-set chain mappings from product specifications.
 *
 * This file provides the core traversal logic for processing [ProductModulesContentSpec] into
 * structured content blocks used for XML generation and validation. It performs a single
 * hierarchical traversal that simultaneously:
 * - Builds [ContentBlock] list for each module set
 * - Creates module-to-set chain mapping for tracing module origins
 * - Collects module aliases from module sets
 * - Tracks `includeDependencies` flags
 * - Validates for duplicate modules and invalid overrides
 *
 * **Key function**: [buildContentBlocksAndChainMapping] - the main entry point that processes
 * a product spec and returns all computed mappings in [ContentBuildData].
 *
 * @see ProductModulesContentSpec for the input specification
 * @see ContentBlock for the output structure
 * @see validation.logic.DslConstraints for validation rules applied during traversal
 */
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import org.jetbrains.intellij.build.productLayout.validator.rule.validateAndRecordAlias
import org.jetbrains.intellij.build.productLayout.validator.rule.validateModuleSetOverrides
import org.jetbrains.intellij.build.productLayout.validator.rule.validateNoDuplicateModules

internal data class ContentBuildData(
  @JvmField val contentBlocks: List<ContentBlock>,
  @JvmField val moduleToSetChainMapping: Map<ContentModuleName, List<String>>,
  @JvmField val aliasToSource: Map<String, String>,
  @JvmField val moduleToIncludeDependencies: Map<ContentModuleName, Boolean>,
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
  val moduleToChain = LinkedHashMap<ContentModuleName, List<String>>()
  val moduleToSets = LinkedHashMap<String, MutableList<String>>()  // String keys for validateNoDuplicateModules
  val moduleToIncludeDeps = LinkedHashMap<ContentModuleName, Boolean>()
  val aliasToSource = if (collectModuleSetAliases) LinkedHashMap<String, String>() else null
  val processedSets = HashSet<String>()
  val contentBlockByName = HashMap<String, ContentBlock>()

  fun traverse(moduleSet: ModuleSet, chain: List<String>, overrides: Map<ContentModuleName, ModuleLoadingRuleValue>) {
    val setName = "$MODULE_SET_PREFIX${moduleSet.name}"
    
    // Check if already processed
    val alreadyProcessed = !processedSets.add(setName)
    if (alreadyProcessed) {
      // If already processed, but now we have overrides, update the existing content block
      if (overrides.isNotEmpty()) {
        val existingBlock = contentBlockByName.get(moduleSet.name)
        if (existingBlock != null) {
          // Check if existing block has any non-default loading attributes (OPTIONAL is the default)
          val hasExistingOverrides = existingBlock.modules.any { it.loading != ModuleLoadingRuleValue.OPTIONAL }
          if (!hasExistingOverrides) {
            // Reuse the filtered module list from existing block (already filtered by first pass)
            val updatedModules = mutableListOf<ContentModule>()
            for (existingModule in existingBlock.modules) {
              val effectiveLoading = overrides.get(existingModule.name) ?: existingModule.loading
              updatedModules.add(
                ContentModule(
                  existingModule.name,
                  effectiveLoading,
                  existingModule.includeDependencies,
                  existingModule.allowedMissingPluginIds,
                )
              )
            }
            
            // Create new content block with overrides and replace the old one
            val updatedBlock = ContentBlock(moduleSet.name, updatedModules)
            val oldBlockIndex = contentBlocks.indexOf(existingBlock)
            if (oldBlockIndex >= 0) {
              contentBlocks[oldBlockIndex] = updatedBlock
              contentBlockByName.put(moduleSet.name, updatedBlock)
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
    val modulesWithLoading = mutableListOf<ContentModule>()
    for (module in moduleSet.modules) {
      // Track for duplicate detection (String keys for validateNoDuplicateModules)
      moduleToSets.computeIfAbsent(module.name.value) { mutableListOf() }.add(moduleSet.name)
      // Track chain
      moduleToChain.put(module.name, currentChain)
      // Track includeDependencies flag
      if (module.includeDependencies) {
        moduleToIncludeDeps.put(module.name, true)
      }
      // Build loading info - apply overrides from module set
      val effectiveLoading = overrides.get(module.name) ?: module.loading
      modulesWithLoading.add(
        ContentModule(
          module.name,
          effectiveLoading,
          module.includeDependencies,
          module.allowedMissingPluginIds,
        )
      )
    }

    if (modulesWithLoading.isNotEmpty()) {
      val block = ContentBlock(moduleSet.name, modulesWithLoading)
      contentBlocks.add(block)
      contentBlockByName.put(moduleSet.name, block)
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

  // Add additional modules if any
  val additionalModulesWithLoading = mutableListOf<ContentModule>()
  for (module in spec.additionalModules) {
    additionalModulesWithLoading.add(
      ContentModule(
        module.name,
        module.loading,
        module.includeDependencies,
        module.allowedMissingPluginIds,
      )
    )
    // Track includeDependencies flag
    if (module.includeDependencies) {
      moduleToIncludeDeps.put(module.name, true)
    }
    // Track for duplicate detection - mark as from "additionalModules" block (String keys for validateNoDuplicateModules)
    moduleToSets.computeIfAbsent(module.name.value) { mutableListOf() }.add(ADDITIONAL_MODULES_BLOCK)
  }

  if (additionalModulesWithLoading.isNotEmpty()) {
    contentBlocks.add(ContentBlock(ADDITIONAL_MODULES_BLOCK, additionalModulesWithLoading))
  }

  // Check for duplicates and FAIL if found (after ALL modules are collected, including additionalModules)
  validateNoDuplicateModules(moduleToSets)

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
 * @return List of validated unique aliases as PluginId
 */
internal fun collectAndValidateAliases(
  spec: ProductModulesContentSpec,
  moduleSetAliases: Map<String, String>
): List<PluginId> {
  val allAliases = HashMap(moduleSetAliases)

  // Collect product-level aliases and check for conflicts with module set aliases
  for (alias in spec.productModuleAliases) {
    validateAndRecordAlias(
      alias = alias,
      source = "product level",
      aliasToSource = allAliases
    )
  }

  return allAliases.keys.sorted().map { PluginId(it) }
}
