// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.GENERATOR_VERSION
import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.JAR_REPOSITORY_FILE_NAME
import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryValidator
import com.intellij.openapi.util.io.isAncestor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.runtime.repository.MalformedRepositoryException
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.intellij.util.containers.MultiMap
import com.jetbrains.plugin.structure.base.utils.exists
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.impl.PlatformJarNames.APP_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_JAR
import org.jetbrains.intellij.build.impl.projectStructureMapping.*
import org.jetbrains.jps.model.library.JpsOrderRootType
import java.io.IOException
import java.nio.file.Path
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
  val compiledModulesDescriptors: Map<String, RawRuntimeModuleDescriptor>
  try {
    compiledModulesDescriptors = RuntimeModuleRepositorySerialization.loadFromJar(repositoryForCompiledModulesPath)
  }
  catch (e: MalformedRepositoryException) {
    context.messages.error("Failed to load runtime module repository: ${e.message}", e)
    return
  }
  
  val distDescriptors = ArrayList<RawRuntimeModuleDescriptor>()
  val resourcePathMapping = MultiMap.createOrderedSet<String, String>()

  val mainPathsForResources = computeMainPathsForResourcesCopiedToMultiplePlaces(entries, context)

  for (entry in entries) {
    //todo handle entries from OS-specific directories as well
    if (context.paths.distAllDir.isAncestor(entry.path, false)) {
      val moduleId = entry.runtimeModuleId
      if (mainPathsForResources[moduleId].let { it != null && it != entry.path}) {
        continue
      }
      val pathInDist = context.paths.distAllDir.relativize(entry.path).pathString
      val realPathInDist = if (pathInDist != "lib/$PRODUCT_JAR" || context.isEmbeddedJetBrainsClientEnabled) pathInDist else "lib/$APP_JAR"
      resourcePathMapping.putValue(moduleId.stringId, realPathInDist)
    }
  }

  addMappingsForDuplicatingLibraries(resourcePathMapping, compiledModulesDescriptors)

  val transitiveDependencies = LinkedHashSet<String>()
  collectTransitiveDependencies(resourcePathMapping.keySet(), compiledModulesDescriptors, transitiveDependencies)

  for ((moduleId, resourcePaths) in resourcePathMapping.entrySet()) {
    val descriptor = compiledModulesDescriptors[moduleId]
    if (descriptor == null) {
      context.messages.warning("Descriptor for '$moduleId' isn't found in module repository $repositoryForCompiledModulesPath")
      continue
    }
    
    //this is a temporary workaround to skip dependencies which aren't used in production and aren't included in the distribution
    val dependenciesToSkip = dependenciesUsedOnlyWhenRunningFromSources[descriptor.id] ?: emptySet()
    
    distDescriptors.add(RawRuntimeModuleDescriptor(moduleId, resourcePaths.toList(), descriptor.dependencies - dependenciesToSkip))
  }

  /* include descriptors of aggregating modules which don't have own resources (and therefore don't have DistributionFileEntry),
     but used from other modules */
  for (dependencyId in transitiveDependencies) {
    if (!resourcePathMapping.containsKey(dependencyId)) {
      val descriptor = compiledModulesDescriptors[dependencyId]
      if (descriptor != null && descriptor.resourcePaths.isEmpty()) {
        distDescriptors.add(descriptor)
      }
    }
  }

  val errors = ArrayList<String>()
  RuntimeModuleRepositoryValidator.validate(distDescriptors) { errors.add(it) }
  if (errors.isNotEmpty()) {
    context.messages.error("Runtime module repository has ${errors.size} ${StringUtil.pluralize("error", errors.size)}:\n" +
                           errors.joinToString("\n"))
  }
  try {
    RuntimeModuleRepositorySerialization.saveToJar(distDescriptors, context.paths.distAllDir.resolve(JAR_REPOSITORY_FILE_NAME),
                                                   GENERATOR_VERSION)
  }
  catch (e: IOException) {
    context.messages.error("Failed to save runtime module repository: ${e.message}", e)
  }
}

/**
 * Some project-level libraries and modules are copied to multiple places in the distribution. 
 * In order to decide which location should be specified in the runtime descriptor, this method determines the main location used the
 * following heuristics:
 *   * the entry from IDE_HOME/lib is preferred;
 *   * otherwise, the entry which is put to a separate JAR file is preferred.
 */
private fun computeMainPathsForResourcesCopiedToMultiplePlaces(entries: List<DistributionFileEntry>,
                                                               context: BuildContext): Map<RuntimeModuleId, Path> {
  val singleFileProjectLibraries = context.project.libraryCollection.libraries.asSequence()
    .filter { it.getFiles(JpsOrderRootType.COMPILED).size == 1 }
    .mapTo(HashSet()) { it.name }
  val pathToEntries = entries.groupBy { it.path }

  val moduleIdsToPaths = entries.asSequence()
    .filter { entry ->
      (entry is ProjectLibraryEntry && entry.data.libraryName in singleFileProjectLibraries || entry is ModuleOutputEntry) &&
      context.paths.distAllDir.isAncestor(entry.path, true)
    }
    .groupBy({ it.runtimeModuleId }, { it.path })

  val libDir = context.paths.distAllDir.resolve("lib")
  fun chooseMainLocation(moduleId: RuntimeModuleId, paths: List<Path>): Path {
    val mainLocation =  paths.singleOrNull { it.parent == libDir } ?:
                        paths.singleOrNull { pathToEntries[it]?.size == 1 }
    if (mainLocation != null) {
      return mainLocation
    }
    context.messages.warning("Cannot choose the main location for '${moduleId.stringId}' among $paths")
    return paths.min()
  }

  val mainPaths = HashMap<RuntimeModuleId, Path>()
  for ((moduleId, paths) in moduleIdsToPaths) {
    if (paths.size > 1) {
      mainPaths[moduleId] = chooseMainLocation(moduleId, paths)
    }
  }
  return mainPaths
}

/**
 * Adds mappings for libraries which aren't explicitly included in the distribution, but their JARs are included as part of other libraries.  
 */
private fun addMappingsForDuplicatingLibraries(resourcePathMapping: MultiMap<String, String>,
                                               compiledModulesDescriptors: Map<String, RawRuntimeModuleDescriptor>) {
  val descriptorsByResource = HashMap<String, MutableList<RawRuntimeModuleDescriptor>>()
  compiledModulesDescriptors.values.forEach { descriptor ->
    descriptor.resourcePaths.groupByTo(descriptorsByResource, { it }) { descriptor }
  }
  val includedInMapping = resourcePathMapping.keySet().toMutableSet()
  for ((moduleId, resourcePathsInDist) in resourcePathMapping.entrySet().toList()) {
    val includedDescriptor = compiledModulesDescriptors[moduleId]
    includedDescriptor?.resourcePaths?.forEach { resourcePath ->
      descriptorsByResource[resourcePath]?.forEach { anotherDescriptor ->
        if (anotherDescriptor.id != includedDescriptor.id
            && includedDescriptor.resourcePaths.containsAll(anotherDescriptor.resourcePaths)
            && (includedDescriptor.resourcePaths == anotherDescriptor.resourcePaths || resourcePathsInDist.size == 1)
            && includedInMapping.add(anotherDescriptor.id)) {
          resourcePathMapping.putValues(anotherDescriptor.id, resourcePathsInDist)
        }
      }
    }
  }
}

private fun collectTransitiveDependencies(moduleIds: Collection<String>, descriptorMap: Map<String, RawRuntimeModuleDescriptor>, 
                                          result: MutableSet<String>) {
  for (moduleId in moduleIds) {
    if (result.add(moduleId)) {
      val descriptor = descriptorMap[moduleId]
      if (descriptor != null) {
        collectTransitiveDependencies(descriptor.dependencies, descriptorMap, result)
      }
    }
  }
}

private val DistributionFileEntry.runtimeModuleId: RuntimeModuleId
  get() = when (this) {
    is ModuleOutputEntry -> RuntimeModuleId.module(moduleName)
    is ModuleTestOutputEntry -> RuntimeModuleId.moduleTests(moduleName)
    is ModuleLibraryFileEntry -> RuntimeModuleId.moduleLibrary(moduleName, libraryName)
    is ProjectLibraryEntry -> RuntimeModuleId.projectLibrary(data.libraryName)
  }

private val dependenciesUsedOnlyWhenRunningFromSources = mapOf(
  //may be removed when IJPL-125 is fixed
  "intellij.platform.buildScripts.downloader" to setOf("lib.zstd-jni", "lib.zstd-jni-windows-aarch64"), 
)