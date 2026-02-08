// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.stats

import org.jetbrains.intellij.build.productLayout.model.error.ValidationError

private val SEPARATOR = "\u2501".repeat(60)

private fun statusIcon(status: FileChangeStatus): String {
  return when (status) {
    FileChangeStatus.CREATED -> "${AnsiColors.YELLOW}+${AnsiColors.RESET}"
    FileChangeStatus.MODIFIED -> "${AnsiColors.BLUE}~${AnsiColors.RESET}"
    FileChangeStatus.UNCHANGED -> "${AnsiColors.GRAY}\u2022${AnsiColors.RESET}"
    FileChangeStatus.DELETED -> "${AnsiColors.RED}-${AnsiColors.RESET}"
  }
}

private fun changeCounts(created: Int, modified: Int, unchanged: Int, deleted: Int = 0): String {
  return buildList {
    if (created > 0) add("${AnsiColors.YELLOW}$created created${AnsiColors.RESET}")
    if (modified > 0) add("${AnsiColors.BLUE}$modified modified${AnsiColors.RESET}")
    if (deleted > 0) add("${AnsiColors.RED}$deleted deleted${AnsiColors.RESET}")
    if (unchanged > 0) add("${AnsiColors.GRAY}$unchanged unchanged${AnsiColors.RESET}")
  }.joinToString(", ")
}

/**
 * Prints a formatted summary of generation results with colors.
 * Uses compact mode when no changes, detailed mode when there are changes.
 * Shows validation errors with failure indicator when present.
 */
fun printGenerationSummary(
  stats: GenerationStats,
  errors: List<ValidationError> = emptyList(),
) {
  val hasErrors = errors.isNotEmpty()
  val frameColor = if (hasErrors) AnsiColors.YELLOW else AnsiColors.CYAN

  print(buildString {
    appendLine()
    appendLine("${frameColor}${AnsiColors.BOLD}$SEPARATOR${AnsiColors.RESET}")

    when {
      hasErrors -> {
        appendLine("${AnsiColors.RED}\u2717${AnsiColors.RESET} Validation failed")
        appendLine()
        errors.forEach { append(it.format(AnsiStyle(useAnsi = true))) }
      }
      stats.hasChanges -> appendDetailedSummary(
        moduleSetResults = stats.moduleSetResults,
        dependencyResult = stats.dependencyResult,
        contentModuleResult = stats.contentModuleResult,
        pluginDependencyResult = stats.pluginDependencyResult,
        productResult = stats.productResult,
        suppressionConfigStats = stats.suppressionConfigStats,
      )
      else -> appendCompactSummary(
        moduleSetResults = stats.moduleSetResults,
        dependencyResult = stats.dependencyResult,
        contentModuleResult = stats.contentModuleResult,
        pluginDependencyResult = stats.pluginDependencyResult,
        productResult = stats.productResult,
        suppressionConfigStats = stats.suppressionConfigStats,
      )
    }

    appendLine("${frameColor}\u23F1${AnsiColors.RESET} Completed in ${AnsiColors.BOLD}${stats.durationMs / 1000.0}s${AnsiColors.RESET}")
    appendLine("${frameColor}${AnsiColors.BOLD}$SEPARATOR${AnsiColors.RESET}")
  })
}

private fun StringBuilder.appendCompactSummary(
  moduleSetResults: List<ModuleSetGenerationResult>,
  dependencyResult: DependencyGenerationResult?,
  contentModuleResult: DependencyGenerationResult?,
  pluginDependencyResult: PluginDependencyGenerationResult?,
  productResult: ProductGenerationResult?,
  suppressionConfigStats: SuppressionConfigStats?,
) {
  appendLine("${AnsiColors.GREEN}\u2713${AnsiColors.RESET} All files unchanged")

  val totalModuleSetFiles = moduleSetResults.sumOf { it.files.size }
  val totalModules = moduleSetResults.sumOf { it.totalModules }
  appendLine("  Module sets: ${AnsiColors.BOLD}$totalModuleSetFiles${AnsiColors.RESET} files ($totalModules modules)")

  if (dependencyResult != null && dependencyResult.files.isNotEmpty()) {
    appendLine("  Module dependencies: ${AnsiColors.BOLD}${dependencyResult.files.size}${AnsiColors.RESET} files (${dependencyResult.totalDependencies} deps)")
  }

  if (contentModuleResult != null && contentModuleResult.files.isNotEmpty()) {
    appendLine("  Content module dependencies: ${AnsiColors.BOLD}${contentModuleResult.files.size}${AnsiColors.RESET} files (${contentModuleResult.totalDependencies} deps)")
  }

  if (pluginDependencyResult != null && pluginDependencyResult.files.isNotEmpty()) {
    val contentPart = if (pluginDependencyResult.contentModuleCount > 0) ", ${pluginDependencyResult.contentModuleCount} content modules" else ""
    appendLine("  Plugin dependencies: ${AnsiColors.BOLD}${pluginDependencyResult.files.size}${AnsiColors.RESET} files (${pluginDependencyResult.totalDependencies} deps)$contentPart")
  }

  if (productResult != null && productResult.products.isNotEmpty()) {
    appendLine("  Products: ${AnsiColors.BOLD}${productResult.products.size}${AnsiColors.RESET} files")
  }

  if (suppressionConfigStats != null) {
    val stalePart = if (suppressionConfigStats.staleCount > 0) ", ${AnsiColors.YELLOW}${suppressionConfigStats.staleCount} stale${AnsiColors.RESET}" else ""
    appendLine("  Suppression config: ${AnsiColors.BOLD}${suppressionConfigStats.moduleCount}${AnsiColors.RESET} modules (${suppressionConfigStats.suppressionCount} suppressions$stalePart)")
  }
}

private fun StringBuilder.appendDetailedSummary(
  moduleSetResults: List<ModuleSetGenerationResult>,
  dependencyResult: DependencyGenerationResult?,
  contentModuleResult: DependencyGenerationResult?,
  pluginDependencyResult: PluginDependencyGenerationResult?,
  productResult: ProductGenerationResult?,
  suppressionConfigStats: SuppressionConfigStats?,
) {
  appendModuleSetsSection(moduleSetResults)
  appendDependenciesSection(dependencyResult, "Module Dependencies")
  appendDependenciesSection(contentModuleResult, "Content Module Dependencies")
  appendPluginDependenciesSection(pluginDependencyResult)
  appendProductsSection(productResult)
  appendSuppressionConfigSection(suppressionConfigStats)
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

private fun StringBuilder.appendDependenciesSection(result: DependencyGenerationResult?, title: String = "Module Dependencies") {
  if (result == null || result.files.isEmpty()) return

  if (!result.files.hasChanges()) {
    appendLine("$title (${AnsiColors.GRAY}${result.files.size} unchanged${AnsiColors.RESET}, ${result.totalDependencies} deps)")
    appendLine()
    return
  }

  val counts = changeCounts(result.files.createdCount, result.files.modifiedCount, result.files.unchangedCount)
  appendLine("${AnsiColors.BOLD}$title${AnsiColors.RESET} ($counts)")

  for (file in result.files.filter { it.status != FileChangeStatus.UNCHANGED }) {
    appendLine("  ${statusIcon(file.status)} ${file.contentModuleName} (${file.writtenDependencies.size} deps)")
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
    appendLine("  ${statusIcon(file.status)} ${file.pluginContentModuleName} (${file.dependencyCount} deps)")
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

private fun StringBuilder.appendSuppressionConfigSection(stats: SuppressionConfigStats?) {
  if (stats == null) return

  val modifiedIcon = if (stats.configModified) "${AnsiColors.BLUE}~${AnsiColors.RESET}" else "${AnsiColors.GRAY}\u2022${AnsiColors.RESET}"
  val stalePart = if (stats.staleCount > 0) {
    " (${AnsiColors.YELLOW}${stats.staleCount} stale removed${AnsiColors.RESET})"
  }
  else {
    ""
  }

  appendLine("${AnsiColors.BOLD}Suppression Config${AnsiColors.RESET}")
  appendLine("  $modifiedIcon ${stats.moduleCount} modules, ${stats.suppressionCount} suppressions$stalePart")
  appendLine()
}
