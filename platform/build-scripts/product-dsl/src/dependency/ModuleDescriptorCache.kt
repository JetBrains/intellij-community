// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.dependency

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.baseModuleName
import com.intellij.platform.pluginGraph.toDescriptorFileName
import com.intellij.platform.plugins.parser.impl.parseContentAndXIncludes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.productLayout.model.error.ErrorCategory
import org.jetbrains.intellij.build.productLayout.model.error.UnsuppressedPipelineError
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import java.nio.file.Files
import java.nio.file.Path

/**
 * Cache for module descriptor information to avoid redundant file system lookups.
 *
 * This cache handles module descriptors (`{moduleName}.xml`) in both production and test resources.
 * For test descriptors (`{moduleName}._test.xml`), see test descriptor generation in the pipeline.
 *
 * For test plugin content modules, descriptors may be in test resources and need test dependencies.
 * See [docs/test-plugins.md](../../docs/test-plugins.md) for details.
 * 
 * Uses a coroutine-friendly Deferred-based cache pattern:
 * - First caller for a module creates a Deferred via async
 * - Subsequent callers await the same Deferred without blocking
 * - File I/O runs on Dispatchers.IO to avoid blocking coroutine threads
 */
internal class ModuleDescriptorCache(
  private val outputProvider: ModuleOutputProvider,
  scope: CoroutineScope,
) {
  data class DescriptorInfo(
    @JvmField val descriptorPath: Path,
    @JvmField val content: String,
    /** If true, this module has `@skip-dependency-generation` marker and should not have deps auto-generated. */
    @JvmField val skipDependencyGeneration: Boolean,
    /** Plugin dependencies already declared in the XML file (e.g., `<plugin id="com.intellij.copyright"/>`). */
    @JvmField val existingPluginDependencies: List<String> = emptyList(),
    /** Plugin aliases declared via `<module value="..."/>` in the descriptor. */
    @JvmField val pluginAliases: List<String> = emptyList(),
    /** Module dependencies already declared in the XML file (e.g., `<module name="..."/>`). */
    @JvmField val existingModuleDependencies: List<String> = emptyList(),
    /**
     * Suppressible error if the descriptor has issues (e.g., non-standard XML root element).
     * Collected by generators and filtered through suppression config based on [UnsuppressedPipelineError.suppressionKey].
     */
    @JvmField val suppressibleError: UnsuppressedPipelineError? = null,
  )

  private val cache = AsyncCache<String, DescriptorInfo?>(scope)

  /**
   * Gets cached descriptor info or analyzes the module if not yet cached.
   *
   * @param moduleName The module name to analyze
   */
  suspend fun getOrAnalyze(moduleName: String): DescriptorInfo? {
    return cache.getOrPut(moduleName) {
      analyzeModule(moduleName)
    }
  }

  private suspend fun analyzeModule(moduleName: String): DescriptorInfo? {
    // Handle slash-notation modules (e.g., "intellij.restClient/intelliLang")
    // These are virtual content modules without separate JPS modules.
    // Their descriptor is in the parent plugin's resource root with name like "intellij.restClient.intelliLang.xml"
    val name = ContentModuleName(moduleName)
    val jpsModuleName = name.baseModuleName().value
    val descriptorFileName = name.toDescriptorFileName()

    val jpsModule = outputProvider.findModule(jpsModuleName)
    if (jpsModule == null) {
      // For slash-notation modules, parent not found is expected if plugin not loaded
      return null
    }

    // Search production sources first, fallback to test resources (for test plugin content modules)
    val descriptorPath = findFileInModuleSources(
      module = jpsModule,
      relativePath = descriptorFileName,
      onlyProductionSources = false,
    )
    if (descriptorPath == null) {
      // Debug-level: missing descriptors are common for non-plugin modules
      // System.err.println("Debug: No descriptor found for module '$moduleName'")
      return null
    }

    val content = withContext(Dispatchers.IO) { Files.readString(descriptorPath) }

    if (content.contains("<!-- todo: register this as a content module (IJPL-210868)")) {
      return null
    }

    val skipDependencyGeneration = content.contains("@skip-dependency-generation")

    // Use platform parser to extract existing dependencies (xi:include aware)
    val parseResult = parseContentAndXIncludes(input = content.toByteArray(), locationSource = null)

    // Detect non-standard XML root: parser returns empty but file has dependency elements.
    // This indicates <dependencies> root instead of <idea-plugin> - parser can't extract from such files.
    val hasNonStandardRoot = parseResult.moduleDependencies.isEmpty() &&
                             parseResult.pluginDependencies.isEmpty() &&
                             (content.contains("<module name=") || content.contains("<plugin id="))

    val suppressibleError = if (hasNonStandardRoot) {
      UnsuppressedPipelineError(
        context = moduleName,
        errorKey = "nonStandardRoot:$moduleName",
        message = "Descriptor uses non-standard XML root element (e.g., <dependencies> instead of <idea-plugin>). " +
                  "Parser cannot extract dependencies. Fix the XML structure or suppress this error.",
        errorCategory = ErrorCategory.NON_STANDARD_DESCRIPTOR_ROOT,
        contentModuleName = ContentModuleName(moduleName),
        path = descriptorPath,
      )
    }
    else {
      null
    }

    return DescriptorInfo(
      descriptorPath = descriptorPath,
      content = content,
      skipDependencyGeneration = skipDependencyGeneration,
      existingPluginDependencies = parseResult.pluginDependencies,
      pluginAliases = parseResult.pluginAliases,
      existingModuleDependencies = parseResult.moduleDependencies,
      suppressibleError = suppressibleError,
    )
  }
}
