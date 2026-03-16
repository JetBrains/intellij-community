// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.pipeline

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.dependency.PluginContentCache
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.productLayout.discovery.ModuleSetGenerationConfig
import org.jetbrains.intellij.build.productLayout.stats.SuppressionUsage
import org.jetbrains.intellij.build.productLayout.util.DeferredFileUpdater
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
import java.nio.file.Path

/**
 * Result of Stage 1: Discovery.
 *
 * Contains all discovered module sets and products from DSL definitions.
 * This is the raw output of scanning Kotlin DSL files.
 *
 * **Produced by:** [DiscoveryStage]
 * **Consumed by:** [ModelBuildingStage]
 */
internal data class DiscoveryResult(
  /** Module sets by label (e.g., "community" → [...], "ultimate" → [...], "core" → [...]) */
  @JvmField val moduleSetsByLabel: Map<String, List<ModuleSet>>,

  /** All discovered products from dev-build.json */
  @JvmField val products: List<DiscoveredProduct>,

  /** Test product specifications (name → spec pairs) */
  @JvmField val testProductSpecs: List<Pair<String, ProductModulesContentSpec>>,

  /** Module set sources with output directories (label → (source object, output dir)) */
  @JvmField val moduleSetSources: Map<String, Pair<Any, Path>>,
) {
  /** All module sets flattened from all labels */
  val allModuleSets: List<ModuleSet> by lazy {
    moduleSetsByLabel.values.flatten()
  }

  /** Community module sets */
  val communityModuleSets: List<ModuleSet>
    get() = moduleSetsByLabel.get("community") ?: emptyList()

  /** Ultimate module sets */
  val ultimateModuleSets: List<ModuleSet>
    get() = moduleSetsByLabel.get("ultimate") ?: emptyList()

  /** Core module sets */
  val coreModuleSets: List<ModuleSet>
    get() = moduleSetsByLabel.get("core") ?: emptyList()
}

/**
 * Result of Stage 2: Model Building.
 *
 * Contains all caches and shared computed values needed by generators.
 * This is the "prepared world" that generators operate on.
 *
 * **Produced by:** [ModelBuildingStage]
 * **Consumed by:** All [PipelineNode] implementations
 *
 * **Key principle:** All expensive computations are done once here and shared.
 * Generators should never recompute these values.
 */
internal data class GenerationModel(
  // ============ Discovery data (passthrough) ============
  @JvmField val discovery: DiscoveryResult,

  // ============ Configuration ============
  @JvmField val config: ModuleSetGenerationConfig,
  @JvmField val projectRoot: Path,
  @JvmField val outputProvider: ModuleOutputProvider,
  @JvmField val isUltimateBuild: Boolean,

  // ============ Caches (created during model building) ============

  /** Async module descriptor cache */
  @JvmField val descriptorCache: ModuleDescriptorCache,

  /** Plugin content extraction cache (pre-warmed) */
  @JvmField val pluginContentCache: PluginContentCache,

  /** Deferred file updater for atomic writes */
  @JvmField val fileUpdater: DeferredFileUpdater,

  /** XML writer policy (write/diff/skip based on generation mode) */
  @JvmField val xmlWritePolicy: FileUpdateStrategy,

  /** Coroutine scope for async operations */
  @JvmField val scope: CoroutineScope,

  // ============ Pre-computed shared values ============

  /** Unified graph model for plugin/module/product relationships */
  @JvmField val pluginGraph: PluginGraph,

  /** DSL-defined test plugins with auto-added content modules (by product) */
  @JvmField val dslTestPluginsByProduct: Map<String, List<TestPluginSpec>>,

  /** Auto-added dependency chains for DSL-defined test plugins (by plugin id) */
  @JvmField val dslTestPluginDependencyChains: Map<PluginId, Map<ContentModuleName, List<ContentModuleName>>>,

  /** Suppressions used during DSL test plugin dependency traversal */
  @JvmField val dslTestPluginSuppressionUsages: List<SuppressionUsage>,

  /** Per-product allowed missing dependencies */
  @JvmField val productAllowedMissing: Map<String, Set<ContentModuleName>>,

  /** Suppression config loaded from suppressionConfigPath (single source of truth) */
  @JvmField val suppressionConfig: SuppressionConfig,

  /** If true, update suppressions.json (write directly, bypassing deferred updater) */
  @JvmField val updateSuppressions: Boolean,

  /** Effective generation mode for this run */
  @JvmField val generationMode: GenerationMode,
)

internal enum class GenerationMode {
  NORMAL,
  UPDATE_SUPPRESSIONS,
  VALIDATE_ONLY,
}
