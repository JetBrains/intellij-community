// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.serialization.RawProductModules
import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.FrontendModuleFilter
import org.jetbrains.intellij.build.getUnprocessedPluginXmlContent
import org.jetbrains.intellij.build.impl.moduleBased.JpsProductModeMatcher
import org.jetbrains.intellij.build.readPluginContentFromDescriptor
import org.jetbrains.jps.model.JpsNamedElement
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference

internal class FrontendModuleFilterImpl private constructor(
  private val project: JpsProject,
  private val frontendModeMatcher: JpsProductModeMatcher,
  private val includedModuleNames: Set<String>,
  private val includedProjectLibraryNames: Set<String>,
): FrontendModuleFilter {
  companion object {
    fun createFrontendModuleFilter(
      project: JpsProject,
      productModules: RawProductModules,
      context: CompilationContext,
    ): FrontendModuleFilter {
      val frontendModeMatcher = JpsProductModeMatcher(ProductMode.FRONTEND)
      val includedModuleNames = LinkedHashSet<String>()
      val includedProjectLibraryNames = LinkedHashSet<String>()

      for (rootModuleName in productModules.mainGroupModules) {
        val rootModule = project.findModuleByName(rootModuleName.moduleId.stringId) ?: continue
        collectTransitiveDependenciesCompatibleWithFrontend(
          module = rootModule,
          frontendModeMatcher = frontendModeMatcher,
          includedModuleNames = includedModuleNames,
          includedProjectLibraryNames = includedProjectLibraryNames
        )
      }

      for (mainModuleId in productModules.bundledPluginMainModules) {
        val module = project.findModuleByName(mainModuleId.stringId) ?: continue
        if (frontendModeMatcher.matches(module)) {
          includedModuleNames.add(module.name)
          val pluginDescriptor = readXmlAsModel(getUnprocessedPluginXmlContent(module = module, context = context))
          readPluginContentFromDescriptor(pluginDescriptor)
            .mapNotNull { project.findModuleByName(it.first) }
            .filter { frontendModeMatcher.matches(it) }
            .mapTo(includedModuleNames) { it.name }
        }
      }

      return FrontendModuleFilterImpl(
        project = project,
        frontendModeMatcher = frontendModeMatcher,
        includedModuleNames = includedModuleNames,
        includedProjectLibraryNames = includedProjectLibraryNames
      )
    }
  }

  override fun isBackendModule(moduleName: String): Boolean {
    return moduleName !in includedModuleNames
  }

  override fun isBackendProjectLibrary(libraryName: String): Boolean {
    return libraryName !in includedProjectLibraryNames
  }

  override fun isModuleCompatibleWithFrontend(moduleName: String): Boolean {
    val module = project.findModuleByName(moduleName)
    return module != null && frontendModeMatcher.matches(module)
  }
}

const val PLATFORM_MODULE_SCRAMBLED_WITH_FRONTEND: String = "intellij.platform.commercial.license"

val PROJECT_LIBRARIES_SCRAMBLED_WITH_FRONTEND: Set<String> = setOf(
  "LicenseServerAPI",
  "LicenseDecoder",
  "jetbrains.codeWithMe.lobby.server.api",
  "jetbrains.codeWithMe.lobby.server.common",
)

/**
 * Returns `true` if the module or library [element] from the platform part which are also included in the frontend JARs and scrambled (differently) there.
 * It's important not to include JARs for these modules and libraries in the platform part to the classpath of the frontend process, because they may cause clashes.
 */
fun isScrambledWithFrontend(element: JpsNamedElement): Boolean = when (element) {
  is JpsModule -> element.name == PLATFORM_MODULE_SCRAMBLED_WITH_FRONTEND
  is JpsLibrary -> element.name in PROJECT_LIBRARIES_SCRAMBLED_WITH_FRONTEND
  else -> false
}

internal object EmptyFrontendModuleFilter : FrontendModuleFilter {
  override fun isBackendModule(moduleName: String): Boolean = false
  override fun isBackendProjectLibrary(libraryName: String): Boolean = false
  override fun isModuleCompatibleWithFrontend(moduleName: String): Boolean = false
}

private fun collectTransitiveDependenciesCompatibleWithFrontend(
  module: JpsModule,
  frontendModeMatcher: JpsProductModeMatcher,
  includedModuleNames: MutableSet<String>,
  includedProjectLibraryNames: MutableSet<String>,
) {
  if (isScrambledWithFrontend(module) || !frontendModeMatcher.matches(module)) {
    return
  }
  if (!includedModuleNames.add(module.name)) {
    return
  }
  JpsJavaExtensionService.dependencies(module).productionOnly().runtimeOnly().processModuleAndLibraries(
    { depModule ->
      collectTransitiveDependenciesCompatibleWithFrontend(depModule, frontendModeMatcher, includedModuleNames, includedProjectLibraryNames)
    },
    { depLibrary ->
      if (!isScrambledWithFrontend(depLibrary) && depLibrary.createReference().parentReference !is JpsModuleReference) {
        includedProjectLibraryNames.add(depLibrary.name)
      }
    }
  )
}