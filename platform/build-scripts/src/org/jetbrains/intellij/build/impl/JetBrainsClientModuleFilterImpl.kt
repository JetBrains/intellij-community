// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.JetBrainsClientModuleFilter

class JetBrainsClientModuleFilterImpl(clientMainModuleName: String, context: BuildContext): JetBrainsClientModuleFilter {
  private val includedModules: Set<RuntimeModuleId>
  
  init {
    CompilationTasks.create(context).generateRuntimeModuleRepository()
    val repositoryForCompiledModulesPath = context.classesOutputDirectory.resolve(RuntimeModuleRepositoryBuildConstants.JAR_REPOSITORY_FILE_NAME)
    val repository = RuntimeModuleRepository.create(repositoryForCompiledModulesPath)
    val moduleOutputDir = context.getModuleOutputDir(context.findRequiredModule(clientMainModuleName))
    val productModules = RuntimeModuleRepositorySerialization.loadProductModules(
      moduleOutputDir.resolve("META-INF/$clientMainModuleName/product-modules.xml"), repository)
    includedModules = (productModules.rootPlatformModules.map { it.moduleDescriptor } + productModules.bundledPluginMainModules).flatMapTo(
      HashSet()) {
      repository.collectDependencies(it)
    }
  }

  override fun isModuleIncluded(moduleName: String): Boolean {
    return RuntimeModuleId.module(moduleName) in includedModules
  }

  override fun isProjectLibraryIncluded(libraryName: String): Boolean {
    return RuntimeModuleId.projectLibrary(libraryName) in includedModules
  }

  private fun RuntimeModuleRepository.collectDependencies(moduleDescriptor: RuntimeModuleDescriptor,
                                                          result: MutableSet<RuntimeModuleId> = HashSet()): Set<RuntimeModuleId> {
    if (result.add(moduleDescriptor.moduleId)) {
      for (dependency in moduleDescriptor.dependencies) {
        collectDependencies(dependency, result)
      }
    }
    return result
  }
}

object EmptyJetBrainsClientModuleFilter : JetBrainsClientModuleFilter {
  override fun isModuleIncluded(moduleName: String): Boolean = false
  override fun isProjectLibraryIncluded(libraryName: String): Boolean = false
}