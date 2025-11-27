// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.json

import com.fasterxml.jackson.core.JsonGenerator
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.analysis.ModuleDistributionInfo
import org.jetbrains.intellij.build.productLayout.analysis.ModuleSetHierarchyInfo
import org.jetbrains.intellij.build.productLayout.analysis.ModuleSetMetadata
import org.jetbrains.intellij.build.productLayout.analysis.ModuleSetReference
import org.jetbrains.intellij.build.productLayout.analysis.ModuleUsageInfo
import org.jetbrains.intellij.build.productLayout.analysis.ProductReference
import org.jetbrains.intellij.build.productLayout.analysis.ProductSpec
import org.jetbrains.intellij.build.productLayout.collectAllModuleNamesFromSet
import org.jetbrains.intellij.build.productLayout.visitAllModules

// kotlinx.serialization Json instance for serializing data structures
internal val kotlinxJson = Json {
  prettyPrint = false
  encodeDefaults = true
}

/**
 * Enriches products with calculated metrics.
 * Calculates totalModuleCount, directModuleCount, moduleSetCount, and uniqueModuleCount for each product.
 */
fun enrichProductsWithMetrics(
  products: List<ProductSpec>,
  moduleSets: List<ModuleSet>
): List<ProductSpec> {
  return products.map { product ->
    val contentSpec = product.contentSpec
    if (contentSpec == null) {
      product // Return as-is if no contentSpec
    } else {
      // Calculate metrics
      val allModules = mutableSetOf<String>()

      // Collect modules from module sets
      for (msRef in contentSpec.moduleSets) {
        val modulesFromSet = collectAllModuleNamesFromSet(moduleSets, msRef.moduleSet.name)
        allModules.addAll(modulesFromSet)
      }

      // Add additional modules
      for (module in contentSpec.additionalModules) {
        allModules.add(module.name)
      }

      val totalModuleCount = allModules.size
      val directModuleCount = contentSpec.additionalModules.size
      val moduleSetCount = contentSpec.moduleSets.size
      val uniqueModuleCount = allModules.size // Same as total after deduplication

      product.copy(
        totalModuleCount = totalModuleCount,
        directModuleCount = directModuleCount,
        moduleSetCount = moduleSetCount,
        uniqueModuleCount = uniqueModuleCount
      )
    }
  }
}

/**
 * Writes module distribution analysis.
 * For each module, lists which module sets and products use it, plus location information.
 * Replaces TypeScript analyzeModuleDistribution() function.
 * 
 * Output format: { "moduleName": { "inModuleSets": [...], "inProducts": [...], "location": "...", "imlPath": "..." } }
 */
fun writeModuleDistribution(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  moduleLocations: Map<String, org.jetbrains.intellij.build.productLayout.analysis.ModuleLocationInfo>
) {
  // Build module → {inModuleSets: [], inProducts: []} mapping
  val moduleMap = mutableMapOf<String, ModuleDistributionInfo>()

  // Collect modules from module sets (recursively including nested sets)
  for ((moduleSet, _, _) in allModuleSets) {
    visitAllModules(moduleSet) { module ->
      val info = moduleMap.computeIfAbsent(module.name) { ModuleDistributionInfo() }
      info.inModuleSets.add(moduleSet.name)
    }
  }

  // Collect modules from products
  for (product in products) {
    val contentSpec = product.contentSpec ?: continue
    
    // Collect all modules used by this product (from module sets + additional modules)
    val allModulesInProduct = mutableSetOf<String>()
    
    // Collect from module sets (recursively)
    for (msRef in contentSpec.moduleSets) {
      val modulesFromSet = collectAllModuleNamesFromSet(allModuleSets.map { it.moduleSet }, msRef.moduleSet.name)
      allModulesInProduct.addAll(modulesFromSet)
    }
    
    // Add additional modules
    for (module in contentSpec.additionalModules) {
      allModulesInProduct.add(module.name)
    }
    
    // Add to module map
    for (moduleName in allModulesInProduct) {
      val info = moduleMap.computeIfAbsent(moduleName) { ModuleDistributionInfo() }
      if (product.name !in info.inProducts) {
        info.inProducts.add(product.name)
      }
    }
  }

  // Set location information from .idea/modules.xml
  for ((moduleName, info) in moduleMap) {
    val locationInfo = moduleLocations[moduleName]
    if (locationInfo != null) {
      info.location = locationInfo.location
      info.imlPath = locationInfo.imlPath
    }
  }
  
  // Write JSON as object (not array) for direct access by module name
  for ((moduleName, info) in moduleMap.entries.sortedBy { it.key }) {
    gen.writeObjectFieldStart(moduleName)
    
    gen.writeArrayFieldStart("inModuleSets")
    for (setName in info.inModuleSets.sorted()) {
      gen.writeString(setName)
    }
    gen.writeEndArray()
    
    gen.writeArrayFieldStart("inProducts")
    for (productName in info.inProducts.sorted()) {
      gen.writeString(productName)
    }
    gen.writeEndArray()
    
    gen.writeStringField("location", info.location)
    if (info.imlPath != null) {
      gen.writeStringField("imlPath", info.imlPath)
    }
    
    gen.writeEndObject()
  }
}

/**
 * Writes module set hierarchy analysis.
 * For each module set, lists what it includes and what includes it.
 * Replaces TypeScript buildModuleSetHierarchy() function.
 * 
 * Output format: { "setName": { "includes": [...], "includedBy": [...], "moduleCount": N } }
 */
fun writeModuleSetHierarchy(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>
) {
  // Build hierarchy map: moduleSetName → {includes: [], includedBy: [], moduleCount: N}
  val hierarchy = mutableMapOf<String, ModuleSetHierarchyInfo>()

  // First pass: build includes and module counts
  for ((moduleSet, _, _) in allModuleSets) {
    val info = ModuleSetHierarchyInfo(
      includes = moduleSet.nestedSets.map { it.name },
      moduleCount = moduleSet.modules.size
    )
    hierarchy[moduleSet.name] = info
  }

  // Second pass: build reverse references (includedBy)
  for ((moduleSet, _, _) in allModuleSets) {
    for (nestedSet in moduleSet.nestedSets) {
      hierarchy[nestedSet.name]?.includedBy?.add(moduleSet.name)
    }
  }

  // Write JSON as flat object (no "moduleSets" wrapper) for direct access
  for ((setName, info) in hierarchy.entries.sortedBy { it.key }) {
    gen.writeObjectFieldStart(setName)
    
    gen.writeArrayFieldStart("includes")
    for (includedSet in info.includes.sorted()) {
      gen.writeString(includedSet)
    }
    gen.writeEndArray()
    
    gen.writeArrayFieldStart("includedBy")
    for (parentSet in info.includedBy.sorted()) {
      gen.writeString(parentSet)
    }
    gen.writeEndArray()
    
    gen.writeNumberField("moduleCount", info.moduleCount)
    
    gen.writeEndObject()
  }
}

/**
 * Writes module usage index.
 * For each module, provides complete information about where it's used and how to navigate to it.
 * Replaces TypeScript findModuleUsages() function.
 */
fun writeModuleUsageIndex(
  gen: JsonGenerator,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>
) {
  // Build comprehensive usage index
  val usageIndex = mutableMapOf<String, ModuleUsageInfo>()

  // Collect from module sets (recursively including nested sets)
  for ((moduleSet, location, sourceFile) in allModuleSets) {
    visitAllModules(moduleSet) { module ->
      val info = usageIndex.computeIfAbsent(module.name) { ModuleUsageInfo() }
      info.moduleSets.add(ModuleSetReference(moduleSet.name, location, sourceFile))
    }
  }

  // Collect from products
  for (product in products) {
    val contentSpec = product.contentSpec ?: continue
    
    // Collect all modules used by this product
    val allModulesInProduct = mutableSetOf<String>()
    
    // From module sets
    for (msRef in contentSpec.moduleSets) {
      val modulesFromSet = collectAllModuleNamesFromSet(allModuleSets.map { it.moduleSet }, msRef.moduleSet.name)
      allModulesInProduct.addAll(modulesFromSet)
    }
    
    // From additional modules
    for (module in contentSpec.additionalModules) {
      allModulesInProduct.add(module.name)
    }
    
    // Add to index
    for (moduleName in allModulesInProduct) {
      val info = usageIndex.computeIfAbsent(moduleName) { ModuleUsageInfo() }
      info.products.add(ProductReference(product.name, product.sourceFile))
    }
  }

  // Write JSON
  gen.writeObjectFieldStart("modules")
  for ((moduleName, info) in usageIndex.entries.sortedBy { it.key }) {
    gen.writeObjectFieldStart(moduleName)
    
    // Module sets that include this module
    gen.writeArrayFieldStart("moduleSets")
    for (msRef in info.moduleSets.sortedBy { it.name }) {
      gen.writeStartObject()
      gen.writeStringField("name", msRef.name)
      gen.writeStringField("location", msRef.location)
      gen.writeStringField("sourceFile", msRef.sourceFile)
      gen.writeEndObject()
    }
    gen.writeEndArray()
    
    // Products that use this module
    gen.writeArrayFieldStart("products")
    for (prodRef in info.products.sortedBy { it.name }) {
      gen.writeStartObject()
      gen.writeStringField("name", prodRef.name)
      gen.writeStringField("sourceFile", prodRef.sourceFile)
      gen.writeEndObject()
    }
    gen.writeEndArray()
    
    gen.writeEndObject()
  }
  gen.writeEndObject()
}
