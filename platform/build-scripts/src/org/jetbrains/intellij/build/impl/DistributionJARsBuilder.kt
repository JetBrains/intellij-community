// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.fasterxml.jackson.jr.ob.JSON
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScopeBlocking
import com.intellij.util.io.Compressor
import com.jetbrains.plugin.blockmap.core.BlockMap
import com.jetbrains.plugin.blockmap.core.FileHash
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
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
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Predicate
import kotlin.io.path.useLines

/**
 * Assembles output of modules to platform JARs (in [BuildPaths.distAllDir]/lib directory),
 * bundled plugins' JARs (in [distAll][BuildPaths.distAllDir]/plugins directory) and zip archives with
 * non-bundled plugins (in [artifacts][BuildPaths.artifactDir]/plugins directory).
 */
internal suspend fun buildDistribution(state: DistributionBuilderState,
                                       context: BuildContext,
                                       isUpdateFromSources: Boolean = false): List<DistributionFileEntry> = coroutineScope {
  validateModuleStructure(state.platform, context)
  context.productProperties.validateLayout(state.platform, context)
  createBuildBrokenPluginListJob(context)

  val flatIdeClassPath = createIdeClassPath(state.platform, context)
  if (context.productProperties.buildDocAuthoringAssets) {
    launch {
      buildAdditionalAuthoringArtifacts(ideClassPath = flatIdeClassPath, context = context)
    }
  }

  val traceContext = Context.current().asContextElement()
  val entries = coroutineScope {
    // must be completed before plugin building
    context.executeStep(spanBuilder("build searchable options index"), BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP) {
      buildSearchableOptions(ideClassPath = flatIdeClassPath, context = context)
    }

    val pluginLayouts = getPluginLayoutsByJpsModuleNames(modules = context.productProperties.productLayout.bundledPluginModules,
                                                         productLayout = context.productProperties.productLayout)
    val antDir = if (context.productProperties.isAntRequired) context.paths.distAllDir.resolve("lib/ant") else null
    val antTargetFile = antDir?.resolve("lib/ant.jar")
    val moduleOutputPatcher = ModuleOutputPatcher()
    val buildPlatformJob: Deferred<List<DistributionFileEntry>> = async(traceContext) {
      spanBuilder("build platform lib").useWithScope {
        val result = buildLib(moduleOutputPatcher = moduleOutputPatcher, platform = state.platform, context = context)
        if (!isUpdateFromSources && context.productProperties.scrambleMainJar) {
          scramble(state.platform, context)
        }

        val distAllDir = context.paths.distAllDir
        val libDir = distAllDir.resolve("lib")
        context.bootClassPathJarNames = if (context.useModularLoader) {
          persistentListOf(PLATFORM_LOADER_JAR)
        }
        else {
          generateClasspath(homeDir = distAllDir, libDir = libDir, antTargetFile = antTargetFile)
        }
        result
      }
    }

    listOfNotNull(
      buildPlatformJob,
      async {
        buildBundledPlugins(state, pluginLayouts, isUpdateFromSources, buildPlatformJob, context)
      },
      async {
        buildOsSpecificBundledPlugins(state, pluginLayouts, isUpdateFromSources, buildPlatformJob, context)
      },
      async {
        val compressPluginArchive = !isUpdateFromSources && context.options.compressZipFiles
        buildNonBundledPlugins(state.pluginsToPublish, compressPluginArchive, buildPlatformJob, state, context)
      },
      if (antDir == null) null else async(Dispatchers.IO) { copyAnt(antDir, antTargetFile!!, context) }
    )
  }.flatMap { it.getCompleted() }

  // must be before reorderJars as these additional plugins maybe required for IDE start-up
  val additionalPluginPaths = context.productProperties.getAdditionalPluginPaths(context)
  if (!additionalPluginPaths.isEmpty()) {
    val pluginDir = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY)
    withContext(Dispatchers.IO) {
      for (sourceDir in additionalPluginPaths) {
        copyDir(sourceDir = sourceDir, targetDir = pluginDir.resolve(sourceDir.fileName))
      }
    }
  }

  coroutineScope {
    launch(Dispatchers.IO) {
      spanBuilder("generate content report").useWithScope {
        Files.createDirectories(context.paths.artifactDir)
        val contentMappingJson = context.paths.artifactDir.resolve("content-mapping.json")
        writeProjectStructureReport(entries = entries, file = contentMappingJson, buildPaths = context.paths)
        val contentJson = context.paths.artifactDir.resolve("content.json")
        Files.newOutputStream(contentJson).use {
          buildJarContentReport(entries = entries, out = it, buildPaths = context.paths, context = context)
        }
        context.notifyArtifactBuilt(contentMappingJson)
        context.notifyArtifactBuilt(contentJson)
      }
    }
    createBuildThirdPartyLibraryListJob(entries, context)
    if (context.useModularLoader || context.generateRuntimeModuleRepository) {
      launch(Dispatchers.IO) {
        spanBuilder("generate runtime module repository").useWithScope {
          generateRuntimeModuleRepository(entries, context)
        }
      }
    }
  }
  entries
}

/**
 * Validates module structure to be ensure all module dependencies are included.
 */
fun validateModuleStructure(platform: PlatformLayout, context: BuildContext) {
  if (context.options.validateModuleStructure) {
    ModuleStructureValidator(context, platform.includedModules).validate()
  }
}

fun getProductModules(state: DistributionBuilderState): List<String> {
  return state.platform.includedModules.asSequence()
    .filter {
      !it.relativeOutputFile.contains('\\') && !it.relativeOutputFile.contains('/')
    }  // filter out jars with relative paths in the name
    .map { it.moduleName }
    .distinct()
    .toList()
}

private fun getPluginDirectories(context: BuildContext, isUpdateFromSources: Boolean): Map<SupportedDistribution, Path> {
  return if (isUpdateFromSources) {
    mapOf(SupportedDistribution(OsFamily.currentOs, JvmArchitecture.currentJvmArch) to context.paths.distAllDir.resolve(PLUGINS_DIRECTORY))
  }
  else {
    SUPPORTED_DISTRIBUTIONS.associateWith { getOsAndArchSpecificDistDirectory(it.os, it.arch, context).resolve(PLUGINS_DIRECTORY) }
  }
}

suspend fun buildBundledPlugins(state: DistributionBuilderState,
                                plugins: Collection<PluginLayout>,
                                isUpdateFromSources: Boolean,
                                buildPlatformJob: Job?,
                                context: BuildContext): List<DistributionFileEntry> {
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
      val entries = buildPlugins(moduleOutputPatcher = ModuleOutputPatcher(),
                                 plugins = pluginsToBundle,
                                 targetDir = targetDir,
                                 state = state,
                                 context = context,
                                 buildPlatformJob = buildPlatformJob)

      buildPlatformSpecificPluginResources(
        pluginsToBundle.filter { it.platformResourceGenerators.isNotEmpty() },
        getPluginDirectories(context, isUpdateFromSources),
        context)

      entries
    }
}

private suspend fun buildOsSpecificBundledPlugins(state: DistributionBuilderState,
                                                  plugins: Set<PluginLayout>,
                                                  isUpdateFromSources: Boolean,
                                                  buildPlatformJob: Job?,
                                                  context: BuildContext): List<DistributionFileEntry> {
  return spanBuilder("build os-specific bundled plugins")
    .setAttribute("isUpdateFromSources", isUpdateFromSources)
    .setAttribute(AttributeKey.stringArrayKey("pluginDirectoriesToSkip"), context.options.bundledPluginDirectoriesToSkip.toList())
    .useWithScope {
      coroutineScope {
        getPluginDirectories(context, isUpdateFromSources).mapNotNull { (dist, targetDir) ->
          val (os, arch) = dist
          if (!context.shouldBuildDistributionForOS(os, arch)) {
            return@mapNotNull null
          }

          val osSpecificPlugins = plugins.filter { satisfiesBundlingRequirements(it, os, arch, context) }
          if (osSpecificPlugins.isEmpty()) {
            return@mapNotNull null
          }

          async(Dispatchers.IO) {
            spanBuilder("build bundled plugins")
              .setAttribute("os", os.osName)
              .setAttribute("arch", arch.name)
              .setAttribute("count", osSpecificPlugins.size.toLong())
              .setAttribute("outDir", targetDir.toString())
              .useWithScope {
                buildPlugins(moduleOutputPatcher = ModuleOutputPatcher(),
                             plugins = osSpecificPlugins,
                             targetDir = targetDir,
                             state = state,
                             context = context,
                             buildPlatformJob = buildPlatformJob)
              }
          }
        }
      }
    }.flatMap { it.getCompleted() }
}

suspend fun buildNonBundledPlugins(pluginsToPublish: Set<PluginLayout>,
                                   compressPluginArchive: Boolean,
                                   buildPlatformLibJob: Job?,
                                   state: DistributionBuilderState,
                                   context: BuildContext): List<DistributionFileEntry> {
  return spanBuilder("build non-bundled plugins").setAttribute("count", pluginsToPublish.size.toLong()).useWithScope { span ->
    if (pluginsToPublish.isEmpty()) {
      return@useWithScope emptyList<DistributionFileEntry>()
    }
    if (context.isStepSkipped(BuildOptions.NON_BUNDLED_PLUGINS_STEP)) {
      span.addEvent("skip")
      return@useWithScope emptyList<DistributionFileEntry>()
    }

    val nonBundledPluginsArtifacts = context.paths.artifactDir.resolve("${context.applicationInfo.productCode}-plugins")
    val autoUploadingDir = nonBundledPluginsArtifacts.resolve("auto-uploading")
    coroutineScope {
      val buildKeymapPluginsTask = async { buildKeymapPlugins(autoUploadingDir, context) }
      val moduleOutputPatcher = ModuleOutputPatcher()
      val stageDir = context.paths.tempDir.resolve("non-bundled-plugins-${context.applicationInfo.productCode}")
      NioFiles.deleteRecursively(stageDir)
      val dirToJar = ConcurrentLinkedQueue<NonBundledPlugin>()
      val defaultPluginVersion = if (context.buildNumber.endsWith(".SNAPSHOT")) {
        "${context.buildNumber}.${pluginDateFormat.format(ZonedDateTime.now())}"
      }
      else {
        context.buildNumber
      }

      // buildPlugins pluginBuilt listener is called concurrently
      val pluginSpecs = ConcurrentLinkedQueue<PluginRepositorySpec>()
      val autoPublishPluginChecker = loadPluginAutoPublishList(context)
      val prepareCustomPluginRepository = context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins &&
                                          !context.isStepSkipped(BuildOptions.ARCHIVE_PLUGINS)
      // we don't simplify the layout for non-bundled plugins, because PluginInstaller not ready for this (see rootEntryName)
      val mappings = buildPlugins(moduleOutputPatcher = moduleOutputPatcher,
                                  plugins = pluginsToPublish.sortedWith(PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE),
                                  targetDir = stageDir,
                                  state = state,
                                  context = context,
                                  buildPlatformJob = buildPlatformLibJob) { plugin, pluginDirOrFile ->
        val targetDirectory = if (autoPublishPluginChecker.test(plugin)) autoUploadingDir else nonBundledPluginsArtifacts
        val moduleOutput = context.getModuleOutputDir(context.findRequiredModule(plugin.mainModule))
        val pluginXmlPath = moduleOutput.resolve("META-INF/plugin.xml")
        val pluginVersion = if (Files.exists(pluginXmlPath)) {
          plugin.versionEvaluator.evaluate(pluginXmlPath, defaultPluginVersion, context)
        }
        else {
          defaultPluginVersion
        }
        val destFile = targetDirectory.resolve("${plugin.directoryName}-$pluginVersion.zip")
        val pluginXml = moduleOutputPatcher.getPatchedPluginXml(plugin.mainModule)
        pluginSpecs.add(PluginRepositorySpec(destFile, pluginXml))
        dirToJar.add(NonBundledPlugin(pluginDirOrFile, destFile, !plugin.enableSymlinksAndExecutableResources))
      }

      archivePlugins(items = dirToJar, compress = compressPluginArchive, withBlockMap = compressPluginArchive, context = context)

      val helpPlugin = buildHelpPlugin(pluginVersion = defaultPluginVersion, context = context)
      if (helpPlugin != null) {
        val spec = buildHelpPlugin(helpPlugin = helpPlugin,
                                   pluginsToPublishDir = stageDir,
                                   targetDir = autoUploadingDir,
                                   moduleOutputPatcher = moduleOutputPatcher,
                                   state = state,
                                   context = context)
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
      pluginSpecs.forEach {
        launch {
          validatePlugin(it.pluginZip, context)
        }
      }
      mappings
    }
  }
}

private suspend fun validatePlugin(path: Path, context: BuildContext) {
  context.executeStep(spanBuilder("plugin validation").setAttribute("path", "$path"), BuildOptions.VALIDATE_PLUGINS_TO_BE_PUBLISHED) { span ->
    if (Files.notExists(path)) {
      span.addEvent("path doesn't exist, skipped")
      return@executeStep
    }

    val pluginManager = IdePluginManager.createManager()
    val id = (pluginManager.createPlugin(path, validateDescriptor = false)
      as? PluginCreationSuccess)
      ?.plugin?.pluginId
    val result = pluginManager.createPlugin(path, validateDescriptor = true)
    val problems = context.productProperties.validatePlugin(result, context)
    if (problems.isNotEmpty()) {
      context.messages.reportBuildProblem(problems.joinToString(
        prefix = "${id ?: path}: ",
        separator = ". ",
      ), identity = "${id ?: path}")
    }
  }
}

private suspend fun buildHelpPlugin(helpPlugin: PluginLayout,
                                    pluginsToPublishDir: Path,
                                    targetDir: Path,
                                    moduleOutputPatcher: ModuleOutputPatcher,
                                    state: DistributionBuilderState,
                                    context: BuildContext): PluginRepositorySpec {
  val directory = helpPlugin.directoryName
  val destFile = targetDir.resolve("$directory.zip")
  spanBuilder("build help plugin").setAttribute("dir", directory).useWithScope {
    buildPlugins(moduleOutputPatcher = moduleOutputPatcher,
                 plugins = listOf(helpPlugin),
                 targetDir = pluginsToPublishDir.resolve(directory),
                 state = state,
                 context = context,
                 buildPlatformJob = null)
    zipWithCompression(targetFile = destFile, dirs = mapOf(pluginsToPublishDir.resolve(directory) to ""))
    null
  }
  return PluginRepositorySpec(destFile, moduleOutputPatcher.getPatchedPluginXml(helpPlugin.mainModule))
}

internal suspend fun generateProjectStructureMapping(
  context: BuildContext,
  platformLayout: PlatformLayout,
): Pair<List<DistributionFileEntry>, List<DistributionFileEntry>> {
  return coroutineScope {
    val moduleOutputPatcher = ModuleOutputPatcher()
    val libDirLayout = async {
      layoutPlatformDistribution(moduleOutputPatcher = moduleOutputPatcher,
                                 targetDirectory = context.paths.distAllDir,
                                 platform = platformLayout,
                                 context = context,
                                 copyFiles = false)
    }

    val allPlugins = getPluginLayoutsByJpsModuleNames(modules = context.productProperties.productLayout.bundledPluginModules,
                                                      productLayout = context.productProperties.productLayout)
    val entries = mutableListOf<DistributionFileEntry>()
    for (plugin in allPlugins) {
      if (satisfiesBundlingRequirements(plugin = plugin, osFamily = null, arch = null, context = context)) {
        val targetDirectory = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY).resolve(plugin.directoryName)
        entries.addAll(layoutDistribution(layout = plugin,
                                          platformLayout = platformLayout,
                                          targetDirectory = targetDirectory,
                                          copyFiles = false,
                                          moduleOutputPatcher = moduleOutputPatcher,
                                          includedModules = plugin.includedModules,
                                          moduleWithSearchableOptions = emptySet(),
                                          context = context).first)
      }
    }
    libDirLayout.await() to entries
  }
}

private suspend fun buildPlugins(moduleOutputPatcher: ModuleOutputPatcher,
                                 plugins: Collection<PluginLayout>,
                                 targetDir: Path,
                                 state: DistributionBuilderState,
                                 context: BuildContext,
                                 buildPlatformJob: Job?,
                                 pluginBuilt: ((PluginLayout, pluginDirOrFile: Path) -> Unit)? = null): List<DistributionFileEntry> {
  val scrambleTool = context.proprietaryBuildTools.scrambleTool
  val isScramblingSkipped = context.options.buildStepsToSkip.contains(BuildOptions.SCRAMBLING_STEP)

  class ScrambleTask(@JvmField val plugin: PluginLayout, @JvmField val pluginDir: Path, @JvmField val targetDir: Path)

  val scrambleTasks = mutableListOf<ScrambleTask>()

  val moduleWithSearchableOptions = getModuleWithSearchableOptions(context)
  val entries: List<DistributionFileEntry> = coroutineScope {
    plugins.map { plugin ->
      if (plugin.mainModule != "intellij.platform.builtInHelp") {
        checkOutputOfPluginModules(mainPluginModule = plugin.mainModule,
                                   includedModules = plugin.includedModules,
                                   moduleExcludes = plugin.moduleExcludes,
                                   context = context)
        patchPluginXml(moduleOutputPatcher = moduleOutputPatcher,
                       plugin = plugin,
                       releaseDate = context.applicationInfo.majorReleaseDate,
                       releaseVersion = context.applicationInfo.releaseVersionForLicensing,
                       pluginsToPublish = state.pluginsToPublish,
                       context = context)
      }

      val directoryName = plugin.directoryName
      val pluginDir = targetDir.resolve(directoryName)
      val task = async {
        spanBuilder("plugin").setAttribute("path", context.paths.buildOutputDir.relativize(pluginDir).toString()).useWithScope {
          val (entries, file) = layoutDistribution(layout = plugin,
                                                   platformLayout = state.platform,
                                                   targetDirectory = pluginDir,
                                                   copyFiles = true,
                                                   moduleOutputPatcher = moduleOutputPatcher,
                                                   includedModules = plugin.includedModules,
                                                   moduleWithSearchableOptions = moduleWithSearchableOptions,
                                                   context = context)
          pluginBuilt?.invoke(plugin, file)
          entries
        }
      }

      if (!plugin.pathsToScramble.isEmpty()) {
        val attributes = Attributes.of(AttributeKey.stringKey("plugin"), directoryName)
        if (scrambleTool == null) {
          Span.current().addEvent("skip scrambling plugin because scrambleTool isn't defined, but plugin defines paths to be scrambled",
                                  attributes)
        }
        else if (isScramblingSkipped) {
          Span.current().addEvent("skip scrambling plugin because step is disabled", attributes)
        }
        else {
          // we cannot start executing right now because the plugin can use other plugins in a scramble classpath
          scrambleTasks.add(ScrambleTask(plugin, pluginDir, targetDir))
        }
      }

      task
    }
  }.flatMap { it.getCompleted() }

  if (scrambleTasks.isNotEmpty()) {
    checkNotNull(scrambleTool)

    // scrambling can require classes from the platform
    buildPlatformJob?.let { task ->
      spanBuilder("wait for platform lib for scrambling").useWithScope { task.join() }
    }
    coroutineScope {
      for (scrambleTask in scrambleTasks) {
        launch {
          scrambleTool.scramblePlugin(context = context,
                                      pluginLayout = scrambleTask.plugin,
                                      targetDir = scrambleTask.pluginDir,
                                      additionalPluginsDir = scrambleTask.targetDir)
        }
      }
    }
  }
  return entries
}

private suspend fun getModuleWithSearchableOptions(context: BuildContext): Set<String> {
  return withContext(Dispatchers.IO) {
    try {
      val result = HashSet<String>()
      val dir = context.paths.searchableOptionDir
      Files.newDirectoryStream(dir).use { stream ->
        for (file in stream) {
          result.add(file.fileName.toString())
        }
      }
      result
    }
    catch (e: IOException) {
      emptySet()
    }
  }
}

private suspend fun buildPlatformSpecificPluginResources(plugins: Collection<PluginLayout>,
                                                         targetDirs: Map<SupportedDistribution, Path>,
                                                         context: BuildContext) {
  plugins.asSequence()
    .flatMap { plugin -> 
      plugin.platformResourceGenerators.entries.flatMap { (dist, generators) -> 
        generators.map { generator -> Triple(dist, generator, plugin.directoryName) }
      }
    }
    .mapNotNull { (dist, generator, dirName) -> targetDirs[dist]?.let { path -> generator to path.resolve(dirName) } }
    .forEach { (generator, pluginDir) ->
      spanBuilder("plugin")
        .setAttribute("path", context.paths.buildOutputDir.relativize(pluginDir).toString())
        .useWithScope {
          generator(pluginDir, context)
        }
    }
}

private const val PLUGINS_DIRECTORY = "plugins"
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

suspend fun buildLib(moduleOutputPatcher: ModuleOutputPatcher,
                     platform: PlatformLayout,
                     context: BuildContext): List<DistributionFileEntry> {
  val libDirMappings = layoutPlatformDistribution(moduleOutputPatcher = moduleOutputPatcher,
                                                  targetDirectory = context.paths.distAllDir,
                                                  platform = platform,
                                                  context = context,
                                                  copyFiles = true)
  context.proprietaryBuildTools.scrambleTool?.validatePlatformLayout(platform.includedModules, context)
  return libDirMappings
}

suspend fun layoutPlatformDistribution(moduleOutputPatcher: ModuleOutputPatcher,
                                       targetDirectory: Path,
                                       platform: PlatformLayout,
                                       context: BuildContext,
                                       copyFiles: Boolean): List<DistributionFileEntry> {
  if (copyFiles) {
    coroutineScope {
      createStatisticsRecorderBundledMetadataProviderTask(moduleOutputPatcher = moduleOutputPatcher, context = context)
      launch {
        patchKeyMapWithAltClickReassignedToMultipleCarets(moduleOutputPatcher = moduleOutputPatcher, context = context)
      }
      launch {
        spanBuilder("write patched app info").useWithScopeBlocking {
          val moduleOutDir = context.getModuleOutputDir(context.findRequiredModule("intellij.platform.core"))
          val relativePath = "com/intellij/openapi/application/ApplicationNamesInfo.class"
          val result = injectAppInfo(inFile = moduleOutDir.resolve(relativePath), newFieldValue = context.applicationInfo.appInfoXml)
          moduleOutputPatcher.patchModuleOutput("intellij.platform.core", relativePath, result)
        }
      }
    }
  }

  return spanBuilder("layout lib")
    .setAttribute("path", targetDirectory.toString())
    .useWithScope {
      layoutDistribution(layout = platform,
                         platformLayout = platform,
                         targetDirectory = targetDirectory,
                         copyFiles = copyFiles,
                         moduleOutputPatcher = moduleOutputPatcher,
                         includedModules = platform.includedModules,
                         moduleWithSearchableOptions = if (copyFiles) getModuleWithSearchableOptions(context) else emptySet(),
                         context = context).first
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

@VisibleForTesting
fun getOsAndArchSpecificDistDirectory(osFamily: OsFamily, arch: JvmArchitecture, context: BuildContext): Path {
  return context.paths.buildOutputDir.resolve("dist.${osFamily.distSuffix}.${arch.name}")
}

fun checkOutputOfPluginModules(mainPluginModule: String,
                               includedModules: Collection<ModuleItem>,
                               moduleExcludes: Map<String, List<String>>,
                               context: BuildContext) {
  // don't check modules which are not direct children of lib/ directory
  val modulesWithPluginXml = mutableListOf<String>()
  for (item in includedModules) {
    if (!item.relativeOutputFile.contains('/')) {
      val moduleName = item.moduleName
      if (containsFileInOutput(moduleName = moduleName,
                               filePath = "META-INF/plugin.xml",
                               excludes = moduleExcludes[moduleName] ?: emptyList(),
                               context = context)) {
        modulesWithPluginXml.add(moduleName)
      }
    }
  }

  check(!modulesWithPluginXml.isEmpty()) {
    "No module from \'$mainPluginModule\' plugin contains plugin.xml"
  }
  check(modulesWithPluginXml.size == 1) {
    "Multiple modules (${modulesWithPluginXml.joinToString()}) from \'$mainPluginModule\' plugin " +
    "contain plugin.xml files so the plugin won\'t work properly"
  }
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
  val fileInOutput = moduleOutput.resolve(filePath)
  if (Files.notExists(fileInOutput)) {
    return false
  }

  val set = FileSet(moduleOutput).include(filePath)
  excludes.forEach(set::exclude)
  return !set.isEmpty()
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

fun readPluginAutoUploadFile(autoUploadFile: Path): Collection<String> =
  autoUploadFile.useLines { lines ->
    lines
      .map { StringUtil.split(it, "//", true, false)[0] }
      .map { StringUtil.split(it, "#", true, false)[0].trim() }
      .filter { !it.isEmpty() }
      .toCollection(TreeSet(String.CASE_INSENSITIVE_ORDER))
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

private suspend fun copyAnt(antDir: Path, antTargetFile: Path, context: BuildContext): List<DistributionFileEntry> {
  return spanBuilder("copy Ant lib").setAttribute("antDir", antDir.toString()).useWithScope {
    val sources = ArrayList<ZipSource>()
    val libraryData = ProjectLibraryData("Ant", LibraryPackMode.MERGED, reason = "ant")
    copyDir(sourceDir = context.paths.communityHomeDir.resolve("lib/ant"),
            targetDir = antDir,
            dirFilter = { !it.endsWith("src") },
            fileFilter = { file ->
              if (file.toString().endsWith(".jar")) {
                sources.add(ZipSource(file = file, distributionFileEntryProducer = null))
                false
              }
              else {
                true
              }
            })
    sources.sort()
    buildJar(targetFile = antTargetFile, sources = sources)

    sources.map { source ->
      ProjectLibraryEntry(path = antTargetFile,
                          data = libraryData,
                          libraryFile = source.file,
                          hash = source.hash,
                          size = source.size)
    }
  }
}

private fun CoroutineScope.createBuildBrokenPluginListJob(context: BuildContext): Job? {
  val buildString = context.fullBuildNumber
  val fileName = "brokenPlugins.db"
  val targetFile = context.paths.tempDir.resolve(fileName)
  return createSkippableJob(spanBuilder("build broken plugin list")
                              .setAttribute("buildNumber", buildString)
                              .setAttribute("path", targetFile.toString()), BuildOptions.BROKEN_PLUGINS_LIST_STEP, context) {
    buildBrokenPlugins(targetFile, buildString, context.options.isInDevelopmentMode)
    if (Files.exists(targetFile)) {
      context.addDistFile(DistFile(file = targetFile, relativePath = "bin/$fileName"))
    }
  }
}

private fun CoroutineScope.createBuildThirdPartyLibraryListJob(entries: List<DistributionFileEntry>, context: BuildContext): Job? {
  return createSkippableJob(spanBuilder("generate table of licenses for used third-party libraries"),
                            BuildOptions.THIRD_PARTY_LIBRARIES_LIST_STEP, context) {
    val generator = LibraryLicensesListGenerator.create(project = context.project,
                                                        licensesList = context.productProperties.allLibraryLicenses,
                                                        usedModulesNames = entries.includedModules.toHashSet())
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
  if (plugin.directoryName in context.options.bundledPluginDirectoriesToSkip) {
    return false
  }

  val bundlingRestrictions = plugin.bundlingRestrictions

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

  if (bundlingRestrictions == PluginBundlingRestrictions.MARKETPLACE) {
    return false
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
  return spanBuilder("build keymap plugins").useWithScope {
    withContext(Dispatchers.IO) {
      listOf(
        arrayOf("Mac OS X", "Mac OS X 10.5+"),
        arrayOf("Default for GNOME"),
        arrayOf("Default for KDE"),
        arrayOf("Default for XWin"),
        arrayOf("Emacs"),
        arrayOf("Sublime Text", "Sublime Text (Mac OS X)"),
      ).map {
        async { buildKeymapPlugin(it, context.buildNumber, targetDir, keymapDir) }
      }
    }.map { it.getCompleted() }
  }
}

suspend fun layoutDistribution(layout: BaseLayout,
                               platformLayout: PlatformLayout,
                               targetDirectory: Path,
                               copyFiles: Boolean = true,
                               moduleOutputPatcher: ModuleOutputPatcher,
                               includedModules: Collection<ModuleItem>,
                               moduleWithSearchableOptions: Set<String>,
                               context: BuildContext): Pair<List<DistributionFileEntry>, Path> {
  Files.createDirectories(targetDirectory)

  if (copyFiles) {
    withContext(Dispatchers.IO) {
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
            patcher(moduleOutputPatcher, context)
          }
        }
      }
    }
  }

  val entries = coroutineScope {
    val tasks = ArrayList<Deferred<Collection<DistributionFileEntry>>>(3)
    tasks.add(async {
      val outputDir = targetDirectory.resolve("lib")
      spanBuilder("pack").setAttribute("outputDir", outputDir.toString()).useWithScope {
        JarPackager.pack(includedModules = includedModules,
                         outputDir = outputDir,
                         isRootDir = layout is PlatformLayout,
                         layout = layout,
                         platformLayout = platformLayout,
                         moduleOutputPatcher = moduleOutputPatcher,
                         moduleWithSearchableOptions = moduleWithSearchableOptions,
                         dryRun = !copyFiles,
                         context = context)
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

@Internal
fun hasResourcePaths(layout: BaseLayout): Boolean = !layout.resourcePaths.isEmpty()

@Internal
fun layoutResourcePaths(layout: BaseLayout, context: BuildContext, targetDirectory: Path, overwrite: Boolean) {
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
  if (Files.exists(targetFile)) {
    val t = Files.getLastModifiedTime(targetFile).toMillis()
    val s = Files.getLastModifiedTime(sourceDir).toMillis()
    if (t == s) {
      return false
    }
    Files.delete(targetFile)
  }
  return true
}

private suspend fun layoutAdditionalResources(layout: BaseLayout, context: BuildContext, targetDirectory: Path) {
  layoutResourcePaths(layout = layout, context = context, targetDirectory = targetDirectory, overwrite = false)
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
    var artifactPath = targetDirectory.resolve("lib").resolve(relativePath)
    val sourcePath = artifact.outputFilePath?.let(Path::of) ?: error("Missing output path for '$artifactName' artifact")
    if (copyFiles) {
      require(withContext(Dispatchers.IO) { Files.exists(sourcePath) }) {
        "Missing output file for '$artifactName' artifact: outputFilePath=${artifact.outputFilePath}, outputPath=${artifact.outputPath}"
      }
    }
    if (artifact.outputFilePath == artifact.outputPath) {
      if (copyFiles) {
        withContext(Dispatchers.IO) {
          copyDir(sourcePath, artifactPath)
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
      entries.add(ModuleOutputEntry(path = artifactFile,
                                    moduleName = element.moduleReference.moduleName,
                                    size = 0,
                                    hash = 0,
                                    reason = "artifact: ${artifact.name}"))
    }
    else if (element is JpsTestModuleOutputPackagingElement) {
      entries.add(ModuleTestOutputEntry(path = artifactFile, moduleName = element.moduleReference.moduleName))
    }
    else if (element is JpsLibraryFilesPackagingElement) {
      val library = element.libraryReference.resolve()
      val parentReference = library!!.createReference().parentReference
      if (parentReference is JpsModuleReference) {
        entries.add(ModuleLibraryFileEntry(path = artifactFile,
                                           moduleName = parentReference.moduleName,
                                           libraryName = LibraryLicensesListGenerator.getLibraryName(library),
                                           libraryFile = null,
                                           hash = 0,
                                           size = 0))
      }
      else {
        val libraryData = ProjectLibraryData(library.name, LibraryPackMode.MERGED, reason = "<- artifact ${artifact.name}")
        entries.add(ProjectLibraryEntry(path = artifactFile, data = libraryData, libraryFile = null, hash = 0, size = 0))
      }
    }
  }
}

private fun checkModuleExcludes(moduleExcludes: Map<String, List<String>>, context: CompilationContext) {
  moduleExcludes.keys.forEach { module ->
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
    withContext(Dispatchers.IO) {
      for ((source, target, optimized) in items) {
        launch {
          spanBuilder("archive plugin")
            .setAttribute("input", source.toString())
            .setAttribute("outputFile", target.toString())
            .setAttribute("optimizedZip", optimized)
            .useWithScope {
              if (optimized) {
                writeNewZip(target, compress = compress, withOptimizedMetadataEnabled = false) { zipCreator ->
                  ZipArchiver(zipCreator).use { archiver ->
                    if (Files.isDirectory(source)) {
                      archiver.setRootDir(source, source.fileName.toString())
                      archiveDir(startDir = source, archiver = archiver, excludes = null)
                    }
                    else {
                      archiver.setRootDir(source.parent)
                      archiver.addFile(source)
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
          if (withBlockMap) {
            spanBuilder("build plugin blockmap").setAttribute("file", target.toString()).useWithScope {
              buildBlockMap(target, json)
            }
          }
        }
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
  writeNewZip(fileParent.resolve("$fileName.blockmap.zip"), compress = true) {
    it.compressedData("blockmap.json", ByteBuffer.wrap(bytes))
  }

  val hashFile = fileParent.resolve("$fileName.hash.json")
  Files.newInputStream(file).use { input ->
    Files.newOutputStream(hashFile, *W_CREATE_NEW.toTypedArray()).use { output ->
      json.write(FileHash(input, algorithm), output)
    }
  }
}

suspend fun createIdeClassPath(platform: PlatformLayout, context: BuildContext): Set<String> {
  val (lib, plugins) = generateProjectStructureMapping(context = context, platformLayout = platform)

  val pluginLayouts = context.productProperties.productLayout.pluginLayouts
  val classPath = LinkedHashSet<String>()

  val libDir = context.paths.distAllDir.resolve("lib")
  for (entry in lib) {
    if (libDir.relativize(entry.path).nameCount != 1) {
      continue
    }

    when (entry) {
      is ModuleOutputEntry -> {
        classPath.add(context.getModuleOutputDir(context.findRequiredModule(entry.moduleName)).toString())
      }
      is LibraryFileEntry -> classPath.add(entry.libraryFile.toString())
      else -> throw UnsupportedOperationException("Entry $entry is not supported")
    }
  }

  val pluginDir = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY)
  for (entry in plugins) {
    val relativePath = pluginDir.relativize(entry.path)
    // for plugins, our classloader load jars only from lib folder
    if (relativePath.nameCount != 3 || relativePath.getName(1).toString() != "lib") {
      continue
    }

    when (entry) {
      is ModuleOutputEntry -> {
        classPath.add(context.getModuleOutputDir(context.findRequiredModule(entry.moduleName)).toString())
        (pluginLayouts.firstOrNull { it.mainModule == entry.moduleName } ?: continue)
          .scrambleClasspathPlugins
          .asSequence()
          .map { it.first }
          .map { directoryName -> pluginLayouts.single { it.directoryName == directoryName } }
          .mapTo(classPath) { context.getModuleOutputDir(context.findRequiredModule(it.mainModule)).toString() }
      }
      is LibraryFileEntry -> classPath.add(entry.libraryFile.toString())
      else -> throw UnsupportedOperationException("Entry $entry is not supported")
    }
  }
  return classPath
}

suspend fun buildSearchableOptions(platform: PlatformLayout,
                                   context: BuildContext,
                                   systemProperties: Map<String, Any> = emptyMap()): Path? {
  return buildSearchableOptions(ideClassPath = createIdeClassPath(platform, context),
                                context = context,
                                systemProperties = systemProperties)
}

/**
 * Build index which is used to search options in the Settings dialog.
 */
suspend fun buildSearchableOptions(ideClassPath: Set<String>,
                                   context: BuildContext,
                                   systemProperties: Map<String, Any> = emptyMap()): Path? {
  val span = Span.current()
  if (context.isStepSkipped(BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP)) {
    span.addEvent("skip building searchable options index")
    return null
  }

  val targetDirectory = context.paths.searchableOptionDir
  val modules = withContext(Dispatchers.IO) {
    NioFiles.deleteRecursively(targetDirectory)
    // bundled maven is also downloaded during traverseUI execution in an external process,
    // making it fragile to call more than one traverseUI at the same time (in the reproducibility test, for example),
    // so it's pre-downloaded with proper synchronization
    BundledMavenDownloader.downloadMaven4Libs(context.paths.communityHomeDirRoot)
    BundledMavenDownloader.downloadMaven3Libs(context.paths.communityHomeDirRoot)
    BundledMavenDownloader.downloadMavenDistribution(context.paths.communityHomeDirRoot)
    // Start the product in headless mode using com.intellij.ide.ui.search.TraverseUIStarter.
    // It'll process all UI elements in the `Settings` dialog and build an index for them.
    runApplicationStarter(context = context,
                          tempDir = context.paths.tempDir.resolve("searchableOptions"),
                          ideClasspath = ideClassPath,
                          arguments = listOf("traverseUI", targetDirectory.toString(), "true"),
                          vmOptions = listOf("-Xmx2g"),
                          systemProperties = systemProperties)
    check(Files.isDirectory(targetDirectory)) {
      "Failed to build searchable options index: $targetDirectory does not exist. See log above for error output from traverseUI run."
    }
    Files.newDirectoryStream(targetDirectory).use { it.toList() }
  }
  check(!modules.isEmpty()) {
    "Failed to build searchable options index: $targetDirectory is empty. See log above for error output from traverseUI run."
  }

  span.setAttribute(AttributeKey.longKey("moduleCountWithSearchableOptions"), modules.size)
  span.setAttribute(AttributeKey.stringArrayKey("modulesWithSearchableOptions"), modules.map { targetDirectory.relativize(it).toString() })
  return targetDirectory
}
