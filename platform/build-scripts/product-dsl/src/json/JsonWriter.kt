// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.json

import com.fasterxml.jackson.core.JsonGenerator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.tooling.ModuleSetMetadata
import org.jetbrains.intellij.build.productLayout.tooling.ProductSpec
import org.jetbrains.intellij.build.productLayout.traversal.ModuleSetTraversalCache
import org.jetbrains.intellij.build.productLayout.visitAllModules

// kotlinx.serialization Json instance for serializing data structures
internal val kotlinxJson = Json {
  prettyPrint = false
  encodeDefaults = true
}

/**
 * Enriches products with calculated metrics (parallelized).
 * Calculates totalModuleCount, directModuleCount, and moduleSetCount for each product.
 *
 * Uses cache for O(1) module name lookups instead of repeated traversals.
 */
internal suspend fun enrichProductsWithMetrics(
  products: List<ProductSpec>,
  moduleSets: List<ModuleSet>
): List<ProductSpec> = coroutineScope {
  // Build cache for O(1) module name lookups
  val cache = ModuleSetTraversalCache(moduleSets)

  products.map { product ->
    async {
      val contentSpec = product.contentSpec
      if (contentSpec == null) {
        product // Return as-is if no contentSpec
      }
      else {
        val allModules = cache.collectProductModuleNames(contentSpec)
        product.copy(
          totalModuleCount = allModules.size,
          directModuleCount = contentSpec.additionalModules.size,
          moduleSetCount = contentSpec.moduleSets.size
        )
      }
    }
  }.awaitAll()
}

/**
 * Writes module distribution analysis.
 * For each module, lists which module sets and products use it, plus location information.
 *
 * Output format: { "moduleName": { "inModuleSets": [...], "inProducts": [...], "location": "...", "imlPath": "..." } }
 */
internal fun writeModuleDistribution(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  moduleLocations: Map<String, org.jetbrains.intellij.build.productLayout.tooling.ModuleLocationInfo>,
  cache: ModuleSetTraversalCache
) {
  @Serializable
  data class Entry(
    val inModuleSets: List<String>,
    val inProducts: List<String>,
    val location: String,
    val imlPath: String? = null
  )

  // Build module → sets/products mapping
  val inModuleSets = mutableMapOf<String, MutableSet<String>>()
  val inProducts = mutableMapOf<String, MutableSet<String>>()

  // Collect modules from module sets
  for ((moduleSet, _, _) in allModuleSets) {
    visitAllModules(moduleSet) { module ->
      inModuleSets.computeIfAbsent(module.name) { mutableSetOf() }.add(moduleSet.name)
    }
  }

  // Collect modules from products (using cache for O(1) lookups)
  for (product in products) {
    val contentSpec = product.contentSpec ?: continue
    for (moduleName in cache.collectProductModuleNames(contentSpec)) {
      inProducts.computeIfAbsent(moduleName) { mutableSetOf() }.add(product.name)
    }
  }

  // Build result map
  val allModuleNames = (inModuleSets.keys + inProducts.keys).sorted()
  val result = allModuleNames.associateWith { moduleName ->
    val locationInfo = moduleLocations.get(moduleName)
    Entry(
      inModuleSets = inModuleSets.get(moduleName)?.sorted() ?: emptyList(),
      inProducts = inProducts.get(moduleName)?.sorted() ?: emptyList(),
      location = locationInfo?.location?.name ?: "UNKNOWN",
      imlPath = locationInfo?.imlPath
    )
  }

  gen.writeRawValue(kotlinxJson.encodeToString(result))
}

/**
 * Writes module set hierarchy analysis.
 * For each module set, lists what it includes and what includes it.
 *
 * Output format: { "setName": { "includes": [...], "includedBy": [...], "moduleCount": N } }
 */
internal fun writeModuleSetHierarchy(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>
) {
  @Serializable
  data class Entry(
    val includes: List<String>,
    val includedBy: List<String>,
    val moduleCount: Int
  )

  // Build includes map
  val includes = mutableMapOf<String, List<String>>()
  val moduleCounts = mutableMapOf<String, Int>()
  val includedBy = mutableMapOf<String, MutableList<String>>()

  for ((moduleSet, _, _) in allModuleSets) {
    includes[moduleSet.name] = moduleSet.nestedSets.map { it.name }.sorted()
    moduleCounts[moduleSet.name] = moduleSet.modules.size
    includedBy.computeIfAbsent(moduleSet.name) { mutableListOf() }
  }

  // Build reverse references
  for ((moduleSet, _, _) in allModuleSets) {
    for (nestedSet in moduleSet.nestedSets) {
      includedBy.computeIfAbsent(nestedSet.name) { mutableListOf() }.add(moduleSet.name)
    }
  }

  val result = includes.keys.sorted().associateWith { setName ->
    Entry(
      includes = includes[setName] ?: emptyList(),
      includedBy = includedBy[setName]?.sorted() ?: emptyList(),
      moduleCount = moduleCounts[setName] ?: 0
    )
  }

  gen.writeRawValue(kotlinxJson.encodeToString(result))
}

/**
 * Writes module usage index.
 * For each module, provides complete information about where it's used and how to navigate to it.
 */
internal fun writeModuleUsageIndex(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  cache: ModuleSetTraversalCache
) {
  @Serializable
  data class ModuleSetRef(@JvmField val name: String, @JvmField val location: String, @JvmField val sourceFile: String)

  @Serializable
  data class ProductRef(@JvmField val name: String, @JvmField val sourceFile: String)

  @Serializable
  data class ModuleEntry(@JvmField val moduleSets: List<ModuleSetRef>, @JvmField val products: List<ProductRef>)

  @Serializable
  data class Wrapper(@JvmField val modules: Map<String, ModuleEntry>)

  val moduleSetsMap = mutableMapOf<String, MutableList<ModuleSetRef>>()
  val productsMap = mutableMapOf<String, MutableList<ProductRef>>()

  // Collect from module sets
  for ((moduleSet, location, sourceFile) in allModuleSets) {
    visitAllModules(moduleSet) { module ->
      moduleSetsMap.computeIfAbsent(module.name) { mutableListOf() }
        .add(ModuleSetRef(moduleSet.name, location.name, sourceFile))
    }
  }

  // Collect from products (using cache for O(1) lookups)
  for (product in products) {
    val contentSpec = product.contentSpec ?: continue
    for (moduleName in cache.collectProductModuleNames(contentSpec)) {
      productsMap.computeIfAbsent(moduleName) { mutableListOf() }
        .add(ProductRef(product.name, product.sourceFile))
    }
  }

  val allModuleNames = (moduleSetsMap.keys + productsMap.keys).sorted()
  val modules = allModuleNames.associateWith { moduleName ->
    ModuleEntry(
      moduleSets = moduleSetsMap[moduleName]?.sortedBy { it.name } ?: emptyList(),
      products = productsMap[moduleName]?.sortedBy { it.name } ?: emptyList()
    )
  }

  gen.writeRawValue(kotlinxJson.encodeToString(Wrapper(modules)))
}
