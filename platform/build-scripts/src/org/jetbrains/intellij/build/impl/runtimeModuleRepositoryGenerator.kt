// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.GENERATOR_VERSION
import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.JAR_REPOSITORY_FILE_NAME
import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryValidator
import com.intellij.openapi.util.io.isAncestor
import com.intellij.platform.runtime.repository.MalformedRepositoryException
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.intellij.util.containers.MultiMap
import com.jetbrains.plugin.structure.base.utils.exists
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.impl.projectStructureMapping.*
import java.io.IOException
import kotlin.io.path.pathString

/**
 * Generates a file with descriptors of modules for [com.intellij.platform.runtime.repository.RuntimeModuleRepository]. 
 * Currently, this function uses information from [DistributionFileEntry] to determine which resources were copied to the distribution and
 * how they are organized. It would be better to rework this: load the module repository file produced during compilation, and use it 
 * (along with information from plugin.xml files and other files describing custom layouts of plugins if necessary) to determine which 
 * resources should be included in the distribution, instead of taking this information from the project model.  
 */
internal fun generateRuntimeModuleRepository(entries: List<DistributionFileEntry>, context: BuildContext) {
  //maybe it makes sense to produce the repository along with compiled classes and reuse it 
  CompilationTasks.create(context).generateRuntimeModuleRepository()
  
  val repositoryForCompiledModulesPath = context.classesOutputDirectory.resolve(JAR_REPOSITORY_FILE_NAME)
  if (!repositoryForCompiledModulesPath.exists()) {
    context.messages.error("Runtime module repository wasn't generated during compilation: $repositoryForCompiledModulesPath doesn't exist")
  }
  val compiledModulesDescriptors: MutableMap<String, RawRuntimeModuleDescriptor>
  try {
    compiledModulesDescriptors = RuntimeModuleRepositorySerialization.loadFromJar(repositoryForCompiledModulesPath)
  }
  catch (e: MalformedRepositoryException) {
    context.messages.error("Failed to load runtime module repository: ${e.message}", e)
    return
  }
  
  val distDescriptors = ArrayList<RawRuntimeModuleDescriptor>()
  val resourcePathMapping = MultiMap.createOrderedSet<String, String>()
  for (entry in entries) {
    //todo handle entries from OS-specific directories as well
    if (context.paths.distAllDir.isAncestor(entry.path, false)) {
      val moduleId = entry.runtimeModuleId.stringId
      val targetPath = "../${context.paths.distAllDir.relativize(entry.path).pathString}"
      resourcePathMapping.putValue(moduleId, targetPath)
    }
  }
  for ((moduleId, resourcePaths) in resourcePathMapping.entrySet()) {
    val descriptor = compiledModulesDescriptors[moduleId]
    if (descriptor == null) {
      context.messages.warning("Descriptor for '$moduleId' isn't found in module repository $repositoryForCompiledModulesPath")
      continue
    }
    distDescriptors.add(RawRuntimeModuleDescriptor(moduleId, resourcePaths.toList(), descriptor.dependencies))
  }
  RuntimeModuleRepositoryValidator.validate(distDescriptors) { context.messages.warning("Runtime module repository problem: $it") }
  try {
    RuntimeModuleRepositorySerialization.saveToJar(distDescriptors, context.paths.distAllDir.resolve(JAR_REPOSITORY_FILE_NAME),
                                                   GENERATOR_VERSION)
  }
  catch (e: IOException) {
    context.messages.error("Failed to save runtime module repository: ${e.message}", e)
  }
}

private val DistributionFileEntry.runtimeModuleId: RuntimeModuleId
  get() = when (this) {
    is ModuleOutputEntry -> RuntimeModuleId.module(moduleName)
    is ModuleTestOutputEntry -> RuntimeModuleId.moduleTests(moduleName)
    is ModuleLibraryFileEntry -> RuntimeModuleId.moduleLibrary(moduleName, libraryName)
    is ProjectLibraryEntry -> RuntimeModuleId.projectLibrary(data.libraryName)
  }