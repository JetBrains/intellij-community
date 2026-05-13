// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.moduleRepository

import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimePluginHeader
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.classPath.PluginBuildDescriptor
import org.jetbrains.intellij.build.classPath.getEmbeddedProductTempPluginDir
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.SUPPORTED_DISTRIBUTIONS
import org.jetbrains.intellij.build.impl.createPlatformLayout
import org.jetbrains.intellij.build.impl.getOsAndArchSpecificDistDirectory
import org.jetbrains.intellij.build.impl.getPluginLayoutsByJpsModuleNames
import org.jetbrains.intellij.build.impl.layoutPlatformDistribution
import org.jetbrains.intellij.build.impl.plugins.buildPlugins
import org.jetbrains.intellij.build.impl.projectStructureMapping.ContentReport
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.model.module.JpsModule
import java.io.IOException
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.reader

/**
 * Generates a file with descriptors of modules for [com.intellij.platform.runtime.repository.RuntimeModuleRepository].
 * Currently, this function uses information from [DistributionFileEntry] to determine which resources were copied to the distribution and
 * how they are organized.
 */
internal suspend fun generateRuntimeModuleRepositoryForDistribution(
  contentReport: ContentReport,
  context: BuildContext,
  platformLayout: PlatformLayout,
) {
  val additionalFrontendOnlyPlugins = computeDescriptorsForAdditionalFrontendPlugins(context, platformLayout)

  val osSpecificDistPaths = SUPPORTED_DISTRIBUTIONS.associateWith {
    getOsAndArchSpecificDistDirectory(osFamily = it.os, arch = it.arch, libc = it.libcImpl, context = context)
  }

  val hasOsSpecificPlatformEntries = contentReport.platform.any { entry -> osSpecificDistPaths.values.any { entry.path.startsWith(it) } }
  val commonTargetDirectory = context.paths.distAllDir
  if (!hasOsSpecificPlatformEntries && contentReport.bundledPlugins.all { it.os == null && it.arch == null }) {
    generateRepositoryForDistribution(
      targetDirectory = commonTargetDirectory,
      platformEntries = contentReport.platform,
      bundledPlugins = contentReport.bundledPlugins,
      additionalFrontendOnlyPlugins = additionalFrontendOnlyPlugins,
      platformLayout = platformLayout,
      context = context,
      entryPathRelativizer = { if (it.startsWith(commonTargetDirectory)) commonTargetDirectory.relativize(it) else null }
    )
  }
  else {
    SUPPORTED_DISTRIBUTIONS
      .filter { context.shouldBuildDistributionForOS(it.os, it.arch) }
      .forEach { distribution ->
        val targetDirectory = osSpecificDistPaths.getValue(distribution)
        val actualPlatformEntries = contentReport.platform.filter { it.path.startsWith(commonTargetDirectory) || it.path.startsWith(targetDirectory) }
        val actualPlugins = contentReport.bundledPlugins.filter { (it.os == null || it.os == distribution.os) && (it.arch == null || it.arch == distribution.arch) }
        generateRepositoryForDistribution(
          targetDirectory = targetDirectory,
          platformEntries = actualPlatformEntries,
          bundledPlugins = actualPlugins,
          additionalFrontendOnlyPlugins = additionalFrontendOnlyPlugins,
          context = context,
          platformLayout = platformLayout,
          entryPathRelativizer = {
            when {
              it.startsWith(commonTargetDirectory) -> commonTargetDirectory.relativize(it)
              it.startsWith(targetDirectory) -> targetDirectory.relativize(it)
              else -> null
            }
          }
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
  val additionalFrontendOnlyPlugins = computeDescriptorsForAdditionalFrontendPlugins(context, platformLayout)
  generateRepositoryForDistribution(
    targetDirectory = targetDirectory,
    platformEntries = contentReport.platform,
    bundledPlugins = contentReport.bundledPlugins,
    additionalFrontendOnlyPlugins = additionalFrontendOnlyPlugins,
    platformLayout = platformLayout,
    context = context,
    entryPathRelativizer = { targetDirectory.relativize(it) }
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
  saveModuleRepository(commonDescriptors, commonPluginHeaders, targetDir)
  return targetDir
}

/**
 * Generates and saves the runtime module repository for a distribution.
 * @param entryPathRelativizer converts an absolute path to a path relative to the distribution root
 */
private suspend fun generateRepositoryForDistribution(
  targetDirectory: Path,
  platformEntries: List<DistributionFileEntry>,
  context: BuildContext,
  bundledPlugins: List<PluginBuildDescriptor>,
  additionalFrontendOnlyPlugins: List<PluginBuildDescriptor>,
  platformLayout: PlatformLayout,
  entryPathRelativizer: (Path) -> Path?,
) {
  val pluginDescriptorModulesForAdditionalFrontendPlugins = additionalFrontendOnlyPlugins.mapTo(HashSet()) { it.layout.mainModule }
  val corePluginDescriptorModuleName = context.productProperties.applicationInfoModule
  val embeddedFrontendDescriptorModuleName = context.getEmbeddedFrontendProductContext()?.productProperties?.applicationInfoModule
  val originalPluginDescriptorsData = fetchPluginDescriptorsData(
    platformLayout,
    corePluginDescriptorModuleName,
    embeddedFrontendDescriptorModuleName,
    bundledPlugins,
    additionalFrontendOnlyPlugins,
  )
  val pluginDescriptorsData = removeDataForSuppressedPlugins(originalPluginDescriptorsData, context)
  val pluginConfigurationModuleToDistributionEntries =
    (bundledPlugins + additionalFrontendOnlyPlugins).associateByTo(HashMap(), { it.layout.mainModule }, { it.distribution })
  pluginConfigurationModuleToDistributionEntries[corePluginDescriptorModuleName] = platformEntries
  val pluginHeadersData = try {
    generateRuntimePluginHeaders(pluginDescriptorsData, pluginConfigurationModuleToDistributionEntries, entryPathRelativizer, context.project)
  }
  catch (e: Exception) {
    context.messages.logErrorAndThrow("Failed to generate runtime plugin headers: ${e.message}", e)
    return
  }
  val pluginHeaders = pluginHeadersData.map { it.header }
  val pluginDataToGenerateModuleDescriptors = pluginHeadersData.filterNot { it.header.pluginDescriptorModuleId.name in pluginDescriptorModulesForAdditionalFrontendPlugins }
  val distDescriptors = generateRuntimeModuleDescriptors(pluginDataToGenerateModuleDescriptors)
  val errors = ArrayList<String>()
  val errorReporter = object : RuntimeModuleRepositoryValidator.ErrorReporter {
    override fun reportError(errorMessage: String) {
      errors.add(errorMessage)
    }
  }
  RuntimeModuleRepositoryValidator.validate(distDescriptors, pluginHeaders, errorReporter)
  if (errors.isNotEmpty()) {
    context.messages.logErrorAndThrow(
      "Runtime module repository which is used to run the frontend process has ${errors.size} ${StringUtil.pluralize("error", errors.size)}:\n " +
      errors.joinToString("\n ")
    )
  }
  withContext(Dispatchers.IO) {
    saveModuleRepository(
      descriptors = distDescriptors,
      pluginHeaders = pluginHeaders,
      targetDirectory = targetDirectory.resolve(RUNTIME_REPOSITORY_MODULES_DIR_NAME)
    )
  }
}

/**
 * If some plugins are suppressed in the product by default, they should not be included in the runtime module repository to avoid ambiguity if they contain modules duplicating
 * modules from other plugins.
 */
private fun removeDataForSuppressedPlugins(originalPluginDescriptorsData: List<PluginDescriptorDataForHeader>, context: BuildContext): List<PluginDescriptorDataForHeader> {
  val properties = Properties()
  context.productProperties.additionalIDEPropertiesFilePaths.forEach { propertiesFile ->
    propertiesFile.reader().buffered().use { reader ->
      properties.load(reader)
    }
  }
  val selector = properties.getProperty("idea.suppressed.plugins.set.selector") ?: return originalPluginDescriptorsData
  val suppressedPluginsString = properties.getProperty("idea.suppressed.plugins.set.${selector}") ?: return originalPluginDescriptorsData
  val suppressedPlugins = suppressedPluginsString.split(",").mapTo(HashSet()) { it.trim() }
  return originalPluginDescriptorsData.filterNot { it.pluginId in suppressedPlugins }
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

    val additionalPluginModules = embeddedFrontendContext.getBundledPluginModules().toMutableSet()
    additionalPluginModules.removeAll(context.getBundledPluginModules().toSet())

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
        layoutOnly = true
      ))
    }
    additionalFrontendPlugins
  }
}

internal fun hasTestSourcesAndNoProductionSources(module: JpsModule): Boolean {
  val sourceRoots = module.sourceRoots
  return sourceRoots.isNotEmpty() && sourceRoots.all { it.rootType.isForTests }
}

private const val GENERATOR_VERSION: Int = 3

private fun saveModuleRepository(descriptors: List<RawRuntimeModuleDescriptor>, pluginHeaders: List<RuntimePluginHeader>,
                         targetDirectory: Path) {
  try {
    val bootstrapModuleName = "intellij.platform.bootstrap"
    targetDirectory.createDirectories()
    RuntimeModuleRepositorySerialization.saveToCompactFile(descriptors,
                                                           pluginHeaders, bootstrapModuleName, targetDirectory.resolve(COMPACT_REPOSITORY_FILE_NAME), GENERATOR_VERSION)
    RuntimeModuleRepositorySerialization.saveToJar(descriptors,
                                                   pluginHeaders, bootstrapModuleName, targetDirectory.resolve(JAR_REPOSITORY_FILE_NAME), GENERATOR_VERSION)
  }
  catch (e: IOException) {
    throw RuntimeException("Failed to save runtime module repository: ${e.message}", e)
  }
}

private const val JAR_REPOSITORY_FILE_NAME: String = "module-descriptors.jar"
private const val COMPACT_REPOSITORY_FILE_NAME: String = "module-descriptors.dat"
internal const val RUNTIME_REPOSITORY_MODULES_DIR_NAME = "modules"
internal const val MODULE_DESCRIPTORS_JAR_PATH: String = "$RUNTIME_REPOSITORY_MODULES_DIR_NAME/$JAR_REPOSITORY_FILE_NAME" 
const val MODULE_DESCRIPTORS_COMPACT_PATH: String = "$RUNTIME_REPOSITORY_MODULES_DIR_NAME/$COMPACT_REPOSITORY_FILE_NAME" 
