// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.model.error

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import org.jetbrains.intellij.build.productLayout.model.ModuleSourceInfo
import org.jetbrains.intellij.build.productLayout.model.getModuleSourceInfo
import org.jetbrains.intellij.build.productLayout.moduleSetPluginModuleName
import org.jetbrains.intellij.build.productLayout.stats.AnsiStyle

data class SelfContainedValidationError(
  override val context: String,
  @JvmField val missingDependencies: Map<ContentModuleName, Set<ContentModuleName>>,
  override val ruleName: String = "SelfContainedModuleSetValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.SELF_CONTAINED_VIOLATION

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Module set '${context}' is marked selfContained but has unresolvable dependencies${s.reset}")
    appendLine()

    for ((dep, needingModules) in missingDependencies.entries.sortedByDescending { it.value.size }) {
      appendLine("  ${s.red}*${s.reset} Missing: ${s.bold}${dep.value}${s.reset}")
      appendLine("    Needed by: ${needingModules.map { it.value }.sorted().joinToString(", ")}")
    }

    appendLine()
    appendLine("${s.yellow}To fix:${s.reset}")
    appendLine("1. Add the missing modules/sets to '${context}' to make it truly self-contained")
    appendLine("2. Or remove selfContained=true if this set is designed to compose with other sets")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

data class ModuleSetPluginizationError(
  override val context: String,
  @JvmField val embeddedModules: Set<ContentModuleName> = emptySet(),
  @JvmField val nestedPluginizedSets: Set<String> = emptySet(),
  override val ruleName: String = "ModuleSetPluginizationValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.MODULE_SET_PLUGINIZATION

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Module set '$context' cannot be materialized as a plugin${s.reset}")
    if (embeddedModules.isNotEmpty()) {
      appendLine()
      appendLine("  ${s.red}*${s.reset} Contains embedded modules in transitive closure:")
      for (module in embeddedModules.sortedBy { it.value }) {
        appendLine("    - ${module.value}")
      }
    }
    if (nestedPluginizedSets.isNotEmpty()) {
      appendLine()
      appendLine("  ${s.red}*${s.reset} Contains nested pluginized module sets:")
      for (setName in nestedPluginizedSets.sorted()) {
        appendLine("    - $setName")
      }
    }
    appendLine()
    appendLine("${s.yellow}Fix:${s.reset} keep pluginized module sets free of embedded modules and nested pluginized sets")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

data class PluginizedModuleSetReferenceError(
  override val context: String,
  @JvmField val pluginizedModuleSetName: String,
  @JvmField val ownerKind: OwnerKind,
  override val ruleName: String = "PluginizedModuleSetReferenceValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.MODULE_SET_PLUGINIZATION

  enum class OwnerKind {
    PRODUCT,
    MODULE_SET,
  }

  override fun format(s: AnsiStyle): String = buildString {
    when (ownerKind) {
      OwnerKind.PRODUCT -> appendLine(
        "${s.red}${s.bold}Product '$context' references pluginized module set '$pluginizedModuleSetName' as a regular module set${s.reset}"
      )
      OwnerKind.MODULE_SET -> appendLine(
        "${s.red}${s.bold}Module set '$context' nests pluginized module set '$pluginizedModuleSetName' as a regular module set${s.reset}"
      )
    }
    appendLine()
    appendLine(
      "  ${s.red}*${s.reset} Pluginized module sets are standalone bundled plugin wrappers and are not inlined through moduleSet(...) references"
    )
    appendLine()
    appendLine("${s.yellow}Fix:${s.reset}")
    appendLine("1. Remove the moduleSet(...) reference to '$pluginizedModuleSetName'")
    appendLine("2. Bundle '${moduleSetPluginModuleName(pluginizedModuleSetName).value}' in products that should ship it")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

data class DuplicateModuleSetPluginWrapperError(
  override val context: String,
  override val ruleName: String = "ModuleSetPluginizationValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.MODULE_SET_PLUGINIZATION

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Module-set plugin wrapper '$context' is defined in multiple registries${s.reset}")
    appendLine()
    appendLine("  ${s.red}*${s.reset} Community and ultimate module sets resolve to the same wrapper module name")
    appendLine()
    appendLine("${s.yellow}Fix:${s.reset} keep pluginized module set names unique across community and ultimate registries")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

data class UltimateModuleSetMainModuleError(
  override val context: String,
  override val ruleName: String = "ModuleSetPluginizationValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.MODULE_SET_PLUGINIZATION

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Ultimate module set '$context' cannot be added to intellij.moduleSet.plugin.main${s.reset}")
    appendLine()
    appendLine("  ${s.red}*${s.reset} addToMainModule=true is only supported for community wrappers while intellij.moduleSet.plugin.main remains community-only")
    appendLine()
    appendLine("${s.yellow}Fix:${s.reset} set addToMainModule=false for ultimate pluginized module sets")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

data class MissingModuleSetsError(
  override val context: String,
  @JvmField val missingModuleSets: Set<String>,
  override val ruleName: String = "ProductModuleSetValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.MISSING_MODULE_SETS

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Product '${context}' references non-existent module sets${s.reset}")
    appendLine()
    for (setName in missingModuleSets.sorted()) {
      appendLine("  ${s.red}*${s.reset} Module set '${s.bold}$setName${s.reset}' does not exist")
    }
    appendLine()
    appendLine("${s.blue}Fix: Remove the reference or define the module set${s.reset}")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

data class DuplicateModulesError(
  override val context: String,
  @JvmField val duplicates: Map<ContentModuleName, Int>,
  override val ruleName: String = "ProductModuleSetValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.DUPLICATE_MODULES

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Product '${context}' has duplicate content modules${s.reset}")
    appendLine()
    appendLine("${s.yellow}Duplicated modules (appearing ${s.bold}${duplicates.values.max()}${s.reset}${s.yellow} times):${s.reset}")
    for ((moduleName, count) in duplicates.entries.sortedBy { it.key.value }) {
      appendLine("  ${s.red}*${s.reset} ${s.bold}${moduleName.value}${s.reset} (appears $count times)")
    }
    appendLine()
    appendLine("${s.blue}This causes runtime error: \"Plugin has duplicated content modules declarations\"${s.reset}")
    appendLine("${s.blue}Fix: Remove duplicate moduleSet() nesting or redundant module() calls${s.reset}")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

internal data class MissingDependenciesError(
  override val context: String,
  @JvmField val missingModules: Map<ContentModuleName, Set<ContentModuleName>>,
  /** Graph for on-demand module source info queries (preferred over moduleSourceInfo map) */
  @JvmField val pluginGraph: PluginGraph? = null,
  /** Unified source info for all modules (needing and missing) - deprecated, use pluginModelGraph instead */
  @JvmField val moduleSourceInfo: Map<ContentModuleName, ModuleSourceInfo> = emptyMap(),
  override val ruleName: String = "ProductModuleSetValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.MISSING_DEPENDENCY

  /** Get module source info - from graph on-demand if available, otherwise from pre-built map */
  private fun getSourceInfo(contentModuleName: ContentModuleName): ModuleSourceInfo? {
    return pluginGraph?.let { getModuleSourceInfo(it, contentModuleName) } ?: moduleSourceInfo.get(contentModuleName)
  }

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.bold}Product: ${context}${s.reset}")
    appendLine()

    for ((missingDep, needingModules) in missingModules.entries.sortedByDescending { it.value.size }) {
      val missingDepInfo = getSourceInfo(missingDep)
      appendLine("  ${s.red}*${s.reset} Missing: ${s.bold}'${missingDep.value}'${s.reset}${formatMissingDepSource(missingDepInfo)}")

      appendLine("    Needed by:")
      val sortedModules = needingModules.sortedBy { it.value }
      for ((modIdx, mod) in sortedModules.withIndex()) {
        val isLast = modIdx == sortedModules.lastIndex
        val modPrefix = if (isLast) "└─" else "├─"
        val info = getSourceInfo(mod)
        val moduleType = if (info?.sourcePlugin != null) "content module" else "module"
        appendLine("      $modPrefix ${s.bold}${mod.value}${s.reset} ($moduleType)")

        val childPrefix = if (isLast) "   " else "│  "
        when {
          info?.sourcePlugin != null -> {
            appendLine("      $childPrefix └─ in plugin: ${info.sourcePlugin.value}")
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

      formatFixSuggestion(this, missingDep.value, missingDepInfo, s)
      appendLine()
    }
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }

  private fun formatMissingDepSource(info: ModuleSourceInfo?): String {
    return when {
      info == null -> " (not in any known plugin or module set)"
      info.sourcePlugin != null && info.isTestPlugin -> " (only available in test plugin: ${info.sourcePlugin.value})"
      info.sourcePlugin != null -> ""
      info.sourceModuleSet != null -> " (from module set: ${info.sourceModuleSet})"
      else -> " (not in any known plugin or module set)"
    }
  }

  private fun formatFixSuggestion(sb: StringBuilder, missingDep: String, info: ModuleSourceInfo?, s: AnsiStyle) {
    when {
      info?.sourcePlugin != null && info.bundledInProducts.isEmpty() && info.compatibleWithProducts.isEmpty() -> {
        sb.appendLine("    ${s.blue}Fix:${s.reset} Add '${info.sourcePlugin.value}' to knownPlugins in ultimateGenerator.kt")
      }
      info == null || (info.sourcePlugin == null && info.sourceModuleSet == null) -> {
        // Use pre-computed containingModuleSets from ModuleSourceInfo (graph-derived)
        val containingSets = info?.containingModuleSets ?: emptySet()

        if (containingSets.isNotEmpty()) {
          sb.appendLine("    ${s.blue}Fix:${s.reset} Add module set: ${containingSets.sorted().joinToString(" or ")}")
        }
        else {
          sb.appendLine("    ${s.blue}Fix:${s.reset} Add '$missingDep' to a module set or include as plugin content")
        }
      }
    }
  }
}

enum class ContentModuleBackingIssueKind {
  NO_BACKING_TARGET,
  MULTIPLE_BACKING_TARGETS,
  MISMATCHED_BACKING_TARGET,
  MISSING_JPS_MODULE,
}

data class ContentModuleBackingIssue(
  @JvmField val reason: ContentModuleBackingIssueKind,
  @JvmField val backingTargets: Set<String> = emptySet(),
  @JvmField val expectedTarget: String? = null,
  @JvmField val sources: Set<String> = emptySet(),
)

data class MissingContentModuleBackingError(
  override val context: String,
  @JvmField val missingModules: Map<ContentModuleName, ContentModuleBackingIssue>,
  override val ruleName: String = "ContentModuleBackingValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.CONTENT_MODULE_BACKING_MISSING

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Content modules without backing JPS modules${s.reset}")
    appendLine()

    for ((moduleName, issue) in missingModules.entries.sortedBy { it.key.value }) {
      appendLine("  ${s.red}*${s.reset} ${s.bold}${moduleName.value}${s.reset} - ${formatReason(issue)}")
      if (issue.sources.isNotEmpty()) {
        appendLine("    Declared in: ${issue.sources.sorted().joinToString(", ")}")
      }
      if (issue.expectedTarget != null) {
        appendLine("    Expected target: ${issue.expectedTarget}")
      }
      if (issue.backingTargets.isNotEmpty()) {
        appendLine("    Backing target(s): ${issue.backingTargets.sorted().joinToString(", ")}")
      }
    }

    appendLine()
    appendLine("${s.yellow}Fix:${s.reset} Ensure the module name is correct and the backing JPS module exists.")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

private fun formatReason(issue: ContentModuleBackingIssue): String {
  return when (issue.reason) {
    ContentModuleBackingIssueKind.NO_BACKING_TARGET -> "missing backedBy edge"
    ContentModuleBackingIssueKind.MULTIPLE_BACKING_TARGETS -> "multiple backing targets"
    ContentModuleBackingIssueKind.MISMATCHED_BACKING_TARGET -> "backing target does not match base module"
    ContentModuleBackingIssueKind.MISSING_JPS_MODULE -> "backing target not found in JPS model"
  }
}
