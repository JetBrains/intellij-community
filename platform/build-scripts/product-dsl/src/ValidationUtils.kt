// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout

/**
 * Formats a validation error message with consistent structure.
 * 
 * @param title Error title (will be prefixed with ❌)
 * @param details List of detail lines to include in the message body
 * @param hint Optional hint text (will be prefixed with 💡 Hint:)
 * @return Formatted error message string
 */
internal fun formatValidationError(
  title: String,
  details: List<String>,
  hint: String? = null
): String = buildString {
  appendLine("❌ $title")
  appendLine()
  details.forEach { appendLine(it) }
  if (hint != null) {
    appendLine()
    appendLine("💡 Hint: $hint")
  }
}

/**
 * Validates that all overridden modules exist as direct modules in their respective module sets.
 * Throws an error if invalid overrides are found.
 *
 * @param moduleSetWithOverrides The module set with overrides to validate
 */
internal fun validateModuleSetOverrides(
  moduleSetWithOverrides: ModuleSetWithOverrides
) {
  if (moduleSetWithOverrides.loadingOverrides.isEmpty()) return
  
  val directModules = moduleSetWithOverrides.moduleSet.modules.mapTo(LinkedHashSet()) { it.name }
  val invalidOverrides = moduleSetWithOverrides.loadingOverrides.keys.filter { it !in directModules }
  
  if (invalidOverrides.isNotEmpty()) {
    val details = buildList {
      add("The following ${invalidOverrides.size} module(s) are not direct modules of this set:")
      invalidOverrides.sorted().forEach { add("  ✗ $it") }
      add("")
      add("Note: You cannot override nested set modules.")
      add("")
      add("Available direct modules in '${moduleSetWithOverrides.moduleSet.name}':")
      directModules.sorted().take(10).forEach { add("  ✓ $it") }
      if (directModules.size > 10) {
        add("  ... and ${directModules.size - 10} more")
      }
    }
    val hint = """You can only override direct modules, not modules from nested sets.
   To override modules from nested sets, reference the nested set directly:
   
   moduleSet(YourNestedSet()) {
     overrideAsEmbedded("module.name")
   }"""
    error(formatValidationError(
      "Invalid loading overrides for module set '${moduleSetWithOverrides.moduleSet.name}'",
      details,
      hint
    ))
  }
}

/**
 * Validates that no module appears in multiple module sets.
 * Throws an error if duplicates are found.
 *
 * @param moduleToSets Map from module name to list of module set names containing it
 */
internal fun validateNoDuplicateModules(moduleToSets: Map<String, MutableList<String>>) {
  val duplicates = moduleToSets.filterValues { it.size > 1 }
  
  if (duplicates.isNotEmpty()) {
    val details = buildList {
      for ((moduleName, sets) in duplicates.toSortedMap()) {
        add("  ✗ Module '$moduleName' appears in:")
        sets.sorted().forEach { add("      - $it") }
        add("    → Suggested fix: Remove from: ${sets.sorted().drop(1).joinToString(", ")}")
        add("")
      }
      add("📋 Each module must belong to exactly one module set.")
      add("   Fix the module set definitions in:")
      add("   • community/platform/build-scripts/src/org/jetbrains/intellij/build/productLayout/CommunityModuleSets.kt")
      add("   • platform/buildScripts/src/productLayout/UltimateModuleSets.kt")
    }
    error(formatValidationError(
      "ERROR: Duplicate modules found across ${duplicates.size} module set(s)",
      details,
      hint = null
    ))
  }
}

/**
 * Validates that a module alias is unique and records it.
 * Throws an error if the alias is already defined.
 *
 * @param alias The alias to validate
 * @param source The source defining this alias (e.g., "product level", "module set 'foo'")
 * @param aliasToSource Map tracking where each alias is defined
 */
internal fun validateAndRecordAlias(
  alias: String,
  source: String,
  aliasToSource: MutableMap<String, String>
) {
  val existing = aliasToSource.put(alias, source)
  if (existing != null) {
    val hint = if (source == "product level") {
      "Module aliases must be unique across the entire product. Remove the duplicate alias declaration from your product specification."
    } else {
      """Module aliases must be unique across all module sets.
   Either:
   • Remove the alias from $source
   • Rename the alias to something unique
   • Remove the alias from $existing"""
    }
    error(formatValidationError(
      "Duplicate module alias detected: '$alias'",
      listOf(
        "  Already defined in: $existing",
        "  Attempted to redefine at: $source"
      ),
      hint
    ))
  }
}

/**
 * Validates that products don't reference redundant module sets.
 * A module set is redundant if it's already nested inside another module set the product uses,
 * and the product doesn't apply any overrides to it.
 * 
 * This validation ensures product specifications are correct and maintainable.
 * Redundant module set references can lead to confusion and maintenance issues.
 *
 * @param allModuleSets All available module sets
 * @param productSpecs List of products with their specifications to validate
 * @throws IllegalStateException if any product has redundant module set references
 */
fun validateNoRedundantModuleSets(
  allModuleSets: List<ModuleSet>,
  productSpecs: List<Pair<String, ProductModulesContentSpec?>>
) {
  // Build map of module set name -> nested set names
  val moduleSetToNested = allModuleSets.associate { moduleSet ->
    moduleSet.name to moduleSet.nestedSets.map { it.name }.toSet()
  }
  
  val errors = mutableListOf<String>()
  
  for ((productName, contentSpec) in productSpecs) {
    if (contentSpec == null || contentSpec.moduleSets.isEmpty()) continue
    
    // Get module set names the product uses
    val usedSets = contentSpec.moduleSets.map { it.moduleSet.name }
    
    // Check each module set for redundancy
    for (moduleSetWithOverrides in contentSpec.moduleSets) {
      val setName = moduleSetWithOverrides.moduleSet.name
      
      // Skip if this set has overrides (overrides make it non-redundant)
      if (moduleSetWithOverrides.loadingOverrides.isNotEmpty()) {
        continue
      }
      
      // Check if this set is nested in any other set the product uses
      for (otherSetName in usedSets) {
        if (otherSetName == setName) continue
        
        val nestedInOther = moduleSetToNested[otherSetName]
        if (nestedInOther != null && setName in nestedInOther) {
          errors.add("  ✗ Product '$productName': module set '$setName' is redundant (already nested in '$otherSetName')")
        }
      }
    }
  }
  
  if (errors.isNotEmpty()) {
    val hint = """Remove redundant module sets from product's getProductContentDescriptor() method.
   
   Example fix:
   override fun getProductContentDescriptor() = productModules {
     // moduleSet(ssh())           // ← REMOVE (already in ide.ultimate)
     // moduleSet(rd.common())      // ← REMOVE (already in ide.ultimate)
     moduleSet(ideUltimate())       // ← KEEP (includes ssh and rd.common)
   }"""
    error(formatValidationError(
      "Product specification errors: Redundant module set references detected",
      errors,
      hint
    ))
  }
}
