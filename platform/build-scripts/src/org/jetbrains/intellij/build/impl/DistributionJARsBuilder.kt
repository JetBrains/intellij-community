// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.ijent.community.buildConstants.isMultiRoutingFileSystemEnabledForProduct
import com.intellij.util.io.Compressor
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
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PLATFORM_LOADER_JAR
import org.jetbrains.intellij.build.PluginBuildDescriptor
import org.jetbrains.intellij.build.PluginBundlingRestrictions
import org.jetbrains.intellij.build.PluginDistribution
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.antToRegex
import org.jetbrains.intellij.build.buildSearchableOptions
import org.jetbrains.intellij.build.executeStep
import org.jetbrains.intellij.build.fus.createStatisticsRecorderBundledMetadataProviderTask
import org.jetbrains.intellij.build.generateClassPathByLayoutReport
import org.jetbrains.intellij.build.generatePluginClassPath
import org.jetbrains.intellij.build.generatePluginClassPathFromPrebuiltPluginFiles
import org.jetbrains.intellij.build.impl.plugins.doBuildNonBundledPlugins
import org.jetbrains.intellij.build.impl.projectStructureMapping.ContentReport
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleTestOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.buildJarContentReport
import org.jetbrains.intellij.build.impl.projectStructureMapping.getIncludedModules
import org.jetbrains.intellij.build.injectAppInfo
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFile
import org.jetbrains.intellij.build.io.copyFileToDir
import org.jetbrains.intellij.build.io.writeNewZipWithoutIndex
import org.jetbrains.intellij.build.io.zip
import org.jetbrains.intellij.build.productLayout.ProductModulesLayout
import org.jetbrains.intellij.build.productLayout.createPluginLayoutSet
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
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
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

    val pluginLayouts = getPluginLayoutsByJpsModuleNames(modules = context.getBundledPluginModules(), productLayout = context.productProperties.productLayout)
    val moduleOutputPatcher = ModuleOutputPatcher()
    val buildPlatformJob: Deferred<List<DistributionFileEntry>> = async(traceContext + CoroutineName("build platform lib")) {
      spanBuilder("build platform lib").use {
        buildPlatform(
          moduleOutputPatcher = moduleOutputPatcher,
          state = state,
          searchableOptionSet = searchableOptionSet,
          context = context,
          isUpdateFromSources = isUpdateFromSources,
        )
      }
    }

    val buildNonBundledPlugins = async(CoroutineName("build non-bundled plugins")) {
      context.executeStep(spanBuilder("build non-bundled plugins").setAttribute("count", state.pluginsToPublish.size.toLong()), BuildOptions.NON_BUNDLED_PLUGINS_STEP) {
        doBuildNonBundledPlugins(
          isUpdateFromSources = isUpdateFromSources,
          pluginsToPublish = state.pluginsToPublish,
          compressPluginArchive = !isUpdateFromSources && context.options.compressZipFiles,
          buildPlatformLibJob = buildPlatformJob,
          state = state,
          searchableOptionSet = searchableOptionSet,
          context = context,
        )
      } ?: emptyList()
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

    ContentReport(platform = buildPlatformJob.await(), bundledPlugins = bundledPluginItems, nonBundledPlugins = buildNonBundledPlugins.await())
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
          generateRuntimeModuleRepositoryForDistribution(contentReport.bundled(), context)
        }
      }
    }
  }
  contentReport
}

@VisibleForTesting
suspend fun buildPlatform(
  moduleOutputPatcher: ModuleOutputPatcher,
  state: DistributionBuilderState,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  context: BuildContext,
  isUpdateFromSources: Boolean,
): List<DistributionFileEntry> {
  val distributionFileEntries = buildLib(
    moduleOutputPatcher = moduleOutputPatcher,
    platform = state.platform,
    searchableOptionSetDescriptor = searchableOptionSet,
    context = context,
  )
  if (!isUpdateFromSources && context.productProperties.scrambleMainJar) {
    val tool = context.proprietaryBuildTools.scrambleTool
    if (tool == null) {
      Span.current().addEvent("skip scrambling because `scrambleTool` isn't defined")
    }
    else {
      tool.scramble(platform = state.platform, platformFileEntries = distributionFileEntries, context = context)
    }
  }
  context.bootClassPathJarNames = if (context.useModularLoader) {
    listOf(PLATFORM_LOADER_JAR)
  }
  else {
    val libDir = context.paths.distAllDir.resolve("lib")
    generateClassPathByLayoutReport(
      libDir = libDir,
      entries = distributionFileEntries,
      skipNioFs = isMultiRoutingFileSystemEnabledForProduct(context.productProperties.platformPrefix)
    ).map { libDir.relativize(it).toString() }
  }
  return distributionFileEntries
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
      state = state,
      plugins = pluginLayouts,
      isUpdateFromSources = isUpdateFromSources,
      buildPlatformJob = buildPlatformJob,
      searchableOptionSet = searchableOptionSetDescriptor,
      moduleOutputPatcher = moduleOutputPatcher,
      context = context,
    )
  }

  val additionalDeferred = async(CoroutineName("build additional plugins")) {
    copyAdditionalPlugins(context = context, pluginDir = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY))
  }

  val pluginDirs = getPluginDirs(context, isUpdateFromSources)
  val specificDeferred = async(CoroutineName("build OS-specific bundled plugins")) {
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
    platformLayout = state.platform,
    context = context,
  )
  common + specific.values.flatten()
}

private fun writePluginInfo(
  moduleOutputPatcher: ModuleOutputPatcher,
  pluginDirs: List<Pair<SupportedDistribution, Path>>,
  common: List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>,
  specific: Map<SupportedDistribution, List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>>,
  additional: List<Pair<Path, List<Path>>>?,
  platformLayout: PlatformLayout,
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
    writePluginClassPathHeader(out = out, isJarOnly = true, pluginCount = pluginCount, platformLayout = platformLayout, context = context)
    out.write(commonClassPath)
    additionalClassPath?.let { out.write(it) }
    specificClasspath?.let { out.write(it) }
    out.close()

    context.addDistFile(DistFile(
      content = InMemoryDistFileContent(byteOut.toByteArray()),
      relativePath = PLUGIN_CLASSPATH,
      os = supportedDist.os,
      libcImpl = supportedDist.libcImpl,
      arch = supportedDist.arch,
    ))
  }
}

/**
 * Validates module structure to be ensure all module dependencies are included.
 */
fun validateModuleStructure(platform: PlatformLayout, context: BuildContext) {
  if (context.options.validateModuleStructure) {
    ModuleStructureValidator(context = context, allProductModules = platform.includedModules).validate()
  }
}

private fun getPluginDirs(context: BuildContext, isUpdateFromSources: Boolean): List<Pair<SupportedDistribution, Path>> {
  if (isUpdateFromSources) {
    return listOf(SupportedDistribution(OsFamily.currentOs, JvmArchitecture.currentJvmArch, LibcImpl.current(OsFamily.currentOs)) to
                    context.paths.distAllDir.resolve(PLUGINS_DIRECTORY))
  }
  else {
    return SUPPORTED_DISTRIBUTIONS.map {
      it to getOsAndArchSpecificDistDirectory(osFamily = it.os, arch = it.arch, libc = it.libcImpl, context = context).resolve(PLUGINS_DIRECTORY)
    }
  }
}

suspend fun buildBundledPlugins(
  state: DistributionBuilderState,
  plugins: Collection<PluginLayout>,
  searchableOptionSetDescriptor: SearchableOptionSetDescriptor?,
  context: BuildContext,
) {
  doBuildBundledPlugins(
    state = state,
    plugins = plugins,
    isUpdateFromSources = false,
    buildPlatformJob = null,
    searchableOptionSet = searchableOptionSetDescriptor,
    moduleOutputPatcher = ModuleOutputPatcher(),
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
    .block { span ->
      val pluginsToBundle = ArrayList<PluginLayout>(plugins.size)
      plugins.filterTo(pluginsToBundle) { satisfiesBundlingRequirements(plugin = it, osFamily = null, arch = null, context) }
      span.setAttribute("satisfiableCount", pluginsToBundle.size.toLong())

      // doesn't make sense to require passing here a list with a stable order (unnecessary complication, sorting by main module is enough)
      pluginsToBundle.sortWith(PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE)
      val targetDir = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY)
      val platformSpecificPluginDirs = getPluginDirs(context, isUpdateFromSources)
      val entries = buildPlugins(
        moduleOutputPatcher = moduleOutputPatcher,
        plugins = pluginsToBundle,
        os = null,
        targetDir = targetDir,
        state = state,
        context = context,
        buildPlatformJob = buildPlatformJob,
        searchableOptionSet = searchableOptionSet,
      ) { layout, _ ->
        if (layout.hasPlatformSpecificResources) {
          buildPlatformSpecificPluginResources(plugin = layout, targetDirs = platformSpecificPluginDirs, context = context)
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
          satisfiesBundlingRequirements(plugin = it, osFamily = os, arch = arch, context = context)
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
              buildPlugins(
                moduleOutputPatcher = moduleOutputPatcher,
                plugins = osSpecificPlugins,
                os = os,
                targetDir = targetDir,
                state = state,
                context = context,
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


fun nonBundledPluginsStageDir(context: BuildContext): Path {
  return context.paths.tempDir.resolve("non-bundled-plugins-${context.applicationInfo.productCode}")
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
      if (satisfiesBundlingRequirements(plugin = plugin, osFamily = null, arch = null, context = context)) {
        val targetDirectory = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY).resolve(plugin.directoryName)
        entries.add(PluginBuildDescriptor(
          dir = targetDirectory,
          os = null,
          layout = plugin,
          moduleNames = emptyList()
        ) to layoutDistribution(
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
        spanBuilder("plugin").setAttribute("path", context.paths.buildOutputDir.relativize(pluginDir).toString()).use {
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

internal const val PLUGINS_DIRECTORY = "plugins"
internal const val LIB_DIRECTORY = "lib"

const val PLUGIN_CLASSPATH: String = "$PLUGINS_DIRECTORY/plugin-classpath.txt"

internal val PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE: Comparator<PluginLayout> = compareBy { it.mainModule }

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
    if (toPublish && layouts.size == 2 && layouts.get(0).bundlingRestrictions != layouts.get(1).bundlingRestrictions) {
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

private fun basePath(buildContext: BuildContext, moduleName: String): Path {
  return Path.of(JpsPathUtil.urlToPath(buildContext.findRequiredModule(moduleName).contentRootsList.urls.first()))
}

suspend fun buildLib(
  moduleOutputPatcher: ModuleOutputPatcher,
  platform: PlatformLayout,
  searchableOptionSetDescriptor: SearchableOptionSetDescriptor?,
  context: BuildContext,
): List<DistributionFileEntry> {
  val targetDirectory = context.paths.distAllDir
  val libDirMappings = layoutPlatformDistribution(
    moduleOutputPatcher = moduleOutputPatcher,
    targetDirectory = targetDirectory,
    platform = platform,
    searchableOptionSet = searchableOptionSetDescriptor,
    copyFiles = true,
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
          moduleOutputPatcher.patchModuleOutput(moduleName = moduleName, path = relativePath, content = patchedBytes)
        }
      }
    }
  }

  return spanBuilder("layout lib")
    .setAttribute("path", targetDirectory.toString())
    .use {
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

private suspend fun patchKeyMapWithAltClickReassignedToMultipleCarets(moduleOutputPatcher: ModuleOutputPatcher, context: BuildContext) {
  if (!context.productProperties.reassignAltClickToMultipleCarets) {
    return
  }

  val moduleName = "intellij.platform.resources"
  val relativePath = $$"keymaps/$default.xml"
  val sourceFileContent = context.readFileContentFromModuleOutput(module = context.findRequiredModule(moduleName), relativePath = relativePath)
                          ?: error("Not found '$relativePath' in module $moduleName output")
  var text = String(sourceFileContent, StandardCharsets.UTF_8)
  text = text.replace("<mouse-shortcut keystroke=\"alt button1\"/>", "<mouse-shortcut keystroke=\"to be alt shift button1\"/>")
  text = text.replace("<mouse-shortcut keystroke=\"alt shift button1\"/>", "<mouse-shortcut keystroke=\"alt button1\"/>")
  text = text.replace("<mouse-shortcut keystroke=\"to be alt shift button1\"/>", "<mouse-shortcut keystroke=\"alt shift button1\"/>")
  moduleOutputPatcher.patchModuleOutput(moduleName, relativePath, text)
}

fun getOsAndArchSpecificDistDirectory(osFamily: OsFamily, arch: JvmArchitecture, libc: LibcImpl, context: BuildContext): Path {
  return context.paths.buildOutputDir.resolve(
    "dist.${osFamily.distSuffix}.${arch.name}${
      if (libc == LinuxLibcImpl.MUSL) {
        "-musl"
      }
      else {
        ""
      }
    }"
  )
}

private suspend fun checkOutputOfPluginModules(
  mainPluginModule: String,
  includedModules: Collection<ModuleItem>,
  moduleExcludes: Map<String, List<String>>,
  context: BuildContext,
) {
  for (module in includedModules.asSequence().map { it.moduleName }.distinct()) {
    if (
      module == "intellij.java.guiForms.rt" ||
      !containsFileInOutput(module, "com/intellij/uiDesigner/core/GridLayoutManager.class", moduleExcludes.get(module) ?: emptyList(), context)
    ) {
      @Suppress("GrazieInspection")
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
      context = context,
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
  val overwrite = ("intellij.clion.radler" == (layout as? PluginLayout)?.mainModule)
  layoutResourcePaths(layout = layout, context = context, targetDirectory = targetDirectory, overwrite = overwrite)
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
          sourcePath.copyToRecursively(target = artifactPath, followLinks = false)
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
      entries.add(
        ModuleOutputEntry(
          path = artifactFile,
          owner = ModuleItem(moduleName = element.moduleReference.moduleName, relativeOutputFile = "DO NOT USE ME", reason = null),
          size = 0,
          hash = 0,
          relativeOutputFile = "",
          reason = "artifact: ${artifact.name}",
        )
      )
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
          owner = null,
        ))
      }
      else {
        val libraryData = ProjectLibraryData(libraryName = library.name, reason = "<- artifact ${artifact.name}")
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

private suspend fun checkModuleExcludes(moduleExcludes: Map<String, List<String>>, context: CompilationContext) {
  for (module in moduleExcludes.keys) {
    check(context.getModuleOutputRoots(context.findRequiredModule(module)).all(Files::exists)) {
      "There are excludes defined for module '${module}', but the module wasn't compiled;" +
      " most probably it means that '${module}' isn't included in the product distribution," +
      " so it doesn't make sense to define excludes for it."
    }
  }
}