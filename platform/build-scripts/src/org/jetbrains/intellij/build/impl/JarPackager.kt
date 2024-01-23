// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build.impl

import com.dynatrace.hash4j.hashing.HashStream64
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.platform.diagnostic.telemetry.helpers.use
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
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.security.MessageDigest
import java.util.*
import kotlin.io.path.invariantSeparatorsPathString

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
  "pty4j", "jna", "sqlite-native", "async-profiler", "jetbrains.skiko.awt.runtime.all"
)

private val notImportantKotlinLibs = java.util.Set.of(
  "kotlinx-coroutines-guava",
  "kotlinx-datetime-jvm",
  "kotlinx-html-jvm",
)

private val predefinedMergeRules = HashMap<String, (String, JetBrainsClientModuleFilter) -> Boolean>().let { map ->
  map.put("groovy.jar") { it, _ -> it.startsWith("org.codehaus.groovy:") }
  map.put("jsch-agent.jar") { it, _ -> it.startsWith("jsch-agent") }
  map.put("rd.jar") { it, _ -> it.startsWith("rd-") }
  // all grpc garbage into one jar
  map.put("grpc.jar") { it, _ -> it.startsWith("grpc-") }
  // separate file to use in Gradle Daemon classpath
  map.put("opentelemetry.jar") { it, _ -> it == "opentelemetry" || it.startsWith("opentelemetry-exporter-otlp") }
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

class JarPackager private constructor(private val outputDir: Path,
                                      private val context: BuildContext,
                                      private val platformLayout: PlatformLayout?,
                                      private val isRootDir: Boolean) {
  private val jarDescriptors = LinkedHashMap<Path, AssetDescriptor>()
  private val dirDescriptors = LinkedHashMap<Path, AssetDescriptor>()

  private val libToMetadata = HashMap<JpsLibrary, ProjectLibraryData>()
  private val copiedFiles = HashMap<Path, CopiedFor>()

  private val helper = JarPackagerDependencyHelper(context)

  companion object {
    suspend fun pack(includedModules: Collection<ModuleItem>,
                     outputDir: Path,
                     isRootDir: Boolean,
                     isCodesignEnabled: Boolean = true,
                     layout: BaseLayout?,
                     platformLayout: PlatformLayout?,
                     moduleOutputPatcher: ModuleOutputPatcher = ModuleOutputPatcher(),
                     dryRun: Boolean,
                     moduleWithSearchableOptions: Set<String> = emptySet(),
                     context: BuildContext): Collection<DistributionFileEntry> {

      val packager = JarPackager(outputDir = outputDir, platformLayout = platformLayout, isRootDir = isRootDir, context = context)
      packager.computeModuleSources(includedModules = includedModules,
                                    moduleOutputPatcher = moduleOutputPatcher,
                                    moduleWithSearchableOptions = moduleWithSearchableOptions,
                                    layout = layout)
      if (layout != null) {
        packager.computeModuleCustomLibrarySources(layout)

        val clientModuleFilter = context.jetBrainsClientModuleFilter
        val libraryToMerge = packager.computeProjectLibrariesSources(outputDir = outputDir,
                                                                     layout = layout,
                                                                     copiedFiles = packager.copiedFiles,
                                                                     clientModuleFilter = clientModuleFilter)
        if (isRootDir) {
          for ((key, value) in predefinedMergeRules) {
            packager.mergeLibsByPredicate(jarName = key,
                                          libraryToMerge = libraryToMerge,
                                          outputDir = outputDir,
                                          predicate = value,
                                          clientModuleFilter = clientModuleFilter)
          }
          if (!libraryToMerge.isEmpty()) {
            val clientLibraries = libraryToMerge.filterKeys { clientModuleFilter.isProjectLibraryIncluded(it.name) }
            if (clientLibraries.isNotEmpty()) {
              packager.filesToSourceWithMappings(outputDir.resolve(PlatformJarNames.LIB_CLIENT_JAR), clientLibraries)
            }
            val nonClientLibraries = libraryToMerge.filterKeys { !clientModuleFilter.isProjectLibraryIncluded(it.name) }
            if (nonClientLibraries.isNotEmpty()) {
              packager.filesToSourceWithMappings(outputDir.resolve(PlatformJarNames.LIB_JAR), nonClientLibraries)
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
      val nativeFiles = buildJars(descriptors = packager.jarDescriptors.values,
                                  layout = layout,
                                  cache = cacheManager,
                                  context = context,
                                  isCodesignEnabled = isCodesignEnabled,
                                  useCacheAsTargetFile = isUnpackedDist && !dryRun && layout !is PluginLayout,
                                  dryRun = dryRun)
      return coroutineScope {
        if (nativeFiles.isNotEmpty()) {
          launch {
            packNativePresignedFiles(nativeFiles = nativeFiles, dryRun = dryRun, context = context)
          }
        }

        val list = mutableListOf<DistributionFileEntry>()
        val hasher = Hashing.komihash5_0().hashStream()
        for (item in packager.jarDescriptors.values) {
          computeDistributionFileEntries(item = item, hasher = hasher, list = list, dryRun = dryRun, cacheManager = cacheManager)
        }
        for (item in packager.dirDescriptors.values) {
          computeDistributionFileEntries(item = item, hasher = hasher, list = list, dryRun = dryRun, cacheManager = cacheManager)
        }

        // sort because projectStructureMapping is a concurrent collection
        // call invariantSeparatorsPathString because the result of Path ordering is platform-dependent
        list.sortWith(compareBy(
          { it.path.invariantSeparatorsPathString },
          { it.type },
          { (it as? ModuleOutputEntry)?.moduleName },
          { (it as? LibraryFileEntry)?.libraryFile?.let(::isFromLocalMavenRepo) != true },
          { (it as? LibraryFileEntry)?.libraryFile?.invariantSeparatorsPathString },
        ))
        list
      }
    }
  }

  private suspend fun computeModuleSources(includedModules: Collection<ModuleItem>,
                                           moduleOutputPatcher: ModuleOutputPatcher,
                                           layout: BaseLayout?,
                                           moduleWithSearchableOptions: Set<String>) {
    for (item in includedModules) {
      computeSourcesForModule(item = item,
                              moduleOutputPatcher = moduleOutputPatcher,
                              layout = layout,
                              moduleWithSearchableOptions = moduleWithSearchableOptions)
    }

    if (layout !is PluginLayout || !layout.auto) {
      return
    }

    // for now, check only direct dependencies of the main plugin module
    val childPrefix = "${layout.mainModule}."
    for (name in helper.getModuleDependencies(layout.mainModule)) {
      if (includedModules.any { it.moduleName == name } || !name.startsWith(childPrefix)) {
        continue
      }

      val moduleItem = ModuleItem(moduleName = name, relativeOutputFile = layout.getMainJarName(), reason = "<- ${layout.mainModule}")
      if (platformLayout!!.includedModules.contains(moduleItem)) {
        continue
      }

      computeSourcesForModule(item = moduleItem,
                              moduleOutputPatcher = moduleOutputPatcher,
                              layout = layout,
                              moduleWithSearchableOptions = moduleWithSearchableOptions)
    }
  }

  private suspend fun computeSourcesForModule(item: ModuleItem,
                                              moduleOutputPatcher: ModuleOutputPatcher,
                                              layout: BaseLayout?,
                                              moduleWithSearchableOptions: Set<String>) {
    val moduleName = item.moduleName
    val patchedDirs = moduleOutputPatcher.getPatchedDir(moduleName)
    val patchedContent = moduleOutputPatcher.getPatchedContent(moduleName)

    val searchableOptionsModuleDir = if (moduleWithSearchableOptions.contains(moduleName)) {
      context.paths.searchableOptionDir.resolve(moduleName)
    }
    else {
      null
    }

    val module = context.findRequiredModule(moduleName)
    val moduleOutputDir = context.getModuleOutputDir(module)
    val extraExcludes = layout?.moduleExcludes?.get(moduleName) ?: emptyList()

    val packToDir = isUnpackedDist &&
                    layout is PlatformLayout &&
                    patchedContent.isEmpty() &&
                    patchedDirs.isEmpty() &&
                    extraExcludes.isEmpty() &&
                    !isModuleAlwaysPacked(moduleName)

    val outFile = outputDir.resolve(item.relativeOutputFile)
    val moduleSources = if (packToDir) {
      jarDescriptors.computeIfAbsent(outputDir.resolve("unpacked-${moduleOutputDir.fileName}.jar")) { jarFile ->
        createAssetDescriptor(outputDir = outputDir, targetFile = jarFile, context = context)
      }
    }
    else {
      jarDescriptors.computeIfAbsent(outFile) { jarFile ->
        createAssetDescriptor(outputDir = outputDir, targetFile = jarFile, context = context)
      }
    }.includedModules.computeIfAbsent(item) { mutableListOf() }

    val jarSources = getJarDescriptorSources(outFile)

    for (entry in patchedContent) {
      moduleSources.add(InMemoryContentSource(entry.key, entry.value))
    }

    // must be before module output to override
    for (dir in patchedDirs) {
      moduleSources.add(DirSource(dir = dir))
    }

    if (searchableOptionsModuleDir != null) {
      jarSources.add(DirSource(dir = searchableOptionsModuleDir))
    }

    val excludes = if (extraExcludes.isEmpty()) {
      commonModuleExcludes
    }
    else {
      val fileSystem = FileSystems.getDefault()
      commonModuleExcludes + extraExcludes.map { fileSystem.getPathMatcher("glob:$it") }
    }
    moduleSources.add(DirSource(dir = moduleOutputDir, excludes = excludes))

    if (layout != null) {
      computeSourcesForModuleLibs(item = item, module = module, layout = layout, copiedFiles = copiedFiles, sources = jarSources)
    }
  }

  private suspend fun computeSourcesForModuleLibs(item: ModuleItem,
                                                  layout: BaseLayout,
                                                  module: JpsModule,
                                                  copiedFiles: MutableMap<Path, CopiedFor>,
                                                  sources: MutableList<Source>) {
    if (item.relativeOutputFile.contains('/')) {
      return
    }

    val moduleName = module.name
    if (layout.modulesWithExcludedModuleLibraries.contains(moduleName)) {
      return
    }

    val includeProjectLib = layout is PluginLayout && layout.auto

    val excluded = layout.excludedModuleLibraries.get(moduleName)
    for (element in helper.getLibraryDependencies(module)) {
      var isModuleLevel = true
      val libraryReference = element.libraryReference
      if (libraryReference.parentReference !is JpsModuleReference) {
        if (includeProjectLib) {
          val library = element.library!!
          val name = library.name
          if (platformLayout!!.hasLibrary(name) || layout.hasLibrary(name)) {
            continue
          }

          if (helper.hasLibraryInDependencyChainOfModuleDependencies(dependentModule = module,
                                                                     libraryName = name,
                                                                     siblings = layout.includedModules)) {
            continue
          }

          libToMetadata.put(library, ProjectLibraryData(name, LibraryPackMode.MERGED, reason = "<- $moduleName"))
          isModuleLevel = false
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

      val targetFile = outputDir.resolve(item.relativeOutputFile)
      val files = getLibraryFiles(library = library, copiedFiles = copiedFiles, isModuleLevel = true, targetFile = targetFile)
      for (i in (files.size - 1) downTo 0) {
        val file = files.get(i)
        val fileName = file.fileName.toString()
        if (item.relativeOutputFile.contains('/') || isSeparateJar(fileName, file)) {
          files.removeAt(i)
          addLibrary(library, outputDir.resolve(removeVersionFromJar(fileName)), listOf(file))
        }
      }

      for (file in files) {
        @Suppress("NAME_SHADOWING")
        sources.add(ZipSource(file, distributionFileEntryProducer = { size, hash, targetFile ->
          if (isModuleLevel) {
            ModuleLibraryFileEntry(path = targetFile,
                                   moduleName = moduleName,
                                   libraryName = LibraryLicensesListGenerator.getLibraryName(library),
                                   libraryFile = file,
                                   hash = hash,
                                   size = size)
          }
          else {
            ProjectLibraryEntry(path = targetFile,
                                libraryFile = file,
                                size = size,
                                hash = hash,
                                data = ProjectLibraryData(libraryName, LibraryPackMode.MERGED, reason = "<- $moduleName"))

          }
        }))
      }
    }
  }

  private fun computeModuleCustomLibrarySources(layout: BaseLayout) {
    for (item in layout.includedModuleLibraries) {
      val library = context.findRequiredModule(item.moduleName).libraryCollection.libraries
                      .find { getLibraryFileName(it) == item.libraryName }
                    ?: throw IllegalArgumentException("Cannot find library ${item.libraryName} in \'${item.moduleName}\' module")
      var fileName = nameToJarFileName(item.libraryName)
      var relativePath = item.relativeOutputPath
      if (relativePath.endsWith(".jar")) {
        val index = relativePath.lastIndexOf('/')
        if (index == -1) {
          fileName = relativePath
          relativePath = ""
        }
        else {
          fileName = relativePath.substring(index + 1)
          relativePath = relativePath.substring(0, index)
        }
      }

      val targetFile = if (relativePath.isEmpty()) {
        outputDir.resolve(fileName)
      }
      else {
        outputDir.resolve(relativePath).resolve(fileName)
      }
      addLibrary(
        library = library,
        targetFile = targetFile,
        files = getLibraryFiles(library = library, copiedFiles = copiedFiles, isModuleLevel = true, targetFile = targetFile)
      )
    }
  }

  private suspend fun isSeparateJar(fileName: String, file: Path): Boolean {
    if (fileName.endsWith("-rt.jar") || fileName.contains("-agent")) {
      return true
    }

    val result = withContext(Dispatchers.IO) {
      ImmutableZipFile.load(file).use {
        it.getResource("META-INF/sisu/javax.inject.Named") != null
      }
    }
    if (result) {
      Span.current().addEvent("$fileName contains file that prevent merging")
    }
    return result
  }

  private fun alreadyHasLibrary(layout: BaseLayout, libraryName: String): Boolean {
    return layout.includedModuleLibraries.any { it.libraryName == libraryName && !it.extraCopy }
  }

  private fun mergeLibsByPredicate(jarName: String,
                                   libraryToMerge: MutableMap<JpsLibrary, List<Path>>,
                                   outputDir: Path,
                                   predicate: (String, JetBrainsClientModuleFilter) -> Boolean,
                                   clientModuleFilter: JetBrainsClientModuleFilter) {
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
    filesToSourceWithMappings(outputDir.resolve(jarName), result)
  }

  private fun filesToSourceWithMappings(uberJarFile: Path, libraryToMerge: Map<JpsLibrary, List<Path>>) {
    val sources = getJarDescriptorSources(targetFile = uberJarFile)
    for ((key, value) in libraryToMerge) {
      filesToSourceWithMapping(sources = sources, files = value, library = key)
    }
  }

  private fun computeProjectLibrariesSources(outputDir: Path,
                                             layout: BaseLayout,
                                             copiedFiles: MutableMap<Path, CopiedFor>,
                                             clientModuleFilter: JetBrainsClientModuleFilter): MutableMap<JpsLibrary, List<Path>> {
    val toMerge = LinkedHashMap<JpsLibrary, List<Path>>()
    val projectLibs = if (layout.includedProjectLibraries.isEmpty()) {
      emptyList()
    }
    else {
      layout.includedProjectLibraries.sortedBy { it.libraryName }
    }

    for (libraryData in projectLibs) {
      val library = context.project.libraryCollection.findLibrary(libraryData.libraryName)
                    ?: throw IllegalArgumentException("Cannot find library ${libraryData.libraryName} in the project")
      libToMetadata.put(library, libraryData)
      val libName = library.name
      var packMode = libraryData.packMode
      if (packMode == LibraryPackMode.MERGED && !predefinedMergeRules.values.any { it(libName, clientModuleFilter) } && !isLibraryMergeable(
          libName)) {
        packMode = LibraryPackMode.STANDALONE_MERGED
      }

      val outPath = libraryData.outPath
      val files = getLibraryFiles(library = library, copiedFiles = copiedFiles, isModuleLevel = false, targetFile = null)
      if (packMode == LibraryPackMode.MERGED && outPath == null) {
        toMerge.put(library, files)
      }
      else {
        var libOutputDir = outputDir
        if (outPath != null) {
          libOutputDir = if (outPath.endsWith(".jar")) {
            addLibrary(library = library, targetFile = outputDir.resolve(outPath), files = files)
            continue
          }
          else {
            outputDir.resolve(outPath)
          }
        }
        if (packMode == LibraryPackMode.STANDALONE_MERGED) {
          addLibrary(library = library, targetFile = libOutputDir.resolve(nameToJarFileName(libName)), files = files)
        }
        else {
          for (file in files) {
            var fileName = file.fileName.toString()
            if (packMode == LibraryPackMode.STANDALONE_SEPARATE_WITHOUT_VERSION_NAME) {
              fileName = removeVersionFromJar(fileName)
            }
            addLibrary(library = library, targetFile = libOutputDir.resolve(fileName), files = listOf(file))
          }
        }
      }
    }
    return toMerge
  }

  private fun filesToSourceWithMapping(sources: MutableList<Source>, files: List<Path>, library: JpsLibrary) {
    val moduleName = (library.createReference().parentReference as? JpsModuleReference)?.moduleName
    val isPreSignedCandidate = isRootDir && presignedLibNames.contains(library.name)
    for (file in files) {
      sources.add(ZipSource(file = file,
                            isPreSignedAndExtractedCandidate = isPreSignedCandidate,
                            distributionFileEntryProducer = { size, hash,  targetFile ->
                              moduleName?.let {
                                ModuleLibraryFileEntry(
                                  path = targetFile,
                                  moduleName = it,
                                  libraryName = LibraryLicensesListGenerator.getLibraryName(library),
                                  libraryFile = file,
                                  hash = hash,
                                  size = size,
                                )
                              } ?: ProjectLibraryEntry(
                                path = targetFile,
                                data = libToMetadata.get(library) ?: throw IllegalStateException("Metadata not found for ${library.name}"),
                                libraryFile = file,
                                hash = hash,
                                size = size,
                              )
                            }))
    }
  }

  private fun addLibrary(library: JpsLibrary, targetFile: Path, files: List<Path>) {
    filesToSourceWithMapping(sources = getJarDescriptorSources(targetFile), files = files, library = library)
  }

  private fun getJarDescriptorSources(targetFile: Path): MutableList<Source> {
    return jarDescriptors.computeIfAbsent(targetFile) {
      createAssetDescriptor(outputDir = outputDir, targetFile = targetFile, context = context)
    }.sources
  }
}

private data class AssetDescriptor(
  @JvmField val file: Path,
  @JvmField var effectiveFile: Path = file,
  @JvmField val pathInClassLog: String,
) {
  @JvmField
  val sources: MutableList<Source> = mutableListOf()

  @JvmField
  val includedModules: IdentityHashMap<ModuleItem, MutableList<Source>> = IdentityHashMap()
}

private fun removeVersionFromJar(fileName: String): String {
  val matcher = JAR_NAME_WITH_VERSION_PATTERN.matcher(fileName)
  return if (matcher.matches()) "${matcher.group(1)}.jar" else fileName
}

private fun getLibraryFiles(library: JpsLibrary,
                            copiedFiles: MutableMap<Path, CopiedFor>,
                            isModuleLevel: Boolean,
                            targetFile: Path?): MutableList<Path> {
  val files = library.getPaths(JpsOrderRootType.COMPILED)
  val libName = library.name

  // allow duplication if packed into the same target file and have the same common prefix
  files.removeIf {
    val alreadyCopiedFor = copiedFiles.get(it) ?: return@removeIf false
    val alreadyCopiedLibraryName = alreadyCopiedFor.library.name
    alreadyCopiedFor.targetFile == targetFile &&
    (alreadyCopiedLibraryName.startsWith("ktor-") ||
     alreadyCopiedLibraryName.startsWith("commons-") ||
     alreadyCopiedLibraryName.startsWith("ai.grazie.") ||
     (isModuleLevel && alreadyCopiedLibraryName == libName))
  }

  for (file in files) {
    val alreadyCopiedFor = copiedFiles.putIfAbsent(file, CopiedFor(library, targetFile))
    if (alreadyCopiedFor != null) {
      // check name - we allow having the same named module level library name
      if (isModuleLevel && alreadyCopiedFor.library.name == libName) {
        continue
      }

      throw IllegalStateException("File $file from $libName is already provided by ${alreadyCopiedFor.library.name} library")
    }
  }
  return files
}

private fun nameToJarFileName(name: String): String {
  return "${sanitizeFileName(name.lowercase(), replacement = "-")}.jar"
}

@Suppress("SpellCheckingInspection")
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

private data class CopiedFor(@JvmField val library: JpsLibrary, @JvmField val targetFile: Path?)

private suspend fun buildJars(descriptors: Collection<AssetDescriptor>,
                              cache: JarCacheManager,
                              context: BuildContext,
                              isCodesignEnabled: Boolean,
                              useCacheAsTargetFile: Boolean,
                              dryRun: Boolean,
                              layout: BaseLayout?): Map<ZipSource, List<String>> {
  val uniqueFiles = HashMap<Path, List<Source>>()
  for (descriptor in descriptors) {
    val existing = uniqueFiles.putIfAbsent(descriptor.file, descriptor.sources)
    check(existing == null) {
      "File ${descriptor.file} is already associated." +
      "\nPrevious:\n  ${existing!!.joinToString(separator = "\n  ")}" +
      "\nCurrent:\n  ${descriptor.sources.joinToString(separator = "\n  ")}"
    }
  }

  val list = withContext(Dispatchers.IO) {
    descriptors.map { item ->
      async {
        val nativeFileHandler = if (isCodesignEnabled) NativeFileHandlerImpl(context) else null
        val sources = mutableListOf<Source>()
        sources.addAll(item.sources)
        for (moduleSources in item.includedModules.values) {
          sources.addAll(moduleSources)
        }

        val file = item.file
        spanBuilder("build jar")
          .setAttribute("jar", file.toString())
          .setAttribute(AttributeKey.stringArrayKey("sources"), sources.map(Source::toString))
          .use { span ->
            if (sources.isEmpty()) {
              return@async emptyMap()
            }
            else if (!dryRun) {
              item.effectiveFile = cache.computeIfAbsent(
                sources = sources,
                targetFile = file,
                nativeFiles = nativeFileHandler?.sourceToNativeFiles,
                span = span,
                producer = object : SourceBuilder {
                  override val useCacheAsTargetFile: Boolean
                    get() = useCacheAsTargetFile

                  override fun updateDigest(digest: MessageDigest) {
                    if (layout is PluginLayout) {
                      val mainModule = layout.mainModule
                      digest.update(mainModule.length.toByte())
                      digest.update(mainModule.encodeToByteArray())
                    }
                    else {
                      digest.update(0)
                    }
                  }

                  override suspend fun produce() {
                    buildJar(targetFile = file, sources = sources, nativeFileHandler = nativeFileHandler, notify = false)
                  }
                }
              )
            }
          }

        if (!dryRun && item.pathInClassLog.isNotEmpty()) {
          reorderJar(relativePath = item.pathInClassLog, file = file)
        }
        nativeFileHandler?.sourceToNativeFiles ?: emptyMap()
      }
    }
  }

  val result = TreeMap<ZipSource, List<String>>(compareBy { it.file.fileName.toString() })
  list.asSequence().map { it.getCompleted() }.forEach(result::putAll)
  return result
}

private class NativeFileHandlerImpl(private val context: BuildContext) : NativeFileHandler {
  override val sourceToNativeFiles = HashMap<ZipSource, List<String>>()

  @Suppress("SpellCheckingInspection", "GrazieInspection")
  override suspend fun sign(name: String, dataSupplier: () -> ByteBuffer): Path? {
    if (!context.isMacCodeSignEnabled ||
        context.proprietaryBuildTools.signTool.signNativeFileMode != SignNativeFileMode.ENABLED) {
      return null
    }

    // we allow to use .so for macOS binraries (binaries/macos/libasyncProfiler.so), but removing obvious linux binaries
    // (binaries/linux-aarch64/libasyncProfiler.so) to avoid detecting by binary content
    if (
      name.endsWith(".dll") || name.endsWith(".exe") || name.contains("/linux/") || name.contains("/linux-") ||
      name.contains("icudtl.dat")
    ) {
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

private fun createAssetDescriptor(outputDir: Path, targetFile: Path, context: BuildContext): AssetDescriptor {
  var pathInClassLog = ""
  if (!context.isStepSkipped(BuildOptions.GENERATE_JAR_ORDER_STEP)) {
    if (context.paths.distAllDir == outputDir.parent) {
      pathInClassLog = outputDir.parent.relativize(targetFile).toString().replace(File.separatorChar, '/')
    }
    else if (outputDir.startsWith(context.paths.distAllDir)) {
      pathInClassLog = context.paths.distAllDir.relativize(targetFile).toString().replace(File.separatorChar, '/')
    }
    else {
      val parent = outputDir.parent
      if (parent?.fileName.toString() == "plugins") {
        pathInClassLog = parent.parent.relativize(targetFile).toString().replace(File.separatorChar, '/')
      }
    }
  }

  return AssetDescriptor(file = targetFile, pathInClassLog = pathInClassLog)
}

// also, put libraries from Maven repo ahead of others, for them to not depend on the lexicographical order of Maven repo and source path
private fun isFromLocalMavenRepo(path: Path) = path.startsWith(MAVEN_REPO)

private fun computeDistributionFileEntries(item: AssetDescriptor,
                                           hasher: HashStream64,
                                           list: MutableList<DistributionFileEntry>,
                                           dryRun: Boolean,
                                           cacheManager: JarCacheManager) {
  for ((module, sources) in item.includedModules) {
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
    list.add(ModuleOutputEntry(path = item.effectiveFile,
                               moduleName = module.moduleName,
                               size = size,
                               hash = hash,
                               reason = module.reason))
  }

  for (source in item.sources) {
    (source as? ZipSource)?.distributionFileEntryProducer
      ?.consume(source.size, source.hash, item.effectiveFile)?.let(list::add)
  }
}

private fun isModuleAlwaysPacked(moduleName: String): Boolean {
  return moduleName.endsWith(".resources") ||
         moduleName.contains(".resources.") ||
         moduleName.endsWith(".icons") ||
         // rarely modified
         moduleName.contains(".jps.") ||
         moduleName.contains(".scriptDebugger.") ||
         moduleName == "intellij.java.guiForms.rt" ||
         moduleName == "intellij.java.rt" ||
         moduleName == "intellij.platform.tips" ||
         moduleName.startsWith("intellij.platform.util.")
}
