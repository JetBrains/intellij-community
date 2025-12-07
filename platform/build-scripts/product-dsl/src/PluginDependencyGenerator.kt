// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.findFileInModuleSources
import java.nio.file.Files

/**
 * Generates dependencies for bundled plugin `plugin.xml` files.
 * Uses same logic as ModuleDescriptorDependencyGenerator but for plugin.xml files.
 *
 * For each bundled plugin module:
 * 1. Finds META-INF/plugin.xml in module sources
 * 2. Gets JPS production dependencies that have XML descriptors (content modules)
 * 3. Updates the `<dependencies>` section with generated `<module name="..."/>` entries
 */
internal suspend fun generatePluginDependencies(
  plugins: List<String>,
  moduleOutputProvider: ModuleOutputProvider,
  descriptorCache: ModuleDescriptorCache,
  dependencyFilter: (String) -> Boolean,
): PluginDependencyGenerationResult = coroutineScope {
  if (plugins.isEmpty()) {
    return@coroutineScope PluginDependencyGenerationResult(emptyList())
  }

  val results = plugins.map { pluginModuleName ->
    async {
      generatePluginDependency(
        pluginModuleName = pluginModuleName,
        moduleOutputProvider = moduleOutputProvider,
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
 * @return PluginDependencyFileResult or null if plugin.xml not found
 */
private fun generatePluginDependency(
  pluginModuleName: String,
  moduleOutputProvider: ModuleOutputProvider,
  descriptorCache: ModuleDescriptorCache,
  dependencyFilter: (String) -> Boolean,
): PluginDependencyFileResult? {
  val jpsModule = moduleOutputProvider.findModule(pluginModuleName) ?: return null

  val pluginXmlPath = findFileInModuleSources(module = jpsModule, relativePath = PLUGIN_XML_RELATIVE_PATH, onlyProductionSources = true) ?: return null

  // Read file once and extract content modules (these should be excluded from dependencies)
  val pluginXmlContent = Files.readString(pluginXmlPath)
  // null means plugin has content module references (modules with '/'), skip it
  val contentModules = extractContentModulesFromText(pluginXmlContent) ?: return null

  // Get JPS dependencies that have XML descriptors (content modules)
  // Skip if:
  // 1. Module is in <content> section of this plugin.xml
  // 2. Module is filtered out by dependencyFilter
  // 3. Module doesn't have a descriptor
  val deps = mutableListOf<String>()
  for (dep in jpsModule.getProductionModuleDependencies(withTests = false)) {
    val depName = dep.moduleReference.moduleName
    if (depName in contentModules) {
      continue
    }
    if (!dependencyFilter(depName)) {
      continue
    }
    if (!descriptorCache.hasDescriptor(depName)) {
      continue
    }
    deps.add(depName)
  }

  val dependencies = deps.distinct().sorted()
  val status = updateXmlDependencies(path = pluginXmlPath, content = pluginXmlContent, moduleDependencies = dependencies, preserveExistingModule = { !dependencyFilter(it) })

  // Also process content modules - generate dependencies for their module descriptors
  val contentModuleResults = mutableListOf<DependencyFileResult>()
  for (contentModuleName in contentModules) {
    val result = generateContentModuleDependencies(contentModuleName = contentModuleName, descriptorCache = descriptorCache, dependencyFilter = dependencyFilter)
    if (result != null) {
      contentModuleResults.add(result)
    }
  }

  return PluginDependencyFileResult(
    pluginModuleName = pluginModuleName,
    pluginXmlPath = pluginXmlPath,
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
private fun generateContentModuleDependencies(
  contentModuleName: String,
  descriptorCache: ModuleDescriptorCache,
  dependencyFilter: (String) -> Boolean,
): DependencyFileResult? {
  val info = descriptorCache.getOrAnalyze(contentModuleName) ?: return null
  val filteredDeps = info.dependencies.filter(dependencyFilter)
  val status = updateXmlDependencies(
    path = info.descriptorPath,
    content = Files.readString(info.descriptorPath),
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
