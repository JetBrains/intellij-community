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
 * Result of generating a single module set XML file.
 */
data class ModuleSetFileResult(
  /** File name (e.g., "intellij.moduleSets.essential.xml") */
  @JvmField val fileName: String,
  /** Change status of the file */
  @JvmField val status: FileChangeStatus,
  /** Number of direct modules in this set (excluding nested) */
  @JvmField val moduleCount: Int,
)

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
  val createdCount: Int get() = files.count { it.status == FileChangeStatus.CREATED }
  val modifiedCount: Int get() = files.count { it.status == FileChangeStatus.MODIFIED }
  val unchangedCount: Int get() = files.count { it.status == FileChangeStatus.UNCHANGED }
  val deletedCount: Int get() = files.count { it.status == FileChangeStatus.DELETED }
  val totalModules: Int get() = files.sumOf { it.moduleCount }
}

/**
 * Result of generating a single product XML file.
 */
data class ProductFileResult(
  /** Product name (e.g., "Gateway") */
  val productName: String,
  /** Relative path from project root */
  val relativePath: String,
  /** Change status of the file */
  val status: FileChangeStatus,
  /** Number of `xi:include` directives */
  val includeCount: Int,
  /** Number of content blocks */
  val contentBlockCount: Int,
  /** Total number of modules across all content blocks */
  val totalModules: Int,
)

/**
 * Result of generating all product XML files.
 */
data class ProductGenerationResult(
  @JvmField val products: List<ProductFileResult>,
) {
  val createdCount: Int
    get() = products.count { it.status == FileChangeStatus.CREATED }
  val modifiedCount: Int
    get() = products.count { it.status == FileChangeStatus.MODIFIED }
  val unchangedCount: Int
    get() = products.count { it.status == FileChangeStatus.UNCHANGED }
}

/**
 * Result of generating a single module descriptor dependency file.
 */
data class DependencyFileResult(
  /** Module name (e.g., "intellij.platform.core.ui") */
  val moduleName: String,
  /** Absolute path to the descriptor file */
  val descriptorPath: Path,
  /** Change status of the file */
  val status: FileChangeStatus,
  /** Number of dependencies added */
  val dependencyCount: Int,
)

/**
 * Result of generating all module descriptor dependencies.
 */
data class DependencyGenerationResult(
  val files: List<DependencyFileResult>,
) {
  val createdCount: Int get() = files.count { it.status == FileChangeStatus.CREATED }
  val modifiedCount: Int get() = files.count { it.status == FileChangeStatus.MODIFIED }
  val unchangedCount: Int get() = files.count { it.status == FileChangeStatus.UNCHANGED }
  val totalDependencies: Int get() = files.sumOf { it.dependencyCount }
}

/**
 * Combined results from all generation operations.
 * Used to collect parallel generation results before printing summary.
 */
data class GenerationResults(
  val moduleSetResults: List<ModuleSetGenerationResult>,
  val dependencyResult: DependencyGenerationResult,
  val productResult: ProductGenerationResult
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
 * Prints a section header with separator lines.
 */
private fun printSectionHeader(title: String) {
  println("${AnsiColors.CYAN}${AnsiColors.BOLD}$SEPARATOR${AnsiColors.RESET}")
  println("${AnsiColors.CYAN}${AnsiColors.BOLD}$title${AnsiColors.RESET}")
  println("${AnsiColors.CYAN}${AnsiColors.BOLD}$SEPARATOR${AnsiColors.RESET}")
}

/**
 * Pluralizes "file" based on count.
 */
private fun fileWord(count: Int): String = if (count == 1) "file" else "files"

/**
 * Prints a formatted summary of generation results with colors.
 */
fun printGenerationSummary(
  moduleSetResults: List<ModuleSetGenerationResult>,
  dependencyResult: DependencyGenerationResult?,
  productResult: ProductGenerationResult?,
  projectRoot: Path,
  durationMs: Long
) {
  println()
  printModuleSetsSummary(moduleSetResults)
  printDependenciesSummary(dependencyResult, projectRoot)
  printProductsSummary(productResult)
  printOverallSummary(moduleSetResults, dependencyResult, productResult, durationMs)
}

/**
 * Prints module sets section with per-label breakdown.
 */
private fun printModuleSetsSummary(moduleSetResults: List<ModuleSetGenerationResult>) {
  printSectionHeader("Module Sets")

  for (result in moduleSetResults) {
    val relativeDir = result.outputDir.toString().replace(System.getProperty("user.home"), "~")
    println("${AnsiColors.BOLD}${result.label.replaceFirstChar { it.uppercase() }}${AnsiColors.RESET} ${AnsiColors.GRAY}($relativeDir)${AnsiColors.RESET}")

    // Show changed files (up to 5)
    val changedFiles = result.files.filter { it.status != FileChangeStatus.UNCHANGED }
    for (file in changedFiles.take(5)) {
      val (statusIcon, statusText) = formatFileStatus(file.status)
      println("  $statusIcon ${file.fileName} ($statusText, ${AnsiColors.BOLD}${file.moduleCount}${AnsiColors.RESET} modules)")
    }

    if (result.unchangedCount > 0) {
      println("  ${AnsiColors.GRAY}• ${result.unchangedCount} ${fileWord(result.unchangedCount)} unchanged${AnsiColors.RESET}")
    }

    val changesSummary = buildChangesSummary(result.createdCount, result.modifiedCount, result.unchangedCount, result.deletedCount)
    println("  ${AnsiColors.BOLD}Total:${AnsiColors.RESET} ${result.files.size} files ($changesSummary), ${AnsiColors.BOLD}${result.totalModules}${AnsiColors.RESET} modules")
    println()
  }
}

/**
 * Prints module dependencies section.
 */
private fun printDependenciesSummary(dependencyResult: DependencyGenerationResult?, projectRoot: Path) {
  if (dependencyResult == null || dependencyResult.files.isEmpty()) return

  printSectionHeader("Module Dependencies")

  // Show changed files (up to 10)
  val changedFiles = dependencyResult.files.filter { it.status != FileChangeStatus.UNCHANGED }
  for (file in changedFiles.take(10)) {
    val (statusIcon, statusText) = formatFileStatus(file.status)
    val relativePath = projectRoot.relativize(file.descriptorPath)
    println("  $statusIcon ${AnsiColors.BOLD}${file.moduleName}${AnsiColors.RESET} ${AnsiColors.GRAY}($relativePath)${AnsiColors.RESET}")
    println("    Status: $statusText, ${AnsiColors.BOLD}${file.dependencyCount}${AnsiColors.RESET} dependencies")
  }

  if (dependencyResult.unchangedCount > 0) {
    println("  ${AnsiColors.GRAY}• ${dependencyResult.unchangedCount} ${fileWord(dependencyResult.unchangedCount)} unchanged${AnsiColors.RESET}")
  }

  val changesSummary = buildChangesSummary(dependencyResult.createdCount, dependencyResult.modifiedCount, dependencyResult.unchangedCount)
  println("  ${AnsiColors.BOLD}Total:${AnsiColors.RESET} ${dependencyResult.files.size} ${fileWord(dependencyResult.files.size)} ($changesSummary), ${AnsiColors.BOLD}${dependencyResult.totalDependencies}${AnsiColors.RESET} dependencies")
  println()
}

/**
 * Prints products section.
 */
private fun printProductsSummary(productResult: ProductGenerationResult?) {
  if (productResult == null || productResult.products.isEmpty()) return

  printSectionHeader("Products")

  for (product in productResult.products) {
    val (statusIcon, statusText) = formatFileStatus(product.status)
    println("$statusIcon ${AnsiColors.BOLD}${product.productName}${AnsiColors.RESET} ${AnsiColors.GRAY}(${product.relativePath})${AnsiColors.RESET}")
    println("  Status: $statusText")
    println("  Content: ${AnsiColors.BOLD}${product.includeCount}${AnsiColors.RESET} xi:includes, ${AnsiColors.BOLD}${product.contentBlockCount}${AnsiColors.RESET} content blocks, ${AnsiColors.BOLD}${product.totalModules}${AnsiColors.RESET} modules")
  }

  val changesSummary = buildChangesSummary(productResult.createdCount, productResult.modifiedCount, productResult.unchangedCount)
  println("  ${AnsiColors.BOLD}Total:${AnsiColors.RESET} ${productResult.products.size} ${fileWord(productResult.products.size)} ($changesSummary)")
  println()
}

/**
 * Prints overall summary with totals and timing.
 */
private fun printOverallSummary(
  moduleSetResults: List<ModuleSetGenerationResult>,
  dependencyResult: DependencyGenerationResult?,
  productResult: ProductGenerationResult?,
  durationMs: Long
) {
  printSectionHeader("Summary")

  // Module sets total
  val totalFiles = moduleSetResults.sumOf { it.files.size }
  val totalCreated = moduleSetResults.sumOf { it.createdCount }
  val totalModified = moduleSetResults.sumOf { it.modifiedCount }
  val totalUnchanged = moduleSetResults.sumOf { it.unchangedCount }
  val totalDeleted = moduleSetResults.sumOf { it.deletedCount }
  val moduleSetSummary = buildChangesSummary(totalCreated, totalModified, totalUnchanged, totalDeleted)
  println("${AnsiColors.GREEN}✓${AnsiColors.RESET} ${AnsiColors.BOLD}$totalFiles${AnsiColors.RESET} module set ${fileWord(totalFiles)} ($moduleSetSummary)")

  // Dependencies total
  if (dependencyResult != null && dependencyResult.files.isNotEmpty()) {
    val depSummary = buildChangesSummary(dependencyResult.createdCount, dependencyResult.modifiedCount, dependencyResult.unchangedCount)
    println("${AnsiColors.GREEN}✓${AnsiColors.RESET} ${AnsiColors.BOLD}${dependencyResult.files.size}${AnsiColors.RESET} dependency ${fileWord(dependencyResult.files.size)} ($depSummary)")
  }

  // Products total
  if (productResult != null) {
    val prodSummary = buildChangesSummary(productResult.createdCount, productResult.modifiedCount, productResult.unchangedCount)
    println("${AnsiColors.GREEN}✓${AnsiColors.RESET} ${AnsiColors.BOLD}${productResult.products.size}${AnsiColors.RESET} product ${fileWord(productResult.products.size)} ($prodSummary)")
  }

  println("${AnsiColors.GREEN}⏱${AnsiColors.RESET} Completed in ${AnsiColors.BOLD}${durationMs / 1000.0}s${AnsiColors.RESET}")
  println("${AnsiColors.CYAN}${AnsiColors.BOLD}$SEPARATOR${AnsiColors.RESET}")
}