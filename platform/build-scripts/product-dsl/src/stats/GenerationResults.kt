// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.stats

import java.nio.file.Path

/**
 * Status of a generated file.
 */
internal enum class FileChangeStatus {
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
internal sealed interface HasFileChangeStatus {
  val status: FileChangeStatus
}

/** Count of items with CREATED status */
internal val <T : HasFileChangeStatus> List<T>.createdCount: Int
  get() = count { it.status == FileChangeStatus.CREATED }

/** Count of items with MODIFIED status */
internal val <T : HasFileChangeStatus> List<T>.modifiedCount: Int
  get() = count { it.status == FileChangeStatus.MODIFIED }

/** Count of items with UNCHANGED status */
internal val <T : HasFileChangeStatus> List<T>.unchangedCount: Int
  get() = count { it.status == FileChangeStatus.UNCHANGED }

/** Count of items with DELETED status */
internal val <T : HasFileChangeStatus> List<T>.deletedCount: Int
  get() = count { it.status == FileChangeStatus.DELETED }

/**
 * Result of generating a single module set XML file.
 */
internal data class ModuleSetFileResult(
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
internal data class ModuleSetGenerationResult(
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
internal data class ProductFileResult(
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
internal data class ProductGenerationResult(
  @JvmField val products: List<ProductFileResult>,
)

/**
 * Result of generating a single module descriptor dependency file.
 */
internal data class DependencyFileResult(
  /** Module name (e.g., "intellij.platform.core.ui") */
  @JvmField val moduleName: String,
  /** Absolute path to the descriptor file */
  @JvmField val descriptorPath: Path,
  /** Change status of the file */
  override val status: FileChangeStatus,
  /** Number of dependencies added */
  @JvmField val dependencyCount: Int,
) : HasFileChangeStatus

/**
 * Result of generating all module descriptor dependencies.
 */
internal data class DependencyGenerationResult(
  @JvmField val files: List<DependencyFileResult>,
) {
  val totalDependencies: Int get() = files.sumOf { it.dependencyCount }
}

/**
 * Result of generating a single plugin.xml dependency file.
 */
internal data class PluginDependencyFileResult(
  /** Plugin module name (e.g., "intellij.database.plugin") */
  @JvmField val pluginModuleName: String,
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
internal data class PluginDependencyGenerationResult(
  @JvmField val files: List<PluginDependencyFileResult>,
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
 * Combined results from all generation operations.
 * Used to collect parallel generation results before printing summary.
 */
internal data class GenerationResults(
  @JvmField val moduleSetResults: List<ModuleSetGenerationResult>,
  @JvmField val dependencyResult: DependencyGenerationResult,
  @JvmField val pluginDependencyResult: PluginDependencyGenerationResult?,
  @JvmField val productResult: ProductGenerationResult,
)
