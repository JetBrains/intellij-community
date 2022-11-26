// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog",
               "BlockingMethodInNonBlockingContext")
package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.use
import com.intellij.util.PathUtilRt
import com.intellij.util.io.URLUtil
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.lang.HashMapZipFile
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.BaseLayout.Companion.APP_JAR
import org.jetbrains.intellij.build.impl.projectStructureMapping.*
import org.jetbrains.intellij.build.io.W_CREATE_NEW
import org.jetbrains.intellij.build.tasks.*
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.IntConsumer

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
  "fastutil-min",
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
  "Guava",
  "http-client",
  "commons-codec",
  "commons-logging",
  "commons-lang3",
  "kotlin-stdlib-jdk8",
  // see ConsoleProcessListFetcher.getConsoleProcessCount
  "pty4j",
)

private val extraMergeRules: PersistentMap<String, (String) -> Boolean> = persistentMapOf<String, (String) -> Boolean>().mutate { map ->
  map.put("groovy.jar") { it.startsWith("org.codehaus.groovy:") }
  map.put("jsch-agent.jar") { it.startsWith("jsch-agent") }
  map.put("rd.jar") { it.startsWith("rd-") }
  // see ClassPathUtil.getUtilClassPath
  map.put("3rd-party-rt.jar") { libsThatUsedInJps.contains(it) || it.startsWith("kotlinx-") || it == "kotlin-reflect" }
}

internal fun getLibraryFileName(lib: JpsLibrary): String {
  val name = lib.name
  if (!name.startsWith("#")) {
    return name
  }

  val roots = lib.getRoots(JpsOrderRootType.COMPILED)
  check(roots.size == 1) {
    "Non-single entry module library $name: ${roots.joinToString { it.url }}"
  }
  return PathUtilRt.getFileName(roots.first().url.removeSuffix(URLUtil.JAR_SEPARATOR))
}

class JarPackager private constructor(private val context: BuildContext) {
  private val jarDescriptors = LinkedHashMap<Path, JarDescriptor>()
  private val libraryEntries = ConcurrentLinkedQueue<LibraryFileEntry>()
  private val libToMetadata = HashMap<JpsLibrary, ProjectLibraryData>()

  companion object {
    suspend fun pack(jarToModules: Map<String, List<String>>,
                     outputDir: Path,
                     layout: BaseLayout = BaseLayout(),
                     moduleOutputPatcher: ModuleOutputPatcher = ModuleOutputPatcher(),
                     dryRun: Boolean = false,
                     context: BuildContext): Collection<DistributionFileEntry> {
      val copiedFiles = HashMap<Path, CopiedFor>()
      val packager = JarPackager(context)
      for (data in layout.includedModuleLibraries) {
        val library = context.findRequiredModule(data.moduleName).libraryCollection.libraries
                        .find { getLibraryFileName(it) == data.libraryName }
                      ?: throw IllegalArgumentException("Cannot find library ${data.libraryName} in \'${data.moduleName}\' module")
        var fileName = nameToJarFileName(data.libraryName)
        var relativePath = data.relativeOutputPath
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
          files = getLibraryFiles(library = library, copiedFiles = copiedFiles, isModuleLevel = true, targetFile = targetFile)
        )
      }

      val extraLibSources = HashMap<String, MutableList<Source>>()
      val libraryToMerge = packager.packProjectLibraries(jarToModuleNames = jarToModules,
                                                         outputDir = outputDir,
                                                         layout = layout,
                                                         copiedFiles = copiedFiles,
                                                         extraLibSources = extraLibSources)
      val isRootDir = context.paths.distAllDir == outputDir.parent
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
        assert(jarToModules.containsKey(mainJarName))
        packager.filesToSourceWithMappings(outputDir.resolve(mainJarName), libraryToMerge)
      }

      // must be concurrent - buildJars executed in parallel
      val moduleNameToSize = ConcurrentHashMap<String, Int>()
      for ((jarPath, modules) in jarToModules) {
        val jarFile = outputDir.resolve(jarPath)
        val descriptor = packager.jarDescriptors.computeIfAbsent(jarFile) { JarDescriptor(jarFile = it) }
        val includedModules = descriptor.includedModules
        if (includedModules == null) {
          descriptor.includedModules = modules.toMutableList()
        }
        else {
          includedModules.addAll(modules)
        }

        val sourceList = descriptor.sources
        extraLibSources.get(jarPath)?.let(sourceList::addAll)
        packager.packModuleOutputAndUnpackedProjectLibraries(modules = modules,
                                                             jarPath = jarPath,
                                                             jarFile = jarFile,
                                                             moduleOutputPatcher = moduleOutputPatcher,
                                                             layout = layout,
                                                             moduleNameToSize = moduleNameToSize,
                                                             sourceList = sourceList)
      }

      val descriptors = ArrayList<BuildJarDescriptor>(packager.jarDescriptors.size)
      val isReorderingEnabled = !context.options.buildStepsToSkip.contains(BuildOptions.GENERATE_JAR_ORDER_STEP)
      val collectNativeFiles = layout !is PluginLayout
      for (descriptor in packager.jarDescriptors.values) {
        var pathInClassLog = ""
        if (isReorderingEnabled) {
          if (isRootDir) {
            pathInClassLog = outputDir.parent.relativize(descriptor.jarFile).toString().replace(File.separatorChar, '/')
          }
          else if (outputDir.startsWith(context.paths.distAllDir)) {
            pathInClassLog = context.paths.distAllDir.relativize(descriptor.jarFile).toString().replace(File.separatorChar, '/')
          }
          else {
            val parent = outputDir.parent
            if (parent?.fileName.toString() == "plugins") {
              pathInClassLog = outputDir.parent.parent.relativize(descriptor.jarFile).toString().replace(File.separatorChar, '/')
            }
          }
        }
        val fileName = descriptor.jarFile.fileName.toString()
        descriptors.add(BuildJarDescriptor(
          descriptor = descriptor,
          relativePath = pathInClassLog,
          collectNativeFiles = collectNativeFiles && (fileName == APP_JAR || fileName.startsWith("3rd-party-")),
        ))
      }

      val nativeFiles = buildJars(descriptors = descriptors, dryRun = dryRun)
      val list = mutableListOf<DistributionFileEntry>()

      if (nativeFiles.isNotEmpty()) {
        packNativeFiles(outputDir = outputDir, nativeFiles = nativeFiles, packager = packager, list = list, dryRun = dryRun)
      }

      for (item in packager.jarDescriptors.values) {
        for (moduleName in (item.includedModules ?: emptyList())) {
          val size = moduleNameToSize.get(moduleName)
                     ?: throw IllegalStateException("Size is not set for $moduleName (moduleNameToSize=$moduleNameToSize)")
          list.add(ModuleOutputEntry(path = item.jarFile, moduleName = moduleName, size = size))
        }
      }

      // sort because projectStructureMapping is a concurrent collection
      return list +
             packager.libraryEntries.sortedWith { a, b -> compareValuesBy(a, b, { it.path }, { it.type }, { it.libraryFile }) }
    }

    private suspend fun packNativeFiles(outputDir: Path,
                                        nativeFiles: Map<ZipSource, MutableList<String>>,
                                        packager: JarPackager,
                                        list: MutableList<DistributionFileEntry>,
                                        dryRun: Boolean) {
      val targetFile = outputDir.resolve("3rd-party-native.jar")
      val sources = mutableListOf<Source>()
      coroutineScope {
        for (source in nativeFiles.keys.sortedBy { it.file.fileName.toString() }) {
          val paths = nativeFiles.get(source)!!
          val sourceFile = source.file
          val fileName = sourceFile.fileName.toString()
          if (fileName.startsWith("jna-") || fileName.startsWith("pty4j-")) {
            async(Dispatchers.IO) {
              unpackNativeLibraries(sourceFile = sourceFile, paths = paths, context = packager.context)
            }
            continue
          }

          sources.add(ZipSource(file = sourceFile, filter = paths::contains) { size ->
            val originalEntry = packager.libraryEntries.first { it.libraryFile === sourceFile }
            if (originalEntry is ProjectLibraryEntry) {
              list.add(ProjectLibraryEntry(path = targetFile,
                                           data = originalEntry.data,
                                           libraryFile = sourceFile,
                                           size = size))
            }
            else {
              list.add(ModuleLibraryFileEntry(path = targetFile,
                                              moduleName = (originalEntry as ModuleLibraryFileEntry).moduleName,
                                              libraryFile = originalEntry.libraryFile,
                                              size = size))
            }
          })
        }

        withContext(Dispatchers.IO) {
          buildJar(targetFile = targetFile, sources = sources, dryRun = dryRun, nativeFiles = null)
        }
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
    val sources = getJarDescriptorSources(uberJarFile)
    for ((key, value) in libraryToMerge) {
      filesToSourceWithMapping(to = sources, files = value, library = key, targetFile = uberJarFile)
    }
  }

  private fun packModuleOutputAndUnpackedProjectLibraries(modules: Collection<String>,
                                                          jarPath: String,
                                                          jarFile: Path,
                                                          moduleOutputPatcher: ModuleOutputPatcher,
                                                          layout: BaseLayout,
                                                          moduleNameToSize: MutableMap<String, Int>,
                                                          sourceList: MutableList<Source>): List<DistributionFileEntry> {
    Span.current().addEvent("include module outputs", Attributes.of(AttributeKey.stringArrayKey("modules"), java.util.List.copyOf(modules)))
    val searchableOptionsDir = context.searchableOptionDir
    for (moduleName in modules) {
      addModuleSources(moduleName = moduleName,
                       moduleNameToSize = moduleNameToSize,
                       moduleOutputDir = context.getModuleOutputDir(context.findRequiredModule(moduleName)),
                       modulePatches = moduleOutputPatcher.getPatchedDir(moduleName),
                       modulePatchContents = moduleOutputPatcher.getPatchedContent(moduleName),
                       searchableOptionsRootDir = searchableOptionsDir,
                       extraExcludes = layout.moduleExcludes.get(moduleName) ?: emptyList(),
                       sourceList = sourceList)
    }

    val list = mutableListOf<DistributionFileEntry>()
    for (libraryName in layout.projectLibrariesToUnpack.get(jarPath)) {
      val library = context.project.libraryCollection.findLibrary(libraryName)
      check(library != null) {
        "Project library \'$libraryName\' from $jarPath should be unpacked but it isn\'t found"
      }

      for (file in library.getPaths(JpsOrderRootType.COMPILED)) {
        sourceList.add(ZipSource(file = file) { size ->
          val libraryData = ProjectLibraryData(libraryName = library.name,
                                               outPath = null,
                                               packMode = LibraryPackMode.MERGED,
                                               reason = "explicitUnpack")
          libraryEntries.add(ProjectLibraryEntry(path = jarFile, data = libraryData, libraryFile = file, size = size))
        })
      }
    }
    return list
  }

  private fun packProjectLibraries(jarToModuleNames: Map<String, List<String>>,
                                   outputDir: Path,
                                   layout: BaseLayout,
                                   copiedFiles: MutableMap<Path, CopiedFor>,
                                   extraLibSources: MutableMap<String, MutableList<Source>>): MutableMap<JpsLibrary, List<Path>> {
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
    for ((targetFilename, value) in jarToModuleNames) {
      if (targetFilename.contains('/')) {
        continue
      }
      for (moduleName in value) {
        if (layout.modulesWithExcludedModuleLibraries.contains(moduleName)) {
          continue
        }

        val excluded = layout.excludedModuleLibraries.get(moduleName)
        for (element in context.findRequiredModule(moduleName).dependenciesList.dependencies) {
          if (element !is JpsLibraryDependency) {
            continue
          }

          packModuleLibs(moduleName = moduleName,
                         targetFilename = targetFilename,
                         libraryDependency = element,
                         excluded = excluded,
                         layout = layout,
                         outputDir = outputDir,
                         copiedFiles = copiedFiles,
                         extraLibSources = extraLibSources)
        }
      }
    }
    return toMerge
  }

  private fun packModuleLibs(moduleName: String,
                             targetFilename: String,
                             libraryDependency: JpsLibraryDependency,
                             excluded: Collection<String>,
                             layout: BaseLayout,
                             outputDir: Path,
                             copiedFiles: MutableMap<Path, CopiedFor>,
                             extraLibSources: MutableMap<String, MutableList<Source>>) {
    if (libraryDependency.libraryReference.parentReference!!.resolve() !is JpsModule) {
      return
    }

    if (JpsJavaExtensionService.getInstance().getDependencyExtension(libraryDependency)?.scope
        ?.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME) != true) {
      return
    }

    val library = libraryDependency.library!!
    val libraryName = getLibraryFileName(library)
    if (excluded.contains(libraryName) || layout.includedModuleLibraries.any { it.libraryName == libraryName }) {
      return
    }

    val targetFile = outputDir.resolve(targetFilename)
    val files = getLibraryFiles(library = library, copiedFiles = copiedFiles, isModuleLevel = true, targetFile = targetFile)
    for (i in (files.size - 1) downTo 0) {
      val file = files.get(i)
      val fileName = file.fileName.toString()
      if (fileName.endsWith("-rt.jar") || fileName.contains("-agent")) {
        files.removeAt(i)
        addLibrary(library, outputDir.resolve(removeVersionFromJar(fileName)), listOf(file))
      }
    }

    if (!files.isEmpty()) {
      val sources = extraLibSources.computeIfAbsent(targetFilename) { mutableListOf() }
      for (file in files) {
        sources.add(ZipSource(file) { size ->
          libraryEntries.add(ModuleLibraryFileEntry(targetFile, moduleName, file, size))
        })
      }
    }
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
    return jarDescriptors.computeIfAbsent(targetFile) { JarDescriptor(targetFile) }.sources
  }
}

private suspend fun unpackNativeLibraries(sourceFile: Path, paths: List<String>, context: BuildContext) {
  val libVersion = sourceFile.getName(sourceFile.nameCount - 2).toString()
  val signTool = context.proprietaryBuildTools.signTool
  val unsignedFiles = TreeMap<OsFamily, MutableList<Path>>()
  val packagePrefix = getCommonPath(paths)
  val libName = sourceFile.fileName.toString().substringBefore('-')
  HashMapZipFile.load(sourceFile).use { zipFile ->
    val jnaOutDir = Files.createDirectories(context.paths.tempDir.resolve(libName))
    Files.createDirectories(jnaOutDir)
    for (pathWithPackage in paths) {
      val path = pathWithPackage.substring(packagePrefix.length)
      val fileName = path.substring(path.lastIndexOf('/') + 1)

      val os = when {
        path.startsWith("darwin-") || path.startsWith("darwin/") -> OsFamily.MACOS
        path.startsWith("win32-") || path.startsWith("win/") -> OsFamily.WINDOWS
        path.startsWith("linux-") || path.startsWith("linux/") -> OsFamily.LINUX
        else -> continue
      }

      val osAndArch = path.substring(0, path.indexOf('/'))
      val arch: JvmArchitecture? = when {
        osAndArch.endsWith("-aarch64") || path.contains("/aarch64/") -> JvmArchitecture.aarch64
        osAndArch.endsWith("-x86-64") || path.contains("/x86-64/") -> JvmArchitecture.x64
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
        file = jnaOutDir.resolve(path)!!
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
        unsignedFiles.get(OsFamily.MACOS)?.let { context.signFiles(it, MAC_CODE_SIGN_OPTIONS + versionOption) }
      }
      launch {
        unsignedFiles.get(OsFamily.WINDOWS)?.let {
          @Suppress("SpellCheckingInspection")
          context.signFiles(it, BuildOptions.WIN_SIGN_OPTIONS + versionOption + persistentMapOf("jsign_replace" to "true"))
        }
      }
    }
  }
}

private data class JarDescriptor(@JvmField val jarFile: Path) {
  val sources: MutableList<Source> = mutableListOf()
  var includedModules: MutableList<String>? = null
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
      // check name - we allow to have same named module level library name
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
  "sqlite", "async-profiler",
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

internal val commonModuleExcludes = java.util.List.of(
  FileSystems.getDefault().getPathMatcher("glob:**/icon-robots.txt"),
  FileSystems.getDefault().getPathMatcher("glob:icon-robots.txt"),
  FileSystems.getDefault().getPathMatcher("glob:.unmodified"),
  // compilation cache on TC
  FileSystems.getDefault().getPathMatcher("glob:.hash"),
  FileSystems.getDefault().getPathMatcher("glob:classpath.index"),
)

private fun addModuleSources(moduleName: String,
                             moduleNameToSize: MutableMap<String, Int>,
                             moduleOutputDir: Path,
                             modulePatches: Collection<Path>,
                             modulePatchContents: Map<String, ByteArray>,
                             searchableOptionsRootDir: Path,
                             extraExcludes: Collection<String>,
                             sourceList: MutableList<Source>) {
  val sizeConsumer = IntConsumer {
    moduleNameToSize.merge(moduleName, it) { oldValue, value -> oldValue + value }
  }

  for (entry in modulePatchContents) {
    sourceList.add(InMemoryContentSource(entry.key, entry.value, sizeConsumer))
  }
  // must be before module output to override
  for (moduleOutputPatch in modulePatches) {
    sourceList.add(DirSource(moduleOutputPatch, Collections.emptyList(), sizeConsumer))
  }

  val searchableOptionsModuleDir = searchableOptionsRootDir.resolve(moduleName)
  if (Files.exists(searchableOptionsModuleDir)) {
    sourceList.add(DirSource(searchableOptionsModuleDir, Collections.emptyList(), sizeConsumer))
  }

  val excludes = if (extraExcludes.isEmpty()) {
    commonModuleExcludes
  }
  else {
    commonModuleExcludes.plus(extraExcludes.map { FileSystems.getDefault().getPathMatcher("glob:$it") })
  }
  sourceList.add(DirSource(dir = moduleOutputDir, excludes = excludes, sizeConsumer = sizeConsumer))
}

private data class CopiedFor(@JvmField val library: JpsLibrary, @JvmField val targetFile: Path?)

private data class BuildJarDescriptor(@JvmField val descriptor: JarDescriptor,
                                      @JvmField val relativePath: String,
                                      @JvmField val collectNativeFiles: Boolean) {
  val file: Path
    get() = descriptor.jarFile

  val sources: List<Source>
    get() = descriptor.sources
}

private const val DO_NOT_EXPORT_TO_CONSOLE = "_CES_"

private suspend fun buildJars(descriptors: List<BuildJarDescriptor>, dryRun: Boolean): Map<ZipSource, MutableList<String>> {
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
            buildJar(targetFile = file, sources = item.sources, dryRun = dryRun, nativeFiles = nativeFiles)
          }

        // app.jar is combined later with other JARs and then re-ordered
        if (!dryRun && item.relativePath.isNotEmpty() && item.relativePath != "lib/app.jar") {
          reorderJar(relativePath = item.relativePath, file = file)
        }
        nativeFiles
      }
    }
  }

  val result = TreeMap<ZipSource, MutableList<String>>(compareBy { it.file.fileName.toString() })
  list.asSequence().mapNotNull { it.getCompleted() }.forEach(result::putAll)
  return result
}