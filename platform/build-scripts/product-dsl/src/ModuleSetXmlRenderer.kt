// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRule

/**
 * Determines if a module set needs to be inlined (cannot use xi:include).
 * Inlining is required when:
 * - The module set has loading overrides for its direct modules
 * - The module set contains nested sets that are overridden at top level
 *
 * @return true if the module set must be inlined, false if xi:include can be used
 */
internal fun shouldInlineModuleSet(
  moduleSet: ModuleSet,
  overrides: Map<String, ModuleLoadingRule>,
  overriddenModuleSetNames: Set<ModuleSetName>
): Boolean {
  return overrides.isNotEmpty() || containsOverriddenNestedSet(moduleSet, overriddenModuleSetNames)
}

/**
 * Appends inlined module set content with direct modules and nested set processing.
 * Used when xi:include cannot be used due to overrides or nested set conflicts.
 */
internal fun StringBuilder.appendInlinedModuleSet(
  moduleSet: ModuleSet,
  overrides: Map<String, ModuleLoadingRule>,
  contentBlocks: List<ContentBlock>,
  overriddenModuleSetNames: Set<ModuleSetName>
) {
  val hasOverrides = overrides.isNotEmpty()
  val directBlock = contentBlocks.find { it.source == moduleSet.name }
  
  // Append direct modules if present
  if (directBlock != null && directBlock.modules.isNotEmpty()) {
    val label = if (hasOverrides) moduleSet.name else "${moduleSet.name} (selective inline)"
    withEditorFold(this, "  ", label) {
      append("  <content namespace=\"$JETBRAINS_NAMESPACE\">\n")
      
      // Add explanatory comment
      if (hasOverrides) {
        val overriddenModules = overrides.entries
          .sortedBy { it.key }
          .joinToString(", ") { "${it.key}=${it.value.name.lowercase().replace('_', '-')}" }
        append("    <!-- Module set '${moduleSet.name}' with ${overrides.size} loading override(s): $overriddenModules -->\n")
      }
      else {
        val overriddenNested = findOverriddenNestedSetNames(moduleSet, overriddenModuleSetNames)
        val nestedNames = overriddenNested.joinToString(", ") { "'$it'" }
        append("    <!-- Module set '${moduleSet.name}' selectively inlined - nested set(s) $nestedNames referenced separately with overrides -->\n")
      }
      
      // Append modules with effective overrides
      for (module in directBlock.modules) {
        val effectiveLoading = overrides[module.name] ?: module.loading
        appendModuleLine(ModuleWithLoading(module.name, effectiveLoading, module.includeDependencies), "    ")
      }
      
      append("  </content>\n")
    }
  }
  
  // Process nested sets recursively
  appendNestedModuleSets(moduleSet.nestedSets, contentBlocks, overriddenModuleSetNames)
}

/**
 * Appends nested module sets with appropriate handling (inline or xi:include).
 */
internal fun StringBuilder.appendNestedModuleSets(
  nestedSets: List<ModuleSet>,
  contentBlocks: List<ContentBlock>,
  overriddenModuleSetNames: Set<ModuleSetName>
) {
  for (nestedSet in nestedSets) {
    val nestedSetName = ModuleSetName(nestedSet.name)
    
    when {
      nestedSetName in overriddenModuleSetNames -> {
        // Skip - will be processed separately at top level with its overrides
        append("  <!-- Nested set '${nestedSet.name}' is referenced at top-level with overrides, skipping xi:include here -->\n")
      }
      containsOverriddenNestedSet(nestedSet, overriddenModuleSetNames) -> {
        // Recursively inline - contains overridden nested sets
        appendModuleSetXml(nestedSet, emptyMap(), contentBlocks, overriddenModuleSetNames)
      }
      else -> {
        // Safe to use xi:include
        appendModuleSetInclude(nestedSet.name)
      }
    }
  }
}

/**
 * Appends a simple xi:include directive for a module set.
 */
internal fun StringBuilder.appendModuleSetInclude(moduleSetName: String) {
  append("  <xi:include href=\"/META-INF/$MODULE_SET_PREFIX$moduleSetName.xml\"/>\n")
}

/**
 * Recursively generates XML content for a module set, applying selective inlining when necessary.
 * 
 * When a module set has overrides or contains overridden nested sets, it cannot use xi:include
 * (which would lose the overrides). Instead, we inline the direct modules and generate
 * xi:include directives for non-overridden nested sets.
 *
 * @param moduleSet The module set to generate content for
 * @param overrides Loading rule overrides for direct modules
 * @param contentBlocks Pre-computed content blocks containing modules with applied rules
 * @param overriddenModuleSetNames Names of module sets that are overridden at top-level
 */
internal fun StringBuilder.appendModuleSetXml(
  moduleSet: ModuleSet,
  overrides: Map<String, ModuleLoadingRule>,
  contentBlocks: List<ContentBlock>,
  overriddenModuleSetNames: Set<ModuleSetName>
) {
  if (shouldInlineModuleSet(moduleSet, overrides, overriddenModuleSetNames)) {
    appendInlinedModuleSet(moduleSet, overrides, contentBlocks, overriddenModuleSetNames)
  }
  else {
    appendModuleSetInclude(moduleSet.name)
  }
}

/**
 * Appends a single module XML element with optional loading attribute.
 */
internal fun StringBuilder.appendModuleLine(moduleWithLoading: ModuleWithLoading, indent: String = "    ") {
  append("$indent<module name=\"${moduleWithLoading.name}\"")
  if (moduleWithLoading.loading != null) {
    // convert enum to lowercase with hyphens (e.g., ON_DEMAND -> on-demand)
    append(" loading=\"${moduleWithLoading.loading.name.lowercase().replace('_', '-')}\"")
  }
  append("/>\n")
}

/**
 * Appends a content block with modules wrapped in editor fold.
 */
internal fun StringBuilder.appendContentBlock(
  blockSource: String,
  modules: List<ModuleWithLoading>,
  indent: String = "  ",
) {
  withEditorFold(sb = this, indent = indent, description = blockSource) {
    append("$indent<content namespace=\"$JETBRAINS_NAMESPACE\">\n")
    for (module in modules) {
      appendModuleLine(module, "$indent  ")
    }
    append("$indent</content>\n")
  }
}

/**
 * Appends module set loading strategy comment when there are overrides.
 */
internal fun StringBuilder.appendModuleSetsStrategyComment(
  spec: ProductModulesContentSpec,
  overriddenModuleSetNames: Set<ModuleSetName>
) {
  if (overriddenModuleSetNames.isEmpty()) return
  
  append("  <!-- Module Set Loading Strategy:\n")
  for (moduleSetWithOverrides in spec.moduleSets) {
    if (moduleSetWithOverrides.hasOverrides) {
      val overrideCount = moduleSetWithOverrides.loadingOverrides.size
      append("       - '${moduleSetWithOverrides.moduleSet.name}': $overrideCount module(s) with loading overrides\n")
    }
    else if (containsOverriddenNestedSet(moduleSetWithOverrides.moduleSet, overriddenModuleSetNames)) {
      val overriddenNested = findOverriddenNestedSetNames(moduleSetWithOverrides.moduleSet, overriddenModuleSetNames)
      val nestedNames = overriddenNested.joinToString(", ") { "'$it'" }
      append("       - '${moduleSetWithOverrides.moduleSet.name}': selectively inlined (contains overridden nested set(s): $nestedNames)\n")
    }
  }
  append("  -->\n")
}
