// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validation

import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.stats.AnsiStyle
import org.jetbrains.intellij.build.productLayout.traversal.ModuleSetTraversal

/** Returns a short identifier for test naming */
fun getErrorId(error: ValidationError): String = when (error) {
  is FileDiff -> "diff:${error.path.fileName}"
  is XIncludeResolutionError -> "xinclude:${error.pluginName}:${error.xIncludePath}"
  is MissingDependenciesError -> "missing-deps:${error.context}"
  is MissingModuleSetsError -> "missing-sets:${error.context}"
  is DuplicateModulesError -> "duplicates:${error.context}"
  is SelfContainedValidationError -> "self-contained:${error.context}"
  is PluginDependencyError -> "plugin-dep:${error.pluginName}"
}

internal fun formatValidationErrors(errors: List<ValidationError>, useAnsi: Boolean = true): String {
  if (errors.isEmpty()) return ""
  val s = AnsiStyle(useAnsi)
  return buildString {
    for (error in errors) {
      append(formatValidationError(error, s))
    }
  }
}

fun formatValidationError(error: ValidationError, useAnsi: Boolean = true): String = formatValidationError(error, AnsiStyle(useAnsi))

private fun formatValidationError(error: ValidationError, s: AnsiStyle): String = when (error) {
  is FileDiff -> error.context  // FileDiff uses context directly as the message
  is XIncludeResolutionError -> formatXIncludeResolutionError(error, s)
  is MissingDependenciesError -> formatMissingDependenciesError(error, s)
  is MissingModuleSetsError -> formatMissingModuleSetsError(error, s)
  is DuplicateModulesError -> formatDuplicateModulesError(error, s)
  is SelfContainedValidationError -> formatSelfContainedError(error, s)
  is PluginDependencyError -> formatPluginDependencyError(error, s)
}

private fun formatSelfContainedError(error: SelfContainedValidationError, s: AnsiStyle): String = buildString {
  appendLine("${s.red}${s.bold}Module set '${error.context}' is marked selfContained but has unresolvable dependencies${s.reset}")
  appendLine()

  for ((dep, needingModules) in error.missingDependencies.entries.sortedByDescending { it.value.size }) {
    appendLine("  ${s.red}*${s.reset} Missing: ${s.bold}$dep${s.reset}")
    appendLine("    Needed by: ${needingModules.sorted().joinToString(", ")}")
  }

  appendLine()
  appendLine("${s.yellow}To fix:${s.reset}")
  appendLine("1. Add the missing modules/sets to '${error.context}' to make it truly self-contained")
  appendLine("2. Or remove selfContained=true if this set is designed to compose with other sets")
  appendLine()
}

private fun formatMissingModuleSetsError(error: MissingModuleSetsError, s: AnsiStyle): String = buildString {
  appendLine("${s.red}${s.bold}Product '${error.context}' references non-existent module sets${s.reset}")
  appendLine()
  for (setName in error.missingModuleSets.sorted()) {
    appendLine("  ${s.red}*${s.reset} Module set '${s.bold}$setName${s.reset}' does not exist")
  }
  appendLine()
  appendLine("${s.blue}Fix: Remove the reference or define the module set${s.reset}")
  appendLine()
}

private fun formatDuplicateModulesError(error: DuplicateModulesError, s: AnsiStyle): String = buildString {
  appendLine("${s.red}${s.bold}Product '${error.context}' has duplicate content modules${s.reset}")
  appendLine()
  appendLine("${s.yellow}Duplicated modules (appearing ${s.bold}${error.duplicates.values.max()}${s.reset}${s.yellow} times):${s.reset}")
  for ((moduleName, count) in error.duplicates.entries.sortedBy { it.key }) {
    appendLine("  ${s.red}*${s.reset} ${s.bold}$moduleName${s.reset} (appears $count times)")
  }
  appendLine()
  appendLine("${s.blue}This causes runtime error: \"Plugin has duplicated content modules declarations\"${s.reset}")
  appendLine("${s.blue}Fix: Remove duplicate moduleSet() nesting or redundant module() calls${s.reset}")
  appendLine()
}

private fun formatXIncludeResolutionError(error: XIncludeResolutionError, s: AnsiStyle): String = buildString {
  appendLine("${s.red}${s.bold}Failed to resolve xi:include in plugin ${error.pluginName}${s.reset}")
  appendLine()
  appendLine("  ${s.red}*${s.reset} Path: ${s.bold}${error.xIncludePath}${s.reset}")
  appendLine("  ${s.gray}Debug: ${error.debugInfo}${s.reset}")
  appendLine()
  appendLine("${s.blue}Fix: If this file comes from an external library, add the path to 'skipXIncludePaths' in ModuleSetGenerationConfig${s.reset}")
  appendLine()
}

private fun formatMissingDependenciesError(error: MissingDependenciesError, s: AnsiStyle): String = buildString {
  appendLine("${s.bold}Product: ${error.context}${s.reset}")
  appendLine()

  for ((missingDep, needingModules) in error.missingModules.entries.sortedByDescending { it.value.size }) {
    val missingDepInfo = error.moduleSourceInfo[missingDep]
    appendLine("  ${s.red}*${s.reset} Missing: ${s.bold}'$missingDep'${s.reset}${formatMissingDepSource(missingDepInfo)}")

    appendLine("    Needed by:")
    val sortedModules = needingModules.sorted()
    for ((modIdx, mod) in sortedModules.withIndex()) {
      val isLast = modIdx == sortedModules.lastIndex
      val modPrefix = if (isLast) "└─" else "├─"
      val info = error.moduleSourceInfo[mod]
      val moduleType = if (info?.sourcePlugin != null) "content module" else "module"
      appendLine("      $modPrefix ${s.bold}$mod${s.reset} ($moduleType)")

      val childPrefix = if (isLast) "   " else "│  "
      when {
        info?.sourcePlugin != null -> {
          appendLine("      $childPrefix └─ in plugin: ${info.sourcePlugin}")
          if (info.bundledInProducts.isNotEmpty()) {
            appendLine("      $childPrefix     └─ bundled in: ${info.bundledInProducts.sorted().joinToString(", ")}")
          }
          if (info.compatibleWithProducts.isNotEmpty()) {
            appendLine("      $childPrefix     └─ compatible with: ${info.compatibleWithProducts.sorted().joinToString(", ")}")
          }
        }
        info?.sourceModuleSet != null -> {
          appendLine("      $childPrefix └─ in module set: ${info.sourceModuleSet}")
        }
      }
    }

    formatFixSuggestion(this, missingDep, missingDepInfo, error.allModuleSets, s)
    appendLine()
  }
}

private fun formatMissingDepSource(info: ModuleSourceInfo?): String {
  return when {
    info == null -> " (not in any known plugin or module set)"
    info.sourcePlugin != null -> ""
    info.sourceModuleSet != null -> " (from module set: ${info.sourceModuleSet})"
    else -> " (not in any known plugin or module set)"
  }
}

private fun formatFixSuggestion(sb: StringBuilder, missingDep: String, info: ModuleSourceInfo?, allModuleSets: List<ModuleSet>, s: AnsiStyle) {
  when {
    info?.sourcePlugin != null && info.bundledInProducts.isEmpty() && info.compatibleWithProducts.isEmpty() -> {
      sb.appendLine("    ${s.blue}Fix:${s.reset} Add '${info.sourcePlugin}' to knownPlugins in ultimateGenerator.kt")
    }
    info == null || (info.sourcePlugin == null && info.sourceModuleSet == null) -> {
      val containingSets = allModuleSets
        .filter { ModuleSetTraversal.containsModule(it, missingDep) }
        .map { it.name }

      if (containingSets.isNotEmpty()) {
        sb.appendLine("    ${s.blue}Fix:${s.reset} Add module set: ${containingSets.joinToString(" or ")}")
      }
      else {
        sb.appendLine("    ${s.blue}Fix:${s.reset} Add '$missingDep' to a module set or include as plugin content")
      }
    }
  }
}

private fun formatPluginDependencyError(error: PluginDependencyError, s: AnsiStyle): String = buildString {
  appendLine("${s.red}${s.bold}Plugin '${error.pluginName}' has unresolvable module dependencies${s.reset}")
  appendLine()

  for ((missingDep, products) in error.missingDependencies.entries.sortedBy { it.key }) {
    appendLine("  ${s.red}*${s.reset} Missing: ${s.bold}'$missingDep'${s.reset}")
    appendLine("    Unresolvable in products: ${products.sorted().joinToString(", ")}")
  }

  appendLine()
  appendLine("${s.blue}Fix:${s.reset} Add the missing modules to module sets used by these products,")
  appendLine("     or add them to 'allowedMissingDependencies' in the product spec if intentional.")
  appendLine()
}
