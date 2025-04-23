// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import com.dynatrace.hash4j.hashing.HashStream64
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.util.PathUtilRt
import com.intellij.util.io.URLUtil
import com.intellij.util.io.sanitizeFileName
import com.jetbrains.util.filetype.FileType
import com.jetbrains.util.filetype.FileTypeDetector.DetectFileType
import io.opentelemetry.api.common.AttributeKey
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.DirSource
import org.jetbrains.intellij.build.FrontendModuleFilter
import org.jetbrains.intellij.build.InMemoryContentSource
import org.jetbrains.intellij.build.JarPackagerDependencyHelper
import org.jetbrains.intellij.build.LazySource
import org.jetbrains.intellij.build.NativeFileHandler
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.SignNativeFileMode
import org.jetbrains.intellij.build.Source
import org.jetbrains.intellij.build.UTIL_8_JAR
import org.jetbrains.intellij.build.UTIL_JAR
import org.jetbrains.intellij.build.ZipSource
import org.jetbrains.intellij.build.buildJar
import org.jetbrains.intellij.build.checkForNoDiskSpace
import org.jetbrains.intellij.build.computeHashForModuleOutput
import org.jetbrains.intellij.build.computeModuleSourcesByContent
import org.jetbrains.intellij.build.defaultLibrarySourcesNamesFilter
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_CLIENT_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_JAR
import org.jetbrains.intellij.build.impl.projectStructureMapping.CustomAssetEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.intellij.build.inferModuleSources
import org.jetbrains.intellij.build.jarCache.JarCacheManager
import org.jetbrains.intellij.build.jarCache.NonCachingJarCacheManager
import org.jetbrains.intellij.build.jarCache.SourceBuilder
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.ByteBuffer
import java.nio.file.FileSystemException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.attribute.BasicFileAttributes
import java.util.TreeMap
import kotlin.io.path.invariantSeparatorsPathString

private val JAR_NAME_WITH_VERSION_PATTERN = "(.*)-\\d+(?:\\.\\d+)*\\.jar*".toPattern()

private val libsUsedInJps = setOf(
  "ASM",
  "netty-buffer",
  "netty-codec-http",
  "netty-codec-protobuf",
  "netty-handler-proxy",
  "gson",
  "Log4J",
  "slf4j-api",
  "slf4j-jdk14",
  // see getBuildProcessApplicationClasspath - used in JPS
  "lz4-java",
  "jna",
  "maven-resolver-provider",
  "OroMatcher",
  "jgoodies-forms",
  "jgoodies-common",
  // see ArtifactRepositoryManager.getClassesFromDependencies
  "plexus-utils",
  "http-client",
  "commons-codec",
  "commons-logging",
  "commons-lang3",
  "kotlin-stdlib",
  "fastutil-min",
)

private val presignedLibNames = setOf(
  "pty4j", "jna", "sqlite-native", "async-profiler", "jetbrains.skiko.awt.runtime.all"
)

private fun isLibPreSigned(library: JpsLibrary) = presignedLibNames.contains(library.name)

private val notImportantKotlinLibs = setOf(
  "kotlinx-collections-immutable",
  "kotlinx-coroutines-guava",
  "kotlinx-datetime-jvm",
  "kotlinx-html-jvm",
)

const val rdJarName: String = "rd.jar"

// must be sorted
private val predefinedMergeRules = listOf<Pair<String, (String, FrontendModuleFilter) -> Boolean>>(
  "groovy.jar" to { it, _ -> it.startsWith("org.codehaus.groovy:") },
  "jsch-agent.jar" to { it, _ -> it.startsWith("jsch-agent") },
  rdJarName to { it, _ -> it.startsWith("rd-") },
  // separate file to use in Gradle Daemon classpath
  "opentelemetry.jar" to { it, _ -> it == "opentelemetry" || it == "opentelemetry-semconv" || it.startsWith("opentelemetry-exporter-otlp") },
  "bouncy-castle.jar" to { it, _ -> it.startsWith("bouncy-castle-") },
  PRODUCT_JAR to { name, filter -> (name.startsWith("License") || name.startsWith("jetbrains.codeWithMe.lobby.server.")) && !filter.isProjectLibraryIncluded(name) },
  PRODUCT_CLIENT_JAR to { name, filter -> (name.startsWith("License") || name.startsWith("jetbrains.codeWithMe.lobby.server.")) && filter.isProjectLibraryIncluded(name) },
  // see ClassPathUtil.getUtilClassPath
  UTIL_8_JAR to { it, _ ->
    libsUsedInJps.contains(it) ||
    (it.startsWith("kotlinx-") && !notImportantKotlinLibs.contains(it)) ||
    it == "kotlin-reflect"
  },

  // used in an external process - see `ConsoleProcessListFetcher.getConsoleProcessCount`
  UTIL_JAR to { it, _ -> it == "pty4j" || it == "jvm-native-trusted-roots" || it == "caffeine" },
)

internal fun getLibraryFileName(library: JpsLibrary): String {
  val name = library.name
  if (!name.startsWith('#')) {
    return name
  }

  val roots = library.getRoots(JpsOrderRootType.COMPILED)
  check(roots.size == 1) {
    "Non-single entry module library $name: ${roots.joinToString { it.url }}"
  }
  return PathUtilRt.getFileName(roots.first().url.removeSuffix(URLUtil.JAR_SEPARATOR))
}

class JarPackager private constructor(
  private val outDir: Path,
  private val context: BuildContext,
  private val platformLayout: PlatformLayout?,
  private val isRootDir: Boolean,
  @JvmField internal val moduleOutputPatcher: ModuleOutputPatcher,
) {
  private val assets = LinkedHashMap<Path, AssetDescriptor>()

  private val libToMetadata = HashMap<JpsLibrary, ProjectLibraryData>()
  private val copiedFiles = HashMap<CopiedForKey, CopiedFor>()

  private val helper = (context as BuildContextImpl).jarPackagerDependencyHelper

  companion object {
    suspend fun pack(
      includedModules: Collection<ModuleItem>,
      outputDir: Path,
      context: BuildContext,
    ) {
      val packager = JarPackager(outDir = outputDir, context = context, platformLayout = null, isRootDir = false, moduleOutputPatcher = ModuleOutputPatcher())
      packager.computeModuleSources(includedModules = includedModules, layout = null, searchableOptionSet = null)
      buildJars(
        assets = packager.assets.values,
        layout = null,
        cache = if (context is BuildContextImpl) context.jarCacheManager else NonCachingJarCacheManager,
        context = context,
        isCodesignEnabled = false,
        useCacheAsTargetFile = context.options.isUnpackedDist,
        dryRun = false,
        helper = packager.helper
      )
    }

    suspend fun pack(
      includedModules: Collection<ModuleItem>,
      outputDir: Path,
      isRootDir: Boolean,
      isCodesignEnabled: Boolean = true,
      layout: BaseLayout,
      platformLayout: PlatformLayout?,
      moduleOutputPatcher: ModuleOutputPatcher,
      dryRun: Boolean,
      searchableOptionSet: SearchableOptionSetDescriptor? = null,
      context: BuildContext,
    ): Collection<DistributionFileEntry> {
      val packager = JarPackager(outDir = outputDir, context = context, platformLayout = platformLayout, isRootDir = isRootDir, moduleOutputPatcher = moduleOutputPatcher)
      packager.computeModuleSources(includedModules = includedModules, layout = layout, searchableOptionSet = searchableOptionSet)
      packager.computeModuleCustomLibrarySources(layout)

      val frontendModuleFilter = context.getFrontendModuleFilter()
      val libraryToMerge = packager.computeProjectLibrariesSources(outDir = outputDir, layout = layout, copiedFiles = packager.copiedFiles, frontendModuleFilter = frontendModuleFilter)
      if (isRootDir) {
        for ((jarName, predicate) in predefinedMergeRules) {
          packager.mergeLibsByPredicate(jarName = jarName, libraryToMerge = libraryToMerge, outputDir = outputDir, predicate = predicate, frontendModuleFilter = frontendModuleFilter)
        }

        if (!libraryToMerge.isEmpty()) {
          val clientLibraries = libraryToMerge.filterKeys { frontendModuleFilter.isProjectLibraryIncluded(it.name) }
          if (clientLibraries.isNotEmpty()) {
            packager.projectLibsToSourceWithMappings(uberJarFile = outputDir.resolve(PlatformJarNames.LIB_CLIENT_JAR), libraryToMerge = clientLibraries)
          }

          val nonClientLibraries = libraryToMerge.filterKeys { !frontendModuleFilter.isProjectLibraryIncluded(it.name) }
          if (nonClientLibraries.isNotEmpty()) {
            packager.projectLibsToSourceWithMappings(uberJarFile = outputDir.resolve(PlatformJarNames.LIB_JAR), libraryToMerge = nonClientLibraries)
          }
        }
      }
      else if (!libraryToMerge.isEmpty()) {
        val mainJarName = (layout as PluginLayout).getMainJarName()
        check(includedModules.any { it.relativeOutputFile == mainJarName })
        packager.projectLibsToSourceWithMappings(uberJarFile = outputDir.resolve(mainJarName), libraryToMerge = libraryToMerge)
      }

      val cacheManager = if (dryRun || context !is BuildContextImpl) NonCachingJarCacheManager else context.jarCacheManager
      val buildAssetResult = buildJars(
        assets = packager.assets.values,
        layout = layout,
        cache = cacheManager,
        context = context,
        isCodesignEnabled = isCodesignEnabled,
        useCacheAsTargetFile = !dryRun && context.options.isUnpackedDist,
        dryRun = dryRun,
        helper = packager.helper,
      )

      return coroutineScope {
        if (buildAssetResult.sourceToNativeFiles.isNotEmpty()) {
          launch(CoroutineName("pack native presigned files")) {
            packNativePresignedFiles(nativeFiles = buildAssetResult.sourceToNativeFiles, dryRun = dryRun, context = context, toRelativePath = { libName, fileName ->
              "lib/$libName/$fileName"
            })
          }
        }

        val list = mutableListOf<DistributionFileEntry>()
        val hasher = Hashing.xxh3_64().hashStream()
        for (item in packager.assets.values) {
          computeDistributionFileEntries(asset = item, hasher = hasher, list = list, dryRun = dryRun, buildAssetResult = buildAssetResult)
        }
        list
      }
    }
  }

  private suspend fun computeModuleSources(includedModules: Collection<ModuleItem>, layout: BaseLayout?, searchableOptionSet: SearchableOptionSetDescriptor?) {
    val addedModules = HashSet<String>()

    // First, check the content. This is done prior to everything else since we might configure a custom relativeOutputFile.
    if (layout is PluginLayout) {
      computeModuleSourcesByContent(helper = helper, context = context, layout = layout, addedModules = addedModules, jarPackager = this, searchableOptionSet = searchableOptionSet)
    }

    for (item in includedModules) {
      if (layout is PluginLayout && addedModules.contains(item.moduleName) && !item.relativeOutputFile.contains('/')) {
        check(item.relativeOutputFile == layout.getMainJarName()) {
          "Custom output path is not allowed for content modules ($item)"
        }
        continue
      }

      computeSourcesForModule(item, layout, searchableOptionSet)
      addedModules.add(item.moduleName)
    }

    if (layout !is PluginLayout || !layout.auto) {
      return
    }

    inferModuleSources(
      layout = layout,
      addedModules = addedModules,
      platformLayout = platformLayout!!,
      helper = helper,
      jarPackager = this,
      searchableOptionSet = searchableOptionSet,
      context = context,
    )
  }

  internal suspend fun computeSourcesForModule(item: ModuleItem, layout: BaseLayout?, searchableOptionSet: SearchableOptionSetDescriptor?) {
    val moduleName = item.moduleName
    val patchedContent = moduleOutputPatcher.getPatchedContent(moduleName)

    val module = context.findRequiredModule(moduleName)
    val useTestModuleOutput = helper.isTestPluginModule(moduleName, module)
    val moduleOutDir = context.getModuleOutputDir(module, forTests = useTestModuleOutput)
    val extraExcludes = layout?.moduleExcludes?.get(moduleName) ?: emptyList()

    val packToDir = context.options.isUnpackedDist &&
                    !item.relativeOutputFile.contains('/') &&
                    (patchedContent.isEmpty() || (patchedContent.size == 1 && patchedContent.containsKey("META-INF/plugin.xml"))) &&
                    extraExcludes.isEmpty()

    val outFile = outDir.resolve(item.relativeOutputFile)
    val asset = if (packToDir) {
      assets.computeIfAbsent(moduleOutDir) { file ->
        AssetDescriptor(isDir = true, file = file, relativePath = "")
      }
    }
    else {
      assets.computeIfAbsent(outFile) { file ->
        createAssetDescriptor(relativeOutputFile = item.relativeOutputFile, targetFile = file)
      }
    }

    val moduleSources = asset.includedModules.computeIfAbsent(item) { mutableListOf() }

    for (entry in patchedContent) {
      moduleSources.add(InMemoryContentSource(relativePath = entry.key, data = entry.value))
    }

    val jarAsset = lazy(LazyThreadSafetyMode.NONE) {
      if (packToDir) {
        getJarAsset(targetFile = outFile, relativeOutputFile = item.relativeOutputFile)
      }
      else {
        asset
      }
    }

    if (searchableOptionSet != null) {
      addSearchableOptionSources(layout = layout, moduleName = moduleName, module = module, sources = jarAsset.value.sources, searchableOptionSet = searchableOptionSet)
    }

    val excludes = if (extraExcludes.isEmpty()) {
      commonModuleExcludes
    }
    else {
      val fileSystem = FileSystems.getDefault()
      val result = ArrayList<PathMatcher>(commonModuleExcludes.size + extraExcludes.size)
      result.addAll(commonModuleExcludes)
      extraExcludes.mapTo(result) { fileSystem.getPathMatcher("glob:$it") }
      result
    }

    val source = createModuleSource(module, moduleOutDir, excludes)
    if (source != null) {
      moduleSources.add(source)
    }

    if (layout is PluginLayout && layout.mainModule == moduleName) {
      handleCustomAssets(layout, jarAsset)
    }

    if (layout != null && (layout !is PluginLayout || !layout.modulesWithExcludedModuleLibraries.contains(moduleName))) {
      computeSourcesForModuleLibs(
        item = item,
        layout = layout,
        module = module,
        copiedFiles = copiedFiles,
        asset = jarAsset,
        withTests = useTestModuleOutput
      )
    }
  }

  private suspend fun handleCustomAssets(layout: PluginLayout, jarAsset: Lazy<AssetDescriptor>) {
    for (customAsset in layout.customAssets) {
      if (customAsset.platformSpecific != null) {
        continue
      }

      val relativePath = customAsset.relativePath
      if (relativePath == null) {
        customAsset.getSources(context)?.let { jarAsset.value.sources.addAll(it) }
      }
      else {
        val targetFile = outDir.resolveSibling(relativePath)
        val assetDescriptor = AssetDescriptor(
          isDir = false,
          file = targetFile,
          relativePath = relativePath,
          useCacheAsTargetFile = false,
        )
        customAsset.getSources(context)?.let { assetDescriptor.sources.addAll(it) }
        val existing = assets.putIfAbsent(targetFile, assetDescriptor)
        require(existing == null) {
          "CustomAsset must be packed into separate target file (existing=$existing, new=$assetDescriptor)"
        }
      }
    }
  }

  private suspend fun addSearchableOptionSources(
    layout: BaseLayout?,
    moduleName: String,
    module: JpsModule,
    sources: MutableList<Source>,
    searchableOptionSet: SearchableOptionSetDescriptor
  ) {
    if (layout is PluginLayout) {
      if (moduleName == BUILT_IN_HELP_MODULE_NAME) {
        return
      }

      if (moduleName == layout.mainModule) {
        val pluginId = helper.getPluginIdByModule(module)
        sources.addAll(searchableOptionSet.createSourceByPlugin(pluginId))
      }
      else {
        // is it a product module?
        findFileInModuleSources(module, "$moduleName.xml")?.let {
          sources.addAll(searchableOptionSet.createSourceByModule(moduleName))
        }
      }
    }
    else if (moduleName == context.productProperties.applicationInfoModule) {
      sources.addAll(searchableOptionSet.createSourceByPlugin("com.intellij"))
    }
  }

  private fun computeSourcesForModuleLibs(
    item: ModuleItem,
    layout: BaseLayout,
    module: JpsModule,
    copiedFiles: MutableMap<CopiedForKey, CopiedFor>,
    asset: Lazy<AssetDescriptor>,
    withTests: Boolean
  ) {
    val moduleName = module.name
    val includeProjectLib = if (layout is PluginLayout) layout.auto else item.reason == ModuleIncludeReasons.PRODUCT_MODULES

    val excluded = if (layout is PluginLayout) (layout.excludedLibraries.get(moduleName) ?: emptyList()) + (layout.excludedLibraries.get(null) ?: emptyList()) else emptySet()
    for (element in helper.getLibraryDependencies(module, withTests = withTests)) {
      var projectLibraryData: ProjectLibraryData? = null
      val libRef = element.libraryReference
      if (libRef.parentReference !is JpsModuleReference) {
        val libName = libRef.libraryName
        if (includeProjectLib) {
          if (platformLayout!!.hasLibrary(libName) || layout.hasLibrary(libName)) {
            continue
          }

          if (helper.hasLibraryInDependencyChainOfModuleDependencies(dependentModule = module, libraryName = libName, siblings = layout.includedModules, withTests = withTests)) {
            continue
          }

          projectLibraryData = ProjectLibraryData(libraryName = libName, reason = "<- $moduleName")
        }
        else if (platformLayout != null && platformLayout.isLibraryAlwaysPackedIntoPlugin(libName)) {
          platformLayout.findProjectLibrary(libName)?.let {
            throw IllegalStateException("Library $libName must not be included into platform layout: $it")
          }

          if (layout.hasLibrary(libName)) {
            continue
          }

          projectLibraryData = ProjectLibraryData(libraryName = libName, reason = "<- $moduleName (always packed into plugin)")
        }
        else {
          continue
        }
      }

      val library = element.library ?: throw IllegalStateException("cannot find $libRef")
      val libraryName = getLibraryFileName(library)
      if (excluded.contains(libraryName) || alreadyHasLibrary(layout, libraryName)) {
        continue
      }

      if (item.reason == ModuleIncludeReasons.PRODUCT_MODULES) {
        packLibFilesIntoModuleJar(asset = asset.value, item = item, files = library.getPaths(JpsOrderRootType.COMPILED), projectLibraryData = projectLibraryData, library = library)
      }
      else {
        fun addLibrary(relativeOutputFile: String, files: List<Path>) {
          filesToSourceWithMapping(
            asset = getJarAsset(targetFile = outDir.resolve(relativeOutputFile), relativeOutputFile = relativeOutputFile),
            files = files,
            library = library,
            relativeOutputFile = relativeOutputFile,
            projectLibraryData = projectLibraryData,
          )
        }

        val targetFile = outDir.resolve(item.relativeOutputFile)
        val files = getLibraryFiles(library = library, copiedFiles = copiedFiles, targetFile = targetFile)
        if (layout is PluginLayout && item.relativeOutputFile == layout.getMainJarName()) {
          if (files.size > 1) {
            for (i in (files.size - 1) downTo 0) {
              val file = files[i]
              val fileName = file.fileName.toString()
              if (fileName.endsWith("-rt.jar") || fileName.startsWith("maven-")) {
                files.removeAt(i)
                addLibrary(relativeOutputFile = removeVersionFromJar(fileName), files = listOf(file))
              }
            }
          }

          addLibrary(relativeOutputFile = removeVersionFromJar(nameToJarFileName(getLibraryFileName(library))), files = files)
        }
        else {
          for (i in (files.size - 1) downTo 0) {
            val file = files[i]
            val fileName = file.fileName.toString()
            if (isSeparateJar(fileName)) {
              files.removeAt(i)
              addLibrary(relativeOutputFile = removeVersionFromJar(fileName), files = listOf(file))
            }
          }

          packLibFilesIntoModuleJar(asset = asset.value, item = item, files = files, projectLibraryData = projectLibraryData, library = library)
        }
      }
    }
  }

  private fun packLibFilesIntoModuleJar(
    asset: AssetDescriptor,
    item: ModuleItem,
    files: List<Path>,
    projectLibraryData: ProjectLibraryData?,
    library: JpsLibrary,
  ) {
    val libraryName = getLibraryFilename(library)
    for (file in files) {
      @Suppress("NAME_SHADOWING")
      asset.sources.add(
        ZipSource(
          file = file,
          distributionFileEntryProducer = { size, hash, targetFile ->
            if (projectLibraryData == null) {
              ModuleLibraryFileEntry(
                path = targetFile,
                moduleName = item.moduleName,
                libraryName = libraryName,
                libraryFile = file,
                size = size,
                hash = hash,
                relativeOutputFile = item.relativeOutputFile,
              )
            }
            else {
              ProjectLibraryEntry(path = targetFile, data = projectLibraryData, libraryFile = file, hash = hash, size = size, relativeOutputFile = item.relativeOutputFile)
            }
          },
          isPreSignedAndExtractedCandidate = isLibPreSigned(library),
          filter = ::defaultLibrarySourcesNamesFilter,
        )
      )
    }
  }

  private fun computeModuleCustomLibrarySources(layout: BaseLayout) {
    for (item in layout.includedModuleLibraries) {
      val library = context.findRequiredModule(item.moduleName).libraryCollection.libraries
                      .find { getLibraryFileName(it) == item.libraryName }
                    ?: throw IllegalArgumentException("Cannot find library ${item.libraryName} in '${item.moduleName}' module")

      var relativePath = item.relativeOutputPath
      val targetFile: Path
      if (relativePath.endsWith(".jar")) {
        targetFile = outDir.resolve(relativePath)
        if (!relativePath.contains('/')) {
          relativePath = ""
        }
      }
      else {
        val fileName = nameToJarFileName(item.libraryName)
        if (relativePath.isEmpty()) {
          targetFile = outDir.resolve(fileName)
        }
        else {
          targetFile = outDir.resolve(relativePath).resolve(fileName)
          relativePath += "/$fileName"
        }
      }

      filesToSourceWithMapping(
        asset = getJarAsset(targetFile = targetFile, relativeOutputFile = relativePath),
        files = getLibraryFiles(library = library, copiedFiles = copiedFiles, targetFile = targetFile),
        library = library,
        relativeOutputFile = relativePath,
        projectLibraryData = null,
      )
    }
  }

  private fun alreadyHasLibrary(layout: BaseLayout, libraryName: String): Boolean {
    return layout.includedModuleLibraries.any { it.libraryName == libraryName && !it.extraCopy }
  }

  private fun mergeLibsByPredicate(
    jarName: String,
    libraryToMerge: MutableMap<JpsLibrary, List<Path>>,
    outputDir: Path,
    predicate: (String, FrontendModuleFilter) -> Boolean,
    frontendModuleFilter: FrontendModuleFilter,
  ) {
    val result = LinkedHashMap<JpsLibrary, List<Path>>()
    val iterator = libraryToMerge.entries.iterator()
    while (iterator.hasNext()) {
      val (key, value) = iterator.next()
      if (predicate(key.name, frontendModuleFilter)) {
        iterator.remove()
        result.put(key, value)
      }
    }
    if (result.isEmpty()) {
      return
    }
    projectLibsToSourceWithMappings(uberJarFile = outputDir.resolve(jarName), libraryToMerge = result)
  }

  private fun projectLibsToSourceWithMappings(uberJarFile: Path, libraryToMerge: Map<JpsLibrary, List<Path>>) {
    val descriptor = getJarAsset(targetFile = uberJarFile, relativeOutputFile = "")
    for ((library, files) in libraryToMerge) {
      filesToSourceWithMapping(
        asset = descriptor,
        files = files,
        library = library,
        relativeOutputFile = null,
        projectLibraryData = libToMetadata.get(library) ?: throw IllegalStateException("Metadata not found for ${library.name}"),
      )
    }
  }

  private fun computeProjectLibrariesSources(
    outDir: Path,
    layout: BaseLayout,
    copiedFiles: MutableMap<CopiedForKey, CopiedFor>,
    frontendModuleFilter: FrontendModuleFilter
  ): MutableMap<JpsLibrary, List<Path>> {
    if (layout.includedProjectLibraries.isEmpty()) {
      return LinkedHashMap()
    }

    val projectLibs = layout.includedProjectLibraries.sortedBy { it.libraryName }
    val toMerge = LinkedHashMap<JpsLibrary, List<Path>>()
    for (libraryData in projectLibs) {
      val library = context.project.libraryCollection.findLibrary(libraryData.libraryName)
                    ?: throw IllegalArgumentException("Cannot find library ${libraryData.libraryName} in the project")
      libToMetadata.put(library, libraryData)
      val libName = library.name
      var packMode = libraryData.packMode
      if (packMode == LibraryPackMode.MERGED) {
        if (layout is PluginLayout) {
          throw IllegalStateException("PackMode.MERGED is deprecated for plugins, please check why the library is marked as MERGED (libName=$libName, plugin=${layout.mainModule})")
        }
        else if (!predefinedMergeRules.any { it.second(libName, frontendModuleFilter) } && !isLibraryMergeable(libName)) {
          packMode = LibraryPackMode.STANDALONE_MERGED
        }
      }

      val outPath = libraryData.outPath
      if (packMode == LibraryPackMode.MERGED && outPath == null) {
        toMerge.put(library, getLibraryFiles(library, copiedFiles, targetFile = null))
        continue
      }

      var libOutputDir = outDir
      if (outPath != null) {
        if (outPath.endsWith(".jar")) {
          val targetFile = outDir.resolve(outPath)
          filesToSourceWithMapping(
            asset = getJarAsset(targetFile = targetFile, relativeOutputFile = outPath),
            files = getLibraryFiles(library = library, copiedFiles = copiedFiles, targetFile = targetFile),
            library = library,
            relativeOutputFile = outPath,
            projectLibraryData = libraryData,
          )
          continue
        }

        libOutputDir = outDir.resolve(outPath)
      }

      fun addLibrary(targetFile: Path, relativeOutputFile: String, files: List<Path>) {
        filesToSourceWithMapping(
          asset = getJarAsset(targetFile = targetFile, relativeOutputFile = relativeOutputFile),
          files = files,
          library = library,
          relativeOutputFile = relativeOutputFile,
          projectLibraryData = libraryData,
        )
      }

      if (packMode == LibraryPackMode.STANDALONE_MERGED) {
        val targetFile = libOutputDir.resolve(nameToJarFileName(libName))
        val relativeOutputFile = if (outDir == libOutputDir) "" else outDir.relativize(targetFile).invariantSeparatorsPathString
        addLibrary(targetFile = targetFile, relativeOutputFile = relativeOutputFile, files = getLibraryFiles(library, copiedFiles, targetFile))
      }
      else {
        for (file in library.getPaths(JpsOrderRootType.COMPILED)) {
          var fileName = file.fileName.toString()
          if (packMode == LibraryPackMode.STANDALONE_SEPARATE_WITHOUT_VERSION_NAME) {
            fileName = removeVersionFromJar(fileName)
          }

          val targetFile = libOutputDir.resolve(fileName)
          val relativeOutputFile = if (outDir == libOutputDir) "" else outDir.relativize(targetFile).invariantSeparatorsPathString
          addLibrary(targetFile = targetFile, relativeOutputFile = relativeOutputFile, files = listOf(file))
        }
      }
    }
    return toMerge
  }

  private fun filesToSourceWithMapping(
    asset: AssetDescriptor,
    files: List<Path>,
    library: JpsLibrary,
    relativeOutputFile: String?,
    projectLibraryData: ProjectLibraryData?,
  ) {
    val libraryName = library.name
    val moduleName = (library.createReference().parentReference as? JpsModuleReference)?.moduleName
    if (moduleName == null && projectLibraryData == null) {
      throw IllegalStateException("Metadata not specified for $libraryName")
    }

    val sources = asset.sources
    val isPreSignedCandidate = isRootDir && isLibPreSigned(library)
    for (file in files) {
      sources.add(
        ZipSource(
          file = file,
          isPreSignedAndExtractedCandidate = isPreSignedCandidate,
          optimizeConfigId = libraryName.takeIf { isRootDir && libraryName == "jsvg" },
          distributionFileEntryProducer = { size, hash, targetFile ->
            if (moduleName == null) {
              ProjectLibraryEntry(
                path = targetFile,
                data = projectLibraryData ?: throw IllegalStateException("Metadata not specified for $libraryName"),
                libraryFile = file,
                hash = hash,
                size = size,
                relativeOutputFile = relativeOutputFile,
              )
            }
            else {
              ModuleLibraryFileEntry(
                path = targetFile,
                moduleName = moduleName,
                libraryName = getLibraryFilename(library),
                libraryFile = file,
                hash = hash,
                size = size,
                relativeOutputFile = relativeOutputFile,
              )
            }
          },
          filter = ::defaultLibrarySourcesNamesFilter,
        )
      )
    }
  }

  private fun getJarAsset(targetFile: Path, relativeOutputFile: String): AssetDescriptor {
    return assets.computeIfAbsent(targetFile) {
      createAssetDescriptor(targetFile = targetFile, relativeOutputFile = relativeOutputFile)
    }
  }
}

private val agentLibrariesNotForcedInSeparateJars = listOf(
  "ideformer",
  "code-agents",
  "code-prompt-agents"
)

private fun isSeparateJar(fileName: String): Boolean {
  return fileName.endsWith("-rt.jar") ||
         (fileName.contains("-agent") && agentLibrariesNotForcedInSeparateJars.none { fileName.contains(it) }) ||
         fileName.startsWith("maven-")
}

private data class AssetDescriptor(
  @JvmField val isDir: Boolean,
  @JvmField val file: Path,
  @JvmField val relativePath: String,
  @JvmField var effectiveFile: Path = file,
  @JvmField val useCacheAsTargetFile: Boolean = true,
) {
  // must be sorted - we use it as is for Jar Cache
  @JvmField
  val sources: MutableList<Source> = mutableListOf()

  // must be sorted - we use it as is for Jar Cache
  @JvmField
  val includedModules = Reference2ObjectLinkedOpenHashMap<ModuleItem, MutableList<Source>>()
}

private fun removeVersionFromJar(fileName: String): String {
  val matcher = JAR_NAME_WITH_VERSION_PATTERN.matcher(fileName)
  return if (matcher.matches()) "${matcher.group(1)}.jar" else fileName
}

private fun getLibraryFiles(library: JpsLibrary, copiedFiles: MutableMap<CopiedForKey, CopiedFor>, targetFile: Path?): MutableList<Path> {
  val files = library.getPaths(JpsOrderRootType.COMPILED)
  val iterator = files.iterator()
  while (iterator.hasNext()) {
    val file = iterator.next()
    // allow duplication if packed into the same target file
    val alreadyCopiedFor = copiedFiles.putIfAbsent(CopiedForKey(file, targetFile), CopiedFor(library, targetFile)) ?: continue
    if (alreadyCopiedFor.targetFile == targetFile) {
      iterator.remove()
    }
  }
  return files
}

private fun nameToJarFileName(name: String): String {
  return "${sanitizeFileName(name.lowercase(), replacement = "-")}.jar"
}

@Suppress("SpellCheckingInspection", "RedundantSuppression")
private val excludedFromMergeLibs = setOf(
  "async-profiler",
  "dexlib2", // android-only lib
  "intellij-test-discovery", // used as an agent
  "protobuf", // https://youtrack.jetbrains.com/issue/IDEA-268753
)

private fun isLibraryMergeable(libName: String): Boolean {
  return !excludedFromMergeLibs.contains(libName) &&
         !(libName.startsWith("kotlin-") && !libName.startsWith("kotlin-test-")) &&
         !libName.startsWith("kotlinc.") &&
         !libName.contains("-agent-") &&
         !libName.startsWith("rd-") &&
         !libName.contains("annotations", ignoreCase = true) &&
         !libName.startsWith("junit", ignoreCase = true) &&
         !libName.startsWith("cucumber-", ignoreCase = true) &&
         !libName.contains("groovy", ignoreCase = true)
}

internal val commonModuleExcludes: List<PathMatcher> = FileSystems.getDefault().let { fs ->
  listOf(
    fs.getPathMatcher("glob:**/icon-robots.txt"),
    fs.getPathMatcher("glob:icon-robots.txt"),
    fs.getPathMatcher("glob:.unmodified"),
    // compilation cache on TC
    fs.getPathMatcher("glob:.hash"),
    fs.getPathMatcher("glob:classpath.index"),
    fs.getPathMatcher("glob:module-info.class"),
  )
}

fun createModuleSourcesNamesFilter(excludes: List<PathMatcher>): (String) -> Boolean = { name ->
  val p = Path.of(name)
  excludes.none { it.matches(p) }
}

// null targetFile means main jar
private data class CopiedForKey(@JvmField val file: Path, @JvmField val targetFile: Path?)
private data class CopiedFor(@JvmField val library: JpsLibrary, @JvmField val targetFile: Path?)

private suspend fun buildJars(
  assets: Collection<AssetDescriptor>,
  cache: JarCacheManager,
  context: BuildContext,
  isCodesignEnabled: Boolean,
  useCacheAsTargetFile: Boolean,
  dryRun: Boolean,
  layout: BaseLayout?,
  helper: JarPackagerDependencyHelper
): BuildAssetResult {
  checkAssetUniqueness(assets)

  if (dryRun) {
    return emptyBuildJarsResult()
  }

  val list = withContext(Dispatchers.IO) {
    assets.map { asset ->
      async(CoroutineName("build jar for ${asset.relativePath}")) {
        buildAsset(
          asset = asset,
          isCodesignEnabled = isCodesignEnabled,
          context = context,
          cache = cache,
          useCacheAsTargetFile = useCacheAsTargetFile,
          layout = layout,
          helper = helper,
        )
      }
    }
  }

  val sourceToNativeFiles = TreeMap<ZipSource, List<String>>(compareBy { it.file.fileName.toString() })
  val sourceToMetadata = HashMap<Source, SizeAndHash>()

  for (deferred in list) {
    val item = deferred.getCompleted()
    sourceToNativeFiles.putAll(item.sourceToNativeFiles)
    sourceToMetadata.putAll(item.sourceToMetadata)
  }
  return BuildAssetResult(sourceToNativeFiles = sourceToNativeFiles.ifEmpty { emptyMap() }, sourceToMetadata = sourceToMetadata)
}

private data class SizeAndHash(@JvmField val size: Int, @JvmField val hash: Long)

private data class BuildAssetResult(
  @JvmField val sourceToNativeFiles: Map<ZipSource, List<String>>,
  @JvmField val sourceToMetadata: Map<Source, SizeAndHash>,
)

private suspend fun buildAsset(
  asset: AssetDescriptor,
  isCodesignEnabled: Boolean,
  context: BuildContext,
  cache: JarCacheManager,
  useCacheAsTargetFile: Boolean,
  layout: BaseLayout?,
  helper: JarPackagerDependencyHelper,
): BuildAssetResult {
  val includedModules = asset.includedModules
  if (asset.isDir) {
    val sourceToMetadata = HashMap<Source, SizeAndHash>()
    for (sources in includedModules.values) {
      for (source in sources) {
        if (source is DirSource) {
          sourceToMetadata.computeIfAbsent(source) {
            SizeAndHash(size = 0, hash = computeHashForModuleOutput(it as DirSource))
          }
        }
      }
    }
    return BuildAssetResult(sourceToNativeFiles = emptyMap(), sourceToMetadata = sourceToMetadata)
  }

  val sources = if (includedModules.isEmpty()) {
    asset.sources
  }
  else if (asset.sources.isEmpty() && includedModules.size == 1 && includedModules.values.first().size == 1) {
    listOf(includedModules.values.first().first())
  }
  else {
    val sources = ObjectLinkedOpenHashSet<Source>(asset.sources.size + includedModules.values.sumOf { it.size })
    sources.addAll(asset.sources)
    for (moduleSources in includedModules.values) {
      for (source in moduleSources) {
        val old = sources.get(source)
        require(old == null) {
          "Source is duplicated: new $source, old: $old"
        }

        sources.add(source)
      }
    }
    sources
  }

  if (sources.isEmpty()) {
    return emptyBuildJarsResult()
  }

  val nativeFileHandler = if (isCodesignEnabled) NativeFileHandlerImpl(context) else null
  val sourceToMetadata = HashMap<Source, SizeAndHash>(sources.size)

  val file = asset.file
  spanBuilder("build jar")
    .setAttribute("jar", file.toString())
    .setAttribute(AttributeKey.stringArrayKey("sources"), sources.map(Source::toString))
    .use { span ->
      asset.effectiveFile = cache.computeIfAbsent(
        sources = sources,
        targetFile = file,
        nativeFiles = nativeFileHandler?.sourceToNativeFiles,
        span = span,
        producer = object : SourceBuilder {
          override val useCacheAsTargetFile: Boolean
            get() = useCacheAsTargetFile && asset.useCacheAsTargetFile && !asset.relativePath.contains('/')

          override fun updateDigest(digest: HashStream64) {
            if (layout is PluginLayout) {
              digest.putString(layout.mainModule)
            }
            else {
              digest.putInt(0)
            }
          }

          override suspend fun produce(targetFile: Path) {
            buildJar(
              targetFile = targetFile,
              sources = sources,
              nativeFileHandler = nativeFileHandler,
              addDirEntries = includedModules.any { helper.isTestPluginModule(moduleName = it.key.moduleName, module = null) },
            )
          }

          override fun consumeInfo(source: Source, size: Int, hash: Long) {
            val old = sourceToMetadata.putIfAbsent(source, SizeAndHash(size = size, hash = hash))
            require(old == null) {
              "Source is duplicated: new $source, old: $old"
            }
          }
        },
      )
    }

  return BuildAssetResult(sourceToNativeFiles = nativeFileHandler?.sourceToNativeFiles ?: emptyMap(), sourceToMetadata = sourceToMetadata)
}

private fun emptyBuildJarsResult() = BuildAssetResult(sourceToNativeFiles = emptyMap(), sourceToMetadata = emptyMap())

private fun checkAssetUniqueness(assets: Collection<AssetDescriptor>) {
  val uniqueFiles = HashMap<Path, List<Source>>(assets.size)
  for (asset in assets) {
    val existing = uniqueFiles.putIfAbsent(asset.file, asset.sources)
    check(existing == null) {
      "File ${asset.file} is already associated." +
      "\nPrevious:\n  ${existing!!.joinToString(separator = "\n  ")}" +
      "\nCurrent:\n  ${asset.sources.joinToString(separator = "\n  ")}"
    }
  }
}

private class NativeFileHandlerImpl(private val context: BuildContext) : NativeFileHandler {
  override val sourceToNativeFiles = HashMap<ZipSource, List<String>>()

  override fun isNative(name: String): Boolean {
    @Suppress("SpellCheckingInspection", "RedundantSuppression")
    return isMacLibrary(name) ||
           name.endsWith(".exe") ||
           name.endsWith(".dll") ||
           name.endsWith("pty4j-unix-spawn-helper") ||
           name.endsWith("icudtl.dat")
  }

  override fun isCompatibleWithTargetPlatform(name: String): Boolean {
    if (!isNative(name)) {
      return true
    }
    return NativeFilesMatcher.isCompatibleWithTargetPlatform(name, context.options.targetOs, context.options.targetArch)
  }

  @Suppress("SpellCheckingInspection")
  override suspend fun sign(name: String, dataSupplier: () -> ByteBuffer): Path? {
    if (!context.isMacCodeSignEnabled || context.proprietaryBuildTools.signTool.signNativeFileMode != SignNativeFileMode.ENABLED) {
      return null
    }

    // we allow using .so for macOS binraries (binaries/macOS/libasyncProfiler.so), but removing obvious Linux binaries
    // (binaries/linux-aarch64/libasyncProfiler.so) to avoid detecting by binary content
    if (name.endsWith(".dll") || name.endsWith(".exe") || name.contains("/linux/") || name.contains("/linux-") || name.contains("icudtl.dat")) {
      return null
    }

    val data = dataSupplier()
    data.mark()
    val byteBufferChannel = ByteBufferChannel(data)
    if (byteBufferChannel.DetectFileType().first != FileType.MachO) {
      return null
    }

    data.reset()
    if (isSigned(byteBufferChannel, name)) {
      return null
    }

    data.reset()
    return signData(data, context)
  }
}

suspend fun buildJar(targetFile: Path, moduleNames: List<String>, context: BuildContext, dryRun: Boolean = false) {
  if (dryRun) {
    return
  }

  checkForNoDiskSpace(context) {
    buildJar(
      targetFile = targetFile,
      sources = moduleNames.mapNotNull { moduleName ->
        val module = context.findRequiredModule(moduleName)
        val output = context.getModuleOutputDir(module)
        createModuleSource(module = module, outputDir = output, excludes = commonModuleExcludes)
      },
    )
  }
}

private fun createModuleSource(module: JpsModule, outputDir: Path, excludes: List<PathMatcher>): Source? {
  val attributes = try {
    Files.readAttributes(outputDir, BasicFileAttributes::class.java)
  }
  catch (_: FileSystemException) {
    null
  }

  return when {
    attributes != null && attributes.isDirectory -> DirSource(dir = outputDir, excludes = excludes)
    attributes != null -> ZipSource(file = outputDir, distributionFileEntryProducer = null, filter = createModuleSourcesNamesFilter(excludes))
    module.sourceRoots.any { !it.rootType.isForTests } -> error("Module ${module.name} output does not exist: $outputDir")
    else -> null
  }
}

private fun createAssetDescriptor(relativeOutputFile: String, targetFile: Path): AssetDescriptor {
  return AssetDescriptor(isDir = false, file = targetFile, relativePath = relativeOutputFile)
}

private fun computeDistributionFileEntries(
  asset: AssetDescriptor,
  hasher: HashStream64,
  list: MutableList<DistributionFileEntry>,
  dryRun: Boolean,
  buildAssetResult: BuildAssetResult,
) {
  for ((module, sources) in asset.includedModules) {
    var size = 0
    hasher.reset()
    if (!dryRun) {
      for (source in sources) {
        val info = buildAssetResult.sourceToMetadata.get(source) ?: continue
        size += info.size
        hasher.putInt(size)
        hasher.putLong(info.hash)
      }
    }

    hasher.putInt(sources.size)

    val hash = hasher.asLong
    list.add(
      ModuleOutputEntry(
        path = asset.effectiveFile,
        moduleName = module.moduleName,
        size = size,
        hash = hash,
        relativeOutputFile = module.relativeOutputFile,
        reason = module.reason,
      )
    )
  }

  for (source in asset.sources) {
    if (source is ZipSource) {
      source.distributionFileEntryProducer?.consume(size = 0, hash = 0, targetFile = asset.effectiveFile)?.let(list::add)
    }
    else if (source is LazySource) {
      list.add(CustomAssetEntry(path = asset.effectiveFile, hash = 0))
    }
  }
}