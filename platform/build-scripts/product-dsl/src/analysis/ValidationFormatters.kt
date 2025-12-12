// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.analysis

import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.stats.AnsiColors

private fun ansi(code: String, useAnsi: Boolean) = if (useAnsi) code else ""

/** Returns a short identifier for test naming */
fun getErrorId(error: ValidationError): String = when (error) {
  is XIncludeResolutionError -> "xinclude:${error.pluginName}:${error.xIncludePath}"
  is MissingDependenciesError -> "missing-deps:${error.context}"
  is MissingModuleSetsError -> "missing-sets:${error.context}"
  is DuplicateModulesError -> "duplicates:${error.context}"
  is SelfContainedValidationError -> "self-contained:${error.context}"
}

fun formatValidationErrors(errors: List<ValidationError>, useAnsi: Boolean = true): String {
  if (errors.isEmpty()) return ""
  return buildString {
    for (error in errors) {
      append(formatValidationError(error, useAnsi))
    }
  }
}

fun formatValidationError(error: ValidationError, useAnsi: Boolean = true): String = when (error) {
  is XIncludeResolutionError -> formatXIncludeResolutionError(error, useAnsi)
  is MissingDependenciesError -> formatMissingDependenciesError(error, useAnsi)
  is MissingModuleSetsError -> formatMissingModuleSetsError(error, useAnsi)
  is DuplicateModulesError -> formatDuplicateModulesError(error, useAnsi)
  is SelfContainedValidationError -> formatSelfContainedError(error, useAnsi)
}

private fun formatSelfContainedError(error: SelfContainedValidationError, useAnsi: Boolean): String = buildString {
  val red = ansi(AnsiColors.RED, useAnsi)
  val bold = ansi(AnsiColors.BOLD, useAnsi)
  val yellow = ansi(AnsiColors.YELLOW, useAnsi)
  val reset = ansi(AnsiColors.RESET, useAnsi)

  appendLine("${red}${bold}Module set '${error.context}' is marked selfContained but has unresolvable dependencies$reset")
  appendLine()

  for ((dep, needingModules) in error.missingDependencies.entries.sortedByDescending { it.value.size }) {
    appendLine("  ${red}*$reset Missing: $bold'$dep'$reset")
    appendLine("    Needed by: ${needingModules.sorted().joinToString(", ")}")
  }

  appendLine()
  appendLine("${yellow}To fix:$reset")
  appendLine("1. Add the missing modules/sets to '${error.context}' to make it truly self-contained")
  appendLine("2. Or remove selfContained=true if this set is designed to compose with other sets")
  appendLine()
}

private fun formatMissingModuleSetsError(error: MissingModuleSetsError, useAnsi: Boolean): String = buildString {
  val red = ansi(AnsiColors.RED, useAnsi)
  val bold = ansi(AnsiColors.BOLD, useAnsi)
  val blue = ansi(AnsiColors.BLUE, useAnsi)
  val reset = ansi(AnsiColors.RESET, useAnsi)

  appendLine("${red}${bold}Product '${error.context}' references non-existent module sets$reset")
  appendLine()
  for (setName in error.missingModuleSets.sorted()) {
    appendLine("  ${red}*$reset Module set '$bold$setName$reset' does not exist")
  }
  appendLine()
  appendLine("${blue}Fix: Remove the reference or define the module set$reset")
  appendLine()
}

private fun formatDuplicateModulesError(error: DuplicateModulesError, useAnsi: Boolean): String = buildString {
  val red = ansi(AnsiColors.RED, useAnsi)
  val bold = ansi(AnsiColors.BOLD, useAnsi)
  val yellow = ansi(AnsiColors.YELLOW, useAnsi)
  val blue = ansi(AnsiColors.BLUE, useAnsi)
  val reset = ansi(AnsiColors.RESET, useAnsi)

  appendLine("${red}${bold}Product '${error.context}' has duplicate content modules$reset")
  appendLine()
  appendLine("${yellow}Duplicated modules (appearing $bold${error.duplicates.values.max()}$reset$yellow times):$reset")
  for ((moduleName, count) in error.duplicates.entries.sortedBy { it.key }) {
    appendLine("  ${red}*$reset $bold$moduleName$reset (appears $count times)")
  }
  appendLine()
  appendLine("${blue}This causes runtime error: \"Plugin has duplicated content modules declarations\"$reset")
  appendLine("${blue}Fix: Remove duplicate moduleSet() nesting or redundant module() calls$reset")
  appendLine()
}

private fun formatXIncludeResolutionError(error: XIncludeResolutionError, useAnsi: Boolean): String = buildString {
  val red = ansi(AnsiColors.RED, useAnsi)
  val bold = ansi(AnsiColors.BOLD, useAnsi)
  val blue = ansi(AnsiColors.BLUE, useAnsi)
  val gray = ansi(AnsiColors.GRAY, useAnsi)
  val reset = ansi(AnsiColors.RESET, useAnsi)

  appendLine("${red}${bold}Failed to resolve xi:include in plugin ${error.pluginName}$reset")
  appendLine()
  appendLine("  ${red}*$reset Path: $bold${error.xIncludePath}$reset")
  appendLine("  ${gray}Debug: ${error.debugInfo}$reset")
  appendLine()
  appendLine("${blue}Fix: If this file comes from an external library, add the path to 'skipXIncludePaths' in ModuleSetGenerationConfig$reset")
  appendLine()
}

private fun formatMissingDependenciesError(error: MissingDependenciesError, useAnsi: Boolean): String = buildString {
  val red = ansi(AnsiColors.RED, useAnsi)
  val bold = ansi(AnsiColors.BOLD, useAnsi)
  val blue = ansi(AnsiColors.BLUE, useAnsi)
  val reset = ansi(AnsiColors.RESET, useAnsi)

  appendLine("${bold}Product: ${error.context}$reset")
  appendLine()

  for ((missingDep, needingModules) in error.missingModules.entries.sortedByDescending { it.value.size }) {
    val missingDepInfo = error.moduleSourceInfo[missingDep]
    appendLine("  ${red}*$reset Missing: $bold'$missingDep'$reset${formatMissingDepSource(missingDepInfo)}")

    val bySource = needingModules.groupBy { getModuleSourceKey(error.moduleSourceInfo[it]) }
    appendLine("    Needed by:")
    val sourceEntries = bySource.entries.sortedBy { it.key }
    for ((sourceIdx, sourceEntry) in sourceEntries.withIndex()) {
      val (sourceKey, modules) = sourceEntry
      val isLastSource = sourceIdx == sourceEntries.lastIndex
      val sourcePrefix = if (isLastSource) "└─" else "├─"
      val sourceInfo = error.moduleSourceInfo[modules.first()]
      val sourceDesc = formatSourceDescription(sourceInfo)
      appendLine("      $sourcePrefix $bold$sourceKey$reset${sourceDesc?.let { " ($it)" } ?: ""}")

      val sortedModules = modules.sorted()
      val childPrefix = if (isLastSource) "   " else "│  "
      for ((modIdx, mod) in sortedModules.withIndex()) {
        val modPrefix = if (modIdx == sortedModules.lastIndex) "└─" else "├─"
        appendLine("      $childPrefix $modPrefix $mod")
      }
    }

    formatFixSuggestion(this, missingDep, missingDepInfo, error.allModuleSets, blue, reset)
    appendLine()
  }
}

private fun getModuleSourceKey(info: ModuleSourceInfo?): String {
  return info?.sourcePlugin
    ?: info?.sourceModuleSet?.let { "moduleSet:$it" }
    ?: "unknown"
}

private fun formatMissingDepSource(info: ModuleSourceInfo?): String {
  return when {
    info == null -> " (not in any known plugin or module set)"
    info.sourcePlugin != null -> ""
    info.sourceModuleSet != null -> " (from module set: ${info.sourceModuleSet})"
    else -> " (not in any known plugin or module set)"
  }
}

private fun formatSourceDescription(info: ModuleSourceInfo?): String? {
  return when {
    info == null -> null
    info.sourceProducts.isNotEmpty() -> "in products: ${info.sourceProducts.sorted().joinToString(", ")}"
    info.additionalPluginSource != null -> info.additionalPluginSource
    else -> null
  }
}

private fun formatFixSuggestion(sb: StringBuilder, missingDep: String, info: ModuleSourceInfo?, allModuleSets: List<ModuleSet>, blue: String, reset: String) {
  when {
    info?.sourcePlugin != null && info.additionalPluginSource == null && info.sourceProducts.isEmpty() -> {
      sb.appendLine("    ${blue}Fix:$reset Add '${info.sourcePlugin}' to additionalPlugins in ultimateGenerator.kt")
    }
    info == null || (info.sourcePlugin == null && info.sourceModuleSet == null) -> {
      val containingSets = allModuleSets
        .filter { ModuleSetTraversal.containsModule(it, missingDep) }
        .map { it.name }

      if (containingSets.isNotEmpty()) {
        sb.appendLine("    ${blue}Fix:$reset Add module set: ${containingSets.joinToString(" or ")}")
      }
      else {
        sb.appendLine("    ${blue}Fix:$reset Find plugin containing '$missingDep' and add to additionalPlugins")
      }
    }
  }
}
