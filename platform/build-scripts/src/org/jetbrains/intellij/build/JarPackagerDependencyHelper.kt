// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.intellij.build

import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.productLayout.util.getProductionModuleDependencies
import org.jetbrains.intellij.build.productLayout.util.isProductionRuntimeDependency
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Whether the plugin's content module [module] is packed into a jar of its own, which is what makes the embedded
 * descriptor take `separate-jar="true"`.
 *
 * Public, and taking the library dependencies as an argument, because two callers need one body: the assembly asks
 * through [JarPackagerDependencyHelper], which caches the dependency list, and the dev-distribution descriptor plan
 * asks during generation so that a produced descriptor needs no project model. A reproduction in the generator would
 * be a second spelling of two branches, and a drift between the two is a wrong attribute in a shipped descriptor.
 */
fun isPluginModulePackedIntoSeparateJar(
  module: JpsModule,
  layout: PluginLayout,
  frontendModuleFilter: FrontendModuleFilter,
  productionLibraryDependencies: List<JpsLibraryDependency>,
): Boolean {
  if (!layout.getModulesWithExcludedModuleLibraries().contains(module.name) &&
      productionLibraryDependencies.any { it.libraryReference.parentReference is JpsModuleReference }) {
    return true
  }
  if (!frontendModuleFilter.isModuleCompatibleWithFrontend(layout.mainModule) && frontendModuleFilter.isModuleCompatibleWithFrontend(module.name)) {
    return true
  }
  return false
}

/** [isPluginModulePackedIntoSeparateJar]'s dependency argument, for a caller with no [JarPackagerDependencyHelper]. */
fun getProductionLibraryDependencies(module: JpsModule): List<JpsLibraryDependency> {
  val javaExtensionService = JpsJavaExtensionService.getInstance()
  return module.dependenciesList.dependencies.filterIsInstance<JpsLibraryDependency>().filter {
    isProductionRuntimeDependency(element = it, javaExtensionService = javaExtensionService, withTests = false)
  }
}

// production-only - JpsJavaClasspathKind.PRODUCTION_RUNTIME
internal class JarPackagerDependencyHelper(private val outputProvider: ModuleOutputProvider) {
  private val productionLibraryCache = ConcurrentHashMap<JpsModule, List<JpsLibraryDependency>>()
  private val testRuntimeLibraryCache = ConcurrentHashMap<JpsModule, List<JpsLibraryDependency>>()

  fun getModuleDependencies(moduleName: String): Sequence<String> {
    return outputProvider.findRequiredModule(moduleName).getProductionModuleDependencies(withTests = false).map { it.moduleReference.moduleName }
  }

  fun isPluginModulePackedIntoSeparateJar(module: JpsModule, layout: PluginLayout, frontendModuleFilter: FrontendModuleFilter): Boolean {
    return isPluginModulePackedIntoSeparateJar(
      module = module,
      layout = layout,
      frontendModuleFilter = frontendModuleFilter,
      productionLibraryDependencies = getLibraryDependencies(module = module, withTests = false),
    )
  }

  fun isTestPluginModule(moduleName: String, module: JpsModule?): Boolean {
    return isTestOnlyPluginModule(moduleName = moduleName, module = module, outputProvider = outputProvider)
  }

  suspend fun getPluginIdByModule(pluginModule: JpsModule): String {
    // it is ok to read the plugin descriptor with unresolved x-include as the ID should be specified at the root
    val root = readXmlAsModel(getUnprocessedPluginXmlContent(module = pluginModule, outputProvider = outputProvider))
    val element = root.getChild("id") ?: root.getChild("name") ?: throw IllegalStateException("Cannot find attribute id or name (module=$pluginModule)")
    return element.content!!
  }

  fun getLibraryDependencies(module: JpsModule, withTests: Boolean): List<JpsLibraryDependency> {
    val cache = if (withTests) testRuntimeLibraryCache else productionLibraryCache
    return cache.computeIfAbsent(module) {
      val javaExtensionService = JpsJavaExtensionService.getInstance()
      val result = mutableListOf<JpsLibraryDependency>()
      for (element in module.dependenciesList.dependencies) {
        if (element is JpsLibraryDependency && isProductionRuntimeDependency(element = element, javaExtensionService = javaExtensionService, withTests = withTests)) {
          result.add(element)
        }
      }
      if (result.isEmpty()) java.util.List.of() else result
    }
  }

  // cool.module.core has dependency on a library cool-library.
  // And it is a plugin.
  //
  // cool.module.part1 has dependency on cool.module.core AND on library cool-library.
  // And it is a plugin that depends on cool.module.core.
  //
  // We should include cool-library only to cool.module.core (same group).
  fun hasLibraryInDependencyChainOfModuleDependencies(dependentModule: JpsModule, libraryName: String, siblings: Collection<ModuleItem>, withTests: Boolean): Boolean {
    val parentGroup = dependentModule.name.let { it.substring(0, it.lastIndexOf('.')) }
    val prefix = "$parentGroup."
    for (dependency in dependentModule.getProductionModuleDependencies(withTests = false)) {
      val moduleName = dependency.moduleReference.moduleName
      if (moduleName == parentGroup) {
        if (getLibraryDependencies(dependency.module ?: continue, withTests).any { it.libraryReference.libraryName == libraryName }) {
          return true
        }
      }
      else if (moduleName.startsWith(prefix) &&
          siblings.none { it.moduleName == moduleName } &&
          getLibraryDependencies(dependency.module ?: continue, withTests).any { it.libraryReference.libraryName == libraryName }) {
        return true
      }
    }
    return false
  }
}
