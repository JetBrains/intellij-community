// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.pipeline

import org.jetbrains.intellij.build.productLayout.discovery.ModuleSetGenerationConfig
import org.jetbrains.intellij.build.productLayout.discovery.discoverModuleSets
import org.jetbrains.intellij.build.productLayout.validator.rule.validateNoRedundantModuleSets
import org.jetbrains.intellij.build.mapConcurrent

/**
 * Stage 1: Discovery
 *
 * Scans Kotlin DSL definitions to discover:
 * - Module sets from configured sources (community, ultimate, core)
 * - Products from dev-build.json (provided via config)
 * - Test product specifications
 *
 * **Input:** [ModuleSetGenerationConfig]
 * **Output:** [DiscoveryResult]
 *
 * **Validation:** Checks for redundant module sets across products.
 */
internal object DiscoveryStage {
  /**
   * Executes the discovery stage.
   *
   * @param config Generation configuration with module set sources and products
   * @return Discovered module sets and products
   */
  fun execute(config: ModuleSetGenerationConfig): DiscoveryResult {
    // Discover all module sets in parallel from configured sources
    val moduleSetsByLabel = config.moduleSetSources.entries.mapConcurrent { (label, source) ->
      val (sourceObj, _) = source
      label to discoverModuleSets(sourceObj)
    }.toMap()

    val allModuleSets = moduleSetsByLabel.values.flatten()

    // Validate: no redundant module sets across products
    val productSpecs = config.discoveredProducts.map { it.name to it.spec }
    validateNoRedundantModuleSets(allModuleSets = allModuleSets, productSpecs = productSpecs)

    return DiscoveryResult(
      moduleSetsByLabel = moduleSetsByLabel,
      products = config.discoveredProducts,
      testProductSpecs = config.testProductSpecs,
      moduleSetSources = config.moduleSetSources,
    )
  }
}
