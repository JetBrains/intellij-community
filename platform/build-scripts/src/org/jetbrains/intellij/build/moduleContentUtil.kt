// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.lang.ImmutableZipFile
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path

private val rootTypeOrder = arrayOf(JavaResourceRootType.RESOURCE, JavaSourceRootType.SOURCE, JavaResourceRootType.TEST_RESOURCE, JavaSourceRootType.TEST_SOURCE)

internal fun findFileInModuleSources(module: JpsModule, relativePath: String, onlyProductionSources: Boolean = false): Path? {
  for (type in rootTypeOrder) {
    for (root in module.sourceRoots) {
      if (type != root.rootType || (onlyProductionSources && !(root.rootType == JavaResourceRootType.RESOURCE || root.rootType == JavaSourceRootType.SOURCE))) {
        continue
      }
      val sourceFile = JpsJavaExtensionService.getInstance().findSourceFile(root, relativePath)
      if (sourceFile != null) {
        return sourceFile
      }
    }
  }
  return null
}

internal fun isModuleNameLikeFilename(relativePath: String): Boolean = relativePath.startsWith("intellij.") || relativePath.startsWith("fleet.")

internal fun findFileInModuleLibraryDependencies(module: JpsModule, relativePath: String): ByteArray? {
  for (dependency in module.dependenciesList.dependencies) {
    if (dependency is JpsLibraryDependency) {
      val library = dependency.library ?: continue
      for (jarPath in library.getPaths(JpsOrderRootType.COMPILED)) {
        ImmutableZipFile.load(jarPath).use { zipFile ->
          zipFile.getData(relativePath)?.let { return it }
        }
      }
    }
  }
  return null
}