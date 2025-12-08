// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.discovery

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.util.getProductionModuleDependencies
import org.jetbrains.intellij.build.productLayout.xml.extractContentModulesFromText
import java.nio.file.Files
import java.nio.file.Path

/**
 * Result of extracting plugin content from plugin.xml.
 * Contains everything needed for both validation and dependency generation.
 */
internal data class PluginContentInfo(
  @JvmField val pluginXmlPath: Path,
  @JvmField val pluginXmlContent: String,
  @JvmField val contentModules: Set<String>,
  /** Lazy JPS production dependencies - only called by plugin dep gen, not validation */
  @JvmField val jpsDependencies: () -> List<String>,
)

/**
 * Extracts content modules from a plugin's plugin.xml.
 * Returns null if plugin.xml not found or has module references with '/'.
 */
internal suspend fun extractPluginContent(
  pluginName: String,
  moduleOutputProvider: ModuleOutputProvider,
): PluginContentInfo? {
  val jpsModule = moduleOutputProvider.findModule(pluginName) ?: return null
  val pluginXmlPath = findFileInModuleSources(module = jpsModule, relativePath = PLUGIN_XML_RELATIVE_PATH, onlyProductionSources = true) ?: return null
  val content = withContext(Dispatchers.IO) { Files.readString(pluginXmlPath) }
  val contentModules = extractContentModulesFromText(content) ?: return null
  return PluginContentInfo(
    pluginXmlPath = pluginXmlPath,
    pluginXmlContent = content,
    contentModules = contentModules,
    jpsDependencies = { jpsModule.getProductionModuleDependencies(withTests = false).map { it.moduleReference.moduleName }.toList() },
  )
}

/**
 * Collects all embedded modules from all product specs.
 * Used to filter out embedded platform modules from plugin dependencies.
 */
internal fun collectEmbeddedModulesFromProducts(products: List<DiscoveredProduct>): Set<String> {
  val result = HashSet<String>()
  for (discovered in products) {
    val spec = discovered.spec ?: continue
    for (moduleSetWithOverrides in spec.moduleSets) {
      collectEmbeddedModules(moduleSetWithOverrides.moduleSet, result)
    }
    for (module in spec.additionalModules) {
      if (module.loading == ModuleLoadingRuleValue.EMBEDDED) {
        result.add(module.name)
      }
    }
  }
  return result
}

private fun collectEmbeddedModules(moduleSet: ModuleSet, result: MutableSet<String>) {
  for (module in moduleSet.modules) {
    if (module.loading == ModuleLoadingRuleValue.EMBEDDED) {
      result.add(module.name)
    }
  }
  for (nestedSet in moduleSet.nestedSets) {
    collectEmbeddedModules(nestedSet, result)
  }
}
