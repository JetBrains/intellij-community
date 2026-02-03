// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.devkit.runtimeModuleRepository.generator.ResourcePathsSchema
import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryGenerator
import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryGenerator.COMPACT_REPOSITORY_FILE_NAME
import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryGenerator.JAR_REPOSITORY_FILE_NAME
import com.intellij.devkit.runtimeModuleRepository.generator.RuntimeModuleRepositoryValidator
import com.intellij.devkit.runtimeModuleRepository.generator.isProjectLevel
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.intellij.util.containers.MultiMap
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleTestOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.jps.model.JpsNamedElement
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.pathString

/**
 * Generates a file with descriptors of modules for [com.intellij.platform.runtime.repository.RuntimeModuleRepository].
 * Currently, this function uses information from [DistributionFileEntry] to determine which resources were copied to the distribution and
 * how they are organized. It would be better to rework this: load the module repository file produced during compilation, and use it
 * (along with information from plugin.xml files and other files describing custom layouts of plugins if necessary) to determine which
 * resources should be included in the distribution, instead of taking this information from the project model.
 */
internal suspend fun generateRuntimeModuleRepositoryForDistribution(entries: Sequence<DistributionFileEntry>, context: BuildContext) {
  val repositoryEntries = ArrayList<RuntimeModuleRepositoryEntry>()
  val osSpecificDistPaths = listOf(null to context.paths.distAllDir) +
                            SUPPORTED_DISTRIBUTIONS.map { it to getOsAndArchSpecificDistDirectory(osFamily = it.os, arch = it.arch, libc = it.libcImpl, context = context) }
  for (entry in entries) {
    val (distribution, rootPath) = osSpecificDistPaths.find { entry.path.startsWith(it.second) } ?: continue

    val pathInDist = rootPath.relativize(entry.path).invariantSeparatorsPathString
    repositoryEntries.add(RuntimeModuleRepositoryEntry(distribution = distribution, relativePath = pathInDist, origin = entry))
  }

  if (repositoryEntries.all { it.distribution == null }) {
    generateRepositoryForDistribution(
      targetDirectory = context.paths.distAllDir,
      entries = repositoryEntries,
      context = context,
    )
  }
  else {
    for (distribution in SUPPORTED_DISTRIBUTIONS) {
      val targetDirectory = getOsAndArchSpecificDistDirectory(osFamily = distribution.os, arch = distribution.arch, libc = distribution.libcImpl, context = context)
      val actualEntries = repositoryEntries.filter { it.distribution == null || it.distribution == distribution }
      generateRepositoryForDistribution(
        targetDirectory = targetDirectory,
        entries = actualEntries,
        context = context,
      )
    }
  }
}

/**
 * A variant of [generateRuntimeModuleRepositoryForDistribution] which should be used for 'dev build', when all [entries] correspond to the current OS,
 * and distribution files are generated under [targetDirectory].
 */
@ApiStatus.Internal
suspend fun generateRuntimeModuleRepositoryForDevBuild(entries: Sequence<DistributionFileEntry>, targetDirectory: Path, context: BuildContext) {
  val actualEntries = entries.map { entry ->
    RuntimeModuleRepositoryEntry(
      distribution = null,
      relativePath = targetDirectory.relativize(entry.path).invariantSeparatorsPathString,
      origin = entry,
    )
  }
  generateRepositoryForDistribution(
    targetDirectory = targetDirectory,
    entries = actualEntries.toList(),
    context = context,
  )
}

/**
 * Merges module repositories for different OS to a common one which can be used in the cross-platform distribution. 
 * @return path to the directory with the generated repository file or `null` if [distAllPath] already contains a common module repository file which is used for all OSes
 */
internal fun generateCrossPlatformRepository(distAllPath: Path, osSpecificDistPaths: List<Path>, context: BuildContext): Path? {
  val commonRepositoryFile = distAllPath.resolve(MODULE_DESCRIPTORS_COMPACT_PATH)
  if (commonRepositoryFile.exists()) {
    return null
  }
  
  val repositories = osSpecificDistPaths.map { osSpecificDistPath ->
    val repositoryFile = osSpecificDistPath.resolve(MODULE_DESCRIPTORS_COMPACT_PATH)
    if (!repositoryFile.exists()) {
      context.messages.logErrorAndThrow("Cannot generate runtime module repository for cross-platform distribution: $repositoryFile doesn't exist")
    }
    RuntimeModuleRepositorySerialization.loadFromCompactFile(repositoryFile)
  }
  val commonIds = repositories.map { it.allIds }.reduce { a, b -> a.intersect(b) }
  val commonDescriptors = ArrayList<RawRuntimeModuleDescriptor>()
  for (moduleId in commonIds) {
    val descriptors = repositories.map { it.findDescriptor(moduleId)!! }
    val commonResourcePaths = descriptors.map { it.resourcePaths.toSet() }.reduce { a, b -> a.intersect(b) }
    val commonDependencies = descriptors.first().dependencies
    for (descriptor in descriptors) {
      if (descriptor.dependencies != commonDependencies) {
        context.messages.logErrorAndThrow("Cannot generate runtime module repository for cross-platform distribution: different dependencies for module '$moduleId', ${descriptor.dependencies} and $commonDependencies")
      }
    }
    commonDescriptors.add(RawRuntimeModuleDescriptor.create(moduleId, commonResourcePaths.toList(), commonDependencies))
  }
  val targetDir = context.paths.tempDir.resolve("cross-platform-module-repository")
  RuntimeModuleRepositoryGenerator.saveModuleRepository(commonDescriptors, targetDir)
  return targetDir
}

private data class RuntimeModuleRepositoryEntry(
  @JvmField val distribution: SupportedDistribution?,
  /** Relative path from the distribution root ('Contents' directory on macOS) with '/' as a separator */
  @JvmField val relativePath: String,
  @JvmField val origin: DistributionFileEntry,
)

private suspend fun generateRepositoryForDistribution(
  targetDirectory: Path,
  entries: List<RuntimeModuleRepositoryEntry>,
  context: BuildContext
) {
  val mainPathsForResources = computeMainPathsForResourcesCopiedToMultiplePlaces(entries, context)
  fun isMainPath(element: JpsNamedElement, path: String): Boolean {
    val mainPath = mainPathsForResources[element]
    return mainPath == null || mainPath == path
  }
  
  val moduleProductionPaths = MultiMap.createOrderedSet<JpsModule, String>()
  val moduleTestPaths = MultiMap.createOrderedSet<JpsModule, String>()
  val libraryPaths = MultiMap.createOrderedSet<JpsLibrary, String>()
  for (entry in entries) {
    when (entry.origin) {
      is ModuleOutputEntry -> {
        val module = context.findRequiredModule(entry.origin.owner.moduleName)
        if (isMainPath(module, entry.relativePath)) {
          moduleProductionPaths.putValue(module, entry.relativePath)
        }
      }
      is ModuleTestOutputEntry -> {
        moduleTestPaths.putValue(context.findRequiredModule(entry.origin.moduleName), entry.relativePath)
      }
      is ProjectLibraryEntry -> {
        val library = context.project.libraryCollection.findLibrary(entry.origin.data.libraryName) ?: error("Cannot find project-level library '${entry.origin.data.libraryName}'")
        if (isMainPath(library, entry.relativePath)) {
          libraryPaths.putValue(library, entry.relativePath)
        }
      }
      is ModuleLibraryFileEntry -> {
        val library = entry.origin.findLibrary(context)
        if (isMainPath(library, entry.relativePath)) {
          libraryPaths.putValue(library, entry.relativePath)
        }
      }
      else -> {
        continue
      }
    }
  }

  addMappingForModulesWithoutResources(moduleProductionPaths)
  addMappingsForDuplicatingLibraries(libraryPaths, moduleProductionPaths)

  val distDescriptors = RuntimeModuleRepositoryGenerator.generateRuntimeModuleDescriptors(
    includedProduction = moduleProductionPaths.keySet(),
    includedTests = moduleTestPaths.keySet(),
    includedProjectLibraries = libraryPaths.keySet().filter { it.isProjectLevel },
    resourcePathsSchema = DistributionResourcePathsSchema(moduleProductionPaths, moduleTestPaths, libraryPaths), 
  ).map { descriptor ->
    //this is a temporary workaround to skip optional dependencies which aren't included in the distribution
    val dependenciesToSkip = dependenciesToSkip[descriptor.id] ?: return@map descriptor
    val actualDependencies = descriptor.dependencies.filterNot { it in dependenciesToSkip}
    RawRuntimeModuleDescriptor.create(descriptor.id, descriptor.resourcePaths, actualDependencies)
  }

  val errors = ArrayList<String>()
  val errorReporter = object : RuntimeModuleRepositoryValidator.ErrorReporter {
    override fun reportDuplicatingId(moduleId: String) {
      errors.add("Module '$moduleId' is included several times in the runtime module repository")
    }
  }
  RuntimeModuleRepositoryValidator.validate(distDescriptors, errorReporter)
  require(errors.isEmpty()) {
    "Runtime module repository has ${errors.size} ${StringUtil.pluralize("error", errors.size)}:\n" + errors.joinToString("\n")
  }
  withContext(Dispatchers.IO) {
    RuntimeModuleRepositoryGenerator.saveModuleRepository(descriptors = distDescriptors, targetDirectory = targetDirectory.resolve(RUNTIME_REPOSITORY_MODULES_DIR_NAME))
  }
}

private class DistributionResourcePathsSchema(
  private val moduleProductionPaths: MultiMap<JpsModule, String>,
  private val moduleTestPaths: MultiMap<JpsModule, String>,
  private val libraryPaths: MultiMap<JpsLibrary, String>
) : ResourcePathsSchema {
  override fun moduleOutputPaths(module: JpsModule): List<String> = moduleProductionPaths.get(module).convertToRelative()
  override fun moduleTestOutputPaths(module: JpsModule): List<String> = moduleTestPaths.get(module).convertToRelative()
  override fun libraryPaths(library: JpsLibrary): List<String> = libraryPaths.get(library).convertToRelative()
  
  private fun Collection<String>.convertToRelative(): List<String> = map {
    if (it.startsWith("$RUNTIME_REPOSITORY_MODULES_DIR_NAME/")) it.removePrefix("$RUNTIME_REPOSITORY_MODULES_DIR_NAME/") else "../$it"
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
private suspend fun computeMainPathsForResourcesCopiedToMultiplePlaces(
  entries: List<RuntimeModuleRepositoryEntry>,
  context: BuildContext,
): Map<JpsNamedElement, String> {
  val project = context.project
  val singleFileProjectLibraries = project.libraryCollection.libraries.asSequence()
    .filter { it.getFiles(JpsOrderRootType.COMPILED).size == 1 }
    .mapTo(HashSet()) { it.name }
  
  fun isPackedIntoSingleJar(projectLibraryEntry: ProjectLibraryEntry): Boolean {
    return (projectLibraryEntry.data.libraryName in singleFileProjectLibraries
            || projectLibraryEntry.data.packMode == LibraryPackMode.MERGED
            || projectLibraryEntry.data.packMode == LibraryPackMode.STANDALONE_MERGED)
  }
  
  val pathToEntries = entries.groupBy { Path(it.relativePath) }

  //exclude libraries which may be packed in multiple JARs from consideration, because multiple entries may not indicate that a library is copied to multiple places in such cases,
  //and all resource roots should be kept
  val projectElementToPaths = entries.asSequence()
    .mapNotNull { entry -> 
      val element = when (entry.origin) {
        is ProjectLibraryEntry if isPackedIntoSingleJar(entry.origin) -> project.libraryCollection.findLibrary(entry.origin.data.libraryName)
        is ModuleLibraryFileEntry -> entry.origin.findLibrary(context).takeIf { it.getFiles(JpsOrderRootType.COMPILED).size == 1 }
        is ModuleOutputEntry -> context.findRequiredModule(entry.origin.owner.moduleName)
        else -> null
      }
      element?.let { it to entry.relativePath }
    }
    .groupBy({ it.first }, { Path(it.second) })

  suspend fun isIncludedInEmbeddedFrontend(entry: DistributionFileEntry): Boolean {
    return entry is ModuleOutputEntry && !context.getFrontendModuleFilter().isBackendModule(entry.owner.moduleName)
  }
  
  suspend fun chooseMainLocation(element: JpsNamedElement, paths: List<Path>): String {
    val mainLocation = paths.singleOrNull { it.parent?.pathString == "lib" && !isScrambledWithFrontend(element) } ?:
                       paths.singleOrNull { pathToEntries[it]?.size == 1 } ?:
                       paths.singleOrNull { pathToEntries[it]?.any { entry -> isIncludedInEmbeddedFrontend(entry.origin) } == true } ?:
                       paths.singleOrNull { it.parent?.name in setOf("client", "frontend", "frontend-split") }
    if (mainLocation != null) {
      return mainLocation.invariantSeparatorsPathString
    }
    val sorted = paths.map { it.invariantSeparatorsPathString }.sorted()
    Span.current().addEvent("cannot choose the main location for '${element.name}' among $sorted, the first one will be used")
    return sorted.first()
  }

  val mainPaths = HashMap<JpsNamedElement, String>()
  for ((element, paths) in projectElementToPaths) {
    val distinctPaths = paths.distinct()
    if (distinctPaths.size > 1) {
      mainPaths[element] = chooseMainLocation(element, distinctPaths)
    }
  }
  return mainPaths
}

private fun ModuleLibraryFileEntry.findLibrary(context: BuildContext): JpsLibrary {
  val library = context.findRequiredModule(moduleName).libraryCollection.libraries.find { getLibraryFilename(it) == libraryName }
  require(library != null) { "Cannot find module-level library '$libraryName' in '$moduleName'" }
  return library
}

/** 
 * Include descriptors of aggregating modules which don't have own resources (and therefore don't have DistributionFileEntry), but used from other modules 
 */
private fun addMappingForModulesWithoutResources(moduleProductionPaths: MultiMap<JpsModule, String>) {
  val originalModules = moduleProductionPaths.keySet().toList()
  JpsJavaExtensionService.getInstance().enumerateDependencies(originalModules).runtimeOnly().productionOnly().withoutSdk().withoutLibraries().recursively().processModules { module ->
    if (!moduleProductionPaths.containsKey(module) && module.sourceRoots.none { it.rootType in JavaModuleSourceRootTypes.PRODUCTION }) {
      moduleProductionPaths.putValues(module, emptySet<String>())
    }
  }
}

/**
 * Adds mappings for libraries which aren't explicitly included in the distribution, but their JARs are included as part of other libraries.  
 */
private fun addMappingsForDuplicatingLibraries(libraryPaths: MultiMap<JpsLibrary, String>, moduleProductionPaths: MultiMap<JpsModule, String>) {
  val librariesFromDependencies = HashSet<JpsLibrary>(libraryPaths.keySet())
  JpsJavaExtensionService.getInstance().enumerateDependencies(moduleProductionPaths.keySet()).withoutSdk().runtimeOnly().productionOnly().recursively().processLibraries { 
    librariesFromDependencies.add(it) 
  }

  val librariesByPath = HashMap<Path, MutableList<JpsLibrary>>()
  librariesFromDependencies.forEach { library ->
    library.getPaths(JpsOrderRootType.COMPILED).groupByTo(librariesByPath, { it }) { library }
  }
  val originalEntries = libraryPaths.entrySet().toList()
  for ((library, resourcePathsInDist) in originalEntries) {
    val libraryRoots = library.getPaths(JpsOrderRootType.COMPILED)
    libraryRoots.forEach { resourcePath ->
      librariesByPath[resourcePath]?.forEach { anotherLibrary ->
        if (anotherLibrary != library
            && !libraryPaths.containsKey(anotherLibrary)
            && libraryRoots.containsAll(anotherLibrary.getPaths(JpsOrderRootType.COMPILED))
            && (libraryRoots == anotherLibrary.getPaths(JpsOrderRootType.COMPILED) || resourcePathsInDist.size == 1)) {
          libraryPaths.putValues(anotherLibrary, resourcePathsInDist)
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

internal const val RUNTIME_REPOSITORY_MODULES_DIR_NAME = "modules"
internal const val MODULE_DESCRIPTORS_JAR_PATH: String = "$RUNTIME_REPOSITORY_MODULES_DIR_NAME/$JAR_REPOSITORY_FILE_NAME" 
const val MODULE_DESCRIPTORS_COMPACT_PATH: String = "$RUNTIME_REPOSITORY_MODULES_DIR_NAME/$COMPACT_REPOSITORY_FILE_NAME" 

private val dependenciesToSkip = mapOf(
  //may be removed when IJPL-125 is fixed
  "intellij.platform.buildScripts.downloader" to setOf("lib.zstd-jni"),
)
