// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

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
  /** Metadata about modules that have missing dependencies (loading mode, source plugin, etc.) */
  val moduleMetadata: Map<String, ModuleMetadata> = emptyMap(),
  /** Full traceability info for all plugin modules (for looking up missing dep info) */
  val moduleTraceInfo: Map<String, ModuleTraceInfo> = emptyMap(),
) : ValidationError

/**
 * Metadata about a module's origin and loading characteristics.
 * Used to provide contextual information in validation error messages.
 */
internal data class ModuleMetadata(
  /** The loading mode (EMBEDDED, REQUIRED, OPTIONAL, ON_DEMAND) */
  val loadingMode: ModuleLoadingRuleValue?,
  /** Name of the bundled plugin that contributed this module, or null if from module set */
  val sourcePlugin: String?,
  /** Name of the module set that contributed this module, or null if from bundled plugin */
  val sourceModuleSet: String?,
  /** Products that contain this module (for global validation error messages) */
  val sourceProducts: Set<String>? = null,
)

/**
 * Complete traceability info for a plugin content module.
 * Single lookup provides all context for error messages.
 * Built once and shared across all validation lookups.
 */
internal data class ModuleTraceInfo(
  /** Plugin containing this module */
  @JvmField val sourcePlugin: String,
  /** Products that bundle this plugin (empty for additional/non-bundled plugins) */
  @JvmField val bundledInProducts: Set<String>,
  /** Source description if plugin is in additionalPlugins, null otherwise */
  @JvmField val additionalPluginSource: String?,
)

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
  additionalPlugins: Map<String, String> = emptyMap(),
): List<ValidationError> {
  val allPluginModules = collectAllPluginModules(pluginContentJobs)

  // Build product indices once and reuse for both cross-product collection and validation
  val productIndices: Map<String, ProductModuleIndex> = coroutineScope {
    productSpecs
      .filter { (_, spec) -> spec != null }
      .map { (productName, spec) ->
        async { productName to buildProductModuleIndex(productName, spec!!, cache, pluginContentJobs) }
      }.awaitAll().toMap()
  }

  // Collect cross-product modules from pre-built indices
  val crossProductModules = productIndices.values.flatMapTo(HashSet()) { it.allModules }

  // Collect union of all per-product allowedMissingDependencies for global validation
  val globalAllowedMissing = productSpecs
    .mapNotNull { it.second?.allowedMissingDependencies }
    .flatten()
    .toSet()

  // Build module traceability info for error messages (single lookup per module)
  // Step 1: plugin -> products that bundle it
  val pluginToProducts = HashMap<String, MutableSet<String>>()
  for ((productName, spec) in productSpecs) {
    spec?.bundledPlugins?.forEach { pluginName ->
      pluginToProducts.computeIfAbsent(pluginName) { HashSet() }.add(productName)
    }
  }
  // Step 2: module -> full trace info (single pass through all plugins)
  val moduleTraceInfo = HashMap<String, ModuleTraceInfo>()
  for ((pluginName, job) in pluginContentJobs) {
    val products = pluginToProducts[pluginName] ?: emptySet()
    val info = ModuleTraceInfo(
      sourcePlugin = pluginName,
      bundledInProducts = products,
      additionalPluginSource = additionalPlugins[pluginName],
    )
    job.await()?.contentModules?.forEach { moduleName ->
      moduleTraceInfo[moduleName] = info
    }
  }

  // Identify non-critical plugin modules BEFORE any validation (just module names, O(n) filter)
  // A module is non-critical globally only if it's NOT critical (EMBEDDED/REQUIRED) in ANY product
  val nonCriticalPluginModules = allPluginModules.filterTo(HashSet()) { moduleName ->
    productIndices.values.none { productIndex ->
      val loading = productIndex.moduleLoadings[moduleName]
      loading == ModuleLoadingRuleValue.EMBEDDED || loading == ModuleLoadingRuleValue.REQUIRED
    }
  }

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
        moduleTraceInfo = moduleTraceInfo,
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
    // Build metadata for modules that have missing dependencies
    val affectedModules = missingDeps.values.flatten().toSet()
    val moduleMetadata = affectedModules.associateWith { moduleName ->
      ModuleMetadata(
        loadingMode = productIndex.moduleLoadings[moduleName],
        sourcePlugin = productIndex.moduleToSourcePlugin[moduleName],
        sourceModuleSet = productIndex.moduleToSourceModuleSet[moduleName],
      )
    }
    errors.add(MissingDependenciesError(
      context = productName,
      missingModules = missingDeps,
      allModuleSets = allModuleSets,
      moduleMetadata = moduleMetadata,
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

private suspend fun collectAllPluginModules(pluginContentJobs: Map<String, Deferred<PluginContentInfo?>>): Set<String> {
  val result = mutableSetOf<String>()
  for ((_, job) in pluginContentJobs) {
    job.await()?.contentModules?.let { result.addAll(it) }
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
 * @return List of validation errors for modules with missing dependencies
 */
private suspend fun preValidateNonCriticalPluginModules(
  nonCriticalPluginModules: Set<String>,
  allPluginModules: Set<String>,
  crossProductModules: Set<String>,
  descriptorCache: ModuleDescriptorCache,
  allModuleSets: List<ModuleSet>,
  allowedMissing: Set<String>,
  moduleTraceInfo: Map<String, ModuleTraceInfo>,
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

  // Build error with full traceability from moduleTraceInfo
  val affectedModules = globallyMissingDeps.values.flatMapTo(HashSet()) { it }
  val moduleMetadata = affectedModules.associateWith { moduleName ->
    val traceInfo = moduleTraceInfo[moduleName]
    ModuleMetadata(
      loadingMode = null, // Non-critical by definition
      sourcePlugin = traceInfo?.sourcePlugin,
      sourceModuleSet = null,
      sourceProducts = traceInfo?.bundledInProducts,
    )
  }

  return listOf(MissingDependenciesError(
    context = "Non-critical plugin modules (global validation)",
    missingModules = globallyMissingDeps,
    allModuleSets = allModuleSets,
    moduleMetadata = moduleMetadata,
    moduleTraceInfo = moduleTraceInfo, // Pass for missing dep lookup
  ))
}

/**
 * Finds missing transitive dependencies using BFS traversal.
 *
 * ## Performance Characteristics
 *
 * - **Module analysis is cached**: Uses [descriptorCache] for O(1) dependency lookups
 * - **BFS traversal stops at boundaries**: Does not traverse into cross-plugin/cross-product modules
 * - **Per-product context**: Result depends on [availableModules] and [allowedMissing]
 *
 * ## Traversal Logic
 *
 * For each dependency encountered:
 * 1. If in [availableModules] → valid, traverse into its dependencies
 * 2. If in [allowedMissing] → skip (explicitly allowed to be missing)
 * 3. If in [crossPluginModules] and source is NOT critical → valid, don't traverse (external responsibility)
 * 4. If in [crossProductModules] and source is NOT critical → valid, don't traverse (external responsibility)
 * 5. Otherwise → missing dependency error
 *
 * ## Why Same Module May Be Validated Multiple Times
 *
 * When the same module appears in multiple products, BFS runs once per product because:
 * - [availableModules] differs per product
 * - [allowedMissing] can differ per product (configured via `allowMissingDependencies`)
 * - A module might be "critical" in one product but not another
 *
 * For non-critical modules, the effective "available" set is global ([crossPluginModules] ∪ [crossProductModules]),
 * which could enable future optimization to pre-validate globally and reuse results.
 *
 * @param modules Modules to check dependencies for
 * @param availableModules Modules that are available within this product/context
 * @param descriptorCache Cache for module descriptor information (shared across all validations)
 * @param allowedMissing Dependencies explicitly allowed to be missing (per-product configuration)
 * @param crossPluginModules Modules from other plugins (valid for non-critical modules)
 * @param crossProductModules Modules from other products (valid for non-critical modules)
 * @param criticalModules Modules with EMBEDDED or REQUIRED loading that cannot depend on cross-plugin modules
 * @return Map of missing dependency → set of modules that need it
 */
internal suspend fun findMissingTransitiveDependencies(
  modules: Set<String>,
  availableModules: Set<String>,
  descriptorCache: ModuleDescriptorCache,
  allowedMissing: Set<String> = emptySet(),
  crossPluginModules: Set<String> = emptySet(),
  crossProductModules: Set<String> = emptySet(),
  criticalModules: Set<String> = emptySet(),
): Map<String, Set<String>> {
  val missingDeps = HashMap<String, MutableSet<String>>()

  for (moduleName in modules) {
    val info = descriptorCache.getOrAnalyze(moduleName) ?: continue
    val isCritical = criticalModules.contains(moduleName)
    val visited = HashSet<String>()
    visited.add(moduleName)
    val queue = ArrayDeque(info.dependencies)

    while (queue.isNotEmpty()) {
      val dep = queue.removeFirst()
      if (!visited.add(dep)) {
        continue
      }

      when {
        availableModules.contains(dep) -> {
          // Present - traverse into its deps
          descriptorCache.getOrAnalyze(dep)?.dependencies?.let { queue.addAll(it) }
        }
        allowedMissing.contains(dep) -> {
          // Explicitly allowed to be missing - skip
        }
        !isCritical && crossPluginModules.contains(dep) -> {
          // Valid cross-plugin optional dependency - skip
        }
        !isCritical && crossProductModules.contains(dep) -> {
          // Module exists in another product - valid for optional deps
        }
        else -> {
          // Missing dependency
          missingDeps.computeIfAbsent(dep) { HashSet() }.add(moduleName)
        }
      }
    }
  }

  return missingDeps
}

private data class ProductModuleIndex(
  @JvmField val productName: String,
  @JvmField val allModules: Set<String>,
  @JvmField val referencedModuleSets: Set<String>,
  @JvmField val moduleLoadings: Map<String, ModuleLoadingRuleValue?>,
  /** Map module name -> bundled plugin name that contributed it */
  @JvmField val moduleToSourcePlugin: Map<String, String> = emptyMap(),
  /** Map module name -> module set name that contributed it */
  @JvmField val moduleToSourceModuleSet: Map<String, String> = emptyMap(),
)

private suspend fun buildProductModuleIndex(
  productName: String,
  spec: ProductModulesContentSpec,
  cache: ModuleSetTraversalCache,
  pluginContentJobs: Map<String, Deferred<PluginContentInfo?>> = emptyMap(),
): ProductModuleIndex {
  val allModules = mutableSetOf<String>()
  val referencedModuleSets = mutableSetOf<String>()
  val moduleLoadings = mutableMapOf<String, ModuleLoadingRuleValue?>()
  val moduleToSourcePlugin = mutableMapOf<String, String>()
  val moduleToSourceModuleSet = mutableMapOf<String, String>()

  for (moduleSetWithOverrides in spec.moduleSets) {
    val moduleSet = moduleSetWithOverrides.moduleSet
    referencedModuleSets.add(moduleSet.name)
    // O(1) lookup from cache instead of re-traversal
    for ((moduleName, info) in cache.getModulesWithLoading(moduleSet)) {
      allModules.add(moduleName)
      moduleLoadings[moduleName] = info.loading
      moduleToSourceModuleSet[moduleName] = info.sourceModuleSet
    }
  }

  for (module in spec.additionalModules) {
    allModules.add(module.name)
    moduleLoadings[module.name] = module.loading
  }

  for (pluginName in spec.bundledPlugins) {
    val pluginInfo = pluginContentJobs[pluginName]?.await() ?: continue
    for (moduleName in pluginInfo.contentModules) {
      allModules.add(moduleName)
      moduleToSourcePlugin[moduleName] = pluginName
      // Get loading mode from plugin content info if available
      pluginInfo.contentModuleLoadings?.get(moduleName)?.let { moduleLoadings[moduleName] = it }
    }
  }

  return ProductModuleIndex(
    productName = productName,
    allModules = allModules,
    referencedModuleSets = referencedModuleSets,
    moduleLoadings = moduleLoadings,
    moduleToSourcePlugin = moduleToSourcePlugin,
    moduleToSourceModuleSet = moduleToSourceModuleSet,
  )
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

  // error.missingModules is already Map<missingDep, Set<needingModules>>
  for ((missingDep, needingModules) in error.missingModules.entries.sortedByDescending { it.value.size }) {
    // Show missing dep info with full traceability
    val missingDepInfo = error.moduleTraceInfo[missingDep]
    if (missingDepInfo != null) {
      sb.appendLine("  ${AnsiColors.RED}*${AnsiColors.RESET} Missing: ${AnsiColors.BOLD}'$missingDep'${AnsiColors.RESET}")
      sb.appendLine("    From plugin: ${missingDepInfo.sourcePlugin}")
      if (missingDepInfo.additionalPluginSource != null) {
        sb.appendLine("    Source: ${missingDepInfo.additionalPluginSource}")
      }
      else if (missingDepInfo.bundledInProducts.isNotEmpty()) {
        sb.appendLine("    Bundled in: ${missingDepInfo.bundledInProducts.sorted().joinToString(", ")}")
      }
    }
    else {
      sb.appendLine("  ${AnsiColors.RED}*${AnsiColors.RESET} Missing: ${AnsiColors.BOLD}'$missingDep'${AnsiColors.RESET} (not in any known plugin)")
    }

    // Show needing modules grouped by plugin as tree
    val byPlugin = needingModules.groupBy { error.moduleTraceInfo[it]?.sourcePlugin }
    sb.appendLine("    Needed by:")
    val pluginEntries = byPlugin.entries.sortedBy { it.key ?: "" }
    for ((pluginIdx, pluginEntry) in pluginEntries.withIndex()) {
      val (pluginName, modules) = pluginEntry
      val isLastPlugin = pluginIdx == pluginEntries.lastIndex
      val pluginPrefix = if (isLastPlugin) "└─" else "├─"
      val traceInfo = pluginName?.let { error.moduleTraceInfo[modules.first()] }

      // Plugin line with source info
      val sourceDesc = when {
        traceInfo?.bundledInProducts?.isNotEmpty() == true ->
          "bundled in: ${traceInfo.bundledInProducts.sorted().joinToString(", ")}"
        traceInfo?.additionalPluginSource != null -> traceInfo.additionalPluginSource
        else -> null
      }
      val pluginDesc = pluginName ?: "unknown"
      sb.appendLine("      $pluginPrefix ${AnsiColors.BOLD}$pluginDesc${AnsiColors.RESET}${sourceDesc?.let { " ($it)" } ?: ""}")

      // Module lines under plugin
      val sortedModules = modules.sorted()
      val childPrefix = if (isLastPlugin) "   " else "│  "
      for ((modIdx, mod) in sortedModules.withIndex()) {
        val modPrefix = if (modIdx == sortedModules.lastIndex) "└─" else "├─"
        sb.appendLine("      $childPrefix $modPrefix $mod")
      }
    }

    // Actionable suggestion based on missing dep status
    if (missingDepInfo != null && missingDepInfo.additionalPluginSource == null && missingDepInfo.bundledInProducts.isEmpty()) {
      // Plugin exists but not bundled anywhere - suggest adding to additionalPlugins
      sb.appendLine("    ${AnsiColors.BLUE}Fix:${AnsiColors.RESET} Add '${missingDepInfo.sourcePlugin}' to additionalPlugins in ultimateGenerator.kt")
    }
    else if (missingDepInfo == null) {
      // Not in any known plugin - check module sets
      val containingSets = error.allModuleSets
        .filter { ModuleSetTraversal.containsModule(it, missingDep) }
        .map { it.name }

      if (containingSets.isNotEmpty()) {
        sb.appendLine("    ${AnsiColors.BLUE}Fix:${AnsiColors.RESET} Add module set: ${containingSets.joinToString(" or ")}")
      }
      else {
        sb.appendLine("    ${AnsiColors.BLUE}Fix:${AnsiColors.RESET} Find plugin containing '$missingDep' and add to additionalPlugins")
      }
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