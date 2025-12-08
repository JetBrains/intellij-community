// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.dependency

import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.productLayout.util.getProductionModuleDependencies
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache for module descriptor information to avoid redundant file system lookups.
 */
internal class ModuleDescriptorCache(private val moduleOutputProvider: ModuleOutputProvider) {
  data class DescriptorInfo(
    @JvmField val descriptorPath: Path,
    @JvmField val dependencies: List<String>,
  )

  // Wrapper to allow caching null results (ConcurrentHashMap doesn't support null values)
  private class CacheValue(@JvmField val info: DescriptorInfo?)

  private val cache = ConcurrentHashMap<String, CacheValue>()

  /**
   * Gets cached descriptor info or analyzes the module if not yet cached.
   * Caches both positive (has descriptor) and negative (no descriptor) results.
   * Thread-safe: ensures exactly one analysis per module using double-checked locking.
   */
  fun getOrAnalyze(moduleName: String): DescriptorInfo? {
    return (cache.get(moduleName) ?: synchronized(moduleName.intern()) {
      cache.getOrPut(moduleName) { CacheValue(analyzeModule(moduleName)) }
    }).info
  }

  /**
   * Analyzes a module to find its descriptor and production dependencies.
   */
  private fun analyzeModule(moduleName: String): DescriptorInfo? {
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

  private fun shouldSkipDescriptor(descriptorPath: Path): Boolean {
    val content = Files.readString(descriptorPath)
    return content.contains("<!-- todo: register this as a content module (IJPL-210868)")
  }

  /**
   * Checks if a module has a descriptor XML file.
   */
  fun hasDescriptor(moduleName: String): Boolean = getOrAnalyze(moduleName) != null
}
