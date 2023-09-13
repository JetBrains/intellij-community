// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsDependencyElement
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import java.util.concurrent.ConcurrentHashMap

// production-only - JpsJavaClasspathKind.PRODUCTION_RUNTIME
internal class JarPackagerDependencyHelper(private val context: BuildContext) {
  private val javaExtensionService = JpsJavaExtensionService.getInstance()

  private val libraryCache = ConcurrentHashMap<JpsModule, List<JpsLibraryDependency>>()

  fun getModuleDependencies(moduleName: String): Sequence<String> {
    return getModuleDependencies(context.findRequiredModule(moduleName)).map { it.moduleReference.moduleName }
  }

  private fun getModuleDependencies(module: JpsModule): Sequence<JpsModuleDependency> {
    return sequence {
      for (element in module.dependenciesList.dependencies) {
        if (element is JpsModuleDependency && isProductionRuntime(element)) {
          yield(element)
        }
      }
    }
  }

  fun getLibraryDependencies(module: JpsModule): List<JpsLibraryDependency> {
    return libraryCache.computeIfAbsent(module) {
      val result = mutableListOf<JpsLibraryDependency>()
      for (element in module.dependenciesList.dependencies) {
        if (!isProductionRuntime(element)) {
          continue
        }

        result.add((element as? JpsLibraryDependency) ?: continue)
      }
      if (result.isEmpty()) emptyList() else result
    }
  }

  // cool.module.core has dependency on library cool-library.
  // And it is a plugin.
  //
  // cool.module.part1 has dependency on cool.module.core AND on library cool-library.
  // And it is a plugin that depends on cool.module.core.
  //
  // We should include cool-library only to cool.module.core (same group).
  fun hasLibraryInDependencyChainOfModuleDependencies(dependentModule: JpsModule,
                                                      libraryName: String,
                                                      siblings: Collection<ModuleItem>): Boolean {
    val prefix = dependentModule.name.let { it.substring(0, it.lastIndexOf('.') + 1) }
    for (dependency in getModuleDependencies(dependentModule)) {
      val moduleName = dependency.moduleReference.moduleName
      if (moduleName.startsWith(prefix) &&
          siblings.none { it.moduleName == moduleName } &&
          getLibraryDependencies(dependency.module ?: continue).any { it.libraryReference.libraryName == libraryName }) {
        return true
      }
    }
    return false
  }

  private fun isProductionRuntime(element: JpsDependencyElement): Boolean {
    return javaExtensionService.getDependencyExtension(element)?.scope?.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME) == true
  }
}
