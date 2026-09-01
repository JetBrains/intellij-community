// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.util

import org.jetbrains.intellij.build.DescriptorSearchPass
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.findFileInModuleDependenciesRecursive
import org.jetbrains.intellij.build.findFileInModuleLibraryDependencies
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.readDescriptor
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Files

/**
 * Resolves an xi:include path relative to a JPS module: own sources first, then transitive module dependencies, and
 * only if the checkout has nothing the same walk over module output, the module's library dependencies and finally
 * any module output.
 *
 * The pass order is what keeps the search cheap to miss: the last resort walks *every* module in the project, and in
 * [DescriptorSearchPass.MODULE_OUTPUT] a miss resolves each one's Bazel output, which declares it as an input. See
 * [DescriptorSearchPass].
 */
internal suspend fun resolveXIncludeBytes(
  path: String,
  module: JpsModule,
  outputProvider: ModuleOutputProvider,
  prefix: String?,
  declaredOwner: JpsModule? = null,
): ByteArray? {
  findFileInModuleSources(module, path)?.let { return Files.readAllBytes(it) }

  for (pass in DescriptorSearchPass.entries) {
    // The model names the module that owns a generated module-set descriptor and every `deprecatedInclude`, so a
    // caller that has the model can say where the file is. Without the hint the search reaches the file only through
    // the last resort, which opens an output jar for every module in the project. It reads the owner through
    // [readDescriptor], so the owner obeys the same pass rules as any other candidate.
    if (declaredOwner != null && declaredOwner.name != module.name) {
      readDescriptor(module = declaredOwner, path = path, outputProvider = outputProvider, pass = pass)?.let { return it }
    }

    if (pass == DescriptorSearchPass.MODULE_OUTPUT) {
      findFileInModuleLibraryDependencies(module, path, outputProvider)?.let { return it }
      outputProvider.readFileContentFromModuleOutput(module, path)?.let { return it }
    }

    // Fresh per pass: a shared set would make the output pass skip every module the sources pass visited.
    val processedModules = HashSet<String>()
    processedModules.add(module.name)

    findFileInModuleDependenciesRecursive(
      module = module,
      relativePath = path,
      provider = outputProvider,
      processedModules = processedModules,
      pass = pass,
      moduleNamePrefix = prefix,
    )?.let { return it }

    if (pass == DescriptorSearchPass.MODULE_OUTPUT) {
      outputProvider.findFileInAnyModuleOutput(path, prefix, processedModules)?.let { return it }
    }
  }

  return null
}
