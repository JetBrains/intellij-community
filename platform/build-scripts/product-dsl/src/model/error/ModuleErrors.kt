// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.model.error

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import org.jetbrains.intellij.build.productLayout.model.ModuleSourceInfo
import org.jetbrains.intellij.build.productLayout.model.getModuleSourceInfo
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

/**
 * Reported when an embedded module with `includeDependencies=true` transitively pulls
 * a content module that is not explicitly declared by the product (or any of its module
 * sets / bundled plugins).
 *
 * Plugin-model content modules must be the only truth for product packaging. Silently
 * bundling a content module through JPS runtime closure makes the jar layout differ
 * across products and hides real dependencies. Ancient JPS-only (non-content) modules
 * are allowed to flow; content modules must be listed explicitly.
 */
data class ImplicitEmbeddedContentModuleError(
  override val context: String,
  /** Map from transitively pulled content module → set of embedded-with-includeDependencies=true roots whose chain reached it */
  @JvmField val missingModules: Map<ContentModuleName, Set<ContentModuleName>>,
  /** Dep chain (target names) for each missing module, from the embedded root to the violating module */
  @JvmField val chains: Map<ContentModuleName, List<String>> = emptyMap(),
  override val ruleName: String = "ImplicitEmbeddedContentModuleValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.IMPLICIT_EMBEDDED_CONTENT_MODULE

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Product '${context}' implicitly bundles content modules via embedded-module JPS runtime-dep closure${s.reset}")
    appendLine()
    appendLine("${s.yellow}Packaging packs transitive JPS runtime deps of each embedded module declared with includeDependencies=true into that embedded module's jar.${s.reset}")
    appendLine("${s.yellow}For content modules (modules with a descriptor) reached via this closure, silent bundling is forbidden — the plugin model must be the only truth for packaging.${s.reset}")
    appendLine()

    for ((missingDep, rootModules) in missingModules.entries.sortedByDescending { it.value.size }) {
      appendLine("  ${s.red}*${s.reset} Implicitly bundled: ${s.bold}${missingDep.value}${s.reset}")
      appendLine("    Reached from embedded module(s) with includeDependencies=true:")
      for (root in rootModules.sortedBy { it.value }) {
        appendLine("      - ${root.value}")
      }
      val chain = chains.get(missingDep)
      if (!chain.isNullOrEmpty()) {
        appendLine("    Chain: ${chain.joinToString(separator = " -> ")}")
      }
    }

    appendLine()
    appendLine("${s.yellow}Fix:${s.reset}")
    appendLine("1. Add each implicitly bundled content module explicitly to the product's content spec (module set or additionalModules).")
    appendLine("2. Or, if the module truly should not ship with the product, break the JPS dependency chain that pulls it in.")
    appendLine("3. As a temporary allowlist, add the module name to the product's allowedMissingDependencies.")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

data class EmbeddedContentModuleSource(
  @JvmField val kind: String,
  @JvmField val name: String,
  @JvmField val loading: String?,
)

data class EmbeddedContentModuleDependencyIssue(
  @JvmField val sourceModule: String,
  @JvmField val dependency: String,
  @JvmField val dependencyPath: List<String>,
  @JvmField val dependencySources: List<EmbeddedContentModuleSource>,
)

/**
 * Reported when a product/module-set embedded content module depends on content
 * supplied by a bundled plugin wrapper. Such a dependency crosses from the main
 * classloader into a separately loaded plugin classloader and can create runtime
 * loader cycles.
 */
data class EmbeddedContentModuleDependencyError(
  override val context: String,
  @JvmField val violations: List<EmbeddedContentModuleDependencyIssue>,
  override val ruleName: String = "EmbeddedContentModuleDependencyValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.EMBEDDED_CONTENT_MODULE_DEPENDENCY

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Product '${context}' has embedded content modules depending on bundled plugin content${s.reset}")
    appendLine()
    appendLine("${s.yellow}Embedded product/module-set modules are loaded by the main classloader.${s.reset}")
    appendLine("${s.yellow}They cannot depend on content that is supplied only by bundled plugin wrappers.${s.reset}")
    appendLine()

    for (violation in violations.sortedWith(compareBy({ it.sourceModule }, { it.dependency }))) {
      appendLine("  ${s.red}*${s.reset} ${s.bold}${violation.sourceModule}${s.reset} depends on ${s.bold}${violation.dependency}${s.reset}")
      if (violation.dependencyPath.size > 2) {
        appendLine("    Path: ${violation.dependencyPath.joinToString(separator = " -> ")}")
      }
      if (violation.dependencySources.isNotEmpty()) {
        appendLine("    Dependency source(s):")
        for (source in violation.dependencySources.sortedWith(compareBy({ it.kind }, { it.name }))) {
          val loading = source.loading?.lowercase() ?: "unspecified"
          appendLine("      - ${source.kind} ${source.name} ($loading)")
        }
      }
    }

    appendLine()
    appendLine("${s.yellow}Fix:${s.reset}")
    appendLine("1. Make the dependency embedded in the same product/module-set content, or")
    appendLine("2. Make the depending module non-embedded, or")
    appendLine("3. Move the dependency boundary so embedded code no longer references separately loaded content.")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}
