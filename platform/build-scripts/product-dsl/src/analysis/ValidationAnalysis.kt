// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.analysis

import com.intellij.openapi.util.JDOMUtil
import org.jetbrains.intellij.build.productLayout.collectAllModuleNamesFromSet
import java.nio.file.Files
import java.nio.file.Path

/**
 * Parses .idea/modules.xml to determine module locations (community vs ultimate).
 * This information is used by validation functions to ensure architectural constraints.
 * 
 * @param projectRoot Absolute path to the project root directory
 * @return Map of module name to ModuleLocationInfo
 */
fun parseModulesXml(projectRoot: Path): Map<String, ModuleLocationInfo> {
  val modulesXmlPath = projectRoot.resolve(".idea/modules.xml")
  
  if (!Files.exists(modulesXmlPath)) {
    return emptyMap()
  }
  
  val modules = mutableMapOf<String, ModuleLocationInfo>()
  
  try {
    val document = JDOMUtil.load(modulesXmlPath)
    
    // Get all <module> elements under <component name="ProjectModuleManager">
    val projectModuleManager = document.getChildren("component")
      .find { it.getAttributeValue("name") == "ProjectModuleManager" }
    
    val modulesParent = projectModuleManager?.getChild("modules") ?: return emptyMap()
    
    for (moduleElement in modulesParent.getChildren("module")) {
      var filepath = moduleElement.getAttributeValue("filepath") ?: continue
      
      // Replace $PROJECT_DIR$ with actual project root
      filepath = filepath.replace("\$PROJECT_DIR\$", projectRoot.toString())
      
      // Extract module name from .iml filename
      val moduleName = Path.of(filepath).fileName.toString().removeSuffix(".iml")
      
      // Determine location based on filepath
      val location = when {
        filepath.contains("/community/") -> "community"
        filepath.contains("/ultimate/") -> "ultimate"
        else -> "unknown"
      }
      
      modules[moduleName] = ModuleLocationInfo(location, filepath)
    }
  }
  catch (e: Exception) {
    // If parsing fails, return empty map (validation will report as unknown)
    System.err.println("Warning: Failed to parse .idea/modules.xml: ${e.message}")
  }
  
  return modules
}

/**
 * Validates that community products don't use ultimate modules.
 * This enforces the architectural constraint that community products must only use community modules.
 * 
 * @param products List of all products
 * @param allModuleSets List of all module sets with metadata
 * @param moduleLocations Map of module names to their locations (from .idea/modules.xml)
 * @param projectRoot Project root path for constructing file paths
 * @return List of violations
 */
fun validateCommunityProducts(
  products: List<ProductSpec>,
  allModuleSets: List<ModuleSetMetadata>,
  moduleLocations: Map<String, ModuleLocationInfo>,
  projectRoot: Path
): List<CommunityProductViolation> {
  val violations = mutableListOf<CommunityProductViolation>()
  
  for (product in products) {
    if (product.pluginXmlPath == null || product.contentSpec == null) continue
    
    // Check if product is in community
    val productFile = projectRoot.resolve(product.pluginXmlPath).toString()
    val isCommunityProduct = productFile.contains("/community/")
    
    if (isCommunityProduct) {
      // Check each module set used by this product
      for (msRef in product.contentSpec.moduleSets) {
        val setName = msRef.moduleSet.name
        val msEntry = allModuleSets.firstOrNull { it.moduleSet.name == setName } ?: continue
        
        // Get ALL modules including those from nested sets (pre-calculated)
        val allModules = collectAllModuleNamesFromSet(allModuleSets.map { it.moduleSet }, setName)
        
        // Find ultimate modules
        val ultimateModules = mutableListOf<String>()
        var communityModulesCount = 0
        var unknownModulesCount = 0
        
        for (moduleName in allModules) {
          val locationInfo = moduleLocations[moduleName]
          when (locationInfo?.location) {
            "ultimate" -> ultimateModules.add(moduleName)
            "community" -> communityModulesCount++
            else -> unknownModulesCount++
          }
        }
        
        if (ultimateModules.isNotEmpty()) {
          violations.add(CommunityProductViolation(
            product = product.name,
            productFile = productFile,
            moduleSet = setName,
            moduleSetFile = projectRoot.resolve(msEntry.sourceFile).toString(),
            ultimateModules = ultimateModules,
            communityModulesCount = communityModulesCount,
            unknownModulesCount = unknownModulesCount,
            totalModulesCount = allModules.size
          ))
        }
      }
    }
  }
  
  return violations
}

/**
 * Validates that module sets are in correct locations (community vs ultimate).
 * Reports violations where:
 * - A module set in community/ contains ultimate modules
 * - A module set in ultimate/ contains only community modules
 * 
 * @param allModuleSets List of all module sets with metadata
 * @param moduleLocations Map of module names to their locations (from .idea/modules.xml)
 * @param projectRoot Project root path for constructing file paths
 * @return List of violations
 */
fun validateModuleSetLocations(
  allModuleSets: List<ModuleSetMetadata>,
  moduleLocations: Map<String, ModuleLocationInfo>,
  projectRoot: Path
): List<ModuleSetLocationViolation> {
  val violations = mutableListOf<ModuleSetLocationViolation>()
  
  for (msEntry in allModuleSets) {
    val ms = msEntry.moduleSet
    val setFile = projectRoot.resolve(msEntry.sourceFile).toString()
    val isInCommunity = setFile.contains("/community/")
    val isInUltimate = setFile.contains("/ultimate/")
    
    // Count community vs ultimate modules in this set
    val ultimateModules = mutableListOf<String>()
    val communityModules = mutableListOf<String>()
    var unknownCount = 0
    
    for (module in ms.modules) {
      val locationInfo = moduleLocations[module.name]
      when (locationInfo?.location) {
        "ultimate" -> ultimateModules.add(module.name)
        "community" -> communityModules.add(module.name)
        else -> unknownCount++
      }
    }
    
    // Violation: Module set in community/ contains ultimate modules
    if (isInCommunity && ultimateModules.isNotEmpty()) {
      violations.add(ModuleSetLocationViolation(
        moduleSet = ms.name,
        file = setFile,
        issue = "community_contains_ultimate",
        ultimateModules = ultimateModules,
        communityModulesCount = communityModules.size,
        unknownModulesCount = unknownCount,
        suggestion = "Move to ultimate/platform-ultimate/resources/META-INF/"
      ))
    }
    
    // Warning: Module set in ultimate/ contains only community modules
    if (isInUltimate && ultimateModules.isEmpty() && communityModules.isNotEmpty()) {
      violations.add(ModuleSetLocationViolation(
        moduleSet = ms.name,
        file = setFile,
        issue = "ultimate_contains_only_community",
        ultimateModulesCount = 0,
        communityModules = communityModules,
        unknownModulesCount = unknownCount,
        suggestion = "Consider if this should be in community/"
      ))
    }
  }
  
  return violations
}
