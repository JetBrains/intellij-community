// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.dependency

import com.intellij.platform.pluginGraph.TargetName
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.discovery.PluginSource
import org.jetbrains.intellij.build.productLayout.discovery.extractPluginContent
import org.jetbrains.intellij.build.productLayout.model.ErrorSink
import org.jetbrains.intellij.build.productLayout.util.AsyncCache

/**
 * Interface for plugin content retrieval with on-demand discovery support.
 * Used by [org.jetbrains.intellij.build.productLayout.generator.PluginXmlDependencyGenerator] and
 * [org.jetbrains.intellij.build.productLayout.generator.ContentModuleDependencyGenerator] to resolve plugin dependencies.
 *
 * @see PluginContentCache for production implementation
 */
internal interface PluginContentProvider {
  /**
   * Gets or extracts plugin content for the given plugin module name.
   * @return PluginContentInfo if module has META-INF/plugin.xml, null otherwise
   */
  suspend fun getOrExtract(pluginModule: TargetName): PluginContentInfo?
}

/**
 * Cache for plugin content extraction with on-demand discovery.
 *
 * Pre-warmed with bundled plugins but can extract any plugin's content on-demand.
 * Plugin origin is tracked via [PluginSource] in [PluginContentInfo].
 *
 * **Key features:**
 * - Deferred-based: first caller for a module creates async job, later callers await same result
 * - Source tracking: each plugin knows its origin (BUNDLED, TEST, DSL_TEST, DISCOVERED)
 * - Handles DSL-defined test plugins (content computed from spec, not read from disk)
 *
 * @see [docs/dependency_generation.md](../../docs/dependency_generation.md) for generation pipeline
 * @see [docs/validation-rules.md](../../docs/validation-rules.md) for validation architecture
 * @see [docs/IntelliJ-Platform/4_man/Plugin-Model/Plugin-Model-v1-v2.md] for plugin vs module distinction
 */
internal class PluginContentCache(
  private val outputProvider: ModuleOutputProvider,
  private val xIncludeCache: AsyncCache<String, ByteArray?>,
  private val skipXIncludePaths: Set<String>,
  private val xIncludePrefixFilter: (String) -> String?,
  scope: CoroutineScope,
  private val errorSink: ErrorSink,
) : PluginContentProvider {
  private val cache = AsyncCache<TargetName, PluginContentInfo?>(scope)

  /**
   * Extracts plugin content with explicit source type.
   * Used for pre-warming the cache with bundled/test plugins.
   *
   * @param plugin Plugin module to extract
   * @param isTest Whether this is a test plugin (determines source and production-only flag)
   * @return PluginContentInfo if module has META-INF/plugin.xml, null otherwise
   */
  suspend fun extract(plugin: TargetName, isTest: Boolean): PluginContentInfo? {
    return cache.getOrPut(plugin) {
      val source = if (isTest) PluginSource.TEST else PluginSource.BUNDLED
      extractPluginContent(
        pluginName = plugin.value,
        outputProvider = outputProvider,
        xIncludeCache = xIncludeCache,
        skipXIncludePaths = skipXIncludePaths,
        prefixFilter = xIncludePrefixFilter,
        onlyProductionSources = !isTest,
        source = source,
        errorSink = errorSink,
      )
    }
  }

  /**
   * Adds a single pre-computed DSL test plugin content to the cache.
   * Called for each DSL test plugin during graph building.
   */
  suspend fun addDslTestPlugin(pluginModule: TargetName, content: PluginContentInfo) {
    cache.getOrPut(pluginModule) { content }
  }

  /**
   * Gets or extracts plugin content for the given plugin module name.
   *
   * If the plugin was not pre-warmed, it will be extracted on-demand with source=DISCOVERED.
   *
   * @param pluginModule The JPS module name (not plugin ID)
   * @return PluginContentInfo if module has META-INF/plugin.xml, null otherwise
   */
  override suspend fun getOrExtract(pluginModule: TargetName): PluginContentInfo? {
    return cache.getOrPut(pluginModule) {
      // Not pre-warmed → discovered on-demand during dependency resolution
      extractPluginContent(
        pluginName = pluginModule.value,
        outputProvider = outputProvider,
        xIncludeCache = xIncludeCache,
        skipXIncludePaths = skipXIncludePaths,
        prefixFilter = xIncludePrefixFilter,
        onlyProductionSources = true,
        source = PluginSource.DISCOVERED,
        errorSink = errorSink,
      )
    }
  }
}
