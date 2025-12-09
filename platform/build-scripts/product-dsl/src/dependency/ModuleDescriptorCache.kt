// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.dependency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.productLayout.util.getProductionModuleDependencies
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache for module descriptor information to avoid redundant file system lookups.
 * 
 * Uses a coroutine-friendly Deferred-based cache pattern:
 * - First caller for a module creates a Deferred via async
 * - Subsequent callers await the same Deferred without blocking
 * - File I/O runs on Dispatchers.IO to avoid blocking coroutine threads
 */
internal class ModuleDescriptorCache(
  private val moduleOutputProvider: ModuleOutputProvider,
  private val scope: CoroutineScope,
) {
  data class DescriptorInfo(
    @JvmField val descriptorPath: Path,
    @JvmField val dependencies: List<String>,
  )

  private val cache = ConcurrentHashMap<String, Deferred<DescriptorInfo?>>()

  /**
   * Gets cached descriptor info or analyzes the module if not yet cached.
   * Caches both positive (has descriptor) and negative (no descriptor) results.
   * 
   * Coroutine-friendly: uses Deferred so concurrent callers await without blocking threads.
   */
  suspend fun getOrAnalyze(moduleName: String): DescriptorInfo? {
    return cache.computeIfAbsent(moduleName) {
      scope.async(Dispatchers.IO) {
        analyzeModule(moduleName)
      }
    }.await()
  }

  /**
   * Analyzes a module to find its descriptor and production dependencies.
   */
  private suspend fun analyzeModule(moduleName: String): DescriptorInfo? {
    val jpsModule = moduleOutputProvider.findRequiredModule(moduleName)
    val descriptorPath = findFileInModuleSources(
      module = jpsModule,
      relativePath = "$moduleName.xml",
      onlyProductionSources = true
    ) ?: return null

    // Skip modules with IJPL-210868 marker (not registered as content modules)
    if (shouldSkipDescriptor(descriptorPath)) {
      return null
    }

    val deps = mutableListOf<String>()
    for (dep in jpsModule.getProductionModuleDependencies(withTests = false)) {
      val depName = dep.moduleReference.moduleName
      if (hasDescriptor(depName)) {
        deps.add(depName)
      }
    }

    // Deduplicate MODULE DEPENDENCIES (not content modules!) before sorting.
    // Handles cases where the same dependency appears multiple times in JPS module graph.
    // Note: Content module duplicates are caught by validateProductModuleSets() during generation.
    return DescriptorInfo(descriptorPath, deps.distinct().sorted())
  }

  private suspend fun shouldSkipDescriptor(descriptorPath: Path): Boolean {
    val content = withContext(Dispatchers.IO) { Files.readString(descriptorPath) }
    return content.contains("<!-- todo: register this as a content module (IJPL-210868)")
  }

  /**
   * Checks if a module has a descriptor XML file.
   */
  suspend fun hasDescriptor(moduleName: String): Boolean = getOrAnalyze(moduleName) != null
}
