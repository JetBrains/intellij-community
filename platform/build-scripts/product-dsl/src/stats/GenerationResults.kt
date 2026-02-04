// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.stats

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import org.jetbrains.intellij.build.productLayout.model.error.FileDiff
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import java.nio.file.Path

/**
 * Type of suppression - maps to fields in suppressions.json.
 */
enum class SuppressionType {
  /** contentModules[].suppressModules - module deps in content module descriptors */
  MODULE_DEP,
  /** contentModules[].suppressPlugins - plugin deps in content module descriptors */
  PLUGIN_DEP,
  /** plugins[].suppressModules - module deps in plugin.xml */
  PLUGIN_XML_MODULE,
  /** plugins[].suppressPlugins - plugin deps in plugin.xml */
  PLUGIN_XML_PLUGIN,
  /** contentModules[].suppressLibraries - library to module replacements in IML */
  LIBRARY_REPLACEMENT,
  /** contentModules[].suppressTestLibraryScope - test library scope changes in IML */
  TEST_LIBRARY_SCOPE,
}

/**
 * Record of a suppression being used during generation.
 * This is the single source of truth for stale detection in SuppressionConfigGenerator.
 *
 * Generators emit this whenever they look up a suppression and find it exists.
 * SuppressionConfigGenerator aggregates these to build suppressions.json.
 *
 * @property sourceModule The module whose descriptor is being generated
 * @property suppressedDep The dependency that was suppressed (module name, plugin ID, or library name)
 * @property type Which suppression field this maps to in suppressions.json
 */
data class SuppressionUsage(
  val sourceModule: ContentModuleName,
  @JvmField val suppressedDep: String,
  @JvmField val type: SuppressionType,
)

/**
 * Status of a generated file.
 */
enum class FileChangeStatus {
  /** File was newly created */
  CREATED,
  /** File content was modified */
  MODIFIED,
  /** File content unchanged */
  UNCHANGED,
  /** File was deleted (obsolete) */
  DELETED
}

/**
 * Interface for items that have a file change status.
 */
sealed interface HasFileChangeStatus {
  val status: FileChangeStatus
}

/** Count of items with CREATED status */
val <T : HasFileChangeStatus> List<T>.createdCount: Int
  get() = count { it.status == FileChangeStatus.CREATED }

/** Count of items with MODIFIED status */
val <T : HasFileChangeStatus> List<T>.modifiedCount: Int
  get() = count { it.status == FileChangeStatus.MODIFIED }

/** Count of items with UNCHANGED status */
val <T : HasFileChangeStatus> List<T>.unchangedCount: Int
  get() = count { it.status == FileChangeStatus.UNCHANGED }

/** Count of items with DELETED status */
val <T : HasFileChangeStatus> List<T>.deletedCount: Int
  get() = count { it.status == FileChangeStatus.DELETED }

/** Returns true if any item has a non-UNCHANGED status */
fun <T : HasFileChangeStatus> List<T>.hasChanges(): Boolean = any { it.status != FileChangeStatus.UNCHANGED }

/**
 * Result of generating a single module set XML file.
 */
data class ModuleSetFileResult(
  /** File name (e.g., "intellij.moduleSets.essential.xml") */
  @JvmField val fileName: String,
  /** Change status of the file */
  override val status: FileChangeStatus,
  /** Number of direct modules in this set (excluding nested) */
  @JvmField val moduleCount: Int,
) : HasFileChangeStatus

/**
 * Result of generating all module sets for a label (community or ultimate).
 */
data class ModuleSetGenerationResult(
  /** Label ("community" or "ultimate") */
  @JvmField val label: String,
  /** Output directory path */
  @JvmField val outputDir: Path,
  /** Results for individual files */
  @JvmField val files: List<ModuleSetFileResult>,
  /** Tracking map: directory -> set of generated file names (used for cleanup aggregation) */
  @JvmField val trackingMap: Map<Path, Set<String>> = emptyMap(),
) {
  val totalModules: Int
    get() = files.sumOf { it.moduleCount }
}

/**
 * Result of generating a single product XML file.
 */
data class ProductFileResult(
  /** Product name (e.g., "Gateway") */
  @JvmField val productName: String,
  /** Relative path from project root */
  @JvmField val relativePath: String,
  /** Change status of the file */
  override val status: FileChangeStatus,
  /** Number of `xi:include` directives */
  @JvmField val includeCount: Int,
  /** Number of content blocks */
  @JvmField val contentBlockCount: Int,
  /** Total number of modules across all content blocks */
  @JvmField val totalModules: Int,
) : HasFileChangeStatus

/**
 * Result of generating all product XML files.
 */
data class ProductGenerationResult(
  @JvmField val products: List<ProductFileResult>,
)

/**
 * Result of generating a single module descriptor dependency file.
 */
data class DependencyFileResult(
  /** Module name (e.g., "intellij.platform.core.ui") */
  val contentModuleName: ContentModuleName,
  /**
   * The actual JPS module these dependencies come from.
   * For test descriptors (._test suffix), this is the base module name without the suffix.
   * For regular descriptors, this equals [contentModuleName].
   *
   * This makes the relationship explicit: test descriptor `foo._test` gets deps from JPS module `foo`.
   */
  val sourceJpsModule: ContentModuleName = contentModuleName,
  /** Absolute path to the descriptor file */
  @JvmField val descriptorPath: Path,
  /** Change status of the file */
  override val status: FileChangeStatus,
  /**
   * Module dependencies written to the XML descriptor (production deps only, `withTests=false`).
   * These are JPS deps with COMPILE/RUNTIME scope that have module descriptors.
   * Stored in the graph as [EDGE_CONTENT_MODULE_DEPENDS_ON] edges.
   */
  val writtenDependencies: List<ContentModuleName>,
  /**
   * Test dependencies for graph (`withTests=true`, superset of [writtenDependencies]).
   * Includes JPS deps with TEST scope (e.g., `intellij.libraries.hamcrest`).
   * Stored in the graph as [EDGE_CONTENT_MODULE_DEPENDS_ON_TEST] edges.
   *
   * Used for test plugin validation - test plugins need TEST scope deps available.
   * Production validation uses [writtenDependencies] only.
   */
  val testDependencies: List<ContentModuleName> = emptyList(),
  /** Module dependencies that were already in XML before generation (from parsing existing file) */
  val existingXmlModuleDependencies: Set<ContentModuleName> = emptySet(),
  /** Plugin dependencies written to the XML descriptor (always written, no filter) */
  val writtenPluginDependencies: List<PluginId> = emptyList(),
  /** All JPS deps that are main plugin modules (for validation: IML deps → XML plugin deps) */
  @JvmField val allJpsPluginDependencies: Set<PluginId> = emptySet(),
  /** Suppression usages recorded during generation (for unified stale detection) */
  @JvmField val suppressionUsages: List<SuppressionUsage> = emptyList(),
) : HasFileChangeStatus

/**
 * Result of generating all module descriptor dependencies.
 */
data class DependencyGenerationResult(
  @JvmField val files: List<DependencyFileResult>,
  /** Validation errors found during dependency generation */
  @JvmField val errors: List<ValidationError> = emptyList(),
  /** Diffs for .iml files shown as proposed fixes (test library scope violations) - not auto-applied */
  @JvmField val diffs: List<FileDiff> = emptyList(),
) {
  val totalDependencies: Int get() = files.sumOf { it.writtenDependencies.size }
}

/**
 * Result of generating a single plugin.xml file's dependencies (without content module details).
 *
 * This is a simplified result type that only contains metadata and suppression usages.
 * All suppression tracking is done via [suppressionUsages] - the single source of truth.
 */
data class PluginXmlFileResult(
  /** Plugin module name */
  val pluginContentModuleName: ContentModuleName,
  /** Absolute path to the plugin.xml file */
  @JvmField val pluginXmlPath: Path,
  /** Change status of the file */
  override val status: FileChangeStatus,
  /** Number of dependencies added */
  @JvmField val dependencyCount: Int,
  /** Suppression usages recorded during generation (for unified stale detection) */
  @JvmField val suppressionUsages: List<SuppressionUsage> = emptyList(),
) : HasFileChangeStatus

/**
 * Result of generating a single plugin.xml dependency file.
 */
data class PluginDependencyFileResult(
  /** Plugin module name (e.g., "intellij.database.plugin") */
  val pluginContentModuleName: ContentModuleName,
  /** Absolute path to the plugin.xml file */
  @JvmField val pluginXmlPath: Path,
  /** Change status of the file */
  override val status: FileChangeStatus,
  /** Number of dependencies added */
  @JvmField val dependencyCount: Int,
  /** Results for content module descriptor updates */
  @JvmField val contentModuleResults: List<DependencyFileResult> = emptyList(),
) : HasFileChangeStatus

/**
 * Result of generating all plugin.xml dependencies for bundled plugins.
 */
data class PluginDependencyGenerationResult(
  @JvmField val files: List<PluginDependencyFileResult>,
  /** Validation errors for plugin dependencies that cannot be resolved in any bundling product */
  @JvmField val errors: List<ValidationError> = emptyList(),
) {
  val totalDependencies: Int get() = files.sumOf { it.dependencyCount }

  // Content module statistics
  private val allContentModuleResults: List<DependencyFileResult>
    get() = files.flatMap { it.contentModuleResults }

  val contentModuleCount: Int get() = allContentModuleResults.size
  val contentModuleCreatedCount: Int get() = allContentModuleResults.createdCount
  val contentModuleModifiedCount: Int get() = allContentModuleResults.modifiedCount
  val contentModuleUnchangedCount: Int get() = allContentModuleResults.unchangedCount
}

/**
 * Result of generating a single test plugin XML file.
 */
data class TestPluginFileResult(
  /** Plugin ID (e.g., "intellij.python.junit5Tests.plugin") */
  val pluginId: PluginId,
  /** Relative path from project root */
  @JvmField val relativePath: String,
  /** Change status of the file */
  override val status: FileChangeStatus,
  /** Total number of modules in the content block */
  @JvmField val moduleCount: Int,
) : HasFileChangeStatus

/**
 * Result of generating all test plugin XML files.
 */
data class TestPluginGenerationResult(
  @JvmField val plugins: List<TestPluginFileResult>,
)

/**
 * Statistics from suppression config generation.
 */
data class SuppressionConfigStats(
  /** Number of modules with suppressions */
  @JvmField val moduleCount: Int,
  /** Total number of suppressed dependencies */
  @JvmField val suppressionCount: Int,
  /** Number of stale suppressions (entries removed because JPS dependency was removed) */
  @JvmField val staleCount: Int,
  /** Whether the config file was modified */
  @JvmField val configModified: Boolean,
)

/**
 * Combined results from all generation operations.
 * Used to collect parallel generation results before printing summary.
 */
data class GenerationStats(
  @JvmField val moduleSetResults: List<ModuleSetGenerationResult>,
  @JvmField val dependencyResult: DependencyGenerationResult?,
  /** Content module dependency results (includes both regular and test descriptor modules) */
  @JvmField val contentModuleResult: DependencyGenerationResult?,
  @JvmField val pluginDependencyResult: PluginDependencyGenerationResult?,
  @JvmField val productResult: ProductGenerationResult?,
  @JvmField val testPluginResult: TestPluginGenerationResult? = null,
  @JvmField val suppressionConfigStats: SuppressionConfigStats? = null,
  @JvmField val durationMs: Long,
  /** All file diffs from DeferredFileUpdater - single source of truth for change detection */
  @JvmField val fileUpdaterDiffs: List<FileDiff> = emptyList(),
) {
  /** Uses central file tracking as single source of truth */
  val hasChanges: Boolean
    get() = fileUpdaterDiffs.isNotEmpty()
}
