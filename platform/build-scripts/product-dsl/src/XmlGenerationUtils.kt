// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout

/**
 * Shared utilities for generating plugin.xml content for both products and module sets.
 * Both products and module sets are "content containers" that:
 * - Contain nested module sets
 * - Declare module aliases
 * - Generate content blocks with modules
 */

/**
 * Visits all module sets recursively, applying the visitor function to each.
 * More efficient than collecting when you only need iteration (avoids intermediate collection).
 *
 * @param sets List of module sets to visit
 * @param visitor Function to apply to each module set
 */
internal fun visitAllModuleSets(sets: List<ModuleSet>, visitor: (ModuleSet) -> Unit) {
  for (set in sets) {
    visitor(set)
    visitAllModuleSets(set.nestedSets, visitor)
  }
}

/**
 * Recursively collects all aliases from a module set and its nested sets.
 * Collects the single `alias` field (for module set's own capability).
 *
 * @param moduleSet The module set to collect aliases from
 * @param result Accumulator for collecting aliases (avoids intermediate allocations)
 * @return List of all aliases found in the module set hierarchy
 */
internal fun collectAllAliases(
  moduleSet: ModuleSet,
  result: MutableList<String> = mutableListOf()
): List<String> {
  if (moduleSet.alias != null) {
    result.add(moduleSet.alias)
  }
  moduleSet.nestedSets.forEach { collectAllAliases(it, result) }
  return result
}

/**
 * Builds multiple `<module value="..."/>` declarations from a list of aliases.
 *
 * @param aliases The list of module aliases (e.g., ["com.intellij.modules.idea", "com.intellij.modules.java-capable"])
 * @return XML string with all aliases, or empty string if list is empty
 */
internal fun buildModuleAliasesXml(aliases: List<String>): String {
  return aliases.joinToString("") { "  <module value=\"$it\"/>\n" }
}

/**
 * Wraps content with region comments for collapsible sections in IDE.
 * Uses try-finally to ensure the closing tag is always written, even if block throws.
 *
 * @param sb StringBuilder to append to
 * @param indent Indentation string (e.g., "    ")
 * @param description Description for the fold (shown in IDE)
 * @param block Lambda that generates the content between fold tags
 */
internal inline fun withEditorFold(sb: StringBuilder, indent: String, description: String, block: () -> Unit) {
  sb.append("$indent<!-- region $description -->\n")
  try {
    block()
  }
  finally {
    sb.append("$indent<!-- endregion -->\n")
  }
}

/**
 * Visits all modules recursively (including nested sets), applying the visitor function to each.
 * More efficient than collecting when you only need iteration (avoids intermediate collection).
 *
 * @param moduleSet The module set to visit
 * @param visitor Function to apply to each module
 */
internal fun visitAllModules(moduleSet: ModuleSet, visitor: (ContentModule) -> Unit) {
  for (module in moduleSet.modules) {
    visitor(module)
  }
  for (nestedSet in moduleSet.nestedSets) {
    visitAllModules(nestedSet, visitor)
  }
}

/**
 * Checks if a module set contains (directly or transitively) any nested set whose name is in the given set.
 * Used to detect when a parent module set contains overridden nested sets.
 */
internal fun containsOverriddenNestedSet(moduleSet: ModuleSet, overriddenNames: Set<ModuleSetName>): Boolean {
  // Check direct nested sets
  for (nestedSet in moduleSet.nestedSets) {
    if (ModuleSetName(nestedSet.name) in overriddenNames) {
      return true
    }
    // Check recursively
    if (containsOverriddenNestedSet(nestedSet, overriddenNames)) {
      return true
    }
  }
  return false
}

/**
 * Finds all nested set names (directly or transitively) that are in the given set of names.
 * Used for generating descriptive comments about which nested sets are overridden.
 */
internal fun findOverriddenNestedSetNames(moduleSet: ModuleSet, overriddenNames: Set<ModuleSetName>): List<ModuleSetName> {
  val result = mutableListOf<ModuleSetName>()
  for (nestedSet in moduleSet.nestedSets) {
    val nestedSetName = ModuleSetName(nestedSet.name)
    if (nestedSetName in overriddenNames) {
      result.add(nestedSetName)
    }
    // Check recursively
    result.addAll(findOverriddenNestedSetNames(nestedSet, overriddenNames))
  }
  return result
}

