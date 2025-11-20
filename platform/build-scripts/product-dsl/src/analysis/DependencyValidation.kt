// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.analysis

import org.jetbrains.intellij.build.productLayout.AnsiColors
import org.jetbrains.intellij.build.productLayout.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec

/**
 * Known missing dependencies that are temporarily allowed.
 * These are typically provided by plugin layouts rather than module sets.
 * TODO: Move these to proper module sets or fix product definitions
 */
internal val KNOWN_MISSING_DEPENDENCIES = setOf(
  "intellij.cidr.core",
  "intellij.cidr.util.execution",
  "intellij.cidr.debugger.core",
  "intellij.cidr.debugger.backend",
  "intellij.cidr.runner"
)

/**
 * Validates module sets marked with selfContained=true in isolation.
 * 
 * Self-contained module sets must be resolvable without other module sets.
 * This ensures they have all their dependencies available internally.
 * 
 * Example: core.platform is self-contained because CodeServer uses it alone
 * without other module sets, so it must contain everything needed.
 * 
 * Only validates self-contained sets to avoid false positives from composable
 * module sets like debugger(), vcs(), xml() that depend on modules from other sets.
 * 
 * **Key difference from normal validation**: For self-contained sets, ALL modules
 * in the entire hierarchy (including nested sets) can see ALL other modules.
 * This matches runtime behavior where order doesn't matter (topological sort).
 */
internal fun validateSelfContainedModuleSets(
  allModuleSets: List<ModuleSet>,
  descriptorCache: ModuleDescriptorCache
) {
  // Collect all self-contained module sets (recursively check nested sets too)
  val selfContainedSets = mutableListOf<ModuleSet>()
  fun collectSelfContained(moduleSet: ModuleSet) {
    if (moduleSet.selfContained) {
      selfContainedSets.add(moduleSet)
    }
    for (nestedSet in moduleSet.nestedSets) {
      collectSelfContained(nestedSet)
    }
  }
  for (moduleSet in allModuleSets) {
    collectSelfContained(moduleSet)
  }
  
  if (selfContainedSets.isEmpty()) {
    return
  }
  
  // Validate each self-contained set in isolation
  for (moduleSet in selfContainedSets) {
    // Collect ALL modules from the entire hierarchy (flatten nested sets)
    val allModulesInSet = mutableSetOf<String>()
    fun collectAllModules(ms: ModuleSet) {
      for (module in ms.modules) {
        allModulesInSet.add(module.name)
      }
      for (nestedSet in ms.nestedSets) {
        collectAllModules(nestedSet)
      }
    }
    collectAllModules(moduleSet)
    
    // Collect modules with descriptors
    val modulesWithDescriptors = allModulesInSet.mapNotNull { moduleName ->
      descriptorCache.getOrAnalyze(moduleName)?.let { moduleName to it }
    }
    
    if (modulesWithDescriptors.isEmpty()) {
      continue
    }
    
    // Validate dependencies: each module must be able to reach its dependencies
    // within the flattened set (all modules can see all other modules)
    val missingDeps = mutableMapOf<String, MutableSet<String>>()
    
    for ((moduleName, info) in modulesWithDescriptors) {
      // Check direct and transitive dependencies
      val visited = mutableSetOf(moduleName)
      val queue = ArrayDeque(info.dependencies.map { it to listOf(moduleName) })
      
      while (queue.isNotEmpty()) {
        val (dep, chain) = queue.removeFirst()
        
        if (dep in visited) {
          continue
        }
        visited.add(dep)
        
        // Check if dependency is in the flattened set
        if (dep !in allModulesInSet) {
          missingDeps.getOrPut(dep) { mutableSetOf() }.add(chain.first())
        }
        
        // Add transitive dependencies
        val depInfo = descriptorCache.getOrAnalyze(dep)
        if (depInfo != null) {
          for (transitiveDep in depInfo.dependencies) {
            queue.add(transitiveDep to (chain + dep))
          }
        }
      }
    }
    
    if (missingDeps.isNotEmpty()) {
      error(buildString {
        appendLine("${AnsiColors.RED}${AnsiColors.BOLD}❌ Module set '${moduleSet.name}' is marked selfContained but has unresolvable dependencies${AnsiColors.RESET}")
        appendLine()
        
        for ((dep, needingModules) in missingDeps.entries.sortedByDescending { it.value.size }) {
          appendLine("  ${AnsiColors.RED}✗${AnsiColors.RESET} Missing: ${AnsiColors.BOLD}'$dep'${AnsiColors.RESET}")
          appendLine("    Needed by: ${needingModules.sorted().joinToString(", ")}")
        }
        
        appendLine()
        appendLine("${AnsiColors.YELLOW}💡 To fix:${AnsiColors.RESET}")
        appendLine("1. Add the missing modules/sets to '${moduleSet.name}' to make it truly self-contained")
        appendLine("2. Or remove selfContained=true if this set is designed to compose with other sets")
      })
    }
  }
}

/**
 * Validates that all products have resolvable module set dependencies.
 * 
 * This is Tier 2 validation that ensures products can actually load at runtime.
 * It validates that:
 * 1. All module sets referenced by a product exist and are resolvable
 * 2. All modules in those sets can have their dependencies satisfied within the product's composition
 * 3. No module references dependencies outside the product's available modules
 * 
 * This catches the class of errors like:
 * "Plugin 'Java' has dependency on 'com.intellij.modules.vcs' which is not installed"
 * 
 * @param allModuleSets All available module sets (community + ultimate)
 * @param productSpecs List of (productName, ProductModulesContentSpec) pairs
 * @param descriptorCache Cache for module descriptor information
 * @param allowUnresolvableProducts Set of product names explicitly allowed to have unresolvable dependencies (e.g., test products)
 * @throws IllegalStateException if any product has unresolvable dependencies
 */
internal fun validateProductModuleSets(
  allModuleSets: List<ModuleSet>,
  productSpecs: List<Pair<String, ProductModulesContentSpec?>>,
  descriptorCache: ModuleDescriptorCache,
  allowUnresolvableProducts: Set<String> = setOf()
) {
  data class ProductError(
    val productName: String,
    val missingModules: Map<String, Set<String>>,  // module -> set of dependencies it needs
  )
  
  val productErrors = mutableListOf<ProductError>()
  val moduleSetsByName = allModuleSets.associateBy { it.name }
  
  for ((productName, spec) in productSpecs) {
    // Skip products without specs
    if (spec == null) {
      continue
    }
    
    // Build index of modules available in this product
    val productIndex = buildProductModuleIndex(productName, spec)
    
    // Validate that all referenced module sets exist
    val missingModuleSets = productIndex.referencedModuleSets.filter { it !in moduleSetsByName }
    if (missingModuleSets.isNotEmpty()) {
      error(buildString {
        appendLine("${AnsiColors.RED}${AnsiColors.BOLD}❌ Product '$productName' references non-existent module sets${AnsiColors.RESET}")
        appendLine()
        for (setName in missingModuleSets.sorted()) {
          appendLine("  ${AnsiColors.RED}✗${AnsiColors.RESET} Module set '${AnsiColors.BOLD}$setName${AnsiColors.RESET}' does not exist")
        }
        appendLine()
        appendLine("${AnsiColors.BLUE}💡 Fix: Remove the reference or define the module set${AnsiColors.RESET}")
      })
    }
    
    // Validate no duplicate content modules in product
    // This validation ALWAYS runs, even for products in allowUnresolvableProducts
    val allContentModules = mutableListOf<String>()
    for (moduleSetWithOverrides in spec.moduleSets) {
      val moduleSet = moduleSetsByName[moduleSetWithOverrides.moduleSet.name]
      if (moduleSet != null) {
        allContentModules.addAll(ModuleSetTraversal.collectAllModuleNamesAsList(moduleSet))
      }
    }
    for (module in spec.additionalModules) {
      allContentModules.add(module.name)
    }
    
    val duplicateModules = allContentModules.groupingBy { it }.eachCount().filter { it.value > 1 }
    if (duplicateModules.isNotEmpty()) {
      error(buildString {
        appendLine("${AnsiColors.RED}${AnsiColors.BOLD}❌ Product '$productName' has duplicate content modules${AnsiColors.RESET}")
        appendLine()
        appendLine("${AnsiColors.YELLOW}Duplicated modules (appearing ${AnsiColors.BOLD}${duplicateModules.values.max()}${AnsiColors.RESET}${AnsiColors.YELLOW} times):${AnsiColors.RESET}")
        for ((moduleName, count) in duplicateModules.entries.sortedBy { it.key }) {
          appendLine("  ${AnsiColors.RED}✗${AnsiColors.RESET} ${AnsiColors.BOLD}$moduleName${AnsiColors.RESET} (appears $count times)")
        }
        appendLine()
        appendLine("${AnsiColors.BLUE}💡 This causes runtime error: \"Plugin has duplicated content modules declarations\"${AnsiColors.RESET}")
        appendLine("${AnsiColors.BLUE}Fix: Remove duplicate moduleSet() nesting or redundant module() calls${AnsiColors.RESET}")
      })
    }
    
    // Skip missing dependency validation for explicitly allowed products
    if (productName in allowUnresolvableProducts) {
      continue
    }
    
    // Validate module dependencies within product scope
    val missingDependencies = mutableMapOf<String, MutableSet<String>>()
    
    for (moduleName in productIndex.allModules) {
      val info = descriptorCache.getOrAnalyze(moduleName) ?: continue
      val reachableModules = productIndex.moduleToReachableModules[moduleName] ?: emptySet()
      
      // Check direct dependencies
      for (dependency in info.dependencies) {
        if (dependency !in reachableModules && dependency !in KNOWN_MISSING_DEPENDENCIES) {
          missingDependencies.getOrPut(moduleName) { mutableSetOf() }.add(dependency)
        }
      }
      
      // Check transitive dependencies
      val visited = mutableSetOf(moduleName)
      val queue = ArrayDeque(info.dependencies)
      
      while (queue.isNotEmpty()) {
        val dep = queue.removeFirst()
        if (dep in visited) continue
        visited.add(dep)
        
        if (dep !in reachableModules && dep !in KNOWN_MISSING_DEPENDENCIES) {
          missingDependencies.getOrPut(moduleName) { mutableSetOf() }.add(dep)
        }
        
        val depInfo = descriptorCache.getOrAnalyze(dep)
        if (depInfo != null) {
          queue.addAll(depInfo.dependencies)
        }
      }
    }
    
    if (missingDependencies.isNotEmpty()) {
      productErrors.add(ProductError(productName, missingDependencies))
    }
  }
  
  // Report all errors
  if (productErrors.isNotEmpty()) {
    error(buildString {
      appendLine("${AnsiColors.RED}${AnsiColors.BOLD}❌ Product-level validation failed: Unresolvable module dependencies${AnsiColors.RESET}")
      appendLine()
      
      for (productError in productErrors) {
        appendLine("${AnsiColors.BOLD}Product: ${productError.productName}${AnsiColors.RESET}")
        appendLine()
        
        // Group by missing dependency for clearer output
        val depToModules = mutableMapOf<String, MutableSet<String>>()
        for ((module, deps) in productError.missingModules) {
          for (dep in deps) {
            depToModules.getOrPut(dep) { mutableSetOf() }.add(module)
          }
        }
        
        for ((missingDep, needingModules) in depToModules.entries.sortedByDescending { it.value.size }) {
          appendLine("  ${AnsiColors.RED}✗${AnsiColors.RESET} Missing: ${AnsiColors.BOLD}'$missingDep'${AnsiColors.RESET}")
          appendLine("    Needed by: ${needingModules.sorted().take(5).joinToString(", ")}")
          if (needingModules.size > 5) {
            appendLine("    ... and ${needingModules.size - 5} more modules")
          }
          
          // Suggest which module sets contain this dependency
          val containingSets = allModuleSets.filter { moduleSet ->
            ModuleSetTraversal.containsModule(moduleSet, missingDep)
          }.map { it.name }
          
          if (containingSets.isNotEmpty()) {
            appendLine("    ${AnsiColors.BLUE}Suggestion:${AnsiColors.RESET} Add module set: ${containingSets.joinToString(" or ")}")
          }
          appendLine()
        }
        
        appendLine()
      }
      
      appendLine("${AnsiColors.BLUE}💡 This will cause runtime errors: \"Plugin X has dependency on Y which is not installed\"${AnsiColors.RESET}")
      appendLine()
      appendLine("${AnsiColors.BOLD}To fix:${AnsiColors.RESET}")
      appendLine("${AnsiColors.BLUE}1.${AnsiColors.RESET} Add the required module sets to the product's getProductContentDescriptor()")
      appendLine("${AnsiColors.BLUE}2.${AnsiColors.RESET} Or add individual modules via module()/embeddedModule()")
      appendLine("${AnsiColors.BLUE}3.${AnsiColors.RESET} Or add the product to allowUnresolvableProducts if this is intentional")
    })
  }
}

// Helper functions

/**
 * Builds an index of all modules available in a specific product.
 * This is product-scoped, unlike the global ModuleSetIndex.
 */
private data class ProductModuleIndex(
  val productName: String,
  val allModules: Set<String>,  // All modules available in this product
  val moduleToReachableModules: Map<String, Set<String>>,  // Product-scoped reachability
  val referencedModuleSets: Set<String>,  // Module set names used by product
)

private fun buildProductModuleIndex(
  productName: String,
  spec: ProductModulesContentSpec,
): ProductModuleIndex {
  val allModules = mutableSetOf<String>()
  val referencedModuleSets = mutableSetOf<String>()

  // Recursively collect all modules from a module set
  fun collectModulesFromSet(moduleSet: ModuleSet) {
    for (module in moduleSet.modules) {
      allModules.add(module.name)
    }
    for (nestedSet in moduleSet.nestedSets) {
      collectModulesFromSet(nestedSet)
    }
  }
  
  // Process all module sets referenced by the product
  // Use moduleSetWithOverrides.moduleSet directly - it already contains the full ModuleSet
  for (moduleSetWithOverrides in spec.moduleSets) {
    val moduleSet = moduleSetWithOverrides.moduleSet
    referencedModuleSets.add(moduleSet.name)
    collectModulesFromSet(moduleSet)
  }
  
  // Add additional individual modules
  for (module in spec.additionalModules) {
    allModules.add(module.name)
  }
  
  // Build product-scoped reachability: each module can see all other modules in the product
  // This is different from module set validation where modules can only see within their set
  val moduleToReachableModules = allModules.associateWith { allModules }
  
  return ProductModuleIndex(
    productName = productName,
    allModules = allModules,
    moduleToReachableModules = moduleToReachableModules,
    referencedModuleSets = referencedModuleSets,
  )
}


