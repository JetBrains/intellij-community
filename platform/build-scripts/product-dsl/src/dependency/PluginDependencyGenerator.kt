// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.dependency

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.stats.DependencyFileResult
import org.jetbrains.intellij.build.productLayout.stats.PluginDependencyFileResult
import org.jetbrains.intellij.build.productLayout.stats.PluginDependencyGenerationResult
import org.jetbrains.intellij.build.productLayout.xml.updateXmlDependencies

/**
 * Generates dependencies for bundled plugin `plugin.xml` files.
 * Uses same logic as ModuleDescriptorDependencyGenerator but for plugin.xml files.
 *
 * For each bundled plugin module:
 * 1. Uses pre-extracted content from shared jobs (path, content, JPS deps - avoids duplicate lookups)
 * 2. Filters JPS production dependencies to those with XML descriptors
 * 3. Updates the `<dependencies>` section with generated `<module name="..."/>` entries
 *
 * @param pluginContentJobs Pre-launched async jobs containing all plugin info.
 *        Multiple consumers can await the same Deferred - extraction runs only once per plugin.
 */
internal suspend fun generatePluginDependencies(
  plugins: List<String>,
  pluginContentJobs: Map<String, Deferred<PluginContentInfo?>>,
  descriptorCache: ModuleDescriptorCache,
  dependencyFilter: (moduleName: String, depName: String) -> Boolean,
): PluginDependencyGenerationResult = coroutineScope {
  if (plugins.isEmpty()) {
    return@coroutineScope PluginDependencyGenerationResult(emptyList())
  }

  val results = plugins.map { pluginModuleName ->
    async {
      generatePluginDependency(
        pluginModuleName = pluginModuleName,
        pluginContentJobs = pluginContentJobs,
        descriptorCache = descriptorCache,
        dependencyFilter = dependencyFilter,
      )
    }
  }.awaitAll().filterNotNull()

  PluginDependencyGenerationResult(results)
}

/**
 * Generates dependencies for a single plugin module.
 *
 * @param pluginContentJobs Pre-launched async jobs containing all plugin info.
 * @return PluginDependencyFileResult or null if plugin.xml not found or has module refs with '/'
 */
private suspend fun generatePluginDependency(
  pluginModuleName: String,
  pluginContentJobs: Map<String, Deferred<PluginContentInfo?>>,
  descriptorCache: ModuleDescriptorCache,
  dependencyFilter: (moduleName: String, depName: String) -> Boolean,
): PluginDependencyFileResult? {
  // All data from shared jobs - NO additional lookups needed
  val info = pluginContentJobs.get(pluginModuleName)?.await() ?: return null

  // Filter JPS dependencies: exclude content modules, apply filter, require descriptor
  val dependencies = info.jpsDependencies()
    .filter { depName ->
      depName !in info.contentModules &&
      dependencyFilter(pluginModuleName, depName) &&
      descriptorCache.hasDescriptor(depName)
    }
    .distinct()
    .sorted()

  val status = updateXmlDependencies(
    path = info.pluginXmlPath,
    content = info.pluginXmlContent,
    moduleDependencies = dependencies,
    preserveExistingModule = { !dependencyFilter(pluginModuleName, it) },
  )

  // Also process content modules - generate dependencies for their module descriptors
  val contentModuleResults = mutableListOf<DependencyFileResult>()
  for (contentModuleName in info.contentModules) {
    val result = generateContentModuleDependencies(contentModuleName = contentModuleName, descriptorCache = descriptorCache, dependencyFilter = { dependencyFilter(contentModuleName, it) })
    if (result != null) {
      contentModuleResults.add(result)
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
 * Generates dependencies for a content module's descriptor file.
 *
 * @return DependencyFileResult or null if module has no descriptor
 */
private suspend fun generateContentModuleDependencies(
  contentModuleName: String,
  descriptorCache: ModuleDescriptorCache,
  dependencyFilter: (String) -> Boolean,
): DependencyFileResult? {
  val info = descriptorCache.getOrAnalyze(contentModuleName) ?: return null
  val filteredDeps = info.dependencies.filter(dependencyFilter)
  val status = updateXmlDependencies(
    path = info.descriptorPath,
    content = info.content,
    moduleDependencies = filteredDeps,
    preserveExistingModule = { !dependencyFilter(it) },
  )
  return DependencyFileResult(
    moduleName = contentModuleName,
    descriptorPath = info.descriptorPath,
    status = status,
    dependencyCount = filteredDeps.size
  )
}
