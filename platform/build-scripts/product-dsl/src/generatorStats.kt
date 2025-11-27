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
  UNCHANGED
}

/**
 * Result of generating a single module set XML file.
 */
data class ModuleSetFileResult(
  /** File name (e.g., "intellij.moduleSets.essential.xml") */
  val fileName: String,
  /** Change status of the file */
  val status: FileChangeStatus,
  /** Number of direct modules in this set (excluding nested) */
  val moduleCount: Int,
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
) {
  val createdCount: Int get() = files.count { it.status == FileChangeStatus.CREATED }
  val modifiedCount: Int get() = files.count { it.status == FileChangeStatus.MODIFIED }
  val unchangedCount: Int get() = files.count { it.status == FileChangeStatus.UNCHANGED }
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
  val products: List<ProductFileResult>,
) {
  val createdCount: Int get() = products.count { it.status == FileChangeStatus.CREATED }
  val modifiedCount: Int get() = products.count { it.status == FileChangeStatus.MODIFIED }
  val unchangedCount: Int get() = products.count { it.status == FileChangeStatus.UNCHANGED }
}

// ANSI color codes
private const val RESET = "\u001B[0m"
private const val BOLD = "\u001B[1m"
private const val GREEN = "\u001B[32m"
private const val YELLOW = "\u001B[33m"
private const val BLUE = "\u001B[34m"
private const val CYAN = "\u001B[36m"
private const val GRAY = "\u001B[90m"

/**
 * Formats file change status to colored icon and text representation.
 * @return Pair of (coloredStatusIcon, statusText)
 */
private fun formatFileStatus(status: FileChangeStatus): Pair<String, String> {
  return when (status) {
    FileChangeStatus.CREATED -> "${YELLOW}+${RESET}" to "${YELLOW}created${RESET}"
    FileChangeStatus.MODIFIED -> "${BLUE}✓${RESET}" to "${BLUE}modified${RESET}"
    FileChangeStatus.UNCHANGED -> "${GRAY}•${RESET}" to "${GRAY}unchanged${RESET}"
  }
}

/**
 * Builds a colored summary string showing file change counts.
 * @return Formatted string like "2 created, 5 modified, 10 unchanged"
 */
private fun buildChangesSummary(createdCount: Int, modifiedCount: Int, unchangedCount: Int): String {
  return buildList {
    if (createdCount > 0) add("${YELLOW}$createdCount created${RESET}")
    if (modifiedCount > 0) add("${BLUE}$modifiedCount modified${RESET}")
    if (unchangedCount > 0) add("${GRAY}$unchangedCount unchanged${RESET}")
  }.joinToString(", ")
}

/**
 * Prints a formatted summary of generation results with colors.
 */
fun printGenerationSummary(
  moduleSetResults: List<ModuleSetGenerationResult>,
  productResult: ProductGenerationResult?,
  durationMs: Long
) {
  println()
  println("${CYAN}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}")
  println("${CYAN}${BOLD}Module Sets${RESET}")
  println("${CYAN}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}")

  for (result in moduleSetResults) {
    val relativeDir = result.outputDir.toString().replace(System.getProperty("user.home"), "~")
    println("${BOLD}${result.label.replaceFirstChar { it.uppercase() }}${RESET} ${GRAY}($relativeDir)${RESET}")

    // Show changed files
    val changedFiles = result.files.filter { it.status != FileChangeStatus.UNCHANGED }
    for (file in changedFiles.take(5)) {
      val (statusIcon, statusText) = formatFileStatus(file.status)
      println("  $statusIcon ${file.fileName} ($statusText, ${BOLD}${file.moduleCount}${RESET} modules)")
    }

    // Show summary if there are more files
    val unchangedCount = result.unchangedCount
    if (unchangedCount > 0) {
      val fileWord = if (unchangedCount == 1) "file" else "files"
      println("  ${GRAY}• $unchangedCount $fileWord unchanged${RESET}")
    }

    val totalFiles = result.files.size
    val changesSummary = buildChangesSummary(result.createdCount, result.modifiedCount, unchangedCount)

    println("  ${BOLD}Total:${RESET} $totalFiles files ($changesSummary), ${BOLD}${result.totalModules}${RESET} modules")
    println()
  }

  if (productResult != null && productResult.products.isNotEmpty()) {
    println("${CYAN}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}")
    println("${CYAN}${BOLD}Products${RESET}")
    println("${CYAN}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}")

    for (product in productResult.products) {
      val (statusIcon, statusText) = formatFileStatus(product.status)
      println("$statusIcon ${BOLD}${product.productName}${RESET} ${GRAY}(${product.relativePath})${RESET}")
      println("  Status: $statusText")
      println("  Content: ${BOLD}${product.includeCount}${RESET} xi:includes, ${BOLD}${product.contentBlockCount}${RESET} content blocks, ${BOLD}${product.totalModules}${RESET} modules")
    }

    // Show summary with breakdown
    val changesSummary = buildChangesSummary(productResult.createdCount, productResult.modifiedCount, productResult.unchangedCount)

    val productFileWord = if (productResult.products.size == 1) "file" else "files"
    println("  ${BOLD}Total:${RESET} ${productResult.products.size} $productFileWord ($changesSummary)")
    println()
  }

  // Overall summary
  val totalModuleSetFiles = moduleSetResults.sumOf { it.files.size }
  val totalModuleSetCreated = moduleSetResults.sumOf { it.createdCount }
  val totalModuleSetModified = moduleSetResults.sumOf { it.modifiedCount }
  val totalModuleSetUnchanged = moduleSetResults.sumOf { it.unchangedCount }

  println("${CYAN}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}")
  println("${CYAN}${BOLD}Summary${RESET}")
  println("${CYAN}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}")

  val moduleSetFileWord = if (totalModuleSetFiles == 1) "file" else "files"
  val moduleSetSummary = buildChangesSummary(totalModuleSetCreated, totalModuleSetModified, totalModuleSetUnchanged)
  println("${GREEN}✓${RESET} ${BOLD}$totalModuleSetFiles${RESET} module set $moduleSetFileWord ($moduleSetSummary)")

  if (productResult != null) {
    val productFileWord = if (productResult.products.size == 1) "file" else "files"
    val productSummary = buildChangesSummary(productResult.createdCount, productResult.modifiedCount, productResult.unchangedCount)
    println("${GREEN}✓${RESET} ${BOLD}${productResult.products.size}${RESET} product $productFileWord ($productSummary)")
  }

  println("${GREEN}⏱${RESET} Completed in ${BOLD}${durationMs / 1000.0}s${RESET}")
  println("${CYAN}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}")
}