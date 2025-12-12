// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.stats

import java.nio.file.Path

private val SEPARATOR = "\u2501".repeat(60)

private fun statusIcon(status: FileChangeStatus): String = when (status) {
  FileChangeStatus.CREATED -> "${AnsiColors.YELLOW}+${AnsiColors.RESET}"
  FileChangeStatus.MODIFIED -> "${AnsiColors.BLUE}~${AnsiColors.RESET}"
  FileChangeStatus.UNCHANGED -> "${AnsiColors.GRAY}\u2022${AnsiColors.RESET}"
  FileChangeStatus.DELETED -> "${AnsiColors.RED}-${AnsiColors.RESET}"
}

private fun <T : HasFileChangeStatus> List<T>.hasChanges(): Boolean = any { it.status != FileChangeStatus.UNCHANGED }

private fun changeCounts(created: Int, modified: Int, unchanged: Int, deleted: Int = 0): String = buildList {
  if (created > 0) add("${AnsiColors.YELLOW}$created created${AnsiColors.RESET}")
  if (modified > 0) add("${AnsiColors.BLUE}$modified modified${AnsiColors.RESET}")
  if (deleted > 0) add("${AnsiColors.RED}$deleted deleted${AnsiColors.RESET}")
  if (unchanged > 0) add("${AnsiColors.GRAY}$unchanged unchanged${AnsiColors.RESET}")
}.joinToString(", ")

/**
 * Prints a formatted summary of generation results with colors.
 * Uses compact mode when no changes, detailed mode when there are changes.
 */
internal fun printGenerationSummary(
  moduleSetResults: List<ModuleSetGenerationResult>,
  dependencyResult: DependencyGenerationResult?,
  pluginDependencyResult: PluginDependencyGenerationResult?,
  productResult: ProductGenerationResult?,
  @Suppress("UNUSED_PARAMETER") projectRoot: Path,
  durationMs: Long,
) {
  val hasChanges = moduleSetResults.any { it.files.hasChanges() } ||
                   dependencyResult?.files?.hasChanges() == true ||
                   pluginDependencyResult?.files?.hasChanges() == true ||
                   productResult?.products?.hasChanges() == true

  print(buildString {
    appendLine()
    appendLine("${AnsiColors.CYAN}${AnsiColors.BOLD}$SEPARATOR${AnsiColors.RESET}")

    if (hasChanges) {
      appendDetailedSummary(moduleSetResults, dependencyResult, pluginDependencyResult, productResult)
    }
    else {
      appendCompactSummary(moduleSetResults, dependencyResult, pluginDependencyResult, productResult)
    }

    appendLine("${AnsiColors.GREEN}\u23F1${AnsiColors.RESET} Completed in ${AnsiColors.BOLD}${durationMs / 1000.0}s${AnsiColors.RESET}")
    appendLine("${AnsiColors.CYAN}${AnsiColors.BOLD}$SEPARATOR${AnsiColors.RESET}")
  })
}

private fun StringBuilder.appendCompactSummary(
  moduleSetResults: List<ModuleSetGenerationResult>,
  dependencyResult: DependencyGenerationResult?,
  pluginDependencyResult: PluginDependencyGenerationResult?,
  productResult: ProductGenerationResult?,
) {
  appendLine("${AnsiColors.GREEN}\u2713${AnsiColors.RESET} All files unchanged")

  val totalModuleSetFiles = moduleSetResults.sumOf { it.files.size }
  val totalModules = moduleSetResults.sumOf { it.totalModules }
  appendLine("  Module sets: ${AnsiColors.BOLD}$totalModuleSetFiles${AnsiColors.RESET} files ($totalModules modules)")

  if (dependencyResult != null && dependencyResult.files.isNotEmpty()) {
    appendLine("  Module dependencies: ${AnsiColors.BOLD}${dependencyResult.files.size}${AnsiColors.RESET} files (${dependencyResult.totalDependencies} deps)")
  }

  if (pluginDependencyResult != null && pluginDependencyResult.files.isNotEmpty()) {
    val contentPart = if (pluginDependencyResult.contentModuleCount > 0) ", ${pluginDependencyResult.contentModuleCount} content modules" else ""
    appendLine("  Plugin dependencies: ${AnsiColors.BOLD}${pluginDependencyResult.files.size}${AnsiColors.RESET} files (${pluginDependencyResult.totalDependencies} deps)$contentPart")
  }

  if (productResult != null && productResult.products.isNotEmpty()) {
    appendLine("  Products: ${AnsiColors.BOLD}${productResult.products.size}${AnsiColors.RESET} files")
  }
}

private fun StringBuilder.appendDetailedSummary(
  moduleSetResults: List<ModuleSetGenerationResult>,
  dependencyResult: DependencyGenerationResult?,
  pluginDependencyResult: PluginDependencyGenerationResult?,
  productResult: ProductGenerationResult?,
) {
  appendModuleSetsSection(moduleSetResults)
  appendDependenciesSection(dependencyResult)
  appendPluginDependenciesSection(pluginDependencyResult)
  appendProductsSection(productResult)
}

private fun StringBuilder.appendModuleSetsSection(results: List<ModuleSetGenerationResult>) {
  val allFiles = results.flatMap { it.files }
  if (!allFiles.hasChanges()) {
    val total = allFiles.size
    val modules = results.sumOf { it.totalModules }
    appendLine("Module Sets (${AnsiColors.GRAY}$total unchanged${AnsiColors.RESET}, $modules modules)")
    appendLine()
    return
  }

  val counts = changeCounts(allFiles.createdCount, allFiles.modifiedCount, allFiles.unchangedCount, allFiles.deletedCount)
  appendLine("${AnsiColors.BOLD}Module Sets${AnsiColors.RESET} ($counts)")

  for (result in results) {
    val changedFiles = result.files.filter { it.status != FileChangeStatus.UNCHANGED }
    for (file in changedFiles) {
      appendLine("  ${statusIcon(file.status)} ${file.fileName} (${file.moduleCount} modules)")
    }
  }
  appendLine()
}

private fun StringBuilder.appendDependenciesSection(result: DependencyGenerationResult?) {
  if (result == null || result.files.isEmpty()) return

  if (!result.files.hasChanges()) {
    appendLine("Module Dependencies (${AnsiColors.GRAY}${result.files.size} unchanged${AnsiColors.RESET}, ${result.totalDependencies} deps)")
    appendLine()
    return
  }

  val counts = changeCounts(result.files.createdCount, result.files.modifiedCount, result.files.unchangedCount)
  appendLine("${AnsiColors.BOLD}Module Dependencies${AnsiColors.RESET} ($counts)")

  for (file in result.files.filter { it.status != FileChangeStatus.UNCHANGED }) {
    appendLine("  ${statusIcon(file.status)} ${file.moduleName} (${file.dependencyCount} deps)")
  }
  appendLine()
}

private fun StringBuilder.appendPluginDependenciesSection(result: PluginDependencyGenerationResult?) {
  if (result == null || result.files.isEmpty()) return

  val hasPluginChanges = result.files.hasChanges()
  val hasContentChanges = result.files.any { it.contentModuleResults.hasChanges() }

  if (!hasPluginChanges && !hasContentChanges) {
    val contentPart = if (result.contentModuleCount > 0) ", ${result.contentModuleCount} content modules" else ""
    appendLine("Plugin Dependencies (${AnsiColors.GRAY}${result.files.size} unchanged${AnsiColors.RESET}, ${result.totalDependencies} deps)$contentPart")
    appendLine()
    return
  }

  val counts = changeCounts(result.files.createdCount, result.files.modifiedCount, result.files.unchangedCount)
  appendLine("${AnsiColors.BOLD}Plugin Dependencies${AnsiColors.RESET} ($counts)")

  for (file in result.files.filter { it.status != FileChangeStatus.UNCHANGED }) {
    appendLine("  ${statusIcon(file.status)} ${file.pluginModuleName} (${file.dependencyCount} deps)")
    val changedContent = file.contentModuleResults.filter { it.status != FileChangeStatus.UNCHANGED }
    if (changedContent.isNotEmpty()) {
      appendLine("    ${changedContent.size} content modules updated")
    }
  }

  if (result.contentModuleCount > 0) {
    val contentCounts = changeCounts(result.contentModuleCreatedCount, result.contentModuleModifiedCount, result.contentModuleUnchangedCount)
    appendLine("  Content modules: $contentCounts")
  }
  appendLine()
}

private fun StringBuilder.appendProductsSection(result: ProductGenerationResult?) {
  if (result == null || result.products.isEmpty()) return

  if (!result.products.hasChanges()) {
    appendLine("Products (${AnsiColors.GRAY}${result.products.size} unchanged${AnsiColors.RESET})")
    appendLine()
    return
  }

  val counts = changeCounts(result.products.createdCount, result.products.modifiedCount, result.products.unchangedCount)
  appendLine("${AnsiColors.BOLD}Products${AnsiColors.RESET} ($counts)")

  for (product in result.products.filter { it.status != FileChangeStatus.UNCHANGED }) {
    appendLine("  ${statusIcon(product.status)} ${product.productName} (${product.relativePath})")
    appendLine("    ${product.includeCount} xi:includes, ${product.contentBlockCount} content blocks, ${product.totalModules} modules")
  }
  appendLine()
}
