// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.telemetry.use
import com.intellij.platform.util.putMoreLikelyPluginJarsFirst
import com.intellij.util.lang.HashMapZipFile
import io.opentelemetry.api.common.AttributeKey
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PlatformJarNames
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.io.INDEX_FILENAME
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.transformZipUsingTempFile
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private fun processClassReport(consumer: (String, String) -> Unit) {
  val osName = System.getProperty("os.name")
  val classifier = when {
    osName.startsWith("windows", ignoreCase = true) -> "windows"
    osName.startsWith("mac", ignoreCase = true) -> "mac"
    else -> "linux"
  }
  PackageIndexBuilder::class.java.classLoader.getResourceAsStream("${classifier}/class-report.txt")!!.bufferedReader().use {
    it.forEachLine { line ->
      val (classFilePath, libPath) = line.split(':', limit = 2)
      consumer(classFilePath, libPath)
    }
  }
}

private val sourceToNames: Map<String, MutableList<String>> by lazy {
  val sourceToNames = LinkedHashMap<String, MutableList<String>>()
  processClassReport { classFilePath, jarPath ->
    sourceToNames.computeIfAbsent(jarPath) { mutableListOf() }.add(classFilePath)
  }
  sourceToNames
}

fun reorderJar(relativePath: String, file: Path) {
  val orderedNames = sourceToNames.get(relativePath) ?: return
  spanBuilder("reorder jar")
    .setAttribute("relativePath", relativePath)
    .setAttribute("file", file.toString())
    .use {
      reorderJar(jarFile = file, orderedNames = orderedNames)
    }
}

internal val excludedLibJars: Set<String> = java.util.Set.of(PlatformJarNames.TEST_FRAMEWORK_JAR)

internal fun generateClasspath(homeDir: Path, libDir: Path): List<String> {
  spanBuilder("generate classpath")
    .setAttribute("dir", homeDir.toString())
    .use { span ->
      val existing = HashSet<Path>()
      addJarsFromDir(dir = libDir) { paths ->
        paths.filterTo(existing) { !excludedLibJars.contains(it.fileName.toString()) }
      }
      val files = computeAppClassPath(libDir = libDir, existing = existing, homeDir = homeDir)
      val result = files.map { libDir.relativize(it).toString() }
      span.setAttribute(AttributeKey.stringArrayKey("result"), result)
      return result
    }
}

fun computeAppClassPath(libDir: Path, existing: Set<Path>, homeDir: Path): LinkedHashSet<Path> {
  val result = LinkedHashSet<Path>(existing.size + 4)
  // add first - should be listed first
  sequenceOf(PLATFORM_LOADER_JAR, UTIL_8_JAR, UTIL_JAR).map(libDir::resolve).filterTo(result, existing::contains)
  computeCoreSources().asSequence().map { homeDir.resolve(it) }.filterTo(result) { existing.contains(it) }
  // sorted to ensure stable performance results
  result.addAll(if (isWindows) existing.sortedBy(Path::toString) else existing.sorted())
  return result
}

private fun computeCoreSources(): Set<String> {
  val result = LinkedHashSet<String>()
  processClassReport { _, jarPath ->
    if (jarPath.startsWith("lib/")) {
      result.add(jarPath)
    }
  }
  return result
}

private inline fun addJarsFromDir(dir: Path, consumer: (Sequence<Path>) -> Unit) {
  Files.newDirectoryStream(dir).use { stream ->
    consumer(stream.asSequence().filter { it.toString().endsWith(".jar") })
  }
}

@VisibleForTesting
fun readClassLoadingLog(classLoadingLog: InputStream, rootDir: Path): Map<Path, List<String>> {
  val sourceToNames = LinkedHashMap<Path, MutableList<String>>()
  classLoadingLog.bufferedReader().forEachLine {
    val data = it.split(':', limit = 2)
    val sourcePath = data[1]
    sourceToNames.computeIfAbsent(rootDir.resolve(sourcePath)) { mutableListOf() }.add(data[0])
  }
  return sourceToNames
}

fun reorderJar(jarFile: Path, orderedNames: List<String>) {
  val orderedNameToIndex = Object2IntOpenHashMap<String>(orderedNames.size)
  orderedNameToIndex.defaultReturnValue(-1)
  for ((index, orderedName) in orderedNames.withIndex()) {
    orderedNameToIndex.put(orderedName, index)
  }

  return transformZipUsingTempFile(jarFile) { zipCreator ->
    val packageIndexBuilder = PackageIndexBuilder()

    HashMapZipFile.load(jarFile).use { sourceZip ->
      val entries = sourceZip.entries.filterTo(mutableListOf()) { !it.isDirectory && it.name != INDEX_FILENAME }
      // ignore the existing package index on reorder - a new one will be computed even if it is the same, do not optimize for simplicity
      entries.sortWith(Comparator { o1, o2 ->
        val o2p = o2.name
        if ("META-INF/plugin.xml" == o2p) {
          return@Comparator Int.MAX_VALUE
        }

        val o1p = o1.name
        if ("META-INF/plugin.xml" == o1p) {
          -Int.MAX_VALUE
        }
        else {
          val i1 = orderedNameToIndex.getInt(o1p)
          if (i1 == -1) {
            if (orderedNameToIndex.containsKey(o2p)) 1 else 0
          }
          else {
            val i2 = orderedNameToIndex.getInt(o2p)
            if (i2 == -1) -1 else (i1 - i2)
          }
        }
      })

      for (entry in entries) {
        packageIndexBuilder.addFile(entry.name)
        zipCreator.uncompressedData(
          nameString = entry.name,
          data = entry.getByteBuffer(sourceZip),
          indexWriter = packageIndexBuilder.indexWriter,
        )
      }
      packageIndexBuilder.writePackageIndex(zipCreator)
    }
  }
}

internal data class PluginBuildDescriptor(
  @JvmField val dir: Path,
  @JvmField val os: OsFamily?,
  @JvmField val layout: PluginLayout,
  @JvmField val moduleNames: List<String>,
)

internal fun writePluginClassPathHeader(out: DataOutputStream, isJarOnly: Boolean, pluginCount: Int, moduleOutputPatcher: ModuleOutputPatcher, context: BuildContext) {
  // format version
  out.write(2)
  // jarOnly
  out.write(if (isJarOnly) 1 else 0)

  // main plugin
  val mainDescriptor = moduleOutputPatcher.getPatchedContent(context.productProperties.applicationInfoModule)
    .let { it.get("META-INF/plugin.xml") ?: it.get("META-INF/${context.productProperties.platformPrefix}Plugin.xml") }

  val mainPluginDescriptorContent = requireNotNull(mainDescriptor) {
    "Cannot find core plugin descriptor (module=${context.productProperties.applicationInfoModule})"
  }
  out.writeInt(mainPluginDescriptorContent.size)
  out.write(mainPluginDescriptorContent)

  // bundled plugin metadata
  out.writeShort(pluginCount)
}

internal fun generatePluginClassPath(pluginEntries: List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>, moduleOutputPatcher: ModuleOutputPatcher): ByteArray {
  val byteOut = ByteArrayOutputStream()
  val out = DataOutputStream(byteOut)

  val uniqueGuard = HashSet<Path>()
  for ((pluginAsset, entries) in pluginEntries) {
    val pluginDir = pluginAsset.dir

    val files = ArrayList<Path>(entries.size)
    uniqueGuard.clear()
    for (entry in entries) {
      val relativeOutputFile = entry.relativeOutputFile
      if (relativeOutputFile != null && relativeOutputFile.contains('/')) {
        continue
      }

      if (!uniqueGuard.add(entry.path)) {
        continue
      }

      files.add(entry.path)

      check(!entry.path.startsWith(pluginDir) || pluginDir.relativize(entry.path).nameCount == 2) {
        "plugin entry is not specified correctly: ${entry.path}"
      }
    }

    if (files.size > 1) {
      // always sort
      putMoreLikelyPluginJarsFirst(pluginDir.fileName.toString(), filesInLibUnderPluginDir = files)
    }

    var pluginDescriptorContent: ByteArray? = null
    for (file in files) {
      if (file.toString().endsWith(".jar")) {
        pluginDescriptorContent = HashMapZipFile.load(file).use { zip ->
          zip.getRawEntry("META-INF/plugin.xml")?.getData(zip)
        }
        if (pluginDescriptorContent != null) {
          break
        }
      }
    }

    if (pluginDescriptorContent == null) {
      pluginDescriptorContent = moduleOutputPatcher.getPatchedPluginXml(pluginAsset.layout.mainModule)
    }

    writeEntry(out = out, files = files, pluginDir = pluginDir, pluginDescriptorContent = pluginDescriptorContent)
  }

  out.close()
  return byteOut.toByteArray()
}

private fun writeEntry(out: DataOutputStream, files: Collection<Path>, pluginDir: Path, pluginDescriptorContent: ByteArray) {
  // the plugin dir as the last item in the list
  out.writeShort(files.size)
  out.writeUTF(pluginDir.fileName.invariantSeparatorsPathString)

  out.writeInt(pluginDescriptorContent.size)
  out.write(pluginDescriptorContent)

  for (file in files) {
    out.writeUTF(pluginDir.relativize(file).invariantSeparatorsPathString)
  }
}

internal fun generatePluginClassPathFromPrebuiltPluginFiles(pluginEntries: List<Pair<Path, List<Path>>>): ByteArray {
  val byteOut = ByteArrayOutputStream()
  val out = DataOutputStream(byteOut)

  for ((pluginDir, entries) in pluginEntries) {
    val files = entries.toMutableList()
    if (files.size > 1) {
      // always sort
      putMoreLikelyPluginJarsFirst(pluginDir.fileName.toString(), filesInLibUnderPluginDir = files)
    }

    // move dir with plugin.xml to top (it may not exist if for some reason the main module dir still being packed into JAR)
    writeEntry(out = out, files = files, pluginDir = pluginDir, pluginDescriptorContent = reorderPluginClassPath(files))
  }

  out.close()
  return byteOut.toByteArray()
}

private fun reorderPluginClassPath(files: MutableList<Path>): ByteArray {
  for ((index, file) in files.withIndex()) {
    val pluginDescriptorContent = HashMapZipFile.load(file).use { zip ->
      zip.getRawEntry("META-INF/plugin.xml")?.getData(zip)
    }

    if (pluginDescriptorContent != null) {
      files.add(0, files.removeAt(index))
      return pluginDescriptorContent
    }
  }

  throw IllegalStateException("plugin descriptor is not found among\n  ${files.joinToString(separator = "\n  ")}")
}
