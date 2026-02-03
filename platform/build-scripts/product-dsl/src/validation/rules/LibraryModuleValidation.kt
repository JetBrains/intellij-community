// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validation.rules

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.file.Files

/**
 * Represents a library dependency that should be replaced with a module dependency.
 */
private data class LibraryViolation(
  @JvmField val libraryName: String,
  @JvmField val libraryModuleName: String,
  @JvmField val isTestScope: Boolean,
  @JvmField val alreadyDependsOnLibraryModule: Boolean,
)

/**
 * Validates that modules don't directly depend on libraries that are exported by library modules.
 * 
 * Library modules (prefixed with `intellij.libraries.`) export one or more project libraries.
 * Modules should depend on the library module instead of the library directly to:
 * - Ensure consistent library versioning across the codebase
 * - Enable proper dependency tracking in the build system
 * - Simplify library updates (only one place to update)
 * 
 * This validation checks both production and test scope dependencies.
 * Returns diffs for .iml files that need fixing. These diffs are auto-applied during
 * "Generate Product Layouts" and shown as FileComparisonFailedError in packaging tests.
 * 
 * @param modulesToCheck Set of module names to validate
 * @param outputProvider Module output provider for accessing JPS modules
 * @return List of diffs for .iml files that need fixing
 */
internal fun validateLibraryModuleDependencies(
  modulesToCheck: Set<String>,
  outputProvider: ModuleOutputProvider,
  strategy: FileUpdateStrategy,
  libraryModuleFilter: (libraryModuleName: String) -> Boolean = { true },
) {
  // Collect all libraries exported by library modules
  val libraryToModuleMap = collectExportedLibraries(modulesToCheck, outputProvider)
  if (libraryToModuleMap.isEmpty()) {
    return
  }
  
  val javaExtensionService = JpsJavaExtensionService.getInstance()
  // Group violations by module name
  val violationsByModule = HashMap<String, MutableList<LibraryViolation>>()
  
  for (moduleName in modulesToCheck) {
    // Skip library modules themselves
    if (moduleName.startsWith(LIB_MODULE_PREFIX)) {
      continue
    }
    
    // Skip modules that are allowed to use exported libraries directly
    if (isAllowedToUseExportedLibrariesDirectly(moduleName)) {
      continue
    }
    
    val module = outputProvider.findModule(moduleName) ?: continue
    val moduleDependencies = module.dependenciesList.dependencies
    
    // Collect module dependencies for checking if module already depends on library module
    val dependsOnModules = moduleDependencies
      .filterIsInstance<JpsModuleDependency>()
      .mapTo(HashSet()) { it.moduleReference.moduleName }
    
    for (dep in moduleDependencies) {
      if (dep !is JpsLibraryDependency) {
        continue
      }
      
      val libRef = dep.libraryReference
      
      // Skip module-level libraries (local to a module, not project libraries)
      if (libRef.parentReference is JpsModuleReference) {
        continue
      }
      
      val libName = libRef.libraryName
      val libraryModuleName = libraryToModuleMap.get(libName) ?: continue

      // Skip if filter says not to replace this library module
      if (!libraryModuleFilter(libraryModuleName)) {
        continue
      }
      
      // Check the scope
      val scope = javaExtensionService.getDependencyExtension(dep)?.scope
      val isTestScope = scope == JpsJavaDependencyScope.TEST
      
      // Check if module already depends on the library module
      val alreadyDependsOnLibraryModule = dependsOnModules.contains(libraryModuleName)
      
      violationsByModule.computeIfAbsent(moduleName) { ArrayList() }.add(LibraryViolation(
        libraryName = libName,
        libraryModuleName = libraryModuleName,
        isTestScope = isTestScope,
        alreadyDependsOnLibraryModule = alreadyDependsOnLibraryModule,
      ))
    }
  }
  
  // Apply fixes for each module with violations
  for ((moduleName, violations) in violationsByModule) {
    applyLibraryModuleFix(moduleName, violations, outputProvider, strategy)
  }
}

/**
 * Applies library module fixes to the module's .iml file.
 */
private fun applyLibraryModuleFix(
  moduleName: String,
  violations: List<LibraryViolation>,
  outputProvider: ModuleOutputProvider,
  strategy: FileUpdateStrategy,
) {
  val module = outputProvider.findModule(moduleName) ?: return
  val imlFile = outputProvider.getModuleImlFile(module)
  
  val currentContent = Files.readString(imlFile)
  val fixedContent = applyLibraryModuleFixes(currentContent, violations)
  
  strategy.writeIfChanged(imlFile, currentContent, fixedContent)
}

/**
 * Applies fixes to .iml content by replacing library dependencies with module dependencies.
 * Uses JDOM for reliable XML manipulation regardless of attribute order.
 */
private fun applyLibraryModuleFixes(content: String, violations: List<LibraryViolation>): String {
  val librariesToReplace = violations
    .filter { !it.alreadyDependsOnLibraryModule }
    .associate { it.libraryName to it.libraryModuleName }
  val librariesToRemove = violations
    .filter { it.alreadyDependsOnLibraryModule }
    .map { it.libraryName }
    .toSet()
  
  if (librariesToReplace.isEmpty() && librariesToRemove.isEmpty()) {
    return content
  }
  
  // Parse XML using JDOM
  val document = JDOMUtil.load(content)
  
  // Find all orderEntry elements (they are inside component/NewModuleRootManager)
  val orderEntries = document.getChildren("component")
    .flatMap { it.getChildren("orderEntry") }
  
  val elementsToRemove = mutableListOf<Element>()
  var changesApplied = 0
  
  for (element in orderEntries) {
    if (element.getAttributeValue("type") != "library") continue
    
    val libName = element.getAttributeValue("name")
    
    // Check if this library should be replaced with module dependency
    val moduleReplacement = librariesToReplace[libName]
    if (moduleReplacement != null) {
      // Save existing attribute values before clearing
      val exported = element.getAttributeValue("exported")
      val scope = element.getAttributeValue("scope")
      
      // Clear all attributes and set in canonical order: type, module-name, exported, scope
      element.attributes.clear()
      element.setAttribute("type", "module")
      element.setAttribute("module-name", moduleReplacement)
      if (exported != null) {
        element.setAttribute("exported", exported)
      }
      if (scope != null) {
        element.setAttribute("scope", scope)
      }
      changesApplied++
    }
    else if (libName in librariesToRemove) {
      elementsToRemove.add(element)
      changesApplied++
    }
  }
  
  // Remove elements
  for (element in elementsToRemove) {
    element.parent.removeContent(element)
  }
  
  // If no changes were applied, return original content unchanged
  if (changesApplied == 0) {
    return content
  }
  
  // Serialize back to string
  return """<?xml version="1.0" encoding="UTF-8"?>""" + "\n" + JDOMUtil.writeElement(document)
}

/**
 * Collects all libraries that are exported by library modules.
 * 
 * @param modulesToCheck Set of module names to scan (filters for library modules internally)
 * @param outputProvider Module output provider for accessing JPS modules
 * @return Map from library name to the library module that exports it
 */
private fun collectExportedLibraries(
  modulesToCheck: Set<String>,
  outputProvider: ModuleOutputProvider,
): Map<String, String> {
  val javaExtensionService = JpsJavaExtensionService.getInstance()
  val result = HashMap<String, String>()
  for (libraryModuleName in modulesToCheck) {
    if (!libraryModuleName.startsWith(LIB_MODULE_PREFIX)) {
      continue
    }
    val module = outputProvider.findModule(libraryModuleName) ?: continue
    
    for (dep in module.dependenciesList.dependencies) {
      if (dep !is JpsLibraryDependency) {
        continue
      }
      
      // Only consider exported libraries
      val depExtension = javaExtensionService.getDependencyExtension(dep)
      if (depExtension?.isExported != true) {
        continue
      }
      
      val libName = dep.library?.name ?: continue
      result.put(libName, libraryModuleName)
    }
  }
  
  return result
}

/**
 * Determines if a module is allowed to directly depend on exported libraries.
 * 
 * Some modules need direct library access due to:
 * - Dual project structures (Fleet, Toolbox) that require direct library references
 * - Modules used in both production and build scripts
 */
private fun isAllowedToUseExportedLibrariesDirectly(moduleName: String): Boolean {
  return when {
    // Fleet modules need direct access due to dual project structure
    moduleName.startsWith("fleet.") -> true
    
    // Toolbox modules need direct access due to dual project structure
    moduleName.startsWith("toolbox.") -> true
    moduleName.startsWith("intellij.station.") -> true
    
    // Specific exceptions for modules used in both production and build scripts
    // https://youtrack.jetbrains.com/issue/IJPL-125
    moduleName == "intellij.platform.buildScripts.downloader" -> true
    
    else -> false
  }
}
