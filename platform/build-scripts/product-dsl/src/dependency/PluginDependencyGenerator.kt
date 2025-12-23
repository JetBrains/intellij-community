// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.dependency

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.stats.DependencyFileResult
import org.jetbrains.intellij.build.productLayout.stats.PluginDependencyFileResult
import org.jetbrains.intellij.build.productLayout.stats.PluginDependencyGenerationResult
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
import org.jetbrains.intellij.build.productLayout.util.getProductionModuleDependencies
import org.jetbrains.intellij.build.productLayout.validation.PluginDependencyError
import org.jetbrains.intellij.build.productLayout.validation.ProductModuleIndex
import org.jetbrains.intellij.build.productLayout.validation.ValidationError
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import org.jetbrains.intellij.build.productLayout.xml.updateXmlDependencies

/**
 * Holds both the flat set of all plugin content modules and the per-plugin breakdown.
 * Computed once in ProductGeneration.kt and shared by multiple consumers to avoid duplicate iteration.
 */
internal data class AllPluginModules(
  /** All content modules from all plugins (flat set) */
  @JvmField val allModules: Set<String>,
  /** Content modules grouped by plugin name */
  @JvmField val byPlugin: Map<String, Set<String>>,
)

/**
 * Generates dependencies for **plugin content modules** (modules declared in plugin.xml `<content>` sections).
 *
 * This generator handles:
 * - `plugin.xml` files - the main plugin descriptor
 * - Content module descriptors (`{moduleName}.xml`) - production dependencies
 * - Test descriptors (`{moduleName}._test.xml`) - test dependencies with `withTests = true`
 *
 * For product modules (modules declared in module sets), see [generateModuleDescriptorDependencies].
 *
 * For each bundled plugin module:
 * 1. Uses pre-extracted content from shared jobs (path, content, JPS deps - avoids duplicate lookups)
 * 2. Filters JPS production dependencies to those with XML descriptors
 * 3. Updates the `<dependencies>` section with generated `<module name="..."/>` entries
 * 4. Processes content modules and their test descriptors
 * 5. **Validates** that generated dependencies can be resolved in products that bundle this plugin
 *
 *        Multiple consumers can await the same Deferred - extraction runs only once per plugin.
 */
internal fun isTestPlugin(contentModules: Set<String>, testFrameworkContentModules: Set<String>): Boolean {
  return testFrameworkContentModules.isNotEmpty() && contentModules.any { it in testFrameworkContentModules }
}

/**
 * Computes content modules from production plugins only (excluding test plugins).
 * Used by validation to determine which modules are available at runtime.
 *
 * @param pluginContentByPlugin Map of plugin name -> content modules for ALL plugins (test + production)
 * @param testFrameworkContentModules Modules that indicate a plugin is a test plugin when declared as content
 * @return Set of all content modules from production plugins
 */
internal fun computeProductionPluginModules(
  pluginContentByPlugin: Map<String, Set<String>>,
  testFrameworkContentModules: Set<String>,
): Set<String> {
  val result = HashSet<String>()
  for ((_, contentModules) in pluginContentByPlugin) {
    if (!isTestPlugin(contentModules, testFrameworkContentModules)) {
      result.addAll(contentModules)
    }
  }
  return result
}

/**
 * Finds module dependencies that cannot be resolved at runtime.
 * A dependency is "missing" if it's not in crossProductModules AND not in productionPluginModules.
 *
 * @param depsToValidate Set of module dependencies to validate
 * @param crossProductModules Modules available from module sets (always present at runtime)
 * @param productionPluginModules Content modules from production plugins (available at runtime)
 * @return Set of missing dependencies
 */
internal fun findMissingDependencies(
  depsToValidate: Set<String>,
  crossProductModules: Set<String>,
  productionPluginModules: Set<String>,
): Set<String> {
  return depsToValidate.filterTo(HashSet()) { dep ->
    dep !in crossProductModules && dep !in productionPluginModules
  }
}

internal suspend fun generatePluginDependencies(
  plugins: List<String>,
  pluginContentJobs: Map<String, Deferred<PluginContentInfo?>>,
  allPluginModulesDeferred: Deferred<AllPluginModules>,
  productIndicesDeferred: Deferred<Map<String, ProductModuleIndex>>,
  descriptorCache: ModuleDescriptorCache,
  dependencyFilter: (moduleName: String, depName: String, isTest: Boolean) -> Boolean,
  strategy: FileUpdateStrategy,
  testFrameworkContentModules: Set<String>,
  pluginAllowedMissingDependencies: Map<String, Set<String>> = emptyMap(),
): PluginDependencyGenerationResult = coroutineScope {
  if (plugins.isEmpty()) {
    return@coroutineScope PluginDependencyGenerationResult(emptyList())
  }

  // Await shared product indices (computed once in ProductGeneration.kt)
  val productIndices = productIndicesDeferred.await()

  // Compute union of ALL product modules - used for global validation
  val crossProductModules = productIndices.values.flatMapTo(HashSet()) { it.allModules }

  // Cache content module results - same module can be declared as <content> in multiple plugins,
  // process each once and reuse the result
  val contentModuleCache = AsyncCache<String, DependencyFileResult?>(this)
  val testContentModuleCache = AsyncCache<String, DependencyFileResult?>(this)

  // Generate deps and validate in parallel
  val generationResults = plugins.map { pluginModuleName ->
    async {
      generatePluginDependency(
        pluginModuleName = pluginModuleName,
        pluginContentJobs = pluginContentJobs,
        descriptorCache = descriptorCache,
        dependencyFilter = dependencyFilter,
        strategy = strategy,
        contentModuleCache = contentModuleCache,
        testContentModuleCache = testContentModuleCache,
      )
    }
  }.awaitAll().filterNotNull()

  // Validate generated dependencies for ALL plugins against available modules
  // A plugin's <dependencies>/<module> entries MUST be resolvable at runtime.
  // They can be satisfied by:
  // 1. Modules in crossProductModules (from module sets - always available)
  // 2. Content modules of PRODUCTION plugins (not test plugins which won't be present at runtime)
  val errors = mutableListOf<ValidationError>()

  // Use pre-computed plugin content from shared Deferred (computed once in ProductGeneration.kt)
  // Test plugins are detected by their content: if they declare test framework modules
  // (junit, testFramework, etc.) as content, they're test plugins
  val pluginContentByPlugin = allPluginModulesDeferred.await().byPlugin

  // Use extracted function to compute production plugin modules
  val productionPluginModules = computeProductionPluginModules(pluginContentByPlugin, testFrameworkContentModules)

  for (result in generationResults) {
    val pluginName = result.pluginModuleName

    // Get the plugin's JPS deps that were written to plugin.xml (same filtering as generation)
    val pluginInfo = pluginContentJobs.get(pluginName)?.await() ?: continue
    val generatedDeps = filterPluginDependencies(pluginInfo = pluginInfo, pluginModuleName = pluginName, dependencyFilter = dependencyFilter, descriptorCache = descriptorCache)

    // Validate ALL module dependencies - both generated AND existing ones from plugin.xml
    // This catches dependencies that exist in plugin.xml but wouldn't be generated anymore
    val existingModuleDeps = pluginInfo.moduleDependencies
    val allDepsToValidate = generatedDeps.toSet() + existingModuleDeps

    // Use extracted function to find missing dependencies
    val missing = findMissingDependencies(allDepsToValidate, crossProductModules, productionPluginModules)
    // Filter out explicitly allowed missing dependencies for this plugin
    val allowedForPlugin = pluginAllowedMissingDependencies[pluginName] ?: emptySet()
    val actualMissing = missing - allowedForPlugin
    if (actualMissing.isNotEmpty()) {
      val missingDeps = actualMissing.associateWithTo(HashMap()) {
        mutableSetOf("(not in any product module set or production plugin content)")
      }
      errors.add(PluginDependencyError(
        context = pluginName,
        pluginName = pluginName,
        missingDependencies = missingDeps,
      ))
    }
  }

  PluginDependencyGenerationResult(generationResults, errors)
}



/**
 * Filters plugin JPS dependencies: excludes content modules, applies filter, requires descriptor.
 * Used by both generation and validation to ensure consistency.
 */
private suspend fun filterPluginDependencies(
  pluginInfo: PluginContentInfo,
  pluginModuleName: String,
  dependencyFilter: (moduleName: String, depName: String, isTest: Boolean) -> Boolean,
  descriptorCache: ModuleDescriptorCache,
): List<String> {
  return pluginInfo.jpsDependencies()
    .filter { depName ->
      depName !in pluginInfo.contentModules &&
      dependencyFilter(pluginModuleName, depName, false) &&
      descriptorCache.hasDescriptor(depName)
    }
    .distinct()
    .sorted()
}

/**
 * Generates dependencies for a single plugin module.
 *
 * @param pluginContentJobs Pre-launched async jobs containing all plugin info.
 * @param contentModuleCache Cache for production content module results (shared across plugins)
 * @param testContentModuleCache Cache for test content module results (shared across plugins)
 * @return PluginDependencyFileResult or null if plugin.xml not found or has module refs with '/'
 */
private suspend fun generatePluginDependency(
  pluginModuleName: String,
  pluginContentJobs: Map<String, Deferred<PluginContentInfo?>>,
  descriptorCache: ModuleDescriptorCache,
  dependencyFilter: (moduleName: String, depName: String, isTest: Boolean) -> Boolean,
  strategy: FileUpdateStrategy,
  contentModuleCache: AsyncCache<String, DependencyFileResult?>,
  testContentModuleCache: AsyncCache<String, DependencyFileResult?>,
): PluginDependencyFileResult? {
  // All data from shared jobs - NO additional lookups needed
  val info = pluginContentJobs.get(pluginModuleName)?.await() ?: return null

  val dependencies = filterPluginDependencies(info, pluginModuleName, dependencyFilter, descriptorCache)

  val status = updateXmlDependencies(
    path = info.pluginXmlPath,
    content = info.pluginXmlContent,
    moduleDependencies = dependencies,
    preserveExistingModule = { !dependencyFilter(pluginModuleName, it, false) },
    strategy = strategy,
  )

  // Process content modules - use cache to avoid duplicate processing
  // (same module can be declared as <content> in multiple plugins)
  val contentModuleResults = mutableListOf<DependencyFileResult>()
  for (contentModuleName in info.contentModules) {
    // Content modules ending with ._test are test modules themselves - their descriptor IS the test descriptor
    val isTestModule = contentModuleName.endsWith("._test")

    // Production descriptor - cached to avoid duplicate processing across plugins
    val prodResult = contentModuleCache.getOrPut(contentModuleName) {
      generateContentModuleDependencies(
        contentModuleName = contentModuleName,
        descriptorCache = descriptorCache,
        // For ._test content modules, use test filter since their .xml IS the test descriptor
        dependencyFilter = { dependencyFilter(contentModuleName, it, isTestModule) },
        strategy = strategy,
      )
    }
    if (prodResult != null) {
      contentModuleResults.add(prodResult)
    }

    // For non-test content modules, also process their test descriptor if it exists
    if (!isTestModule) {
      val testResult = testContentModuleCache.getOrPut(contentModuleName) {
        generateTestContentModuleDependencies(
          contentModuleName = contentModuleName,
          descriptorCache = descriptorCache,
          dependencyFilter = { dependencyFilter(contentModuleName, it, true) },
          strategy = strategy,
        )
      }
      if (testResult != null) {
        contentModuleResults.add(testResult)
      }
    }
  }

  return PluginDependencyFileResult(
    pluginModuleName = pluginModuleName,
    pluginXmlPath = info.pluginXmlPath,
    status = status,
    dependencyCount = dependencies.size,
    contentModuleResults = contentModuleResults,
  )
}

/**
 * Generates dependencies for a content module's production descriptor file (`{moduleName}.xml`).
 *
 * @return DependencyFileResult or null if module has no descriptor
 */
private suspend fun generateContentModuleDependencies(
  contentModuleName: String,
  descriptorCache: ModuleDescriptorCache,
  dependencyFilter: (String) -> Boolean,
  strategy: FileUpdateStrategy,
): DependencyFileResult? {
  val info = descriptorCache.getOrAnalyze(contentModuleName) ?: return null
  val filteredDeps = info.dependencies.filter(dependencyFilter)
  val status = updateXmlDependencies(path = info.descriptorPath, content = info.content, moduleDependencies = filteredDeps, preserveExistingModule = { !dependencyFilter(it) }, strategy = strategy)
  return DependencyFileResult(moduleName = contentModuleName, descriptorPath = info.descriptorPath, status = status, dependencyCount = filteredDeps.size)
}

/**
 * Generates dependencies for a content module's test descriptor file (`{moduleName}._test.xml`).
 *
 * Test descriptors are located in test resources and include test-scope dependencies.
 *
 * @return DependencyFileResult or null if module has no test descriptor
 */
private suspend fun generateTestContentModuleDependencies(
  contentModuleName: String,
  descriptorCache: ModuleDescriptorCache,
  dependencyFilter: (String) -> Boolean,
  strategy: FileUpdateStrategy,
): DependencyFileResult? {
  val outputProvider = descriptorCache.outputProviderRef
  val jpsModule = outputProvider.findRequiredModule(contentModuleName)
  val descriptorPath = findFileInModuleSources(
    module = jpsModule,
    relativePath = "$contentModuleName._test.xml",
    onlyProductionSources = false,
  ) ?: return null

  val content = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { java.nio.file.Files.readString(descriptorPath) }

  // Get dependencies with test scope included
  val deps = mutableListOf<String>()
  for (dep in jpsModule.getProductionModuleDependencies(withTests = true)) {
    val depName = dep.moduleReference.moduleName
    if (dependencyFilter(depName) && descriptorCache.hasDescriptor(depName)) {
      deps.add(depName)
    }
  }
  val filteredDeps = deps.distinct().sorted()

  val status = updateXmlDependencies(
    path = descriptorPath,
    content = content,
    moduleDependencies = filteredDeps,
    preserveExistingModule = { !dependencyFilter(it) },
    strategy = strategy,
  )
  return DependencyFileResult(
    moduleName = "$contentModuleName._test",
    descriptorPath = descriptorPath,
    status = status,
    dependencyCount = filteredDeps.size,
  )
}
