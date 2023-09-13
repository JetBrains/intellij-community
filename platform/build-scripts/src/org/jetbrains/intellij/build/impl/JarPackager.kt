// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build.impl

import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.util.PathUtilRt
import com.intellij.util.io.URLUtil
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.lang.ImmutableZipFile
import com.jetbrains.util.filetype.FileType
import com.jetbrains.util.filetype.FileTypeDetector.DetectFileType
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.IntConsumer
import kotlin.io.path.invariantSeparatorsPathString

private val JAR_NAME_WITH_VERSION_PATTERN = "(.*)-\\d+(?:\\.\\d+)*\\.jar*".toPattern()
private val isUnpackedDist = System.getProperty("idea.dev.build.unpacked").toBoolean()

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
private val libsThatUsedInJps = java.util.Set.of(
  "ASM",
  "netty-buffer",
  "netty-codec-http",
  "netty-handler-proxy",
  "gson",
  "Log4J",
  "Slf4j",
  "slf4j-jdk14",
  // see getBuildProcessApplicationClasspath - used in JPS
  "lz4-java",
  "jna",
  "maven-resolver-provider",
  "OroMatcher",
  "jgoodies-forms",
  "jgoodies-common",
  "NanoXML",
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

private val notImportantKotlinLibs = persistentSetOf(
  "kotlinx-coroutines-guava",
  "kotlinx-datetime-jvm",
  "kotlinx-html-jvm",
)

private val predefinedMergeRules = persistentMapOf<String, (String, JetBrainsClientModuleFilter) -> Boolean>().mutate { map ->
  map.put("groovy.jar") { it, _ -> it.startsWith("org.codehaus.groovy:") }
  map.put("jsch-agent.jar") { it, _ -> it.startsWith("jsch-agent") }
  map.put("rd.jar") { it, _ -> it.startsWith("rd-") }
  // all grpc garbage into one jar
  map.put("grpc.jar") { it, _ -> it.startsWith("grpc-") }
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
  map.put(UTIL_JAR) { it, _ -> it == "pty4j" || it == "jvm-native-trusted-roots" }
}

internal fun getLibraryFileName(library: JpsLibrary): String {
  val name = library.name
  if (!name.startsWith("#")) {
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
  private val jarDescriptors = LinkedHashMap<Path, JarDescriptor>()
  private val libraryEntries = ConcurrentLinkedQueue<LibraryFileEntry>()
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
                     dryRun: Boolean = false,
                     context: BuildContext): Collection<DistributionFileEntry> {
      val packager = JarPackager(outputDir = outputDir, platformLayout = platformLayout, isRootDir = isRootDir, context = context)

      // must be concurrent - buildJars executed in parallel
      val moduleNameToSize = ConcurrentHashMap<String, Int>()
      val unpackedModules = packager.packModules(includedModules = includedModules,
                                                 moduleNameToSize = moduleNameToSize,
                                                 moduleOutputPatcher = moduleOutputPatcher,
                                                 layout = layout)

      for (item in (layout?.includedModuleLibraries ?: emptyList())) {
        val library = context.findRequiredModule(item.moduleName).libraryCollection.libraries
                        .find { getLibraryFileName(it) == item.libraryName }
                      ?: throw IllegalArgumentException("Cannot find library ${item.libraryName} in \'${item.moduleName}\' module")
        var fileName = nameToJarFileName(item.libraryName)
        var relativePath = item.relativeOutputPath
        var targetFile: Path? = null
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
        if (!relativePath.isEmpty()) {
          targetFile = outputDir.resolve(relativePath).resolve(fileName)
        }
        if (targetFile == null) {
          targetFile = outputDir.resolve(fileName)
        }
        packager.addLibrary(
          library = library,
          targetFile = targetFile!!,
          files = getLibraryFiles(library = library, copiedFiles = packager.copiedFiles, isModuleLevel = true, targetFile = targetFile)
        )
      }

      if (layout != null) {
        val clientModuleFilter = context.jetBrainsClientModuleFilter
        val libraryToMerge = packager.packProjectLibraries(outputDir = outputDir, layout = layout, copiedFiles = packager.copiedFiles,
                                                           clientModuleFilter)
        if (isRootDir) {
          for ((key, value) in predefinedMergeRules) {
            packager.mergeLibsByPredicate(key, libraryToMerge, outputDir, value, clientModuleFilter)
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
          packager.filesToSourceWithMappings(outputDir.resolve(mainJarName), libraryToMerge)
        }
      }

      val cacheManager = if (dryRun || context !is BuildContextImpl) NonCachingJarCacheManager else context.jarCacheManager
      val nativeFiles = buildJars(descriptors = packager.jarDescriptors.values,
                                  cache = cacheManager,
                                  context = context,
                                  isCodesignEnabled = isCodesignEnabled,
                                  dryRun = dryRun)
      return coroutineScope {
        if (nativeFiles.isNotEmpty()) {
          packNativePresignedFiles(nativeFiles = nativeFiles, dryRun = dryRun, context = context)
        }

        val list = mutableListOf<DistributionFileEntry>()
        for (item in packager.jarDescriptors.values) {
          for (module in item.includedModules) {
            val moduleName = module.moduleName
            val size = moduleNameToSize.get(moduleName)
                       ?: throw IllegalStateException("Size is not set for $moduleName (moduleNameToSize=$moduleNameToSize)")
            list.add(ModuleOutputEntry(path = item.file, moduleName = moduleName, size = size, reason = module.reason))
          }
        }

        // sort because projectStructureMapping is a concurrent collection
        // call invariantSeparatorsPathString because the result of Path ordering is platform-dependent

        // also, put libraries from Maven repo ahead of others, for them to not depend on the lexicographical order of Maven repo and source path
        fun isFromLocalMavenRepo(path: Path) = path.startsWith(MAVEN_REPO)

        list +
        unpackedModules.sortedWith(compareBy({ it.moduleName }, { it.path.invariantSeparatorsPathString })) +
        packager.libraryEntries.sortedWith(compareBy({ it.path.invariantSeparatorsPathString },
                                                     { it.type },
                                                     { it.libraryFile?.let(::isFromLocalMavenRepo) != true },
                                                     { it.libraryFile?.invariantSeparatorsPathString }))
      }
    }
  }

  private suspend fun packModules(includedModules: Collection<ModuleItem>,
                                  moduleNameToSize: ConcurrentHashMap<String, Int>,
                                  moduleOutputPatcher: ModuleOutputPatcher,
                                  layout: BaseLayout?): List<ModuleOutputEntry> {
    val unpackedModules = mutableListOf<ModuleOutputEntry>()
    for (item in includedModules) {
      packModule(item = item,
                 moduleOutputPatcher = moduleOutputPatcher,
                 layout = layout,
                 moduleNameToSize = moduleNameToSize,
                 unpackedModules = unpackedModules)
    }

    if (layout !is PluginLayout || !layout.auto) {
      return unpackedModules
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

      packModule(item = moduleItem,
                 moduleOutputPatcher = moduleOutputPatcher,
                 layout = layout,
                 moduleNameToSize = moduleNameToSize,
                 unpackedModules = unpackedModules)
    }
    return unpackedModules
  }

  private suspend fun packModule(item: ModuleItem,
                                 moduleOutputPatcher: ModuleOutputPatcher,
                                 layout: BaseLayout?,
                                 moduleNameToSize: ConcurrentHashMap<String, Int>,
                                 unpackedModules: MutableList<ModuleOutputEntry>) {
    val moduleName = item.moduleName
    val patchedDirs = moduleOutputPatcher.getPatchedDir(moduleName)
    val patchedContent = moduleOutputPatcher.getPatchedContent(moduleName)

    val searchableOptionsModuleDir = context.paths.searchableOptionDir.resolve(moduleName).takeIf {
      withContext(Dispatchers.IO) {
        Files.exists(it)
      }
    }

    val module = context.findRequiredModule(moduleName)
    val moduleOutputDir = context.getModuleOutputDir(module)
    val extraExcludes = layout?.moduleExcludes?.get(moduleName) ?: emptyList()

    val packToDir = isUnpackedDist && layout is PlatformLayout && patchedContent.isEmpty() && extraExcludes.isEmpty()

    val descriptor = jarDescriptors.computeIfAbsent(outputDir.resolve(item.relativeOutputFile)) { jarFile ->
      createJarDescriptor(outputDir = outputDir, targetFile = jarFile, context = context)
    }
    descriptor.includedModules = descriptor.includedModules.add(item)

    val sourceList: MutableList<Source>
    if (packToDir) {
      sourceList = mutableListOf()
      // suppress assert
      moduleNameToSize.putIfAbsent(moduleName, 0)
    }
    else {
      sourceList = descriptor.sources
    }

    val sizeConsumer = IntConsumer {
      moduleNameToSize.merge(moduleName, it) { oldValue, value -> oldValue + value }
    }

    for (entry in patchedContent) {
      sourceList.add(InMemoryContentSource(entry.key, entry.value, sizeConsumer))
    }

    // must be before module output to override
    for (moduleOutputPatch in patchedDirs) {
      sourceList.add(DirSource(dir = moduleOutputPatch, sizeConsumer = sizeConsumer))
    }

    if (searchableOptionsModuleDir != null) {
      sourceList.add(DirSource(dir = searchableOptionsModuleDir, sizeConsumer = sizeConsumer))
    }

    val excludes = if (extraExcludes.isEmpty()) {
      commonModuleExcludes
    }
    else {
      val fileSystem = FileSystems.getDefault()
      commonModuleExcludes + extraExcludes.map { fileSystem.getPathMatcher("glob:$it") }
    }
    sourceList.add(DirSource(dir = moduleOutputDir, excludes = excludes, sizeConsumer = sizeConsumer))

    if (layout != null) {
      packModuleLibs(item = item, module = module, layout = layout, copiedFiles = copiedFiles, sources = descriptor.sources)
    }

    if (packToDir) {
      for (source in sourceList) {
        unpackedModules.add(ModuleOutputEntry(moduleName = moduleName, path = (source as DirSource).dir, size = 0))
      }
    }
  }

  private suspend fun packModuleLibs(item: ModuleItem,
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
          val name = element.library!!.name
          if (platformLayout!!.hasLibrary(name) || layout.hasLibrary(name)) {
            continue
          }

          if (helper.hasLibraryInDependencyChainOfModuleDependencies(module, name, layout.includedModules)) {
            continue
          }
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
        sources.add(ZipSource(file) { size ->
          val entry = if (isModuleLevel) {
            ModuleLibraryFileEntry(path = targetFile,
                                   moduleName = moduleName,
                                   libraryName = LibraryLicensesListGenerator.getLibraryName(library),
                                   libraryFile = file,
                                   size = size)
          }
          else {
            ProjectLibraryEntry(path = targetFile,
                                libraryFile = file,
                                size = size,
                                data = ProjectLibraryData(libraryName, LibraryPackMode.MERGED, reason = "<- $moduleName"))

          }
          libraryEntries.add(entry)
        })
      }
    }
  }

  private suspend fun isSeparateJar(fileName: String, file: Path): Boolean {
    if (fileName.endsWith("-rt.jar") || fileName.contains("-agent")) {
      return true
    }

    val result = withContext(Dispatchers.IO) {
      ImmutableZipFile.load(file).use {
        @Suppress("SpellCheckingInspection")
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
      filesToSourceWithMapping(to = sources, files = value, library = key, targetFile = uberJarFile)
    }
  }

  private fun packProjectLibraries(outputDir: Path,
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

  private fun filesToSourceWithMapping(to: MutableList<Source>, files: List<Path>, library: JpsLibrary, targetFile: Path) {
    val moduleName = (library.createReference().parentReference as? JpsModuleReference)?.moduleName
    val isPreSignedCandidate = isRootDir && presignedLibNames.contains(library.name)
    for (file in files) {
      to.add(ZipSource(file = file, isPreSignedAndExtractedCandidate = isPreSignedCandidate) { size ->
        val libraryEntry = moduleName?.let {
          ModuleLibraryFileEntry(
            path = targetFile,
            moduleName = it,
            libraryName = LibraryLicensesListGenerator.getLibraryName(library),
            libraryFile = file,
            size = size,
          )
        } ?: ProjectLibraryEntry(
          path = targetFile,
          data = libToMetadata.get(library)!!,
          libraryFile = file,
          size = size,
        )

        libraryEntries.add(libraryEntry)
      })
    }
  }

  private fun addLibrary(library: JpsLibrary, targetFile: Path, files: List<Path>) {
    filesToSourceWithMapping(to = getJarDescriptorSources(targetFile),
                             files = files,
                             library = library,
                             targetFile = targetFile)
  }

  private fun getJarDescriptorSources(targetFile: Path): MutableList<Source> {
    return jarDescriptors.computeIfAbsent(targetFile) {
      createJarDescriptor(outputDir = outputDir, targetFile = targetFile, context = context)
    }.sources
  }
}

internal data class JarDescriptor(@JvmField val file: Path, @JvmField val pathInClassLog: String) {
  @JvmField
  val sources: MutableList<Source> = mutableListOf()

  @JvmField
  var includedModules: PersistentList<ModuleItem> = persistentListOf()
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

private suspend fun buildJars(descriptors: Collection<JarDescriptor>,
                              cache: JarCacheManager,
                              context: BuildContext,
                              isCodesignEnabled: Boolean,
                              dryRun: Boolean): Map<ZipSource, List<String>> {
  val uniqueFiles = HashMap<Path, List<Source>>()
  for (descriptor in descriptors) {
    val existing = uniqueFiles.putIfAbsent(descriptor.file, descriptor.sources)
    check(existing == null) {
      "File ${descriptor.file} is already associated." +
      "\nPrevious:\n  ${existing!!.joinToString(separator = "\n  ")}" +
      "\nCurrent:\n  ${descriptor.sources.joinToString(separator = "\n  ")}"
    }
  }

  @Suppress("GrazieInspection")
  val list = withContext(Dispatchers.IO) {
    descriptors.map { item ->
      async {
        val nativeFileHandler = if (isCodesignEnabled) {
          object : NativeFileHandler {
            override val sourceToNativeFiles = HashMap<ZipSource, List<String>>()

            @Suppress("SpellCheckingInspection", "GrazieInspection")
            override suspend fun sign(name: String, dataSupplier: () -> ByteBuffer): Path? {
              if (!context.isMacCodeSignEnabled ||
                  context.proprietaryBuildTools.signTool.signNativeFileMode != SignNativeFileMode.ENABLED) {
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
        }
        else {
          null
        }

        val file = item.file
        spanBuilder("build jar")
          .setAttribute("jar", file.toString())
          .setAttribute(AttributeKey.stringArrayKey("sources"), item.sources.map(Source::toString))
          .use { span ->
            if (item.sources.isEmpty()) {
              return@async emptyMap()
            }
            else {
              cache.computeIfAbsent(item = item, nativeFiles = nativeFileHandler?.sourceToNativeFiles, span = span) {
                buildJar(targetFile = file, sources = item.sources, dryRun = dryRun, nativeFileHandler = nativeFileHandler)
              }
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

suspend fun buildJar(targetFile: Path,
                     moduleNames: List<String>,
                     context: BuildContext,
                     dryRun: Boolean = false,
                     compress: Boolean = false) {
  buildJar(
    targetFile = targetFile,
    sources = moduleNames.map { moduleName ->
      DirSource(dir = context.getModuleOutputDir(context.findRequiredModule(moduleName)), excludes = commonModuleExcludes)
    },
    dryRun = dryRun,
    compress = compress,
  )
}

private fun createJarDescriptor(outputDir: Path, targetFile: Path, context: BuildContext): JarDescriptor {
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

  return JarDescriptor(file = targetFile, pathInClassLog = pathInClassLog)
}