// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.analysis

import java.nio.file.Path

/**
 * Finds all paths from a module to products.
 * Shows how a module reaches products either directly or through module sets.
 * 
 * @param moduleName Name of the module to trace
 * @param allModuleSets All module sets with metadata
 * @param products All products
 * @param projectRoot Project root path for constructing file paths
 * @return Module paths result with all discovered paths
 */
fun findModulePaths(
  moduleName: String,
  allModuleSets: List<ModuleSetMetadata>,
  products: List<ProductSpec>,
  projectRoot: Path
): ModulePathsResult {
  val paths = mutableListOf<ModulePath>()
  
  // Find which module sets directly contain this module
  val moduleInSets = allModuleSets.filter { msEntry ->
    msEntry.moduleSet.modules.any { it.name == moduleName }
  }
  
  // For each product, find how this module is included
  for (prod in products) {
    val contentSpec = prod.contentSpec ?: continue
    
    // Check if module is directly included in the product
    if (contentSpec.additionalModules.any { it.name == moduleName }) {
      paths.add(ModulePath(
        type = "direct",
        path = "$moduleName → ${prod.name}",
        files = listOf(
          PathFileReference(
            type = "product",
            path = prod.pluginXmlPath?.let { projectRoot.resolve(it).toString() },
            name = prod.name,
            note = "directly includes module"
          )
        )
      ))
    }
    
    // Check if module is included via module set
    val productModuleSetNames = contentSpec.moduleSets.map { it.moduleSet.name }.toSet()
    for (moduleSetEntry in moduleInSets) {
      if (productModuleSetNames.contains(moduleSetEntry.moduleSet.name)) {
        paths.add(ModulePath(
          type = "module-set",
          path = "$moduleName → (module set) ${moduleSetEntry.moduleSet.name} → ${prod.name}",
          files = listOf(
            PathFileReference(
              type = "module-set",
              path = projectRoot.resolve(moduleSetEntry.sourceFile).toString(),
              name = moduleSetEntry.moduleSet.name,
              note = "contains module"
            ),
            PathFileReference(
              type = "product",
              path = prod.pluginXmlPath?.let { projectRoot.resolve(it).toString() },
              name = prod.name,
              note = "includes module set"
            )
          )
        ))
      }
    }
  }
  
  return ModulePathsResult(
    module = moduleName,
    paths = paths,
    moduleSets = moduleInSets.map { it.moduleSet.name },
    products = paths.map { path ->
      // Extract product name from path string (after last →)
      path.path.substringAfterLast("→ ").trim()
    }.distinct()
  )
}
