// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.analysis

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.stats.AnsiColors

internal sealed interface ValidationError {
  val context: String
}

internal data class SelfContainedValidationError(
  override val context: String,
  val missingDependencies: Map<String, Set<String>>,
) : ValidationError

internal data class MissingModuleSetsError(
  override val context: String,
  val missingModuleSets: Set<String>,
) : ValidationError

internal data class DuplicateModulesError(
  override val context: String,
  val duplicates: Map<String, Int>,
) : ValidationError

internal data class MissingDependenciesError(
  override val context: String,
  val missingModules: Map<String, Set<String>>,
  val allModuleSets: List<ModuleSet>,
) : ValidationError

// endregion

// region Validation Functions

/**
 * Validates module sets marked with selfContained=true in isolation.
 *
 * Self-contained module sets must be resolvable without other module sets.
 * This ensures they have all their dependencies available internally.
 *
 * Example: core.platform is self-contained because CodeServer uses it alone
 * without other module sets, so it must contain everything needed.
 *
 * @return List of validation errors (empty if all self-contained sets are valid)
 */
internal suspend fun validateSelfContainedModuleSets(
  allModuleSets: List<ModuleSet>,
  descriptorCache: ModuleDescriptorCache,
  cache: ModuleSetTraversalCache,
): List<SelfContainedValidationError> {
  val selfContainedSets = collectSelfContainedSets(allModuleSets)
  if (selfContainedSets.isEmpty()) {
    return emptyList()
  }

  // Validate each self-contained set in parallel
  return coroutineScope {
    selfContainedSets.map { moduleSet ->
      async {
        val allModulesInSet = cache.getModuleNames(moduleSet)
        val missingDeps = findMissingTransitiveDependencies(
          modules = allModulesInSet,
          availableModules = allModulesInSet,
          descriptorCache = descriptorCache,
        )

        if (missingDeps.isNotEmpty()) {
          SelfContainedValidationError(context = moduleSet.name, missingDependencies = missingDeps)
        }
        else {
          null
        }
      }
    }.awaitAll().filterNotNull()
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
 * @return List of validation errors (empty if all products are valid)
 */
internal suspend fun validateProductModuleSets(
  allModuleSets: List<ModuleSet>,
  productSpecs: List<Pair<String, ProductModulesContentSpec?>>,
  descriptorCache: ModuleDescriptorCache,
  cache: ModuleSetTraversalCache,
  pluginContentJobs: Map<String, Deferred<PluginContentInfo?>> = emptyMap(),
): List<ValidationError> {
  val allPluginModules = collectAllPluginModules(pluginContentJobs)

  // Validate each product in parallel
  return coroutineScope {
    productSpecs
      .filter { (_, spec) -> spec != null }
      .map { (productName, spec) ->
        async {
          validateSingleProduct(
            productName = productName,
            spec = spec!!,
            cache = cache,
            allModuleSets = allModuleSets,
            allPluginModules = allPluginModules,
            descriptorCache = descriptorCache,
            pluginContentJobs = pluginContentJobs,
          )
        }
      }.awaitAll().flatten()
  }
}

/**
 * Validates a single product and returns any errors found.
 */
private suspend fun validateSingleProduct(
  productName: String,
  spec: ProductModulesContentSpec,
  cache: ModuleSetTraversalCache,
  allModuleSets: List<ModuleSet>,
  allPluginModules: Set<String>,
  descriptorCache: ModuleDescriptorCache,
  pluginContentJobs: Map<String, Deferred<PluginContentInfo?>>,
): List<ValidationError> {
  val errors = mutableListOf<ValidationError>()

  val productIndex = buildProductModuleIndex(productName, spec, pluginContentJobs)

  // Check for missing module sets
  val missingModuleSets = productIndex.referencedModuleSets.filterNot { cache.getModuleSet(it) != null }
  if (missingModuleSets.isNotEmpty()) {
    errors.add(MissingModuleSetsError(context = productName, missingModuleSets = missingModuleSets.toSet()))
  }

  // Check for duplicate content modules (single-pass detection)
  // OPTIMIZED: O(n) single pass instead of O(n log n) groupingBy
  val seen = mutableSetOf<String>()
  val duplicateModules = mutableMapOf<String, Int>()

  for (moduleSetWithOverrides in spec.moduleSets) {
    val moduleSet = cache.getModuleSet(moduleSetWithOverrides.moduleSet.name)
    if (moduleSet != null) {
      for (moduleName in cache.getModuleNames(moduleSet)) {
        if (!seen.add(moduleName)) {
          duplicateModules[moduleName] = (duplicateModules.get(moduleName) ?: 1) + 1
        }
      }
    }
  }
  for (module in spec.additionalModules) {
    if (!seen.add(module.name)) {
      duplicateModules[module.name] = (duplicateModules.get(module.name) ?: 1) + 1
    }
  }
  if (duplicateModules.isNotEmpty()) {
    errors.add(DuplicateModulesError(context = productName, duplicates = duplicateModules))
  }

  // Check for missing dependencies
  val criticalModules = productIndex.moduleLoadings
    .filterValues { it == ModuleLoadingRuleValue.EMBEDDED || it == ModuleLoadingRuleValue.REQUIRED }
    .keys

  val missingDeps = findMissingTransitiveDependencies(
    modules = productIndex.allModules,
    availableModules = productIndex.allModules,
    descriptorCache = descriptorCache,
    allowedMissing = spec.allowedMissingDependencies,
    crossPluginModules = allPluginModules,
    criticalModules = criticalModules,
  )

  if (missingDeps.isNotEmpty()) {
    errors.add(MissingDependenciesError(context = productName, missingModules = missingDeps, allModuleSets = allModuleSets))
  }

  return errors
}

private fun collectSelfContainedSets(allModuleSets: List<ModuleSet>): List<ModuleSet> {
  val result = mutableListOf<ModuleSet>()

  fun visit(moduleSet: ModuleSet) {
    if (moduleSet.selfContained) {
      result.add(moduleSet)
    }
    for (nestedSet in moduleSet.nestedSets) {
      visit(nestedSet)
    }
  }

  for (moduleSet in allModuleSets) {
    visit(moduleSet)
  }
  return result
}

private suspend fun collectAllPluginModules(pluginContentJobs: Map<String, Deferred<PluginContentInfo?>>): Set<String> {
  val result = mutableSetOf<String>()
  for ((_, job) in pluginContentJobs) {
    job.await()?.contentModules?.let { result.addAll(it) }
  }
  return result
}

/**
 * Finds missing transitive dependencies using BFS traversal.
 *
 * @param modules Modules to check dependencies for
 * @param availableModules Modules that are available (dependencies should be in this set)
 * @param descriptorCache Cache for module descriptor information
 * @param allowedMissing Dependencies explicitly allowed to be missing
 * @param crossPluginModules Modules from other plugins (valid for non-critical modules)
 * @param criticalModules Modules that cannot depend on cross-plugin modules
 * @return Map of missing dependency -> set of modules that need it
 */
internal fun findMissingTransitiveDependencies(
  modules: Set<String>,
  availableModules: Set<String>,
  descriptorCache: ModuleDescriptorCache,
  allowedMissing: Set<String> = emptySet(),
  crossPluginModules: Set<String> = emptySet(),
  criticalModules: Set<String> = emptySet(),
): Map<String, Set<String>> {
  val missingDeps = mutableMapOf<String, MutableSet<String>>()

  for (moduleName in modules) {
    val info = descriptorCache.getOrAnalyze(moduleName) ?: continue
    val isCritical = moduleName in criticalModules
    val visited = mutableSetOf(moduleName)
    val queue = ArrayDeque(info.dependencies)

    while (queue.isNotEmpty()) {
      val dep = queue.removeFirst()
      if (dep in visited) {
        continue
      }
      visited.add(dep)

      when {
        dep in availableModules -> {
          // Present - traverse into its deps
          descriptorCache.getOrAnalyze(dep)?.dependencies?.let { queue.addAll(it) }
        }
        dep in allowedMissing -> {
          // Explicitly allowed to be missing - skip
        }
        dep in crossPluginModules && !isCritical -> {
          // Valid cross-plugin optional dependency - skip
        }
        else -> {
          // Missing dependency
          missingDeps.getOrPut(dep) { mutableSetOf() }.add(moduleName)
        }
      }
    }
  }

  return missingDeps
}

private data class ProductModuleIndex(
  val productName: String,
  val allModules: Set<String>,
  val referencedModuleSets: Set<String>,
  val moduleLoadings: Map<String, ModuleLoadingRuleValue?>,
)

private suspend fun buildProductModuleIndex(
  productName: String,
  spec: ProductModulesContentSpec,
  pluginContentJobs: Map<String, Deferred<PluginContentInfo?>> = emptyMap(),
): ProductModuleIndex {
  val allModules = mutableSetOf<String>()
  val referencedModuleSets = mutableSetOf<String>()
  val moduleLoadings = mutableMapOf<String, ModuleLoadingRuleValue?>()

  for (moduleSetWithOverrides in spec.moduleSets) {
    val moduleSet = moduleSetWithOverrides.moduleSet
    referencedModuleSets.add(moduleSet.name)
    collectModulesWithLoadings(moduleSet, allModules, moduleLoadings)
  }

  for (module in spec.additionalModules) {
    allModules.add(module.name)
    moduleLoadings[module.name] = module.loading
  }

  for (pluginName in spec.bundledPlugins) {
    pluginContentJobs[pluginName]?.await()?.contentModules?.let { allModules.addAll(it) }
  }

  return ProductModuleIndex(
    productName = productName,
    allModules = allModules,
    referencedModuleSets = referencedModuleSets,
    moduleLoadings = moduleLoadings,
  )
}

private fun collectModulesWithLoadings(
  moduleSet: ModuleSet,
  modules: MutableSet<String>,
  loadings: MutableMap<String, ModuleLoadingRuleValue?>,
) {
  for (module in moduleSet.modules) {
    modules.add(module.name)
    loadings[module.name] = module.loading
  }
  for (nestedSet in moduleSet.nestedSets) {
    collectModulesWithLoadings(nestedSet, modules, loadings)
  }
}

// endregion

// region Formatting

internal fun formatValidationErrors(errors: List<ValidationError>): String {
  if (errors.isEmpty()) {
    return ""
  }

  return buildString {
    for (error in errors) {
      when (error) {
        is SelfContainedValidationError -> formatSelfContainedError(this, error)
        is MissingModuleSetsError -> formatMissingModuleSetsError(this, error)
        is DuplicateModulesError -> formatDuplicateModulesError(this, error)
        is MissingDependenciesError -> formatMissingDependenciesError(this, error)
      }
    }
  }
}

private fun formatSelfContainedError(sb: StringBuilder, error: SelfContainedValidationError) {
  sb.appendLine("${AnsiColors.RED}${AnsiColors.BOLD}Module set '${error.context}' is marked selfContained but has unresolvable dependencies${AnsiColors.RESET}")
  sb.appendLine()

  for ((dep, needingModules) in error.missingDependencies.entries.sortedByDescending { it.value.size }) {
    sb.appendLine("  ${AnsiColors.RED}*${AnsiColors.RESET} Missing: ${AnsiColors.BOLD}'$dep'${AnsiColors.RESET}")
    sb.appendLine("    Needed by: ${needingModules.sorted().joinToString(", ")}")
  }

  sb.appendLine()
  sb.appendLine("${AnsiColors.YELLOW}To fix:${AnsiColors.RESET}")
  sb.appendLine("1. Add the missing modules/sets to '${error.context}' to make it truly self-contained")
  sb.appendLine("2. Or remove selfContained=true if this set is designed to compose with other sets")
  sb.appendLine()
}

private fun formatMissingModuleSetsError(sb: StringBuilder, error: MissingModuleSetsError) {
  sb.appendLine("${AnsiColors.RED}${AnsiColors.BOLD}Product '${error.context}' references non-existent module sets${AnsiColors.RESET}")
  sb.appendLine()
  for (setName in error.missingModuleSets.sorted()) {
    sb.appendLine("  ${AnsiColors.RED}*${AnsiColors.RESET} Module set '${AnsiColors.BOLD}$setName${AnsiColors.RESET}' does not exist")
  }
  sb.appendLine()
  sb.appendLine("${AnsiColors.BLUE}Fix: Remove the reference or define the module set${AnsiColors.RESET}")
  sb.appendLine()
}

private fun formatDuplicateModulesError(sb: StringBuilder, error: DuplicateModulesError) {
  sb.appendLine("${AnsiColors.RED}${AnsiColors.BOLD}Product '${error.context}' has duplicate content modules${AnsiColors.RESET}")
  sb.appendLine()
  sb.appendLine("${AnsiColors.YELLOW}Duplicated modules (appearing ${AnsiColors.BOLD}${error.duplicates.values.max()}${AnsiColors.RESET}${AnsiColors.YELLOW} times):${AnsiColors.RESET}")
  for ((moduleName, count) in error.duplicates.entries.sortedBy { it.key }) {
    sb.appendLine("  ${AnsiColors.RED}*${AnsiColors.RESET} ${AnsiColors.BOLD}$moduleName${AnsiColors.RESET} (appears $count times)")
  }
  sb.appendLine()
  sb.appendLine("${AnsiColors.BLUE}This causes runtime error: \"Plugin has duplicated content modules declarations\"${AnsiColors.RESET}")
  sb.appendLine("${AnsiColors.BLUE}Fix: Remove duplicate moduleSet() nesting or redundant module() calls${AnsiColors.RESET}")
  sb.appendLine()
}

private fun formatMissingDependenciesError(sb: StringBuilder, error: MissingDependenciesError) {
  sb.appendLine("${AnsiColors.BOLD}Product: ${error.context}${AnsiColors.RESET}")
  sb.appendLine()

  // Group by missing dependency for clearer output
  val depToModules = mutableMapOf<String, MutableSet<String>>()
  for ((module, deps) in error.missingModules) {
    for (dep in deps) {
      depToModules.getOrPut(dep) { mutableSetOf() }.add(module)
    }
  }

  for ((missingDep, needingModules) in depToModules.entries.sortedByDescending { it.value.size }) {
    sb.appendLine("  ${AnsiColors.RED}*${AnsiColors.RESET} Missing: ${AnsiColors.BOLD}'$missingDep'${AnsiColors.RESET}")
    sb.appendLine("    Needed by: ${needingModules.sorted().take(5).joinToString(", ")}")
    if (needingModules.size > 5) {
      sb.appendLine("    ... and ${needingModules.size - 5} more modules")
    }

    val containingSets = error.allModuleSets
      .filter { ModuleSetTraversal.containsModule(it, missingDep) }
      .map { it.name }

    if (containingSets.isNotEmpty()) {
      sb.appendLine("    ${AnsiColors.BLUE}Suggestion:${AnsiColors.RESET} Add module set: ${containingSets.joinToString(" or ")}")
    }
    sb.appendLine()
  }
}

internal fun formatProductDependencyErrorsHeader(sb: StringBuilder) {
  sb.appendLine("${AnsiColors.RED}${AnsiColors.BOLD}Product-level validation failed: Unresolvable module dependencies${AnsiColors.RESET}")
  sb.appendLine()
}

internal fun formatProductDependencyErrorsFooter(sb: StringBuilder) {
  sb.appendLine("${AnsiColors.BLUE}This will cause runtime errors: \"Plugin X has dependency on Y which is not installed\"${AnsiColors.RESET}")
  sb.appendLine()
  sb.appendLine("${AnsiColors.BOLD}To fix:${AnsiColors.RESET}")
  sb.appendLine("${AnsiColors.BLUE}1.${AnsiColors.RESET} Add the required module sets to the product's getProductContentDescriptor()")
  sb.appendLine("${AnsiColors.BLUE}2.${AnsiColors.RESET} Or add individual modules via module()/embeddedModule()")
  sb.appendLine("${AnsiColors.BLUE}3.${AnsiColors.RESET} Or add the product to allowUnresolvableProducts if this is intentional")
}