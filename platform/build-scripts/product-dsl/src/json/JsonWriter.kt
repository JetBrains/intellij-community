// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.json

import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.platform.pluginGraph.PluginGraph
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.productLayout.tooling.ModuleSetMetadata
import org.jetbrains.intellij.build.productLayout.tooling.ProductSpec
import org.jetbrains.intellij.build.productLayout.traversal.collectDirectProductModuleNames
import org.jetbrains.intellij.build.productLayout.traversal.collectModuleSetDirectModuleNames
import org.jetbrains.intellij.build.productLayout.traversal.collectModuleSetDirectNestedNames
import org.jetbrains.intellij.build.productLayout.traversal.collectModuleSetModuleNames
import org.jetbrains.intellij.build.productLayout.traversal.collectProductModuleNames
import org.jetbrains.intellij.build.productLayout.traversal.collectProductModuleSetNames

// kotlinx.serialization Json instance for serializing data structures
internal val kotlinxJson = Json {
  prettyPrint = false
  encodeDefaults = true
}

/**
 * Enriches products with calculated metrics (parallelized).
 * Calculates totalModuleCount, directModuleCount, and moduleSetCount for each product.
 */
internal suspend fun enrichProductsWithMetrics(
  products: List<ProductSpec>,
  pluginGraph: PluginGraph
): List<ProductSpec> {
  return coroutineScope {
    products.map { product ->
      async {
        val contentSpec = product.contentSpec
        if (contentSpec == null) {
          product // Return as-is if no contentSpec
        }
        else if (!pluginGraph.query { product(product.name) != null }) {
          product
        }
        else {
          val totalModules = collectProductModuleNames(pluginGraph, product.name)
          val directModules = collectDirectProductModuleNames(pluginGraph, product.name)
          val moduleSets = collectProductModuleSetNames(pluginGraph, product.name)
          val aliasValues = contentSpec.productModuleAliases.map { it.value }.sorted()
          val compositionCounts = contentSpec.compositionGraph
            .groupingBy { it.type.name.lowercase() }
            .eachCount()
          product.copy(
            totalModuleCount = totalModules.size,
            directModuleCount = directModules.size,
            moduleSetCount = moduleSets.size,
            moduleSets = moduleSets.sorted(),
            directModules = directModules.map { it.value }.sorted(),
            aliasCount = aliasValues.size,
            aliases = aliasValues,
            compositionSummary = org.jetbrains.intellij.build.productLayout.tooling.CompositionSummary(
              totalOperations = contentSpec.compositionGraph.size,
              operationsByType = compositionCounts
            )
          )
        }
      }
    }.awaitAll()
  }
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
  pluginGraph: PluginGraph
) {
  @Serializable
  data class Entry(
    val inModuleSets: List<String>,
    val inProducts: List<String>,
    val location: String,
    val imlPath: String? = null
  )

  // Build module → sets/products mapping
  val inModuleSets = LinkedHashMap<String, MutableSet<String>>()
  val inProducts = LinkedHashMap<String, MutableSet<String>>()

  // Collect modules from module sets
  for (entry in allModuleSets) {
    val modules = collectModuleSetModuleNames(pluginGraph, entry.moduleSet.name)
    for (moduleName in modules) {
      val moduleKey = moduleName.value
      inModuleSets.computeIfAbsent(moduleKey) { LinkedHashSet() }.add(entry.moduleSet.name)
    }
  }

  // Collect modules from products
  for (product in products) {
    if (product.contentSpec == null) continue
    for (moduleName in collectProductModuleNames(pluginGraph, product.name)) {
      val moduleKey = moduleName.value
      inProducts.computeIfAbsent(moduleKey) { LinkedHashSet() }.add(product.name)
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
  allModuleSets: List<ModuleSetMetadata>,
  pluginGraph: PluginGraph
) {
  @Serializable
  data class Entry(
    val includes: List<String>,
    val includedBy: List<String>,
    val moduleCount: Int
  )

  // Build includes map
  val includes = LinkedHashMap<String, List<String>>()
  val moduleCounts = LinkedHashMap<String, Int>()
  val includedBy = LinkedHashMap<String, MutableList<String>>()

  for (entry in allModuleSets) {
    val setName = entry.moduleSet.name
    includes.put(setName, collectModuleSetDirectNestedNames(pluginGraph, setName).sorted())
    moduleCounts.put(setName, collectModuleSetDirectModuleNames(pluginGraph, setName).size)
    includedBy.computeIfAbsent(setName) { mutableListOf() }
  }

  // Build reverse references
  for (entry in allModuleSets) {
    val setName = entry.moduleSet.name
    for (nestedSet in collectModuleSetDirectNestedNames(pluginGraph, setName)) {
      includedBy.computeIfAbsent(nestedSet) { mutableListOf() }.add(setName)
    }
  }

  val result = includes.keys.sorted().associateWith { setName ->
    Entry(
      includes = includes.get(setName) ?: emptyList(),
      includedBy = includedBy.get(setName)?.sorted() ?: emptyList(),
      moduleCount = moduleCounts.get(setName) ?: 0
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
  pluginGraph: PluginGraph
) {
  @Serializable
  data class ModuleSetRef(@JvmField val name: String, @JvmField val location: String, @JvmField val sourceFile: String)

  @Serializable
  data class ProductRef(@JvmField val name: String, @JvmField val sourceFile: String)

  @Serializable
  data class ModuleEntry(@JvmField val moduleSets: List<ModuleSetRef>, @JvmField val products: List<ProductRef>)

  @Serializable
  data class Wrapper(@JvmField val modules: Map<String, ModuleEntry>)

  val moduleSetsMap = LinkedHashMap<String, MutableList<ModuleSetRef>>()
  val productsMap = LinkedHashMap<String, MutableList<ProductRef>>()

  // Collect from module sets
  for (entry in allModuleSets) {
    val modules = collectModuleSetModuleNames(pluginGraph, entry.moduleSet.name)
    for (moduleName in modules) {
      val moduleKey = moduleName.value
      moduleSetsMap.computeIfAbsent(moduleKey) { ArrayList() }
        .add(ModuleSetRef(entry.moduleSet.name, entry.location.name, entry.sourceFile))
    }
  }

  // Collect from products
  for (product in products) {
    if (product.contentSpec == null) continue
    for (moduleName in collectProductModuleNames(pluginGraph, product.name)) {
      val moduleKey = moduleName.value
      productsMap.computeIfAbsent(moduleKey) { ArrayList() }
        .add(ProductRef(product.name, product.sourceFile))
    }
  }

  val allModuleNames = (moduleSetsMap.keys + productsMap.keys).sorted()
  val modules = allModuleNames.associateWith { moduleName ->
    ModuleEntry(
      moduleSets = moduleSetsMap.get(moduleName)?.sortedBy { it.name } ?: emptyList(),
      products = productsMap.get(moduleName)?.sortedBy { it.name } ?: emptyList()
    )
  }

  gen.writeRawValue(kotlinxJson.encodeToString(Wrapper(modules)))
}
