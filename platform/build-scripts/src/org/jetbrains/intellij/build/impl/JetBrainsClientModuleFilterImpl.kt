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
import java.nio.file.Path

class JetBrainsClientModuleFilterImpl(clientMainModuleName: String, context: BuildContext): JetBrainsClientModuleFilter {
  private val includedModules: Set<RuntimeModuleId>
  
  init {
    CompilationTasks.create(context).generateRuntimeModuleRepository()
    val repositoryForCompiledModulesPath = context.classesOutputDirectory.resolve(RuntimeModuleRepositoryBuildConstants.JAR_REPOSITORY_FILE_NAME)
    val repository = RuntimeModuleRepository.create(repositoryForCompiledModulesPath)
    val productModulesFile = findProductModulesFile(context, clientMainModuleName)!!
    val productModules = RuntimeModuleRepositorySerialization.loadProductModules(productModulesFile, repository)
    includedModules = (sequenceOf(productModules.mainModuleGroup) + productModules.bundledPluginModuleGroups.asSequence())
       .flatMap { it.includedModules.asSequence() } 
       .mapTo(HashSet()) { it.moduleDescriptor.moduleId }
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

internal fun findProductModulesFile(context: BuildContext, clientMainModuleName: String): Path? =
  context.findFileInModuleSources(context.findRequiredModule(clientMainModuleName), "META-INF/$clientMainModuleName/product-modules.xml")

object EmptyJetBrainsClientModuleFilter : JetBrainsClientModuleFilter {
  override fun isModuleIncluded(moduleName: String): Boolean = false
  override fun isProjectLibraryIncluded(libraryName: String): Boolean = false
}