// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "GrazieInspection", "GrazieStyle")

package org.jetbrains.intellij.build.productLayout.discovery

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
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
import org.jetbrains.intellij.build.findFileInModuleLibraryDependencies
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.model.ErrorSink
import org.jetbrains.intellij.build.productLayout.model.error.XIncludeResolutionError
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
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
 * Origin of plugin content - how it was obtained during generation.
 * Replaces separate tracking structures in PluginContentCache.
 *
 * @see [docs/dependency_generation.md](../../docs/dependency_generation.md) for generation pipeline
 */
internal enum class PluginSource {
  /** Bundled in products, extracted from META-INF/plugin.xml */
  BUNDLED,

  /** Test plugin (hardcoded list), extracted from test resources */
  TEST,

  /** DSL-defined test plugin, content computed from TestPluginSpec */
  DSL_TEST,

  /** Discovered on-demand during dependency resolution */
  DISCOVERED,
}

/**
 * Content module with its loading mode.
 * Consolidates module name and loading into a single structure.
 */
internal data class ContentModuleInfo(
  val name: ContentModuleName,
  @JvmField val loadingMode: ModuleLoadingRuleValue?,
)

/**
 * Dependencies from a single XML file (main plugin.xml or xi:included file).
 * Used for per-file tracking to distinguish deps from main file vs xi:includes.
 */
internal data class FileDepInfo(
  /** Relative path of the file (e.g., "/META-INF/plugin.xml" or "/META-INF/js-plugin.xml") */
  @JvmField val relativePath: String,
  @JvmField val moduleDependencies: Set<ContentModuleName>,
  @JvmField val pluginDependencies: Set<PluginId>,
)

/**
 * Legacy `<depends>` entry from plugin.xml (v1 format).
 * These should be migrated to `<plugin id="..."/>` in `<dependencies>` section (v2 format).
 */
internal data class LegacyDepends(
  val pluginId: PluginId,
  @JvmField val optional: Boolean = false,
  @JvmField val configFile: String? = null,
)

/**
 * Result of extracting plugin content from plugin.xml.
 * Contains everything needed for both validation and dependency generation.
 */
internal data class PluginContentInfo(
  @JvmField val pluginXmlPath: Path,
  @JvmField val pluginXmlContent: String,
  /** Plugin ID from <id> element in plugin.xml. May differ from module name. */
  val pluginId: PluginId?,
  /** Content modules with their loading modes */
  @JvmField val contentModules: List<ContentModuleInfo>,

  /** Module dependencies from <dependencies>/<module name="..."/> (all: main file + xi:includes) */
  @JvmField val moduleDependencies: Set<ContentModuleName> = emptySet(),
  /** Plugin dependencies from <dependencies>/<plugin id="..."/> (all: main file + xi:includes) */
  @JvmField val pluginDependencies: Set<PluginId> = emptySet(),
  /** Deps by source file: first entry = main plugin.xml, subsequent = xi:includes */
  @JvmField val depsByFile: List<FileDepInfo> = emptyList(),
  /**
   * Origin of this plugin content - how it was obtained during generation.
   *
   * This is pipeline metadata (not derivable from the graph):
   * - Pre-warmed bundled/test plugins are authoritative inputs; discovered plugins are incidental and not bundled.
   * - Validation treats discovered plugins as unbundled, so deps on them must fail availability checks.
   * - Test plugins can be extracted from test resources (onlyProductionSources=false), while bundled/discovered use production sources.
   */
  @JvmField val source: PluginSource = PluginSource.BUNDLED,
  /** Legacy `<depends>` entries (v1 format) to be migrated to `<plugin id="..."/>` (v2 format) */
  @JvmField val legacyDepends: List<LegacyDepends> = emptyList(),
  /**
   * Plugin aliases declared via `<module value="alias.name"/>` elements.
   *
   * Other plugins can depend on these aliases instead of the actual plugin ID.
   * Used for IDE capability markers (e.g., `com.intellij.modules.java`, `com.intellij.modules.ruby-capable`).
   */
  @JvmField val pluginAliases: List<PluginId> = emptyList(),
) {
  /** True if DSL-defined (content computed from spec, not extracted from disk) */
  val isDslDefined: Boolean get() = source == PluginSource.DSL_TEST

  /** True if test plugin (deps auto-derived from JPS). Includes both TEST and DSL_TEST sources. */
  val isTestPlugin: Boolean get() = source == PluginSource.TEST || source == PluginSource.DSL_TEST
}

private val PLUGIN_ID_PATTERN = Regex("""<id>([^<]+)</id>""")
private val PLUGIN_NAME_PATTERN = Regex("""<name>([^<]+)</name>""")
private val XML_COMMENT_PATTERN = Regex("<!--.*?-->", setOf(RegexOption.DOT_MATCHES_ALL))

/** Extracts plugin ID from plugin.xml content */
private fun extractPluginId(content: String): PluginId? {
  return PLUGIN_ID_PATTERN.find(content)?.groupValues?.get(1)?.trim()?.let { PluginId(it) }
}

/** Extracts plugin name from plugin.xml content */
internal fun extractPluginName(content: String): String? {
  val sanitized = content.replace(XML_COMMENT_PATTERN, "")
  return PLUGIN_NAME_PATTERN.find(sanitized)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}

// Pattern for <depends> elements:
// - Simple: <depends>plugin.id</depends>
// - Optional: <depends optional="true">plugin.id</depends>
// - With config-file: <depends optional="true" config-file="x.xml">plugin.id</depends>
private val LEGACY_DEPENDS_PATTERN = Regex(
  """<depends(?:\s+optional\s*=\s*"([^"]*)")?(?:\s+config-file\s*=\s*"([^"]*)")?\s*>([^<]+)</depends>""",
  RegexOption.IGNORE_CASE
)

/** Extracts legacy `<depends>` entries from plugin.xml content (v1 format) */
internal fun extractLegacyDepends(content: String): List<LegacyDepends> {
  val sanitized = content.replace(XML_COMMENT_PATTERN, "")
  return LEGACY_DEPENDS_PATTERN.findAll(sanitized).map { match ->
    val optional = match.groupValues[1].equals("true", ignoreCase = true)
    val configFile = match.groupValues[2].takeIf { it.isNotEmpty() }
    val pluginId = PluginId(match.groupValues[3].trim())
    LegacyDepends(pluginId = pluginId, optional = optional, configFile = configFile)
  }.toList()
}

/**
 * Extracts content modules from a plugin's plugin.xml.
 * Returns null if plugin.xml not found or has module references with '/'.
 *
 * Uses BFS traversal with concurrent xi:include resolution at each level for optimal I/O performance.
 *
 * @param source Origin of this plugin (BUNDLED, KNOWN, TEST, DISCOVERED). DSL_TEST plugins
 *               should use [computePluginContentFromDslSpec] instead of this function.
 * @param errorSink Sink for emitting xi:include resolution errors
 */
internal suspend fun extractPluginContent(
  pluginName: String,
  outputProvider: ModuleOutputProvider,
  xIncludeCache: AsyncCache<String, ByteArray?>,
  skipXIncludePaths: Set<String> = emptySet(),
  prefixFilter: (moduleName: String) -> String? = { null },
  onlyProductionSources: Boolean = true,
  source: PluginSource = PluginSource.BUNDLED,
  errorSink: ErrorSink,
): PluginContentInfo? {
  val jpsModule = outputProvider.findModule(pluginName) ?: return null
  val pluginXmlPath = findFileInModuleSources(module = jpsModule, relativePath = PLUGIN_XML_RELATIVE_PATH, onlyProductionSources = onlyProductionSources) ?: return null
  val content = withContext(Dispatchers.IO) { Files.readString(pluginXmlPath) }

  val prefix = prefixFilter(pluginName)

  val xIncludeResolver: suspend (String) -> ByteArray? = resolver@{ path ->
    // Use cache which handles deduplication. The loader returns null on failure,
    // which we then convert to an error. Failures are cached as null to avoid retrying.
    val data = xIncludeCache.getOrPut(path) {
      when (val result = resolveXInclude(path = path, jpsModule = jpsModule, outputProvider = outputProvider, prefix = prefix)) {
        is XIncludeResult.Success -> result.data
        is XIncludeResult.Failure -> {
          // Emit error immediately to errorSink
          errorSink.emit(XIncludeResolutionError(
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

  // NOTE: Slash-notation modules (e.g., "intellij.restClient/intelliLang") are virtual content modules
  // without separate JPS modules. Their descriptor files are in the parent plugin's resource root.
  // They ARE valid content modules and should be included in the graph for proper validation.
  // See Identifiers.kt for helper functions: isSlashNotation(), parentPluginName(), toDescriptorFileName()

  return PluginContentInfo(
    pluginXmlPath = pluginXmlPath,
    pluginXmlContent = content,
    pluginId = extractPluginId(content),
    contentModules = extractedContent.contentModules.map { ContentModuleInfo(name = ContentModuleName(it.name), loadingMode = it.loadingRule) },

    moduleDependencies = extractedContent.moduleDependencies,
    pluginDependencies = extractedContent.pluginDependencies,
    depsByFile = extractedContent.depsByFile,
    source = source,
    legacyDepends = extractLegacyDepends(content),
    pluginAliases = extractedContent.pluginAliases,
  )
}

private class ExtractedContent(
  @JvmField val contentModules: List<ContentModuleElement>,
  @JvmField val moduleDependencies: Set<ContentModuleName>,
  @JvmField val pluginDependencies: Set<PluginId>,
  /** Plugin aliases declared via `<module value="..."/>` elements (main file + xi:includes) */
  @JvmField val pluginAliases: List<PluginId>,
  /** Deps by source file: first entry = main plugin.xml, subsequent = xi:includes */
  @JvmField val depsByFile: List<FileDepInfo>,
)

/**
 * Extracts content modules and module dependencies from XML using BFS traversal with suspend xi:include resolution.
 * Resolves all `xi:includes` at each level concurrently for optimal I/O performance.
 *
 * Tracks deps per-file (main file + xi:includes) via [FileDepInfo] to support proper detection
 * of existing deps in xi:included files.
 */
private suspend fun extractContentModules(
  input: ByteArray,
  skipXIncludePaths: Set<String>,
  xIncludeResolver: suspend (path: String) -> ByteArray?,
): ExtractedContent {
  val allContent = ArrayList<ContentModuleElement>()
  val allModuleDependencies = LinkedHashSet<ContentModuleName>()
  val allPluginDependencies = LinkedHashSet<PluginId>()
  val allPluginAliases = LinkedHashSet<PluginId>()
  val depsByFile = ArrayList<FileDepInfo>()
  val processedPaths = HashSet<String>()

  // BFS queue: (path, bytes) pairs. Main file uses PLUGIN_XML_RELATIVE_PATH as marker.
  var pending: List<Pair<String, ByteArray>> = listOf(PLUGIN_XML_RELATIVE_PATH to input)

  while (pending.isNotEmpty()) {
    // Parse all pending files synchronously (fast, CPU-bound)
    val results = pending.map { (path, data) ->
      path to parseContentAndXIncludes(input = data, locationSource = null)
    }

    // Collect content modules, module dependencies, plugin dependencies, and aliases from all parsed files
    for ((path, result) in results) {
      allContent.addAll(result.contentModules)
      val moduleDeps = result.moduleDependencies.mapTo(LinkedHashSet()) { ContentModuleName(it) }
      val pluginDeps = result.pluginDependencies.mapTo(LinkedHashSet()) { PluginId(it) }
      allModuleDependencies.addAll(moduleDeps)
      allPluginDependencies.addAll(pluginDeps)
      for (alias in result.pluginAliases) {
        allPluginAliases.add(PluginId(alias))
      }
      // Track deps per file
      depsByFile.add(FileDepInfo(relativePath = path, moduleDependencies = moduleDeps, pluginDependencies = pluginDeps))
    }

    // Collect unique xi:include paths not yet processed
    val newPaths = ArrayList<String>()
    for ((_, result) in results) {
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
          xIncludeResolver(path)?.let { path to it }
        }
      }.awaitAll().filterNotNull()
    }
  }

  return ExtractedContent(
    contentModules = allContent,
    moduleDependencies = allModuleDependencies,
    pluginDependencies = allPluginDependencies,
    pluginAliases = allPluginAliases.toList(),
    depsByFile = depsByFile,
  )
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

  findFileInModuleLibraryDependencies(jpsModule, path, outputProvider)?.let {
    return XIncludeResult.Success(it)
  }

  outputProvider.findFileInAnyModuleOutput(path, prefix, processedModules)?.let {
    return XIncludeResult.Success(it)
  }

  return XIncludeResult.Failure(
    path = path,
    debugInfo = "searched ${jpsModule.name} output, library dependencies, module dependencies, and all outputs (filterPrefix=$prefix, outputProvider=$outputProvider)",
  )
}

private fun collectEmbeddedModules(moduleSet: ModuleSet, result: MutableSet<ContentModuleName>) {
  for (module in moduleSet.modules) {
    if (module.loading == ModuleLoadingRuleValue.EMBEDDED) {
      result.add(module.name)
    }
  }
  for (nestedSet in moduleSet.nestedSets) {
    collectEmbeddedModules(nestedSet, result)
  }
}
