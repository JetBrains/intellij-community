// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.dependency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.productLayout.util.AsyncCache
import org.jetbrains.intellij.build.productLayout.util.getProductionModuleDependencies
import java.nio.file.Files
import java.nio.file.Path

/**
 * Cache for module descriptor information to avoid redundant file system lookups.
 *
 * This cache handles **production** module descriptors (`{moduleName}.xml`).
 * For test descriptors (`{moduleName}._test.xml`), see [PluginDependencyGenerator].
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
    @JvmField val dependencies: List<String>,
    @JvmField val content: String,
  )

  private val cache = AsyncCache<String, DescriptorInfo?>(scope)

  /**
   * Gets cached descriptor info or analyzes the module if not yet cached.
   */
  suspend fun getOrAnalyze(moduleName: String): DescriptorInfo? {
    return cache.getOrPut(moduleName) {
      analyzeModule(moduleName)
    }
  }

  /**
   * Checks if a module has a descriptor XML file.
   */
  suspend fun hasDescriptor(moduleName: String): Boolean = getOrAnalyze(moduleName) != null

  @JvmField val outputProviderRef = outputProvider

  private suspend fun analyzeModule(moduleName: String): DescriptorInfo? {
    val jpsModule = outputProvider.findRequiredModule(moduleName)
    val descriptorPath = findFileInModuleSources(
      module = jpsModule,
      relativePath = "$moduleName.xml",
      onlyProductionSources = true,
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

    val deps = mutableListOf<String>()
    for (dep in jpsModule.getProductionModuleDependencies(withTests = false)) {
      val depName = dep.moduleReference.moduleName
      if (hasDescriptor(depName)) {
        deps.add(depName)
      }
    }

    return DescriptorInfo(descriptorPath, deps.distinct().sorted(), content)
  }
}
