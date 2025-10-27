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
 * Recursively collects all module sets from a list, including nested sets.
 * Flattens the hierarchy into a single list.
 *
 * @param sets List of module sets to collect from
 * @return Flat list containing all module sets and their nested sets recursively
 */
internal fun collectAllModuleSets(sets: List<ModuleSet>): List<ModuleSet> {
  val all = mutableListOf<ModuleSet>()
  for (set in sets) {
    all.add(set)
    all.addAll(collectAllModuleSets(set.nestedSets))
  }
  return all
}

/**
 * Recursively collects all aliases from a module set and its nested sets.
 *
 * @param moduleSet The module set to collect aliases from
 * @return List of all aliases found in the module set hierarchy
 */
internal fun collectAllAliases(moduleSet: ModuleSet): List<String> {
  val aliases = mutableListOf<String>()
  if (moduleSet.alias != null) {
    aliases.add(moduleSet.alias)
  }
  moduleSet.nestedSets.forEach { aliases.addAll(collectAllAliases(it)) }
  return aliases
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
 * Wraps content with editor-fold comments for collapsible sections in IDE.
 *
 * @param sb StringBuilder to append to
 * @param indent Indentation string (e.g., "    ")
 * @param description Description for the fold (shown in IDE)
 * @param block Lambda that generates the content between fold tags
 */
internal inline fun withEditorFold(sb: StringBuilder, indent: String, description: String, block: () -> Unit) {
  sb.append("$indent<!-- <editor-fold desc=\"$description\"> -->\n")
  block()
  sb.append("$indent<!-- </editor-fold> -->\n")
}

/**
 * Extracts all module names from nested module sets, mapping each module to its nested set.
 * Used to determine which modules are "direct" vs inherited from nested sets.
 *
 * @param moduleSet The module set to extract nested module names from
 * @return Map of module name to nested set name that contains it
 */
internal fun getNestedModuleNames(moduleSet: ModuleSet): Map<String, String> {
  val moduleToSet = mutableMapOf<String, String>()
  for (nestedSet in moduleSet.nestedSets) {
    for (module in nestedSet.modules) {
      moduleToSet[module.name] = nestedSet.name
    }
  }
  return moduleToSet
}

/**
 * Gets direct modules from a module set (excluding modules from nested sets and excluded modules).
 *
 * @param moduleSet The module set to get direct modules from
 * @param excludedModules Set of module names to exclude
 * @return List of direct modules
 */
internal fun getDirectModules(moduleSet: ModuleSet, excludedModules: Set<String> = emptySet()): List<ContentModule> {
  val nestedModuleToSet = getNestedModuleNames(moduleSet)
  val directModules = mutableListOf<ContentModule>()
  for (module in moduleSet.modules) {
    // Skip modules that come from nested sets (silently filter)
    if (module.name !in nestedModuleToSet && module.name !in excludedModules) {
      directModules.add(module)
    }
  }
  return directModules
}

/**
 * Generic tree visitor for module sets.
 * Recursively traverses module set hierarchy and calls callback for each module set with its direct modules.
 *
 * @param moduleSet The module set to traverse
 * @param setName The qualified name of this module set (e.g., "intellij.moduleSets.ssh")
 * @param excludedModules Set of module names to exclude from direct modules
 * @param processedSets Tracks already processed sets to avoid duplicates
 * @param onModuleSet Callback invoked for each module set with (moduleSet, setName, directModules)
 */
internal fun traverseModuleSetRecursively(
  moduleSet: ModuleSet,
  setName: String,
  excludedModules: Set<String> = emptySet(),
  processedSets: MutableSet<String> = mutableSetOf(),
  onModuleSet: (moduleSet: ModuleSet, setName: String, directModules: List<ContentModule>) -> Unit
) {
  // Skip if already processed
  if (!processedSets.add(setName)) {
    return
  }

  // Get direct modules for this set
  val directModules = getDirectModules(moduleSet, excludedModules)

  // Call the callback
  onModuleSet(moduleSet, setName, directModules)

  // Recursively process nested sets
  for (nestedSet in moduleSet.nestedSets) {
    traverseModuleSetRecursively(
      moduleSet = nestedSet,
      setName = "intellij.moduleSets.${nestedSet.name}",
      excludedModules = excludedModules,
      processedSets = processedSets,
      onModuleSet = onModuleSet
    )
  }
}

/**
 * Builds a mapping from module names to their module set chains.
 * Each module maps to the full chain of sets containing it (including all parent sets).
 *
 * @param moduleSets Top-level module sets to process
 * @param excludedModules Set of module names to exclude
 * @return Map from module name to module set chain as list (e.g., ["intellij.moduleSets.libraries", "intellij.moduleSets.libraries.core"])
 */
fun buildModuleToSetChainMapping(moduleSets: List<ModuleSet>, excludedModules: Set<String> = emptySet()): Map<String, List<String>> {
  val moduleToChain = mutableMapOf<String, List<String>>()

  fun traverseWithChain(moduleSet: ModuleSet, chainList: List<String>, processedSets: MutableSet<String>) {
    val setName = "intellij.moduleSets.${moduleSet.name}"
    val currentChain = chainList + setName

    // Skip if already processed
    if (!processedSets.add(setName)) {
      return
    }

    // Get direct modules for this set
    val directModules = getDirectModules(moduleSet, excludedModules)

    // Map each direct module to this chain
    for (module in directModules) {
      moduleToChain[module.name] = currentChain
    }

    // Recursively process nested sets with extended chain
    for (nestedSet in moduleSet.nestedSets) {
      traverseWithChain(nestedSet, currentChain, processedSets)
    }
  }

  // Process all top-level module sets
  val processedSets = mutableSetOf<String>()
  for (moduleSet in moduleSets) {
    traverseWithChain(moduleSet, emptyList(), processedSets)
  }

  return moduleToChain
}