// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.use
import com.intellij.util.PathUtilRt
import com.intellij.util.io.URLUtil
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.lang.HashMapZipFile
import io.opentelemetry.api.common.AttributeKey
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.projectStructureMapping.*
import org.jetbrains.intellij.build.io.W_CREATE_NEW
import org.jetbrains.intellij.build.tasks.*
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.IntConsumer
import kotlin.io.path.name

private val JAR_NAME_WITH_VERSION_PATTERN = "(.*)-\\d+(?:\\.\\d+)*\\.jar*".toPattern()

internal val BuildContext.searchableOptionDir: Path
  get() = paths.tempDir.resolve("searchableOptionsResult")

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
private val libsThatUsedInJps = java.util.Set.of(
  "ASM",
  "aalto-xml",
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
  // see ConsoleProcessListFetcher.getConsoleProcessCount
  "pty4j",
)

private val extraMergeRules: PersistentMap<String, (String) -> Boolean> = persistentMapOf<String, (String) -> Boolean>().mutate { map ->
  map.put("groovy.jar") { it.startsWith("org.codehaus.groovy:") }
  map.put("jsch-agent.jar") { it.startsWith("jsch-agent") }
  map.put("rd.jar") { it.startsWith("rd-") }
  map.put(PRODUCT_JAR) { it.startsWith("License") }
  // see ClassPathUtil.getUtilClassPath
  map.put("3rd-party-rt.jar") {
    libsThatUsedInJps.contains(it) || it.startsWith("kotlinx-") || it == "kotlin-reflect"
  }
  // used by intellij.database.jdbcConsole
  map.put("util.jar") { it == "jbr-api" }
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

class JarPackager private constructor(private val collectNativeFiles: Boolean,
                                      private val outputDir: Path,
                                      private val context: BuildContext) {
  private val jarDescriptors = LinkedHashMap<Path, JarDescriptor>()
  private val libraryEntries = ConcurrentLinkedQueue<LibraryFileEntry>()
  private val libToMetadata = HashMap<JpsLibrary, ProjectLibraryData>()
  private val copiedFiles = HashMap<Path, CopiedFor>()

  companion object {
    suspend fun pack(includedModules: Collection<ModuleItem>,
                     outputDir: Path,
                     layout: BaseLayout?,
                     moduleOutputPatcher: ModuleOutputPatcher = ModuleOutputPatcher(),
                     dryRun: Boolean = false,
                     context: BuildContext): Collection<DistributionFileEntry> {
      val packager = JarPackager(collectNativeFiles = layout !is PluginLayout, outputDir = outputDir, context = context)

      // must be concurrent - buildJars executed in parallel
      val moduleNameToSize = ConcurrentHashMap<String, Int>()
      packager.packModules(includedModules = includedModules,
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

      val isRootDir = context.paths.distAllDir == outputDir.parent
      if (layout != null) {
        val libraryToMerge = packager.packProjectLibraries(outputDir = outputDir,
                                                           layout = layout,
                                                           copiedFiles = packager.copiedFiles)
        if (isRootDir) {
          for ((key, value) in extraMergeRules) {
            packager.mergeLibsByPredicate(key, libraryToMerge, outputDir, value)
          }
          if (!libraryToMerge.isEmpty()) {
            packager.filesToSourceWithMappings(outputDir.resolve(APP_JAR), libraryToMerge)
          }
        }
        else if (!libraryToMerge.isEmpty()) {
          val mainJarName = (layout as PluginLayout).getMainJarName()
          check(includedModules.any { it.relativeOutputFile == mainJarName })
          packager.filesToSourceWithMappings(outputDir.resolve(mainJarName), libraryToMerge)
        }
      }

      val nativeFiles = buildJars(descriptors = packager.jarDescriptors.values, dryRun = dryRun)
      val list = mutableListOf<DistributionFileEntry>()

      if (nativeFiles.isNotEmpty()) {
        packNativePresignedFiles(nativeFiles = nativeFiles, context = context)
      }

      for (item in packager.jarDescriptors.values) {
        for (module in item.includedModules) {
          val moduleName = module.moduleName
          val size = moduleNameToSize.get(moduleName)
                     ?: throw IllegalStateException("Size is not set for $moduleName (moduleNameToSize=$moduleNameToSize)")
          list.add(ModuleOutputEntry(path = item.file, moduleName = moduleName, size = size, reason = module.reason))
        }
      }

      // sort because projectStructureMapping is a concurrent collection
      return list +
             packager.libraryEntries.sortedWith { a, b -> compareValuesBy(a, b, { it.path }, { it.type }, { it.libraryFile }) }
    }

    private suspend fun packNativePresignedFiles(nativeFiles: Map<ZipSource, MutableList<String>>, context: BuildContext) {
      coroutineScope {
        for (source in nativeFiles.keys.sortedBy { it.file.name }) {
          val paths = nativeFiles.getValue(source)
          val sourceFile = source.file
          async(Dispatchers.IO) {
            unpackNativeLibraries(sourceFile = sourceFile, paths = paths, context = context)
          }
          continue
        }
      }
    }
  }

  private fun packModules(includedModules: Collection<ModuleItem>,
                          moduleNameToSize: ConcurrentHashMap<String, Int>,
                          moduleOutputPatcher: ModuleOutputPatcher,
                          layout: BaseLayout?) {
    for (item in includedModules) {
      val descriptor = jarDescriptors.computeIfAbsent(outputDir.resolve(item.relativeOutputFile)) { jarFile ->
        createJarDescriptor(outputDir = outputDir,
                            targetFile = jarFile,
                            collectNativeFiles = collectNativeFiles,
                            context = context)
      }
      descriptor.includedModules = descriptor.includedModules.add(item)

      val sourceList = descriptor.sources
      val moduleName = item.moduleName
      val extraExcludes = layout?.moduleExcludes?.get(moduleName) ?: emptyList()

      val sizeConsumer = IntConsumer {
        moduleNameToSize.merge(moduleName, it) { oldValue, value -> oldValue + value }
      }

      for (entry in moduleOutputPatcher.getPatchedContent(moduleName)) {
        sourceList.add(InMemoryContentSource(entry.key, entry.value, sizeConsumer))
      }

      // must be before module output to override
      for (moduleOutputPatch in moduleOutputPatcher.getPatchedDir(moduleName)) {
        sourceList.add(DirSource(dir = moduleOutputPatch, sizeConsumer = sizeConsumer))
      }

      val searchableOptionsModuleDir = context.searchableOptionDir.resolve(moduleName)
      if (Files.exists(searchableOptionsModuleDir)) {
        sourceList.add(DirSource(dir = searchableOptionsModuleDir, sizeConsumer = sizeConsumer))
      }

      val excludes = if (extraExcludes.isEmpty()) {
        commonModuleExcludes
      }
      else {
        val fileSystem = FileSystems.getDefault()
        commonModuleExcludes + extraExcludes.map { fileSystem.getPathMatcher("glob:$it") }
      }
      sourceList.add(DirSource(dir = context.getModuleOutputDir(context.findRequiredModule(moduleName)),
                               excludes = excludes,
                               sizeConsumer = sizeConsumer))

      if (layout != null) {
        packModuleLibs(item = item, layout = layout, copiedFiles = copiedFiles, sources = descriptor.sources)
      }
    }
  }

  private fun packModuleLibs(item: ModuleItem,
                             layout: BaseLayout,
                             copiedFiles: MutableMap<Path, CopiedFor>,
                             sources: MutableList<Source>) {
    if (item.relativeOutputFile.contains('/')) {
      return
    }

    val moduleName = item.moduleName
    if (layout.modulesWithExcludedModuleLibraries.contains(moduleName)) {
      return
    }

    val excluded = layout.excludedModuleLibraries.get(moduleName)
    for (element in context.findRequiredModule(moduleName).dependenciesList.dependencies) {
      val libraryReference = (element as? JpsLibraryDependency)?.libraryReference ?: continue
      if (libraryReference.parentReference !is JpsModuleReference) {
        continue
      }

      if (JpsJavaExtensionService.getInstance().getDependencyExtension(element)?.scope
            ?.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME) != true) {
        continue
      }

      val library = element.library!!
      val libraryName = getLibraryFileName(library)
      if (excluded.contains(libraryName) || layout.includedModuleLibraries.any { it.libraryName == libraryName }) {
        continue
      }

      val targetFile = outputDir.resolve(item.relativeOutputFile)
      val files = getLibraryFiles(library = library, copiedFiles = copiedFiles, isModuleLevel = true, targetFile = targetFile)
      for (i in (files.size - 1) downTo 0) {
        val file = files.get(i)
        val fileName = file.fileName.toString()
        if (fileName.endsWith("-rt.jar") || fileName.contains("-agent")) {
          files.removeAt(i)
          addLibrary(library, outputDir.resolve(removeVersionFromJar(fileName)), listOf(file))
        }
      }

      for (file in files) {
        sources.add(ZipSource(file) { size ->
          libraryEntries.add(ModuleLibraryFileEntry(path = targetFile, moduleName = moduleName, libraryFile = file, size = size))
        })
      }
    }
  }

  private fun mergeLibsByPredicate(jarName: String,
                                   libraryToMerge: MutableMap<JpsLibrary, List<Path>>,
                                   outputDir: Path,
                                   predicate: (String) -> Boolean) {
    val result = LinkedHashMap<JpsLibrary, List<Path>>()
    val iterator = libraryToMerge.entries.iterator()
    while (iterator.hasNext()) {
      val (key, value) = iterator.next()
      if (predicate(key.name)) {
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
                                   copiedFiles: MutableMap<Path, CopiedFor>): MutableMap<JpsLibrary, List<Path>> {
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
      if (packMode == LibraryPackMode.MERGED && !extraMergeRules.values.any { it(libName) } && !isLibraryMergeable(libName)) {
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
    for (file in files) {
      to.add(ZipSource(file) { size ->
        val libraryEntry = moduleName?.let {
          ModuleLibraryFileEntry(
            path = targetFile,
            moduleName = it,
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
      createJarDescriptor(outputDir = outputDir,
                          targetFile = targetFile,
                          collectNativeFiles = collectNativeFiles,
                          context = context)
    }.sources
  }
}

private suspend fun unpackNativeLibraries(sourceFile: Path, paths: List<String>, context: BuildContext) {
  val libVersion = sourceFile.getName(sourceFile.nameCount - 2).toString()
  val signTool = context.proprietaryBuildTools.signTool
  val unsignedFiles = TreeMap<OsFamily, MutableList<Path>>()

  val packagePrefix = if (paths.size == 1) {
    // if a native lib is built with the only arch for testing purposes
    val first = paths.first()
    first.substring(0, first.indexOf('/') + 1)
  }
  else {
    getCommonPath(paths)
  }

  val libName = sourceFile.name.substringBefore('-')
  HashMapZipFile.load(sourceFile).use { zipFile ->
    val outDir = Files.createDirectories(context.paths.tempDir.resolve(libName))
    Files.createDirectories(outDir)
    for (pathWithPackage in paths) {
      val path = pathWithPackage.substring(packagePrefix.length)
      val fileName = path.substring(path.lastIndexOf('/') + 1)

      val os = when {
        path.startsWith("darwin-") || path.startsWith("mac-") || path.startsWith("darwin/") || path.startsWith("mac/") || path.startsWith(
          "Mac/") -> OsFamily.MACOS
        path.startsWith("win32-") || path.startsWith("win/") || path.startsWith("win-") || path.startsWith("Windows/") -> OsFamily.WINDOWS
        path.startsWith("Linux-Android/") || path.startsWith("Linux-Musl/") -> continue
        path.startsWith("linux-") || path.startsWith("linux/") || path.startsWith("Linux/") -> OsFamily.LINUX
        else -> continue
      }

      val osAndArch = path.substring(0, path.indexOf('/'))
      val arch: JvmArchitecture? = when {
        osAndArch.endsWith("-aarch64") || path.contains("/aarch64/") -> JvmArchitecture.aarch64
        path.contains("x86-64") || path.contains("x86_64") -> JvmArchitecture.x64
        // universal library
        os == OsFamily.MACOS && path.count { it == '/' } == 1 -> null
        else -> continue
      }

      var file: Path? = if (os != OsFamily.LINUX && signTool.usePresignedNativeFiles) {
        signTool.getPresignedLibraryFile(path = path, libName = libName, libVersion = libVersion, context = context)
      }
      else {
        null
      }

      if (file == null) {
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        file = outDir.resolve(path)!!
        Files.createDirectories(file.parent)
        FileChannel.open(file, W_CREATE_NEW).use { channel ->
          val byteBuffer = zipFile.getByteBuffer(pathWithPackage)!!
          try {
            while (byteBuffer.hasRemaining()) {
              channel.write(byteBuffer)
            }
          }
          finally {
            zipFile.releaseBuffer(byteBuffer)
          }
        }

        if (os != OsFamily.LINUX) {
          unsignedFiles.computeIfAbsent(os) { mutableListOf() }.add(file)
        }
      }

      val relativePath = "lib/$libName/" + if (libName == "jna") {
        "${arch!!.dirName}/$fileName"
      }
      else {
        path
      }
      context.addDistFile(DistFile(file = file, relativePath = relativePath, os = os, arch = arch))
    }
  }

  if (!signTool.usePresignedNativeFiles) {
    val versionOption = mapOf(SignTool.LIB_VERSION_OPTION_NAME to libVersion)
    coroutineScope {
      launch {
        unsignedFiles.get(OsFamily.MACOS)?.let {
          signMacBinaries(context, it, additionalOptions = versionOption)
        }
      }
      launch {
        unsignedFiles.get(OsFamily.WINDOWS)?.let {
          @Suppress("SpellCheckingInspection")
          context.signFiles(it, BuildOptions.WIN_SIGN_OPTIONS + versionOption + persistentMapOf(
            "contentType" to "application/x-exe",
            "jsign_replace" to "true"
          ))
        }
      }
    }
  }
}

private data class JarDescriptor(@JvmField val file: Path,
                                 @JvmField val pathInClassLog: String,
                                 @JvmField val collectNativeFiles: Boolean) {
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
    alreadyCopiedFor.targetFile == targetFile &&
    (alreadyCopiedFor.library.name.startsWith("ktor-") || (isModuleLevel && alreadyCopiedFor.library.name == libName))
  }

  for (file in files) {
    val alreadyCopiedFor = copiedFiles.putIfAbsent(file, CopiedFor(library, targetFile))
    if (alreadyCopiedFor != null) {
      // check name - we allow having same named module level library name
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
         !libName.startsWith("projector-") &&
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

private const val DO_NOT_EXPORT_TO_CONSOLE = "_CES_"

private suspend fun buildJars(descriptors: Collection<JarDescriptor>, dryRun: Boolean): Map<ZipSource, MutableList<String>> {
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
        val nativeFiles = if (item.collectNativeFiles) HashMap<ZipSource, MutableList<String>>() else null
        val file = item.file
        spanBuilder("build jar")
          .setAttribute(DO_NOT_EXPORT_TO_CONSOLE, true)
          .setAttribute("jar", file.toString())
          .setAttribute(AttributeKey.stringArrayKey("sources"), item.sources.map(Source::toString))
          .use {
            if (item.sources.isEmpty()) {
              return@async emptyMap()
            }
            else {
              buildJar(targetFile = file, sources = item.sources, dryRun = dryRun, nativeFiles = nativeFiles)
            }
          }

        // app.jar is combined later with other JARs and then re-ordered
        if (!dryRun && item.pathInClassLog.isNotEmpty() && item.pathInClassLog != "lib/app.jar") {
          reorderJar(relativePath = item.pathInClassLog, file = file)
        }
        nativeFiles
      }
    }
  }

  val result = TreeMap<ZipSource, MutableList<String>>(compareBy { it.file.fileName.toString() })
  list.asSequence().mapNotNull { it.getCompleted() }.forEach(result::putAll)
  return result
}

fun buildJar(targetFile: Path,
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

private fun createJarDescriptor(outputDir: Path,
                                targetFile: Path,
                                collectNativeFiles: Boolean,
                                context: BuildContext): JarDescriptor {
  var pathInClassLog = ""
  val isReorderingEnabled = !context.options.buildStepsToSkip.contains(BuildOptions.GENERATE_JAR_ORDER_STEP)
  if (isReorderingEnabled) {
    if (context.paths.distAllDir == outputDir.parent) {
      pathInClassLog = outputDir.parent.relativize(targetFile).toString().replace(File.separatorChar, '/')
    }
    else if (outputDir.startsWith(context.paths.distAllDir)) {
      pathInClassLog = context.paths.distAllDir.relativize(targetFile).toString().replace(File.separatorChar, '/')
    }
    else {
      val parent = outputDir.parent
      if (parent?.fileName.toString() == "plugins") {
        pathInClassLog = outputDir.parent.parent.relativize(targetFile).toString().replace(File.separatorChar, '/')
      }
    }
  }

  val fileName = targetFile.fileName.toString()
  return JarDescriptor(file = targetFile,
                       pathInClassLog = pathInClassLog,
                       collectNativeFiles = collectNativeFiles && (fileName == APP_JAR || fileName.startsWith("3rd-party-")))
}