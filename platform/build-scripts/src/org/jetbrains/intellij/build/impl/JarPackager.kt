// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.dynatrace.hash4j.hashing.HashStream64
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.util.PathUtilRt
import com.intellij.util.io.URLUtil
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.lang.ZipFile
import com.jetbrains.util.filetype.FileType
import com.jetbrains.util.filetype.FileTypeDetector.DetectFileType
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_CLIENT_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_JAR
import org.jetbrains.intellij.build.impl.projectStructureMapping.*
import org.jetbrains.intellij.build.jarCache.JarCacheManager
import org.jetbrains.intellij.build.jarCache.NonCachingJarCacheManager
import org.jetbrains.intellij.build.jarCache.SourceBuilder
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.*
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
  "pty4j", "jna", "sqlite-native", "async-profiler" //, "jetbrains.skiko.awt.runtime.all" - do not pre-sign skiko until we can use different files per classloader
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
      val packager = JarPackager(outputDir, context, platformLayout = null, isRootDir = false, ModuleOutputPatcher())
      packager.computeModuleSources(includedModules, layout = null, searchableOptionSet = null)
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
      val packager = JarPackager(outputDir, context, platformLayout, isRootDir, moduleOutputPatcher)
      packager.computeModuleSources(includedModules, layout, searchableOptionSet)
      packager.computeModuleCustomLibrarySources(layout)

      val frontendModuleFilter = context.getFrontendModuleFilter()
      val libraryToMerge = packager.computeProjectLibrariesSources(outputDir, layout, packager.copiedFiles, frontendModuleFilter)
      if (isRootDir) {
        for ((jarName, predicate) in predefinedMergeRules) {
          packager.mergeLibsByPredicate(jarName, libraryToMerge, outputDir, predicate, frontendModuleFilter)
        }

        if (!libraryToMerge.isEmpty()) {
          val clientLibraries = libraryToMerge.filterKeys { frontendModuleFilter.isProjectLibraryIncluded(it.name) }
          if (clientLibraries.isNotEmpty()) {
            packager.filesToSourceWithMappings(uberJarFile = outputDir.resolve(PlatformJarNames.LIB_CLIENT_JAR), libraryToMerge = clientLibraries)
          }

          val nonClientLibraries = libraryToMerge.filterKeys { !frontendModuleFilter.isProjectLibraryIncluded(it.name) }
          if (nonClientLibraries.isNotEmpty()) {
            packager.filesToSourceWithMappings(uberJarFile = outputDir.resolve(PlatformJarNames.LIB_JAR), libraryToMerge = nonClientLibraries)
          }
        }
      }
      else if (!libraryToMerge.isEmpty()) {
        val mainJarName = (layout as PluginLayout).getMainJarName()
        check(includedModules.any { it.relativeOutputFile == mainJarName })
        packager.filesToSourceWithMappings(uberJarFile = outputDir.resolve(mainJarName), libraryToMerge = libraryToMerge)
      }

      val cacheManager = if (dryRun || context !is BuildContextImpl) NonCachingJarCacheManager else context.jarCacheManager
      val nativeFiles = buildJars(
        assets = packager.assets.values,
        layout = layout,
        cache = cacheManager,
        context = context,
        isCodesignEnabled = isCodesignEnabled,
        useCacheAsTargetFile = !dryRun && context.options.isUnpackedDist,
        dryRun = dryRun,
        helper = packager.helper
      )

      return coroutineScope {
        if (nativeFiles.isNotEmpty()) {
          launch(CoroutineName("pack native presigned files")) {
            packNativePresignedFiles(nativeFiles, dryRun, context, toRelativePath = { libName, fileName ->
              "lib/$libName/$fileName"
            })
          }
        }

        val list = mutableListOf<DistributionFileEntry>()
        val hasher = Hashing.komihash5_0().hashStream()
        for (item in packager.assets.values) {
          computeDistributionFileEntries(item, hasher, list, dryRun, cacheManager)
        }
        list
      }
    }
  }

  private suspend fun computeModuleSources(includedModules: Collection<ModuleItem>, layout: BaseLayout?, searchableOptionSet: SearchableOptionSetDescriptor?) {
    val addedModules = HashSet<String>()

    // First, check the content. This is done prior to everything else since we might configure a custom relativeOutputFile.
    if (layout is PluginLayout) {
      computeModuleSourcesByContent(helper, context, layout, addedModules, jarPackager = this, searchableOptionSet)
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

    inferModuleSources(layout, addedModules, platformLayout!!, helper, jarPackager = this, searchableOptionSet, context)
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

    val nativeFiles = context.getModuleOutputFileContent(module, forTests = useTestModuleOutput, relativePath = "META-INF/native-files-list")?.let { String(it) }?.lines()
    val outFile = outDir.resolve(item.relativeOutputFile)
    val asset = if (packToDir) {
      assets.computeIfAbsent(moduleOutDir) { file ->
        AssetDescriptor(isDir = true, file = file, relativePath = "", pathInClassLog = "", nativeFiles = nativeFiles)
      }
    }
    else {
      assets.computeIfAbsent(outFile) { file ->
        createAssetDescriptor(outDir, item.relativeOutputFile, file, context, nativeFiles)
      }
    }

    val moduleSources = asset.includedModules.computeIfAbsent(item) { mutableListOf() }

    for (entry in patchedContent) {
      moduleSources.add(InMemoryContentSource(entry.key, entry.value))
    }

    val jarAsset = lazy(LazyThreadSafetyMode.NONE) {
      if (packToDir) {
        getJarAsset(outFile, item.relativeOutputFile, nativeFiles)
      }
      else {
        asset
      }
    }

    if (searchableOptionSet != null) {
      addSearchableOptionSources(layout, moduleName, module, jarAsset.value.sources, searchableOptionSet)
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

    val source = module.toSource(moduleOutDir, excludes)
    if (source != null) {
      moduleSources += source
    }

    if (layout is PluginLayout && layout.mainModule == moduleName) {
      handleCustomAssets(layout, jarAsset)
    }

    if (layout != null && (layout !is PluginLayout || !layout.modulesWithExcludedModuleLibraries.contains(moduleName))) {
      computeSourcesForModuleLibs(item, layout, module, copiedFiles, jarAsset, useTestModuleOutput)
    }
  }

  private suspend fun handleCustomAssets(layout: PluginLayout, jarAsset: Lazy<AssetDescriptor>) {
    for (customAsset in layout.customAssets) {
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
          pathInClassLog = "",
          nativeFiles = null,
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

  private suspend fun computeSourcesForModuleLibs(
    item: ModuleItem,
    layout: BaseLayout,
    module: JpsModule,
    copiedFiles: MutableMap<CopiedForKey, CopiedFor>,
    asset: Lazy<AssetDescriptor>,
    withTests: Boolean,
  ) {
    val moduleName = module.name
    val includeProjectLib = if (layout is PluginLayout) layout.auto else item.reason == ModuleIncludeReasons.PRODUCT_MODULES

    val excluded = if (layout is PluginLayout) (layout.excludedLibraries[moduleName] ?: emptyList()) + (layout.excludedLibraries[null] ?: emptyList()) else emptySet()
    for (element in helper.getLibraryDependencies(module, withTests = withTests)) {
      var projectLibraryData: ProjectLibraryData? = null
      val libRef = element.libraryReference
      if (libRef.parentReference !is JpsModuleReference) {
        val libName = libRef.libraryName
        if (includeProjectLib) {
          if (platformLayout!!.hasLibrary(libName) || layout.hasLibrary(libName)) {
            continue
          }

          if (helper.hasLibraryInDependencyChainOfModuleDependencies(module, libName, layout.includedModules, withTests)) {
            continue
          }

          projectLibraryData = ProjectLibraryData(libName, LibraryPackMode.MERGED, reason = "<- $moduleName")
          val library = element.library ?: throw IllegalStateException("cannot find ${libRef}")
          libToMetadata.put(library, projectLibraryData)
        }
        else if (platformLayout != null && platformLayout.isLibraryAlwaysPackedIntoPlugin(libName)) {
          platformLayout.findProjectLibrary(libName)?.let {
            throw IllegalStateException("Library $libName must not be included into platform layout: $it")
          }

          if (layout.hasLibrary(libName)) {
            continue
          }

          projectLibraryData = ProjectLibraryData(libName, LibraryPackMode.MERGED, reason = "<- $moduleName (always packed into plugin)")
          val library = element.library ?: throw IllegalStateException("cannot find ${libRef}")
          libToMetadata.put(library, projectLibraryData)
        }
        else {
          continue
        }
      }

      val library = element.library ?: throw IllegalStateException("cannot find ${libRef}")
      val libraryName = getLibraryFileName(library)
      if (excluded.contains(libraryName) || alreadyHasLibrary(layout, libraryName)) {
        continue
      }

      val targetFile = outDir.resolve(item.relativeOutputFile)
      val files = getLibraryFiles(library, copiedFiles, targetFile)
      for (i in (files.size - 1) downTo 0) {
        val file = files[i]
        val fileName = file.fileName.toString()
        if (item.reason != ModuleIncludeReasons.PRODUCT_MODULES && isSeparateJar(fileName, file, asset.value.relativePath)) {
          files.removeAt(i)
          addLibrary(library, outDir.resolve(removeVersionFromJar(fileName)), item.relativeOutputFile, listOf(file))
        }
      }

      for (file in files) {
        @Suppress("NAME_SHADOWING")
        asset.value.sources.add(
          ZipSource(
            file = file,
            distributionFileEntryProducer = { size, hash, targetFile ->
              if (projectLibraryData == null) {
                ModuleLibraryFileEntry(targetFile, moduleName, getLibraryFilename(library), file, size, hash, item.relativeOutputFile)
              }
              else {
                ProjectLibraryEntry(targetFile, projectLibraryData, file, hash, size, item.relativeOutputFile)
              }
            },
            isPreSignedAndExtractedCandidate = asset.value.nativeFiles != null || isLibPreSigned(library),
            filter = ::defaultLibrarySourcesNamesFilter,
          )
        )
      }
    }
  }

  private fun computeModuleCustomLibrarySources(layout: BaseLayout) {
    for (item in layout.includedModuleLibraries) {
      val library = context.findRequiredModule(item.moduleName).libraryCollection.libraries
                      .find { getLibraryFileName(it) == item.libraryName }
                    ?: throw IllegalArgumentException("Cannot find library ${item.libraryName} in '${item.moduleName}' module")
      var relativePath = item.relativeOutputPath
      if (relativePath.endsWith(".jar")) {
        val targetFile = outDir.resolve(relativePath)
        if (!relativePath.contains('/')) {
          relativePath = ""
        }
        addLibrary(library, targetFile, relativePath, getLibraryFiles(library, copiedFiles, targetFile))
      }
      else {
        val fileName = nameToJarFileName(item.libraryName)
        val targetFile: Path
        if (relativePath.isEmpty()) {
          targetFile = outDir.resolve(fileName)
        }
        else {
          targetFile = outDir.resolve(relativePath).resolve(fileName)
          relativePath += "/$fileName"
        }
        addLibrary(library, targetFile, relativePath, getLibraryFiles(library, copiedFiles, targetFile))
      }
    }
  }

  private fun alreadyHasLibrary(layout: BaseLayout, libraryName: String): Boolean =
    layout.includedModuleLibraries.any { it.libraryName == libraryName && !it.extraCopy }

  private fun mergeLibsByPredicate(
    jarName: String,
    libraryToMerge: MutableMap<JpsLibrary, List<Path>>,
    outputDir: Path,
    predicate: (String, FrontendModuleFilter) -> Boolean,
    frontendModuleFilter: FrontendModuleFilter
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
    filesToSourceWithMappings(uberJarFile = outputDir.resolve(jarName), libraryToMerge = result)
  }

  private fun filesToSourceWithMappings(uberJarFile: Path, libraryToMerge: Map<JpsLibrary, List<Path>>) {
    val descriptor = getJarAsset(targetFile = uberJarFile, relativeOutputFile = "", nativeFiles = null)
    for ((key, value) in libraryToMerge) {
      filesToSourceWithMapping(asset = descriptor, files = value, library = key, relativeOutputFile = null)
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
      if (packMode == LibraryPackMode.MERGED && !predefinedMergeRules.any { it.second(libName, frontendModuleFilter) } && !isLibraryMergeable(libName)) {
        packMode = LibraryPackMode.STANDALONE_MERGED
      }

      val outPath = libraryData.outPath
      if (packMode == LibraryPackMode.MERGED && outPath == null) {
        toMerge.put(library, getLibraryFiles(library, copiedFiles, targetFile = null))
      }
      else {
        var libOutputDir = outDir
        if (outPath != null) {
          if (outPath.endsWith(".jar")) {
            val targetFile = outDir.resolve(outPath)
            filesToSourceWithMapping(
              getJarAsset(targetFile, outPath, nativeFiles = null),
              getLibraryFiles(library, copiedFiles, targetFile),
              library,
              outPath
            )
            continue
          }

          libOutputDir = outDir.resolve(outPath)
        }

        if (packMode == LibraryPackMode.STANDALONE_MERGED) {
          val targetFile = libOutputDir.resolve(nameToJarFileName(libName))
          val relativeOutputFile = if (outDir == libOutputDir) "" else outDir.relativize(targetFile).invariantSeparatorsPathString
          addLibrary(library, targetFile, relativeOutputFile, getLibraryFiles(library, copiedFiles, targetFile))
        }
        else {
          for (file in library.getPaths(JpsOrderRootType.COMPILED)) {
            var fileName = file.fileName.toString()
            if (packMode == LibraryPackMode.STANDALONE_SEPARATE_WITHOUT_VERSION_NAME) {
              fileName = removeVersionFromJar(fileName)
            }

            val targetFile = libOutputDir.resolve(fileName)
            val relativeOutputFile = if (outDir == libOutputDir) "" else outDir.relativize(targetFile).invariantSeparatorsPathString
            addLibrary(library, targetFile, relativeOutputFile, listOf(file))
          }
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
  ) {
    val moduleName = (library.createReference().parentReference as? JpsModuleReference)?.moduleName
    val sources = asset.sources
    val isPreSignedCandidate = asset.nativeFiles != null || (isRootDir && isLibPreSigned(library))
    val libraryName = library.name
    for (file in files) {
      sources.add(
        ZipSource(
          file = file,
          isPreSignedAndExtractedCandidate = isPreSignedCandidate,
          optimizeConfigId = libraryName.takeIf { isRootDir && (libraryName == "jsvg") },
          distributionFileEntryProducer = { size, hash, targetFile ->
            moduleName?.let {
              ModuleLibraryFileEntry(
                path = targetFile,
                moduleName = it,
                libraryName = getLibraryFilename(library),
                libraryFile = file,
                hash = hash,
                size = size,
                relativeOutputFile = relativeOutputFile,
              )
            } ?: ProjectLibraryEntry(
              path = targetFile,
              data = libToMetadata[library] ?: throw IllegalStateException("Metadata not found for $libraryName"),
              libraryFile = file,
              hash = hash,
              size = size,
              relativeOutputFile = relativeOutputFile,
            )
          },
          filter = ::defaultLibrarySourcesNamesFilter,
        )
      )
    }
  }

  private fun addLibrary(library: JpsLibrary, targetFile: Path, relativeOutputFile: String, files: List<Path>) {
    filesToSourceWithMapping(
      asset = getJarAsset(targetFile = targetFile, relativeOutputFile = relativeOutputFile, nativeFiles = null),
      files = files,
      library = library,
      relativeOutputFile = relativeOutputFile,
    )
  }

  private fun getJarAsset(targetFile: Path, relativeOutputFile: String, nativeFiles: List<String>?): AssetDescriptor {
    return assets.computeIfAbsent(targetFile) {
      createAssetDescriptor(outDir = outDir, targetFile = targetFile, relativeOutputFile = relativeOutputFile, context = context, nativeFiles = nativeFiles)
    }
  }
}

private val agentLibrariesNotForcedInSeparateJars = listOf(
  "ideformer",
  "code-agents-core"
)

private suspend fun isSeparateJar(fileName: String, file: Path, jarPath: String): Boolean {
  if (fileName.endsWith("-rt.jar")
      || (fileName.contains("-agent") && agentLibrariesNotForcedInSeparateJars.none { fileName.contains(it) })) {
    return true
  }

  if (!fileName.startsWith("maven-")) {
    return false
  }

  val filePreventingMerging = "META-INF/sisu/javax.inject.Named"
  val result = withContext(Dispatchers.IO) {
    ZipFile.load(file).use {
      it.getResource(filePreventingMerging) != null
    }
  }
  if (result) {
    Span.current().addEvent("$fileName contains file '$filePreventingMerging' that prevent its merging into $jarPath")
  }
  return result
}

private data class AssetDescriptor(
  @JvmField val isDir: Boolean,
  @JvmField val file: Path,
  @JvmField val relativePath: String,
  @JvmField var effectiveFile: Path = file,
  @JvmField val pathInClassLog: String,
  @JvmField val nativeFiles: List<String>?,
  @JvmField val useCacheAsTargetFile: Boolean = true,
) {
  // must be sorted - we use it as is for Jar Cache
  @JvmField
  val sources: MutableList<Source> = mutableListOf()

  // must be sorted - we use it as is for Jar Cache
  @JvmField
  val includedModules: MutableMap<ModuleItem, MutableList<Source>> = Reference2ObjectLinkedOpenHashMap()
}

private fun removeVersionFromJar(fileName: String): String {
  val matcher = JAR_NAME_WITH_VERSION_PATTERN.matcher(fileName)
  return if (matcher.matches()) "${matcher.group(1)}.jar" else fileName
}

private fun getLibraryFiles(library: JpsLibrary, copiedFiles: MutableMap<CopiedForKey, CopiedFor>, targetFile: Path?): MutableList<Path> {
  val files = library.getPaths(JpsOrderRootType.COMPILED)
  val libName = library.name
  if (libName == "ktor-client-jvm") {
    return files
  }

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
): Map<ZipSource, List<String>> {
  checkAssetUniqueness(assets)

  if (dryRun) {
    return emptyMap()
  }

  val list = withContext(Dispatchers.IO) {
    assets.map { asset ->
      async(CoroutineName("build jar for ${asset.relativePath}")) {
        if (asset.isDir) {
          updateModuleSourceHash(asset)
          return@async emptyMap()
        }

        val nativeFileHandler = if (isCodesignEnabled) NativeFileHandlerImpl(context, asset) else null
        val sources = mutableListOf<Source>()
        sources.addAll(asset.sources)
        for (moduleSources in asset.includedModules.values) {
          sources.addAll(moduleSources)
        }

        val file = asset.file
        if (sources.isEmpty()) {
          return@async emptyMap()
        }

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
                  get() = useCacheAsTargetFile && !asset.relativePath.contains('/') && asset.useCacheAsTargetFile

                override fun updateDigest(digest: HashStream64) {
                  if (layout is PluginLayout) {
                    digest.putString(layout.mainModule)
                  }
                  else {
                    digest.putInt(0)
                  }
                }

                override suspend fun produce(targetFile: Path) {
                  buildJar(targetFile = targetFile, sources = sources, nativeFileHandler = nativeFileHandler, notify = false, addDirEntries = asset.includedModules.any { helper.isTestPluginModule(it.key.moduleName, null) })
                }
              }
            )
          }

        if (asset.pathInClassLog.isNotEmpty()) {
          reorderJar(relativePath = asset.pathInClassLog, file = file)
        }
        nativeFileHandler?.sourceToNativeFiles ?: emptyMap()
      }
    }
  }

  val result = TreeMap<ZipSource, List<String>>(compareBy { it.file.fileName.toString() })
  list.asSequence().map { it.getCompleted() }.forEach(result::putAll)
  return result
}

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

private class NativeFileHandlerImpl(private val context: BuildContext, private val descriptor: AssetDescriptor) : NativeFileHandler {
  override val sourceToNativeFiles = HashMap<ZipSource, List<String>>()

  override fun isNative(name: String): Boolean {
    descriptor.nativeFiles?.let { return descriptor.nativeFiles.contains(name) }

    @Suppress("SpellCheckingInspection", "RedundantSuppression")
    return isMacLibrary(name) ||
           name.endsWith(".exe") ||
           name.endsWith(".dll") ||
           name.endsWith("pty4j-unix-spawn-helper") ||
           name.endsWith("icudtl.dat")
  }

  override fun isCompatibleWithTargetPlatform(name: String): Boolean {
    if (!isNative(name)) return true
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

  buildJar(
    targetFile = targetFile,
    sources = moduleNames.mapNotNull { moduleName ->
      val module = context.findRequiredModule(moduleName)
      val output = context.getModuleOutputDir(module)
      module.toSource(output, commonModuleExcludes)
    },
  )
}

private fun JpsModule.toSource(outputDir: Path, excludes: List<PathMatcher>): Source? {
  return when {
    Files.isDirectory(outputDir) -> {
      DirSource(dir = outputDir, excludes = excludes)
    }
    Files.exists(outputDir) -> {
      ZipSource(file = outputDir, distributionFileEntryProducer = null, filter = createModuleSourcesNamesFilter(excludes))
    }
    sourceRoots.any { !it.rootType.isForTests } -> {
      error("Module $name output does not exist: $outputDir")
    }
    else -> null
  }
}

private fun createAssetDescriptor(outDir: Path, relativeOutputFile: String, targetFile: Path, context: BuildContext, nativeFiles: List<String>?): AssetDescriptor {
  var pathInClassLog = ""
  if (!context.isStepSkipped(BuildOptions.GENERATE_JAR_ORDER_STEP)) {
    if (context.paths.distAllDir == outDir.parent) {
      pathInClassLog = outDir.parent.relativize(targetFile).toString().replace(File.separatorChar, '/')
    }
    else if (outDir.startsWith(context.paths.distAllDir)) {
      pathInClassLog = context.paths.distAllDir.relativize(targetFile).toString().replace(File.separatorChar, '/')
    }
    else {
      val parent = outDir.parent
      if (parent?.fileName.toString() == "plugins") {
        pathInClassLog = parent.parent.relativize(targetFile).toString().replace(File.separatorChar, '/')
      }
    }
  }

  return AssetDescriptor(isDir = false, file = targetFile, relativePath = relativeOutputFile, pathInClassLog = pathInClassLog, nativeFiles = nativeFiles)
}

private fun computeDistributionFileEntries(asset: AssetDescriptor, hasher: HashStream64, list: MutableList<DistributionFileEntry>, dryRun: Boolean, cacheManager: JarCacheManager) {
  for ((module, sources) in asset.includedModules) {
    if (asset.isDir) {
      val single = sources.singleOrNull()
      if (single is DirSource && single.exist == false) {
        // do not add ModuleOutputEntry for a non-existent directory
        break
      }
    }

    var size = 0
    hasher.reset()
    for (source in sources) {
      size += source.size
      if (!dryRun) {
        cacheManager.validateHash(source)
        hasher.putLong(source.hash)
        hasher.putInt(source.size)
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
        reason = module.reason
      )
    )
  }

  for (source in asset.sources) {
    if (source is ZipSource) {
      source.distributionFileEntryProducer?.consume(size = source.size, hash = source.hash, targetFile = asset.effectiveFile)?.let(list::add)
    }
    else if (source is LazySource) {
      list.add(CustomAssetEntry(path = asset.effectiveFile, hash = source.hash))
    }
  }
}

private fun updateModuleSourceHash(asset: AssetDescriptor) {
  for (sources in asset.includedModules.values) {
    for (source in sources) {
      if (source is DirSource && source.hash == 0L) {
        source.hash = computeHashForModuleOutput(source)
      }
    }
  }
}
