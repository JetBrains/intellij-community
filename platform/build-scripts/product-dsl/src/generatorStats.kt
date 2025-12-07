// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import java.nio.file.Path

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
  val label: String,
  /** Output directory path */
  val outputDir: Path,
  /** Results for individual files */
  val files: List<ModuleSetFileResult>,
  /** Tracking map: directory -> set of generated file names (used for cleanup aggregation) */
  val trackingMap: Map<Path, Set<String>> = emptyMap(),
) {
  val totalModules: Int get() = files.sumOf { it.moduleCount }
}

/**
 * Result of generating a single product XML file.
 */
internal data class ProductFileResult(
  /** Product name (e.g., "Gateway") */
  val productName: String,
  /** Relative path from project root */
  val relativePath: String,
  /** Change status of the file */
  override val status: FileChangeStatus,
  /** Number of `xi:include` directives */
  val includeCount: Int,
  /** Number of content blocks */
  val contentBlockCount: Int,
  /** Total number of modules across all content blocks */
  val totalModules: Int,
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
  val moduleName: String,
  /** Absolute path to the descriptor file */
  val descriptorPath: Path,
  /** Change status of the file */
  override val status: FileChangeStatus,
  /** Number of dependencies added */
  val dependencyCount: Int,
) : HasFileChangeStatus

/**
 * Result of generating all module descriptor dependencies.
 */
internal data class DependencyGenerationResult(
  val files: List<DependencyFileResult>,
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
  val moduleSetResults: List<ModuleSetGenerationResult>,
  val dependencyResult: DependencyGenerationResult,
  val pluginDependencyResult: PluginDependencyGenerationResult?,
  val productResult: ProductGenerationResult,
)

// ANSI color codes
/**
 * Formats file change status to colored icon and text representation.
 * @return Pair of (coloredStatusIcon, statusText)
 */
private fun formatFileStatus(status: FileChangeStatus): Pair<String, String> {
  return when (status) {
    FileChangeStatus.CREATED -> "${AnsiColors.YELLOW}+${AnsiColors.RESET}" to "${AnsiColors.YELLOW}created${AnsiColors.RESET}"
    FileChangeStatus.MODIFIED -> "${AnsiColors.BLUE}✓${AnsiColors.RESET}" to "${AnsiColors.BLUE}modified${AnsiColors.RESET}"
    FileChangeStatus.UNCHANGED -> "${AnsiColors.GRAY}•${AnsiColors.RESET}" to "${AnsiColors.GRAY}unchanged${AnsiColors.RESET}"
    FileChangeStatus.DELETED -> "${AnsiColors.RED}-${AnsiColors.RESET}" to "${AnsiColors.RED}deleted${AnsiColors.RESET}"
  }
}

/**
 * Builds a colored summary string showing file change counts.
 * @return Formatted string like "2 created, 5 modified, 10 unchanged, 1 deleted"
 */
private fun buildChangesSummary(createdCount: Int, modifiedCount: Int, unchangedCount: Int, deletedCount: Int = 0): String {
  return buildList {
    if (createdCount > 0) add("${AnsiColors.YELLOW}$createdCount created${AnsiColors.RESET}")
    if (modifiedCount > 0) add("${AnsiColors.BLUE}$modifiedCount modified${AnsiColors.RESET}")
    if (unchangedCount > 0) add("${AnsiColors.GRAY}$unchangedCount unchanged${AnsiColors.RESET}")
    if (deletedCount > 0) add("${AnsiColors.RED}$deletedCount deleted${AnsiColors.RESET}")
  }.joinToString(", ")
}

private const val SEPARATOR = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

/**
 * Appends a section header with separator lines.
 */
private fun StringBuilder.appendSectionHeader(title: String) {
  appendLine("${AnsiColors.CYAN}${AnsiColors.BOLD}$SEPARATOR${AnsiColors.RESET}")
  appendLine("${AnsiColors.CYAN}${AnsiColors.BOLD}$title${AnsiColors.RESET}")
  appendLine("${AnsiColors.CYAN}${AnsiColors.BOLD}$SEPARATOR${AnsiColors.RESET}")
}

/**
 * Pluralizes "file" based on count.
 */
private fun fileWord(count: Int): String = if (count == 1) "file" else "files"

/**
 * Appends changed files from a list, filtering out unchanged ones.
 * Shows up to [maxToShow] changed files, then appends unchanged count.
 * @param fileLabel custom label for files (e.g., "plugin.xml files" instead of "files")
 */
private inline fun <T : HasFileChangeStatus> StringBuilder.appendChangedFilesSummary(
  files: List<T>,
  maxToShow: Int = 10,
  fileLabel: String? = null,
  appendFile: StringBuilder.(T) -> Unit,
) {
  val changedFiles = files.filter { it.status != FileChangeStatus.UNCHANGED }
  for (file in changedFiles.take(maxToShow)) {
    appendFile(file)
  }
  if (files.unchangedCount > 0) {
    val label = fileLabel ?: fileWord(files.unchangedCount)
    appendLine("  ${AnsiColors.GRAY}• ${files.unchangedCount} $label unchanged${AnsiColors.RESET}")
  }
}

/**
 * Appends a file entry with status, name, path, and dependency count.
 */
private fun StringBuilder.appendDependencyFileEntry(
  status: FileChangeStatus,
  name: String,
  relativePath: Path,
  dependencyCount: Int,
) {
  val (statusIcon, statusText) = formatFileStatus(status)
  appendLine("  $statusIcon ${AnsiColors.BOLD}$name${AnsiColors.RESET} ${AnsiColors.GRAY}($relativePath)${AnsiColors.RESET}")
  appendLine("    Status: $statusText, ${AnsiColors.BOLD}$dependencyCount${AnsiColors.RESET} dependencies")
}

/**
 * Prints a formatted summary of generation results with colors.
 */
internal fun printGenerationSummary(
  moduleSetResults: List<ModuleSetGenerationResult>,
  dependencyResult: DependencyGenerationResult?,
  pluginDependencyResult: PluginDependencyGenerationResult? = null,
  productResult: ProductGenerationResult?,
  projectRoot: Path,
  durationMs: Long,
) {
  print(buildString {
    appendLine()
    appendModuleSetsSummary(moduleSetResults)
    appendDependenciesSummary(dependencyResult, projectRoot)
    appendPluginDependenciesSummary(pluginDependencyResult, projectRoot)
    appendProductsSummary(productResult)
    appendOverallSummary(moduleSetResults, dependencyResult, pluginDependencyResult, productResult, durationMs)
  })
}

/**
 * Appends module sets section with per-label breakdown.
 */
private fun StringBuilder.appendModuleSetsSummary(moduleSetResults: List<ModuleSetGenerationResult>) {
  appendSectionHeader("Module Sets")

  for (result in moduleSetResults) {
    val relativeDir = result.outputDir.toString().replace(System.getProperty("user.home"), "~")
    appendLine("${AnsiColors.BOLD}${result.label.replaceFirstChar { it.uppercase() }}${AnsiColors.RESET} ${AnsiColors.GRAY}($relativeDir)${AnsiColors.RESET}")

    // Show changed files (up to 5)
    val changedFiles = result.files.filter { it.status != FileChangeStatus.UNCHANGED }
    for (file in changedFiles.take(5)) {
      val (statusIcon, statusText) = formatFileStatus(file.status)
      appendLine("  $statusIcon ${file.fileName} ($statusText, ${AnsiColors.BOLD}${file.moduleCount}${AnsiColors.RESET} modules)")
    }

    if (result.files.unchangedCount > 0) {
      appendLine("  ${AnsiColors.GRAY}• ${result.files.unchangedCount} ${fileWord(result.files.unchangedCount)} unchanged${AnsiColors.RESET}")
    }

    val changesSummary = buildChangesSummary(result.files.createdCount, result.files.modifiedCount, result.files.unchangedCount, result.files.deletedCount)
    appendLine("  ${AnsiColors.BOLD}Total:${AnsiColors.RESET} ${result.files.size} files ($changesSummary), ${AnsiColors.BOLD}${result.totalModules}${AnsiColors.RESET} modules")
    appendLine()
  }
}

/**
 * Appends module dependencies section.
 */
private fun StringBuilder.appendDependenciesSummary(dependencyResult: DependencyGenerationResult?, projectRoot: Path) {
  if (dependencyResult == null || dependencyResult.files.isEmpty()) return

  appendSectionHeader("Module Dependencies")
  appendChangedFilesSummary(dependencyResult.files) { file ->
    appendDependencyFileEntry(file.status, file.moduleName, projectRoot.relativize(file.descriptorPath), file.dependencyCount)
  }

  val changesSummary = buildChangesSummary(dependencyResult.files.createdCount, dependencyResult.files.modifiedCount, dependencyResult.files.unchangedCount)
  appendLine("  ${AnsiColors.BOLD}Total:${AnsiColors.RESET} ${dependencyResult.files.size} ${fileWord(dependencyResult.files.size)} ($changesSummary), ${AnsiColors.BOLD}${dependencyResult.totalDependencies}${AnsiColors.RESET} dependencies")
  appendLine()
}

/**
 * Appends plugin dependencies section.
 */
private fun StringBuilder.appendPluginDependenciesSummary(pluginDependencyResult: PluginDependencyGenerationResult?, projectRoot: Path) {
  if (pluginDependencyResult == null || pluginDependencyResult.files.isEmpty()) return

  appendSectionHeader("Plugin Dependencies")
  appendChangedFilesSummary(pluginDependencyResult.files, fileLabel = "plugin.xml ${fileWord(pluginDependencyResult.files.unchangedCount)}") { file ->
    appendDependencyFileEntry(file.status, file.pluginModuleName, projectRoot.relativize(file.pluginXmlPath), file.dependencyCount)
    // Show content module updates for this plugin
    val changedContentModules = file.contentModuleResults.filter { it.status != FileChangeStatus.UNCHANGED }
    if (changedContentModules.isNotEmpty()) {
      appendLine("    Content modules updated: ${changedContentModules.size}")
    }
  }

  val changesSummary = buildChangesSummary(pluginDependencyResult.files.createdCount, pluginDependencyResult.files.modifiedCount, pluginDependencyResult.files.unchangedCount)
  appendLine("  ${AnsiColors.BOLD}Plugin.xml Total:${AnsiColors.RESET} ${pluginDependencyResult.files.size} ${fileWord(pluginDependencyResult.files.size)} ($changesSummary), ${AnsiColors.BOLD}${pluginDependencyResult.totalDependencies}${AnsiColors.RESET} dependencies")

  // Content module summary
  if (pluginDependencyResult.contentModuleCount > 0) {
    val contentSummary = buildChangesSummary(
      pluginDependencyResult.contentModuleCreatedCount,
      pluginDependencyResult.contentModuleModifiedCount,
      pluginDependencyResult.contentModuleUnchangedCount
    )
    appendLine("  ${AnsiColors.BOLD}Content Modules:${AnsiColors.RESET} ${pluginDependencyResult.contentModuleCount} ${fileWord(pluginDependencyResult.contentModuleCount)} ($contentSummary)")
  }
  appendLine()
}

/**
 * Appends products section.
 */
private fun StringBuilder.appendProductsSummary(productResult: ProductGenerationResult?) {
  if (productResult == null || productResult.products.isEmpty()) return

  appendSectionHeader("Products")

  for (product in productResult.products) {
    val (statusIcon, statusText) = formatFileStatus(product.status)
    appendLine("$statusIcon ${AnsiColors.BOLD}${product.productName}${AnsiColors.RESET} ${AnsiColors.GRAY}(${product.relativePath})${AnsiColors.RESET}")
    appendLine("  Status: $statusText")
    appendLine("  Content: ${AnsiColors.BOLD}${product.includeCount}${AnsiColors.RESET} xi:includes, ${AnsiColors.BOLD}${product.contentBlockCount}${AnsiColors.RESET} content blocks, ${AnsiColors.BOLD}${product.totalModules}${AnsiColors.RESET} modules")
  }

  val changesSummary = buildChangesSummary(productResult.products.createdCount, productResult.products.modifiedCount, productResult.products.unchangedCount)
  appendLine("  ${AnsiColors.BOLD}Total:${AnsiColors.RESET} ${productResult.products.size} ${fileWord(productResult.products.size)} ($changesSummary)")
  appendLine()
}

/**
 * Appends overall summary with totals and timing.
 */
private fun StringBuilder.appendOverallSummary(
  moduleSetResults: List<ModuleSetGenerationResult>,
  dependencyResult: DependencyGenerationResult?,
  pluginDependencyResult: PluginDependencyGenerationResult?,
  productResult: ProductGenerationResult?,
  durationMs: Long,
) {
  appendSectionHeader("Summary")

  // Module sets total
  val totalFiles = moduleSetResults.sumOf { it.files.size }
  val totalCreated = moduleSetResults.sumOf { it.files.createdCount }
  val totalModified = moduleSetResults.sumOf { it.files.modifiedCount }
  val totalUnchanged = moduleSetResults.sumOf { it.files.unchangedCount }
  val totalDeleted = moduleSetResults.sumOf { it.files.deletedCount }
  val moduleSetSummary = buildChangesSummary(totalCreated, totalModified, totalUnchanged, totalDeleted)
  appendLine("${AnsiColors.GREEN}✓${AnsiColors.RESET} ${AnsiColors.BOLD}$totalFiles${AnsiColors.RESET} module set ${fileWord(totalFiles)} ($moduleSetSummary)")

  // Module dependencies total
  if (dependencyResult != null && dependencyResult.files.isNotEmpty()) {
    val depSummary = buildChangesSummary(dependencyResult.files.createdCount, dependencyResult.files.modifiedCount, dependencyResult.files.unchangedCount)
    appendLine("${AnsiColors.GREEN}✓${AnsiColors.RESET} ${AnsiColors.BOLD}${dependencyResult.files.size}${AnsiColors.RESET} module dependency ${fileWord(dependencyResult.files.size)} ($depSummary)")
  }

  // Plugin dependencies total
  if (pluginDependencyResult != null && pluginDependencyResult.files.isNotEmpty()) {
    val pluginDepSummary = buildChangesSummary(pluginDependencyResult.files.createdCount, pluginDependencyResult.files.modifiedCount, pluginDependencyResult.files.unchangedCount)
    appendLine("${AnsiColors.GREEN}✓${AnsiColors.RESET} ${AnsiColors.BOLD}${pluginDependencyResult.files.size}${AnsiColors.RESET} plugin dependency ${fileWord(pluginDependencyResult.files.size)} ($pluginDepSummary)")

    // Content modules summary
    if (pluginDependencyResult.contentModuleCount > 0) {
      val contentSummary = buildChangesSummary(
        pluginDependencyResult.contentModuleCreatedCount,
        pluginDependencyResult.contentModuleModifiedCount,
        pluginDependencyResult.contentModuleUnchangedCount
      )
      appendLine("${AnsiColors.GREEN}✓${AnsiColors.RESET} ${AnsiColors.BOLD}${pluginDependencyResult.contentModuleCount}${AnsiColors.RESET} content module dependency ${fileWord(pluginDependencyResult.contentModuleCount)} ($contentSummary)")
    }
  }

  // Products total
  if (productResult != null) {
    val prodSummary = buildChangesSummary(productResult.products.createdCount, productResult.products.modifiedCount, productResult.products.unchangedCount)
    appendLine("${AnsiColors.GREEN}✓${AnsiColors.RESET} ${AnsiColors.BOLD}${productResult.products.size}${AnsiColors.RESET} product ${fileWord(productResult.products.size)} ($prodSummary)")
  }

  appendLine("${AnsiColors.GREEN}⏱${AnsiColors.RESET} Completed in ${AnsiColors.BOLD}${durationMs / 1000.0}s${AnsiColors.RESET}")
  appendLine("${AnsiColors.CYAN}${AnsiColors.BOLD}$SEPARATOR${AnsiColors.RESET}")
}