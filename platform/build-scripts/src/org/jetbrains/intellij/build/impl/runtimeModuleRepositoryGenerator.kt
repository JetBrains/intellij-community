// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.GENERATOR_VERSION
import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.JAR_REPOSITORY_FILE_NAME
import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryValidator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.runtime.repository.MalformedRepositoryException
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.intellij.util.containers.MultiMap
import com.jetbrains.plugin.structure.base.utils.exists
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationTasks
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
  val (repositoryForCompiledModulesPath, compiledModulesDescriptors) = loadForCompiledModules(context)

  val repositoryEntries = ArrayList<RuntimeModuleRepositoryEntry>()
  val osSpecificDistPaths = listOf(null to context.paths.distAllDir) +
                            SUPPORTED_DISTRIBUTIONS.map { it to getOsAndArchSpecificDistDirectory(it.os, it.arch, context) }
  for (entry in entries) {
    val (distribution, rootPath) = osSpecificDistPaths.find { entry.path.startsWith(it.second) }
                                   ?: continue

    val pathInDist = rootPath.relativize(entry.path).pathString
    repositoryEntries.add(RuntimeModuleRepositoryEntry(distribution, pathInDist, entry))
  }

  if (repositoryEntries.all { it.distribution == null }) {
    generateRepositoryForDistribution(context.paths.distAllDir, repositoryEntries, compiledModulesDescriptors,
                                      context, repositoryForCompiledModulesPath)
  }
  else {
    SUPPORTED_DISTRIBUTIONS.forEach { distribution ->
      val targetDirectory = getOsAndArchSpecificDistDirectory(distribution.os, distribution.arch, context)
      val actualEntries = repositoryEntries.filter { it.distribution == null || it.distribution == distribution }
      generateRepositoryForDistribution(targetDirectory, actualEntries, compiledModulesDescriptors,
                                        context, repositoryForCompiledModulesPath)
    }
  }
}

/**
 * A variant of [generateRuntimeModuleRepository] which should be used for 'dev build', when all [entries] correspond to the current OS,
 * and distribution files are generated under [targetDirectory].
 */
@ApiStatus.Internal
fun generateRuntimeModuleRepositoryForDevBuild(entries: Sequence<DistributionFileEntry>, targetDirectory: Path, context: BuildContext) {
  val (repositoryForCompiledModulesPath, compiledModulesDescriptors) = loadForCompiledModules(context)
  val actualEntries = entries.mapNotNull { entry ->
    if (entry.path.startsWith(targetDirectory)) {
      RuntimeModuleRepositoryEntry(distribution = null,
                                   relativePath = targetDirectory.relativize(entry.path).pathString,
                                   origin = entry)
    }
    else {
      context.messages.warning("${entry.path} entry is not under $targetDirectory")
      null
    }
  }
  generateRepositoryForDistribution(targetDirectory = targetDirectory,
                                    entries = actualEntries.toList(),
                                    compiledModulesDescriptors = compiledModulesDescriptors,
                                    context = context,
                                    repositoryForCompiledModulesPath = repositoryForCompiledModulesPath)
}

private fun loadForCompiledModules(context: BuildContext): Pair<Path, Map<RuntimeModuleId, RawRuntimeModuleDescriptor>> {
  // maybe it makes sense to produce the repository along with compiled classes and reuse it
  CompilationTasks.create(context).generateRuntimeModuleRepository()

  val repositoryForCompiledModulesPath = context.classesOutputDirectory.resolve(JAR_REPOSITORY_FILE_NAME)
  if (!repositoryForCompiledModulesPath.exists()) {
    context.messages.error("Runtime module repository wasn't generated during compilation: $repositoryForCompiledModulesPath doesn't exist")
  }
  val compiledModulesDescriptors = try {
    RuntimeModuleRepositorySerialization.loadFromJar(repositoryForCompiledModulesPath).mapKeys { RuntimeModuleId.raw(it.key) }
  }
  catch (e: MalformedRepositoryException) {
    context.messages.error("Failed to load runtime module repository: ${e.message}", e)
    emptyMap<RuntimeModuleId, RawRuntimeModuleDescriptor>()
  }
  return repositoryForCompiledModulesPath to compiledModulesDescriptors
}

private data class RuntimeModuleRepositoryEntry(val distribution: SupportedDistribution?, val relativePath: String, val origin: DistributionFileEntry)

private fun generateRepositoryForDistribution(
  targetDirectory: Path,
  entries: List<RuntimeModuleRepositoryEntry>,
  compiledModulesDescriptors: Map<RuntimeModuleId, RawRuntimeModuleDescriptor>,
  context: BuildContext,
  repositoryForCompiledModulesPath: Path
) {
  val mainPathsForResources = computeMainPathsForResourcesCopiedToMultiplePlaces(entries, context)
  val resourcePathMapping = MultiMap.createOrderedSet<RuntimeModuleId, String>()
  for (entry in entries) {
    val moduleId = entry.origin.runtimeModuleId
    val mainPath = mainPathsForResources[moduleId]
    if (mainPath == null || mainPath == entry.relativePath) {
      resourcePathMapping.putValue(moduleId, entry.relativePath)
    }
  }

  addMappingsForDuplicatingLibraries(resourcePathMapping, compiledModulesDescriptors)

  val transitiveDependencies = LinkedHashSet<RuntimeModuleId>()
  collectTransitiveDependencies(resourcePathMapping.keySet(), compiledModulesDescriptors, transitiveDependencies)

  val distDescriptors = ArrayList<RawRuntimeModuleDescriptor>()
  for ((moduleId, resourcePaths) in resourcePathMapping.entrySet()) {
    val descriptor = compiledModulesDescriptors[moduleId]
    if (descriptor == null) {
      context.messages.warning("Descriptor for '$moduleId' isn't found in module repository $repositoryForCompiledModulesPath")
      continue
    }

    //this is a temporary workaround to skip optional dependencies which aren't included in the distribution
    val dependenciesToSkip = dependenciesToSkip[descriptor.id] ?: emptySet()

    val actualDependencies = descriptor.dependencies.mapNotNull { dependency ->
      when (dependency) {
        in dependenciesToSkip -> null
        "lib.jetbrains-annotations-java5" -> "lib.jetbrains-annotations" //'jetbrains-annotations-java5' isn't included in distribution, 'jetbrains-annotations' is included instead
        else -> dependency
      }
    }
    val actualResourcePaths = resourcePaths.mapTo(ArrayList()) {
      if (it.startsWith("$MODULES_DIR_NAME/")) it.removePrefix("$MODULES_DIR_NAME/") else "../$it"
    }
    distDescriptors.add(RawRuntimeModuleDescriptor(moduleId.stringId, actualResourcePaths, actualDependencies))
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
    RuntimeModuleRepositorySerialization.saveToJar(distDescriptors, "intellij.platform.bootstrap",
                                                   targetDirectory.resolve(MODULE_DESCRIPTORS_JAR_PATH), GENERATOR_VERSION)
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
 *   * otherwise, the entry which is put to a separate JAR file is preferred;
 *   * otherwise, a JAR included in JetBrains Client is preferred.
 */
private fun computeMainPathsForResourcesCopiedToMultiplePlaces(entries: List<RuntimeModuleRepositoryEntry>,
                                                               context: BuildContext): Map<RuntimeModuleId, String> {
  val singleFileProjectLibraries = context.project.libraryCollection.libraries.asSequence()
    .filter { it.getFiles(JpsOrderRootType.COMPILED).size == 1 }
    .mapTo(HashSet()) { it.name }
  
  fun ProjectLibraryEntry.isPackedIntoSingleJar() = data.libraryName in singleFileProjectLibraries 
                                                    || data.packMode == LibraryPackMode.MERGED 
                                                    || data.packMode == LibraryPackMode.STANDALONE_MERGED
  
  val pathToEntries = entries.groupBy { it.relativePath }

  val moduleIdsToPaths = entries.asSequence()
    .filter { entry -> entry.origin is ProjectLibraryEntry && entry.origin.isPackedIntoSingleJar() || entry.origin is ModuleOutputEntry }
    .groupBy({ it.origin.runtimeModuleId }, { it.relativePath })

  fun DistributionFileEntry.isIncludedInJetBrainsClient() = 
    this is ModuleOutputEntry && context.jetBrainsClientModuleFilter.isModuleIncluded(moduleName) 
  
  fun chooseMainLocation(moduleId: RuntimeModuleId, paths: List<String>): String {
    val mainLocation = paths.singleOrNull { it.substringBeforeLast("/") == "lib" } ?:
                       paths.singleOrNull { pathToEntries[it]?.size == 1 } ?:
                       paths.singleOrNull { pathToEntries[it]?.any { entry -> entry.origin.isIncludedInJetBrainsClient() } == true }
    if (mainLocation != null) {
      return mainLocation
    }
    val sorted = paths.sorted()
    Span.current().addEvent("cannot choose the main location for '${moduleId.stringId}' among $sorted, the first one will be used")
    return sorted.first()
  }

  val mainPaths = HashMap<RuntimeModuleId, String>()
  for ((moduleId, paths) in moduleIdsToPaths) {
    val distinctPaths = paths.distinct()
    if (distinctPaths.size > 1) {
      mainPaths[moduleId] = chooseMainLocation(moduleId, distinctPaths)
    }
  }
  return mainPaths
}

/**
 * Adds mappings for libraries which aren't explicitly included in the distribution, but their JARs are included as part of other libraries.  
 */
private fun addMappingsForDuplicatingLibraries(resourcePathMapping: MultiMap<RuntimeModuleId, String>,
                                               compiledModulesDescriptors: Map<RuntimeModuleId, RawRuntimeModuleDescriptor>) {
  val transitiveDependencies = LinkedHashSet<RuntimeModuleId>()
  collectTransitiveDependencies(resourcePathMapping.keySet(), compiledModulesDescriptors, transitiveDependencies)

  val descriptorsByResource = HashMap<String, MutableList<RawRuntimeModuleDescriptor>>()
  compiledModulesDescriptors.values.forEach { descriptor ->
    descriptor.resourcePaths.groupByTo(descriptorsByResource, { it }) { descriptor }
  }
  val includedInMapping = resourcePathMapping.keySet().toMutableSet()
  for ((moduleId, resourcePathsInDist) in resourcePathMapping.entrySet().toList()) {
    val includedDescriptor = compiledModulesDescriptors[moduleId]
    includedDescriptor?.resourcePaths?.forEach { resourcePath ->
      descriptorsByResource[resourcePath]?.forEach { anotherDescriptor ->
        val anotherId = RuntimeModuleId.raw(anotherDescriptor.id)
        if (anotherId.stringId != includedDescriptor.id
            && anotherId in transitiveDependencies
            && includedDescriptor.resourcePaths.containsAll(anotherDescriptor.resourcePaths)
            && (includedDescriptor.resourcePaths == anotherDescriptor.resourcePaths || resourcePathsInDist.size == 1)
            && includedInMapping.add(anotherId)) {
          resourcePathMapping.putValues(anotherId, resourcePathsInDist)
        }
      }
    }
  }
}

private fun collectTransitiveDependencies(moduleIds: Collection<RuntimeModuleId>, descriptorMap: Map<RuntimeModuleId, RawRuntimeModuleDescriptor>,
                                          result: MutableSet<RuntimeModuleId>) {
  for (moduleId in moduleIds) {
    if (result.add(moduleId)) {
      val descriptor = descriptorMap[moduleId]
      if (descriptor != null) {
        collectTransitiveDependencies(descriptor.dependencies.map { RuntimeModuleId.raw(it) }, descriptorMap, result)
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

private const val MODULES_DIR_NAME = "modules"
@VisibleForTesting
const val MODULE_DESCRIPTORS_JAR_PATH: String = "$MODULES_DIR_NAME/$JAR_REPOSITORY_FILE_NAME" 

private val dependenciesToSkip = mapOf(
  //may be removed when IJPL-125 is fixed
  "intellij.platform.buildScripts.downloader" to setOf("lib.zstd-jni", "lib.zstd-jni-windows-aarch64"),
  //RDCT-488
  "intellij.performanceTesting" to setOf(
    "intellij.platform.vcs.impl", 
    "intellij.platform.vcs.log",
    "intellij.platform.vcs.log.impl",
  )
)
