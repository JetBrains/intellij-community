// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.pluginGraph.ContentModuleName
import org.jdom.Element
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.file.Files

/**
 * Library module replacement validation.
 *
 * Purpose: Replace direct project library deps with library module deps (`intellij.libraries.*`).
 * Inputs: plugin graph, JPS model, libraryModuleFilter.
 * Output: `.iml` updates.
 * Auto-fix: yes.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/library-module.md.
 */
internal object LibraryModuleValidator : PipelineNode {
  override val id get() = NodeIds.LIBRARY_MODULE_VALIDATION

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val graph = model.pluginGraph
    val outputProvider = model.config.outputProvider
    val strategy = model.fileUpdater
    val updateSuppressions = model.updateSuppressions
    if (updateSuppressions) {
      return
    }

    // Map of project library name -> library module name (built from JPS library modules).
    val libraryToModuleMap = model.config.projectLibraryToModuleMap
    if (libraryToModuleMap.isEmpty()) {
      return
    }

    val javaExtensionService = JpsJavaExtensionService.getInstance()
    // Group violations by module name
    val violationsByModule = HashMap<ContentModuleName, MutableList<LibraryViolation>>()

    // Validate content modules from graph
    graph.query {
      contentModules { contentModule ->
        val moduleName = contentModule.name()
        val moduleNameValue = moduleName.value

        // Skip library modules themselves
        if (moduleNameValue.startsWith(LIB_MODULE_PREFIX)) {
          return@contentModules
        }

        // Skip modules that are allowed to use exported libraries directly
        if (isAllowedToUseExportedLibrariesDirectly(moduleNameValue)) {
          return@contentModules
        }

        val module = outputProvider.findModule(moduleNameValue) ?: return@contentModules
        val moduleDependencies = module.dependenciesList.dependencies

        // Collect module dependencies for checking if module already depends on library module
        val dependsOnModules = moduleDependencies
          .filterIsInstance<JpsModuleDependency>()
          .mapTo(HashSet()) { ContentModuleName(it.moduleReference.moduleName) }

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
          val libraryModuleName = ContentModuleName(libraryToModuleMap.get(libName) ?: continue)
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
    }

    for ((moduleName, violations) in violationsByModule) {
      applyLibraryModuleFix(moduleName, violations, outputProvider, strategy)
    }
  }
}

/**
 * Represents a library dependency that should be replaced with a module dependency.
 */
private data class LibraryViolation(
  @JvmField val libraryName: String,
  val libraryModuleName: ContentModuleName,
  @JvmField val isTestScope: Boolean,
  @JvmField val alreadyDependsOnLibraryModule: Boolean,
)

/**
 * Applies library module fixes to the module's .iml file.
 */
private fun applyLibraryModuleFix(
  moduleName: ContentModuleName,
  violations: List<LibraryViolation>,
  outputProvider: ModuleOutputProvider,
  strategy: FileUpdateStrategy,
) {
  val module = outputProvider.findModule(moduleName.value) ?: return
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
    .associate { it.libraryName to it.libraryModuleName.value }
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
