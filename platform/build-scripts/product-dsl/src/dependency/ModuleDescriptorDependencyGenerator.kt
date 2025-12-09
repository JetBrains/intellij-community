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
import org.jetbrains.intellij.build.productLayout.analysis.MissingDependenciesError
import org.jetbrains.intellij.build.productLayout.analysis.ModuleSetTraversalCache
import org.jetbrains.intellij.build.productLayout.analysis.ValidationError
import org.jetbrains.intellij.build.productLayout.analysis.formatProductDependencyErrorsFooter
import org.jetbrains.intellij.build.productLayout.analysis.formatProductDependencyErrorsHeader
import org.jetbrains.intellij.build.productLayout.analysis.formatValidationErrors
import org.jetbrains.intellij.build.productLayout.analysis.validateProductModuleSets
import org.jetbrains.intellij.build.productLayout.analysis.validateSelfContainedModuleSets
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.stats.DependencyFileResult
import org.jetbrains.intellij.build.productLayout.stats.DependencyGenerationResult
import org.jetbrains.intellij.build.productLayout.visitAllModules
import org.jetbrains.intellij.build.productLayout.xml.updateXmlDependencies
import java.nio.file.Files.readString
import kotlin.system.exitProcess

/**
 * Generates module descriptor dependencies for all modules with includeDependencies=true.
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
  additionalPlugins: Map<String, String> = emptyMap(),
): DependencyGenerationResult = coroutineScope {
  val allModuleSets = communityModuleSets + coreModuleSets + ultimateModuleSets
  val modulesToProcess = collectModulesToProcess(allModuleSets)
  if (modulesToProcess.isEmpty()) {
    return@coroutineScope DependencyGenerationResult(emptyList())
  }

  // Create traversal cache for O(1) lookups
  val traversalCache = ModuleSetTraversalCache(allModuleSets)

  // Collect all validation errors
  val errors = mutableListOf<ValidationError>()

  // Validate self-contained module sets in isolation
  errors.addAll(validateSelfContainedModuleSets(allModuleSets, cache, traversalCache))

  // Tier 2: Validate product-level dependencies
  errors.addAll(validateProductModuleSets(
    allModuleSets = allModuleSets,
    productSpecs = productSpecs,
    descriptorCache = cache,
    cache = traversalCache,
    pluginContentJobs = pluginContentJobs,
    additionalPlugins = additionalPlugins,
  ))

  // Report all errors at once
  if (errors.isNotEmpty()) {
    val hasMissingDependencies = errors.any { it is MissingDependenciesError }
    System.err.println(buildString {
      if (hasMissingDependencies) {
        formatProductDependencyErrorsHeader(this)
      }
      append(formatValidationErrors(errors))
      if (hasMissingDependencies) {
        formatProductDependencyErrorsFooter(this)
      }
    })
    exitProcess(1)
  }

  // Write XML files in parallel
  val results = modulesToProcess.map { moduleName ->
    async {
      val info = cache.getOrAnalyze(moduleName) ?: return@async null
      val status = updateXmlDependencies(path = info.descriptorPath, content = readString(info.descriptorPath), moduleDependencies = info.dependencies)
      DependencyFileResult(
        moduleName = moduleName,
        descriptorPath = info.descriptorPath,
        status = status,
        dependencyCount = info.dependencies.size,
      )
    }
  }.awaitAll().filterNotNull()

  DependencyGenerationResult(results)
}

/**
 * Collects modules that need dependency generation:
 * 1. Modules with includeDependencies=true
 * 2. Library modules (intellij.libraries.*) that are not embedded
 */
private fun collectModulesToProcess(moduleSets: List<ModuleSet>): Set<String> {
  val result = mutableSetOf<String>()
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
