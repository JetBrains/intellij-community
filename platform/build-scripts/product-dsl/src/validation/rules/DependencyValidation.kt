// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validation.rules

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.dependency.AllPluginModules
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.traversal.ModuleSetTraversalCache
import org.jetbrains.intellij.build.productLayout.validation.DuplicateModulesError
import org.jetbrains.intellij.build.productLayout.validation.MissingDependenciesError
import org.jetbrains.intellij.build.productLayout.validation.MissingModuleSetsError
import org.jetbrains.intellij.build.productLayout.validation.ModuleSourceInfo
import org.jetbrains.intellij.build.productLayout.validation.ProductModuleIndex
import org.jetbrains.intellij.build.productLayout.validation.SelfContainedValidationError
import org.jetbrains.intellij.build.productLayout.validation.ValidationError

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
 * This is **Tier 1: Product-Level Validation** that ensures products can actually load at runtime.
 *
 * ## What It Validates
 *
 * 1. All module sets referenced by a product exist and are resolvable
 * 2. All modules in those sets can have their dependencies satisfied within the product's composition
 * 3. No module references dependencies outside the product's available modules (with exceptions for cross-plugin deps)
 * 4. No duplicate content modules (would cause runtime "Plugin has duplicated content modules" error)
 *
 * ## Caching Strategy
 *
 * - **ProductModuleIndex**: Built once per product, reused for cross-product collection and validation
 * - **crossProductModules**: Union of all product modules, computed once before validation
 * - **Parallel execution**: Products validated concurrently via `coroutineScope`
 *
 * ## Per-Product vs Global Validation
 *
 * Each product has a different "available modules" set, so validation must run per-product.
 * However, for non-critical modules (OPTIONAL, ON_DEMAND), the effective available set is global
 * ([crossPluginModules] ∪ [crossProductModules]), enabling potential future optimization.
 *
 * @param allModuleSets All defined module sets (for suggesting fixes in error messages)
 * @param productSpecs Product specifications to validate
 * @param descriptorCache Cached module dependency information
 * @param cache Cached module set traversal information
 * @param pluginContentJobs Async jobs for extracting plugin content modules
 * @return List of validation errors (empty if all products are valid)
 */
internal suspend fun validateProductModuleSets(
  allModuleSets: List<ModuleSet>,
  productSpecs: List<Pair<String, ProductModulesContentSpec?>>,
  descriptorCache: ModuleDescriptorCache,
  cache: ModuleSetTraversalCache,
  pluginContentJobs: Map<String, Deferred<PluginContentInfo?>> = emptyMap(),
  allPluginModulesDeferred: Deferred<AllPluginModules>,
  productIndicesDeferred: Deferred<Map<String, ProductModuleIndex>>,
  nonBundledPlugins: Map<String, Set<String>> = emptyMap(),
  knownPlugins: Set<String> = emptySet(),
): List<ValidationError> {
  val allPluginModules = allPluginModulesDeferred.await().allModules

  // Await pre-computed product indices (shared with TIER 3)
  val productIndices = productIndicesDeferred.await()

  // Collect cross-product modules from pre-built indices
  val crossProductModules = productIndices.values.flatMapTo(HashSet()) { it.allModules }

  // Collect union of all per-product allowedMissingDependencies for global validation
  val globalAllowedMissing = productSpecs
    .mapNotNull { it.second?.allowedMissingDependencies }
    .flatten()
    .toSet()

  val moduleSourceInfo = buildModuleSourceInfo(pluginContentJobs, productIndices, nonBundledPlugins, knownPlugins)

  // Identify non-critical plugin modules BEFORE any validation
  // A module is non-critical globally only if it's NOT critical (EMBEDDED/REQUIRED) in ANY product
  // OPTIMIZED: O(products + plugins) instead of O(plugins × products)
  // Step 1: Collect all critical modules from all products in one pass O(products)
  val criticalModulesGlobal = productIndices.values
    .flatMapTo(HashSet()) { productIndex ->
      productIndex.moduleLoadings
        .filterValues { it == ModuleLoadingRuleValue.EMBEDDED || it == ModuleLoadingRuleValue.REQUIRED }
        .keys
    }
  // Step 2: Filter plugin modules against the set O(plugins)
  val nonCriticalPluginModules = allPluginModules - criticalModulesGlobal

  // FULLY PARALLEL: Run global validation and per-product validation concurrently
  // No dependency because per-product validation uses module NAMES (computed above),
  // not validation RESULTS
  return coroutineScope {
    // Global: validate all non-critical plugin modules once
    val globalValidationJob = async {
      preValidateNonCriticalPluginModules(
        nonCriticalPluginModules = nonCriticalPluginModules,
        allPluginModules = allPluginModules,
        crossProductModules = crossProductModules,
        descriptorCache = descriptorCache,
        allModuleSets = allModuleSets,
        allowedMissing = globalAllowedMissing,
        moduleSourceInfo = moduleSourceInfo,
      )
    }

    // Per-product: validate everything EXCEPT non-critical plugin modules
    val productValidationJobs = productIndices.map { (productName, productIndex) ->
      async {
        validateSingleProduct(
          productIndex = productIndex,
          spec = productSpecs.first { it.first == productName }.second!!,
          cache = cache,
          allModuleSets = allModuleSets,
          allPluginModules = allPluginModules,
          crossProductModules = crossProductModules,
          descriptorCache = descriptorCache,
          nonCriticalPluginModules = nonCriticalPluginModules,
          moduleSourceInfo = moduleSourceInfo,
        )
      }
    }

    // Await all and merge
    val globalErrors = globalValidationJob.await()
    val productErrors = productValidationJobs.awaitAll().flatten()
    globalErrors + productErrors
  }
}

/**
 * Validates a single product and returns any errors found.
 *
 * @param nonCriticalPluginModules Modules to skip (they're validated globally in parallel).
 *        These are non-critical plugin modules that can depend on cross-plugin/cross-product modules.
 * @param moduleSourceInfo Unified source info for all modules (from plugins and module sets).
 */
private suspend fun validateSingleProduct(
  productIndex: ProductModuleIndex,
  spec: ProductModulesContentSpec,
  cache: ModuleSetTraversalCache,
  allModuleSets: List<ModuleSet>,
  allPluginModules: Set<String>,
  crossProductModules: Set<String>,
  descriptorCache: ModuleDescriptorCache,
  nonCriticalPluginModules: Set<String>,
  moduleSourceInfo: Map<String, ModuleSourceInfo>,
): List<ValidationError> {
  val errors = mutableListOf<ValidationError>()
  val productName = productIndex.productName

  // Check for missing module sets
  val missingModuleSets = productIndex.referencedModuleSets.filterNot { cache.getModuleSet(it) != null }
  if (missingModuleSets.isNotEmpty()) {
    errors.add(MissingModuleSetsError(context = productName, missingModuleSets = missingModuleSets.toSet()))
  }

  // Check for duplicate content modules (single-pass detection)
  // OPTIMIZED: O(n) single pass instead of O(n log n) groupingBy
  val seen = HashSet<String>()
  val duplicateModules = HashMap<String, Int>()

  for (moduleSetWithOverrides in spec.moduleSets) {
    val moduleSet = cache.getModuleSet(moduleSetWithOverrides.moduleSet.name)
    if (moduleSet != null) {
      for (moduleName in cache.getModuleNames(moduleSet)) {
        if (!seen.add(moduleName)) {
          duplicateModules.put(moduleName, (duplicateModules.get(moduleName) ?: 1) + 1)
        }
      }
    }
  }
  for (module in spec.additionalModules) {
    if (!seen.add(module.name)) {
      duplicateModules.put(module.name, (duplicateModules.get(module.name) ?: 1) + 1)
    }
  }
  if (duplicateModules.isNotEmpty()) {
    errors.add(DuplicateModulesError(context = productName, duplicates = duplicateModules.toSortedMap()))
  }

  // Check for missing dependencies
  val criticalModules = productIndex.moduleLoadings
    .filterValues { it == ModuleLoadingRuleValue.EMBEDDED || it == ModuleLoadingRuleValue.REQUIRED }
    .keys

  // Skip ALL non-critical plugin modules (they're validated globally in parallel)
  val modulesToValidate = productIndex.allModules
    .filterNotTo(HashSet()) { it in nonCriticalPluginModules }

  val missingDeps = findMissingTransitiveDependencies(
    modules = modulesToValidate,
    availableModules = productIndex.allModules,
    descriptorCache = descriptorCache,
    allowedMissing = spec.allowedMissingDependencies,
    crossPluginModules = allPluginModules,
    crossProductModules = crossProductModules,
    criticalModules = criticalModules,
  )

  if (missingDeps.isNotEmpty()) {
    errors.add(MissingDependenciesError(
      context = productName,
      missingModules = missingDeps,
      allModuleSets = allModuleSets,
      moduleSourceInfo = moduleSourceInfo,
    ))
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



/**
 * Pre-validates non-critical plugin modules globally against the union of all product/plugin modules.
 *
 * This runs IN PARALLEL with per-product validation (no dependency between them).
 * Non-critical modules can depend on any module in [crossProductModules] or [allPluginModules],
 * so their validation result is the same across all products.
 *
 * Each module is validated in parallel for maximum throughput.
 *
 * @param nonCriticalPluginModules Pre-computed set of non-critical plugin module names
 * @param moduleSourceInfo Unified source info for all modules (from plugins and module sets).
 * @return List of validation errors for modules with missing dependencies
 */
private suspend fun preValidateNonCriticalPluginModules(
  nonCriticalPluginModules: Set<String>,
  allPluginModules: Set<String>,
  crossProductModules: Set<String>,
  descriptorCache: ModuleDescriptorCache,
  allModuleSets: List<ModuleSet>,
  allowedMissing: Set<String>,
  moduleSourceInfo: Map<String, ModuleSourceInfo>,
): List<ValidationError> {
  if (nonCriticalPluginModules.isEmpty()) {
    return emptyList()
  }

  // PARALLEL: Validate each module independently
  // Each BFS traversal is independent and uses thread-safe descriptorCache
  val perModuleResults = coroutineScope {
    nonCriticalPluginModules.map { moduleName ->
      async {
        val missingDeps = findMissingTransitiveDependencies(
          modules = setOf(moduleName),
          availableModules = crossProductModules,
          descriptorCache = descriptorCache,
          allowedMissing = allowedMissing,
          crossPluginModules = allPluginModules,
          crossProductModules = crossProductModules,
          criticalModules = emptySet(),
        )
        moduleName to missingDeps
      }
    }.awaitAll()
  }

  // Merge results: collect all missing dependencies
  val globallyMissingDeps = HashMap<String, MutableSet<String>>()
  for ((_, missingDeps) in perModuleResults) {
    for ((dep, needers) in missingDeps) {
      globallyMissingDeps.computeIfAbsent(dep) { HashSet() }.addAll(needers)
    }
  }

  if (globallyMissingDeps.isEmpty()) {
    return emptyList()
  }

  return listOf(MissingDependenciesError(
    context = "Non-critical plugin modules (global validation)",
    missingModules = globallyMissingDeps,
    allModuleSets = allModuleSets,
    moduleSourceInfo = moduleSourceInfo,
  ))
}

// endregion