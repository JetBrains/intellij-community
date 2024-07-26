// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.fasterxml.jackson.jr.ob.JSON
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.intellij.build.telemetry.useWithScope
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
import kotlinx.coroutines.*
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.fus.createStatisticsRecorderBundledMetadataProviderTask
import org.jetbrains.intellij.build.impl.projectStructureMapping.*
import org.jetbrains.intellij.build.io.*
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
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Predicate
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.useLines

/**
 * Assembles output of modules to platform JARs (in [BuildPaths.distAllDir]/lib directory),
 * bundled plugins' JARs (in [distAll][BuildPaths.distAllDir]/plugins directory) and zip archives with
 * non-bundled plugins (in [artifacts][BuildPaths.artifactDir]/plugins directory).
 */
internal suspend fun buildDistribution(
  state: DistributionBuilderState,
  context: BuildContext,
  isUpdateFromSources: Boolean = false,
): ContentReport = coroutineScope {
  validateModuleStructure(state.platform, context)
  context.productProperties.validateLayout(state.platform, context)
  createBuildBrokenPluginListJob(context)

  val productRunner = context.createProductRunner()
  if (context.productProperties.buildDocAuthoringAssets) {
    launch {
      buildAdditionalAuthoringArtifacts(productRunner = productRunner, context = context)
    }
  }

  val traceContext = Context.current().asContextElement()
  val contentReport = coroutineScope {
    // must be completed before plugin building
    val searchableOptionSet = context.executeStep(spanBuilder("build searchable options index"), BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP) {
      buildSearchableOptions(productRunner = productRunner, context = context)
    }

    val pluginLayouts = getPluginLayoutsByJpsModuleNames(
      modules = context.bundledPluginModules,
      productLayout = context.productProperties.productLayout,
    )
    val moduleOutputPatcher = ModuleOutputPatcher()
    val buildPlatformJob: Deferred<List<DistributionFileEntry>> = async(traceContext) {
      spanBuilder("build platform lib").useWithScope {
        val result = buildLib(moduleOutputPatcher = moduleOutputPatcher, platform = state.platform, searchableOptionSetDescriptor = searchableOptionSet, context = context)
        if (!isUpdateFromSources && context.productProperties.scrambleMainJar) {
          scramble(state.platform, context)
        }

        val distAllDir = context.paths.distAllDir
        val libDir = distAllDir.resolve("lib")
        context.bootClassPathJarNames = if (context.useModularLoader) listOf(PLATFORM_LOADER_JAR) else generateClasspath(homeDir = distAllDir, libDir = libDir)
        result
      }
    }

    val buildNonBundledPlugins = async {
      buildNonBundledPlugins(
        pluginsToPublish = state.pluginsToPublish,
        compressPluginArchive = !isUpdateFromSources && context.options.compressZipFiles,
        buildPlatformLibJob = buildPlatformJob,
        state = state,
        searchableOptionSet = searchableOptionSet,
        context = context,
      )
    }

    val bundledPluginItems = buildBundledPluginsForAllPlatforms(
      state = state,
      pluginLayouts = pluginLayouts,
      isUpdateFromSources = isUpdateFromSources,
      buildPlatformJob = buildPlatformJob,
      searchableOptionSetDescriptor = searchableOptionSet,
      moduleOutputPatcher = moduleOutputPatcher,
      context = context,
    )

    ContentReport(
      platform = buildPlatformJob.await(),
      bundledPlugins = bundledPluginItems,
      nonBundledPlugins = buildNonBundledPlugins.await(),
    )
  }

  coroutineScope {
    launch(Dispatchers.IO) {
      spanBuilder("generate content report").useWithScope {
        Files.createDirectories(context.paths.artifactDir)
        val contentMappingJson = context.paths.artifactDir.resolve("content-mapping.json")
        writeProjectStructureReport(contentReport = contentReport, file = contentMappingJson, buildPaths = context.paths)
        val contentReportFile = context.paths.artifactDir.resolve("content-report.zip")
        writeNewZipWithoutIndex(contentReportFile) { zipFileWriter ->
          buildJarContentReport(contentReport = contentReport, zipFileWriter = zipFileWriter, buildPaths = context.paths, context = context)
        }
        context.notifyArtifactBuilt(contentMappingJson)
        context.notifyArtifactBuilt(contentReportFile)
      }
    }
    createBuildThirdPartyLibraryListJob(contentReport.combined(), context)
    if (context.useModularLoader || context.generateRuntimeModuleRepository) {
      launch(Dispatchers.IO) {
        spanBuilder("generate runtime module repository").useWithScope {
          generateRuntimeModuleRepository(contentReport.combined(), context)
        }
      }
    }
  }
  contentReport
}

private suspend fun buildBundledPluginsForAllPlatforms(
  state: DistributionBuilderState,
  pluginLayouts: MutableSet<PluginLayout>,
  isUpdateFromSources: Boolean,
  buildPlatformJob: Deferred<List<DistributionFileEntry>>,
  searchableOptionSetDescriptor: SearchableOptionSetDescriptor?,
  moduleOutputPatcher: ModuleOutputPatcher,
  context: BuildContext,
): List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>> {
  return coroutineScope {
    val commonDeferred = async {
      doBuildBundledPlugins(
        state = state,
        plugins = pluginLayouts,
        isUpdateFromSources = isUpdateFromSources,
        buildPlatformJob = buildPlatformJob,
        searchableOptionSet = searchableOptionSetDescriptor,
        moduleOutputPatcher = moduleOutputPatcher,
        context = context,
      )
    }

    val additionalDeferred = async {
      copyAdditionalPlugins(context = context, pluginDir = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY))
    }

    val pluginDirs = getPluginDirs(context = context, isUpdateFromSources = isUpdateFromSources)
    val specificDeferred = async {
      buildOsSpecificBundledPlugins(
        state = state,
        plugins = pluginLayouts,
        isUpdateFromSources = isUpdateFromSources,
        buildPlatformJob = buildPlatformJob,
        context = context,
        searchableOptionSet = searchableOptionSetDescriptor,
        pluginDirs = pluginDirs,
        moduleOutputPatcher = moduleOutputPatcher,
      )
    }

    val common = commonDeferred.await()
    val specific = specificDeferred.await()
    buildPlatformJob.join()
    writePluginInfo(
      moduleOutputPatcher = moduleOutputPatcher,
      pluginDirs = pluginDirs,
      common = common,
      specific = specific,
      additional = additionalDeferred.await(),
      context = context,
    )
    listOf(common, specific.values.flatten())
  }.flatten()
}

private fun writePluginInfo(
  moduleOutputPatcher: ModuleOutputPatcher,
  pluginDirs: List<Pair<SupportedDistribution, Path>>,
  common: List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>,
  specific: Map<SupportedDistribution, List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>>,
  additional: List<Pair<Path, List<Path>>>?,
  context: BuildContext,
) {
  val commonClassPath = generatePluginClassPath(pluginEntries = common, moduleOutputPatcher = moduleOutputPatcher)
  val additionalClassPath = additional?.let { generatePluginClassPathFromPrebuiltPluginFiles(it) }

  for ((supportedDist) in pluginDirs) {
    val specificList = specific.get(supportedDist)
    val specificClasspath = specificList?.let { generatePluginClassPath(pluginEntries = it, moduleOutputPatcher = moduleOutputPatcher) }

    val byteOut = ByteArrayOutputStream()
    val out = DataOutputStream(byteOut)
    val pluginCount = common.size + (additional?.size ?: 0) + (specificList?.size ?: 0)
    writePluginClassPathHeader(out = out, isJarOnly = true, pluginCount = pluginCount, moduleOutputPatcher = moduleOutputPatcher, context = context)
    out.write(commonClassPath)
    additionalClassPath?.let { out.write(it) }
    specificClasspath?.let { out.write(it) }
    out.close()

    context.addDistFile(DistFile(relativePath = PLUGIN_CLASSPATH, content = InMemoryDistFileContent(byteOut.toByteArray()), os = supportedDist.os, arch = supportedDist.arch))
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

private fun getPluginDirs(context: BuildContext, isUpdateFromSources: Boolean): List<Pair<SupportedDistribution, Path>> {
  if (isUpdateFromSources) {
    return listOf(SupportedDistribution(OsFamily.currentOs, JvmArchitecture.currentJvmArch) to context.paths.distAllDir.resolve(PLUGINS_DIRECTORY))
  }
  else {
    return SUPPORTED_DISTRIBUTIONS.map {
      it to getOsAndArchSpecificDistDirectory(osFamily = it.os, arch = it.arch, context = context).resolve(PLUGINS_DIRECTORY)
    }
  }
}

suspend fun buildBundledPlugins(
  state: DistributionBuilderState,
  plugins: Collection<PluginLayout>,
  isUpdateFromSources: Boolean,
  buildPlatformJob: Job?,
  searchableOptionSetDescriptor: SearchableOptionSetDescriptor?,
  moduleOutputPatcher: ModuleOutputPatcher,
  context: BuildContext,
) {
  doBuildBundledPlugins(
    state = state,
    plugins = plugins,
    isUpdateFromSources = isUpdateFromSources,
    buildPlatformJob = buildPlatformJob,
    searchableOptionSet = searchableOptionSetDescriptor,
    moduleOutputPatcher = moduleOutputPatcher,
    context = context,
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
    .useWithScope { span ->
      val pluginsToBundle = ArrayList<PluginLayout>(plugins.size)
      plugins.filterTo(pluginsToBundle) { satisfiesBundlingRequirements(it, osFamily = null, arch = null, context) }
      span.setAttribute("satisfiableCount", pluginsToBundle.size.toLong())

      // doesn't make sense to require passing here a list with a stable order (unnecessary complication, sorting by main module is enough)
      pluginsToBundle.sortWith(PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE)
      val targetDir = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY)
      val entries = buildPlugins(
        moduleOutputPatcher = moduleOutputPatcher,
        plugins = pluginsToBundle,
        targetDir = targetDir,
        state = state,
        context = context,
        buildPlatformJob = buildPlatformJob,
        os = null,
        searchableOptionSet = searchableOptionSet,
      )

      buildPlatformSpecificPluginResources(
        plugins = pluginsToBundle.filter { it.platformResourceGenerators.isNotEmpty() },
        targetDirs = getPluginDirs(context, isUpdateFromSources),
        context = context,
      )

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
    .useWithScope {
      pluginDirs.mapNotNull { (dist, targetDir) ->
        val (os, arch) = dist
        if (!context.shouldBuildDistributionForOS(os = os, arch = arch)) {
          return@mapNotNull null
        }

        val osSpecificPlugins = plugins.filter {
          satisfiesBundlingRequirements(plugin = it, osFamily = os, arch = arch, context = context)
        }
        if (osSpecificPlugins.isEmpty()) {
          return@mapNotNull null
        }

        dist to async(Dispatchers.IO) {
          spanBuilder("build bundled plugins")
            .setAttribute("os", os.osName)
            .setAttribute("arch", arch.name)
            .setAttribute("count", osSpecificPlugins.size.toLong())
            .setAttribute("outDir", targetDir.toString())
            .useWithScope {
              buildPlugins(
                moduleOutputPatcher = moduleOutputPatcher,
                plugins = osSpecificPlugins,
                targetDir = targetDir,
                state = state,
                context = context,
                os = os,
                buildPlatformJob = buildPlatformJob,
                searchableOptionSet = searchableOptionSet,
              )
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

  return spanBuilder("copy additional plugins").useWithScope(Dispatchers.IO) {
    val allEntries = mutableListOf<Pair<Path, List<Path>>>()

    for (sourceDir in additionalPluginPaths) {
      val targetDir = pluginDir.resolve(sourceDir.fileName)
      copyDir(sourceDir, targetDir)
      val entries = targetDir.resolve(LIB_DIRECTORY).listDirectoryEntries("*.jar")
      check(entries.isNotEmpty()) {
        "Suspicious additional plugin (no 'lib/*.jar' files): ${sourceDir}"
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

    val nonBundledPluginsArtifacts = context.paths.artifactDir.resolve("${context.applicationInfo.productCode}-plugins")
    val autoUploadingDir = nonBundledPluginsArtifacts.resolve("auto-uploading")
    val buildKeymapPluginsTask = async { buildKeymapPlugins(targetDir = autoUploadingDir, context = context) }
    val moduleOutputPatcher = ModuleOutputPatcher()
    val stageDir = context.paths.tempDir.resolve("non-bundled-plugins-${context.applicationInfo.productCode}")
    NioFiles.deleteRecursively(stageDir)
    val dirToJar = ConcurrentLinkedQueue<NonBundledPlugin>()

    // buildPlugins pluginBuilt listener is called concurrently
    val pluginSpecs = ConcurrentLinkedQueue<PluginRepositorySpec>()
    val autoPublishPluginChecker = loadPluginAutoPublishList(context)
    val prepareCustomPluginRepository = context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins &&
                                        !context.isStepSkipped(BuildOptions.ARCHIVE_PLUGINS)
    val mappings = buildPlugins(
      moduleOutputPatcher = moduleOutputPatcher,
      plugins = pluginsToPublish.sortedWith(PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE),
      targetDir = stageDir,
      state = state,
      searchableOptionSet = searchableOptionSet,
      context = context,
      buildPlatformJob = buildPlatformLibJob,
      os = null,
    ) { plugin, pluginDirOrFile ->
      val pluginVersion = if (plugin.mainModule == BUILT_IN_HELP_MODULE_NAME) {
        context.buildNumber
      }
      else {
        plugin.versionEvaluator.evaluate(
          pluginXmlSupplier = { (context as BuildContextImpl).jarPackagerDependencyHelper.getPluginXmlContent(context.findRequiredModule(plugin.mainModule)) },
          ideBuildVersion = context.pluginBuildNumber,
          context = context,
        ).pluginVersion
      }

      val targetDirectory = if (autoPublishPluginChecker.test(plugin)) autoUploadingDir else nonBundledPluginsArtifacts
      val destFile = targetDirectory.resolve("${plugin.directoryName}-$pluginVersion.zip")
      val pluginXml = moduleOutputPatcher.getPatchedPluginXml(plugin.mainModule)
      pluginSpecs.add(PluginRepositorySpec(destFile, pluginXml))
      dirToJar.add(NonBundledPlugin(pluginDirOrFile, destFile, !plugin.enableSymlinksAndExecutableResources))
    }

    archivePlugins(items = dirToJar, compress = compressPluginArchive, withBlockMap = compressPluginArchive, context = context)

    val helpPlugin = buildHelpPlugin(pluginVersion = context.pluginBuildNumber, context = context)
    if (helpPlugin != null) {
      val spec = buildHelpPlugin(
        helpPlugin = helpPlugin,
        pluginsToPublishDir = stageDir,
        targetDir = autoUploadingDir,
        moduleOutputPatcher = moduleOutputPatcher,
        state = state,
        searchableOptionSetDescriptor = searchableOptionSet,
        context = context,
      )
      pluginSpecs.add(spec)
    }

    for (item in buildKeymapPluginsTask.await()) {
      pluginSpecs.add(PluginRepositorySpec(pluginZip = item.first, pluginXml = item.second))
    }

    if (prepareCustomPluginRepository) {
      val list = pluginSpecs.sortedBy { it.pluginZip }
      generatePluginRepositoryMetaFile(list, nonBundledPluginsArtifacts, context)
      generatePluginRepositoryMetaFile(list.filter { it.pluginZip.startsWith(autoUploadingDir) }, autoUploadingDir, context)
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
      launch {
        validatePlugin(path, context, span)
      }
    }
  }
}

private fun validatePlugin(path: Path, context: BuildContext, span: Span) {
  val pluginManager = IdePluginManager.createManager()
  val result = pluginManager.createPlugin(path, validateDescriptor = true)
  // todo fix AddStatisticsEventLogListenerTemporary
  val id = when (result) {
    is PluginCreationSuccess -> result.plugin.pluginId
    is PluginCreationFail -> (pluginManager.createPlugin(path, validateDescriptor = false) as? PluginCreationSuccess)?.plugin?.pluginId
  }
  val problems = context.productProperties.validatePlugin(id, result, context)
  if (problems.isNotEmpty()) {
    span.addEvent("failed", Attributes.of(AttributeKey.stringKey("path"), "$path"))
    context.messages.reportBuildProblem(
      problems.joinToString(
        prefix = "${id ?: path}: ",
        separator = ". ",
      ), identity = "${id ?: path}"
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
  spanBuilder("build help plugin").setAttribute("dir", directory).useWithScope {
    buildPlugins(
      moduleOutputPatcher = moduleOutputPatcher,
      plugins = listOf(helpPlugin),
      targetDir = pluginsToPublishDir.resolve(directory),
      state = state,
      context = context,
      searchableOptionSet = searchableOptionSetDescriptor,
      buildPlatformJob = null,
      os = null,
    )
    zipWithCompression(targetFile = destFile, dirs = mapOf(pluginsToPublishDir.resolve(directory) to ""))
    null
  }
  return PluginRepositorySpec(pluginZip = destFile, pluginXml = moduleOutputPatcher.getPatchedPluginXml(helpPlugin.mainModule))
}

internal suspend fun generateProjectStructureMapping(platformLayout: PlatformLayout, context: BuildContext): ContentReport {
  return coroutineScope {
    val moduleOutputPatcher = ModuleOutputPatcher()
    val libDirLayout = async {
      layoutPlatformDistribution(
        moduleOutputPatcher = moduleOutputPatcher,
        targetDirectory = context.paths.distAllDir,
        platform = platformLayout,
        context = context,
        searchableOptionSet = null,
        copyFiles = false,
      )
    }

    val allPlugins = getPluginLayoutsByJpsModuleNames(
      modules = context.bundledPluginModules,
      productLayout = context.productProperties.productLayout,
    )
    val entries = mutableListOf<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>()
    for (plugin in allPlugins) {
      if (satisfiesBundlingRequirements(plugin = plugin, osFamily = null, arch = null, context = context)) {
        val targetDirectory = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY).resolve(plugin.directoryName)
        entries.add(PluginBuildDescriptor(dir = targetDirectory, layout = plugin, os = null, moduleNames = emptyList()) to layoutDistribution(
          layout = plugin,
          platformLayout = platformLayout,
          targetDirectory = targetDirectory,
          copyFiles = false,
          moduleOutputPatcher = moduleOutputPatcher,
          includedModules = plugin.includedModules,
          searchableOptionSet = null,
          context = context,
        ).first)
      }
    }
    ContentReport(platform = libDirLayout.await(), bundledPlugins = entries, nonBundledPlugins = emptyList())
  }
}

internal suspend fun buildPlugins(
  moduleOutputPatcher: ModuleOutputPatcher,
  plugins: Collection<PluginLayout>,
  os: OsFamily?,
  targetDir: Path,
  state: DistributionBuilderState,
  context: BuildContext,
  buildPlatformJob: Job?,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  pluginBuilt: ((PluginLayout, pluginDirOrFile: Path) -> Unit)? = null,
): List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>> {
  val scrambleTool = context.proprietaryBuildTools.scrambleTool
  val isScramblingSkipped = context.options.buildStepsToSkip.contains(BuildOptions.SCRAMBLING_STEP)

  class ScrambleTask(@JvmField val plugin: PluginLayout, @JvmField val pluginDir: Path, @JvmField val targetDir: Path)

  val scrambleTasks = mutableListOf<ScrambleTask>()

  val entries = coroutineScope {
    plugins.map { plugin ->
      if (plugin.mainModule != BUILT_IN_HELP_MODULE_NAME) {
        checkOutputOfPluginModules(mainPluginModule = plugin.mainModule, includedModules = plugin.includedModules, moduleExcludes = plugin.moduleExcludes, context = context)
        patchPluginXml(
          moduleOutputPatcher = moduleOutputPatcher,
          plugin = plugin,
          releaseDate = context.applicationInfo.majorReleaseDate,
          releaseVersion = context.applicationInfo.releaseVersionForLicensing,
          pluginsToPublish = state.pluginsToPublish,
          helper = (context as BuildContextImpl).jarPackagerDependencyHelper,
          platformLayout = state.platform,
          context = context,
        )
      }

      val directoryName = plugin.directoryName
      val pluginDir = targetDir.resolve(directoryName)
      val task = async(CoroutineName("Build plugin (module=${plugin.mainModule})")) {
        spanBuilder("plugin").setAttribute("path", context.paths.buildOutputDir.relativize(pluginDir).toString()).useWithScope {
          val (entries, file) = layoutDistribution(
            layout = plugin,
            platformLayout = state.platform,
            targetDirectory = pluginDir,
            copyFiles = true,
            moduleOutputPatcher = moduleOutputPatcher,
            includedModules = plugin.includedModules,
            searchableOptionSet = searchableOptionSet,
            context = context,
          )
          pluginBuilt?.invoke(plugin, file)
          entries
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

      PluginBuildDescriptor(dir = pluginDir, layout = plugin, os = os, moduleNames = emptyList()) to task.await()
    }
  }

  if (scrambleTasks.isNotEmpty()) {
    checkNotNull(scrambleTool)

    // scrambling can require classes from the platform
    buildPlatformJob?.let { task ->
      spanBuilder("wait for platform lib for scrambling").useWithScope { task.join() }
    }
    coroutineScope {
      for (scrambleTask in scrambleTasks) {
        launch {
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

private suspend fun buildPlatformSpecificPluginResources(
  plugins: Collection<PluginLayout>,
  targetDirs: List<Pair<SupportedDistribution, Path>>,
  context: BuildContext,
) {
  plugins.asSequence()
    .flatMap { plugin ->
      plugin.platformResourceGenerators.entries.flatMap { (dist, generators) ->
        generators.map { generator -> Triple(dist, generator, plugin.directoryName) }
      }
    }
    .mapNotNull {
      (dist, generator, dirName) -> targetDirs.firstOrNull { it.first == dist }?.let { path -> generator to path.second.resolve(dirName) }
    }
    .forEach { (generator, pluginDir) ->
      spanBuilder("plugin")
        .setAttribute("path", context.paths.buildOutputDir.relativize(pluginDir).toString())
        .useWithScope {
          generator(pluginDir, context)
        }
    }
}

private const val PLUGINS_DIRECTORY = "plugins"
private const val LIB_DIRECTORY = "lib"

const val PLUGIN_CLASSPATH: String = "$PLUGINS_DIRECTORY/plugin-classpath.txt"

private val PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE: Comparator<PluginLayout> = compareBy { it.mainModule }

internal class PluginRepositorySpec(@JvmField val pluginZip: Path, @JvmField val pluginXml: ByteArray /* content of plugin.xml */)

fun getPluginLayoutsByJpsModuleNames(modules: Collection<String>, productLayout: ProductModulesLayout): MutableSet<PluginLayout> {
  if (modules.isEmpty()) {
    return createPluginLayoutSet(expectedSize = 0)
  }

  val pluginLayouts = productLayout.pluginLayouts
  val pluginLayoutsByMainModule = pluginLayouts.groupByTo(HashMap()) { it.mainModule }
  val result = createPluginLayoutSet(modules.size)
  for (moduleName in modules) {
    val customLayouts = pluginLayoutsByMainModule.get(moduleName)
    if (customLayouts == null) {
      check(moduleName == "kotlin-ultimate.kmm-plugin" || result.add(PluginLayout.pluginAuto(listOf(moduleName)))) {
        "Plugin layout for module $moduleName is already added (duplicated module name?)"
      }
    }
    else {
      for (layout in customLayouts) {
        check(layout.mainModule == "kotlin-ultimate.kmm-plugin" || result.add(layout)) {
          "Plugin layout for module $moduleName is already added (duplicated module name?)"
        }
      }
    }
  }
  return result
}

private fun basePath(buildContext: BuildContext, moduleName: String): Path {
  return Path.of(JpsPathUtil.urlToPath(buildContext.findRequiredModule(moduleName).contentRootsList.urls.first()))
}

suspend fun buildLib(
  moduleOutputPatcher: ModuleOutputPatcher,
  platform: PlatformLayout,
  searchableOptionSetDescriptor: SearchableOptionSetDescriptor?,
  context: BuildContext,
): List<DistributionFileEntry> {
  val libDirMappings = layoutPlatformDistribution(
    moduleOutputPatcher = moduleOutputPatcher,
    targetDirectory = context.paths.distAllDir,
    platform = platform,
    copyFiles = true,
    searchableOptionSet = searchableOptionSetDescriptor,
    context = context,
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
      createStatisticsRecorderBundledMetadataProviderTask(moduleOutputPatcher = moduleOutputPatcher, context = context)
      launch {
        patchKeyMapWithAltClickReassignedToMultipleCarets(moduleOutputPatcher = moduleOutputPatcher, context = context)
      }
      launch {
        spanBuilder("write patched app info").use {
          val moduleOutDir = context.getModuleOutputDir(context.findRequiredModule("intellij.platform.core"))
          val relativePath = "com/intellij/openapi/application/ApplicationNamesInfo.class"
          val result = injectAppInfo(inFile = moduleOutDir.resolve(relativePath), newFieldValue = context.appInfoXml)
          moduleOutputPatcher.patchModuleOutput("intellij.platform.core", relativePath, result)
        }
      }
    }
  }

  return spanBuilder("layout lib")
    .setAttribute("path", targetDirectory.toString())
    .useWithScope {
      layoutDistribution(
        layout = platform,
        platformLayout = platform,
        targetDirectory = targetDirectory,
        copyFiles = copyFiles,
        moduleOutputPatcher = moduleOutputPatcher,
        includedModules = platform.includedModules,
        searchableOptionSet = searchableOptionSet,
        context = context,
      ).first
    }
}

private fun patchKeyMapWithAltClickReassignedToMultipleCarets(moduleOutputPatcher: ModuleOutputPatcher, context: BuildContext) {
  if (!context.productProperties.reassignAltClickToMultipleCarets) {
    return
  }

  val moduleName = "intellij.platform.resources"
  val sourceFile = context.getModuleOutputDir((context.findModule(moduleName))!!).resolve("keymaps/\$default.xml")
  var text = Files.readString(sourceFile)
  text = text.replace("<mouse-shortcut keystroke=\"alt button1\"/>", "<mouse-shortcut keystroke=\"to be alt shift button1\"/>")
  text = text.replace("<mouse-shortcut keystroke=\"alt shift button1\"/>", "<mouse-shortcut keystroke=\"alt button1\"/>")
  text = text.replace("<mouse-shortcut keystroke=\"to be alt shift button1\"/>", "<mouse-shortcut keystroke=\"alt shift button1\"/>")
  moduleOutputPatcher.patchModuleOutput(moduleName, "keymaps/\$default.xml", text)
}

fun getOsAndArchSpecificDistDirectory(osFamily: OsFamily, arch: JvmArchitecture, context: BuildContext): Path {
  return context.paths.buildOutputDir.resolve("dist.${osFamily.distSuffix}.${arch.name}")
}

private fun checkOutputOfPluginModules(mainPluginModule: String, includedModules: Collection<ModuleItem>, moduleExcludes: Map<String, List<String>>, context: BuildContext) {
  for (module in includedModules.asSequence().map { it.moduleName }.distinct()) {
    if (module == "intellij.java.guiForms.rt" ||
        !containsFileInOutput(moduleName = module,
                              filePath = "com/intellij/uiDesigner/core/GridLayoutManager.class",
                              excludes = moduleExcludes[module] ?: emptyList(),
                              context = context)) {
      "Runtime classes of GUI designer must not be packaged to \'$module\' module in \'$mainPluginModule\' plugin, " +
      "because they are included into a platform JAR. Make sure that 'Automatically copy form runtime classes " +
      "to the output directory' is disabled in Settings | Editor | GUI Designer."
    }
  }
}

private fun containsFileInOutput(moduleName: String,
                                 filePath: String,
                                 excludes: Collection<String>,
                                 context: BuildContext): Boolean {
  val moduleOutput = context.getModuleOutputDir(context.findRequiredModule(moduleName))
  if (Files.notExists(moduleOutput.resolve(filePath))) {
    return false
  }

  for (exclude in excludes) {
    if (antToRegex(exclude).matches(FileUtilRt.toSystemIndependentName(filePath))) {
      return false
    }
  }

  return true
}

fun getPluginAutoUploadFile(context: BuildContext): Path? {
  val autoUploadFile = context.paths.communityHomeDir.resolve("../build/plugins-autoupload.txt")
  return when {
    Files.isRegularFile(autoUploadFile) -> autoUploadFile
    // public sources build
    context.paths.projectHome.toUri() == context.paths.communityHomeDir.toUri() -> null
    else -> error("File '$autoUploadFile' must exist")
  }
}

fun readPluginAutoUploadFile(autoUploadFile: Path): Collection<String> {
  return autoUploadFile.useLines { lines ->
    lines
      .map { StringUtil.split(it, "//", true, false)[0] }
      .map { StringUtil.split(it, "#", true, false)[0].trim() }
      .filter { !it.isEmpty() }
      .toCollection(TreeSet(String.CASE_INSENSITIVE_ORDER))
  }
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
      project = context.project,
      licenseList = context.productProperties.allLibraryLicenses,
      usedModulesNames = getIncludedModules(entries).toHashSet(),
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
    val isNightly = context.options.isNightlyBuild
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

/**
 * @see [[build/plugins-autoupload.txt]] for the specification.
 *
 * @return predicate to test if the given plugin should be auto-published
 */
private fun loadPluginAutoPublishList(context: BuildContext): Predicate<PluginLayout> {
  val file = getPluginAutoUploadFile(context) ?: return Predicate<PluginLayout> { false }
  val config = readPluginAutoUploadFile(file)

  val productCode = context.applicationInfo.productCode
  return Predicate<PluginLayout> { plugin ->
    val mainModuleName = plugin.mainModule

    val includeInAllProducts = config.contains(mainModuleName)
    val includeInProduct = config.contains("+$productCode:$mainModuleName")
    val excludedFromProduct = config.contains("-$productCode:$mainModuleName")

    if (includeInProduct && (excludedFromProduct || includeInAllProducts)) {
      context.messages.error("Unsupported rules combination: " + config.filter {
        it == mainModuleName || it.endsWith(":$mainModuleName")
      })
    }

    !excludedFromProduct && (includeInAllProducts || includeInProduct)
  }
}

private suspend fun buildKeymapPlugins(targetDir: Path, context: BuildContext): List<Pair<Path, ByteArray>> {
  val keymapDir = context.paths.communityHomeDir.resolve("platform/platform-resources/src/keymaps")
  Files.createDirectories(targetDir)
  return spanBuilder("build keymap plugins").useWithScope(Dispatchers.IO) {
    listOf(
      arrayOf("Mac OS X", "Mac OS X 10.5+"),
      arrayOf("Default for GNOME"),
      arrayOf("Default for KDE"),
      arrayOf("Default for XWin"),
      arrayOf("Emacs"),
      arrayOf("Sublime Text", "Sublime Text (Mac OS X)"),
    ).map {
      async {
        buildKeymapPlugin(keymaps = it, buildNumber = context.buildNumber, targetDir = targetDir, keymapDir = keymapDir)
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
        launch {
          checkModuleExcludes(layout.moduleExcludes, context)
        }
      }

      // patchers must be executed _before_ pack because patcher patches module output
      val patchers = layout.patchers
      if (!patchers.isEmpty()) {
        spanBuilder("execute custom patchers").setAttribute("count", patchers.size.toLong()).useWithScope {
          for (patcher in patchers) {
            patcher(moduleOutputPatcher, platformLayout, context)
          }
        }
      }
    }
  }

  val entries = coroutineScope {
    val tasks = ArrayList<Deferred<Collection<DistributionFileEntry>>>(3)
    tasks.add(async {
      val outputDir = targetDirectory.resolve(LIB_DIRECTORY)
      spanBuilder("pack").setAttribute("outputDir", outputDir.toString()).useWithScope {
        JarPackager.pack(
          includedModules = includedModules,
          outputDir = outputDir,
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

    if (copyFiles &&
        !context.options.skipCustomResourceGenerators &&
        (layout.resourcePaths.isNotEmpty() || layout is PluginLayout && !layout.resourceGenerators.isEmpty())) {
      tasks.add(async(Dispatchers.IO) {
        spanBuilder("pack additional resources").useWithScope {
          layoutAdditionalResources(layout = layout, context = context, targetDirectory = targetDirectory)
          emptyList()
        }
      })
    }

    if (!layout.includedArtifacts.isEmpty()) {
      tasks.add(async {
        spanBuilder("pack artifacts").useWithScope {
          layoutArtifacts(layout = layout, context = context, copyFiles = copyFiles, targetDirectory = targetDirectory)
        }
      })
    }
    tasks
  }.flatMap { it.getCompleted() }

  return entries to targetDirectory
}

private fun layoutResourcePaths(layout: BaseLayout, context: BuildContext, targetDirectory: Path, overwrite: Boolean) {
  for (resourceData in layout.resourcePaths) {
    val source = basePath(buildContext = context, moduleName = resourceData.moduleName).resolve(resourceData.resourcePath).normalize()
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
          copyDir(sourceDir = source, targetDir = target, fileFilter = Predicate {
            copyIfChanged(target, source, it)
          })
        }
        else {
          copyDir(sourceDir = source, targetDir = target)
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
  layoutResourcePaths(layout = layout, context = context, targetDirectory = targetDirectory, overwrite = overwrite)
  if (layout !is PluginLayout) {
    return
  }

  val resourceGenerators = layout.resourceGenerators
  if (!resourceGenerators.isEmpty()) {
    spanBuilder("generate and pack resources").useWithScope {
      for (item in resourceGenerators) {
        item(targetDirectory, context)
      }
    }
  }
}

@OptIn(ExperimentalPathApi::class)
private suspend fun layoutArtifacts(layout: BaseLayout,
                                    context: BuildContext,
                                    copyFiles: Boolean,
                                    targetDirectory: Path): Collection<DistributionFileEntry> {
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
    addArtifactMapping(artifact = artifact, entries = entries, artifactFile = artifactPath)
  }
  return entries
}

private fun addArtifactMapping(artifact: JpsArtifact, entries: MutableCollection<DistributionFileEntry>, artifactFile: Path) {
  val rootElement = artifact.rootElement
  for (element in rootElement.children) {
    if (element is JpsProductionModuleOutputPackagingElement) {
      entries.add(ModuleOutputEntry(
        path = artifactFile,
        moduleName = element.moduleReference.moduleName,
        size = 0,
        hash = 0,
        relativeOutputFile = "",
        reason = "artifact: ${artifact.name}",
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
          path = artifactFile,
          moduleName = parentReference.moduleName,
          libraryName = getLibraryFilename(library),
          libraryFile = null,
          hash = 0,
          size = 0,
          relativeOutputFile = null,
        ))
      }
      else {
        val libraryData = ProjectLibraryData(library.name, LibraryPackMode.MERGED, reason = "<- artifact ${artifact.name}")
        entries.add(ProjectLibraryEntry(
          path = artifactFile,
          data = libraryData,
          libraryFile = null,
          hash = 0,
          size = 0,
          relativeOutputFile = null,
        ))
      }
    }
  }
}

private fun checkModuleExcludes(moduleExcludes: Map<String, List<String>>, context: CompilationContext) {
  for (module in moduleExcludes.keys) {
    check(Files.exists(context.getModuleOutputDir(context.findRequiredModule(module)))) {
      "There are excludes defined for module '${module}', but the module wasn't compiled;" +
      " most probably it means that '${module}' isn't included into the product distribution," +
      " so it doesn't make sense to define excludes for it."
    }
  }
}

private data class NonBundledPlugin(@JvmField val sourceDir: Path, @JvmField val targetZip: Path, @JvmField val optimizedZip: Boolean)

private suspend fun archivePlugins(items: Collection<NonBundledPlugin>, compress: Boolean, withBlockMap: Boolean, context: BuildContext) {
  context.executeStep(
    spanBuilder = spanBuilder("archive plugins").setAttribute(AttributeKey.longKey("count"), items.size.toLong()),
    stepId = BuildOptions.ARCHIVE_PLUGINS
  ) {
    val json by lazy { JSON.std.without(JSON.Feature.USE_FIELDS) }
    for ((source, target, optimized) in items) {
      launch {
        spanBuilder("archive plugin")
          .setAttribute("input", source.toString())
          .setAttribute("outputFile", target.toString())
          .setAttribute("optimizedZip", optimized)
          .useWithScope {
            archivePlugin(optimized = optimized, target = target, compress = compress, source = source, context = context)
          }
        if (withBlockMap) {
          spanBuilder("build plugin blockmap").setAttribute("file", target.toString()).useWithScope {
            buildBlockMap(target, json)
          }
        }
      }
    }
  }
}

private fun archivePlugin(optimized: Boolean, target: Path, compress: Boolean, source: Path, context: BuildContext) {
  if (optimized) {
    writeNewZipWithoutIndex(target, compress = compress) { zipCreator ->
      ZipArchiver(zipCreator).use { archiver ->
        if (Files.isDirectory(source)) {
          archiver.setRootDir(source, source.fileName.toString())
          archiveDir(startDir = source, archiver = archiver, excludes = null, indexWriter = null)
        }
        else {
          archiver.setRootDir(source.parent)
          archiver.addFile(source, indexWriter = null)
        }
      }
    }
  }
  else {
    writeNewFile(target) { outFileChannel ->
      NoDuplicateZipArchiveOutputStream(outFileChannel, compress = context.options.compressZipFiles).use { out ->
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
 * Builds a blockmap and hash files for plugin to provide downloading plugins via incremental downloading algorithm Blockmap.
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

suspend fun createIdeClassPath(platform: PlatformLayout, context: BuildContext): Collection<String> {
  val contentReport = generateProjectStructureMapping(context = context, platformLayout = platform)

  val pluginLayouts = context.productProperties.productLayout.pluginLayouts
  val classPath = LinkedHashSet<Path>()

  val libDir = context.paths.distAllDir.resolve("lib")
  for (entry in contentReport.platform) {
    if (!(entry is ModuleOutputEntry && entry.reason == ModuleIncludeReasons.PRODUCT_MODULES)) {
      val relativePath = libDir.relativize(entry.path)
      if (relativePath.nameCount != 1 && !relativePath.startsWith("modules")) {
        continue
      }
    }

    when (entry) {
      is ModuleOutputEntry -> {
        classPath.add(context.getModuleOutputDir(context.findRequiredModule(entry.moduleName)))
      }
      is LibraryFileEntry -> classPath.add(entry.libraryFile!!)
      else -> throw UnsupportedOperationException("Entry $entry is not supported")
    }
  }

  val pluginDir = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY)
  for (entry in contentReport.bundledPlugins.flatMap { it.second }) {
    val relativePath = pluginDir.relativize(entry.path)
    // for plugins, our classloader load jars only from lib folder
    if (relativePath.nameCount != 3 || relativePath.getName(1).toString() != LIB_DIRECTORY) {
      continue
    }

    when (entry) {
      is ModuleOutputEntry -> {
        classPath.add(context.getModuleOutputDir(context.findRequiredModule(entry.moduleName)))
        for (classpathPluginEntry in pluginLayouts.firstOrNull { it.mainModule == entry.moduleName }?.scrambleClasspathPlugins ?: emptyList()) {
          context.getModuleOutputDir(context.findRequiredModule(classpathPluginEntry.pluginMainModuleName)).toString()
        }
      }
      is LibraryFileEntry -> classPath.add(entry.libraryFile!!)
      is CustomAssetEntry -> {
      }
      else -> throw UnsupportedOperationException("Entry $entry is not supported")
    }
  }
  return classPath.map { it.toString() }
}
