// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.productLayout.analysis.MissingDependenciesError
import org.jetbrains.intellij.build.productLayout.analysis.ValidationError
import org.jetbrains.intellij.build.productLayout.analysis.formatProductDependencyErrorsFooter
import org.jetbrains.intellij.build.productLayout.analysis.formatProductDependencyErrorsHeader
import org.jetbrains.intellij.build.productLayout.analysis.formatValidationErrors
import org.jetbrains.intellij.build.productLayout.analysis.validateProductModuleSets
import org.jetbrains.intellij.build.productLayout.analysis.validateSelfContainedModuleSets
import java.nio.file.Files
import java.nio.file.Files.readString
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

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
  moduleOutputProvider: ModuleOutputProvider,
  productSpecs: List<Pair<String, ProductModulesContentSpec?>> = emptyList(),
  pluginContentJobs: Map<String, Deferred<PluginContentInfo?>> = emptyMap(),
): DependencyGenerationResult = coroutineScope {
  val allModuleSets = communityModuleSets + coreModuleSets + ultimateModuleSets
  val modulesToProcess = collectModulesToProcess(allModuleSets)
  if (modulesToProcess.isEmpty()) {
    return@coroutineScope DependencyGenerationResult(emptyList())
  }

  val cache = ModuleDescriptorCache(moduleOutputProvider)

  // Collect all validation errors
  val errors = mutableListOf<ValidationError>()

  // Validate self-contained module sets in isolation
  errors.addAll(validateSelfContainedModuleSets(allModuleSets, cache))

  // Tier 2: Validate product-level dependencies
  errors.addAll(validateProductModuleSets(
    allModuleSets = allModuleSets,
    productSpecs = productSpecs,
    descriptorCache = cache,
    pluginContentJobs = pluginContentJobs,
  ))

  // Report all errors at once
  if (errors.isNotEmpty()) {
    val hasMissingDependencies = errors.any { it is MissingDependenciesError }
    error(buildString {
      if (hasMissingDependencies) {
        formatProductDependencyErrorsHeader(this)
      }
      append(formatValidationErrors(errors))
      if (hasMissingDependencies) {
        formatProductDependencyErrorsFooter(this)
      }
    })
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
 * Cache for module descriptor information to avoid redundant file system lookups.
 */
internal class ModuleDescriptorCache(private val moduleOutputProvider: ModuleOutputProvider) {
  data class DescriptorInfo(
    @JvmField val descriptorPath: Path,
    @JvmField val dependencies: List<String>,
  )

  // Wrapper to allow caching null results (ConcurrentHashMap doesn't support null values)
  private class CacheValue(@JvmField val info: DescriptorInfo?)

  private val cache = ConcurrentHashMap<String, CacheValue>()

  /**
   * Gets cached descriptor info or analyzes the module if not yet cached.
   * Caches both positive (has descriptor) and negative (no descriptor) results.
   * Thread-safe: ensures exactly one analysis per module using double-checked locking.
   */
  fun getOrAnalyze(moduleName: String): DescriptorInfo? {
    return (cache.get(moduleName) ?: synchronized(moduleName.intern()) {
      cache.getOrPut(moduleName) { CacheValue(analyzeModule(moduleName)) }
    }).info
  }

  /**
   * Analyzes a module to find its descriptor and production dependencies.
   */
  private fun analyzeModule(moduleName: String): DescriptorInfo? {
    val jpsModule = moduleOutputProvider.findRequiredModule(moduleName)
    val descriptorPath = findFileInModuleSources(
      module = jpsModule,
      relativePath = "$moduleName.xml",
      onlyProductionSources = true
    ) ?: return null

    // Skip modules with IJPL-210868 marker (not registered as content modules)
    if (shouldSkipDescriptor(descriptorPath)) {
      return null
    }

    val deps = mutableListOf<String>()
    for (dep in jpsModule.getProductionModuleDependencies(withTests = false)) {
      val depName = dep.moduleReference.moduleName
      if (hasDescriptor(depName)) {
        deps.add(depName)
      }
    }

    // Deduplicate MODULE DEPENDENCIES (not content modules!) before sorting.
    // Handles cases where the same dependency appears multiple times in JPS module graph.
    // Note: Content module duplicates are caught by validateProductModuleSets() during generation.
    return DescriptorInfo(descriptorPath, deps.distinct().sorted())
  }

  private fun shouldSkipDescriptor(descriptorPath: Path): Boolean {
    val content = Files.readString(descriptorPath)
    return content.contains("<!-- todo: register this as a content module (IJPL-210868)")
  }

  /**
   * Checks if a module has a descriptor XML file.
   */
  fun hasDescriptor(moduleName: String): Boolean = getOrAnalyze(moduleName) != null
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