// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "GrazieInspection")

package org.jetbrains.intellij.build.productLayout.model.error

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import org.jetbrains.intellij.build.productLayout.model.ModuleSourceInfo
import org.jetbrains.intellij.build.productLayout.stats.AnsiStyle

/**
 * Error when content module IML dependencies on main plugin modules are not declared in XML.
 *
 * This catches the case where a content module has a compile dependency on a main plugin module
 * (e.g., intellij.python.community.plugin) but the generated XML doesn't have the corresponding
 * `<plugin id="..."/>` dependency, which would cause NoClassDefFoundError at runtime.
 */
data class MissingContentModulePluginDependencyError(
  override val context: String,
  /** Content module with missing plugin dependencies */
  val contentModuleName: ContentModuleName,
  /** Plugin IDs that are depended on in IML but not declared in XML */
  @JvmField val missingPluginIds: Set<PluginId>,
  override val ruleName: String = "PluginValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.CONTENT_MODULE_PLUGIN_DEP_MISSING

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Content module '${context}' has IML dependencies on plugin modules but missing XML plugin declarations${s.reset}")
    appendLine()
    appendLine("  ${s.yellow}The module has compile dependencies on plugin main modules, but the generated XML descriptor")
    appendLine("  doesn't declare the corresponding <plugin id=\"...\"/> dependencies.${s.reset}")
    appendLine()
    appendLine("  ${s.red}Missing plugin IDs:${s.reset}")
    for (pluginId in missingPluginIds.sortedBy { it.value }) {
      appendLine("    ${s.red}*${s.reset} ${s.bold}${pluginId.value}${s.reset}")
    }
    appendLine()
    appendLine("${s.blue}Fix:${s.reset} Add to the content module descriptor XML:")
    for (pluginId in missingPluginIds.sortedBy { it.value }) {
      appendLine("       ${s.gray}<depends><plugin id=\"${pluginId.value}\"/></depends>${s.reset}")
    }
    appendLine()
    val kotlinCode = "\"${contentModuleName.value}\" to setOf(${missingPluginIds.sortedBy { it.value }.joinToString { "\"${it.value}\"" }}),"
    appendLine("${s.blue}Or suppress temporarily:${s.reset} Add to contentModuleAllowedMissingPluginDeps in ModuleSetGenerationConfig:")
    appendLine("       ${s.gray}$kotlinCode${s.reset}")
    appendLine()
    appendLine("${s.yellow}Why this matters:${s.reset} Without the plugin dependency declaration, classes from the plugin")
    appendLine("will not be available at runtime, causing NoClassDefFoundError.")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

/**
 * Error when a content module is declared in multiple bundled plugins for the same product.
 */
data class DuplicatePluginContentModulesError(
  override val context: String,
  @JvmField val duplicates: Map<ContentModuleName, List<PluginOwner>>,
  override val ruleName: String = "PluginContentDuplicatesValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.PLUGIN_CONTENT_DUPLICATE

  data class PluginOwner(val pluginName: TargetName, val isTestPlugin: Boolean)

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Product '${context}' has content modules declared by multiple bundled plugins${s.reset}")
    appendLine()
    for ((moduleName, owners) in duplicates.entries.sortedBy { it.key.value }) {
      appendLine("  ${s.red}*${s.reset} ${s.bold}${moduleName.value}${s.reset}")
      for (owner in owners) {
        val typeSuffix = if (owner.isTestPlugin) " (test)" else ""
        appendLine("      - ${owner.pluginName.value}$typeSuffix")
      }
    }
    appendLine()
    appendLine("${s.blue}This causes runtime error: \"Plugin has duplicated content modules declarations\"${s.reset}")
    appendLine("${s.blue}Fix: ensure each content module is declared by only one bundled plugin for the product.${s.reset}")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

/**
 * Error when production and test plugins declare the same descriptor ID.
 */
data class PluginDescriptorIdConflictError(
  override val context: String,
  @JvmField val duplicates: Map<PluginId, List<DescriptorOwner>>,
  override val ruleName: String = "PluginDescriptorIdConflictValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.PLUGIN_DESCRIPTOR_ID_CONFLICT

  data class DescriptorOwner(
    val pluginName: TargetName,
    val contentModule: ContentModuleName?,
    val isTestPlugin: Boolean,
  )

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Product '${context}' has descriptor IDs declared by both production and test plugins${s.reset}")
    appendLine()
    for ((descriptorId, owners) in duplicates.entries.sortedBy { it.key.value }) {
      appendLine("  ${s.red}*${s.reset} ${s.bold}${descriptorId.value}${s.reset}")
      for (owner in owners) {
        val typeSuffix = if (owner.isTestPlugin) " (test)" else ""
        val moduleSuffix = owner.contentModule?.let { ", content module ${it.value}" } ?: ""
        appendLine("      - ${owner.pluginName.value}$typeSuffix$moduleSuffix")
      }
    }
    appendLine()
    appendLine("${s.blue}This causes runtime error: \"Plugin declares id ... which conflicts with the same id from another plugin\"${s.reset}")
    appendLine("${s.blue}Fix: remove the conflicting descriptor from the test plugin, or adjust bundling so IDs are unique.${s.reset}")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

/**
 * Proposed patch snippet for fixing validation errors.
 */
data class ProposedPatch(
  @JvmField val title: String,
  @JvmField val patch: String,
)

/**
 * Error when a plugin has dependencies that cannot be resolved in any product that bundles it.
 */
data class PluginDependencyError(
  override val context: String,
  /** The plugin target name */
  val pluginName: TargetName,
  /** Missing dependencies -> modules needing them */
  @JvmField val missingDependencies: Map<ContentModuleName, Set<ContentModuleName>>,
  /** Module source info for rich error formatting (plugin, products, etc.) */
  @JvmField val moduleSourceInfo: Map<ContentModuleName, ModuleSourceInfo> = emptyMap(),
  /** Per-product breakdown: product name -> unresolved deps in that product */
  @JvmField val unresolvedByProduct: Map<String, Set<ContentModuleName>> = emptyMap(),
  /** Dependencies that were filtered by dependencyFilter, per content module (for error message clarity) */
  @JvmField val filteredDependencies: Map<ContentModuleName, Set<ContentModuleName>> = emptyMap(),
  /** Structural violations: content module -> deps with violation (e.g., REQUIRED depending on OPTIONAL sibling) */
  @JvmField val structuralViolations: Map<ContentModuleName, Set<ContentModuleName>> = emptyMap(),
  /** Proposed patches for quick fixes (e.g., allowMissingDependencies in product specs) */
  @JvmField val proposedPatches: List<ProposedPatch> = emptyList(),
  override val ruleName: String = "PluginValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.PLUGIN_DEPENDENCY_UNRESOLVED

  override fun format(s: AnsiStyle): String = buildString {
    val isTestDescriptor = context.startsWith("Test descriptor dependencies:")
    val isTestPluginContent = context.startsWith("Test plugin content dependencies:")
    val hasOnlyStructural = structuralViolations.isNotEmpty() &&
                            missingDependencies.isEmpty() &&
                            unresolvedByProduct.isEmpty() &&
                            filteredDependencies.isEmpty()

    val pluginNameStr = pluginName.value
    val header = when {
      hasOnlyStructural && isTestPluginContent -> "Test plugin '$pluginNameStr' has structural violations"
      hasOnlyStructural -> "Plugin '$pluginNameStr' has structural violations"
      isTestDescriptor -> "Plugin '$pluginNameStr' has unresolvable test descriptor dependencies"
      isTestPluginContent -> "Test plugin '$pluginNameStr' has unresolvable content module dependencies"
      else -> "Plugin '$pluginNameStr' has unresolvable content module dependencies"
    }
    appendLine("${s.red}${s.bold}$header${s.reset}")
    appendLine()

    // Report structural violations FIRST - these should be fixed before availability errors
    if (structuralViolations.isNotEmpty()) {
      appendLine("  ${s.yellow}${s.bold}STRUCTURAL VIOLATIONS (fix these first):${s.reset}")
      appendLine()
      for ((module, deps) in structuralViolations.entries.sortedBy { it.key.value }) {
        val modInfo = moduleSourceInfo.get(module)
        val modLoading = modInfo?.loadingMode?.name?.lowercase() ?: "unspecified"
        appendLine("  ${s.red}*${s.reset} ${s.bold}'${module.value}'${s.reset} ($modLoading) depends on:")
        for (dep in deps.sortedBy { it.value }) {
          val depInfo = moduleSourceInfo.get(dep)
          val depLoading = depInfo?.loadingMode?.name?.lowercase() ?: "unspecified"
          // Check if this dep is auto-inferred (filtered out from XML generation)
          val isInferred = filteredDependencies.get(module)?.contains(dep) == true
          val inferredNote = if (isInferred) ", auto-inferred JPS dependency" else ""
          appendLine("      └─ ${s.bold}'${dep.value}'${s.reset} ($depLoading$inferredNote) ← $modLoading cannot depend on $depLoading sibling")
        }
      }
      appendLine()
      appendLine("  ${s.blue}Fix:${s.reset} Either:")
      appendLine("    1. Change the depending module's loading to OPTIONAL/ON_DEMAND")
      appendLine("    2. Or change the dependency's loading to REQUIRED/EMBEDDED")
      appendLine("    3. Or move the dependency to a different plugin")
      appendLine()
    }

    if (hasOnlyStructural) {
      if (proposedPatches.isNotEmpty()) {
        appendLine("${s.blue}Proposed patch:${s.reset} Set loading=\"required\" for the offending dependency modules.")
        for (patch in proposedPatches) {
          appendLine("Patch (${patch.title}):")
          for (line in patch.patch.lineSequence()) {
            appendLine(line)
          }
          appendLine()
        }
      }
      appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
      appendLine()
      return@buildString
    }

    var hasFilteredDeps = false

    for ((missingDep, needingModules) in missingDependencies.entries.sortedByDescending { it.value.size }) {
      val missingDepInfo = moduleSourceInfo.get(missingDep)

      // Check if this dep was filtered out by dependencyFilter for any of the needing modules
      val isFiltered = needingModules.any { moduleName ->
        filteredDependencies.get(moduleName)?.contains(missingDep) == true
      }
      if (isFiltered) {
        hasFilteredDeps = true
      }

      val depSource = if (isFiltered) {
        " (auto-inferred JPS dependency, filtered by config)"
      }
      else {
        formatMissingDepSource(missingDepInfo)
      }
      appendLine("  ${s.red}*${s.reset} Missing: ${s.bold}'${missingDep.value}'${s.reset}$depSource")

      appendLine("    Needed by:")
      val sortedModules = needingModules.sortedBy { it.value }
      for ((modIdx, mod) in sortedModules.withIndex()) {
        val isLast = modIdx == sortedModules.lastIndex
        val modPrefix = if (isLast) "└─" else "├─"
        val info = moduleSourceInfo.get(mod)
        val modStr = mod.value
        val moduleType = when {
          modStr.endsWith("._test") -> "test descriptor"
          info?.sourcePlugin != null -> {
            val loading = info.loadingMode
            if (loading != null && loading != ModuleLoadingRuleValue.OPTIONAL) {
              "content module, loading=${loading.name.lowercase()}"
            }
            else {
              "content module"
            }
          }
          else -> "module"
        }
        appendLine("      $modPrefix ${s.bold}$modStr${s.reset} ($moduleType)")

        val childPrefix = if (isLast) "   " else "│  "
        if (info?.sourcePlugin != null) {
          appendLine("      $childPrefix └─ in plugin: ${info.sourcePlugin.value}")
          if (info.bundledInProducts.isNotEmpty()) {
            appendLine("      $childPrefix     └─ bundled in: ${info.bundledInProducts.sorted().joinToString(", ")}")
          }
        }
      }
      appendLine()
    }

    // Fix suggestion
    // Note: Check hasFilteredDeps BEFORE isNonBundledPlugin because if the only unresolved deps
    // are filtered ones, unresolvedByProduct will be empty but plugin IS bundled
    // Non-bundled plugins have "(non-bundled)" key in unresolvedByProduct
    val isNonBundledPlugin = unresolvedByProduct.containsKey("(non-bundled)") && !hasFilteredDeps
    when {
      isTestDescriptor || isTestPluginContent -> {
        appendLine("${s.blue}Fix:${s.reset} Register the missing module as <content><module> in a test plugin,")
        appendLine("     or add to a module set if it should be generally available.")
      }
      hasFilteredDeps -> {
        val missingDeps = missingDependencies.keys.sortedBy { it.value }
        val kotlinCode = "\"$pluginNameStr\" to setOf(${missingDeps.joinToString { "\"${it.value}\"" }}),"

        appendLine("${s.blue}Fix:${s.reset} Add to pluginAllowedMissingDependencies in ModuleSetGenerationConfig:")
        appendLine("       ${s.gray}$kotlinCode${s.reset}")
        appendLine("     or add to content module descriptor <dependencies>:")
        for (dep in missingDeps) {
          appendLine("       ${s.gray}<module name=\"${dep.value}\"/>${s.reset}")
        }
      }
      isNonBundledPlugin -> {
        appendLine("${s.blue}Fix:${s.reset} The plugin is not bundled in any product.")
        appendLine("     If the dependency should be available, declare it as <content> in a plugin,")
        appendLine("     or add to a module set if it should be generally available.")
      }
      else -> {
        appendLine("${s.blue}Fix:${s.reset} Add the missing modules to module sets used by these products,")
        appendLine("     or add them to 'allowedMissingDependencies' in the product spec if intentional.")
      }
    }
    appendLine()

    if (proposedPatches.isNotEmpty()) {
      val proposedPatchHint = if (isTestDescriptor || isTestPluginContent) {
        "Add missing modules to the test plugin content in product specs."
      }
      else {
        "Add allowMissingDependencies to product specs."
      }
      appendLine("${s.blue}Proposed patch:${s.reset} $proposedPatchHint")
      for (patch in proposedPatches) {
        appendLine("Patch (${patch.title}):")
        for (line in patch.patch.lineSequence()) {
          appendLine(line)
        }
        appendLine()
      }
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
}

/**
 * Error when a plugin depends on another plugin that isn't bundled in the same products.
 */
data class PluginDependencyNotBundledError(
  override val context: String,
  /** The plugin target name */
  val pluginName: TargetName,
  /** Missing dependencies per product */
  @JvmField val missingByProduct: Map<String, Set<PluginId>>,
  /** Dependencies that don't resolve to any plugin node */
  @JvmField val unresolvedDependencies: Set<PluginId> = emptySet(),
  override val ruleName: String = "PluginDependencyValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.PLUGIN_PLUGIN_DEP_MISSING

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Plugin '${pluginName.value}' has unresolvable plugin dependencies${s.reset}")
    appendLine()

    if (unresolvedDependencies.isNotEmpty()) {
      appendLine("  ${s.red}Unresolved plugin IDs:${s.reset}")
      for (pluginId in unresolvedDependencies.sortedBy { it.value }) {
        appendLine("    ${s.red}*${s.reset} ${s.bold}${pluginId.value}${s.reset}")
      }
      appendLine()
    }

    if (missingByProduct.isNotEmpty()) {
      appendLine("  ${s.red}Missing in products:${s.reset}")
      for ((product, deps) in missingByProduct.entries.sortedBy { it.key }) {
        val depsList = deps.sortedBy { it.value }.joinToString { it.value }
        appendLine("    ${s.red}*${s.reset} ${s.bold}$product${s.reset}: $depsList")
      }
      appendLine()
    }

    appendLine("${s.blue}Fix:${s.reset} Bundle the missing plugins in the same products as '${pluginName.value}',")
    appendLine("     or remove the dependency if it's no longer required.")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

enum class DslTestPluginDependencyKind {
  PLUGIN,
  CONTENT_MODULE,
}

data class DslTestPluginOwner(
  val targetName: TargetName,
  val pluginId: PluginId,
)

data class DslTestPluginDependencySource(
  val fromModule: ContentModuleName,
  val scope: String?,
  val isDeclaredInSpec: Boolean,
  val declaredRootModule: ContentModuleName? = null,
)

/**
 * Error when a DSL-defined test plugin depends on a plugin that is not resolvable
 * in the test plugin scope and is not explicitly allowed.
 */
data class DslTestPluginDependencyError(
  override val context: String,
  val testPluginId: PluginId,
  @JvmField val productName: String,
  @JvmField val dependencyKind: DslTestPluginDependencyKind,
  val pluginDependencyId: PluginId? = null,
  val contentModuleDependencyId: ContentModuleName? = null,
  @JvmField val dependencyTargetNames: Set<TargetName> = emptySet(),
  @JvmField val owningPlugins: Set<DslTestPluginOwner> = emptySet(),
  val dependencySource: DslTestPluginDependencySource? = null,
  override val ruleName: String = "DslTestPluginDependencyGeneration",
) : ValidationError {
  init {
    when (dependencyKind) {
      DslTestPluginDependencyKind.PLUGIN -> require(pluginDependencyId != null) {
        "pluginDependencyId must be provided for PLUGIN dependency kind"
      }
      DslTestPluginDependencyKind.CONTENT_MODULE -> require(contentModuleDependencyId != null) {
        "contentModuleDependencyId must be provided for CONTENT_MODULE dependency kind"
      }
    }
  }

  override val category: ErrorCategory get() = ErrorCategory.DSL_TEST_PLUGIN_DEPENDENCY_UNRESOLVED

  override fun format(s: AnsiStyle): String = buildString {
    val header = when (dependencyKind) {
      DslTestPluginDependencyKind.PLUGIN -> "DSL test plugin '${testPluginId.value}' has an unresolvable plugin dependency"
      DslTestPluginDependencyKind.CONTENT_MODULE -> "DSL test plugin '${testPluginId.value}' depends on plugin-owned content that is not resolvable"
    }
    appendLine("${s.red}${s.bold}$header${s.reset}")
    appendLine()
    appendLine("  ${s.red}*${s.reset} Product: ${s.bold}$productName${s.reset}")
    when (dependencyKind) {
      DslTestPluginDependencyKind.PLUGIN -> {
        val pluginId = requireNotNull(pluginDependencyId)
        appendLine("  ${s.red}*${s.reset} Plugin: ${s.bold}${pluginId.value}${s.reset}")
        if (dependencyTargetNames.isNotEmpty()) {
          val targets = dependencyTargetNames.map { it.value }.sorted().joinToString(", ")
          appendLine("    Target name(s): $targets")
        }
      }
      DslTestPluginDependencyKind.CONTENT_MODULE -> {
        val moduleName = requireNotNull(contentModuleDependencyId)
        appendLine("  ${s.red}*${s.reset} Content module: ${s.bold}${moduleName.value}${s.reset}")
        if (owningPlugins.isNotEmpty()) {
          appendLine("    Owned by plugin(s):")
          for (owner in owningPlugins.sortedBy { it.pluginId.value }) {
            appendLine("      - ${owner.targetName.value} (id: ${owner.pluginId.value})")
          }
        }
        if (dependencySource != null) {
          val origin = if (dependencySource.isDeclaredInSpec) {
            "declared in test plugin spec"
          }
          else {
            "auto-added during dependency traversal"
          }
          val scopeSuffix = dependencySource.scope?.let { ", scope: $it" } ?: ""
          appendLine("    Needed by module: ${dependencySource.fromModule} ($origin$scopeSuffix)")
          val rootModule = dependencySource.declaredRootModule
          if (rootModule != null && rootModule != dependencySource.fromModule) {
            appendLine("    Declared module: ${rootModule.value}")
          }
        }
      }
    }
    appendLine()
    appendLine("${s.blue}Fix:${s.reset} Add the owning plugin target name to additionalBundledPluginTargetNames for this test plugin,")
    val moduleHint = if (dependencyKind == DslTestPluginDependencyKind.CONTENT_MODULE) {
      dependencySource?.declaredRootModule ?: dependencySource?.fromModule
    }
    else {
      null
    }
    if (moduleHint != null) {
      appendLine("     or add its plugin ID to allowedMissingPluginIds on module/requiredModule(\"${moduleHint.value}\").")
      appendLine("     (Use testPlugin.allowedMissingPluginIds for a global suppression.)")
    }
    else {
      appendLine("     or list its plugin ID in allowedMissingPluginIds to suppress this error.")
    }
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

/**
 * Error when a DSL-defined test plugin is missing plugin dependencies required by its content modules.
 */
data class MissingTestPluginPluginDependencyError(
  override val context: String,
  val testPluginId: PluginId,
  @JvmField val productName: String,
  /** Plugin IDs required by content module deps but missing in test plugin XML */
  @JvmField val missingPluginIds: Set<PluginId>,
  /** Map of missing plugin IDs to modules that require them */
  @JvmField val requiredByModules: Map<PluginId, Set<ContentModuleName>>,
  override val ruleName: String = "TestPluginPluginDependencyValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.TEST_PLUGIN_PLUGIN_DEP_MISSING

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Test plugin '${testPluginId.value}' is missing plugin dependencies required by its content modules${s.reset}")
    appendLine()
    appendLine("  ${s.red}*${s.reset} Product: ${s.bold}$productName${s.reset}")
    appendLine("  ${s.red}Missing plugin IDs:${s.reset}")
    for (pluginId in missingPluginIds.sortedBy { it.value }) {
      appendLine("    ${s.red}*${s.reset} ${s.bold}${pluginId.value}${s.reset}")
      val requiredBy = requiredByModules[pluginId].orEmpty().sortedBy { it.value }
      if (requiredBy.isNotEmpty()) {
        appendLine("      Needed by: ${requiredBy.joinToString { it.value }}")
      }
    }
    appendLine()
    appendLine("${s.blue}Fix:${s.reset} Ensure the test plugin declares these dependencies in its generated plugin.xml")
    appendLine("     (e.g., update test plugin dependency generation or its JPS dependencies),")
    appendLine("     or list the plugin IDs in allowedMissingPluginIds if the dependency is intentionally omitted.")
    appendLine()
    appendLine("${s.yellow}Why this matters:${s.reset} Missing plugin dependencies can drop classes from the test classpath")
    appendLine("and cause NoClassDefFoundError in tests.")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}

/**
 * Error when a plugin declares the same dependency in both legacy <depends>
 * and modern <dependencies><plugin/> formats.
 */
data class DuplicatePluginDependencyDeclarationError(
  override val context: String,
  /** The plugin target name */
  val pluginName: TargetName,
  /** Duplicated plugin IDs */
  @JvmField val duplicatePluginIds: Set<PluginId>,
  override val ruleName: String = "PluginDependencyValidation",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.PLUGIN_PLUGIN_DEP_DUPLICATE

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Plugin '${pluginName.value}' declares duplicate plugin dependencies${s.reset}")
    appendLine()
    appendLine("  ${s.red}Duplicates declared in both legacy <depends> and modern <dependencies>:${s.reset}")
    for (dep in duplicatePluginIds.sortedBy { it.value }) {
      appendLine("    ${s.red}*${s.reset} ${s.bold}${dep.value}${s.reset}")
    }
    appendLine()
    appendLine("${s.blue}Fix:${s.reset} Remove the legacy <depends> entry or migrate fully to <dependencies>.")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}
