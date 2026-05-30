// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.moduleRepository

import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimePluginHeader
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.intellij.util.containers.MultiMap
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.classPath.PluginBuildDescriptor
import org.jetbrains.intellij.build.classPath.getEmbeddedProductTempPluginDir
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.LibraryPackMode
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.SUPPORTED_DISTRIBUTIONS
import org.jetbrains.intellij.build.impl.SupportedDistribution
import org.jetbrains.intellij.build.impl.createPlatformLayout
import org.jetbrains.intellij.build.impl.getLibraryFilename
import org.jetbrains.intellij.build.impl.getOsAndArchSpecificDistDirectory
import org.jetbrains.intellij.build.impl.getPluginLayoutsByJpsModuleNames
import org.jetbrains.intellij.build.impl.isScrambledWithFrontend
import org.jetbrains.intellij.build.impl.layoutPlatformDistribution
import org.jetbrains.intellij.build.impl.moduleRepository.RuntimeModuleRepositoryGenerator.COMPACT_REPOSITORY_FILE_NAME
import org.jetbrains.intellij.build.impl.moduleRepository.RuntimeModuleRepositoryGenerator.JAR_REPOSITORY_FILE_NAME
import org.jetbrains.intellij.build.impl.plugins.buildPlugins
import org.jetbrains.intellij.build.impl.projectStructureMapping.ContentReport
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleTestOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.telemetry.use
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
internal suspend fun generateRuntimeModuleRepositoryForDistribution(
  contentReport: ContentReport,
  context: BuildContext,
  platformLayout: PlatformLayout,
) {
  val osSpecificDistPaths = listOf(null to context.paths.distAllDir) +
                            SUPPORTED_DISTRIBUTIONS.map { it to getOsAndArchSpecificDistDirectory(osFamily = it.os, arch = it.arch, libc = it.libcImpl, context = context) }

  fun convertToRuntimeModuleRepositoryEntry(entry: DistributionFileEntry): RuntimeModuleRepositoryEntry? {
    val (distribution, rootPath) = osSpecificDistPaths.find { entry.path.startsWith(it.second) } ?: return null

    val pathInDist = rootPath.relativize(entry.path).invariantSeparatorsPathString
    return RuntimeModuleRepositoryEntry(distribution = distribution, relativePath = pathInDist, origin = entry)
  }

  val platformEntries = contentReport.platform.mapNotNull(::convertToRuntimeModuleRepositoryEntry)
  val bundledPluginEntries = contentReport.bundledPlugins.flatMap {
    it.distribution.mapNotNull(::convertToRuntimeModuleRepositoryEntry)
  }

  if (platformEntries.all { it.distribution == null } && bundledPluginEntries.all { it.distribution == null }
      && contentReport.bundledPlugins.all { it.os == null && it.arch == null }) {
    generateRepositoryForDistribution(
      targetDirectory = context.paths.distAllDir,
      platformEntries = platformEntries,
      bundledPluginEntries = bundledPluginEntries,
      bundledPlugins = contentReport.bundledPlugins,
      platformLayout = platformLayout,
      context = context,
    )
  }
  else {
    SUPPORTED_DISTRIBUTIONS
      .filter { context.shouldBuildDistributionForOS(it.os, it.arch) }
      .forEach { distribution ->
        val targetDirectory = getOsAndArchSpecificDistDirectory(osFamily = distribution.os, arch = distribution.arch, libc = distribution.libcImpl, context = context)
        val actualPlatformEntries = platformEntries.filter { it.distribution == null || it.distribution == distribution }
        val actualBundledPluginEnries = bundledPluginEntries.filter { it.distribution == null || it.distribution == distribution }
        val actualPlugins = contentReport.bundledPlugins.filter { (it.os == null || it.os == distribution.os) && (it.arch == null || it.arch == distribution.arch) }
        generateRepositoryForDistribution(
          targetDirectory = targetDirectory,
          platformEntries = actualPlatformEntries,
          bundledPluginEntries = actualBundledPluginEnries,
          bundledPlugins = actualPlugins,
          context = context,
          platformLayout = platformLayout,
        )
    }
  }
}

/**
 * A variant of [generateRuntimeModuleRepositoryForDistribution] which should be used for 'dev build', when all entries correspond to the current OS,
 * and distribution files are generated under [targetDirectory].
 */
internal suspend fun generateRuntimeModuleRepositoryForDevBuild(
  contentReport: ContentReport,
  targetDirectory: Path,
  context: BuildContext,
  platformLayout: PlatformLayout
) {
  val platformEntries = contentReport.platform.map { entry ->
    RuntimeModuleRepositoryEntry(
      distribution = null,
      relativePath = targetDirectory.relativize(entry.path).invariantSeparatorsPathString,
      origin = entry,
    )
  }
  val bundledPluginEntries = contentReport.bundledPlugins.flatMap { plugin ->
    plugin.distribution.map { entry ->
      RuntimeModuleRepositoryEntry(
        distribution = null,
        relativePath = targetDirectory.relativize(entry.path).invariantSeparatorsPathString,
        origin = entry,
      )
    }

  }
  generateRepositoryForDistribution(
    targetDirectory = targetDirectory,
    platformEntries = platformEntries,
    bundledPluginEntries = bundledPluginEntries,
    bundledPlugins = contentReport.bundledPlugins,
    platformLayout = platformLayout,
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
  val commonIds = repositories.map { it.allModuleIds }.reduce { a, b -> a.intersect(b) }
  val commonPluginDescriptorModules = repositories
    .map { repository -> repository.pluginHeaders.mapTo(HashSet()) { it.pluginDescriptorModuleId } }
    .reduce<Set<RuntimeModuleId>, Set<RuntimeModuleId>> { a, b -> a.intersect(b) }
  val commonDescriptors = ArrayList<RawRuntimeModuleDescriptor>()
  for (moduleId in commonIds) {
    val descriptors = repositories.map { it.findDescriptor(moduleId)!! }
    val commonResourcePaths = descriptors.map { it.resourcePaths.toSet() }.reduce { a, b -> a.intersect(b) }
    val commonDependencies = descriptors.first().dependencyIds
    for (descriptor in descriptors) {
      if (descriptor.dependencyIds != commonDependencies) {
        context.messages.logErrorAndThrow("Cannot generate runtime module repository for cross-platform distribution: different dependencies for module '${moduleId.displayName}', ${descriptor.dependencyIds} and $commonDependencies")
      }
    }
    commonDescriptors.add(RawRuntimeModuleDescriptor.create(moduleId, commonResourcePaths.toList(), commonDependencies))
  }
  val commonPluginHeaders = ArrayList<RuntimePluginHeader>()
  for (pluginDescriptorModule in commonPluginDescriptorModules) {
    val headers = repositories.map { repository -> repository.pluginHeaders.single { it.pluginDescriptorModuleId == pluginDescriptorModule } }
    val header = headers.first()
    for (anotherHeader in headers.drop(1)) {
      if (header.pluginId != anotherHeader.pluginId || header.includedModules != anotherHeader.includedModules) {
        context.messages.logErrorAndThrow("Cannot generate runtime module repository for cross-platform distribution: different plugin headers for module '${pluginDescriptorModule.displayName}': $header and $anotherHeader")
      }
    }
    commonPluginHeaders.add(header)
  }
  val targetDir = context.paths.tempDir.resolve("cross-platform-module-repository")
  RuntimeModuleRepositoryGenerator.saveModuleRepository(commonDescriptors, commonPluginHeaders, targetDir)
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
  platformEntries: List<RuntimeModuleRepositoryEntry>,
  bundledPluginEntries: List<RuntimeModuleRepositoryEntry>,
  context: BuildContext,
  bundledPlugins: List<PluginBuildDescriptor>,
  platformLayout: PlatformLayout,
) {
  val entries = platformEntries + bundledPluginEntries
  val mainPathsForResources = computeMainPathsForResourcesCopiedToMultiplePlaces(entries, context)
  fun isMainPath(element: JpsNamedElement, path: String): Boolean {
    val mainPath = mainPathsForResources[element]
    return mainPath == null || mainPath == path
  }
  
  val moduleProductionPaths = MultiMap.createOrderedSet<JpsModule, String>()
  val moduleTestPaths = MultiMap.createOrderedSet<JpsModule, String>()
  val libraryPaths = MultiMap.createOrderedSet<JpsLibrary, String>()
  val jarPackagerDependencyHelper = (context as BuildContextImpl).jarPackagerDependencyHelper
  for (entry in entries) {
    when (entry.origin) {
      is ModuleOutputEntry -> {
        val module = context.outputProvider.findRequiredModule(entry.origin.owner.moduleName)
        if (isMainPath(module, entry.relativePath)) {
          if (!jarPackagerDependencyHelper.isTestPluginModule(entry.origin.owner.moduleName, module) && !hasTestSourcesAndNoProductionSources(module)) {
            moduleProductionPaths.putValue(module, entry.relativePath)
          }
          else {
            moduleTestPaths.putValue(module, entry.relativePath)
          }
        }
      }
      is ModuleTestOutputEntry -> {
        moduleTestPaths.putValue(context.outputProvider.findRequiredModule(entry.origin.moduleName), entry.relativePath)
      }
      is ProjectLibraryEntry -> {
        val library = context.project.libraryCollection.findLibrary(entry.origin.data.libraryName) ?: error("Cannot find project-level library '${entry.origin.data.libraryName}'")
        if (isMainPath(library, entry.relativePath)) {
          libraryPaths.putValue(library, entry.relativePath)
        }
      }
      is ModuleLibraryFileEntry -> {
        val library = entry.origin.findLibrary(context.outputProvider)
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

  val additionalFrontendPlugins = computeDescriptorsForAdditionalFrontendPlugins(context, platformLayout)
  val corePluginDescriptorModuleName = context.productProperties.applicationInfoModule
  val embeddedFrontendDescriptorModuleName = context.getEmbeddedFrontendProductContext()?.productProperties?.applicationInfoModule
  val contentModuleDetector = ContentModuleDetectorImpl(
    platformLayout,
    corePluginDescriptorModuleName,
    platformEntries.map { it.origin },
    bundledPlugins + additionalFrontendPlugins,
    embeddedFrontendDescriptorModuleName,
    context.project
  )
  val distDescriptors = RuntimeModuleRepositoryGenerator.generateRuntimeModuleDescriptors(
    includedProduction = moduleProductionPaths.keySet(),
    includedTests = moduleTestPaths.keySet(),
    includedProjectLibraries = libraryPaths.keySet().filter { it.isProjectLevel },
    resourcePathsSchema = DistributionResourcePathsSchema(moduleProductionPaths, moduleTestPaths, libraryPaths),
    contentModuleDetector = contentModuleDetector,
  ).map(::removeSkippedDistributionDependencies)

  val errors = ArrayList<String>()
  val errorReporter = object : RuntimeModuleRepositoryValidator.ErrorReporter {
    override fun reportError(errorMessage: String) {
      errors.add(errorMessage)
    }
  }
  val pluginHeaders = contentModuleDetector.pluginHeaders
  RuntimeModuleRepositoryValidator.validate(distDescriptors, pluginHeaders, errorReporter)
  if (errors.isNotEmpty()) {
    context.messages.logErrorAndThrow(
      "Runtime module repository which is used to run the frontend process has ${errors.size} ${StringUtil.pluralize("error", errors.size)}:\n " +
      errors.joinToString("\n ")
    )
  }
  withContext(Dispatchers.IO) {
    RuntimeModuleRepositoryGenerator.saveModuleRepository(
      descriptors = distDescriptors,
      pluginHeaders = pluginHeaders,
      targetDirectory = targetDirectory.resolve(RUNTIME_REPOSITORY_MODULES_DIR_NAME)
    )
  }
}

/**
 * Returns the list of descriptors for additional plugins which should be added to the runtime module repository.
 * These plugins are not bundled with the IDE, but they are used from the frontend process started from the IDE.
 * To be able to run the frontend process from a regular IDE, we need to include information about its modules to the runtime module repository.
 */
private suspend fun computeDescriptorsForAdditionalFrontendPlugins(
  context: BuildContext,
  platformLayout: PlatformLayout,
): List<PluginBuildDescriptor> {
  return TraceManager.spanBuilder("compute layout of additional plugins for embedded frontend").use {
    val embeddedFrontendContext = context.getEmbeddedFrontendProductContext() ?: return@use emptyList()

    //creates a descriptor for the core plugin of the embedded frontend
    val embeddedFrontendTargetDir = getEmbeddedProductTempPluginDir(context, embeddedFrontendContext.productProperties.applicationInfoModule)
    val embeddedFrontendPlatformEntries = layoutPlatformDistribution(
      moduleOutputPatcher = ModuleOutputPatcher(),
      targetDir = embeddedFrontendTargetDir,
      platform = createPlatformLayout(embeddedFrontendContext),
      searchableOptionSet = null,
      copyFiles = false,
      context = embeddedFrontendContext,
    )

    val additionalFrontendPlugins = mutableListOf(
      PluginBuildDescriptor(
        dir = embeddedFrontendTargetDir,
        os = null,
        arch = null,
        layout = PluginLayout.plugin(embeddedFrontendContext.productProperties.applicationInfoModule),
        distribution = embeddedFrontendPlatformEntries,
      )
    )

    val additionalPluginModules = embeddedFrontendContext.getBundledPluginModules() - context.getBundledPluginModules().toSet()
    if (additionalPluginModules.isNotEmpty()) {
      /* generate descriptors for custom 'Xxx for JetBrains Client' plugins, which are not bundled with the IDE but are used in the frontend process; eventually we'll get rid of
         them (see IJPL-220139) */
      val additionalPluginModuleLayouts = getPluginLayoutsByJpsModuleNames(additionalPluginModules, embeddedFrontendContext.productProperties.productLayout)
      additionalFrontendPlugins.addAll(buildPlugins(
        plugins = additionalPluginModuleLayouts,
        os = null,
        arch = null,
        targetDir = context.paths.tempDir.resolve("frontend-plugins-layout"),
        platformEntriesProvider = null,
        searchableOptionSet = null,
        descriptorCacheContainer = platformLayout.descriptorCacheContainer,
        state = context.distributionState(),
        context = context,
        copyFiles = false,
      ))
    }
    additionalFrontendPlugins
  }
}

internal fun hasTestSourcesAndNoProductionSources(module: JpsModule): Boolean {
  val sourceRoots = module.sourceRoots
  return sourceRoots.isNotEmpty() && sourceRoots.all { it.rootType.isForTests }
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
 *   * otherwise, a JAR located in a directory named 'client', 'frontend' or 'frontend-split' is preferred.
 * 
 * This heuristic is verified by RuntimeModuleRepositoryChecker.checkIntegrityOfEmbeddedProduct.
 * It would be better to get rid of this heuristic and store multiple descriptors for such libraries and modules instead, see IJPL-243081.
 */
private fun computeMainPathsForResourcesCopiedToMultiplePlaces(
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
        is ModuleLibraryFileEntry -> entry.origin.findLibrary(context.outputProvider).takeIf { it.getFiles(JpsOrderRootType.COMPILED).size == 1 }
        is ModuleOutputEntry -> context.outputProvider.findRequiredModule(entry.origin.owner.moduleName)
        else -> null
      }
      element?.let { it to entry.relativePath }
    }
    .groupBy({ it.first }, { Path(it.second) })

  fun chooseMainLocation(element: JpsNamedElement, paths: List<Path>): String {
    val mainLocation = paths.singleOrNull { it.parent?.pathString == "lib" && !isScrambledWithFrontend(element) && !it.name.endsWith("-backend.jar") } ?:
                       paths.singleOrNull { pathToEntries[it]?.size == 1 } ?:
                       paths.singleOrNull { it.parent?.name in setOf("client", "frontend", "frontend-split") } ?:
                       paths.find { pathToEntries[it]?.size == 1 }
    if (mainLocation != null) {
      return mainLocation.invariantSeparatorsPathString
    }
    val sorted = paths.sortedWith(
      compareBy<Path> { pathToEntries[it]?.size ?: 0 }.thenComparing { it.invariantSeparatorsPathString }
    ).map { it.invariantSeparatorsPathString }
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

private fun ModuleLibraryFileEntry.findLibrary(outputProvider: ModuleOutputProvider): JpsLibrary {
  val library = outputProvider.findRequiredModule(moduleName).libraryCollection.libraries.find { getLibraryFilename(it) == libraryName }
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
        collectTransitiveDependencies(descriptor.dependencyIds, descriptorMap, result)
      }
    }
  }
}

private fun removeSkippedDistributionDependencies(descriptor: RawRuntimeModuleDescriptor): RawRuntimeModuleDescriptor {
  val actualDependencies = removeSkippedDistributionDependencies(
    moduleName = descriptor.moduleId.name,
    dependencyIds = descriptor.dependencyIds,
  )
  if (actualDependencies.size == descriptor.dependencyIds.size) {
    return descriptor
  }
  return RawRuntimeModuleDescriptor.create(descriptor.moduleId, descriptor.visibility, descriptor.resourcePaths, actualDependencies)
}

private fun removeSkippedDistributionDependencies(moduleName: String, dependencyIds: List<RuntimeModuleId>): List<RuntimeModuleId> {
  val actualDependencyIds = removeSkippedDistributionDependencyIds(
    moduleName = moduleName,
    dependencyIds = dependencyIds.map(RuntimeModuleId::getName),
  )
  if (actualDependencyIds.size == dependencyIds.size) {
    return dependencyIds
  }
  val dependencyIdsByName = dependencyIds.associateBy(RuntimeModuleId::getName)
  return actualDependencyIds.map(dependencyIdsByName::getValue)
}

@VisibleForTesting
fun removeSkippedDistributionDependencyIds(moduleName: String, dependencyIds: List<String>): List<String> {
  val dependenciesToSkip = dependenciesToSkip.get(moduleName) ?: return dependencyIds
  if (dependencyIds.none { it in dependenciesToSkip }) {
    return dependencyIds
  }
  return dependencyIds.filterNot { it in dependenciesToSkip }
}

internal const val RUNTIME_REPOSITORY_MODULES_DIR_NAME = "modules"
internal const val MODULE_DESCRIPTORS_JAR_PATH: String = "$RUNTIME_REPOSITORY_MODULES_DIR_NAME/$JAR_REPOSITORY_FILE_NAME" 
const val MODULE_DESCRIPTORS_COMPACT_PATH: String = "$RUNTIME_REPOSITORY_MODULES_DIR_NAME/$COMPACT_REPOSITORY_FILE_NAME" 

private val dependenciesToSkip = mapOf(
  // may be removed when IJPL-125 is fixed
  "intellij.platform.buildScripts.downloader" to setOf("zstd-jni"),
)
