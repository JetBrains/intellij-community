// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.discovery

import com.intellij.platform.plugins.parser.impl.LoadedXIncludeReference
import com.intellij.platform.plugins.parser.impl.elements.ContentModuleElement
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.plugins.parser.impl.parseContentAndXIncludes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.findFileInModuleDependencies
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import org.jetbrains.intellij.build.productLayout.util.getProductionModuleDependencies
import org.jetbrains.jps.model.module.JpsModule
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
  /** Map of content module name -> loading mode (null if not specified) */
  @JvmField val contentModuleLoadings: Map<String, ModuleLoadingRuleValue?>? = null,
  /** Lazy JPS production dependencies - only called by plugin dep gen, not validation */
  @JvmField val jpsDependencies: () -> List<String>,
)

/**
 * Extracts content modules from a plugin's plugin.xml.
 * Returns null if plugin.xml not found or has module references with '/'.
 *
 * Uses BFS traversal with concurrent xi:include resolution at each level for optimal I/O performance.
 */
internal suspend fun extractPluginContent(
  pluginName: String,
  moduleOutputProvider: ModuleOutputProvider,
  xIncludeCache: AsyncCache<String, LoadedXIncludeReference?>,
): PluginContentInfo? {
  val jpsModule = moduleOutputProvider.findModule(pluginName) ?: return null
  val pluginXmlPath = findFileInModuleSources(module = jpsModule, relativePath = PLUGIN_XML_RELATIVE_PATH, onlyProductionSources = true) ?: return null
  val content = withContext(Dispatchers.IO) { Files.readString(pluginXmlPath) }

  val processedModulesForDeps = HashSet<String>()

  val xIncludeResolver: suspend (String) -> LoadedXIncludeReference? = { path ->
    xIncludeCache.getOrPut(path) {
      withContext(Dispatchers.IO) {
        resolveXInclude(path = path, jpsModule = jpsModule, moduleOutputProvider = moduleOutputProvider, processedModules = processedModulesForDeps)
      }
    }
  }

  // BFS traversal with concurrent xi:include resolution
  val contentModules = extractContentModulesWithSuspendResolver(
    input = content.toByteArray(),
    xIncludeResolver = xIncludeResolver,
  )

  // Filter out module names with '/' (v2 module paths, not supported yet)
  val filteredModules = contentModules.filter { !it.name.contains('/') }

  return PluginContentInfo(
    pluginXmlPath = pluginXmlPath,
    pluginXmlContent = content,
    contentModules = filteredModules.mapTo(LinkedHashSet(filteredModules.size)) { it.name },
    contentModuleLoadings = filteredModules.associate { it.name to it.loadingRule },
    jpsDependencies = { jpsModule.getProductionModuleDependencies().map { it.moduleReference.moduleName }.toList() },
  )
}

/**
 * Extracts content modules from XML using BFS traversal with suspend xi:include resolution.
 * Resolves all `xi:includes` at each level concurrently for optimal I/O performance.
 */
private suspend fun extractContentModulesWithSuspendResolver(
  input: ByteArray,
  xIncludeResolver: suspend (path: String) -> LoadedXIncludeReference?,
): List<ContentModuleElement> {
  val allContent = ArrayList<ContentModuleElement>()
  val processedPaths = HashSet<String>()

  // BFS queue: (input bytes, baseDir) pairs
  var pending = listOf(input to null as String?)

  while (pending.isNotEmpty()) {
    // Parse all pending files synchronously (fast, CPU-bound)
    val results = pending.map { (data, base) ->
      parseContentAndXIncludes(data, locationSource = null, baseDir = base)
    }

    // Collect content modules from all parsed files
    for (result in results) {
      allContent.addAll(result.contentModules)
    }

    // Collect unique xi:include paths not yet processed
    val newPaths = ArrayList<String>()
    for (result in results) {
      for (path in result.xIncludePaths) {
        if (processedPaths.add(path)) {
          newPaths.add(path)
        }
      }
    }

    if (newPaths.isEmpty()) {
      break
    }

    // Resolve all new paths concurrently
    pending = coroutineScope {
      newPaths.map { path ->
        async {
          xIncludeResolver(path)?.let { resolved ->
            resolved.inputStream to getParentDir(path)
          }
        }
      }.mapNotNull { it.await() }
    }
  }

  return allContent
}

private fun getParentDir(path: String): String? {
  val lastSlash = path.lastIndexOf('/')
  return if (lastSlash > 0) path.substring(0, lastSlash) else null
}

private fun resolveXInclude(
  path: String,
  jpsModule: JpsModule,
  moduleOutputProvider: ModuleOutputProvider,
  processedModules: MutableSet<String>,
): LoadedXIncludeReference? {
  // First, try to find in module sources
  val sourceFile = findFileInModuleSources(module = jpsModule, relativePath = path, onlyProductionSources = true)
  if (sourceFile != null) {
    return LoadedXIncludeReference(Files.readAllBytes(sourceFile), sourceFile.toString())
  }

  // Search module dependencies recursively
  val depContent = findFileInModuleDependencies(
    module = jpsModule,
    relativePath = path,
    context = moduleOutputProvider,
    processedModules = processedModules,
  )
  if (depContent != null) {
    return LoadedXIncludeReference(depContent, null)
  }

  // Final fallback: search module outputs (like runtime classloader does)
  val prefix = jpsModule.name.substringBefore('.', missingDelimiterValue = "").takeIf { it.isNotEmpty() }
  val anyContent = moduleOutputProvider.findFileInAnyModuleOutput(path, prefix)
  return anyContent?.let { LoadedXIncludeReference(it, null) }
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
