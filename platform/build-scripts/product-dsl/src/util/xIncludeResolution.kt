// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.util

import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.findFileInModuleDependenciesRecursive
import org.jetbrains.intellij.build.findFileInModuleLibraryDependencies
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Files

/**
 * Resolves an xi:include path relative to a JPS module: own sources first, then library dependencies,
 * module output, transitive module dependencies, and finally any module output.
 */
internal suspend fun resolveXIncludeBytes(
  path: String,
  module: JpsModule,
  outputProvider: ModuleOutputProvider,
  prefix: String?,
): ByteArray? {
  findFileInModuleSources(module, path)?.let { return Files.readAllBytes(it) }
  findFileInModuleLibraryDependencies(module, path, outputProvider)?.let { return it }
  outputProvider.readFileContentFromModuleOutput(module, path)?.let { return it }

  val processedModules = HashSet<String>()
  processedModules.add(module.name)

  findFileInModuleDependenciesRecursive(
    module = module,
    relativePath = path,
    provider = outputProvider,
    processedModules = processedModules,
    moduleNamePrefix = prefix,
  )?.let { return it }

  outputProvider.findFileInAnyModuleOutput(path, prefix, processedModules)?.let { return it }

  return null
}
