// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.discovery

import com.intellij.platform.plugins.parser.impl.LoadedXIncludeReference
import com.intellij.platform.plugins.parser.impl.PluginDescriptorFromXmlStreamConsumer
import com.intellij.platform.plugins.parser.impl.PluginDescriptorReaderContext
import com.intellij.platform.plugins.parser.impl.XIncludeLoader
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.util.xml.dom.NoOpXmlInterner
import com.intellij.util.xml.dom.createNonCoalescingXmlStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.findFileInModuleDependencies
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.util.getProductionModuleDependencies
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private val NULL_REFERENCE = LoadedXIncludeReference(ByteArray(0), null)

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
 * Uses the standard plugins/parser module which properly handles xi:include directives,
 * nested includes, fallbacks, and xpointer expressions.
 */
internal suspend fun extractPluginContent(
  pluginName: String,
  moduleOutputProvider: ModuleOutputProvider,
  xIncludeCache: ConcurrentHashMap<String, LoadedXIncludeReference> = ConcurrentHashMap(),
): PluginContentInfo? {
  val jpsModule = moduleOutputProvider.findModule(pluginName) ?: return null
  val pluginXmlPath = findFileInModuleSources(module = jpsModule, relativePath = PLUGIN_XML_RELATIVE_PATH, onlyProductionSources = true) ?: return null
  val content = withContext(Dispatchers.IO) { Files.readString(pluginXmlPath) }

  // Use the proper parser with xi:include support
  val xIncludeLoader = ModuleXIncludeLoader(jpsModule, moduleOutputProvider, xIncludeCache)
  val readContext = object : PluginDescriptorReaderContext {
    override val interner = NoOpXmlInterner
    override val isMissingIncludeIgnored = true
  }
  val consumer = PluginDescriptorFromXmlStreamConsumer(
    readContext = readContext,
    xIncludeLoader = xIncludeLoader,
  )
  consumer.consume(createNonCoalescingXmlStreamReader(content.toByteArray(), pluginXmlPath.toString()))
  val descriptor = consumer.build()

  // Filter out module names with '/' (v2 module paths, not supported yet)
  val filteredModules = descriptor.contentModules.filter { !it.name.contains('/') }

  return PluginContentInfo(
    pluginXmlPath = pluginXmlPath,
    pluginXmlContent = content,
    contentModules = filteredModules.mapTo(LinkedHashSet()) { it.name },
    contentModuleLoadings = filteredModules.associate { it.name to it.loadingRule },
    jpsDependencies = { jpsModule.getProductionModuleDependencies(withTests = false).map { it.moduleReference.moduleName }.toList() },
  )
}

/**
 * XIncludeLoader implementation that resolves includes from module sources, output, dependencies, and all modules.
 * This replicates runtime classloader behavior where xi:includes are resolved from any JAR in the classpath.
 */
private class ModuleXIncludeLoader(
  private val jpsModule: JpsModule,
  private val moduleOutputProvider: ModuleOutputProvider,
  private val cache: ConcurrentHashMap<String, LoadedXIncludeReference>,
) : XIncludeLoader {
  private val processedModules = HashSet<String>()

  override fun loadXIncludeReference(path: String): LoadedXIncludeReference? {
    val result = cache.computeIfAbsent(path) { doLoad(it) ?: NULL_REFERENCE }
    return if (result === NULL_REFERENCE) null else result
  }

  private fun doLoad(path: String): LoadedXIncludeReference? {
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
    // If module name is dot-separated (e.g., kotlin.foo.bar), only search in modules with the same prefix (kotlin.*)
    val prefix = jpsModule.name.substringBefore('.', missingDelimiterValue = "").takeIf { it.isNotEmpty() }
    val anyContent = moduleOutputProvider.findFileInAnyModuleOutput(path, prefix)
    return anyContent?.let { LoadedXIncludeReference(it, null) }
  }
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
