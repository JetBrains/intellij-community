// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import com.intellij.platform.ijent.community.buildConstants.isMultiRoutingFileSystemEnabledForProduct
import com.intellij.util.io.Compressor
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.DistFile
import org.jetbrains.intellij.build.InMemoryDistFileContent
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.LinuxLibcImpl
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PLATFORM_LOADER_JAR
import org.jetbrains.intellij.build.PluginBundlingRestrictions
import org.jetbrains.intellij.build.PluginDistribution
import org.jetbrains.intellij.build.ScrambleTool
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.buildSearchableOptions
import org.jetbrains.intellij.build.classPath.PluginBuildDescriptor
import org.jetbrains.intellij.build.classPath.generateClassPathByLayoutReport
import org.jetbrains.intellij.build.classPath.generateCoreClasspathFromPlugins
import org.jetbrains.intellij.build.executeStep
import org.jetbrains.intellij.build.fus.createStatisticsRecorderBundledMetadataProviderTask
import org.jetbrains.intellij.build.impl.moduleRepository.generateRuntimeModuleRepositoryForDistribution
import org.jetbrains.intellij.build.impl.plugins.BundledPluginsBuildResult
import org.jetbrains.intellij.build.impl.plugins.buildBundledPlugins
import org.jetbrains.intellij.build.impl.plugins.buildBundledPluginsForAllPlatforms
import org.jetbrains.intellij.build.impl.plugins.buildNonBundledPlugins
import org.jetbrains.intellij.build.impl.plugins.scrambleAlreadyLaidOutPlugins
import org.jetbrains.intellij.build.impl.plugins.writeBundledPluginInfoAfterScramble
import org.jetbrains.intellij.build.impl.projectStructureMapping.ContentReport
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.buildJarContentReport
import org.jetbrains.intellij.build.impl.projectStructureMapping.getIncludedModules
import org.jetbrains.intellij.build.injectAppInfo
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFileToDir
import org.jetbrains.intellij.build.io.writeNewZipWithoutIndex
import org.jetbrains.intellij.build.io.zip
import org.jetbrains.intellij.build.productLayout.ProductModulesLayout
import org.jetbrains.intellij.build.productLayout.createPluginLayoutSet
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
  val platformLayout = state.platformLayout
  validateModuleStructure(platformLayout, context)
  context.productProperties.validateLayout(platformLayout, context)
  createBuildBrokenPluginListJob(context)

  val productRunner = suspendingLazy("distribution product runner") {
    context.createProductRunner()
  }
  if (context.productProperties.buildDocAuthoringAssets && !context.isStepSkipped(BuildOptions.DOC_AUTHORING_ASSETS_STEP)) {
    launch(CoroutineName("build authoring assets")) {
      buildAdditionalAuthoringArtifacts(productRunner.await(), context)
    }
  }

  val traceContext = Context.current().asContextElement()
  val contentReport = coroutineScope {
    // must be completed before plugin building
    val searchableOptionSet = context.executeStep(spanBuilder("build searchable options index"), BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP) {
      buildSearchableOptions(productRunner.await(), context)
    }

    val pluginLayouts = getPluginLayoutsByJpsModuleNames(modules = context.getBundledPluginModules(), productLayout = context.productProperties.productLayout)
    val moduleOutputPatcher = ModuleOutputPatcher()

    // Standalone publish zips for co-scramble plugins would be unscrambled; these plugins are bundled-only.
    validateCoScramblePluginsAreNotPublished(state.pluginsToPublish)

    val coScramblePluginLayouts = if (!isUpdateFromSources &&
                                      context.productProperties.scrambleMainJar &&
                                      !context.isStepSkipped(BuildOptions.SCRAMBLING_STEP) &&
                                      context.proprietaryBuildTools.scrambleTool != null) {
      collectCoScramblePluginLayouts(pluginLayouts)
    }
    else {
      emptySet()
    }
    if (coScramblePluginLayouts.isEmpty()) {
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
        val compressPluginArchive = !isUpdateFromSources && context.options.compressZipFiles
        buildNonBundledPlugins(
          pluginsToPublish = state.pluginsToPublish,
          compressPluginArchive = compressPluginArchive,
          buildPlatformLibJob = buildPlatformJob,
          state = state,
          searchableOptionSet = searchableOptionSet,
          isUpdateFromSources = isUpdateFromSources,
          descriptorCacheContainer = platformLayout.descriptorCacheContainer,
          context = context,
        )
      }

      val bundledPluginsResult = buildBundledPluginsForAllPlatforms(
        state = state,
        pluginLayouts = pluginLayouts,
        isUpdateFromSources = isUpdateFromSources,
        buildPlatformJob = buildPlatformJob,
        searchableOptionSetDescriptor = searchableOptionSet,
        descriptorCacheContainer = platformLayout.descriptorCacheContainer,
        context = context,
      )

      val platformItems = buildPlatformJob.await()
      val bundledPluginItems = bundledPluginsResult.descriptors
      context.bootClassPathJarNames = generateCoreClassPath(platformLayout, context, platformItems, bundledPluginItems)

      ContentReport(platform = platformItems, bundledPlugins = bundledPluginItems, nonBundledPlugins = buildNonBundledPlugins.await())
    }
    else {
      val additionalPluginsDeferred = async(CoroutineName("build additional plugins")) {
        copyAdditionalPlugins(pluginDir = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY), context = context)
      }
      // Lay out only co-scramble plugins and their explicit scramble-classpath closure before platform scramble.
      val coScramblePluginLayoutJob: Deferred<BundledPluginsBuildResult> = async(traceContext + CoroutineName("lay out co-scramble plugins")) {
        buildBundledPluginsForAllPlatforms(
          state = state,
          pluginLayouts = coScramblePluginLayouts,
          isUpdateFromSources = isUpdateFromSources,
          // never await platform here (would deadlock); per-plugin scramble runs after platform scramble below
          buildPlatformJob = CompletableDeferred(emptyList()),
          searchableOptionSetDescriptor = searchableOptionSet,
          descriptorCacheContainer = platformLayout.descriptorCacheContainer,
          context = context,
          layoutOnly = true,
          includeAdditionalPlugins = false,
        )
      }

      val coScrambleEntriesDeferred: Deferred<List<ScrambleTool.CoScrambleEntry>> = async(traceContext + CoroutineName("collect co-scramble entries")) {
        collectCoScrambleEntries(coScramblePluginLayoutJob.await().descriptors)
      }

      val buildPlatformJob: Deferred<List<DistributionFileEntry>> = async(traceContext + CoroutineName("build platform lib")) {
        spanBuilder("build platform lib").use {
          buildPlatform(
            moduleOutputPatcher = moduleOutputPatcher,
            state = state,
            searchableOptionSet = searchableOptionSet,
            context = context,
            isUpdateFromSources = isUpdateFromSources,
            coScrambleEntriesProvider = { coScrambleEntriesDeferred.await() },
            classpathDirsProvider = { collectAllPluginClasspathDirs(coScramblePluginLayoutJob.await().descriptors) },
          )
        }
      }

      val buildNonBundledPlugins = async(CoroutineName("build non-bundled plugins")) {
        val compressPluginArchive = !isUpdateFromSources && context.options.compressZipFiles
        buildNonBundledPlugins(
          pluginsToPublish = state.pluginsToPublish,
          compressPluginArchive = compressPluginArchive,
          buildPlatformLibJob = buildPlatformJob,
          state = state,
          searchableOptionSet = searchableOptionSet,
          isUpdateFromSources = isUpdateFromSources,
          descriptorCacheContainer = platformLayout.descriptorCacheContainer,
          context = context,
        )
      }

      val coScramblePluginsResult = coScramblePluginLayoutJob.await()
      val platformItems = buildPlatformJob.await()
      val remainingPluginLayouts = pluginLayouts.asSequence()
        .filter { !coScramblePluginLayouts.contains(it) }
        .toCollection(LinkedHashSet())
      val remainingPluginsResult = buildBundledPluginsForAllPlatforms(
        state = state,
        pluginLayouts = remainingPluginLayouts,
        isUpdateFromSources = false,
        // remaining plugins are laid out after platform scramble; per-plugin scramble runs below
        buildPlatformJob = CompletableDeferred(emptyList()),
        searchableOptionSetDescriptor = searchableOptionSet,
        descriptorCacheContainer = platformLayout.descriptorCacheContainer,
        context = context,
        layoutOnly = true,
        includeAdditionalPlugins = false,
      )
      val bundledPluginItems = orderBundledPluginDescriptors(coScramblePluginsResult.descriptors + remainingPluginsResult.descriptors)
      // Per-plugin scramble for non-co-scramble plugins runs after platform scramble.
      scrambleAlreadyLaidOutPlugins(
        descriptors = bundledPluginItems,
        state = state,
        platformEntries = platformItems,
        context = context,
      )
      // Write plugin classpath/info now that descriptors reflect the scrambled layout.
      writeBundledPluginInfoAfterScramble(
        state = state,
        isUpdateFromSources = false,
        descriptors = bundledPluginItems,
        additionalPlugins = additionalPluginsDeferred.await(),
        descriptorCacheContainer = platformLayout.descriptorCacheContainer,
        context = context,
      )

      context.bootClassPathJarNames = generateCoreClassPath(platformLayout, context, platformItems, bundledPluginItems)

      ContentReport(platform = platformItems, bundledPlugins = bundledPluginItems, nonBundledPlugins = buildNonBundledPlugins.await())
    }
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
          generateRuntimeModuleRepositoryForDistribution(contentReport, context, platformLayout)
        }
      }
    }
  }
  contentReport
}

private suspend fun generateCoreClassPath(
  platformLayout: PlatformLayout,
  context: BuildContext,
  platformDistribution: List<DistributionFileEntry>,
  bundledPluginsDistribution: List<PluginBuildDescriptor>,
): List<String> {
  if (context.useModularLoader) {
    return listOf(PLATFORM_LOADER_JAR)
  }

  val libDir = context.paths.distAllDir.resolve("lib")
  val result = ArrayList<String>()
  generateClassPathByLayoutReport(
    libDir = libDir,
    entries = platformDistribution,
    skipNioFs = isMultiRoutingFileSystemEnabledForProduct(context.productProperties.platformPrefix)
  )
    .mapTo(result) { libDir.relativize(it).toString() }
  val pluginsDir = context.paths.distAllDir.resolve("plugins")
  generateCoreClasspathFromPlugins(platformLayout, bundledPluginsDistribution, context).mapTo(result) { pluginsDir.resolve(it).toString() }
  return result
}

@VisibleForTesting
suspend fun buildPlatform(
  moduleOutputPatcher: ModuleOutputPatcher,
  state: DistributionBuilderState,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  isUpdateFromSources: Boolean,
  context: BuildContext,
  coScrambleEntriesProvider: (suspend () -> List<ScrambleTool.CoScrambleEntry>)? = null,
  classpathDirsProvider: (suspend () -> List<Path>)? = null,
): List<DistributionFileEntry> {
  val distributionFileEntries = buildLib(
    moduleOutputPatcher = moduleOutputPatcher,
    platform = state.platformLayout,
    searchableOptionSetDescriptor = searchableOptionSet,
    context = context,
  )
  if (!isUpdateFromSources && context.productProperties.scrambleMainJar) {
    val tool = context.proprietaryBuildTools.scrambleTool
    if (tool == null) {
      Span.current().addEvent("skip scrambling because `scrambleTool` isn't defined")
    }
    else {
      val coScrambleEntries: List<ScrambleTool.CoScrambleEntry>
      val classpathDirs: List<Path>
      if (context.isStepSkipped(BuildOptions.SCRAMBLING_STEP)) {
        coScrambleEntries = emptyList()
        classpathDirs = emptyList()
      }
      else {
        coScrambleEntries = coScrambleEntriesProvider?.invoke() ?: emptyList()
        classpathDirs = classpathDirsProvider?.invoke() ?: emptyList()
      }
      tool.scramble(
        platformLayout = state.platformLayout,
        platformContent = distributionFileEntries,
        coScrambleEntries = coScrambleEntries,
        classpathDirs = classpathDirs,
        context = context,
      )
    }
  }
  return distributionFileEntries
}

fun collectCoScrambleEntries(descriptors: List<PluginBuildDescriptor>): List<ScrambleTool.CoScrambleEntry> {
  if (descriptors.isEmpty()) return emptyList()
  val result = ArrayList<ScrambleTool.CoScrambleEntry>(descriptors.size)
  for (descriptor in descriptors) {
    val layout = descriptor.layout
    if (!layout.scrambleWithPlatform) continue
    for (jarRelative in layout.pathsToScramble) {
      result.add(ScrambleTool.CoScrambleEntry(
        jarFile = descriptor.dir.resolve(jarRelative),
        pluginLayout = layout,
        pluginDir = descriptor.dir,
      ))
    }
  }
  return result
}

fun collectCoScramblePluginLayouts(pluginLayouts: Collection<PluginLayout>): Set<PluginLayout> {
  if (pluginLayouts.none { it.scrambleWithPlatform }) return emptySet()

  val layoutsByMainModule = LinkedHashMap<String, PluginLayout>(pluginLayouts.size)
  for (layout in pluginLayouts) {
    layoutsByMainModule.putIfAbsent(layout.mainModule, layout)
  }

  val result = LinkedHashSet<PluginLayout>()
  val queue = ArrayDeque<PluginLayout>()
  for (layout in pluginLayouts) {
    if (layout.scrambleWithPlatform && result.add(layout)) {
      queue.addLast(layout)
    }
  }

  while (!queue.isEmpty()) {
    val layout = queue.removeFirst()
    for (entry in layout.scrambleClasspathPlugins) {
      val dependency = layoutsByMainModule.get(entry.pluginMainModuleName) ?: error(
        "Plugin '${layout.directoryName}' is configured with scrambleWithPlatform() and references " +
        "scrambleClasspathPlugin('${entry.pluginMainModuleName}'), but that plugin is not bundled in this product"
      )
      if (result.add(dependency)) {
        queue.addLast(dependency)
      }
    }
  }

  return result
}

/**
 * Co-scramble plugins are valid only as bundled plugins. A standalone publish zip is built through
 * the per-plugin path, where [PluginLayout.scrambleWithPlatform] layouts are intentionally rejected.
 */
fun validateCoScramblePluginsAreNotPublished(pluginsToPublish: Collection<PluginLayout>) {
  for (layout in pluginsToPublish) {
    check(!layout.scrambleWithPlatform) {
      "Plugin '${layout.directoryName}' is configured with scrambleWithPlatform() but is also configured to be published. " +
      "Co-scramble plugins are bundled-only."
    }
  }
}

/**
 * Walks every laid-out plugin's `lib/` directory and returns every directory containing jars
 * (e.g. `lib`, `lib/modules`, plugin-specific subdirs like `lib/frontend-split`). Pass to
 * [ScrambleTool.scramble]'s `classpathDirs` so the run can resolve any cross-plugin reference
 * during trim/obfuscate.
 */
fun collectAllPluginClasspathDirs(descriptors: List<PluginBuildDescriptor>): List<Path> {
  if (descriptors.isEmpty()) return emptyList()
  val result = LinkedHashSet<Path>()
  for (descriptor in descriptors) {
    val libDir = descriptor.dir.resolve("lib")
    if (!Files.isDirectory(libDir)) continue
    Files.walk(libDir).use { stream ->
      stream
        .filter { Files.isDirectory(it) }
        .sorted(compareBy<Path> { it.toString() })
        .forEach(result::add)
    }
  }
  return result.toList()
}

private fun orderBundledPluginDescriptors(descriptors: List<PluginBuildDescriptor>): List<PluginBuildDescriptor> {
  if (descriptors.size < 2) return descriptors
  val distributionOrder = HashMap<Pair<OsFamily?, JvmArchitecture?>, Int>(SUPPORTED_DISTRIBUTIONS.size + 1)
  distributionOrder.put(Pair(null, null), 0)
  for ((index, distribution) in SUPPORTED_DISTRIBUTIONS.withIndex()) {
    distributionOrder.put(Pair(distribution.os, distribution.arch), index + 1)
  }
  return descriptors.sortedWith(
    compareBy<PluginBuildDescriptor> { distributionOrder.get(Pair(it.os, it.arch)) ?: Int.MAX_VALUE }
      .thenBy { it.layout.mainModule }
  )
}

@VisibleForTesting
suspend fun testBuildBundledPluginsForAllPlatforms(
  state: DistributionBuilderState,
  pluginLayouts: Set<PluginLayout>,
  buildPlatformJob: Deferred<List<DistributionFileEntry>>,
  descriptorCacheContainer: DescriptorCacheContainer,
  context: BuildContext,
  includeAdditionalPlugins: Boolean = true,
): List<DistFile> {
  buildBundledPluginsForAllPlatforms(
    state = state,
    context = context,
    buildPlatformJob = buildPlatformJob,
    pluginLayouts = pluginLayouts,
    isUpdateFromSources = false,
    searchableOptionSetDescriptor = null,
    descriptorCacheContainer = descriptorCacheContainer,
    includeAdditionalPlugins = includeAdditionalPlugins,
  )
  return context.getDistFiles(os = null, arch = null, libcImpl = null).filter { it.relativePath == PLUGIN_CLASSPATH }
}

/**
 * Lays out the given bundled plugins (no scrambling) so the caller can invoke [buildPlatform] with
 * `coScrambleEntriesProvider` / `classpathDirsProvider` populated from [collectCoScrambleEntries]
 * and [collectAllPluginClasspathDirs]. Per-plugin scramble for the laid-out plugins runs later via
 * [testScrambleAlreadyLaidOutPlugins].
 */
@VisibleForTesting
suspend fun testLayoutBundledPlugins(
  state: DistributionBuilderState,
  pluginLayouts: Set<PluginLayout>,
  descriptorCacheContainer: DescriptorCacheContainer,
  context: BuildContext,
  includeAdditionalPlugins: Boolean = true,
): BundledPluginsBuildResult {
  return buildBundledPluginsForAllPlatforms(
    state = state,
    pluginLayouts = pluginLayouts,
    isUpdateFromSources = false,
    // never await platform here (would deadlock)
    buildPlatformJob = CompletableDeferred(emptyList()),
    searchableOptionSetDescriptor = null,
    descriptorCacheContainer = descriptorCacheContainer,
    context = context,
    layoutOnly = true,
    includeAdditionalPlugins = includeAdditionalPlugins,
  )
}

/**
 * Per-plugin scramble pass for plugins that were laid out earlier via [testLayoutBundledPlugins],
 * followed by writing plugin-classpath info (so [BuildContext.getDistFiles] reflects it). Co-scramble
 * plugins ([PluginLayout.scrambleWithPlatform]) are skipped during scramble — they were already
 * scrambled in the platform ZKM run.
 */
@VisibleForTesting
suspend fun testScrambleAlreadyLaidOutPlugins(
  descriptors: List<PluginBuildDescriptor>,
  state: DistributionBuilderState,
  platformEntries: List<DistributionFileEntry>,
  additionalPlugins: List<Pair<Path, List<Path>>>?,
  descriptorCacheContainer: DescriptorCacheContainer,
  context: BuildContext,
) {
  scrambleAlreadyLaidOutPlugins(
    descriptors = descriptors,
    state = state,
    platformEntries = platformEntries,
    context = context,
  )
  writeBundledPluginInfoAfterScramble(
    state = state,
    isUpdateFromSources = false,
    descriptors = descriptors,
    additionalPlugins = additionalPlugins,
    descriptorCacheContainer = descriptorCacheContainer,
    context = context,
  )
}


/**
 * Validates module structure to ensure all module dependencies are included.
 */
fun validateModuleStructure(platform: PlatformLayout, context: BuildContext) {
  if (context.options.validateModuleStructure) {
    ModuleStructureValidator(context = context, allProductModules = platform.includedModules).validate()
  }
}

suspend fun buildBundledPluginsAsStandaloneTask(
  state: DistributionBuilderState,
  plugins: Collection<PluginLayout>,
  searchableOptionSetDescriptor: SearchableOptionSetDescriptor?,
  platformContent: List<DistributionFileEntry>,
  context: BuildContext,
) {
  buildBundledPlugins(
    state = state,
    plugins = plugins,
    isUpdateFromSources = false,
    buildPlatformJob = CompletableDeferred(platformContent),
    searchableOptionSet = searchableOptionSetDescriptor,
    descriptorCacheContainer = state.platformLayout.descriptorCacheContainer,
    context = context,
  )
}

suspend fun copyAdditionalPlugins(pluginDir: Path, context: BuildContext): List<Pair<Path, List<Path>>>? {
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

internal const val PLUGINS_DIRECTORY = "plugins"
internal const val LIB_DIRECTORY = "lib"

const val PLUGIN_CLASSPATH: String = "$PLUGINS_DIRECTORY/plugin-classpath.txt"

internal val PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE: Comparator<PluginLayout> = compareBy { it.mainModule }

@VisibleForTesting
class PluginRepositorySpec(
  @JvmField val pluginZip: Path,
  /**
   * Content of plugin.xml. It is used to read meta-information (e.g., version, name) and dependencies.
   * So, we don't need to use proper (say, after scrambling) content here.
   */
  @JvmField val pluginXml: ByteArray,
)

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
    layouts.retainAll { it.bundlingRestrictions.includeInDistribution != PluginDistribution.CROSS_PLATFORM_DIST_ONLY }
    for (layout in layouts) {
      check(result.add(layout)) {
        "Plugin layout for module $moduleName is already added (duplicated module name?)"
      }
    }
  }
  return result
}

/**
 * Collects executable file patterns from bundled plugins for a specific platform distribution.
 * Returns patterns relative to distribution root (e.g., "plugins/plugin-name/bin/script.sh").
 */
internal fun collectPluginExecutablePatterns(
  context: BuildContext,
  os: OsFamily,
  arch: JvmArchitecture,
  libc: LibcImpl,
): Sequence<String> {
  val productLayout = context.productProperties.productLayout
  val bundledPluginLayouts = getPluginLayoutsByJpsModuleNames(
    modules = productLayout.bundledPluginModules,
    productLayout = productLayout
  )

  val platformDistribution = SupportedDistribution(os, arch, libc)
  return bundledPluginLayouts.asSequence()
    .flatMap { plugin ->
      val patterns = plugin.executablePatterns[platformDistribution] ?: persistentListOf()
      patterns.asSequence().map { pattern ->
        "plugins/${plugin.directoryName}/$pattern"
      }
    }
}

private fun basePath(moduleName: String, outputProvider: ModuleOutputProvider): Path {
  return Path.of(JpsPathUtil.urlToPath(outputProvider.findRequiredModule(moduleName).contentRootsList.urls.first()))
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
    targetDir = targetDirectory,
    platform = platform,
    searchableOptionSet = searchableOptionSetDescriptor,
    copyFiles = true,
    context = context,
  )
  context.proprietaryBuildTools.scrambleTool?.validatePlatformLayout(platform.includedModules, context)
  return libDirMappings
}

internal suspend fun layoutPlatformDistribution(
  moduleOutputPatcher: ModuleOutputPatcher,
  targetDir: Path,
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
          val module = context.outputProvider.findRequiredModule(moduleName)
          val relativePath = "com/intellij/openapi/application/ApplicationNamesInfo.class"
          val sourceBytes = context.outputProvider.readFileContentFromModuleOutput(module, relativePath) ?: error("app info not found")
          val patchedBytes = injectAppInfo(inFileBytes = sourceBytes, newFieldValue = context.appInfoXml)
          moduleOutputPatcher.patchModuleOutput(moduleName = moduleName, path = relativePath, content = patchedBytes)
        }
      }
    }
  }

  return spanBuilder("layout lib")
    .setAttribute("path", targetDir.toString())
    .use {
      layoutDistribution(
        layout = platform,
        platformLayout = platform,
        targetDir = targetDir,
        copyFiles = copyFiles,
        moduleOutputPatcher = moduleOutputPatcher,
        includedModules = platform.includedModules,
        searchableOptionSet = searchableOptionSet,
        cachedDescriptorWriterProvider = null,
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
  val sourceFileContent = context.outputProvider.readFileContentFromModuleOutput(module = context.outputProvider.findRequiredModule(moduleName), relativePath = relativePath)
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

private fun CoroutineScope.createBuildThirdPartyLibraryListJob(entries: Sequence<DistributionFileEntry>, context: BuildContext) {
  createSkippableJob(
    spanBuilder("generate table of licenses for used third-party libraries"),
    BuildOptions.THIRD_PARTY_LIBRARIES_LIST_STEP, context
  ) {
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

internal fun satisfiesBundlingRequirements(plugin: PluginLayout, osFamily: OsFamily?, arch: JvmArchitecture?, context: BuildContext): Boolean {
  if (context.options.bundledPluginDirectoriesToSkip.contains(plugin.directoryName)) {
    return false
  }

  val bundlingRestrictions = plugin.bundlingRestrictions
  if (bundlingRestrictions == PluginBundlingRestrictions.MARKETPLACE) {
    return false
  }

  if (bundlingRestrictions.includeInDistribution == PluginDistribution.CROSS_PLATFORM_DIST_ONLY) {
    return false
  }

  if (context.options.useReleaseCycleRelatedBundlingRestrictionsForContentReport) {
    val isNightly = context.isNightlyBuild
    val isEap = context.applicationInfo.isEAP

    val distributionCondition = when (bundlingRestrictions.includeInDistribution) {
      PluginDistribution.ALL -> true
      PluginDistribution.NOT_FOR_RELEASE -> isNightly || isEap
      PluginDistribution.NOT_FOR_PUBLIC_BUILDS -> isNightly
      PluginDistribution.CROSS_PLATFORM_DIST_ONLY -> false
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

internal suspend fun layoutDistribution(
  layout: BaseLayout,
  platformLayout: PlatformLayout,
  targetDir: Path,
  copyFiles: Boolean,
  moduleOutputPatcher: ModuleOutputPatcher,
  includedModules: Collection<ModuleItem>,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  cachedDescriptorWriterProvider: ScopedCachedDescriptorContainer?,
  context: BuildContext,
): Pair<List<DistributionFileEntry>, Path> {
  if (copyFiles) {
    withContext(Dispatchers.IO) {
      Files.createDirectories(targetDir)

      if (!layout.moduleExcludes.isEmpty()) {
        launch(CoroutineName("check module excludes")) {
          checkModuleExcludes(layout.moduleExcludes, context.outputProvider)
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
    val outputDir = targetDir.resolve(LIB_DIRECTORY)
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
          descriptorCache = cachedDescriptorWriterProvider,
          context = context,
        )
      }
    })

    if (copyFiles &&
        !context.options.skipCustomResourceGenerators &&
        (layout.resourcePaths.isNotEmpty() || layout is PluginLayout && !layout.resourceGenerators.isEmpty())) {
      tasks.add(async(Dispatchers.IO + CoroutineName("pack additional resources")) {
        spanBuilder("pack additional resources").use {
          layoutAdditionalResources(layout, targetDir, context)
          emptyList()
        }
      })
    }

    tasks
  }.awaitAll().flatten()

  return entries to targetDir
}

private fun layoutResourcePaths(layout: BaseLayout, targetDirectory: Path, overwrite: Boolean, outputProvider: ModuleOutputProvider) {
  for (resourceData in layout.resourcePaths) {
    val source = basePath(resourceData.moduleName, outputProvider).resolve(resourceData.resourcePath).normalize()
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

private suspend fun layoutAdditionalResources(layout: BaseLayout, targetDirectory: Path, context: BuildContext) {
  // quick fix for a very annoying FileAlreadyExistsException in CLion dev build
  val overwrite = ("intellij.clion.radler" == (layout as? PluginLayout)?.mainModule)
  layoutResourcePaths(layout = layout, targetDirectory = targetDirectory, overwrite = overwrite, outputProvider = context.outputProvider)
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

private fun checkModuleExcludes(moduleExcludes: Map<String, List<String>>, outputProvider: ModuleOutputProvider) {
  for (module in moduleExcludes.keys) {
    check(outputProvider.getModuleOutputRoots(outputProvider.findRequiredModule(module)).all(Files::exists)) {
      "There are excludes defined for module '${module}', but the module wasn't compiled;" +
      " most probably it means that '${module}' isn't included in the product distribution," +
      " so it doesn't make sense to define excludes for it."
    }
  }
}
