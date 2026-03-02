// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import com.dynatrace.hash4j.hashing.HashFunnel
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.DirSource
import org.jetbrains.intellij.build.FrontendModuleFilter
import org.jetbrains.intellij.build.InMemoryContentSource
import org.jetbrains.intellij.build.JarPackagerDependencyHelper
import org.jetbrains.intellij.build.LazySource
import org.jetbrains.intellij.build.MAVEN_REPO
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.NativeFileHandler
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.SignNativeFileMode
import org.jetbrains.intellij.build.Source
import org.jetbrains.intellij.build.USER_HOME
import org.jetbrains.intellij.build.UTIL_8_JAR
import org.jetbrains.intellij.build.UTIL_JAR
import org.jetbrains.intellij.build.ZipSource
import org.jetbrains.intellij.build.buildJar
import org.jetbrains.intellij.build.checkForNoDiskSpace
import org.jetbrains.intellij.build.computeHashForModuleOutput
import org.jetbrains.intellij.build.computeModuleSourcesByContent
import org.jetbrains.intellij.build.defaultLibrarySourcesNamesFilter
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.getLibraryRoots
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_BACKEND_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_JAR
import org.jetbrains.intellij.build.impl.projectStructureMapping.CustomAssetEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.intellij.build.inferModuleSources
import org.jetbrains.intellij.build.io.WRITE_OPEN_OPTION
import org.jetbrains.intellij.build.io.writeToFileChannelFully
import org.jetbrains.intellij.build.jarCache.JarCacheManager
import org.jetbrains.intellij.build.jarCache.NonCachingJarCacheManager
import org.jetbrains.intellij.build.jarCache.SourceBuilder
import org.jetbrains.intellij.build.productLayout.util.mapConcurrent
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
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
  "netty-codec-protobuf",
  "Log4J",
  "slf4j-api",
  "slf4j-jdk14",
  // see getBuildProcessApplicationClasspath - used in JPS
  "jna",
  // see ArtifactRepositoryManager.getClassesFromDependencies
  "kotlin-stdlib",
)

private val presignedLibNames = setOf(
  "pty4j", "jna", "sqlite-native", "async-profiler", "jetbrains.skiko.awt.runtime.all"
)

private fun isLibPreSigned(library: JpsLibrary) = presignedLibNames.contains(library.name)

private val predefinedMergeRules = listOf<Pair<String, (String, FrontendModuleFilter) -> Boolean>>(
  "groovy.jar" to { it, _ -> it.startsWith("org.codehaus.groovy:") },
  "jsch-agent.jar" to { it, _ -> it.startsWith("jsch-agent") },
  "opentelemetry.jar" to { it, _ -> it == "opentelemetry" || it == "opentelemetry-semconv" || it.startsWith("opentelemetry-exporter-otlp") },
  PRODUCT_BACKEND_JAR to { name, filter -> (name.startsWith("License") || name.startsWith("jetbrains.codeWithMe.lobby.server.")) && filter.isBackendProjectLibrary(name) },
  PRODUCT_JAR to { name, filter -> (name.startsWith("License") || name.startsWith("jetbrains.codeWithMe.lobby.server.")) && !filter.isBackendProjectLibrary(name) },
  // see ClassPathUtil.getUtilClassPath
  UTIL_8_JAR to { it, _ -> libsUsedInJps.contains(it) || (it.startsWith("kotlinx-")) },

  // used in an external process - see `ConsoleProcessListFetcher.getConsoleProcessCount`
  UTIL_JAR to { it, _ -> it == "pty4j" || it == "jvm-native-trusted-roots" },
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
    suspend fun pack(includedModules: Collection<ModuleItem>, outputDir: Path, context: BuildContext) {
      val packager = JarPackager(outDir = outputDir, context = context, platformLayout = null, isRootDir = false, moduleOutputPatcher = ModuleOutputPatcher())
      packager.computeModuleSources(includedModules = includedModules, layout = null, searchableOptionSet = null, cachedDescriptorWriterProvider = null)
      buildJars(
        assets = packager.assets.values,
        cache = if (context is BuildContextImpl) context.jarCacheManager else NonCachingJarCacheManager,
        isCodesignEnabled = false,
        useCacheAsTargetFile = context.options.isUnpackedDist,
        dryRun = false,
        layout = null,
        helper = packager.helper,
        context = context
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
      descriptorCache: ScopedCachedDescriptorContainer? = null,
      context: BuildContext,
    ): Collection<DistributionFileEntry> {
      val packager = JarPackager(outDir = outputDir, context = context, platformLayout = platformLayout, isRootDir = isRootDir, moduleOutputPatcher = moduleOutputPatcher)
      packager.computeModuleSources(
        includedModules = includedModules,
        layout = layout,
        searchableOptionSet = searchableOptionSet,
        cachedDescriptorWriterProvider = descriptorCache
      )
      packager.computeModuleCustomLibrarySources(layout)

      val frontendModuleFilter = context.getFrontendModuleFilter()
      val libraryToMerge = packager.computeProjectLibrariesSources(
        outDir = outputDir,
        layout = layout,
        copiedFiles = packager.copiedFiles,
        frontendModuleFilter = frontendModuleFilter,
      )
      if (isRootDir) {
        for ((jarName, predicate) in predefinedMergeRules) {
          packager.mergeLibsByPredicate(
            jarName = jarName,
            libraryToMerge = libraryToMerge,
            outputDir = outputDir,
            predicate = predicate,
            frontendModuleFilter = frontendModuleFilter,
          )
        }

        if (!libraryToMerge.isEmpty()) {
          val commonLibraries = libraryToMerge.filterKeys { !frontendModuleFilter.isBackendProjectLibrary(it.name) }
          if (commonLibraries.isNotEmpty()) {
            packager.projectLibsToSourceWithMappings(uberJarFile = outputDir.resolve(PlatformJarNames.LIB_JAR), libraryToMerge = commonLibraries)
          }

          val backendLibraries = libraryToMerge.filterKeys { frontendModuleFilter.isBackendProjectLibrary(it.name) }
          if (backendLibraries.isNotEmpty()) {
            packager.projectLibsToSourceWithMappings(uberJarFile = outputDir.resolve(PlatformJarNames.LIB_BACKEND_JAR), libraryToMerge = backendLibraries)
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
        cache = cacheManager,
        isCodesignEnabled = isCodesignEnabled,
        useCacheAsTargetFile = !dryRun && context.options.isUnpackedDist,
        dryRun = dryRun,
        layout = layout,
        helper = packager.helper,
        context = context,
      )

      return coroutineScope {
        if (buildAssetResult.sourceToNativeFiles.isNotEmpty()) {
          launch(CoroutineName("pack native presigned files")) {
            packNativePresignedFiles(
              nativeFiles = buildAssetResult.sourceToNativeFiles,
              dryRun = dryRun,
              context = context,
              toRelativePath = { libName, fileName -> "lib/$libName/$fileName" },
            )
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

  private suspend fun computeModuleSources(
    includedModules: Collection<ModuleItem>,
    layout: BaseLayout?,
    searchableOptionSet: SearchableOptionSetDescriptor?,
    cachedDescriptorWriterProvider: ScopedCachedDescriptorContainer?,
  ) {
    val addedModules = HashSet<String>()

    val modulesWithCustomPath = HashSet<String>()
    for (item in includedModules) {
      if (layout is PluginLayout && !item.relativeOutputFile.contains('/')) {
        if (item.relativeOutputFile != layout.getMainJarName()) {
          modulesWithCustomPath.add(item.moduleName)
        }
      }
    }

    // First, check the content. This is done prior to everything else since we might configure a custom relativeOutputFile.
    if (layout is PluginLayout) {
      computeModuleSourcesByContent(
        helper = helper,
        context = context,
        pluginLayout = layout,
        addedModules = addedModules,
        jarPackager = this,
        searchableOptionSet = searchableOptionSet,
        modulesWithCustomPath = modulesWithCustomPath,
        pluginCachedDescriptorContainer = cachedDescriptorWriterProvider!!,
      )
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
    val moduleOutputRoots = context.outputProvider.getModuleOutputRoots(module, forTests = useTestModuleOutput)
    val extraExcludes = layout?.moduleExcludes?.get(moduleName) ?: emptyList()

    val packToDir = context.options.isUnpackedDist &&
                    !item.relativeOutputFile.contains('/') &&
                    !item.isProductModule() &&
                    (patchedContent.isEmpty() || (patchedContent.size == 1 && patchedContent.containsKey("META-INF/plugin.xml"))) &&
                    extraExcludes.isEmpty() &&
                    moduleOutputRoots.isNotEmpty()

    val outFile = outDir.resolve(item.relativeOutputFile)
    val asset = if (packToDir) {
      assets.computeIfAbsent(moduleOutputRoots.single()) { file ->
        AssetDescriptor(isDir = !file.toString().endsWith(".jar"), file = file, relativePath = "")
      }
    }
    else {
      assets.computeIfAbsent(outFile) { file ->
        AssetDescriptor(isDir = false, file = file, relativePath = item.relativeOutputFile, useCacheAsTargetFile = !item.isProductModule())
      }
    }

    val moduleSources = asset.includedModules.computeIfAbsent(item) { mutableListOf() }

    for ((relativePath, data) in patchedContent) {
      if (layout is PluginLayout && moduleName != layout.mainModule && relativePath == "META-INF/plugin.xml") {
        continue
      }
      moduleSources.add(InMemoryContentSource(relativePath, data))
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

    for (moduleOutDir in moduleOutputRoots) {
      val source = createModuleSource(module, moduleOutDir, excludes)
      if (source != null) {
        moduleSources.add(source)
      }
    }

    if (layout is PluginLayout && layout.mainModule == moduleName) {
      handleCustomAssets(layout, jarAsset)
    }

    if (layout != null && (layout !is PluginLayout || !layout.modulesWithExcludedModuleLibraries.contains(moduleName))) {
      computeSourcesForModuleLibs(item = item, layout = layout, module = module, copiedFiles = copiedFiles, asset = jarAsset, withTests = useTestModuleOutput)
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
        val assetDescriptor = AssetDescriptor(isDir = false, file = targetFile, relativePath = relativePath, useCacheAsTargetFile = false)
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
    searchableOptionSet: SearchableOptionSetDescriptor,
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
    withTests: Boolean,
  ) {
    val moduleName = module.name
    val includeProjectLib = if (layout is PluginLayout) layout.auto else item.isProductModule()

    val excluded = if (layout is PluginLayout) (layout.excludedLibraries.get(moduleName) ?: emptyList()) + (layout.excludedLibraries.get(null) ?: emptyList()) else emptySet()
    for (element in helper.getLibraryDependencies(module, withTests = withTests)) {
      var projectLibraryData: ProjectLibraryData? = null
      val libRef = element.libraryReference
      if (libRef.parentReference !is JpsModuleReference) {
        val libName = libRef.libraryName
        if (includeProjectLib) {
          if (platformLayout!!.hasLibrary(libName, moduleName) || layout.hasLibrary(libName)) {
            continue
          }

          if (helper.hasLibraryInDependencyChainOfModuleDependencies(dependentModule = module, libraryName = libName, siblings = layout.includedModules, withTests = withTests)) {
            continue
          }

          if (layout !is PluginLayout && item.isProductModule()) {
            projectLibraryData = ProjectLibraryData(libraryName = libName, owner = item, reason = null)
          }
          else {
            projectLibraryData = ProjectLibraryData(libraryName = libName, reason = "<- $moduleName", owner = item)
          }
        }
        else if (platformLayout != null && platformLayout.isLibraryAlwaysPackedIntoPlugin(libName)) {
          platformLayout.findProjectLibrary(libName)?.let {
            throw IllegalStateException("Library $libName must not be included into platform layout: $it")
          }

          if (layout.hasLibrary(libName)) {
            continue
          }

          projectLibraryData = ProjectLibraryData(libraryName = libName, reason = "<- $moduleName (always packed into plugin)", owner = item)
        }
        else {
          continue
        }
      }

      val library = requireNotNull(element.library) { "cannot find $libRef" }
      val libraryName = getLibraryFileName(library)
      if (excluded.contains(libraryName) || layout.includedModuleLibraries.any { it.libraryName == libraryName && !it.extraCopy }) {
        continue
      }

      if (item.reason == ModuleIncludeReasons.PRODUCT_MODULES) {
        packLibFilesIntoModuleJar(
          asset = asset.value,
          item = item,
          files = getLibraryRoots(library, context.outputProvider),
          projectLibraryData = projectLibraryData,
          library = library,
        )
      }
      else {
        fun addLibrary(relativeOutputFile: String, files: List<Path>) {
          val asset = getJarAsset(targetFile = outDir.resolve(relativeOutputFile), relativeOutputFile = relativeOutputFile)
          filesToSourceWithMapping(asset = asset, files = files, library = library, relativeOutputFile = relativeOutputFile, projectLibraryData = projectLibraryData)
        }

        val targetFile = outDir.resolve(item.relativeOutputFile)
        val files = getLibraryFiles(library = library, copiedFiles = copiedFiles, targetFile = targetFile, outputProvider = context.outputProvider)
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

          addLibrary(relativeOutputFile = removeVersionFromJar(fileName = nameToJarFileName(getLibraryFileName(library))), files = files)
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
    val mavenPaths = library.getPaths(JpsOrderRootType.COMPILED).map { toCanonicalReportPath(it, context.paths) }
    for (file in files) {
      val canonicalPath = getCanonicalPath(mavenPaths, file)
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
                canonicalLibraryPath = canonicalPath,
                size = size,
                hash = hash,
                relativeOutputFile = item.relativeOutputFile,
                owner = item,
              )
            }
            else {
              ProjectLibraryEntry(
                path = targetFile,
                data = projectLibraryData,
                libraryFile = file,
                canonicalLibraryPath = canonicalPath,
                hash = hash,
                size = size,
                relativeOutputFile = item.relativeOutputFile,
              )
            }
          },
          isPreSignedAndExtractedCandidate = isLibPreSigned(library),
          filter = ::defaultLibrarySourcesNamesFilter,
          moduleName = null,
        )
      )
    }
  }

  private fun computeModuleCustomLibrarySources(layout: BaseLayout) {
    for (item in layout.includedModuleLibraries) {
      val library = context.findRequiredModule(item.moduleName).libraryCollection.libraries.find { getLibraryFileName(it) == item.libraryName }
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

      val asset = getJarAsset(targetFile, relativePath)
      val files = getLibraryFiles(library = library, copiedFiles = copiedFiles, targetFile = targetFile, outputProvider = context.outputProvider)
      filesToSourceWithMapping(asset = asset, files = files, library = library, relativeOutputFile = relativePath, projectLibraryData = null)
    }
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
      val projectLibraryData = libToMetadata.get(library) ?: throw IllegalStateException("Metadata not found for ${library.name}")
      filesToSourceWithMapping(asset = descriptor, files = files, library = library, relativeOutputFile = null, projectLibraryData = projectLibraryData)
    }
  }

  private fun computeProjectLibrariesSources(
    outDir: Path,
    layout: BaseLayout,
    copiedFiles: MutableMap<CopiedForKey, CopiedFor>,
    frontendModuleFilter: FrontendModuleFilter,
  ): MutableMap<JpsLibrary, List<Path>> {
    if (layout.includedProjectLibraries.isEmpty()) {
      return LinkedHashMap()
    }

    val outputProvider = context.outputProvider
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
        toMerge.put(library, getLibraryFiles(library = library, copiedFiles = copiedFiles, targetFile = null, outputProvider = outputProvider))
        continue
      }

      var libOutputDir = outDir
      if (outPath != null) {
        if (outPath.endsWith(".jar")) {
          val targetFile = outDir.resolve(outPath)
          val asset = getJarAsset(targetFile, outPath)
          val files = getLibraryFiles(library, copiedFiles, targetFile, outputProvider = outputProvider)
          filesToSourceWithMapping(asset, files, library, outPath, libraryData)
          continue
        }

        libOutputDir = outDir.resolve(outPath)
      }

      fun addLibrary(targetFile: Path, relativeOutputFile: String, files: List<Path>) {
        val asset = getJarAsset(targetFile, relativeOutputFile)
        filesToSourceWithMapping(asset = asset, files = files, library = library, relativeOutputFile = relativeOutputFile, projectLibraryData = libraryData)
      }

      if (packMode == LibraryPackMode.STANDALONE_MERGED) {
        val targetFile = libOutputDir.resolve(nameToJarFileName(libName))
        val relativeOutputFile = if (outDir == libOutputDir) "" else outDir.relativize(targetFile).invariantSeparatorsPathString
        addLibrary(
          targetFile = targetFile,
          relativeOutputFile = relativeOutputFile,
          files = getLibraryFiles(library = library, copiedFiles = copiedFiles, targetFile = targetFile, outputProvider = outputProvider)
        )
      }
      else {
        for (file in getLibraryRoots(library, outputProvider)) {
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
    val mavenPaths = library.getPaths(JpsOrderRootType.COMPILED).map { toCanonicalReportPath(it, context.paths) }
    for (file in files) {
      val canonicalPath = getCanonicalPath(mavenPaths, file)
      sources.add(
        ZipSource(
          file = file,
          isPreSignedAndExtractedCandidate = isPreSignedCandidate,
          optimizeConfigId = libraryName.takeIf { isRootDir && libraryName == "jsvg" },
          distributionFileEntryProducer = { size, hash, targetFile ->
            if (moduleName == null) {
              val data = projectLibraryData ?: throw IllegalStateException("Metadata not specified for $libraryName")
              ProjectLibraryEntry(
                path = targetFile,
                data = data,
                libraryFile = file,
                canonicalLibraryPath = canonicalPath,
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
                canonicalLibraryPath = canonicalPath,
                size = size,
                hash = hash,
                relativeOutputFile = relativeOutputFile,
                owner = ModuleItem(moduleName, relativeOutputFile = targetFile.fileName.toString(), reason = null),
              )
            }
          },
          filter = ::defaultLibrarySourcesNamesFilter,
          moduleName = null,
        )
      )
    }
  }

  private fun getJarAsset(targetFile: Path, relativeOutputFile: String): AssetDescriptor {
    return assets.computeIfAbsent(targetFile) {
      AssetDescriptor(isDir = false, file = targetFile, relativePath = relativeOutputFile)
    }
  }
}

private fun getCanonicalPath(mavenPaths: List<String>, file: Path): String {
  return mavenPaths.singleOrNull()
         ?: mavenPaths.firstOrNull { it.endsWith("/${file.fileName}") }
         ?: throw IllegalStateException("Cannot find canonical path for $file in $mavenPaths")
}

private fun toCanonicalReportPath(file: Path, buildPaths: BuildPaths): String {
  val projectHome = buildPaths.projectHome
  val mavenHome = MAVEN_REPO
  for (root in listOf(bazelMavenHome, mavenHome, projectHome)) {
    if (file.startsWith(root)) {
      val macro = if (root === projectHome) $$"$PROJECT_DIR$/" else $$"$MAVEN_REPOSITORY$/"
      return macro + root.relativize(file).invariantSeparatorsPathString
    }
  }
  return file.invariantSeparatorsPathString
}

private val bazelMavenHome = USER_HOME.resolve(".m2/repository-do-not-use-maven-repository-with-bazel")

@Suppress("SpellCheckingInspection")
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

private fun getLibraryFiles(library: JpsLibrary, copiedFiles: MutableMap<CopiedForKey, CopiedFor>, targetFile: Path?, outputProvider: ModuleOutputProvider): MutableList<Path> {
  val files = getLibraryRoots(library, outputProvider).toMutableList()
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

private fun nameToJarFileName(name: String): String =
  "${sanitizeFileName(name.lowercase(), replacement = "-") { c -> c == ' '}}.jar"

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

fun moduleOutputAsSource(module: JpsModule, excludes: List<PathMatcher> = commonModuleExcludes, outputProvider: ModuleOutputProvider): Source {
  val outputs = outputProvider.getModuleOutputRoots(module)
  if (outputs.size != 1) {
    throw IllegalStateException("Supports only one module output for module '${module.name}', but got ${outputs.size}: $outputs")
  }
  val moduleOutput = outputs.single()
  check(Files.exists(moduleOutput)) {
    "${module.name} module output directory doesn't exist: $moduleOutput"
  }

  if (moduleOutput.toString().endsWith(".jar")) {
    return ZipSource(file = moduleOutput, distributionFileEntryProducer = null, filter = createModuleSourcesNamesFilter(excludes), moduleName = module.name)
  }
  else {
    return DirSource(dir = moduleOutput, excludes = excludes, moduleName = module.name)
  }
}

internal fun createModuleSourcesNamesFilter(excludes: List<PathMatcher>): (String) -> Boolean {
  return { name ->
    val p = Path.of(name)
    excludes.none { it.matches(p) }
  }
}

// null targetFile means main jar
private data class CopiedForKey(@JvmField val file: Path, @JvmField val targetFile: Path?)

private data class CopiedFor(@JvmField val library: JpsLibrary, @JvmField val targetFile: Path?)

private suspend fun buildJars(
  assets: Collection<AssetDescriptor>,
  cache: JarCacheManager,
  isCodesignEnabled: Boolean,
  useCacheAsTargetFile: Boolean,
  dryRun: Boolean,
  layout: BaseLayout?,
  helper: JarPackagerDependencyHelper,
  context: BuildContext,
): BuildAssetResult {
  checkAssetUniqueness(assets)

  if (dryRun) {
    return emptyBuildJarsResult()
  }

  val list = withContext(Dispatchers.IO) {
    assets.mapConcurrent { asset ->
      withContext(CoroutineName("build jar for ${asset.relativePath}")) {
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

  for (item in list) {
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

private fun buildDuplicateSourceErrorMessage(
  file: Path,
  asset: AssetDescriptor,
  source: Source,
  old: SizeAndHash,
  size: Int,
  hash: Long,
  includedModules: Map<ModuleItem, MutableList<Source>>,
): String = buildString {
  appendLine("Source is duplicated:")
  appendLine("  Target JAR: $file")
  appendLine("  Relative path: ${asset.relativePath}")
  appendLine("  Duplicate source: $source")
  if (source is ZipSource) {
    appendLine("  Source file: ${source.file}")
  }
  appendLine("  Already processed: size=${old.size}, hash=${old.hash}")
  appendLine("  New occurrence:    size=$size, hash=$hash")
  if (includedModules.isEmpty()) {
    appendLine("  Sources being packed into this JAR (no modules, direct library merge):")
    appendLine("    Total sources: ${asset.sources.size}")

    // Count how many times the duplicate source appears
    val duplicateCount = asset.sources.count { it == source }
    if (duplicateCount > 1) {
      appendLine("    Duplicate source appears $duplicateCount times in the list below:")
    }

    var duplicateIndex = 0
    for (s in asset.sources) {
      if (s == source) {
        duplicateIndex++
        appendLine("    >>> $s [DUPLICATE #$duplicateIndex]")
      }
      else {
        appendLine("    - $s")
      }
    }
  }
  else {
    appendLine("  Modules being packed into this JAR:")
    for (module in includedModules.keys) {
      appendLine("    - ${module.moduleName} (reason: ${module.reason}, output: ${module.relativeOutputFile})")
    }
  }
}

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
        when (source) {
          is DirSource -> {
            sourceToMetadata.computeIfAbsent(source) {
              SizeAndHash(size = 0, hash = computeHashForModuleOutput(it as DirSource))
            }
          }
          is InMemoryContentSource -> {
            // ignore
          }
          else -> error("Unexpected source: $source")
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
              layout.bundlingRestrictions.updateDigest(digest)
              digest.putUnorderedIterable(layout.pathsToScramble, HashFunnel.forString(), Hashing.xxh3_64())
            }
            else {
              digest.putInt(0)
            }
          }

          override suspend fun produce(targetFile: Path) {
            val addDirEntries = includedModules.any { helper.isTestPluginModule(moduleName = it.key.moduleName, module = null) }
            buildJar(targetFile = targetFile, sources = sources, nativeFileHandler = nativeFileHandler, addDirEntries = addDirEntries)
          }

          override fun consumeInfo(source: Source, size: Int, hash: Long) {
            val old = sourceToMetadata.putIfAbsent(source, SizeAndHash(size, hash))
            require(old == null) {
              buildDuplicateSourceErrorMessage(
                file = file,
                asset = asset,
                source = source,
                old = old!!,
                size = size,
                hash = hash,
                includedModules = includedModules,
              )
            }
          }
        },
      )
    }

  return BuildAssetResult(sourceToNativeFiles = nativeFileHandler?.sourceToNativeFiles ?: emptyMap(), sourceToMetadata)
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

  @Suppress("SpellCheckingInspection", "RedundantSuppression")
  override fun isNative(name: String): Boolean {
    return isMacLibrary(name) ||
           name.endsWith(".exe") ||
           name.endsWith(".dll") ||
           name.endsWith("pty4j-unix-spawn-helper") ||
           name.endsWith("icudtl.dat")
  }

  override fun isCompatibleWithTargetPlatform(name: String): Boolean {
    return !isNative(name) || NativeFilesMatcher.isCompatibleWithTargetPlatform(name, context.options.targetOs, context.options.targetArch)
  }

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

    val options = macSigningOptions("application/x-mac-app-bin", context)
    val file = Files.createTempFile(context.paths.tempDir, "", "")
    FileChannel.open(file, WRITE_OPEN_OPTION).use { fileChannel ->
      writeToFileChannelFully(fileChannel, data)
    }
    context.proprietaryBuildTools.signTool.signFiles(listOf(file), context, options)
    if (!context.options.isInDevelopmentMode) {
      check(isSigned(file)) { "Missing signature for $file ($name)" }
    }
    return file
  }
}

suspend fun buildJar(targetFile: Path, moduleNames: List<String>, context: CompilationContext, dryRun: Boolean = false) {
  if (dryRun) {
    return
  }

  checkForNoDiskSpace(context) {
    buildJar(
      targetFile = targetFile,
      sources = moduleNames.flatMap { moduleName ->
        val module = context.outputProvider.findRequiredModule(moduleName)
        context.outputProvider.getModuleOutputRoots(module).mapNotNull { output ->
          createModuleSource(module = module, outputDir = output, excludes = commonModuleExcludes)
        }
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
    attributes != null && attributes.isDirectory -> DirSource(dir = outputDir, excludes = excludes, moduleName = module.name)
    attributes != null -> ZipSource(file = outputDir, distributionFileEntryProducer = null, filter = createModuleSourcesNamesFilter(excludes), moduleName = module.name)
    module.sourceRoots.any { !it.rootType.isForTests } -> error("Module ${module.name} output does not exist: $outputDir")
    else -> null
  }
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
        owner = module,
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
