// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build

import com.intellij.platform.diagnostic.telemetry.helpers.useWithoutActiveScope
import com.intellij.platform.util.putMoreLikelyPluginJarsFirst
import com.intellij.util.lang.HashMapZipFile
import io.opentelemetry.api.common.AttributeKey
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.TraceManager.spanBuilder
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
    .useWithoutActiveScope {
      reorderJar(jarFile = file, orderedNames = orderedNames)
    }
}

private val excludedLibJars = java.util.Set.of(PlatformJarNames.TEST_FRAMEWORK_JAR, "junit.jar")

fun generateClasspath(homeDir: Path, libDir: Path): List<String> {
  spanBuilder("generate classpath")
    .setAttribute("dir", homeDir.toString())
    .useWithoutActiveScope { span ->
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

data class PluginBuildDescriptor(
  @JvmField val dir: Path,
  @JvmField val layout: PluginLayout,
  @JvmField val moduleNames: List<String>,
)

fun writePluginClassPathHeader(out: DataOutputStream, isJarOnly: Boolean, pluginCount: Int) {
  // format version
  out.write(1)
  // jarOnly
  out.write(if (isJarOnly) 1 else 0)
  out.writeShort(pluginCount)
}

fun generatePluginClassPath(
  pluginEntries: List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>,
  writeDescriptor: Boolean,
): ByteArray {
  val allEntries = mutableListOf<Pair<Path, List<Path>>>()
  for ((pluginAsset, entries) in pluginEntries) {
    val files = entries.asSequence()
      .filter {
        val relativeOutputFile = it.relativeOutputFile
        if (relativeOutputFile != null && relativeOutputFile.contains('/')) {
          return@filter false
        }
        // assert that relativeOutputFile is correctly specified
        check(!it.path.startsWith(pluginAsset.dir) || pluginAsset.dir.relativize(it.path).nameCount == 2) {
          "relativeOutputFile is not specified correctly for $it"
        }
        true
      }
      .map { it.path }
      .distinct()
      .toMutableList()
    allEntries.add(pluginAsset.dir to files)
  }
  return generatePluginClassPathFromFiles(pluginEntries = allEntries, writeDescriptor = writeDescriptor)
}

fun generatePluginClassPathFromFiles(pluginEntries: List<Pair<Path, List<Path>>>, writeDescriptor: Boolean): ByteArray {
  val byteOut = ByteArrayOutputStream()
  val out = DataOutputStream(byteOut)

  for ((pluginDir, entries) in pluginEntries) {
    val files = entries.asSequence()
      .onEach {
        check(!it.startsWith(pluginDir) || pluginDir.relativize(it).nameCount == 2) {
          "plugin entry is not specified correctly: $it"
        }
      }
      .distinct()
      .toMutableList()
    if (files.size > 1) {
      // always sort
      putMoreLikelyPluginJarsFirst(pluginDir.fileName.toString(), filesInLibUnderPluginDir = files)
    }

    // move dir with plugin.xml to top (it may not exist if for some reason the main module dir still being packed into JAR)
    val pluginDescriptorContent = reorderPluginClassPath(files, writeDescriptor)

    // the plugin dir as the last item in the list
    out.writeShort(files.size)
    out.writeUTF(pluginDir.fileName.invariantSeparatorsPathString)

    if (pluginDescriptorContent == null) {
      out.writeInt(0)
    }
    else {
      out.writeInt(pluginDescriptorContent.size)
      out.write(pluginDescriptorContent)
    }

    for (file in files) {
      out.writeUTF(pluginDir.relativize(file).invariantSeparatorsPathString)
    }
  }

  out.close()
  return byteOut.toByteArray()
}

private fun reorderPluginClassPath(files: MutableList<Path>, writeDescriptor: Boolean): ByteArray? {
  var pluginDescriptorContent: ByteArray? = null
  var pluginDirIndex = -1
  for ((index, file) in files.withIndex()) {
    if (Files.isDirectory(file)) {
      val pluginDescriptorFile = file.resolve("META-INF/plugin.xml")
      if (Files.exists(pluginDescriptorFile)) {
        pluginDescriptorContent = if (writeDescriptor) Files.readAllBytes(pluginDescriptorFile) else null
        pluginDirIndex = index
        break
      }
    }
    else {
      val found = HashMapZipFile.load(file).use { zip ->
        val rawEntry = zip.getRawEntry("META-INF/plugin.xml")
        if (writeDescriptor) {
          pluginDescriptorContent = rawEntry?.getData(zip)
        }
        rawEntry != null
      }

      if (found) {
        pluginDirIndex = index
        break
      }
    }
  }

  check(pluginDirIndex != -1) { "plugin descriptor is not found among\n  ${files.joinToString(separator = "\n  ")}" }
  if (pluginDirIndex != 0) {
    files.add(0, files.removeAt(pluginDirIndex))
  }
  return pluginDescriptorContent
}
