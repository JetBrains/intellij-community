// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.pipeline

import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.deps.PluginDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.model.error.FileDiff
import org.jetbrains.intellij.build.productLayout.stats.DependencyFileResult
import org.jetbrains.intellij.build.productLayout.stats.ModuleSetFileResult
import org.jetbrains.intellij.build.productLayout.stats.PluginDependencyFileResult
import org.jetbrains.intellij.build.productLayout.stats.PluginXmlFileResult
import org.jetbrains.intellij.build.productLayout.stats.ProductFileResult
import org.jetbrains.intellij.build.productLayout.stats.SuppressionUsage
import org.jetbrains.intellij.build.productLayout.stats.TestPluginFileResult
import java.nio.file.Path

// ============================================================================
// Output Data Classes
// ============================================================================
// These combine reporting data and inter-generator communication data into
// unified types that flow through slots. Each generator publishes ONE output.

/**
 * Output from module set XML generation.
 */
internal data class ModuleSetsOutput(
  /** Results grouped by label (community, ultimate, core) */
  @JvmField val resultsByLabel: List<LabelResult>,
  /** Tracking map: directory → generated file names (for orphan cleanup) */
  @JvmField val trackingMaps: Map<Path, Set<String>>,
  @JvmField val diffs: List<FileDiff> = emptyList(),
) {
  /** Per-label result */
  data class LabelResult(
    @JvmField val label: String,
    @JvmField val outputDir: Path,
    @JvmField val files: List<ModuleSetFileResult>,
    @JvmField val trackingMap: Map<Path, Set<String>>,
  )

  /** All files across all labels (convenience accessor) */
  val files: List<ModuleSetFileResult>
    get() = resultsByLabel.flatMap { it.files }
}

/**
 * Output from product module dependency generation.
 * Handles modules declared in module sets (e.g., `essential()`, `ideCommon()`).
 */
internal data class ProductModuleDepsOutput(
  @JvmField val files: List<DependencyFileResult>,
  @JvmField val diffs: List<FileDiff> = emptyList(),
)

/**
 * Output from content module dependency generation (moduleName.xml files).
 * Used for both reporting and inter-generator validation.
 */
internal data class ContentModuleOutput(
  /** Results for each content module - contains both reporting and inter-gen data */
  @JvmField val files: List<DependencyFileResult>,
  @JvmField val diffs: List<FileDiff> = emptyList(),
)



/**
 * Output from plugin.xml dependency generation.
 * Contains both plugin-level results and detailed inter-generator data.
 */
internal data class PluginXmlOutput(
  /** Plugin-level results for reporting */
  @JvmField val files: List<PluginDependencyFileResult>,
  /** Detailed results for inter-generator validation (contains JPS deps, existing XML deps, etc.) */
  @JvmField val detailedResults: List<PluginXmlFileResult>,
  @JvmField val diffs: List<FileDiff> = emptyList(),
)

/**
 * Output from product XML generation.
 */
internal data class ProductsOutput(
  @JvmField val files: List<ProductFileResult>,
  @JvmField val diffs: List<FileDiff> = emptyList(),
)

/**
 * Output from test plugin XML generation.
 */
internal data class TestPluginsOutput(
  @JvmField val files: List<TestPluginFileResult>,
  @JvmField val diffs: List<FileDiff> = emptyList(),
)

/**
 * Output from suppression config generation.
 */
internal data class SuppressionConfigOutput(
  /** Number of modules with implicit dependencies */
  @JvmField val moduleCount: Int,
  /** Total number of suppressed dependencies */
  @JvmField val suppressionCount: Int,
  /** Whether the config file was modified */
  @JvmField val configModified: Boolean,
  /** Number of stale suppressions removed */
  @JvmField val staleCount: Int = 0,
  @JvmField val diffs: List<FileDiff> = emptyList(),
)

// ============================================================================
// Slot Definitions
// ============================================================================
// All slots are defined here as the single source of truth for inter-node
// data exchange. The pipeline infers execution order from these dependencies.

/**
 * All typed slots for the generation pipeline.
 *
 * Nodes declare which slots they [PipelineNode.requires] and [PipelineNode.produces].
 * The pipeline builds a dependency graph from these declarations.
 */
internal object Slots {
  // ============ Generation Slots (produce output files) ============
  @JvmField val CONTENT_MODULE_PLAN = DataSlot<ContentModuleDependencyPlanOutput>("contentModulePlan")
  @JvmField val PLUGIN_DEPENDENCY_PLAN = DataSlot<PluginDependencyPlanOutput>("pluginDependencyPlan")

  /** Module set XML file generation results */
  @JvmField val MODULE_SETS = DataSlot<ModuleSetsOutput>("moduleSets")

  /** Product module dependency generation results (modules in module sets) */
  @JvmField val PRODUCT_MODULE_DEPS = DataSlot<ProductModuleDepsOutput>("productModuleDeps")

  /** Content module dependency generation results (all content modules including test descriptors) */
  @JvmField val CONTENT_MODULE = DataSlot<ContentModuleOutput>("contentModule")

  /** Plugin.xml dependency generation results */
  @JvmField val PLUGIN_XML = DataSlot<PluginXmlOutput>("pluginXml")

  /** Product XML file generation results */
  @JvmField val PRODUCTS = DataSlot<ProductsOutput>("products")

  /** Test plugin XML file generation results */
  @JvmField val TEST_PLUGINS = DataSlot<TestPluginsOutput>("testPlugins")

  /** Test plugin dependency planning results */
  @JvmField val TEST_PLUGIN_DEPENDENCY_PLAN = DataSlot<TestPluginDependencyPlanOutput>("testPluginDepsPlan")

  /** Suppression config generation results */
  @JvmField val SUPPRESSION_CONFIG = DataSlot<SuppressionConfigOutput>("suppressionConfig")

  // ============ Validation Slots (produce inter-node data) ============

  /** Suppression usages from LibraryModuleValidator */
  @JvmField val LIBRARY_SUPPRESSIONS = DataSlot<List<SuppressionUsage>>("librarySuppressions")

  /** Suppression usages from TestLibraryScopeValidator */
  @JvmField val TEST_LIBRARY_SCOPE_SUPPRESSIONS = DataSlot<List<SuppressionUsage>>("testLibraryScopeSuppressions")
}
