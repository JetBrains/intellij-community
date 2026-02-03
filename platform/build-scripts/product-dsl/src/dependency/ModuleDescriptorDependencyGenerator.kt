// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.dependency

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.stats.DependencyFileResult
import org.jetbrains.intellij.build.productLayout.stats.DependencyGenerationResult
import org.jetbrains.intellij.build.productLayout.traversal.ModuleSetTraversalCache
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
import org.jetbrains.intellij.build.productLayout.validation.ProductModuleIndex
import org.jetbrains.intellij.build.productLayout.validation.ValidationError
import org.jetbrains.intellij.build.productLayout.validation.rules.validateLibraryModuleDependencies
import org.jetbrains.intellij.build.productLayout.validation.rules.validateProductModuleSets
import org.jetbrains.intellij.build.productLayout.validation.rules.validateSelfContainedModuleSets
import org.jetbrains.intellij.build.productLayout.validation.rules.validateTestLibraryScopes
import org.jetbrains.intellij.build.productLayout.visitAllModules
import org.jetbrains.intellij.build.productLayout.xml.updateXmlDependencies

/**
 * Marker comment to skip dependency generation for a module.
 * Add `<!-- @skip-dependency-generation -->` to a module descriptor XML file to prevent
 * automatic dependency generation and preserve manually managed dependencies.
 *
 * Use cases:
 * - Dependencies requiring specific topological sort ordering
 * - Modules with complex dependency requirements not expressible via JPS
 */
private const val SKIP_DEPENDENCY_GENERATION_MARKER = "@skip-dependency-generation"

/**
 * Generates module descriptor dependencies for **product modules** (modules declared in module sets).
 *
 * This generator handles modules that are part of the product's module set hierarchy (e.g., `corePlatform()`, `ideCommon()`).
 * For plugin content modules (modules declared in plugin.xml `<content>` sections), see [generatePluginDependencies].
 *
 * **Note on Filtering:**
 * Unlike [generatePluginDependencies], this generator does NOT apply `dependencyFilter` from `ModuleSetGenerationConfig`.
 * All JPS dependencies with descriptors are included automatically.
 * To skip dependency generation for a module entirely (e.g., for manual dependency management),
 * add `<!-- @skip-dependency-generation -->` comment to the module descriptor XML file.
 *
 * **Validation Strategy:**
 * This function not only generates dependencies but also validates that all dependencies
 * (both direct and transitive) are reachable within the module set hierarchy.
 *
 * **Why Transitive Validation Matters:**
 * Before this fix, only direct dependencies were validated. This caused runtime failures when:
 * - Module A depends on Module B (validated ✓)
 * - Module B depends on Module C (NOT validated ✗)
 * - Module C is missing from the module set
 * - At runtime: Module A loads → Module B loads → Module C fails!
 *
 * **Example Bug Fixed:**
 * - `intellij.platform.kernel` (in `corePlatform()`) depends on `fleet.kernel`
 * - But `corePlatform()` doesn't include `fleet()` module set
 * - Modules in `corePlatform()` with `includeDependencies=true` would generate descriptors
 *   listing `intellij.platform.kernel` as a dependency
 * - At runtime, `intellij.platform.kernel` would fail to load because `fleet.kernel` is missing
 * - OLD: No validation error (only checked direct dependencies)
 * - NEW: Validation catches the missing `fleet.kernel` transitive dependency
 */
internal suspend fun generateModuleDescriptorDependencies(
  communityModuleSets: List<ModuleSet>,
  ultimateModuleSets: List<ModuleSet>,
  coreModuleSets: List<ModuleSet> = emptyList(),
  cache: ModuleDescriptorCache,
  productSpecs: List<Pair<String, ProductModulesContentSpec?>> = emptyList(),
  pluginContentJobs: Map<String, Deferred<PluginContentInfo?>> = emptyMap(),
  allPluginModulesDeferred: Deferred<AllPluginModules>,
  productIndicesDeferred: Deferred<Map<String, ProductModuleIndex>>,
  nonBundledPlugins: Map<String, Set<String>> = emptyMap(),
  knownPlugins: Set<String> = emptySet(),
  testingLibraries: Set<String> = emptySet(),
  libraryModuleFilter: (libraryModuleName: String) -> Boolean = { true },
  strategy: FileUpdateStrategy,
): DependencyGenerationResult = coroutineScope {
  val allModuleSets = communityModuleSets + coreModuleSets + ultimateModuleSets
  val modulesToProcess = collectModulesToProcess(allModuleSets)
  if (modulesToProcess.isEmpty()) {
    return@coroutineScope DependencyGenerationResult(emptyList())
  }

  // Create traversal cache for O(1) lookups
  val traversalCache = ModuleSetTraversalCache(allModuleSets)

  // Await all plugin content jobs once (collect xi:include errors)
  val pluginContent = pluginContentJobs.mapValues { it.value.await() }

  // Run all validations in PARALLEL
  val selfContainedJob = async {
    validateSelfContainedModuleSets(allModuleSets, cache, traversalCache)
  }
  val productJob = async {
    validateProductModuleSets(
      allModuleSets = allModuleSets,
      productSpecs = productSpecs,
      descriptorCache = cache,
      cache = traversalCache,
      pluginContentJobs = pluginContentJobs,
      allPluginModulesDeferred = allPluginModulesDeferred,
      productIndicesDeferred = productIndicesDeferred,
      nonBundledPlugins = nonBundledPlugins,
      knownPlugins = knownPlugins,
    )
  }
  // Include plugin modules in library validation (fixes missing validation for bundled plugins)
  // allPluginModulesDeferred contains content modules; pluginContentJobs.keys are the plugin modules themselves
  val allModuleNames = traversalCache.getAllModuleNames() + allPluginModulesDeferred.await().allModules + pluginContentJobs.keys
  val libraryJob = async {
    validateLibraryModuleDependencies(
      modulesToCheck = allModuleNames,
      outputProvider = cache.outputProviderRef,
      strategy = strategy,
      libraryModuleFilter = libraryModuleFilter,
    )
  }
  val testLibraryScopeJob = async {
    validateTestLibraryScopes(
      modulesToCheck = allModuleNames,
      testingLibraries = testingLibraries,
      outputProvider = cache.outputProviderRef,
    )
  }

  // Collect all validation errors
  val errors = mutableListOf<ValidationError>()
  errors.addAll(selfContainedJob.await())
  errors.addAll(productJob.await())
  errors.addAll(pluginContent.values.flatMap { it?.xIncludeErrors ?: emptyList() })
  
  // Library module violations are auto-applied through the strategy
  libraryJob.await()
  
  // Test library scope violations remain as proposed diffs (shown but not auto-applied)
  val testLibraryScopeDiffs = testLibraryScopeJob.await()

  // Write XML files in parallel
  val results = modulesToProcess.map { moduleName ->
    async {
      val info = cache.getOrAnalyze(moduleName) ?: return@async null

      // Skip modules with manual dependency management
      if (info.content.contains(SKIP_DEPENDENCY_GENERATION_MARKER)) {
        return@async null
      }

      val status = updateXmlDependencies(path = info.descriptorPath, content = info.content, moduleDependencies = info.dependencies, strategy = strategy)
      DependencyFileResult(
        moduleName = moduleName,
        descriptorPath = info.descriptorPath,
        status = status,
        dependencyCount = info.dependencies.size,
      )
    }
  }.awaitAll().filterNotNull()

  DependencyGenerationResult(files = results, errors = errors, diffs = testLibraryScopeDiffs)
}

/**
 * Collects modules that need dependency generation:
 * 1. Modules with includeDependencies=true
 * 2. Library modules (intellij.libraries.*) that are not embedded
 */
private fun collectModulesToProcess(moduleSets: List<ModuleSet>): Set<String> {
  val result = LinkedHashSet<String>()
  for (set in moduleSets) {
    visitAllModules(set) { module ->
      // todo enable for all on the next stage
      if (module.includeDependencies ||
          module.name.startsWith(LIB_MODULE_PREFIX) ||
          module.name.startsWith("intellij.platform.settings.")) {
        result.add(module.name)
      }
    }
  }
  return result
}
