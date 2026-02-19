// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference

internal fun buildProjectLibraryToModuleMap(modules: Iterable<JpsModule>): Map<String, String> {
  val javaExtensionService = JpsJavaExtensionService.getInstance()
  val result = HashMap<String, String>()

  for (module in modules) {
    val moduleName = module.name
    if (!moduleName.startsWith(LIB_MODULE_PREFIX)) {
      continue
    }

    for (dep in module.dependenciesList.dependencies) {
      if (dep !is JpsLibraryDependency) {
        continue
      }

      val libRef = dep.libraryReference
      if (libRef.parentReference is JpsModuleReference) {
        continue
      }

      val depExtension = javaExtensionService.getDependencyExtension(dep)
      if (depExtension?.isExported != true) {
        continue
      }

      val libName = dep.library?.name ?: libRef.libraryName
      result[libName] = moduleName
    }
  }

  return result
}
