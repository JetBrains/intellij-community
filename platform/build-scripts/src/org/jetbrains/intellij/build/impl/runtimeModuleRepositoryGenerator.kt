// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.GENERATOR_VERSION
import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.JAR_REPOSITORY_FILE_NAME
import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryValidator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.intellij.util.containers.MultiMap
import com.jetbrains.plugin.structure.base.utils.exists
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.BuildContext
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
internal fun generateRuntimeModuleRepository(entries: Sequence<DistributionFileEntry>, context: BuildContext) {
  val compiledModulesDescriptors = context.originalModuleRepository.rawRepositoryData

  val repositoryEntries = ArrayList<RuntimeModuleRepositoryEntry>()
  val osSpecificDistPaths = listOf(null to context.paths.distAllDir) +
                            SUPPORTED_DISTRIBUTIONS.map { it to getOsAndArchSpecificDistDirectory(it.os, it.arch, context) }
  for (entry in entries) {
    val (distribution, rootPath) = osSpecificDistPaths.find { entry.path.startsWith(it.second) } ?: continue

    val pathInDist = rootPath.relativize(entry.path).pathString
    repositoryEntries.add(RuntimeModuleRepositoryEntry(distribution, pathInDist, entry))
  }

  if (repositoryEntries.all { it.distribution == null }) {
    generateRepositoryForDistribution(
      context.paths.distAllDir, repositoryEntries, compiledModulesDescriptors,
      context
    )
  }
  else {
    SUPPORTED_DISTRIBUTIONS.forEach { distribution ->
      val targetDirectory = getOsAndArchSpecificDistDirectory(distribution.os, distribution.arch, context)
      val actualEntries = repositoryEntries.filter { it.distribution == null || it.distribution == distribution }
      generateRepositoryForDistribution(
        targetDirectory, actualEntries, compiledModulesDescriptors,
        context
      )
    }
  }
}

/**
 * A variant of [generateRuntimeModuleRepository] which should be used for 'dev build', when all [entries] correspond to the current OS,
 * and distribution files are generated under [targetDirectory].
 */
@ApiStatus.Internal
fun generateRuntimeModuleRepositoryForDevBuild(entries: Sequence<DistributionFileEntry>, targetDirectory: Path, context: BuildContext) {
  val compiledModulesDescriptors = context.originalModuleRepository.rawRepositoryData
  val actualEntries = entries.mapNotNull { entry ->
    if (entry.path.startsWith(targetDirectory)) {
      RuntimeModuleRepositoryEntry(
        distribution = null,
        relativePath = targetDirectory.relativize(entry.path).pathString,
        origin = entry
      )
    }
    else {
      context.messages.warning("${entry.path} entry is not under $targetDirectory")
      null
    }
  }
  generateRepositoryForDistribution(
    targetDirectory = targetDirectory,
    entries = actualEntries.toList(),
    compiledModulesDescriptorsData = compiledModulesDescriptors,
    context = context
  )
}

/**
 * Merges module repositories for different OS to a common one which can be used in the cross-platform distribution. 
 * @return path to the generated repository or `null` if [distAllPath] already contains common module repository file which is used for all OSes
 */
internal fun generateCrossPlatformRepository(distAllPath: Path, osSpecificDistPaths: List<Path>, context: BuildContext): Path? {
  val commonRepositoryFile = distAllPath.resolve(MODULE_DESCRIPTORS_JAR_PATH)
  if (commonRepositoryFile.exists()) {
    return null
  }
  
  val repositories = osSpecificDistPaths.map { osSpecificDistPath ->
    val repositoryFile = osSpecificDistPath.resolve(MODULE_DESCRIPTORS_JAR_PATH)
    if (!repositoryFile.exists()) {
      context.messages.error("Cannot generate runtime module repository for cross-platform distribution: $repositoryFile doesn't exist")
    }
    RuntimeModuleRepositorySerialization.loadFromJar(repositoryFile)
  }
  val commonIds = repositories.map { it.allIds }.reduce { a, b -> a.intersect(b) }
  val commonDescriptors = ArrayList<RawRuntimeModuleDescriptor>()
  for (moduleId in commonIds) {
    val descriptors = repositories.map { it.findDescriptor(moduleId)!! }
    val commonResourcePaths = descriptors.map { it.resourcePaths.toSet() }.reduce { a, b -> a.intersect(b) }
    val commonDependencies = descriptors.first().dependencies
    for (descriptor in descriptors) {
      if (descriptor.dependencies != commonDependencies) {
        context.messages.error("Cannot generate runtime module repository for cross-platform distribution: different dependencies for module '$moduleId', ${descriptor.dependencies} and $commonDependencies")
      }
    }
    commonDescriptors.add(RawRuntimeModuleDescriptor.create(moduleId, commonResourcePaths.toList(), commonDependencies))
  }
  val targetFile = context.paths.tempDir.resolve("cross-platform-module-repository").resolve(JAR_REPOSITORY_FILE_NAME)
  saveModuleRepository(commonDescriptors, targetFile, context)
  return targetFile
}

private data class RuntimeModuleRepositoryEntry(val distribution: SupportedDistribution?, val relativePath: String, val origin: DistributionFileEntry)

private fun generateRepositoryForDistribution(
  targetDirectory: Path,
  entries: List<RuntimeModuleRepositoryEntry>,
  compiledModulesDescriptorsData: RawRuntimeModuleRepositoryData,
  context: BuildContext
) {
  val mainPathsForResources = computeMainPathsForResourcesCopiedToMultiplePlaces(entries, context)
  val resourcePathMapping = MultiMap.createOrderedSet<RuntimeModuleId, String>()
  for (entry in entries) {
    val moduleId = entry.origin.getRuntimeModuleId() ?: continue
    val mainPath = mainPathsForResources[moduleId]
    if (mainPath == null || mainPath == entry.relativePath) {
      resourcePathMapping.putValue(moduleId, entry.relativePath)
    }
  }

  val compiledModulesDescriptors = compiledModulesDescriptorsData.allIds.associateBy(
    { RuntimeModuleId.raw(it) }, { compiledModulesDescriptorsData.findDescriptor(it)!! }
  )
  addMappingsForDuplicatingLibraries(resourcePathMapping, compiledModulesDescriptors)

  val transitiveDependencies = LinkedHashSet<RuntimeModuleId>()
  collectTransitiveDependencies(resourcePathMapping.keySet(), compiledModulesDescriptors, transitiveDependencies)

  val distDescriptors = ArrayList<RawRuntimeModuleDescriptor>()
  for ((moduleId, resourcePaths) in resourcePathMapping.entrySet()) {
    val descriptor = compiledModulesDescriptors[moduleId]
    if (descriptor == null) {
      context.messages.warning("Descriptor for '$moduleId' isn't found in module repository ${compiledModulesDescriptorsData.basePath}")
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
    distDescriptors.add(RawRuntimeModuleDescriptor.create(moduleId.stringId, actualResourcePaths, actualDependencies))
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
  saveModuleRepository(distDescriptors, targetDirectory.resolve(MODULE_DESCRIPTORS_JAR_PATH), context)
}

private fun saveModuleRepository(distDescriptors: List<RawRuntimeModuleDescriptor>, targetFile: Path, context: BuildContext) {
  try {
    RuntimeModuleRepositorySerialization.saveToJar(distDescriptors, "intellij.platform.bootstrap", 
                                                   targetFile, GENERATOR_VERSION)
  }
  catch (e: IOException) {
    context.messages.error("Failed to save runtime module repository: ${e.message}", e)
  }
}

/**
 * Some libraries and modules are copied to multiple places in the distribution. 
 * In order to decide which location should be specified in the runtime descriptor, this method determines the main location used the
 * following heuristics:
 *   * the entry from IDE_HOME/lib is preferred (unless it's also included in a separate JAR in the split frontend part and scrambled there);
 *   * otherwise, the entry which is put to a separate JAR file is preferred;
 *   * otherwise, a JAR included in JetBrains Client is preferred.
 *   * otherwise, a JAR located in a directory named 'client' or 'frontend' is preferred.
 * 
 * This heuristic is verified by RuntimeModuleRepositoryChecker.checkIntegrityOfEmbeddedProduct.  
 */
private fun computeMainPathsForResourcesCopiedToMultiplePlaces(entries: List<RuntimeModuleRepositoryEntry>,
                                                               context: BuildContext): Map<RuntimeModuleId, String> {
  val singleFileProjectLibraries = context.project.libraryCollection.libraries.asSequence()
    .filter { it.getFiles(JpsOrderRootType.COMPILED).size == 1 }
    .mapTo(HashSet()) { it.name }
  
  fun isPackedIntoSingleJar(projectLibraryEntry: ProjectLibraryEntry): Boolean {
    return (projectLibraryEntry.data.libraryName in singleFileProjectLibraries
            || projectLibraryEntry.data.packMode == LibraryPackMode.MERGED
            || projectLibraryEntry.data.packMode == LibraryPackMode.STANDALONE_MERGED)
  }
  
  fun ModuleLibraryFileEntry.isPackedIntoSingleJar(): Boolean {
    val library = context.findRequiredModule(moduleName).libraryCollection.libraries.find { getLibraryFilename(it) == libraryName }
    require(library != null) { "Cannot find module-level library '$libraryName' in '$moduleName'" }
    return library.getFiles(JpsOrderRootType.COMPILED).size == 1 
  }
  
  val pathToEntries = entries.groupBy { it.relativePath }

  //exclude libraries which may be packed in multiple JARs from consideration, because multiple entries may not indicate that a library is copied to multiple places in such cases,
  //and all resource roots should be kept
  val moduleIdsToPaths = entries.asSequence()
    .filter { entry -> entry.origin is ProjectLibraryEntry && isPackedIntoSingleJar(entry.origin)
                       || entry.origin is ModuleLibraryFileEntry && entry.origin.isPackedIntoSingleJar()
                       || entry.origin is ModuleOutputEntry }
    .groupBy({ it.origin.getRuntimeModuleId()!! }, { it.relativePath })

  fun DistributionFileEntry.isIncludedInJetBrainsClient() = 
    this is ModuleOutputEntry && context.jetBrainsClientModuleFilter.isModuleIncluded(moduleName) 
  
  fun chooseMainLocation(moduleId: RuntimeModuleId, paths: List<String>): String {
    val mainLocation = paths.singleOrNull { it.substringBeforeLast("/") == "lib" && moduleId !in MODULES_SCRAMBLED_WITH_FRONTEND } ?:
                       paths.singleOrNull { pathToEntries[it]?.size == 1 } ?:
                       paths.singleOrNull { pathToEntries[it]?.any { entry -> entry.origin.isIncludedInJetBrainsClient() } == true } ?:
                       paths.singleOrNull { it.substringBeforeLast("/").substringAfterLast("/") in setOf("client", "frontend") }
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

private fun DistributionFileEntry.getRuntimeModuleId(): RuntimeModuleId? {
  return when (this) {
    is ModuleOutputEntry -> RuntimeModuleId.module(moduleName)
    is ModuleTestOutputEntry -> RuntimeModuleId.moduleTests(moduleName)
    is ModuleLibraryFileEntry -> RuntimeModuleId.moduleLibrary(moduleName, libraryName)
    is ProjectLibraryEntry -> RuntimeModuleId.projectLibrary(data.libraryName)
    is CustomAssetEntry -> null
  }
}

private const val MODULES_DIR_NAME = "modules"
@VisibleForTesting
const val MODULE_DESCRIPTORS_JAR_PATH: String = "$MODULES_DIR_NAME/$JAR_REPOSITORY_FILE_NAME" 

private val dependenciesToSkip = mapOf(
  //may be removed when IJPL-125 is fixed
  "intellij.platform.buildScripts.downloader" to setOf("lib.zstd-jni"),
)
