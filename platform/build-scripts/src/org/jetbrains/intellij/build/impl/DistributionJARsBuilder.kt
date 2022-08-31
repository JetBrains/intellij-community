// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.createTask
import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.diagnostic.telemetry.useWithScope2
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.io.Compressor
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.fus.createStatisticsRecorderBundledMetadataProviderTask
import org.jetbrains.intellij.build.impl.JarPackager.Companion.pack
import org.jetbrains.intellij.build.impl.SVGPreBuilder.createPrebuildSvgIconsTask
import org.jetbrains.intellij.build.impl.projectStructureMapping.*
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFile
import org.jetbrains.intellij.build.io.copyFileToDir
import org.jetbrains.intellij.build.io.zip
import org.jetbrains.intellij.build.tasks.*
import org.jetbrains.jps.model.artifact.JpsArtifact
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.artifact.elements.JpsLibraryFilesPackagingElement
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsProductionModuleOutputPackagingElement
import org.jetbrains.jps.model.java.JpsTestModuleOutputPackagingElement
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ForkJoinTask
import java.util.function.Predicate
import java.util.stream.Collectors

/**
 * Assembles output of modules to platform JARs (in [BuildPaths.distAllDir]/lib directory),
 * bundled plugins' JARs (in [distAll][BuildPaths.distAllDir]/plugins directory) and zip archives with
 * non-bundled plugins (in [artifacts][BuildPaths.artifactDir]/plugins directory).
 */
class DistributionJARsBuilder {
  val state: DistributionBuilderState

  @JvmOverloads
  constructor(context: BuildContext, pluginsToPublish: Set<PluginLayout> = emptySet()) {
    state = DistributionBuilderState(pluginsToPublish, context)
  }

  constructor(state: DistributionBuilderState) {
    this.state = state
  }

  suspend fun buildJARs(context: BuildContext, isUpdateFromSources: Boolean = false): ProjectStructureMapping {
    validateModuleStructure(context)
    val svgPrebuildTask = createPrebuildSvgIconsTask(context)?.fork()
    val brokenPluginsTask = createBuildBrokenPluginListTask(context)?.fork()
    val traceContext = Context.current().asContextElement()
    val entries = coroutineScope {
      // must be completed before plugin building
      createSkippableJob(spanBuilder("build searchable options index"), BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP, context) {
        buildSearchableOptions(context)
      }?.join()

      val pluginLayouts = getPluginsByModules(context.productProperties.productLayout.bundledPluginModules, context)
      val antDir = if (context.productProperties.isAntRequired) context.paths.distAllDir.resolve("lib/ant") else null
      val antTargetFile = antDir?.resolve("lib/ant.jar")
      val moduleOutputPatcher = ModuleOutputPatcher()
      val buildPlatformJob: Deferred<List<DistributionFileEntry>> = async(traceContext) {
        spanBuilder("build platform lib").useWithScope2 {
          coroutineScope {
            createStatisticsRecorderBundledMetadataProviderTask(moduleOutputPatcher, context)
            launch {
              spanBuilder("write patched app info").useWithScope {
                val moduleOutDir = context.getModuleOutputDir(context.findRequiredModule("intellij.platform.core"))
                val relativePath = "com/intellij/openapi/application/ApplicationNamesInfo.class"
                val result = injectAppInfo(moduleOutDir.resolve(relativePath), context.applicationInfo.appInfoXml)
                moduleOutputPatcher.patchModuleOutput("intellij.platform.core", relativePath, result)
              }
            }
          }

          val result = buildLib(moduleOutputPatcher = moduleOutputPatcher, platform = state.platform, context = context)
          if (!isUpdateFromSources && context.productProperties.scrambleMainJar) {
            scramble(context)
          }

          context.bootClassPathJarNames = generateClasspath(homeDir = context.paths.distAllDir,
                                                            mainJarName = context.productProperties.productLayout.mainJarName,
                                                            antTargetFile = antTargetFile)
          result
        }
      }

      listOfNotNull(
        buildPlatformJob,
        async { buildBundledPlugins(pluginLayouts, buildPlatformJob, context) },
        async { buildOsSpecificBundledPlugins(pluginLayouts, isUpdateFromSources, buildPlatformJob, context) },
        async {
          buildNonBundledPlugins(pluginsToPublish = state.pluginsToPublish,
                                 compressPluginArchive = !isUpdateFromSources && context.options.compressNonBundledPluginArchive,
                                 buildPlatformLibJob = buildPlatformJob,
                                 context = context)
        },
        if (antDir == null) null else async { copyAnt(antDir, (antTargetFile)!!, context) }
      )
    }.flatMap { it.getCompleted() }

    // must be before reorderJars as these additional plugins maybe required for IDE start-up
    val additionalPluginPaths = context.productProperties.getAdditionalPluginPaths(context)
    if (!additionalPluginPaths.isEmpty()) {
      val pluginDir = context.paths.distAllDir.resolve("plugins")
      for (sourceDir in additionalPluginPaths) {
        copyDir(sourceDir, pluginDir.resolve(sourceDir.fileName))
      }
    }

    val projectStructureMapping = ProjectStructureMapping(entries)
    ForkJoinTask.invokeAll(listOfNotNull(
      createTask(spanBuilder("generate content report")) {
        Files.createDirectories(context.paths.artifactDir)
        ProjectStructureMapping.writeReport(entries, context.paths.artifactDir.resolve("content-mapping.json"), context.paths)
        Files.newOutputStream(context.paths.artifactDir.resolve("content.json")).use {
          ProjectStructureMapping.buildJarContentReport(entries, it, context.paths)
        }
      },
      buildThirdPartyLibrariesList(projectStructureMapping, context)
    ))

    // inversed order of join - better for FJP (https://shipilev.net/talks/jeeconf-May2012-forkjoin.pdf, slide 32)
    brokenPluginsTask?.join()
    svgPrebuildTask?.join()
    return projectStructureMapping
  }

  /**
   * Validates module structure to be ensure all module dependencies are included
   */
  fun validateModuleStructure(context: BuildContext) {
    if (context.options.validateModuleStructure) {
      ModuleStructureValidator(context, state.platform.moduleJars).validate()
    }
  }

  // Filter out jars with relative paths in name
  val productModules: List<String>
    get() {
      val result = ArrayList<String>()
      for (moduleJar in state.platform.getJarToIncludedModuleNames()) {
        // Filter out jars with relative paths in name
        if (moduleJar.key.contains('\\') || moduleJar.key.contains('/')) {
          continue
        }
        result.addAll((moduleJar.value))
      }
      return result
    }

  /**
   * Build index which is used to search options in the Settings dialog.
   */
  suspend fun buildSearchableOptions(context: BuildContext, systemProperties: Map<String, Any> = emptyMap()): Path? {
    val span = Span.current()
    if (context.options.buildStepsToSkip.contains(BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP)) {
      span.addEvent("skip building searchable options index")
      return null
    }

    val messages = context.messages
    val targetDirectory = context.searchableOptionDir
    val modules = withContext(Dispatchers.IO) {
      val ideClasspath = createIdeClassPath(context)
      NioFiles.deleteRecursively(targetDirectory)
      // Start the product in headless mode using com.intellij.ide.ui.search.TraverseUIStarter.
      // It'll process all UI elements in Settings dialog and build index for them.
      runApplicationStarter(context = context,
                            tempDir = context.paths.tempDir.resolve("searchableOptions"),
                            ideClasspath = ideClasspath,
                            arguments = listOf("traverseUI", targetDirectory.toString(), "true"),
                            systemProperties = systemProperties)
      if (!Files.isDirectory(targetDirectory)) {
        messages.error("Failed to build searchable options index: $targetDirectory does not exist. " +
                       "See log above for error output from traverseUI run.")
      }
      Files.newDirectoryStream(targetDirectory).use { it.toList() }
    }
    if (modules.isEmpty()) {
      messages.error("Failed to build searchable options index: $targetDirectory is empty. " +
                     "See log above for error output from traverseUI run.")
    }
    else {
      span.setAttribute(AttributeKey.longKey("moduleCountWithSearchableOptions"), modules.size)
      span.setAttribute(AttributeKey.stringArrayKey("modulesWithSearchableOptions"),
                        modules.map { targetDirectory.relativize(it).toString() })
    }
    return targetDirectory
  }

  suspend fun createIdeClassPath(context: BuildContext): LinkedHashSet<String> {
    // for some reasons maybe duplicated paths - use set
    val classPath = LinkedHashSet<String>()

    val pluginLayoutRoot: Path = withContext(Dispatchers.IO) {
      Files.createDirectories(context.paths.tempDir)
      Files.createTempDirectory(context.paths.tempDir, "pluginLayoutRoot")
    }
    val nonPluginsEntries = ArrayList<DistributionFileEntry>()
    val pluginsEntries = ArrayList<DistributionFileEntry>()
    for (e in generateProjectStructureMapping(context, pluginLayoutRoot)) {
      if (e.path.startsWith(pluginLayoutRoot)) {
        val relPath = pluginLayoutRoot.relativize(e.path)
        // For plugins our classloader load jars only from lib folder
        val parent = relPath.parent
        if ((parent?.parent) == null && (relPath.parent.toString() == "lib")) {
          pluginsEntries.add(e)
        }
      }
      else {
        nonPluginsEntries.add(e)
      }
    }
    for (entry in (nonPluginsEntries + pluginsEntries)) {
      when (entry) {
        is ModuleOutputEntry -> classPath.add(context.getModuleOutputDir(context.findRequiredModule(entry.moduleName)).toString())
        is LibraryFileEntry -> classPath.add((entry as LibraryFileEntry).libraryFile.toString())
        else -> throw UnsupportedOperationException("Entry $entry is not supported")
      }
    }
    return classPath
  }

  internal suspend fun generateProjectStructureMapping(context: BuildContext, pluginLayoutRoot: Path): List<DistributionFileEntry> {
    val moduleOutputPatcher = ModuleOutputPatcher()
    return coroutineScope {
      val libDirLayout = async {
        processLibDirectoryLayout(moduleOutputPatcher = moduleOutputPatcher,
                                  platform = state.platform,
                                  context = context,
                                  copyFiles = false)
      }
      val allPlugins = getPluginsByModules(context.productProperties.productLayout.bundledPluginModules, context)
      val entries = ArrayList<DistributionFileEntry>()
      for (plugin in allPlugins) {
        if (satisfiesBundlingRequirements(plugin = plugin, osFamily = null, arch = null, context = context)) {
          entries.addAll(layoutDistribution(layout = plugin,
                                targetDirectory = pluginLayoutRoot,
                                copyFiles = false,
                                moduleOutputPatcher = moduleOutputPatcher,
                                moduleJars = plugin.moduleJars,
                                context = context))
        }
      }
      entries.addAll(libDirLayout.await())
       entries
    }
  }

  suspend fun buildBundledPlugins(plugins: Collection<PluginLayout>,
                                  buildPlatformJob: Job?,
                                  context: BuildContext): List<DistributionFileEntry> {
    val pluginDirectoriesToSkip = context.options.bundledPluginDirectoriesToSkip
    return spanBuilder("build bundled plugins")
      .setAttribute(AttributeKey.stringArrayKey("pluginDirectoriesToSkip"), pluginDirectoriesToSkip.toList())
      .setAttribute("count", plugins.size.toLong())
      .useWithScope { span ->
        val pluginsToBundle = ArrayList<PluginLayout>(plugins.size)
        plugins.filterTo(pluginsToBundle) {
          satisfiesBundlingRequirements(plugin = it, osFamily = null, arch = null, context = context) &&
          !pluginDirectoriesToSkip.contains(it.directoryName)
        }

        // Doesn't make sense to require passing here a list with a stable order - unnecessary complication. Just sort by main module.
        pluginsToBundle.sortWith(PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE)
        span.setAttribute("satisfiableCount", pluginsToBundle.size.toLong())
        buildPlugins(moduleOutputPatcher = ModuleOutputPatcher(),
                     plugins = pluginsToBundle,
                     targetDirectory = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY),
                     state = state,
                     context = context,
                     buildPlatformJob = buildPlatformJob)
      }
  }

  private suspend fun buildOsSpecificBundledPlugins(pluginLayouts: Set<PluginLayout>,
                                                    isUpdateFromSources: Boolean,
                                                    buildPlatformJob: Job?,
                                                    context: BuildContext): List<DistributionFileEntry> {
    return spanBuilder("build os-specific bundled plugins")
      .setAttribute("isUpdateFromSources", isUpdateFromSources)
      .useWithScope2 {
        val platforms = if (isUpdateFromSources) {
          listOf(SupportedDistribution(os = OsFamily.currentOs, arch = JvmArchitecture.currentJvmArch))
        }
        else {
          SUPPORTED_DISTRIBUTIONS
        }

        coroutineScope {
          platforms.mapNotNull { (osFamily, arch) ->
            if (!context.shouldBuildDistributionForOS(osFamily, arch)) {
              return@mapNotNull null
            }

            val osSpecificPlugins = pluginLayouts.filter { satisfiesBundlingRequirements(it, osFamily, arch, context) }
            if (osSpecificPlugins.isEmpty()) {
              return@mapNotNull null
            }

            val outDir = if (isUpdateFromSources) {
              context.paths.distAllDir.resolve("plugins")
            }
            else {
              getOsAndArchSpecificDistDirectory(osFamily, arch, context).resolve("plugins")
            }

            async(Dispatchers.IO) {
              spanBuilder("build bundled plugins")
                .setAttribute("os", osFamily.osName)
                .setAttribute("arch", arch.name)
                .setAttribute("count", osSpecificPlugins.size.toLong())
                .setAttribute("outDir", outDir.toString())
                .useWithScope2 {
                  buildPlugins(moduleOutputPatcher = ModuleOutputPatcher(),
                               plugins = osSpecificPlugins, targetDirectory = outDir,
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
                                     context: BuildContext): List<DistributionFileEntry> {
    if (pluginsToPublish.isEmpty()) {
      return emptyList()
    }

    return spanBuilder("build non-bundled plugins").setAttribute("count", pluginsToPublish.size.toLong()).useWithScope2 {
      if (context.options.buildStepsToSkip.contains(BuildOptions.NON_BUNDLED_PLUGINS_STEP)) {
        Span.current().addEvent("skip")
        return@useWithScope2 emptyList<DistributionFileEntry>()
      }

      val nonBundledPluginsArtifacts = context.paths.artifactDir.resolve("${context.applicationInfo.productCode}-plugins")
      val autoUploadingDir = nonBundledPluginsArtifacts.resolve("auto-uploading")
      val buildKeymapPluginsTask = buildKeymapPlugins(autoUploadingDir, context).fork()
      val moduleOutputPatcher = ModuleOutputPatcher()
      val stageDir = context.paths.tempDir.resolve("non-bundled-plugins-" + context.applicationInfo.productCode)
      NioFiles.deleteRecursively(stageDir)
      val dirToJar = ConcurrentLinkedQueue<Pair<String, Path>>()
      val defaultPluginVersion = if (context.buildNumber.endsWith(".SNAPSHOT")) {
        "${context.buildNumber}.${pluginDateFormat.format(ZonedDateTime.now())}"
      }
      else {
        context.buildNumber
      }

      // buildPlugins pluginBuilt listener is called concurrently
      val pluginsToIncludeInCustomRepository = ConcurrentLinkedQueue<PluginRepositorySpec>()
      val autoPublishPluginChecker = loadPluginAutoPublishList(context)
      val prepareCustomPluginRepositoryForPublishedPlugins = context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins
      val mappings = buildPlugins(moduleOutputPatcher = moduleOutputPatcher,
                                  plugins = pluginsToPublish.sortedWith(PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE),
                                  targetDirectory = stageDir,
                                  state = state,
                                  context = context,
                                  buildPlatformJob = buildPlatformLibJob) { plugin, pluginDir ->
        val targetDirectory = if (autoPublishPluginChecker.test(plugin)) autoUploadingDir else nonBundledPluginsArtifacts
        val pluginDirName = pluginDir.fileName.toString()
        val moduleOutput = context.getModuleOutputDir(context.findRequiredModule(plugin.mainModule))
        val pluginXmlPath = moduleOutput.resolve("META-INF/plugin.xml")
        val pluginVersion = if (Files.exists(pluginXmlPath)) {
          plugin.versionEvaluator.evaluate(pluginXmlPath, defaultPluginVersion, context)
        }
        else {
          defaultPluginVersion
        }
        val destFile = targetDirectory.resolve("$pluginDirName-$pluginVersion.zip")
        if (prepareCustomPluginRepositoryForPublishedPlugins) {
          val pluginXml = moduleOutputPatcher.getPatchedPluginXml(plugin.mainModule)
          pluginsToIncludeInCustomRepository.add(PluginRepositorySpec(destFile, pluginXml))
        }
        dirToJar.add(pluginDirName to destFile)
      }

      bulkZipWithPrefix(commonSourceDir = stageDir, items = dirToJar, compress = compressPluginArchive)
      buildHelpPlugin(pluginVersion = defaultPluginVersion, context = context)?.let { helpPlugin ->
        val spec = buildHelpPlugin(helpPlugin = helpPlugin,
                                   pluginsToPublishDir = stageDir,
                                   targetDir = autoUploadingDir,
                                   moduleOutputPatcher = moduleOutputPatcher,
                                   context = context)
        if (prepareCustomPluginRepositoryForPublishedPlugins) {
          pluginsToIncludeInCustomRepository.add(spec)
        }
      }

      for (item in buildKeymapPluginsTask.join()) {
        if (prepareCustomPluginRepositoryForPublishedPlugins) {
          pluginsToIncludeInCustomRepository.add(PluginRepositorySpec(item.first, item.second))
        }
      }

      if (prepareCustomPluginRepositoryForPublishedPlugins) {
        val list = pluginsToIncludeInCustomRepository.sortedBy { it.pluginZip }
        generatePluginRepositoryMetaFile(list, nonBundledPluginsArtifacts, context)
        generatePluginRepositoryMetaFile(list.filter { it.pluginZip.startsWith(autoUploadingDir) }, autoUploadingDir, context)
      }

      mappings
    }
  }

  private suspend fun buildHelpPlugin(helpPlugin: PluginLayout,
                              pluginsToPublishDir: Path,
                              targetDir: Path,
                              moduleOutputPatcher: ModuleOutputPatcher,
                              context: BuildContext): PluginRepositorySpec {
    val directory = getActualPluginDirectoryName(helpPlugin, context)
    val destFile = targetDir.resolve("$directory.zip")
    spanBuilder("build help plugin").setAttribute("dir", directory).useWithScope {
      buildPlugins(moduleOutputPatcher = moduleOutputPatcher,
                   plugins = listOf(helpPlugin),
                   targetDirectory = pluginsToPublishDir,
                   state = state,
                   context = context,
                   buildPlatformJob = null)
      zip(targetFile = destFile, dirs = mapOf(pluginsToPublishDir.resolve(directory) to ""), compress = true)
      null
    }
    return PluginRepositorySpec(destFile, moduleOutputPatcher.getPatchedPluginXml(helpPlugin.mainModule))
  }
}

private suspend fun buildPlugins(moduleOutputPatcher: ModuleOutputPatcher,
                                 plugins: Collection<PluginLayout>,
                                 targetDirectory: Path,
                                 state: DistributionBuilderState,
                                 context: BuildContext,
                                 buildPlatformJob: Job?,
                                 pluginBuilt: ((PluginLayout, Path) -> Unit)? = null): List<DistributionFileEntry> {
  val scrambleTool = context.proprietaryBuildTools.scrambleTool
  val isScramblingSkipped = context.options.buildStepsToSkip.contains(BuildOptions.SCRAMBLING_STEP)
  val scrambleTasks = ArrayList<ForkJoinTask<*>>()

  val entries: List<DistributionFileEntry> = coroutineScope {
    plugins.map { plugin ->
      val isHelpPlugin = plugin.mainModule == "intellij.platform.builtInHelp"
      if (!isHelpPlugin) {
        checkOutputOfPluginModules(plugin.mainModule, plugin.moduleJars, plugin.moduleExcludes, context)
        patchPluginXml(moduleOutputPatcher = moduleOutputPatcher,
                       plugin = plugin,
                       releaseDate = context.applicationInfo.majorReleaseDate,
                       releaseVersion = context.applicationInfo.releaseVersionForLicensing,
                       pluginsToPublish = state.pluginsToPublish,
                       context = context)
      }

      val directoryName = getActualPluginDirectoryName(plugin, context)
      val pluginDir = targetDirectory.resolve(directoryName)
      async {
        spanBuilder("plugin").setAttribute("path", context.paths.buildOutputDir.relativize(pluginDir).toString()).useWithScope2 {
          val result = layoutDistribution(layout = plugin,
                              targetDirectory = pluginDir,
                              copyFiles = true,
                              moduleOutputPatcher = moduleOutputPatcher,
                              moduleJars = plugin.moduleJars,
                              context = context)
          if (!plugin.pathsToScramble.isEmpty()) {
            val attributes = Attributes.of(AttributeKey.stringKey("plugin"), directoryName)
            if (scrambleTool == null) {
              Span.current()
                .addEvent("skip scrambling plugin because scrambleTool isn't defined, but plugin defines paths to be scrambled", attributes)
            }
            else if (isScramblingSkipped) {
              Span.current().addEvent("skip scrambling plugin because step is disabled", attributes)
            }
            else {
              scrambleTool.scramblePlugin(context, plugin, pluginDir, targetDirectory)?.let {
                // we can not start executing right now because the plugin can use other plugins in a scramble classpath
                scrambleTasks.add(it)
              }
            }
          }
          pluginBuilt?.invoke(plugin, pluginDir)
          result
        }
      }
    }
  }.flatMap { it.getCompleted() }

  if (!scrambleTasks.isEmpty()) {
    // scrambling can require classes from platform
    buildPlatformJob?.let { task ->
      spanBuilder("wait for platform lib for scrambling").useWithScope { task.join() }
    }
    invokeAllSettled(scrambleTasks)
  }
  return entries
}

private const val PLUGINS_DIRECTORY = "plugins"
private val PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE: Comparator<PluginLayout> = compareBy { it.mainModule }

internal class PluginRepositorySpec(@JvmField val pluginZip: Path, @JvmField val pluginXml: ByteArray /* content of plugin.xml */)

fun getPluginsByModules(modules: Collection<String>, context: BuildContext): Set<PluginLayout> {
  if (modules.isEmpty()) {
    return emptySet()
  }

  val pluginLayouts = context.productProperties.productLayout.pluginLayouts
  val pluginLayoutsByMainModule = pluginLayouts.groupBy { it.mainModule }
  val result = createPluginLayoutSet(modules.size)
  for (moduleName in modules) {
    var customLayouts = pluginLayoutsByMainModule.get(moduleName)
    if (customLayouts == null) {
      val alternativeModuleName = context.findModule(moduleName)?.name
      if (alternativeModuleName != moduleName) {
        customLayouts = pluginLayoutsByMainModule.get(alternativeModuleName)
      }
    }

    if (customLayouts == null) {
      if (!(moduleName == "kotlin-ultimate.kmm-plugin" || result.add(PluginLayout.simplePlugin(moduleName)))) {
        throw IllegalStateException("Plugin layout for module $moduleName is already added (duplicated module name?)")
      }
    }
    else {
      for (layout in customLayouts) {
        if (layout.mainModule != "kotlin-ultimate.kmm-plugin" && !result.add(layout)) {
          throw IllegalStateException("Plugin layout for module $moduleName is already added (duplicated module name?)")
        }
      }
    }
  }
  return result
}

@TestOnly
fun collectProjectLibrariesWhichShouldBeProvidedByPlatform(plugin: BaseLayout,
                                                           result: MultiMap<JpsLibrary, JpsModule>,
                                                           context: BuildContext) {
  val libsToUnpack = plugin.projectLibrariesToUnpack.values()
  for (moduleName in plugin.getIncludedModuleNames()) {
    val module = context.findRequiredModule((moduleName))
    val dependencies = JpsJavaExtensionService.dependencies(module)
    for (library in dependencies.includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).libraries) {
      if (isProjectLibraryUsedByPlugin(library, plugin, libsToUnpack)) {
        result.putValue(library, module)
      }
    }
  }
}

internal fun getThirdPartyLibrariesHtmlFilePath(context: BuildContext): Path {
  return context.paths.distAllDir.resolve("license/third-party-libraries.html")
}

internal fun getThirdPartyLibrariesJsonFilePath(context: BuildContext): Path {
  return context.paths.tempDir.resolve("third-party-libraries.json")
}

/**
 * Returns path to a JAR file in the product distribution where platform/plugin classes will be placed. If the JAR name corresponds to
 * a module name and the module was renamed, return the old name to temporary keep the product layout unchanged.
 */
private fun getActualModuleJarPath(relativeJarPath: String,
                                   moduleNames: Collection<String>,
                                   explicitlySetJarPaths: Set<String>,
                                   context: BuildContext): String {
  if (explicitlySetJarPaths.contains(relativeJarPath)) {
    return relativeJarPath
  }

  for (moduleName in moduleNames) {
    if (relativeJarPath == "${convertModuleNameToFileName(moduleName)}.jar" && context.getOldModuleName(moduleName) != null) {
      return "${context.getOldModuleName(moduleName)}.jar"
    }
  }
  return relativeJarPath
}

/**
 * Returns name of directory in the product distribution where plugin will be placed. For plugins which use the main module name as the
 * directory name return the old module name to temporary keep layout of plugins unchanged.
 */
fun getActualPluginDirectoryName(plugin: PluginLayout, context: BuildContext): String {
  if (!plugin.directoryNameSetExplicitly && (plugin.directoryName == convertModuleNameToFileName(plugin.mainModule))) {
    context.getOldModuleName(plugin.mainModule)?.let {
      return it
    }
  }
  return plugin.directoryName
}

private fun basePath(buildContext: BuildContext, moduleName: String): Path {
  return Path.of(JpsPathUtil.urlToPath(buildContext.findRequiredModule(moduleName).contentRootsList.urls.first()))
}

suspend fun buildLib(moduleOutputPatcher: ModuleOutputPatcher, platform: PlatformLayout, context: BuildContext): List<DistributionFileEntry> {
  patchKeyMapWithAltClickReassignedToMultipleCarets(moduleOutputPatcher, context)
  val libDirMappings = processLibDirectoryLayout(moduleOutputPatcher = moduleOutputPatcher,
                                                 platform = platform,
                                                 context = context,
                                                 copyFiles = true)

  val scrambleTool = context.proprietaryBuildTools.scrambleTool ?: return libDirMappings
  val libDir = context.paths.distAllDir.resolve("lib")
  for (forbiddenJarName in scrambleTool.getNamesOfJarsRequiredToBeScrambled()) {
    check (!Files.exists(libDir.resolve(forbiddenJarName))) {
      "The following JAR cannot be included into the product 'lib' directory, it need to be scrambled with the main jar: $forbiddenJarName"
    }
  }
  val modulesToBeScrambled = scrambleTool.getNamesOfModulesRequiredToBeScrambled()
  val productLayout = context.productProperties.productLayout
  for (jarName in platform.moduleJars.keySet()) {
    if (jarName != productLayout.mainJarName && jarName != PlatformModules.PRODUCT_JAR) {
      @Suppress("ConvertArgumentToSet")
      val notScrambled = platform.moduleJars.get(jarName).intersect(modulesToBeScrambled)
      if (!notScrambled.isEmpty()) {
        context.messages.error("Module \'${notScrambled.first()}\' is included into $jarName which is not scrambled.")
      }
    }
  }
  return libDirMappings
}

suspend fun processLibDirectoryLayout(moduleOutputPatcher: ModuleOutputPatcher,
                                      platform: PlatformLayout,
                                      context: BuildContext,
                                      copyFiles: Boolean): List<DistributionFileEntry> {
  return spanBuilder("layout")
    .setAttribute("path", context.paths.buildOutputDir.relativize(context.paths.distAllDir).toString())
    .useWithScope2 {
      layoutDistribution(layout = platform,
                         targetDirectory = context.paths.distAllDir,
                         copyFiles = copyFiles,
                         moduleOutputPatcher = moduleOutputPatcher,
                         moduleJars = platform.moduleJars,
                         context = context)
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

internal fun getOsAndArchSpecificDistDirectory(osFamily: OsFamily, arch: JvmArchitecture, context: BuildContext): Path {
  return context.paths.buildOutputDir.resolve("dist.${osFamily.distSuffix}.${arch.name}")
}

fun checkOutputOfPluginModules(mainPluginModule: String,
                               moduleJars: MultiMap<String, String>,
                               moduleExcludes: MultiMap<String, String>,
                               context: BuildContext) {
  // don't check modules which are not direct children of lib/ directory
  val modulesWithPluginXml = ArrayList<String>()
  for (entry in moduleJars.entrySet()) {
    if (!entry.key.contains('/')) {
      for (moduleName in entry.value) {
        if (containsFileInOutput(moduleName, "META-INF/plugin.xml", moduleExcludes.get(moduleName), context)) {
          modulesWithPluginXml.add(moduleName)
        }
      }
    }
  }

  if (modulesWithPluginXml.size > 1) {
    context.messages.error("Multiple modules (${modulesWithPluginXml.joinToString()}) from \'$mainPluginModule\' plugin " +
                           "contain plugin.xml files so the plugin won\'t work properly")
  }
  if (modulesWithPluginXml.isEmpty()) {
    context.messages.error("No module from \'$mainPluginModule\' plugin contains plugin.xml")
  }
  for (moduleJar in moduleJars.values()) {
    if (moduleJar != "intellij.java.guiForms.rt" &&
        containsFileInOutput(moduleJar, "com/intellij/uiDesigner/core/GridLayoutManager.class",
                                                     moduleExcludes.get(moduleJar),
                                                     context)) {
      context.messages.error("Runtime classes of GUI designer must not be packaged to \'$moduleJar\' module in" +
                             " \'$mainPluginModule\' plugin, because they are included into a platform JAR. " +
                             "Make sure that 'Automatically copy form runtime classes " +
                             "to the output directory' is disabled in Settings | Editor | GUI Designer.")
    }
  }
}

private fun containsFileInOutput(moduleName: String,
                                 filePath: String,
                                 excludes: Collection<String>,
                                 buildContext: BuildContext): Boolean {
  val moduleOutput = buildContext.getModuleOutputDir(buildContext.findRequiredModule(moduleName))
  val fileInOutput = moduleOutput.resolve(filePath)
  if (!Files.exists(fileInOutput)) {
    return false
  }

  val set = FileSet(moduleOutput).include(filePath)
  for (it in excludes) {
    set.exclude(it)
  }
  return !set.isEmpty()
}

fun getPluginAutoUploadFile(communityRoot: BuildDependenciesCommunityRoot): Path {
  val autoUploadFile = communityRoot.communityRoot.resolve("../build/plugins-autoupload.txt")
  require(Files.isRegularFile(autoUploadFile)) {
    "File '$autoUploadFile' must exist"
  }
  return autoUploadFile
}

fun readPluginAutoUploadFile(autoUploadFile: Path): Collection<String> {
  val config = Files.lines(autoUploadFile).use { lines ->
    lines
      .map { StringUtil.split(it, "//", true, false)[0] }
      .map { StringUtil.split(it, "#", true, false)[0].trim() }
      .filter { !it.isEmpty() }
      .collect(Collectors.toCollection { TreeSet(String.CASE_INSENSITIVE_ORDER) })
  }

  return config
}

private fun scramble(context: BuildContext) {
  val tool = context.proprietaryBuildTools.scrambleTool

  val actualModuleJars = if (tool == null) emptyMap() else mapOf("internalUtilities.jar" to listOf("intellij.tools.internalUtilities"))
  pack(actualModuleJars = actualModuleJars,
       outputDir = context.paths.buildOutputDir.resolve("internal"),
       context = context)
  tool?.scramble(context.productProperties.productLayout.mainJarName, context)
  ?: Span.current().addEvent("skip scrambling because `scrambleTool` isn't defined")

  // e.g. JetBrainsGateway doesn't have a main jar with license code
  if (Files.exists(context.paths.distAllDir.resolve("lib/${context.productProperties.productLayout.mainJarName}"))) {
    packInternalUtilities(context)
  }
}

private suspend fun copyAnt(antDir: Path, antTargetFile: Path, context: BuildContext): List<DistributionFileEntry> {
  return spanBuilder("copy Ant lib").setAttribute("antDir", antDir.toString()).useWithScope2 {
    val sources = ArrayList<ZipSource>()
    val result = ArrayList<DistributionFileEntry>()
    val libraryData = ProjectLibraryData("Ant", LibraryPackMode.MERGED)
    copyDir(sourceDir = context.paths.communityHomeDir.communityRoot.resolve("lib/ant"),
            targetDir = antDir,
            dirFilter = { !it.endsWith("src") },
            fileFilter = Predicate { file ->
              if (file.toString().endsWith(".jar")) {
                sources.add(createZipSource(file) { result.add(ProjectLibraryEntry(antTargetFile, libraryData, file, it)) })
                false
              }
              else {
                true
              }
            })
    sources.sort()
    // path in class log - empty, do not reorder, doesn't matter
    buildJars(listOf(Triple(antTargetFile, "", sources)), false)
    result
  }
}

private fun packInternalUtilities(context: BuildContext) {
  val sources = ArrayList<Path>()
  for (file in context.project.libraryCollection.findLibrary("JUnit4")!!.getFiles(JpsOrderRootType.COMPILED)) {
    sources.add(file.toPath())
  }
  sources.add(context.paths.buildOutputDir.resolve("internal/internalUtilities.jar"))
  packInternalUtilities(context.paths.artifactDir.resolve("internalUtilities.zip"), sources)
}

private fun createBuildBrokenPluginListTask(context: BuildContext): ForkJoinTask<*>? {
  val buildString = context.fullBuildNumber
  val targetFile = context.paths.tempDir.resolve("brokenPlugins.db")
  return createSkippableTask(spanBuilder("build broken plugin list")
                               .setAttribute("buildNumber", buildString)
                               .setAttribute("path", targetFile.toString()), BuildOptions.BROKEN_PLUGINS_LIST_STEP, context) {
    buildBrokenPlugins(targetFile, buildString, context.options.isInDevelopmentMode)
    if (Files.exists(targetFile)) {
      context.addDistFile(java.util.Map.entry(targetFile, "bin"))
    }
  }
}

private fun buildThirdPartyLibrariesList(projectStructureMapping: ProjectStructureMapping, context: BuildContext): ForkJoinTask<*>? {
  return createSkippableTask(spanBuilder("generate table of licenses for used third-party libraries"),
                             BuildOptions.THIRD_PARTY_LIBRARIES_LIST_STEP, context) {
    val generator = LibraryLicensesListGenerator.create(project = context.project,
                                                        licensesList = context.productProperties.allLibraryLicenses,
                                                        usedModulesNames = projectStructureMapping.includedModules)
    generator.generateHtml(getThirdPartyLibrariesHtmlFilePath(context))
    generator.generateJson(getThirdPartyLibrariesJsonFilePath(context))
  }
}

private fun satisfiesBundlingRequirements(plugin: PluginLayout,
                                          osFamily: OsFamily?,
                                          arch: JvmArchitecture?,
                                          context: BuildContext): Boolean {
  val bundlingRestrictions = plugin.bundlingRestrictions
  if (bundlingRestrictions.includeInEapOnly && !context.applicationInfo.isEAP) {
    return false
  }

  if (osFamily == null && bundlingRestrictions.supportedOs != OsFamily.ALL) {
    return false
  }
  else if (osFamily != null &&
           (bundlingRestrictions.supportedOs == OsFamily.ALL || !bundlingRestrictions.supportedOs.contains(osFamily))) {
    return false
  }
  else if (arch == null && bundlingRestrictions.supportedArch != JvmArchitecture.ALL) {
    return false
  }
  else {
    return arch == null || bundlingRestrictions.supportedArch.contains(arch)
  }
}

/**
 * @see [[build/plugins-autoupload.txt]] for the specification.
 *
 * @return predicate to test if the given plugin should be auto-published
 */
private fun loadPluginAutoPublishList(buildContext: BuildContext): Predicate<PluginLayout> {
  val file = getPluginAutoUploadFile(buildContext.paths.communityHomeDir)
  val config = readPluginAutoUploadFile(file)

  val productCode = buildContext.applicationInfo.productCode
  return Predicate<PluginLayout> { plugin ->
    val mainModuleName = plugin.mainModule

    val includeInAllProducts = config.contains(mainModuleName)
    val includeInProduct = config.contains("+$productCode:$mainModuleName")
    val excludedFromProduct = config.contains("-$productCode:$mainModuleName")

    if (includeInProduct && (excludedFromProduct || includeInAllProducts)) {
      buildContext.messages.error("Unsupported rules combination: " + config.filter {
        it == mainModuleName || it.endsWith(":$mainModuleName")
      })
    }

    !excludedFromProduct && (includeInAllProducts || includeInProduct)
  }
}

private fun buildKeymapPlugins(targetDir: Path, context: BuildContext): ForkJoinTask<List<Pair<Path, ByteArray>>> {
  val keymapDir = context.paths.communityHomeDir.communityRoot.resolve("platform/platform-resources/src/keymaps")
  return buildKeymapPlugins(context.buildNumber, targetDir, keymapDir)
}

suspend fun layoutDistribution(layout: BaseLayout,
                               targetDirectory: Path,
                               copyFiles: Boolean,
                               moduleOutputPatcher: ModuleOutputPatcher,
                               moduleJars: MultiMap<String, String>,
                               context: BuildContext): List<DistributionFileEntry> {
  if (copyFiles) {
    checkModuleExcludes(layout.moduleExcludes, context)
  }

  // patchers must be executed _before_ pack because patcher patches module output
  if (copyFiles && layout is PluginLayout && !layout.patchers.isEmpty()) {
    val patchers = layout.patchers
    spanBuilder("execute custom patchers").setAttribute("count", patchers.size.toLong()).useWithScope {
      for (patcher in patchers) {
        patcher.accept(moduleOutputPatcher, context)
      }
    }
  }

  return coroutineScope {
    val tasks = ArrayList<Deferred<Collection<DistributionFileEntry>>>(3)
    tasks.add(async {
      spanBuilder("pack").useWithScope2 {
        val actualModuleJars = TreeMap<String, MutableList<String>>()
        for (entry in moduleJars.entrySet()) {
          val modules = entry.value
          val jarPath = getActualModuleJarPath(entry.key, modules, layout.explicitlySetJarPaths, context)
          actualModuleJars.computeIfAbsent(jarPath) { mutableListOf() }.addAll(modules)
        }
        withContext(Dispatchers.IO) {
          pack(actualModuleJars, targetDirectory.resolve("lib"), layout, moduleOutputPatcher, !copyFiles, context)
        }
      }
    })

    if (copyFiles && (!layout.resourcePaths.isEmpty() || (layout is PluginLayout && !layout.resourceGenerators.isEmpty()))) {
      tasks.add(async(Dispatchers.IO) {
        spanBuilder("pack additional resources").useWithScope2 {
          layoutAdditionalResources(layout, context, targetDirectory)
          emptyList()
        }
      })
    }

    if (!layout.includedArtifacts.isEmpty()) {
      tasks.add(async(Dispatchers.IO) {
        spanBuilder("pack artifacts").useWithScope2 { layoutArtifacts(layout, context, copyFiles, targetDirectory) }
      })
    }
    tasks
  }.flatMap { it.getCompleted() }
}

private fun layoutAdditionalResources(layout: BaseLayout, context: BuildContext, targetDirectory: Path) {
  for (resourceData in layout.resourcePaths) {
    val source = basePath(context, resourceData.moduleName).resolve(resourceData.resourcePath).normalize()
    var target = targetDirectory.resolve(resourceData.relativeOutputPath).normalize()
    if (resourceData.packToZip) {
      if (Files.isDirectory(source)) {
        // do not compress - doesn't make sense as it is a part of distribution
        zip(target, mapOf(source to ""), compress = false)
      }
      else {
        target = target.resolve(source.fileName)
        Compressor.Zip(target).use { it.addFile(target.fileName.toString(), source) }
      }
    }
    else {
      if (Files.isRegularFile(source)) {
        copyFileToDir(source, target)
      }
      else {
        copyDir(source, target)
      }
    }
  }

  if (layout !is PluginLayout) {
    return
  }

  val resourceGenerators = layout.resourceGenerators
  if (!resourceGenerators.isEmpty()) {
    spanBuilder("generate and pack resources").useWithScope {
      for (item in resourceGenerators) {
        val resourceFile = item.apply(targetDirectory, context) ?: continue
        if (Files.isRegularFile(resourceFile)) {
          copyFileToDir(resourceFile, targetDirectory)
        }
        else {
          copyDir(resourceFile, targetDirectory)
        }
      }
    }
  }
}

private fun layoutArtifacts(layout: BaseLayout,
                            context: BuildContext,
                            copyFiles: Boolean,
                            targetDirectory: Path): Collection<DistributionFileEntry> {
  val span = Span.current()
  val entries = ArrayList<DistributionFileEntry>()
  for (entry in layout.includedArtifacts.entries) {
    val artifactName = entry.key
    val relativePath = entry.value
    span.addEvent("include artifact", Attributes.of(AttributeKey.stringKey("artifactName"), artifactName))
    val artifact = JpsArtifactService.getInstance().getArtifacts(context.project).find { it.name == artifactName }
                   ?: throw IllegalArgumentException("Cannot find artifact $artifactName in the project")
    var artifactFile: Path
    if (artifact.outputFilePath == artifact.outputPath) {
      val source = Path.of(artifact.outputPath!!)
      artifactFile = targetDirectory.resolve("lib").resolve(relativePath)
      if (copyFiles) {
        copyDir(source, targetDirectory.resolve("lib").resolve(relativePath))
      }
    }
    else {
      val source = Path.of(artifact.outputFilePath!!)
      artifactFile = targetDirectory.resolve("lib").resolve(relativePath).resolve(source.fileName)
      if (copyFiles) {
        copyFile(source, artifactFile)
      }
    }
    addArtifactMapping(artifact, entries, artifactFile)
  }
  return entries
}

private fun addArtifactMapping(artifact: JpsArtifact, entries: MutableCollection<DistributionFileEntry>, artifactFile: Path) {
  val rootElement = artifact.rootElement
  for (element in rootElement.children) {
    if (element is JpsProductionModuleOutputPackagingElement) {
      entries.add(ModuleOutputEntry(artifactFile, element.moduleReference.moduleName, 0, "artifact: ${artifact.name}"))
    }
    else if (element is JpsTestModuleOutputPackagingElement) {
      entries.add(ModuleTestOutputEntry(artifactFile, element.moduleReference.moduleName))
    }
    else if (element is JpsLibraryFilesPackagingElement) {
      val library = element.libraryReference.resolve()
      val parentReference = library!!.createReference().parentReference
      if (parentReference is JpsModuleReference) {
        entries.add(ModuleLibraryFileEntry(artifactFile, parentReference.moduleName, null, 0))
      }
      else {
        val libraryData = ProjectLibraryData(library.name, LibraryPackMode.MERGED)
        entries.add(ProjectLibraryEntry(artifactFile, libraryData, null, 0))
      }
    }
  }
}

private fun checkModuleExcludes(moduleExcludes: MultiMap<String, String>, context: BuildContext) {
  for ((module, value) in moduleExcludes.entrySet()) {
    for (pattern in value) {
      if (Files.notExists(context.getModuleOutputDir(context.findRequiredModule(module)))) {
        context.messages.error("There are excludes defined for module `$module`, but the module wasn't compiled;" +
                               " most probably it means that \'$module\' isn\'t include into the product distribution " +
                               "so it makes no sense to define excludes for it.")
      }
    }
  }
}