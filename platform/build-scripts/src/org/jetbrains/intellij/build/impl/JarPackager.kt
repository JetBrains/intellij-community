// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build.impl

import com.dynatrace.hash4j.hashing.HashStream64
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.platform.diagnostic.telemetry.helpers.useWithoutActiveScope
import com.intellij.util.PathUtilRt
import com.intellij.util.io.URLUtil
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.lang.ImmutableZipFile
import com.jetbrains.util.filetype.FileType
import com.jetbrains.util.filetype.FileTypeDetector.DetectFileType
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_CLIENT_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_JAR
import org.jetbrains.intellij.build.impl.projectStructureMapping.*
import org.jetbrains.intellij.build.jarCache.JarCacheManager
import org.jetbrains.intellij.build.jarCache.NonCachingJarCacheManager
import org.jetbrains.intellij.build.jarCache.SourceBuilder
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.*
import java.util.*
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readLines

private val JAR_NAME_WITH_VERSION_PATTERN = "(.*)-\\d+(?:\\.\\d+)*\\.jar*".toPattern()
private val isUnpackedDist = System.getProperty("idea.dev.build.unpacked").toBoolean()

private val libsThatUsedInJps = java.util.Set.of(
  "ASM",
  "netty-buffer",
  "netty-codec-http",
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

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
private val presignedLibNames = java.util.Set.of(
  "pty4j", "jna", "sqlite-native", "async-profiler"
)

private fun isLibPreSigned(library: JpsLibrary) = presignedLibNames.contains(library.name)

private val notImportantKotlinLibs = java.util.Set.of(
  "kotlinx-collections-immutable",
  "kotlinx-coroutines-guava",
  "kotlinx-datetime-jvm",
  "kotlinx-html-jvm",
)

const val rdJarName = "rd.jar"

private val predefinedMergeRules = HashMap<String, (String, JetBrainsClientModuleFilter) -> Boolean>().let { map ->
  map.put("groovy.jar") { it, _ -> it.startsWith("org.codehaus.groovy:") }
  map.put("jsch-agent.jar") { it, _ -> it.startsWith("jsch-agent") }
  map.put(rdJarName) { it, _ -> it.startsWith("rd-") }
  // separate file to use in Gradle Daemon classpath
  map.put("opentelemetry.jar") { it, _ -> it == "opentelemetry" || it == "opentelemetry-semconv" || it.startsWith("opentelemetry-exporter-otlp") }
  map.put("bouncy-castle.jar") { it, _ -> it.startsWith("bouncy-castle-") }
  map.put(PRODUCT_JAR) { name, filter -> name.startsWith("License") && !filter.isProjectLibraryIncluded(name) }
  map.put(PRODUCT_CLIENT_JAR) { name, filter -> name.startsWith("License") && filter.isProjectLibraryIncluded(name) }
  // see ClassPathUtil.getUtilClassPath
  map.put(UTIL_8_JAR) { it, _ ->
    libsThatUsedInJps.contains(it) ||
    (it.startsWith("kotlinx-") && !notImportantKotlinLibs.contains(it)) ||
    it == "kotlin-reflect"
  }

  // used in external process - see ConsoleProcessListFetcher.getConsoleProcessCount
  map.put(UTIL_JAR) { it, _ -> it == "pty4j" || it == "jvm-native-trusted-roots" || it == "caffeine" }

  java.util.Map.copyOf(map)
}

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
  private val isRootDir: Boolean
) {
  private val assets = LinkedHashMap<Path, AssetDescriptor>()

  private val libToMetadata = HashMap<JpsLibrary, ProjectLibraryData>()
  private val copiedFiles = HashMap<CopiedForKey, CopiedFor>()

  private val helper = (context as BuildContextImpl).jarPackagerDependencyHelper

  companion object {
    suspend fun pack(
      includedModules: Collection<ModuleItem>,
      outputDir: Path,
      isRootDir: Boolean,
      isCodesignEnabled: Boolean = true,
      layout: BaseLayout?,
      platformLayout: PlatformLayout?,
      moduleOutputPatcher: ModuleOutputPatcher = ModuleOutputPatcher(),
      dryRun: Boolean,
      jarsWithSearchableOptions: SearchableOptionSetDescriptor? = null,
      context: BuildContext,
    ): Collection<DistributionFileEntry> {
      val packager = JarPackager(outDir = outputDir, platformLayout = platformLayout, isRootDir = isRootDir, context = context)
      packager.computeModuleSources(
        includedModules = includedModules,
        moduleOutputPatcher = moduleOutputPatcher,
        jarsWithSearchableOptions = jarsWithSearchableOptions,
        layout = layout,
      )
      if (layout != null) {
        packager.computeModuleCustomLibrarySources(layout)

        val clientModuleFilter = context.jetBrainsClientModuleFilter
        val libraryToMerge = packager.computeProjectLibrariesSources(outDir = outputDir, layout = layout, copiedFiles = packager.copiedFiles, clientModuleFilter = clientModuleFilter)
        if (isRootDir) {
          for ((key, value) in predefinedMergeRules) {
            packager.mergeLibsByPredicate(jarName = key, libraryToMerge = libraryToMerge, outputDir = outputDir, predicate = value, clientModuleFilter = clientModuleFilter)
          }

          if (!libraryToMerge.isEmpty()) {
            val clientLibraries = libraryToMerge.filterKeys { clientModuleFilter.isProjectLibraryIncluded(it.name) }
            if (clientLibraries.isNotEmpty()) {
              packager.filesToSourceWithMappings(uberJarFile = outputDir.resolve(PlatformJarNames.LIB_CLIENT_JAR), libraryToMerge = clientLibraries)
            }

            val nonClientLibraries = libraryToMerge.filterKeys { !clientModuleFilter.isProjectLibraryIncluded(it.name) }
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
      }

      val cacheManager = if (dryRun || context !is BuildContextImpl) NonCachingJarCacheManager else context.jarCacheManager
      if (!dryRun && isRootDir) {
        cacheManager.cleanup()
      }

      val nativeFiles = coroutineScope {
        val nativeFiles = async {
          buildJars(
            assets = packager.assets.values,
            layout = layout,
            cache = cacheManager,
            context = context,
            isCodesignEnabled = isCodesignEnabled,
            useCacheAsTargetFile = !dryRun && isUnpackedDist,
            dryRun = dryRun,
          )
        }

        nativeFiles.await()
      }

      return coroutineScope {
        if (nativeFiles.isNotEmpty()) {
          launch {
            packNativePresignedFiles(nativeFiles = nativeFiles, dryRun = dryRun, context = context, toRelativePath = { libName, fileName ->
              "lib/$libName/$fileName"
            })
          }
        }

        val list = mutableListOf<DistributionFileEntry>()
        val hasher = Hashing.komihash5_0().hashStream()
        for (item in packager.assets.values) {
          computeDistributionFileEntries(asset = item, hasher = hasher, list = list, dryRun = dryRun, cacheManager = cacheManager)
        }

        // sort because projectStructureMapping is a concurrent collection
        // call invariantSeparatorsPathString because the result of Path ordering is platform-dependent
        list.sortWith(
          compareBy(
            { it.path.invariantSeparatorsPathString },
            { it.type },
            { (it as? ModuleOutputEntry)?.moduleName },
            { (it as? LibraryFileEntry)?.libraryFile?.let(::isFromLocalMavenRepo) != true },
            { (it as? LibraryFileEntry)?.libraryFile?.invariantSeparatorsPathString },
          )
        )
        list
      }
    }
  }

  private suspend fun computeModuleSources(
    includedModules: Collection<ModuleItem>,
    moduleOutputPatcher: ModuleOutputPatcher,
    layout: BaseLayout?,
    jarsWithSearchableOptions: SearchableOptionSetDescriptor?,
  ) {
    val addedModules = HashSet<String>()

    for (item in includedModules) {
      computeSourcesForModule(item = item, moduleOutputPatcher = moduleOutputPatcher, layout = layout, searchableOptionSetDescriptor = jarsWithSearchableOptions)

      addedModules.add(item.moduleName)
    }

    if (layout !is PluginLayout || !layout.auto) {
      return
    }

    inferModuleSources(
      layout = layout,
      platformLayout = platformLayout!!,
      addedModules = addedModules,
      helper = helper,
      moduleOutputPatcher = moduleOutputPatcher,
      jarsWithSearchableOptions = jarsWithSearchableOptions,
      jarPackager = this,
      context = context,
    )
  }

  internal suspend fun computeSourcesForModule(
    item: ModuleItem,
    moduleOutputPatcher: ModuleOutputPatcher,
    layout: BaseLayout?,
    searchableOptionSetDescriptor: SearchableOptionSetDescriptor?,
  ) {
    val moduleName = item.moduleName
    val patchedDirs = moduleOutputPatcher.getPatchedDir(moduleName)
    val patchedContent = moduleOutputPatcher.getPatchedContent(moduleName)

    val module = context.findRequiredModule(moduleName)
    val moduleOutDir = context.getModuleOutputDir(module)
    val extraExcludes = layout?.moduleExcludes?.get(moduleName) ?: emptyList()

    val packToDir = isUnpackedDist &&
                    !item.relativeOutputFile.contains('/') &&
                    patchedContent.isEmpty() &&
                    patchedDirs.isEmpty() &&
                    extraExcludes.isEmpty()

    val outFile = outDir.resolve(item.relativeOutputFile)
    val asset = if (packToDir) {
      assets.computeIfAbsent(moduleOutDir) { file ->
        AssetDescriptor(isDir = true, file = file, relativePath = "", pathInClassLog = "", nativeFiles = null)
      }
    }
    else {
      assets.computeIfAbsent(outFile) { file ->
        createAssetDescriptor(outDir = outDir, targetFile = file, relativeOutputFile = item.relativeOutputFile, context = context, metaInfDir = moduleOutDir.resolve("META-INF"))
      }
    }

    val moduleSources = asset.includedModules.computeIfAbsent(item) { mutableListOf() }

    for (entry in patchedContent) {
      moduleSources.add(InMemoryContentSource(entry.key, entry.value))
    }

    // must be before module output to override
    for (dir in patchedDirs) {
      moduleSources.add(DirSource(dir = dir))
    }

    if (searchableOptionSetDescriptor != null) {
      addSearchableOptionSources(
        layout = layout,
        moduleName = moduleName,
        module = module,
        moduleSources = moduleSources,
        searchableOptionSetDescriptor = searchableOptionSetDescriptor,
      )
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
    moduleSources.add(DirSource(dir = moduleOutDir, excludes = excludes))

    if (layout != null && !layout.modulesWithExcludedModuleLibraries.contains(moduleName)) {
      val jarAsset = if (packToDir) {
        getJarAsset(
          targetFile = outFile,
          relativeOutputFile = item.relativeOutputFile,
          metaInfDir = moduleOutDir.resolve("META-INF"),
        )
      }
      else {
        asset
      }
      computeSourcesForModuleLibs(item = item, module = module, layout = layout, copiedFiles = copiedFiles, asset = jarAsset)
    }
  }

  private fun addSearchableOptionSources(
    layout: BaseLayout?,
    moduleName: String,
    module: JpsModule,
    moduleSources: MutableList<Source>,
    searchableOptionSetDescriptor: SearchableOptionSetDescriptor
  ) {
    if (layout is PluginLayout) {
      if (moduleName == BUILT_IN_HELP_MODULE_NAME) {
        return
      }

      if (moduleName == layout.mainModule) {
        val pluginId = helper.getPluginIdByModule(module)
        moduleSources.addAll(searchableOptionSetDescriptor.createSourceByPlugin(pluginId))
      }
      else {
        // is it a product module?
        context.findFileInModuleSources(module, "$moduleName.xml")?.let {
          moduleSources.addAll(searchableOptionSetDescriptor.createSourceByModule(moduleName))
        }
      }
    }
    else if (moduleName == (context.productProperties.productPluginSourceModuleName ?: context.productProperties.applicationInfoModule)) {
      moduleSources.addAll(searchableOptionSetDescriptor.createSourceByPlugin("com.intellij"))
    }
  }

  private suspend fun computeSourcesForModuleLibs(
    item: ModuleItem,
    layout: BaseLayout,
    module: JpsModule,
    copiedFiles: MutableMap<CopiedForKey, CopiedFor>,
    asset: AssetDescriptor,
  ) {
    val moduleName = module.name
    val includeProjectLib = if (layout is PluginLayout) layout.auto else item.reason == ModuleIncludeReasons.PRODUCT_MODULES

    val excluded = (layout.excludedLibraries.get(moduleName) ?: emptyList()) + (layout.excludedLibraries.get(null) ?: emptyList())
    for (element in helper.getLibraryDependencies(module)) {
      var projectLibraryData: ProjectLibraryData? = null
      val libRef = element.libraryReference
      if (libRef.parentReference !is JpsModuleReference) {
        val libName = libRef.libraryName
        if (includeProjectLib) {
          if (platformLayout!!.hasLibrary(libName) || layout.hasLibrary(libName)) {
            //if (item.reason == ModuleIncludeReasons.PRODUCT_MODULES) {
            //  Span.current().addEvent("$libName is not included into module $moduleName as explicitly included in platform layout")
            //}
            continue
          }

          if (helper.hasLibraryInDependencyChainOfModuleDependencies(dependentModule = module, libraryName = libName, siblings = layout.includedModules)) {
            continue
          }

          projectLibraryData = ProjectLibraryData(libraryName = libName, packMode = LibraryPackMode.MERGED, reason = "<- $moduleName")
          libToMetadata.put(element.library!!, projectLibraryData)
        }
        else if (isLibraryAlwaysPackedIntoPlugin(libName)) {
          platformLayout!!.findProjectLibrary(libName)?.let {
            throw IllegalStateException("Library $libName must not be included into platform layout: $it")
          }

          if (layout.hasLibrary(libName)) {
            continue
          }

          projectLibraryData = ProjectLibraryData(
            libraryName = libName,
            packMode = LibraryPackMode.MERGED,
            reason = "<- $moduleName (always packed into plugin)",
          )
          libToMetadata.put(element.library!!, projectLibraryData)
        }
        else {
          continue
        }
      }

      val library = element.library!!
      val libraryName = getLibraryFileName(library)
      if (excluded.contains(libraryName) || alreadyHasLibrary(layout, libraryName)) {
        continue
      }

      val targetFile = outDir.resolve(item.relativeOutputFile)
      val files = getLibraryFiles(library = library, copiedFiles = copiedFiles, targetFile = targetFile)
      for (i in (files.size - 1) downTo 0) {
        val file = files.get(i)
        val fileName = file.fileName.toString()
        if (item.reason != ModuleIncludeReasons.PRODUCT_MODULES &&
            isSeparateJar(fileName = fileName, file = file, jarPath = asset.relativePath)) {
          files.removeAt(i)
          addLibrary(
            library = library,
            targetFile = outDir.resolve(removeVersionFromJar(fileName)),
            relativeOutputFile = item.relativeOutputFile,
            files = listOf(file),
          )
        }
      }

      for (file in files) {
        @Suppress("NAME_SHADOWING")
        asset.addSource(
          ZipSource(
            file = file,
            distributionFileEntryProducer = { size, hash, targetFile ->
              if (projectLibraryData == null) {
                ModuleLibraryFileEntry(
                  path = targetFile,
                  moduleName = moduleName,
                  libraryName = LibraryLicensesListGenerator.getLibraryName(library),
                  libraryFile = file,
                  hash = hash,
                  size = size,
                  relativeOutputFile = item.relativeOutputFile,
                )
              }
              else {
                ProjectLibraryEntry(
                  path = targetFile,
                  libraryFile = file,
                  size = size,
                  hash = hash,
                  data = projectLibraryData,
                  relativeOutputFile = item.relativeOutputFile,
                )
              }
            },
            isPreSignedAndExtractedCandidate = asset.nativeFiles != null || isLibPreSigned(library),
          )
        )
      }
    }
  }

  private fun computeModuleCustomLibrarySources(layout: BaseLayout) {
    for (item in layout.includedModuleLibraries) {
      val library = context.findRequiredModule(item.moduleName).libraryCollection.libraries
                      .find { getLibraryFileName(it) == item.libraryName }
                    ?: throw IllegalArgumentException("Cannot find library ${item.libraryName} in \'${item.moduleName}\' module")
      var relativePath = item.relativeOutputPath
      if (relativePath.endsWith(".jar")) {
        val targetFile = outDir.resolve(relativePath)
        if (!relativePath.contains('/')) {
          relativePath = ""
        }

        addLibrary(
          library = library,
          targetFile = targetFile,
          relativeOutputFile = relativePath,
          files = getLibraryFiles(library = library, copiedFiles = copiedFiles, targetFile = targetFile)
        )
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
        addLibrary(
          library = library,
          targetFile = targetFile,
          relativeOutputFile = relativePath,
          files = getLibraryFiles(library = library, copiedFiles = copiedFiles, targetFile = targetFile)
        )
      }
    }
  }

  private fun alreadyHasLibrary(layout: BaseLayout, libraryName: String): Boolean {
    return layout.includedModuleLibraries.any { it.libraryName == libraryName && !it.extraCopy }
  }

  private fun mergeLibsByPredicate(
    jarName: String,
    libraryToMerge: MutableMap<JpsLibrary, List<Path>>,
    outputDir: Path,
    predicate: (String, JetBrainsClientModuleFilter) -> Boolean,
    clientModuleFilter: JetBrainsClientModuleFilter
  ) {
    val result = LinkedHashMap<JpsLibrary, List<Path>>()
    val iterator = libraryToMerge.entries.iterator()
    while (iterator.hasNext()) {
      val (key, value) = iterator.next()
      if (predicate(key.name, clientModuleFilter)) {
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
    val descriptor = getJarAsset(targetFile = uberJarFile, relativeOutputFile = "", metaInfDir = null)
    for ((key, value) in libraryToMerge) {
      filesToSourceWithMapping(asset = descriptor, files = value, library = key, relativeOutputFile = null)
    }
  }

  private fun computeProjectLibrariesSources(
    outDir: Path,
    layout: BaseLayout,
    copiedFiles: MutableMap<CopiedForKey, CopiedFor>,
    clientModuleFilter: JetBrainsClientModuleFilter
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
      if (packMode == LibraryPackMode.MERGED && !predefinedMergeRules.values.any { it(libName, clientModuleFilter) } && !isLibraryMergeable(libName)) {
        packMode = LibraryPackMode.STANDALONE_MERGED
      }

      val outPath = libraryData.outPath
      if (packMode == LibraryPackMode.MERGED && outPath == null) {
        toMerge.put(library, getLibraryFiles(library = library, copiedFiles = copiedFiles, targetFile = null))
      }
      else {
        var libOutputDir = outDir
        if (outPath != null) {
          if (outPath.endsWith(".jar")) {
            val targetFile = outDir.resolve(outPath)
            filesToSourceWithMapping(
              asset = getJarAsset(targetFile = targetFile, relativeOutputFile = outPath, metaInfDir = null),
              files = getLibraryFiles(library = library, copiedFiles = copiedFiles, targetFile = targetFile),
              library = library,
              relativeOutputFile = outPath,
            )
            continue
          }

          libOutputDir = outDir.resolve(outPath)
        }

        if (packMode == LibraryPackMode.STANDALONE_MERGED) {
          val targetFile = libOutputDir.resolve(nameToJarFileName(libName))
          addLibrary(
            library = library,
            targetFile = targetFile,
            relativeOutputFile = if (outDir == libOutputDir) "" else outDir.relativize(targetFile).invariantSeparatorsPathString,
            files = getLibraryFiles(library = library, copiedFiles = copiedFiles, targetFile = targetFile),
          )
        }
        else {
          for (file in library.getPaths(JpsOrderRootType.COMPILED)) {
            var fileName = file.fileName.toString()
            if (packMode == LibraryPackMode.STANDALONE_SEPARATE_WITHOUT_VERSION_NAME) {
              fileName = removeVersionFromJar(fileName)
            }

            val targetFile = libOutputDir.resolve(fileName)
            addLibrary(
              library = library,
              targetFile = targetFile,
              relativeOutputFile = if (outDir == libOutputDir) "" else outDir.relativize(targetFile).invariantSeparatorsPathString,
              files = listOf(file),
            )
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
                libraryName = LibraryLicensesListGenerator.getLibraryName(library),
                libraryFile = file,
                hash = hash,
                size = size,
                relativeOutputFile = relativeOutputFile,
              )
            } ?: ProjectLibraryEntry(
              path = targetFile,
              data = libToMetadata.get(library) ?: throw IllegalStateException("Metadata not found for $libraryName"),
              libraryFile = file,
              hash = hash,
              size = size,
              relativeOutputFile = relativeOutputFile,
            )
          },
        )
      )
    }
  }

  private fun addLibrary(library: JpsLibrary, targetFile: Path, relativeOutputFile: String, files: List<Path>) {
    filesToSourceWithMapping(
      asset = getJarAsset(targetFile = targetFile, relativeOutputFile = relativeOutputFile, metaInfDir = null),
      files = files,
      library = library,
      relativeOutputFile = relativeOutputFile,
    )
  }

  private fun getJarAsset(targetFile: Path, relativeOutputFile: String, metaInfDir: Path?): AssetDescriptor {
    return assets.computeIfAbsent(targetFile) {
      createAssetDescriptor(
        outDir = outDir,
        targetFile = targetFile,
        relativeOutputFile = relativeOutputFile,
        context = context,
        metaInfDir = metaInfDir,
      )
    }
  }
}

private suspend fun isSeparateJar(fileName: String, file: Path, jarPath: String): Boolean {
  if (jarPath.contains('/')) {
    return true
  }

  if (fileName.endsWith("-rt.jar") || fileName.contains("-agent")) {
    return true
  }

  if (!fileName.startsWith("maven-")) {
    return false
  }

  val filePreventingMerging = "META-INF/sisu/javax.inject.Named"
  val result = withContext(Dispatchers.IO) {
    ImmutableZipFile.load(file).use {
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
) {
  @JvmField
  val sources: MutableList<Source> = mutableListOf()

  fun addSource(source: Source) {
    sources.add(source)
  }

  @JvmField
  val includedModules: IdentityHashMap<ModuleItem, MutableList<Source>> = IdentityHashMap()
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
private val excludedFromMergeLibs = java.util.Set.of(
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
  java.util.List.of(
    fs.getPathMatcher("glob:**/icon-robots.txt"),
    fs.getPathMatcher("glob:icon-robots.txt"),
    fs.getPathMatcher("glob:.unmodified"),
    // compilation cache on TC
    fs.getPathMatcher("glob:.hash"),
    fs.getPathMatcher("glob:classpath.index"),
  )
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
  layout: BaseLayout?
): Map<ZipSource, List<String>> {
  checkAssetUniqueness(assets)

  if (dryRun) {
    return emptyMap()
  }

  val list = withContext(Dispatchers.IO) {
    assets.map { asset ->
      async {
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
          .useWithoutActiveScope { span ->
            asset.effectiveFile = cache.computeIfAbsent(
              sources = sources,
              targetFile = file,
              nativeFiles = nativeFileHandler?.sourceToNativeFiles,
              span = span,
              producer = object : SourceBuilder {
                override val useCacheAsTargetFile: Boolean
                  get() = useCacheAsTargetFile && !asset.relativePath.contains('/')

                override fun updateDigest(digest: HashStream64) {
                  if (layout is PluginLayout) {
                    digest.putString(layout.mainModule)
                  }
                  else {
                    digest.putByte(0)
                  }
                }

                override suspend fun produce(targetFile: Path) {
                  buildJar(targetFile = targetFile, sources = sources, nativeFileHandler = nativeFileHandler, notify = false)
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
    return name.endsWith(".jnilib") ||
           name.endsWith(".dylib") ||
           name.endsWith(".so") ||
           name.endsWith(".exe") ||
           name.endsWith(".dll") ||
           name.endsWith(".node") ||
           name.endsWith(".tbd")
  }

  @Suppress("SpellCheckingInspection", "GrazieInspection")
  override suspend fun sign(name: String, dataSupplier: () -> ByteBuffer): Path? {
    if (!context.isMacCodeSignEnabled || context.proprietaryBuildTools.signTool.signNativeFileMode != SignNativeFileMode.ENABLED) {
      return null
    }

    // we allow to use .so for macOS binraries (binaries/macos/libasyncProfiler.so), but removing obvious linux binaries
    // (binaries/linux-aarch64/libasyncProfiler.so) to avoid detecting by binary content
    if (name.endsWith(".dll") || name.endsWith(".exe") || name.contains("/linux/") || name.contains("/linux-")) {
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
    sources = moduleNames.map { moduleName ->
      DirSource(dir = context.getModuleOutputDir(context.findRequiredModule(moduleName)), excludes = commonModuleExcludes)
    },
  )
}

private fun createAssetDescriptor(
  outDir: Path,
  relativeOutputFile: String,
  targetFile: Path,
  context: BuildContext,
  metaInfDir: Path?,
): AssetDescriptor {
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

  val nativeFiles = metaInfDir?.resolve("native-files-list")?.takeIf { Files.isRegularFile(it) }?.readLines()
  return AssetDescriptor(
    isDir = false,
    file = targetFile,
    relativePath = relativeOutputFile,
    pathInClassLog = pathInClassLog,
    nativeFiles = nativeFiles,
  )
}

// also, put libraries from Maven repo ahead of others, for them to not depend on the lexicographical order of Maven repo and source path
private fun isFromLocalMavenRepo(path: Path) = path.startsWith(MAVEN_REPO)

private fun computeDistributionFileEntries(
  asset: AssetDescriptor,
  hasher: HashStream64,
  list: MutableList<DistributionFileEntry>,
  dryRun: Boolean,
  cacheManager: JarCacheManager,
) {
  for ((module, sources) in asset.includedModules) {
    if (asset.isDir) {
      val single = sources.singleOrNull()
      if (single is DirSource && single.exist == false) {
        // do not add ModuleOutputEntry for non-existent directory
        break
      }
    }

    var size = 0
    hasher.reset()
    hasher.putInt(sources.size)
    for (source in sources) {
      size += source.size
      if (!dryRun) {
        cacheManager.validateHash(source)
        hasher.putLong(source.hash)
        hasher.putInt(source.size)
      }
    }

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
    (source as? ZipSource)?.distributionFileEntryProducer
      ?.consume(size = source.size, hash = source.hash, targetFile = asset.effectiveFile)?.let(list::add)
  }
}

private fun updateModuleSourceHash(asset: AssetDescriptor) {
  for (sources in asset.includedModules.values) {
    for (source in sources) {
      if (source is FileSource) {
        continue
      }

      check(source is DirSource)
      if (source.hash == 0L) {
        source.hash = computeHashForModuleOutput(source)
      }
    }
  }
}

internal fun computeHashForModuleOutput(source: DirSource): Long {
  val markFile = source.dir.resolve(UNMODIFIED_MARK_FILE_NAME)
  try {
    return Files.getLastModifiedTime(markFile).toMillis()
  }
  catch (e: NoSuchFileException) {
    if (createMarkFile(markFile)) {
      return Files.getLastModifiedTime(markFile).toMillis()
    }
    else {
      source.exist = false
      // module doesn't exist at all
      return 0
    }
  }
}