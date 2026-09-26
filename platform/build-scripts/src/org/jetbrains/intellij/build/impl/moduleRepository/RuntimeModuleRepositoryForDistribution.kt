// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.moduleRepository

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.buildScripts.runtimeModuleRepository.PluginDistributionEntry
import com.intellij.platform.buildScripts.runtimeModuleRepository.RuntimeModuleRepositoryException
import com.intellij.platform.buildScripts.runtimeModuleRepository.RuntimeModuleRepositoryLayout
import com.intellij.platform.buildScripts.runtimeModuleRepository.RuntimeModuleRepositoryPluginLayout
import com.intellij.platform.buildScripts.runtimeModuleRepository.generateRuntimeModuleRepository
import com.intellij.platform.buildScripts.runtimeModuleRepository.removeDataForSuppressedPlugins
import com.intellij.platform.buildScripts.runtimeModuleRepository.saveRuntimeModuleRepository
import com.intellij.platform.buildScripts.runtimeModuleRepository.writeRuntimeModuleRepositoryLayout
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.classPath.PluginBuildDescriptor
import org.jetbrains.intellij.build.classPath.PluginBuildResult
import org.jetbrains.intellij.build.classPath.getEmbeddedProductTempPluginDir
import org.jetbrains.intellij.build.classPath.resolveAndCacheDescriptorForEmbeddedProduct
import org.jetbrains.intellij.build.impl.DistributionBuilderState
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
import org.jetbrains.intellij.build.impl.projectStructureMapping.CustomAssetEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Generates a file with descriptors of modules for [com.intellij.platform.runtime.repository.RuntimeModuleRepository].
 * Currently, this function uses information from [DistributionFileEntry] to determine which resources were copied to the distribution and
 * how they are organized.
 */
internal fun generateRuntimeModuleRepositoryForDistribution(
  contentReport: ContentReport,
  context: BuildContext,
  platformLayout: PlatformLayout,
) {
  val additionalFrontendOnlyPlugins = context.getLayoutOfAdditionalFrontendOnlyPlugins(platformLayout)

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
        val actualPlugins = contentReport.bundledPlugins.filter {
          (it.os == null || it.os == distribution.os) &&
          (it.arch == null || it.arch == distribution.arch)
        }
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
 *
 * [layoutFile] is where to write the [RuntimeModuleRepositoryLayout] the repository is generated from, or `null` for none.
 */
internal fun generateRuntimeModuleRepositoryForDevBuild(
  contentReport: ContentReport,
  targetDirectory: Path,
  context: BuildContext,
  platformLayout: PlatformLayout,
  layoutFile: Path? = null,
) {
  val additionalFrontendOnlyPlugins = computeDescriptorsForAdditionalFrontendPlugins(context, platformLayout)
  generateRepositoryForDistribution(
    targetDirectory = targetDirectory,
    platformEntries = contentReport.platform,
    bundledPlugins = contentReport.bundledPlugins,
    additionalFrontendOnlyPlugins = additionalFrontendOnlyPlugins,
    platformLayout = platformLayout,
    context = context,
    entryPathRelativizer = { targetDirectory.relativize(it) },
    layoutFile = layoutFile,
  )
}

/**
 * Generates a runtime module repository for modules and plugins included in the cross-platform distribution.
 * @return path to the directory with the generated repository file or `null` if `distAllPath` already contains a common module repository file which is used for all OSes
 */
internal fun generateCrossPlatformRepository(
  contentReport: ContentReport,
  context: BuildContext,
  platformLayout: PlatformLayout,
  crossPlatformPluginsDir: Path?,
  crossPlatformBuiltPlugins: List<PluginBuildDescriptor>,
): Path? {
  val commonTargetDirectory = context.paths.distAllDir
  val commonRepositoryFile = commonTargetDirectory.resolve(MODULE_DESCRIPTORS_COMPACT_PATH)
  if (commonRepositoryFile.exists()) {
    return null
  }

  val targetDir = context.paths.tempDir.resolve("cross-platform-module-repository")
  val actualPlatformEntries = contentReport.platform.filter { it.path.startsWith(commonTargetDirectory) }
  val actualPlugins = contentReport.bundledPlugins.filter { it.os == null && it.arch == null } + crossPlatformBuiltPlugins.map { it.buildResult }
  val additionalFrontendOnlyPlugins = context.getLayoutOfAdditionalFrontendOnlyPlugins(platformLayout)
  generateRepositoryForDistribution(
    targetDirectory = targetDir,
    platformEntries = actualPlatformEntries,
    context = context,
    bundledPlugins = actualPlugins,
    additionalFrontendOnlyPlugins = additionalFrontendOnlyPlugins,
    platformLayout = platformLayout,
    entryPathRelativizer = {
      when {
        it.startsWith(commonTargetDirectory) -> commonTargetDirectory.relativize(it)
        crossPlatformPluginsDir != null && it.startsWith(crossPlatformPluginsDir) -> Path("plugins").resolve(crossPlatformPluginsDir.relativize(it))
        else -> null
      }
    },
  )
  return targetDir.resolve(RUNTIME_REPOSITORY_MODULES_DIR_NAME)
}

/**
 * Generates and saves the runtime module repository for a distribution.
 * @param entryPathRelativizer converts an absolute path to a path relative to the distribution root
 * @param layoutFile where to write the [RuntimeModuleRepositoryLayout] of the distribution, or `null` for none
 */
private fun generateRepositoryForDistribution(
  targetDirectory: Path,
  platformEntries: List<DistributionFileEntry>,
  context: BuildContext,
  bundledPlugins: List<PluginBuildResult>,
  additionalFrontendOnlyPlugins: List<PluginBuildResult>,
  platformLayout: PlatformLayout,
  entryPathRelativizer: (Path) -> Path?,
  layoutFile: Path? = null,
) {
  val pluginDescriptorModulesForAdditionalFrontendPlugins = additionalFrontendOnlyPlugins.mapTo(HashSet()) { it.mainModule }
  val corePluginDescriptorModuleName = context.productProperties.applicationInfoModule
  val embeddedFrontendDescriptorModuleName = context.getEmbeddedFrontendProductContext()?.productProperties?.applicationInfoModule
  val originalPluginDescriptorsData = fetchPluginDescriptorsData(
    platformLayout,
    corePluginDescriptorModuleName,
    embeddedFrontendDescriptorModuleName,
    bundledPlugins,
    additionalFrontendOnlyPlugins,
  )
  val pluginDescriptorsData = removeDataForSuppressedPlugins(originalPluginDescriptorsData, context.productProperties.additionalIDEPropertiesFilePaths)
  val repository = try {
    val pluginConfigurationModuleToDistributionEntries = (bundledPlugins + additionalFrontendOnlyPlugins)
      .associateByTo(HashMap(), { it.mainModule }, { toPluginDistributionEntries(it.distribution, entryPathRelativizer) })
    pluginConfigurationModuleToDistributionEntries[corePluginDescriptorModuleName] = toPluginDistributionEntries(platformEntries, entryPathRelativizer)
    if (layoutFile != null) {
      // The core plugin, the bundled plugins and the additional frontend-only plugins, in this order.
      val plugins = (listOf(corePluginDescriptorModuleName) + bundledPlugins.map { it.mainModule }).map { module ->
        RuntimeModuleRepositoryPluginLayout(descriptorModule = module, entries = pluginConfigurationModuleToDistributionEntries.getValue(module))
      } + additionalFrontendOnlyPlugins.map { plugin ->
        RuntimeModuleRepositoryPluginLayout(
          descriptorModule = plugin.mainModule,
          additionalFrontendOnlyPlugin = true,
          entries = pluginConfigurationModuleToDistributionEntries.getValue(plugin.mainModule),
        )
      }
      writeRuntimeModuleRepositoryLayout(RuntimeModuleRepositoryLayout(plugins = plugins), layoutFile)
    }
    generateRuntimeModuleRepository(
      pluginDescriptorsData = pluginDescriptorsData,
      pluginConfigurationModuleToDistributionEntries = pluginConfigurationModuleToDistributionEntries,
      additionalFrontendOnlyPluginModules = pluginDescriptorModulesForAdditionalFrontendPlugins,
      project = context.project,
    )
  }
  catch (e: RuntimeModuleRepositoryException) {
    val cause = e.cause
    if (cause == null) {
      context.messages.logErrorAndThrow(e.message)
    }
    else {
      context.messages.logErrorAndThrow(e.message, cause)
    }
    return
  }
  saveRuntimeModuleRepository(repository, targetDirectory.resolve(RUNTIME_REPOSITORY_MODULES_DIR_NAME))
}

/**
 * Converts the files of a plugin to the form the runtime module repository generator reads.
 * @param entryPathRelativizer converts an absolute path to a path relative to the distribution root
 */
private fun toPluginDistributionEntries(distributionEntries: Collection<DistributionFileEntry>, entryPathRelativizer: (Path) -> Path?): List<PluginDistributionEntry> {
  return distributionEntries.mapNotNull { entry ->
    val path = entryPathRelativizer(entry.path)?.invariantSeparatorsPathString
    when (entry) {
      is ModuleOutputEntry -> PluginDistributionEntry(PluginDistributionEntry.Kind.MODULE_OUTPUT, entry.owner.moduleName, path, entry.relativeOutputFile)
      is ProjectLibraryEntry -> PluginDistributionEntry(PluginDistributionEntry.Kind.PROJECT_LIBRARY, entry.data.libraryName, path, entry.relativeOutputFile)
      is ModuleLibraryFileEntry -> PluginDistributionEntry(PluginDistributionEntry.Kind.MODULE_LIBRARY, entry.moduleName, path, entry.relativeOutputFile)
      is CustomAssetEntry -> null
    }
  }
}

/**
 * Returns the list of descriptors for additional plugins which should be added to the runtime module repository.
 * These plugins are not bundled with the IDE, but they are used from the frontend process started from the IDE.
 * To be able to run the frontend process from a regular IDE, we need to include information about its modules to the runtime module repository.
 * The layout fills the descriptor cache of [platformLayout], so read the result with the same layout.
 * Use [BuildContext.getLayoutOfAdditionalFrontendOnlyPlugins] to get the cached value instead of calling this method directly.
 */
internal fun computeDescriptorsForAdditionalFrontendPlugins(
  context: BuildContext,
  platformLayout: PlatformLayout,
): List<PluginBuildResult> {
  return TraceManager.spanBuilder("compute layout of additional plugins for embedded frontend").use {
    val embeddedFrontendContext = context.getEmbeddedFrontendProductContext() ?: return@use emptyList()

    // creates a descriptor for the core plugin of the embedded frontend
    val embeddedFrontendDescriptorModuleName = embeddedFrontendContext.productProperties.applicationInfoModule
    val embeddedFrontendPlatformLayout = createPlatformLayout(embeddedFrontendContext)
    val embeddedFrontendTargetDir = getEmbeddedProductTempPluginDir(context, embeddedFrontendDescriptorModuleName)
    val embeddedFrontendPlatformEntries = layoutPlatformDistribution(
      moduleOutputPatcher = ModuleOutputPatcher(),
      targetDir = embeddedFrontendTargetDir,
      platform = embeddedFrontendPlatformLayout,
      searchableOptionSet = null,
      copyFiles = false,
      context = embeddedFrontendContext,
    )

    val embeddedFrontendDescriptorFile = embeddedFrontendContext.findFileInModuleSources(
      moduleName = embeddedFrontendDescriptorModuleName,
      relativePath = FRONTEND_CUSTOMIZATION_PLUGIN_XML_PATH,
    ) ?: error("Cannot find $FRONTEND_CUSTOMIZATION_PLUGIN_XML_PATH in $embeddedFrontendDescriptorModuleName")
    val embeddedFrontendDescriptorContainer = platformLayout.descriptorCacheContainer.forPlugin(embeddedFrontendTargetDir)
    resolveAndCacheDescriptorForEmbeddedProduct(
      xml = JDOMUtil.load(embeddedFrontendDescriptorFile),
      clientModuleName = embeddedFrontendDescriptorModuleName,
      additionalSearchModules = emptyList(),
      platformLayout = embeddedFrontendPlatformLayout,
      platformDescriptorContainer = embeddedFrontendPlatformLayout.descriptorCacheContainer.forPlatform(embeddedFrontendPlatformLayout),
      pluginLayout = PluginLayout.pluginAuto(embeddedFrontendDescriptorModuleName) {},
      pluginDescriptorContainer = embeddedFrontendDescriptorContainer,
      targetPluginDescriptorContainer = embeddedFrontendDescriptorContainer,
      context = embeddedFrontendContext,
    )

    val additionalFrontendPlugins = mutableListOf(
      PluginBuildResult(
        mainModule = embeddedFrontendDescriptorModuleName,
        dir = embeddedFrontendTargetDir,
        os = null,
        arch = null,
        distribution = embeddedFrontendPlatformEntries,
      )
    )

    val additionalPluginModules = embeddedFrontendContext.getBundledPluginModules().toMutableSet()
    additionalPluginModules.removeAll(context.getBundledPluginModules().toSet())

    if (additionalPluginModules.isNotEmpty()) {
      /* generate descriptors for custom 'Xxx for JetBrains Client' plugins, which are not bundled with the IDE but are used in the frontend process; eventually we'll get rid of
         them (see IJPL-220139) */
      val additionalPluginModuleLayouts = getPluginLayoutsByJpsModuleNames(additionalPluginModules, embeddedFrontendContext.productProperties.productLayout)
      // A dry layout needs only the platform layout. distributionState() also walks every monorepo plugin when
      // buildAllCompatiblePlugins is on, and that walk needs descriptor sources this fragment does not declare.
      additionalFrontendPlugins.addAll(buildPlugins(
        plugins = additionalPluginModuleLayouts,
        os = null,
        arch = null,
        targetDir = context.paths.tempDir.resolve("frontend-plugins-layout"),
        platformEntriesProvider = null,
        searchableOptionSet = null,
        descriptorCacheContainer = platformLayout.descriptorCacheContainer,
        state = DistributionBuilderState(
          platformLayout = platformLayout,
          pluginsToPublish = emptySet(),
          context = context,
        ),
        context = context,
        copyFiles = false,
        layoutOnly = true
      ))
    }
    additionalFrontendPlugins
  }
}

internal const val RUNTIME_REPOSITORY_MODULES_DIR_NAME: String = com.intellij.platform.buildScripts.runtimeModuleRepository.RUNTIME_REPOSITORY_MODULES_DIR_NAME
internal const val MODULE_DESCRIPTORS_JAR_PATH: String = com.intellij.platform.buildScripts.runtimeModuleRepository.MODULE_DESCRIPTORS_JAR_PATH
const val MODULE_DESCRIPTORS_COMPACT_PATH: String = com.intellij.platform.buildScripts.runtimeModuleRepository.MODULE_DESCRIPTORS_COMPACT_PATH
private const val FRONTEND_CUSTOMIZATION_PLUGIN_XML_PATH: String = "META-INF/JetBrainsClientPlugin.xml"
