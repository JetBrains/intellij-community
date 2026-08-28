// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DestructuringDeclaration")

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

/** Appends one `label: N files<detail>` line, and nothing at all when the section holds no file. */
private fun StringBuilder.appendCompactFileCount(label: String, fileCount: Int, detail: String = "") {
  if (fileCount == 0) {
    return
  }
  appendLine("  $label: ${AnsiColors.BOLD}$fileCount${AnsiColors.RESET} files$detail")
}

/** Prints the generation summary to the standard output. */
fun printGenerationSummary(
  stats: GenerationStats,
  errors: List<ValidationError> = emptyList(),
  committed: Boolean = false,
) {
  print(buildGenerationSummary(stats = stats, errors = errors, committed = committed))
}

/**
 * Builds a formatted summary of generation results with colors.
 * Uses compact mode when no changes, detailed mode when there are changes.
 * Shows validation errors with failure indicator when present.
 * A test reads the text directly, and it needs no `System.out` swap.
 *
 * @param committed `true` means the run wrote its changes to disk.
 */
internal fun buildGenerationSummary(
  stats: GenerationStats,
  errors: List<ValidationError> = emptyList(),
  committed: Boolean = false,
): String {
  val hasErrors = errors.isNotEmpty()
  val frameColor = if (hasErrors) AnsiColors.YELLOW else AnsiColors.CYAN

  return buildString {
    appendLine()
    appendLine("${frameColor}${AnsiColors.BOLD}$SEPARATOR${AnsiColors.RESET}")

    when {
      hasErrors -> {
        appendLine("${AnsiColors.RED}\u2717${AnsiColors.RESET} Validation failed")
        appendLine()
        errors.forEach { append(it.format(AnsiStyle(useAnsi = true))) }
      }
      stats.hasChanges -> appendDetailedSummary(stats)
      else -> appendCompactSummary(stats)
    }

    // A committing run repairs the file on disk and leaves the repair uncommitted. The reader needs that next step
    // stated, or the drift returns with the next reader.
    if (!hasErrors && committed && stats.hasChanges) {
      appendLine("${AnsiColors.YELLOW}\u270E${AnsiColors.RESET} The run wrote the files above. Commit the change.")
    }

    appendLine("${frameColor}\u23F1${AnsiColors.RESET} Completed in ${AnsiColors.BOLD}${stats.durationMs / 1000.0}s${AnsiColors.RESET}")
    appendLine("${frameColor}${AnsiColors.BOLD}$SEPARATOR${AnsiColors.RESET}")
  }
}

private fun StringBuilder.appendCompactSummary(stats: GenerationStats) {
  appendLine("${AnsiColors.GREEN}\u2713${AnsiColors.RESET} All files unchanged")

  val totalModuleSetFiles = stats.moduleSetResults.sumOf { it.files.size }
  val totalModules = stats.moduleSetResults.sumOf { it.totalModules }
  appendLine("  Module sets: ${AnsiColors.BOLD}$totalModuleSetFiles${AnsiColors.RESET} files ($totalModules modules)")

  stats.dependencyResult?.let {
    appendCompactFileCount("Module dependencies", it.files.size, " (${it.totalDependencies} deps)")
  }
  stats.contentModuleResult?.let {
    appendCompactFileCount("Content module dependencies", it.files.size, " (${it.totalDependencies} deps)")
  }
  stats.pluginDependencyResult?.let {
    val contentPart = if (it.contentModuleCount > 0) ", ${it.contentModuleCount} content modules" else ""
    appendCompactFileCount("Plugin dependencies", it.files.size, " (${it.totalDependencies} deps)$contentPart")
  }
  stats.productResult?.let { appendCompactFileCount("Products", it.products.size) }
  stats.devDistPlanResult?.let { appendCompactFileCount("Dev-dist plan", it.files.size) }

  val suppressionConfigStats = stats.suppressionConfigStats
  if (suppressionConfigStats != null) {
    val stalePart = if (suppressionConfigStats.staleCount > 0) ", ${AnsiColors.YELLOW}${suppressionConfigStats.staleCount} stale${AnsiColors.RESET}" else ""
    appendLine("  Suppression config: ${AnsiColors.BOLD}${suppressionConfigStats.moduleCount}${AnsiColors.RESET} modules (${suppressionConfigStats.suppressionCount} suppressions$stalePart)")
  }
}

private fun StringBuilder.appendDetailedSummary(stats: GenerationStats) {
  appendModuleSetsSection(stats.moduleSetResults)
  appendDependenciesSection(stats.dependencyResult, "Module Dependencies")
  appendDependenciesSection(stats.contentModuleResult, "Content Module Dependencies")
  appendPluginDependenciesSection(stats.pluginDependencyResult)
  appendProductsSection(stats.productResult)
  appendDevDistPlanSection(stats.devDistPlanResult)
  appendSuppressionConfigSection(stats.suppressionConfigStats)
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

private fun StringBuilder.appendDevDistPlanSection(result: DevDistPlanGenerationResult?) {
  if (result == null || result.files.isEmpty()) return

  if (!result.files.hasChanges()) {
    appendLine("Dev-Dist Plan (${AnsiColors.GRAY}${result.files.size} unchanged${AnsiColors.RESET})")
    appendLine()
    return
  }

  // The plan generator creates or rewrites a file, and it deletes none, so the deleted count stays out.
  val counts = changeCounts(result.files.createdCount, result.files.modifiedCount, result.files.unchangedCount)
  appendLine("${AnsiColors.BOLD}Dev-Dist Plan${AnsiColors.RESET} ($counts)")

  for (file in result.files.filter { it.status != FileChangeStatus.UNCHANGED }) {
    appendLine("  ${statusIcon(file.status)} ${file.relativePath}")
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
