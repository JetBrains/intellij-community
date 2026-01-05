// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.discovery

import com.intellij.platform.plugins.parser.impl.elements.ContentModuleElement
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.plugins.parser.impl.parseContentAndXIncludes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.findFileInModuleDependenciesRecursiveAsync
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import org.jetbrains.intellij.build.productLayout.util.getProductionModuleDependencies
import org.jetbrains.intellij.build.productLayout.validation.XIncludeResolutionError
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private sealed interface XIncludeResult {
  @JvmInline
  value class Success(val data: ByteArray) : XIncludeResult
  data class Failure(val path: String, val debugInfo: String) : XIncludeResult
}

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
  /** Errors encountered during xi:include resolution */
  @JvmField val xIncludeErrors: List<XIncludeResolutionError> = emptyList(),
  /** Module dependencies from <dependencies>/<module name="..."/> in plugin.xml */
  @JvmField val moduleDependencies: Set<String> = emptySet(),
)

/**
 * Extracts content modules from a plugin's plugin.xml.
 * Returns null if plugin.xml not found or has module references with '/'.
 *
 * Uses BFS traversal with concurrent xi:include resolution at each level for optimal I/O performance.
 */
internal suspend fun extractPluginContent(
  pluginName: String,
  outputProvider: ModuleOutputProvider,
  xIncludeCache: AsyncCache<String, ByteArray?>,
  skipXIncludePaths: Set<String> = emptySet(),
  prefixFilter: (moduleName: String) -> String? = { null },
  onlyProductionSources: Boolean = true,
): PluginContentInfo? {
  val jpsModule = outputProvider.findModule(pluginName) ?: return null
  val pluginXmlPath = findFileInModuleSources(module = jpsModule, relativePath = PLUGIN_XML_RELATIVE_PATH, onlyProductionSources = onlyProductionSources) ?: return null
  val content = withContext(Dispatchers.IO) { Files.readString(pluginXmlPath) }

  val prefix = prefixFilter(pluginName)
  val errors = mutableListOf<XIncludeResolutionError>()

  val xIncludeResolver: suspend (String) -> ByteArray? = resolver@{ path ->
    // Use cache which handles deduplication. The loader returns null on failure,
    // which we then convert to an error. Failures are cached as null to avoid retrying.
    val data = xIncludeCache.getOrPut(path) {
      when (val result = resolveXInclude(path = path, jpsModule = jpsModule, outputProvider = outputProvider, prefix = prefix)) {
        is XIncludeResult.Success -> result.data
        is XIncludeResult.Failure -> {
          errors.add(XIncludeResolutionError(
            context = "Plugin content extraction",
            pluginName = pluginName,
            xIncludePath = result.path,
            debugInfo = result.debugInfo,
          ))
          null
        }
      }
    }
    data
  }

  // BFS traversal with concurrent xi:include resolution
  val extractedContent = extractContentModules(input = content.toByteArray(), skipXIncludePaths = skipXIncludePaths, xIncludeResolver = xIncludeResolver)

  // Filter out module names with '/' (v2 module paths, not supported yet)
  val filteredModules = extractedContent.contentModules.filter { !it.name.contains('/') }
  val filteredModuleDependencies = extractedContent.moduleDependencies.filterNotTo(LinkedHashSet()) { it.contains('/') }

  return PluginContentInfo(
    pluginXmlPath = pluginXmlPath,
    pluginXmlContent = content,
    contentModules = filteredModules.mapTo(LinkedHashSet(filteredModules.size)) { it.name },
    contentModuleLoadings = filteredModules.associate { it.name to it.loadingRule },
    jpsDependencies = { jpsModule.getProductionModuleDependencies().map { it.moduleReference.moduleName }.toList() },
    xIncludeErrors = errors,
    moduleDependencies = filteredModuleDependencies,
  )
}

private class ExtractedContent(
  @JvmField val contentModules: List<ContentModuleElement>,
  @JvmField val moduleDependencies: Set<String>,
)

/**
 * Extracts content modules and module dependencies from XML using BFS traversal with suspend xi:include resolution.
 * Resolves all `xi:includes` at each level concurrently for optimal I/O performance.
 */
private suspend fun extractContentModules(
  input: ByteArray,
  skipXIncludePaths: Set<String>,
  xIncludeResolver: suspend (path: String) -> ByteArray?,
): ExtractedContent {
  val allContent = ArrayList<ContentModuleElement>()
  val allModuleDependencies = LinkedHashSet<String>()
  val processedPaths = HashSet<String>()

  // BFS queue: (input bytes, baseDir) pairs
  var pending = listOf(input)

  while (pending.isNotEmpty()) {
    // Parse all pending files synchronously (fast, CPU-bound)
    val results = pending.map { data ->
      parseContentAndXIncludes(input = data, locationSource = null)
    }

    // Collect content modules and module dependencies from all parsed files
    for (result in results) {
      allContent.addAll(result.contentModules)
      allModuleDependencies.addAll(result.moduleDependencies)
    }

    // Collect unique xi:include paths not yet processed
    val newPaths = ArrayList<String>()
    for (result in results) {
      for (path in result.xIncludePaths) {
        if (path !in skipXIncludePaths && processedPaths.add(path)) {
          newPaths.add(path)
        }
      }
    }

    if (newPaths.isEmpty()) {
      break
    }

    // Resolve all new paths concurrently (errors are collected, not thrown)
    pending = coroutineScope {
      newPaths.map { path ->
        async {
          xIncludeResolver(path)
        }
      }.awaitAll().filterNotNull()
    }
  }

  return ExtractedContent(allContent, allModuleDependencies)
}

private suspend fun resolveXInclude(
  path: String,
  jpsModule: JpsModule,
  outputProvider: ModuleOutputProvider,
  prefix: String?,
): XIncludeResult {
  outputProvider.readFileContentFromModuleOutputAsync(module = jpsModule, relativePath = path)?.let {
    return XIncludeResult.Success(it)
  }

  val processedModules = ConcurrentHashMap.newKeySet<String>()
  processedModules.add(jpsModule.name)

  findFileInModuleDependenciesRecursiveAsync(
    module = jpsModule,
    relativePath = path,
    provider = outputProvider,
    processedModules = processedModules,
    prefix = prefix,
  )?.let {
    return XIncludeResult.Success(it)
  }

  outputProvider.findFileInAnyModuleOutput(path, prefix, processedModules)?.let {
    return XIncludeResult.Success(it)
  }

  return XIncludeResult.Failure(
    path = path,
    debugInfo = "searched ${jpsModule.name} output, dependencies, and all outputs (filterPrefix=$prefix, outputProvider=$outputProvider)",
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
