// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.fasterxml.jackson.jr.ob.JSON
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.Compressor
import com.jetbrains.plugin.blockmap.core.BlockMap
import com.jetbrains.plugin.blockmap.core.FileHash
import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.DistFile
import org.jetbrains.intellij.build.InMemoryDistFileContent
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.LinuxLibcImpl
import org.jetbrains.intellij.build.MAVEN_REPO
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PLATFORM_LOADER_JAR
import org.jetbrains.intellij.build.PluginBuildDescriptor
import org.jetbrains.intellij.build.PluginBundlingRestrictions
import org.jetbrains.intellij.build.PluginDistribution
import org.jetbrains.intellij.build.ProductModulesLayout
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.antToRegex
import org.jetbrains.intellij.build.buildSearchableOptions
import org.jetbrains.intellij.build.createPluginLayoutSet
import org.jetbrains.intellij.build.executeStep
import org.jetbrains.intellij.build.fus.createStatisticsRecorderBundledMetadataProviderTask
import org.jetbrains.intellij.build.generateClasspath
import org.jetbrains.intellij.build.generatePluginClassPath
import org.jetbrains.intellij.build.generatePluginClassPathFromPrebuiltPluginFiles
import org.jetbrains.intellij.build.impl.projectStructureMapping.ContentReport
import org.jetbrains.intellij.build.impl.projectStructureMapping.CustomAssetEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.LibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleTestOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.buildJarContentReport
import org.jetbrains.intellij.build.impl.projectStructureMapping.getIncludedModules
import org.jetbrains.intellij.build.injectAppInfo
import org.jetbrains.intellij.build.io.W_CREATE_NEW
import org.jetbrains.intellij.build.io.ZipArchiver
import org.jetbrains.intellij.build.io.archiveDir
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFile
import org.jetbrains.intellij.build.io.copyFileToDir
import org.jetbrains.intellij.build.io.writeNewFile
import org.jetbrains.intellij.build.io.writeNewZipWithoutIndex
import org.jetbrains.intellij.build.io.zip
import org.jetbrains.intellij.build.io.zipWithCompression
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.block
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.intellij.build.writePluginClassPathHeader
import org.jetbrains.jps.model.artifact.JpsArtifact
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.artifact.elements.JpsLibraryFilesPackagingElement
import org.jetbrains.jps.model.java.JpsProductionModuleOutputPackagingElement
import org.jetbrains.jps.model.java.JpsTestModuleOutputPackagingElement
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.util.JpsPathUtil
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.listDirectoryEntries

/**
 * Assembles output of modules to platform JARs (in [BuildPaths.distAllDir]/lib directory),
 * bundled plugins' JARs (in [distAll][BuildPaths.distAllDir]/plugins directory) and zip archives with
 * non-bundled plugins (in [artifacts][BuildPaths.artifactDir]/plugins directory).
 */
internal suspend fun buildDistribution(
  context: BuildContext,
  isUpdateFromSources: Boolean = false,
): ContentReport = coroutineScope {
  val state = context.distributionState()
  validateModuleStructure(state.platform, context)
  context.productProperties.validateLayout(state.platform, context)
  createBuildBrokenPluginListJob(context)

  val productRunner = context.createProductRunner()
  if (context.productProperties.buildDocAuthoringAssets) {
    launch(CoroutineName("build authoring assets")) {
      buildAdditionalAuthoringArtifacts(productRunner, context)
    }
  }

  val traceContext = Context.current().asContextElement()
  val contentReport = coroutineScope {
    // must be completed before plugin building
    val searchableOptionSet = context.executeStep(spanBuilder("build searchable options index"), BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP) {
      buildSearchableOptions(productRunner, context)
    }

    val bundledPluginLayouts = getPluginLayoutsByJpsModuleNames(
      modules = context.getBundledPluginModules(),
      productLayout = context.productProperties.productLayout,
    )
    val moduleOutputPatcher = ModuleOutputPatcher()
    val buildPlatformJob: Deferred<List<DistributionFileEntry>> = async(traceContext + CoroutineName("build platform lib")) {
      spanBuilder("build platform lib").use {
        val result = buildLib(moduleOutputPatcher, state.platform, searchableOptionSet, context)
        if (!isUpdateFromSources && context.productProperties.scrambleMainJar) {
          scramble(state.platform, context)
        }
        context.bootClassPathJarNames = if (context.useModularLoader) listOf(PLATFORM_LOADER_JAR) else generateClasspath(context)
        result
      }
    }

    val buildNonBundledPlugins = async(CoroutineName("build non-bundled plugins")) {
      val pluginsToPublish = state.pluginsToPublish + bundledPluginLayouts.filterNot {
        satisfiesBundlingRequirements(it, osFamily = null, arch = null, context)
      }
      val compressPluginArchive = !isUpdateFromSources && context.options.compressZipFiles
      buildNonBundledPlugins(pluginsToPublish, compressPluginArchive, buildPlatformJob, state, searchableOptionSet, context)
    }

    val bundledPluginItems = buildBundledPluginsForAllPlatforms(
      state, bundledPluginLayouts, isUpdateFromSources, buildPlatformJob, searchableOptionSet, moduleOutputPatcher, context
    )

    ContentReport(buildPlatformJob.await(), bundledPluginItems, buildNonBundledPlugins.await())
  }

  coroutineScope {
    launch(Dispatchers.IO + CoroutineName("generate content report")) {
      spanBuilder("generate content report").use {
        Files.createDirectories(context.paths.artifactDir)
        val contentReportFile = context.paths.artifactDir.resolve("content-report.zip")
        writeNewZipWithoutIndex(contentReportFile) { zipFileWriter ->
          buildJarContentReport(contentReport, zipFileWriter, context.paths, context)
        }
        context.notifyArtifactBuilt(contentReportFile)
      }
    }
    createBuildThirdPartyLibraryListJob(contentReport.bundled(), context)
    if (context.useModularLoader || context.generateRuntimeModuleRepository) {
      launch(CoroutineName("generate runtime module repository")) {
        spanBuilder("generate runtime module repository").use {
          generateRuntimeModuleRepository(contentReport.bundled(), context)
        }
      }
    }
  }
  contentReport
}

private suspend fun buildBundledPluginsForAllPlatforms(
  state: DistributionBuilderState,
  pluginLayouts: Set<PluginLayout>,
  isUpdateFromSources: Boolean,
  buildPlatformJob: Deferred<List<DistributionFileEntry>>,
  searchableOptionSetDescriptor: SearchableOptionSetDescriptor?,
  moduleOutputPatcher: ModuleOutputPatcher,
  context: BuildContext,
): List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>> = coroutineScope {
  val commonDeferred = async(CoroutineName("build bundled plugins")) {
    doBuildBundledPlugins(
      state, pluginLayouts, isUpdateFromSources, buildPlatformJob, searchableOptionSetDescriptor, moduleOutputPatcher, context
    )
  }

  val additionalDeferred = async(CoroutineName("build additional plugins")) {
    copyAdditionalPlugins(context, pluginDir = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY))
  }

  val pluginDirs = getPluginDirs(context, isUpdateFromSources)
  val specificDeferred = async(CoroutineName("build OS-specific bundled plugins")) {
    buildOsSpecificBundledPlugins(
      state, pluginLayouts, isUpdateFromSources, buildPlatformJob, context, searchableOptionSetDescriptor, pluginDirs, moduleOutputPatcher
    )
  }

  val common = commonDeferred.await()
  val specific = specificDeferred.await()
  buildPlatformJob.join()
  writePluginInfo(moduleOutputPatcher, pluginDirs, common, specific, additionalDeferred.await(), context)
  common + specific.values.flatten()
}

private fun writePluginInfo(
  moduleOutputPatcher: ModuleOutputPatcher,
  pluginDirs: List<Pair<SupportedDistribution, Path>>,
  common: List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>,
  specific: Map<SupportedDistribution, List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>>,
  additional: List<Pair<Path, List<Path>>>?,
  context: BuildContext,
) {
  val commonClassPath = generatePluginClassPath(pluginEntries = common, moduleOutputPatcher)
  val additionalClassPath = additional?.let { generatePluginClassPathFromPrebuiltPluginFiles(it) }

  for ((supportedDist) in pluginDirs) {
    val specificList = specific[supportedDist]
    val specificClasspath = specificList?.let { generatePluginClassPath(pluginEntries = it, moduleOutputPatcher) }

    val byteOut = ByteArrayOutputStream()
    val out = DataOutputStream(byteOut)
    val pluginCount = common.size + (additional?.size ?: 0) + (specificList?.size ?: 0)
    writePluginClassPathHeader(out, isJarOnly = true, pluginCount, moduleOutputPatcher, context)
    out.write(commonClassPath)
    additionalClassPath?.let { out.write(it) }
    specificClasspath?.let { out.write(it) }
    out.close()

    context.addDistFile(DistFile(InMemoryDistFileContent(byteOut.toByteArray()), PLUGIN_CLASSPATH, supportedDist.os, supportedDist.libcImpl, supportedDist.arch))
  }
}

/**
 * Validates module structure to be ensure all module dependencies are included.
 */
fun validateModuleStructure(platform: PlatformLayout, context: BuildContext) {
  if (context.options.validateModuleStructure) {
    ModuleStructureValidator(context, platform.includedModules).validate()
  }
}

private fun getPluginDirs(context: BuildContext, isUpdateFromSources: Boolean): List<Pair<SupportedDistribution, Path>> =
  if (isUpdateFromSources) {
    listOf(SupportedDistribution(OsFamily.currentOs, JvmArchitecture.currentJvmArch, LibcImpl.current(OsFamily.currentOs)) to context.paths.distAllDir.resolve(PLUGINS_DIRECTORY))
  }
  else {
    SUPPORTED_DISTRIBUTIONS.map {
      it to getOsAndArchSpecificDistDirectory(it.os, it.arch, it.libcImpl, context).resolve(PLUGINS_DIRECTORY)
    }
  }

suspend fun buildBundledPlugins(
  state: DistributionBuilderState,
  plugins: Collection<PluginLayout>,
  searchableOptionSetDescriptor: SearchableOptionSetDescriptor?,
  context: BuildContext,
) {
  doBuildBundledPlugins(
    state, plugins, isUpdateFromSources = false, buildPlatformJob = null, searchableOptionSetDescriptor, ModuleOutputPatcher(), context
  )
}

private suspend fun doBuildBundledPlugins(
  state: DistributionBuilderState,
  plugins: Collection<PluginLayout>,
  isUpdateFromSources: Boolean,
  buildPlatformJob: Job?,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  moduleOutputPatcher: ModuleOutputPatcher,
  context: BuildContext,
): List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>> {
  return spanBuilder("build bundled plugins")
    .setAttribute("isUpdateFromSources", isUpdateFromSources)
    .setAttribute(AttributeKey.stringArrayKey("pluginDirectoriesToSkip"), context.options.bundledPluginDirectoriesToSkip.toList())
    .setAttribute("count", plugins.size.toLong())
    .block { span ->
      val pluginsToBundle = ArrayList<PluginLayout>(plugins.size)
      plugins.filterTo(pluginsToBundle) { satisfiesBundlingRequirements(plugin = it, osFamily = null, arch = null, context) }
      span.setAttribute("satisfiableCount", pluginsToBundle.size.toLong())

      // doesn't make sense to require passing here a list with a stable order (unnecessary complication, sorting by main module is enough)
      pluginsToBundle.sortWith(PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE)
      val targetDir = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY)
      val platformSpecificPluginDirs = getPluginDirs(context, isUpdateFromSources)
      val entries = buildPlugins(
        moduleOutputPatcher, pluginsToBundle, os = null, targetDir, state, context, buildPlatformJob, searchableOptionSet
      ) { layout, _ ->
        if (layout.hasPlatformSpecificResources) {
          buildPlatformSpecificPluginResources(layout, platformSpecificPluginDirs, context)
        }
        else {
          emptyList()
        }
      }

      entries
    }
}

private suspend fun buildOsSpecificBundledPlugins(
  state: DistributionBuilderState,
  plugins: Set<PluginLayout>,
  isUpdateFromSources: Boolean,
  buildPlatformJob: Job?,
  context: BuildContext,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  pluginDirs: List<Pair<SupportedDistribution, Path>>,
  moduleOutputPatcher: ModuleOutputPatcher,
): Map<SupportedDistribution, List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>> {
  return spanBuilder("build os-specific bundled plugins")
    .setAttribute("isUpdateFromSources", isUpdateFromSources)
    .setAttribute(AttributeKey.stringArrayKey("pluginDirectoriesToSkip"), context.options.bundledPluginDirectoriesToSkip.toList())
    .use {
      pluginDirs.mapNotNull { (dist, targetDir) ->
        val (os, arch) = dist
        if (!context.shouldBuildDistributionForOS(os, arch)) {
          return@mapNotNull null
        }

        val osSpecificPlugins = plugins.filter {
          satisfiesBundlingRequirements(it, os, arch, context)
        }
        if (osSpecificPlugins.isEmpty()) {
          return@mapNotNull null
        }

        dist to async(CoroutineName("build bundled plugins")) {
          spanBuilder("build bundled plugins")
            .setAttribute("os", os.osName)
            .setAttribute("arch", arch.name)
            .setAttribute("count", osSpecificPlugins.size.toLong())
            .setAttribute("outDir", targetDir.toString())
            .use {
              buildPlugins(moduleOutputPatcher, osSpecificPlugins, os, targetDir, state, context, buildPlatformJob, searchableOptionSet)
            }
        }
      }
    }
    .associateBy(keySelector = { it.first }, valueTransform = { it.second.getCompleted() })
}

suspend fun copyAdditionalPlugins(context: BuildContext, pluginDir: Path): List<Pair<Path, List<Path>>>? {
  val additionalPluginPaths = context.productProperties.getAdditionalPluginPaths(context)
  if (additionalPluginPaths.isEmpty()) {
    return null
  }

  return spanBuilder("copy additional plugins").use(Dispatchers.IO) {
    val allEntries = mutableListOf<Pair<Path, List<Path>>>()

    for (sourceDir in additionalPluginPaths) {
      val targetDir = pluginDir.resolve(sourceDir.fileName)
      copyDir(sourceDir, targetDir)
      val entries = targetDir.resolve(LIB_DIRECTORY).listDirectoryEntries("*.jar")
      check(entries.isNotEmpty()) {
        "Suspicious additional plugin (no 'lib/*.jar' files): $sourceDir"
      }
      allEntries.add(targetDir to entries)
    }

    allEntries
  }
}

internal suspend fun buildNonBundledPlugins(
  pluginsToPublish: Set<PluginLayout>,
  compressPluginArchive: Boolean,
  buildPlatformLibJob: Job?,
  state: DistributionBuilderState,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  context: BuildContext,
): List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>> {
  return context.executeStep(spanBuilder("build non-bundled plugins").setAttribute("count", pluginsToPublish.size.toLong()), BuildOptions.NON_BUNDLED_PLUGINS_STEP) {
    if (pluginsToPublish.isEmpty()) {
      return@executeStep emptyList()
    }

    val buildKeymapPluginsTask = if (context.options.buildStepsToSkip.contains(BuildOptions.KEYMAP_PLUGINS_STEP)) {
      null
    }
    else {
      async(CoroutineName("build keymap plugins")) {
        buildKeymapPlugins(targetDir = context.nonBundledPluginsToBePublished, context)
      }
    }
    val moduleOutputPatcher = ModuleOutputPatcher()
    val stageDir = context.paths.tempDir.resolve("non-bundled-plugins-${context.applicationInfo.productCode}")
    NioFiles.deleteRecursively(stageDir)
    val dirToJar = ConcurrentLinkedQueue<NonBundledPlugin>()

    // buildPlugins pluginBuilt listener is called concurrently
    val pluginSpecs = ConcurrentLinkedQueue<PluginRepositorySpec>()
    val prepareCustomPluginRepository = context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins &&
                                        !context.isStepSkipped(BuildOptions.ARCHIVE_PLUGINS)
    val plugins = pluginsToPublish.sortedWith(PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE)
    val mappings = buildPlugins(
      moduleOutputPatcher, plugins, os = null, stageDir, state, context, buildPlatformLibJob, searchableOptionSet
    ) { plugin, pluginDirOrFile ->
      val pluginVersion = if (plugin.mainModule == BUILT_IN_HELP_MODULE_NAME) {
        context.buildNumber
      }
      else {
        plugin.versionEvaluator.evaluate(
          pluginXmlSupplier = { (context as BuildContextImpl).jarPackagerDependencyHelper.getPluginXmlContent(context.findRequiredModule(plugin.mainModule)) },
          ideBuildVersion = context.pluginBuildNumber,
          context,
        ).pluginVersion
      }

      val targetDirectory = if (context.pluginAutoPublishList.test(plugin)) {
        context.nonBundledPluginsToBePublished
      }
      else {
        context.nonBundledPlugins
      }
      val destFile = targetDirectory.resolve("${plugin.directoryName}-$pluginVersion.zip")
      val pluginXml = moduleOutputPatcher.getPatchedPluginXml(plugin.mainModule)
      pluginSpecs.add(PluginRepositorySpec(destFile, pluginXml))
      dirToJar.add(NonBundledPlugin(pluginDirOrFile, destFile, !plugin.enableSymlinksAndExecutableResources))

      emptyList()
    }

    archivePlugins(dirToJar, compressPluginArchive, compressPluginArchive, context)

    val helpPlugin = buildHelpPlugin(context.pluginBuildNumber, context)
    if (helpPlugin != null) {
      val spec = buildHelpPlugin(
        helpPlugin, stageDir, context.nonBundledPluginsToBePublished, moduleOutputPatcher, state, searchableOptionSet, context
      )
      pluginSpecs.add(spec)
    }

    buildKeymapPluginsTask?.let {
      for (item in it.await()) {
        pluginSpecs.add(PluginRepositorySpec(pluginZip = item.first, pluginXml = item.second))
      }
    }

    if (prepareCustomPluginRepository) {
      val list = pluginSpecs.sortedBy { it.pluginZip }
      if (list.isNotEmpty()) {
        generatePluginRepositoryMetaFile(list, context.nonBundledPlugins, context.buildNumber)
      }

      val pluginsToBePublished = list.filter { it.pluginZip.startsWith(context.nonBundledPluginsToBePublished) }
      if (pluginsToBePublished.isNotEmpty()) {
        generatePluginRepositoryMetaFile(pluginsToBePublished, context.nonBundledPluginsToBePublished, context.buildNumber)
      }
    }

    validatePlugins(context, pluginSpecs)

    mappings
  } ?: emptyList()
}

private suspend fun validatePlugins(context: BuildContext, pluginSpecs: Collection<PluginRepositorySpec>) {
  context.executeStep(spanBuilder("plugins validation"), BuildOptions.VALIDATE_PLUGINS_TO_BE_PUBLISHED) { span ->
    for (plugin in pluginSpecs) {
      val path = plugin.pluginZip
      if (Files.notExists(path)) {
        span.addEvent("doesn't exist, skipped", Attributes.of(AttributeKey.stringKey("path"), "$path"))
        continue
      }
      launch(CoroutineName("$path plugin validation")) {
        validatePlugin(path, context, span)
      }
    }
  }
}

private fun validatePlugin(file: Path, context: BuildContext, span: Span) {
  val pluginManager = IdePluginManager.createManager()
  val result = pluginManager.createPlugin(pluginFile = file, validateDescriptor = true)
  // todo fix AddStatisticsEventLogListenerTemporary
  val id = when (result) {
    is PluginCreationSuccess -> result.plugin.pluginId
    is PluginCreationFail -> (pluginManager.createPlugin(pluginFile = file, validateDescriptor = false) as? PluginCreationSuccess)?.plugin?.pluginId
  }
  for (problem in context.productProperties.validatePlugin(id, result, context)) {
    val problemType = problem::class.java.simpleName
    span.addEvent(
      "plugin validation failed", Attributes.of(
      AttributeKey.stringKey("id"), "$id",
      AttributeKey.stringKey("path"), "$file",
      AttributeKey.stringKey("problemType"), problemType,
    )
    )
    context.messages.reportBuildProblem(
      description = "${id ?: file}, $problemType: $problem",
      identity = "${id ?: file}$problemType"
    )
  }
}

private suspend fun buildHelpPlugin(
  helpPlugin: PluginLayout,
  pluginsToPublishDir: Path,
  targetDir: Path,
  moduleOutputPatcher: ModuleOutputPatcher,
  state: DistributionBuilderState,
  searchableOptionSetDescriptor: SearchableOptionSetDescriptor?,
  context: BuildContext,
): PluginRepositorySpec {
  val directory = helpPlugin.directoryName
  val destFile = targetDir.resolve("$directory.zip")
  spanBuilder("build help plugin").setAttribute("dir", directory).use {
    val targetDir = pluginsToPublishDir.resolve(directory)
    buildPlugins(moduleOutputPatcher, listOf(helpPlugin), os = null, targetDir, state, context, buildPlatformJob = null, searchableOptionSetDescriptor)
    zipWithCompression(targetFile = destFile, dirs = mapOf(targetDir to ""))
    null
  }
  return PluginRepositorySpec(pluginZip = destFile, pluginXml = moduleOutputPatcher.getPatchedPluginXml(helpPlugin.mainModule))
}

internal suspend fun generateProjectStructureMapping(platformLayout: PlatformLayout, context: BuildContext): ContentReport {
  return coroutineScope {
    val moduleOutputPatcher = ModuleOutputPatcher()
    val libDirLayout = async(CoroutineName("layout platform distribution")) {
      layoutPlatformDistribution(
        moduleOutputPatcher, context.paths.distAllDir, platform = platformLayout, searchableOptionSet = null, copyFiles = false, context
      )
    }

    val allPlugins = getPluginLayoutsByJpsModuleNames(
      modules = context.getBundledPluginModules(),
      productLayout = context.productProperties.productLayout,
    )
    val entries = mutableListOf<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>()
    for (plugin in allPlugins) {
      if (satisfiesBundlingRequirements(plugin, osFamily = null, arch = null, context)) {
        val targetDirectory = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY).resolve(plugin.directoryName)
        entries.add(PluginBuildDescriptor(targetDirectory, os = null, plugin, moduleNames = emptyList()) to layoutDistribution(
          plugin, platformLayout, targetDirectory, copyFiles = false, moduleOutputPatcher, plugin.includedModules, searchableOptionSet = null, context
        ).first)
      }
    }
    ContentReport(platform = libDirLayout.await(), bundledPlugins = entries, nonBundledPlugins = emptyList())
  }
}

private class ScrambleTask(@JvmField val plugin: PluginLayout, @JvmField val pluginDir: Path, @JvmField val targetDir: Path)

internal suspend fun buildPlugins(
  moduleOutputPatcher: ModuleOutputPatcher,
  plugins: Collection<PluginLayout>,
  os: OsFamily?,
  targetDir: Path,
  state: DistributionBuilderState,
  context: BuildContext,
  buildPlatformJob: Job?,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  pluginBuilt: (suspend (PluginLayout, pluginDirOrFile: Path) -> List<DistributionFileEntry>)? = null,
): List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>> {
  val scrambleTool = context.proprietaryBuildTools.scrambleTool
  val isScramblingSkipped = context.options.buildStepsToSkip.contains(BuildOptions.SCRAMBLING_STEP)

  val scrambleTasks = mutableListOf<ScrambleTask>()

  val entries = coroutineScope {
    plugins.map { plugin ->
      if (plugin.mainModule != BUILT_IN_HELP_MODULE_NAME) {
        checkOutputOfPluginModules(plugin.mainModule, plugin.includedModules, plugin.moduleExcludes, context)
        patchPluginXml(
          moduleOutputPatcher, plugin, context.applicationInfo.majorReleaseDate, context.applicationInfo.releaseVersionForLicensing, state.pluginsToPublish,
          (context as BuildContextImpl).jarPackagerDependencyHelper, state.platform, context
        )
      }

      val directoryName = plugin.directoryName
      val pluginDir = targetDir.resolve(directoryName)
      val task = async(CoroutineName("Build plugin (module=${plugin.mainModule})")) {
        spanBuilder("plugin").setAttribute("path", context.paths.buildOutputDir.relativize(pluginDir).toString()).use {
          val (entries, file) = layoutDistribution(
            plugin, state.platform, pluginDir, copyFiles = true, moduleOutputPatcher, plugin.includedModules, searchableOptionSet, context
          )

          if (pluginBuilt == null) {
            entries
          }
          else {
            entries + pluginBuilt(plugin, file)
          }
        }
      }

      if (!plugin.pathsToScramble.isEmpty()) {
        val attributes = Attributes.of(AttributeKey.stringKey("plugin"), directoryName)
        if (scrambleTool == null) {
          Span.current().addEvent("skip scrambling plugin because scrambleTool isn't defined, but plugin defines paths to be scrambled", attributes)
        }
        else if (isScramblingSkipped) {
          Span.current().addEvent("skip scrambling plugin because step is disabled", attributes)
        }
        else {
          // we cannot start executing right now because the plugin can use other plugins in a scramble classpath
          scrambleTasks.add(ScrambleTask(plugin = plugin, pluginDir = pluginDir, targetDir = targetDir))
        }
      }

      PluginBuildDescriptor(dir = pluginDir, os = os, layout = plugin, moduleNames = emptyList()) to task.await()
    }
  }

  if (scrambleTasks.isNotEmpty()) {
    checkNotNull(scrambleTool)

    // scrambling can require classes from the platform
    buildPlatformJob?.let { task ->
      spanBuilder("wait for platform lib for scrambling").use { task.join() }
    }
    coroutineScope {
      for (scrambleTask in scrambleTasks) {
        launch(CoroutineName("scramble plugin ${scrambleTask.plugin.directoryName}")) {
          scrambleTool.scramblePlugin(
            pluginLayout = scrambleTask.plugin,
            targetDir = scrambleTask.pluginDir,
            additionalPluginDir = scrambleTask.targetDir,
            layouts = plugins,
            context = context,
          )
        }
      }
    }
  }
  return entries
}

private const val PLUGINS_DIRECTORY = "plugins"
private const val LIB_DIRECTORY = "lib"

const val PLUGIN_CLASSPATH: String = "$PLUGINS_DIRECTORY/plugin-classpath.txt"

private val PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE: Comparator<PluginLayout> = compareBy { it.mainModule }

@VisibleForTesting
class PluginRepositorySpec(@JvmField val pluginZip: Path, @JvmField val pluginXml: ByteArray /* content of plugin.xml */)

fun getPluginLayoutsByJpsModuleNames(modules: Collection<String>, productLayout: ProductModulesLayout, toPublish: Boolean = false): MutableSet<PluginLayout> {
  if (modules.isEmpty()) {
    return createPluginLayoutSet(expectedSize = 0)
  }

  val layoutsByMainModule = productLayout.pluginLayouts.groupByTo(HashMap()) { it.mainModule }
  val result = createPluginLayoutSet(modules.size)
  for (moduleName in modules) {
    val layouts = layoutsByMainModule.get(moduleName) ?: mutableListOf(PluginLayout.pluginAuto(listOf(moduleName)))
    if (toPublish && layouts.size == 2 && layouts[0].bundlingRestrictions != layouts[1].bundlingRestrictions) {
      layouts.retainAll { it.bundlingRestrictions == PluginBundlingRestrictions.MARKETPLACE }
    }
    for (layout in layouts) {
      check(result.add(layout)) {
        "Plugin layout for module $moduleName is already added (duplicated module name?)"
      }
    }
  }
  return result
}

private fun basePath(buildContext: BuildContext, moduleName: String): Path =
  Path.of(JpsPathUtil.urlToPath(buildContext.findRequiredModule(moduleName).contentRootsList.urls.first()))

suspend fun buildLib(
  moduleOutputPatcher: ModuleOutputPatcher,
  platform: PlatformLayout,
  searchableOptionSetDescriptor: SearchableOptionSetDescriptor?,
  context: BuildContext,
): List<DistributionFileEntry> {
  val targetDirectory = context.paths.distAllDir
  val libDirMappings = layoutPlatformDistribution(
    moduleOutputPatcher, targetDirectory, platform, searchableOptionSetDescriptor, copyFiles = true, context
  )
  context.proprietaryBuildTools.scrambleTool?.validatePlatformLayout(platform.includedModules, context)
  return libDirMappings
}

suspend fun layoutPlatformDistribution(
  moduleOutputPatcher: ModuleOutputPatcher,
  targetDirectory: Path,
  platform: PlatformLayout,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  copyFiles: Boolean,
  context: BuildContext,
): List<DistributionFileEntry> {
  if (copyFiles) {
    coroutineScope {
      createStatisticsRecorderBundledMetadataProviderTask(moduleOutputPatcher, context)
      launch(CoroutineName("patch keymap with Alt click reassigned to multiple carets")) {
        patchKeyMapWithAltClickReassignedToMultipleCarets(moduleOutputPatcher, context)
      }
      launch(CoroutineName("write patched app info")) {
        spanBuilder("write patched app info").use {
          val moduleName = "intellij.platform.core"
          val module = context.findRequiredModule(moduleName)
          val relativePath = "com/intellij/openapi/application/ApplicationNamesInfo.class"
          val sourceBytes = context.readFileContentFromModuleOutput(module, relativePath) ?: error("app info not found")
          val patchedBytes = injectAppInfo(inFileBytes = sourceBytes, newFieldValue = context.appInfoXml)
          moduleOutputPatcher.patchModuleOutput(moduleName, relativePath, patchedBytes)
        }
      }
    }
  }

  return spanBuilder("layout lib")
    .setAttribute("path", targetDirectory.toString())
    .use {
      layoutDistribution(layout = platform, platformLayout = platform, targetDirectory, copyFiles, moduleOutputPatcher, platform.includedModules, searchableOptionSet, context)
        .first
    }
}

private suspend fun patchKeyMapWithAltClickReassignedToMultipleCarets(moduleOutputPatcher: ModuleOutputPatcher, context: BuildContext) {
  if (!context.productProperties.reassignAltClickToMultipleCarets) {
    return
  }

  val moduleName = "intellij.platform.resources"
  val relativePath = $$"keymaps/$default.xml"
  val sourceFileContent = context.readFileContentFromModuleOutput(context.findRequiredModule(moduleName), relativePath)
                          ?: error("Not found '$relativePath' in module $moduleName output")
  var text = String(sourceFileContent, StandardCharsets.UTF_8)
  text = text.replace("<mouse-shortcut keystroke=\"alt button1\"/>", "<mouse-shortcut keystroke=\"to be alt shift button1\"/>")
  text = text.replace("<mouse-shortcut keystroke=\"alt shift button1\"/>", "<mouse-shortcut keystroke=\"alt button1\"/>")
  text = text.replace("<mouse-shortcut keystroke=\"to be alt shift button1\"/>", "<mouse-shortcut keystroke=\"alt shift button1\"/>")
  moduleOutputPatcher.patchModuleOutput(moduleName, relativePath, text)
}

fun getOsAndArchSpecificDistDirectory(osFamily: OsFamily, arch: JvmArchitecture, libc: LibcImpl, context: BuildContext): Path =
  context.paths.buildOutputDir.resolve("dist.${osFamily.distSuffix}.${arch.name}${if (libc == LinuxLibcImpl.MUSL) { "-musl" } else {""} }")

private suspend fun checkOutputOfPluginModules(
  mainPluginModule: String,
  includedModules: Collection<ModuleItem>,
  moduleExcludes: Map<String, List<String>>,
  context: BuildContext,
) {
  for (module in includedModules.asSequence().map { it.moduleName }.distinct()) {
    if (
      module == "intellij.java.guiForms.rt" ||
      !containsFileInOutput(module, "com/intellij/uiDesigner/core/GridLayoutManager.class", moduleExcludes[module] ?: emptyList(), context)
    ) {
      "Runtime classes of GUI designer must not be packaged to '$module' module in '$mainPluginModule' plugin, " +
      "because they are included into a platform JAR. Make sure that 'Automatically copy form runtime classes " +
      "to the output directory' is disabled in Settings | Editor | GUI Designer."
    }
  }
}

private suspend fun containsFileInOutput(moduleName: String, filePath: String, excludes: Collection<String>, context: BuildContext): Boolean {
  val exists = context.hasModuleOutputPath(context.findRequiredModule(moduleName), filePath)
  if (!exists) {
    return false
  }

  for (exclude in excludes) {
    if (antToRegex(exclude).matches(FileUtilRt.toSystemIndependentName(filePath))) {
      return false
    }
  }

  return true
}

private suspend fun scramble(platform: PlatformLayout, context: BuildContext) {
  val tool = context.proprietaryBuildTools.scrambleTool
  if (tool == null) {
    Span.current().addEvent("skip scrambling because `scrambleTool` isn't defined")
  }
  else {
    tool.scramble(platform, context)
  }
}

private fun CoroutineScope.createBuildBrokenPluginListJob(context: BuildContext): Job {
  val buildString = context.fullBuildNumber
  return createSkippableJob(
    spanBuilder("build broken plugin list").setAttribute("buildNumber", buildString),
    BuildOptions.BROKEN_PLUGINS_LIST_STEP,
    context,
  ) {
    val data = buildBrokenPlugins(currentBuildString = buildString, isInDevelopmentMode = context.options.isInDevelopmentMode)
    if (data != null) {
      context.addDistFile(DistFile(content = InMemoryDistFileContent(data), relativePath = "bin/brokenPlugins.db"))
    }
  }
}

private fun CoroutineScope.createBuildThirdPartyLibraryListJob(entries: Sequence<DistributionFileEntry>, context: BuildContext): Job {
  return createSkippableJob(spanBuilder("generate table of licenses for used third-party libraries"),
                            BuildOptions.THIRD_PARTY_LIBRARIES_LIST_STEP, context) {
    val generator = createLibraryLicensesListGenerator(
      context, context.productProperties.allLibraryLicenses, getIncludedModules(entries).toHashSet()
    )
    val distAllDir = context.paths.distAllDir
    withContext(Dispatchers.IO) {
      Files.createDirectories(distAllDir)

      val htmlFilePath = distAllDir.resolve("license/third-party-libraries.html")
      val jsonFilePath = distAllDir.resolve("license/third-party-libraries.json")

      generator.generateHtml(htmlFilePath)
      generator.generateJson(jsonFilePath)

      if (context.productProperties.generateLibraryLicensesTable) {
        val artifactNamePrefix = context.productProperties.getBaseArtifactName(context)
        val htmlArtifact = context.paths.artifactDir.resolve("$artifactNamePrefix-third-party-libraries.html")
        val jsonArtifact = context.paths.artifactDir.resolve("$artifactNamePrefix-third-party-libraries.json")
        Files.createDirectories(context.paths.artifactDir)
        Files.copy(htmlFilePath, htmlArtifact)
        Files.copy(jsonFilePath, jsonArtifact)
        context.notifyArtifactBuilt(htmlArtifact)
        context.notifyArtifactBuilt(jsonArtifact)
      }
    }
  }
}

fun satisfiesBundlingRequirements(plugin: PluginLayout, osFamily: OsFamily?, arch: JvmArchitecture?, context: BuildContext): Boolean {
  if (context.options.bundledPluginDirectoriesToSkip.contains(plugin.directoryName)) {
    return false
  }

  val bundlingRestrictions = plugin.bundlingRestrictions
  if (bundlingRestrictions == PluginBundlingRestrictions.MARKETPLACE) {
    return false
  }

  if (context.options.useReleaseCycleRelatedBundlingRestrictionsForContentReport) {
    val isNightly = context.isNightlyBuild
    val isEap = context.applicationInfo.isEAP

    val distributionCondition = when (bundlingRestrictions.includeInDistribution) {
      PluginDistribution.ALL -> true
      PluginDistribution.NOT_FOR_RELEASE -> isNightly || isEap
      PluginDistribution.NOT_FOR_PUBLIC_BUILDS -> isNightly
    }
    if (!distributionCondition) {
      return false
    }
  }

  return when {
    osFamily == null && bundlingRestrictions.supportedOs != OsFamily.ALL -> false
    osFamily != null && (bundlingRestrictions.supportedOs == OsFamily.ALL || !bundlingRestrictions.supportedOs.contains(osFamily)) -> false
    arch == null && bundlingRestrictions.supportedArch != JvmArchitecture.ALL -> false
    else -> arch == null || bundlingRestrictions.supportedArch.contains(arch)
  }
}

private suspend fun buildKeymapPlugins(targetDir: Path, context: BuildContext): List<Pair<Path, ByteArray>> {
  val keymapDir = context.paths.communityHomeDir.resolve("platform/platform-resources/src/keymaps")
  Files.createDirectories(targetDir)
  return spanBuilder("build keymap plugins").use(Dispatchers.IO) {
    listOf(
      arrayOf("Mac OS X", "Mac OS X 10.5+"),
      arrayOf("Default for GNOME"),
      arrayOf("Default for KDE"),
      arrayOf("Default for XWin"),
      arrayOf("Emacs"),
      arrayOf("Sublime Text", "Sublime Text (Mac OS X)"),
    ).map {
      async(CoroutineName("build keymap plugin for ${it[0]}")) {
        buildKeymapPlugin(keymaps = it, context.buildNumber, targetDir, keymapDir)
      }
    }
  }.map { it.getCompleted() }
}

suspend fun layoutDistribution(
  layout: BaseLayout,
  platformLayout: PlatformLayout,
  targetDirectory: Path,
  copyFiles: Boolean = true,
  moduleOutputPatcher: ModuleOutputPatcher,
  includedModules: Collection<ModuleItem>,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  context: BuildContext,
): Pair<List<DistributionFileEntry>, Path> {
  if (copyFiles) {
    withContext(Dispatchers.IO) {
      Files.createDirectories(targetDirectory)

      if (!layout.moduleExcludes.isEmpty()) {
        launch(CoroutineName("check module excludes")) {
          checkModuleExcludes(layout.moduleExcludes, context)
        }
      }

      // patchers must be executed _before_ packing, because patchers patch the module output
      val patchers = layout.patchers
      if (!patchers.isEmpty()) {
        spanBuilder("execute custom patchers").setAttribute("count", patchers.size.toLong()).use {
          for (patcher in patchers) {
            patcher(moduleOutputPatcher, platformLayout, context)
          }
        }
      }
    }
  }

  val entries = coroutineScope {
    val tasks = ArrayList<Deferred<Collection<DistributionFileEntry>>>(3)
    val outputDir = targetDirectory.resolve(LIB_DIRECTORY)
    tasks.add(async(CoroutineName("pack $outputDir")) {
      spanBuilder("pack").setAttribute("outputDir", outputDir.toString()).use {
        JarPackager.pack(
          includedModules,
          outputDir,
          isRootDir = layout is PlatformLayout,
          layout = layout,
          platformLayout = platformLayout,
          moduleOutputPatcher = moduleOutputPatcher,
          searchableOptionSet = searchableOptionSet,
          dryRun = !copyFiles,
          context = context,
        )
      }
    })

    if (
      copyFiles &&
      !context.options.skipCustomResourceGenerators &&
      (layout.resourcePaths.isNotEmpty() || layout is PluginLayout && !layout.resourceGenerators.isEmpty())
    ) {
      tasks.add(async(Dispatchers.IO + CoroutineName("pack additional resources")) {
        spanBuilder("pack additional resources").use {
          layoutAdditionalResources(layout, context, targetDirectory)
          emptyList()
        }
      })
    }

    if (!layout.includedArtifacts.isEmpty()) {
      tasks.add(async(CoroutineName("pack artifacts")) {
        spanBuilder("pack artifacts").use {
          layoutArtifacts(layout, context, copyFiles, targetDirectory)
        }
      })
    }
    tasks
  }.flatMap { it.getCompleted() }

  return entries to targetDirectory
}

private fun layoutResourcePaths(layout: BaseLayout, context: BuildContext, targetDirectory: Path, overwrite: Boolean) {
  for (resourceData in layout.resourcePaths) {
    val source = basePath(context, resourceData.moduleName).resolve(resourceData.resourcePath).normalize()
    var target = targetDirectory.resolve(resourceData.relativeOutputPath).normalize()
    if (resourceData.packToZip) {
      if (Files.isDirectory(source)) {
        // do not compress - doesn't make sense as it is a part of distribution
        zip(targetFile = target, dirs = mapOf(source to ""), overwrite = overwrite)
      }
      else {
        target = target.resolve(source.fileName)
        Compressor.Zip(target).use { it.addFile(target.fileName.toString(), source) }
      }
    }
    else {
      if (Files.isRegularFile(source)) {
        if (overwrite) {
          val targetFile = target.resolve(source.fileName)
          Files.createDirectories(target)
          Files.copy(source, targetFile, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
        }
        else {
          copyFileToDir(source, target)
        }
      }
      else {
        if (overwrite) {
          copyDir(source, target, fileFilter = {
            copyIfChanged(target, source, it)
          })
        }
        else {
          copyDir(source, target)
        }
      }
    }
  }
}

private fun copyIfChanged(targetDir: Path, sourceDir: Path, sourceFile: Path): Boolean {
  val targetFile = targetDir.resolve(sourceDir.relativize(sourceFile))
  val t = try {
    Files.getLastModifiedTime(targetFile).toMillis()
  }
  catch (_: NoSuchFileException) {
    return true
  }
  val s = Files.getLastModifiedTime(sourceFile).toMillis()
  if (t == s) {
    return false
  }
  Files.delete(targetFile)
  return true
}

private suspend fun layoutAdditionalResources(layout: BaseLayout, context: BuildContext, targetDirectory: Path) {
  // quick fix for a very annoying FileAlreadyExistsException in CLion dev build
  val overwrite = ("intellij.rider.plugins.clion.radler" == (layout as? PluginLayout)?.mainModule)
  layoutResourcePaths(layout, context, targetDirectory, overwrite)
  if (layout !is PluginLayout) {
    return
  }

  val resourceGenerators = layout.resourceGenerators
  if (!resourceGenerators.isEmpty()) {
    spanBuilder("generate and pack resources").use {
      for (item in resourceGenerators) {
        item(targetDirectory, context)
      }
    }
  }
}

@OptIn(ExperimentalPathApi::class)
private suspend fun layoutArtifacts(
  layout: BaseLayout,
  context: BuildContext,
  copyFiles: Boolean,
  targetDirectory: Path,
): Collection<DistributionFileEntry> {
  val span = Span.current()
  val entries = mutableListOf<DistributionFileEntry>()
  val jpsArtifactService = JpsArtifactService.getInstance()
  for ((artifactName, relativePath) in layout.includedArtifacts.entries) {
    span.addEvent("include artifact", Attributes.of(AttributeKey.stringKey("artifactName"), artifactName))
    val artifact = jpsArtifactService.getArtifacts(context.project).find { it.name == artifactName }
                   ?: error("Cannot find artifact '$artifactName' in the project")
    var artifactPath = targetDirectory.resolve(LIB_DIRECTORY).resolve(relativePath)
    val sourcePath = artifact.outputFilePath?.let(Path::of) ?: error("Missing output path for '$artifactName' artifact")
    if (copyFiles) {
      require(withContext(Dispatchers.IO) { Files.exists(sourcePath) }) {
        "Missing output file for '$artifactName' artifact: outputFilePath=${artifact.outputFilePath}, outputPath=${artifact.outputPath}"
      }
    }
    if (artifact.outputFilePath == artifact.outputPath) {
      if (copyFiles) {
        withContext(Dispatchers.IO) {
          sourcePath.copyToRecursively(artifactPath, followLinks = false)
        }
      }
    }
    else {
      artifactPath = artifactPath.resolve(sourcePath.fileName)
      if (copyFiles) {
        withContext(Dispatchers.IO) {
          copyFile(sourcePath, artifactPath)
        }
      }
    }
    addArtifactMapping(artifact, entries, artifactPath)
  }
  return entries
}

private fun addArtifactMapping(artifact: JpsArtifact, entries: MutableCollection<DistributionFileEntry>, artifactFile: Path) {
  val rootElement = artifact.rootElement
  for (element in rootElement.children) {
    if (element is JpsProductionModuleOutputPackagingElement) {
      entries.add(ModuleOutputEntry(
        artifactFile, element.moduleReference.moduleName, size = 0, hash = 0, relativeOutputFile = "", reason = "artifact: ${artifact.name}"
      ))
    }
    else if (element is JpsTestModuleOutputPackagingElement) {
      entries.add(ModuleTestOutputEntry(path = artifactFile, moduleName = element.moduleReference.moduleName))
    }
    else if (element is JpsLibraryFilesPackagingElement) {
      val library = element.libraryReference.resolve()
      val parentReference = library!!.createReference().parentReference
      if (parentReference is JpsModuleReference) {
        entries.add(ModuleLibraryFileEntry(
          artifactFile, parentReference.moduleName, getLibraryFilename(library), libraryFile = null, hash = 0, size = 0, relativeOutputFile = null
        ))
      }
      else {
        val libraryData = ProjectLibraryData(libraryName = library.name, reason = "<- artifact ${artifact.name}")
        entries.add(ProjectLibraryEntry(
          artifactFile, libraryData, libraryFile = null, hash = 0, size = 0, relativeOutputFile = null
        ))
      }
    }
  }
}

private suspend fun checkModuleExcludes(moduleExcludes: Map<String, List<String>>, context: CompilationContext) {
  for (module in moduleExcludes.keys) {
    check(context.getModuleOutputRoots(context.findRequiredModule(module)).all(Files::exists)) {
      "There are excludes defined for module '${module}', but the module wasn't compiled;" +
      " most probably it means that '${module}' isn't included into the product distribution," +
      " so it doesn't make sense to define excludes for it."
    }
  }
}

private data class NonBundledPlugin(
  @JvmField val sourceDir: Path,
  @JvmField val targetZip: Path,
  @JvmField val optimizedZip: Boolean,
)

private suspend fun archivePlugins(items: Collection<NonBundledPlugin>, compress: Boolean, withBlockMap: Boolean, context: BuildContext) {
  context.executeStep(
    spanBuilder = spanBuilder("archive plugins").setAttribute(AttributeKey.longKey("count"), items.size.toLong()),
    stepId = BuildOptions.ARCHIVE_PLUGINS
  ) {
    val json by lazy { JSON.std.without(JSON.Feature.USE_FIELDS) }
    for ((source, target, optimized) in items) {
      launch(CoroutineName("archive plugin $source")) {
        spanBuilder("archive plugin")
          .setAttribute("input", source.toString())
          .setAttribute("outputFile", target.toString())
          .setAttribute("optimizedZip", optimized)
          .use {
            archivePlugin(optimized, target, compress, source, context)
          }
        if (withBlockMap) {
          spanBuilder("build plugin blockmap").setAttribute("file", target.toString()).use {
            buildBlockMap(target, json)
          }
        }
      }
    }
  }
}

private fun archivePlugin(optimized: Boolean, target: Path, compress: Boolean, source: Path, context: BuildContext) {
  if (optimized) {
    writeNewZipWithoutIndex(target, compress) { zipCreator ->
      val archiver = ZipArchiver()
      if (Files.isDirectory(source)) {
        archiver.setRootDir(source, source.fileName.toString())
        archiveDir(startDir = source, addFile = { archiver.addFile(it, zipCreator) })
      }
      else {
        archiver.setRootDir(source.parent)
        archiver.addFile(source, zipCreator)
      }
    }
  }
  else {
    writeNewFile(target) { outFileChannel ->
      NoDuplicateZipArchiveOutputStream(outFileChannel, context.options.compressZipFiles).use { out ->
        out.setUseZip64(Zip64Mode.Never)
        out.dir(source, "${source.fileName}/", entryCustomizer = { entry, file, _ ->
          if (Files.isExecutable(file)) {
            entry.unixMode = executableFileUnixMode
          }
        })
      }
    }
  }
}

/**
 * Builds a blockmap and hash files for a plugin.
 */
private fun buildBlockMap(file: Path, json: JSON) {
  val algorithm = "SHA-256"
  val bytes = Files.newInputStream(file).use { input ->
    json.asBytes(BlockMap(input, algorithm))
  }

  val fileParent = file.parent
  val fileName = file.fileName.toString()
  writeNewZipWithoutIndex(fileParent.resolve("$fileName.blockmap.zip"), compress = true) {
    it.compressedData("blockmap.json", ByteBuffer.wrap(bytes))
  }

  val hashFile = fileParent.resolve("$fileName.hash.json")
  Files.newInputStream(file).use { input ->
    Files.newOutputStream(hashFile, *W_CREATE_NEW.toTypedArray()).use { output ->
      json.write(FileHash(input, algorithm), output)
    }
  }
}

private fun sortEntries(unsorted: List<DistributionFileEntry>): List<DistributionFileEntry> {
  // sort because projectStructureMapping is a concurrent collection
  // call invariantSeparatorsPathString because the result of Path ordering is platform-dependent
  return unsorted.sortedWith(
    compareBy(
      { it.path.invariantSeparatorsPathString },
      { it.type },
      { (it as? ModuleOutputEntry)?.moduleName },
      { (it as? LibraryFileEntry)?.libraryFile?.let(::isFromLocalMavenRepo) != true },
      { (it as? LibraryFileEntry)?.libraryFile?.invariantSeparatorsPathString },
    )
  )
}

// also, put libraries from Maven repo ahead of others, for them to not depend on the lexicographical order of Maven repo and source path
private fun isFromLocalMavenRepo(path: Path) = path.startsWith(MAVEN_REPO)

suspend fun createIdeClassPath(platformLayout: PlatformLayout, context: BuildContext): Collection<String> {
  val contentReport = generateProjectStructureMapping(platformLayout, context)

  val pluginLayouts = context.productProperties.productLayout.pluginLayouts
  val classPath = LinkedHashSet<Path>()

  val libDir = context.paths.distAllDir.resolve("lib")
  for (entry in sortEntries(contentReport.platform)) {
    if (!(entry is ModuleOutputEntry && entry.reason == ModuleIncludeReasons.PRODUCT_MODULES)) {
      val relativePath = libDir.relativize(entry.path)
      if (relativePath.nameCount != 1 && !relativePath.startsWith("modules")) {
        continue
      }
    }

    when (entry) {
      is ModuleOutputEntry -> {
        classPath.addAll(context.getModuleOutputRoots(context.findRequiredModule(entry.moduleName)))
      }
      is LibraryFileEntry -> classPath.add(entry.libraryFile!!)
      else -> throw UnsupportedOperationException("Entry $entry is not supported")
    }
  }

  val pluginDir = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY)
  for (entry in contentReport.bundledPlugins.flatMap { it.second }) {
    val relativePath = pluginDir.relativize(entry.path)
    // for plugins, our classloaders load JARs only from the "lib/" and "lib/modules/" directories
    if (!(relativePath.nameCount in 3..4 && relativePath.getName(1).toString() == LIB_DIRECTORY && 
          (relativePath.nameCount == 3 || relativePath.getName(2).toString() == "modules"))) {
      continue
    }

    when (entry) {
      is ModuleOutputEntry -> {
        classPath.addAll(context.getModuleOutputRoots(context.findRequiredModule(entry.moduleName)))
        for (classpathPluginEntry in pluginLayouts.firstOrNull { it.mainModule == entry.moduleName }?.scrambleClasspathPlugins ?: emptyList()) {
          classPath.addAll(context.getModuleOutputRoots(context.findRequiredModule(classpathPluginEntry.pluginMainModuleName)))
        }
      }
      is LibraryFileEntry -> classPath.add(entry.libraryFile!!)
      is CustomAssetEntry -> { }
      else -> throw UnsupportedOperationException("Entry $entry is not supported")
    }
  }
  return classPath.map { it.toString() }
}
