// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.util

import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsDependencyElement
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency

/**
 * Checks if a dependency element is included in production runtime classpath.
 *
 * @param element The dependency element to check
 * @param withTests If true, also includes test runtime dependencies
 * @return true if the dependency should be included in the runtime
 */
fun isProductionRuntimeDependency(element: JpsDependencyElement, javaExtensionService: JpsJavaExtensionService, withTests: Boolean = false): Boolean {
  val scope = javaExtensionService.getDependencyExtension(element)?.scope ?: return false
  if (withTests && scope.isIncludedIn(JpsJavaClasspathKind.TEST_RUNTIME)) {
    return true
  }
  return scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)
}

/**
 * Returns a sequence of module dependencies that are included in production runtime.
 * This filters the module's dependencies to only include those that should be
 * packaged with the application at runtime.
 *
 * @param withTests If true, also includes test runtime dependencies
 * @return Sequence of production runtime module dependencies
 */
fun JpsModule.getProductionModuleDependencies(withTests: Boolean = false): Sequence<JpsModuleDependency> {
  return sequence {
    val javaExtensionService = JpsJavaExtensionService.getInstance()
    for (element in dependenciesList.dependencies) {
      if (element is JpsModuleDependency && isProductionRuntimeDependency(element, javaExtensionService, withTests)) {
        yield(element)
      }
    }
  }
}
