// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.analysis

import org.jetbrains.intellij.build.productLayout.stats.AnsiColors

// region ANSI Formatting (CLI Output)

fun formatValidationErrors(errors: List<ValidationError>): String {
  if (errors.isEmpty()) {
    return ""
  }

  return buildString {
    for (error in errors) {
      when (error) {
        is SelfContainedValidationError -> formatSelfContainedError(this, error)
        is MissingModuleSetsError -> formatMissingModuleSetsError(this, error)
        is DuplicateModulesError -> formatDuplicateModulesError(this, error)
        is MissingDependenciesError -> formatMissingDependenciesError(this, error)
      }
    }
  }
}

private fun formatSelfContainedError(sb: StringBuilder, error: SelfContainedValidationError) {
  sb.appendLine("${AnsiColors.RED}${AnsiColors.BOLD}Module set '${error.context}' is marked selfContained but has unresolvable dependencies${AnsiColors.RESET}")
  sb.appendLine()

  for ((dep, needingModules) in error.missingDependencies.entries.sortedByDescending { it.value.size }) {
    sb.appendLine("  ${AnsiColors.RED}*${AnsiColors.RESET} Missing: ${AnsiColors.BOLD}'$dep'${AnsiColors.RESET}")
    sb.appendLine("    Needed by: ${needingModules.sorted().joinToString(", ")}")
  }

  sb.appendLine()
  sb.appendLine("${AnsiColors.YELLOW}To fix:${AnsiColors.RESET}")
  sb.appendLine("1. Add the missing modules/sets to '${error.context}' to make it truly self-contained")
  sb.appendLine("2. Or remove selfContained=true if this set is designed to compose with other sets")
  sb.appendLine()
}

private fun formatMissingModuleSetsError(sb: StringBuilder, error: MissingModuleSetsError) {
  sb.appendLine("${AnsiColors.RED}${AnsiColors.BOLD}Product '${error.context}' references non-existent module sets${AnsiColors.RESET}")
  sb.appendLine()
  for (setName in error.missingModuleSets.sorted()) {
    sb.appendLine("  ${AnsiColors.RED}*${AnsiColors.RESET} Module set '${AnsiColors.BOLD}$setName${AnsiColors.RESET}' does not exist")
  }
  sb.appendLine()
  sb.appendLine("${AnsiColors.BLUE}Fix: Remove the reference or define the module set${AnsiColors.RESET}")
  sb.appendLine()
}

private fun formatDuplicateModulesError(sb: StringBuilder, error: DuplicateModulesError) {
  sb.appendLine("${AnsiColors.RED}${AnsiColors.BOLD}Product '${error.context}' has duplicate content modules${AnsiColors.RESET}")
  sb.appendLine()
  sb.appendLine("${AnsiColors.YELLOW}Duplicated modules (appearing ${AnsiColors.BOLD}${error.duplicates.values.max()}${AnsiColors.RESET}${AnsiColors.YELLOW} times):${AnsiColors.RESET}")
  for ((moduleName, count) in error.duplicates.entries.sortedBy { it.key }) {
    sb.appendLine("  ${AnsiColors.RED}*${AnsiColors.RESET} ${AnsiColors.BOLD}$moduleName${AnsiColors.RESET} (appears $count times)")
  }
  sb.appendLine()
  sb.appendLine("${AnsiColors.BLUE}This causes runtime error: \"Plugin has duplicated content modules declarations\"${AnsiColors.RESET}")
  sb.appendLine("${AnsiColors.BLUE}Fix: Remove duplicate moduleSet() nesting or redundant module() calls${AnsiColors.RESET}")
  sb.appendLine()
}

private fun formatMissingDependenciesError(sb: StringBuilder, error: MissingDependenciesError) {
  sb.appendLine("${AnsiColors.BOLD}Product: ${error.context}${AnsiColors.RESET}")
  sb.appendLine()

  // error.missingModules is already Map<missingDep, Set<needingModules>>
  for ((missingDep, needingModules) in error.missingModules.entries.sortedByDescending { it.value.size }) {
    // Show missing dep info with full traceability
    val missingDepInfo = error.moduleTraceInfo[missingDep]
    if (missingDepInfo != null) {
      sb.appendLine("  ${AnsiColors.RED}*${AnsiColors.RESET} Missing: ${AnsiColors.BOLD}'$missingDep'${AnsiColors.RESET}")
      sb.appendLine("    From plugin: ${missingDepInfo.sourcePlugin}")
      if (missingDepInfo.additionalPluginSource != null) {
        sb.appendLine("    Source: ${missingDepInfo.additionalPluginSource}")
      }
      else if (missingDepInfo.bundledInProducts.isNotEmpty()) {
        sb.appendLine("    Bundled in: ${missingDepInfo.bundledInProducts.sorted().joinToString(", ")}")
      }
    }
    else {
      sb.appendLine("  ${AnsiColors.RED}*${AnsiColors.RESET} Missing: ${AnsiColors.BOLD}'$missingDep'${AnsiColors.RESET} (not in any known plugin)")
    }

    // Show needing modules grouped by plugin as tree
    val byPlugin = needingModules.groupBy { error.moduleTraceInfo[it]?.sourcePlugin }
    sb.appendLine("    Needed by:")
    val pluginEntries = byPlugin.entries.sortedBy { it.key ?: "" }
    for ((pluginIdx, pluginEntry) in pluginEntries.withIndex()) {
      val (pluginName, modules) = pluginEntry
      val isLastPlugin = pluginIdx == pluginEntries.lastIndex
      val pluginPrefix = if (isLastPlugin) "└─" else "├─"
      val traceInfo = pluginName?.let { error.moduleTraceInfo[modules.first()] }

      // Plugin line with source info
      val sourceDesc = when {
        traceInfo?.bundledInProducts?.isNotEmpty() == true ->
          "bundled in: ${traceInfo.bundledInProducts.sorted().joinToString(", ")}"
        traceInfo?.additionalPluginSource != null -> traceInfo.additionalPluginSource
        else -> null
      }
      val pluginDesc = pluginName ?: "unknown"
      sb.appendLine("      $pluginPrefix ${AnsiColors.BOLD}$pluginDesc${AnsiColors.RESET}${sourceDesc?.let { " ($it)" } ?: ""}")

      // Module lines under plugin
      val sortedModules = modules.sorted()
      val childPrefix = if (isLastPlugin) "   " else "│  "
      for ((modIdx, mod) in sortedModules.withIndex()) {
        val modPrefix = if (modIdx == sortedModules.lastIndex) "└─" else "├─"
        sb.appendLine("      $childPrefix $modPrefix $mod")
      }
    }

    // Actionable suggestion based on missing dep status
    if (missingDepInfo != null && missingDepInfo.additionalPluginSource == null && missingDepInfo.bundledInProducts.isEmpty()) {
      // Plugin exists but not bundled anywhere - suggest adding to additionalPlugins
      sb.appendLine("    ${AnsiColors.BLUE}Fix:${AnsiColors.RESET} Add '${missingDepInfo.sourcePlugin}' to additionalPlugins in ultimateGenerator.kt")
    }
    else if (missingDepInfo == null) {
      // Not in any known plugin - check module sets
      val containingSets = error.allModuleSets
        .filter { ModuleSetTraversal.containsModule(it, missingDep) }
        .map { it.name }

      if (containingSets.isNotEmpty()) {
        sb.appendLine("    ${AnsiColors.BLUE}Fix:${AnsiColors.RESET} Add module set: ${containingSets.joinToString(" or ")}")
      }
      else {
        sb.appendLine("    ${AnsiColors.BLUE}Fix:${AnsiColors.RESET} Find plugin containing '$missingDep' and add to additionalPlugins")
      }
    }
    sb.appendLine()
  }
}

// endregion

// region Plain-text Formatting (Test Output)

/**
 * Converts validation errors to structured errors with plain-text messages (no ANSI, no ASCII art).
 * Each [MissingDependenciesError] is split into one [StructuredValidationError] per missing dependency.
 * Other error types produce one [StructuredValidationError] each.
 */
fun toStructuredErrors(errors: List<ValidationError>): List<StructuredValidationError> {
  val result = mutableListOf<StructuredValidationError>()
  for (error in errors) {
    when (error) {
      is SelfContainedValidationError -> {
        // One error per missing dependency in self-contained set
        for ((dep, needingModules) in error.missingDependencies) {
          result.add(StructuredValidationError(
            id = "self-contained:${error.context}:$dep",
            message = formatSelfContainedErrorPlain(error.context, dep, needingModules),
          ))
        }
      }
      is MissingModuleSetsError -> {
        // One error per missing module set
        for (setName in error.missingModuleSets) {
          result.add(StructuredValidationError(
            id = "missing-module-set:${error.context}:$setName",
            message = "Product '${error.context}' references non-existent module set '$setName'.\nFix: Remove the reference or define the module set.",
          ))
        }
      }
      is DuplicateModulesError -> {
        // One error per duplicate module
        for ((moduleName, count) in error.duplicates) {
          result.add(StructuredValidationError(
            id = "duplicate-module:${error.context}:$moduleName",
            message = "Product '${error.context}' has duplicate content module '$moduleName' (appears $count times).\n" +
                      "This causes runtime error: \"Plugin has duplicated content modules declarations\".\n" +
                      "Fix: Remove duplicate moduleSet() nesting or redundant module() calls.",
          ))
        }
      }
      is MissingDependenciesError -> {
        // One error per missing dependency
        for ((missingDep, needingModules) in error.missingModules) {
          result.add(StructuredValidationError(
            id = "missing-dep:${error.context}:$missingDep",
            message = formatMissingDependencyPlain(error, missingDep, needingModules),
          ))
        }
      }
    }
  }
  return result
}

private fun formatSelfContainedErrorPlain(context: String, dep: String, needingModules: Set<String>): String {
  return buildString {
    appendLine("Module set '$context' is marked selfContained but has unresolvable dependency '$dep'.")
    appendLine("Needed by: ${needingModules.sorted().joinToString(", ")}")
    appendLine()
    appendLine("To fix:")
    appendLine("1. Add the missing module/set to '$context' to make it truly self-contained")
    appendLine("2. Or remove selfContained=true if this set is designed to compose with other sets")
  }
}

private fun formatMissingDependencyPlain(error: MissingDependenciesError, missingDep: String, needingModules: Set<String>): String {
  return buildString {
    // Header with missing dependency info
    val missingDepInfo = error.moduleTraceInfo[missingDep]
    if (missingDepInfo != null) {
      appendLine("Missing dependency: '$missingDep'")
      appendLine("From plugin: ${missingDepInfo.sourcePlugin}")
      if (missingDepInfo.additionalPluginSource != null) {
        appendLine("Source: ${missingDepInfo.additionalPluginSource}")
      }
      else if (missingDepInfo.bundledInProducts.isNotEmpty()) {
        appendLine("Bundled in: ${missingDepInfo.bundledInProducts.sorted().joinToString(", ")}")
      }
    }
    else {
      appendLine("Missing dependency: '$missingDep' (not in any known plugin)")
    }

    appendLine("Context: ${error.context}")
    appendLine()

    // Show needing modules grouped by plugin
    appendLine("Needed by:")
    val byPlugin = needingModules.groupBy { error.moduleTraceInfo[it]?.sourcePlugin }
    for ((pluginName, modules) in byPlugin.entries.sortedBy { it.key ?: "" }) {
      val traceInfo = pluginName?.let { error.moduleTraceInfo[modules.first()] }
      val sourceDesc = when {
        traceInfo?.bundledInProducts?.isNotEmpty() == true ->
          "bundled in: ${traceInfo.bundledInProducts.sorted().joinToString(", ")}"
        traceInfo?.additionalPluginSource != null -> traceInfo.additionalPluginSource
        else -> null
      }
      val pluginDesc = pluginName ?: "unknown"
      val pluginLine = if (sourceDesc != null) "$pluginDesc ($sourceDesc)" else pluginDesc
      appendLine("  - $pluginLine: ${modules.sorted().joinToString(", ")}")
    }

    appendLine()

    // Actionable suggestion
    if (missingDepInfo != null && missingDepInfo.additionalPluginSource == null && missingDepInfo.bundledInProducts.isEmpty()) {
      appendLine("Fix: Add '${missingDepInfo.sourcePlugin}' to additionalPlugins in ultimateGenerator.kt")
    }
    else if (missingDepInfo == null) {
      val containingSets = error.allModuleSets
        .filter { ModuleSetTraversal.containsModule(it, missingDep) }
        .map { it.name }

      if (containingSets.isNotEmpty()) {
        appendLine("Fix: Add module set: ${containingSets.joinToString(" or ")}")
      }
      else {
        appendLine("Fix: Find plugin containing '$missingDep' and add to additionalPlugins")
      }
    }
  }
}

// endregion
