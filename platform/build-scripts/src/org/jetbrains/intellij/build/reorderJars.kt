// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.platform.diagnostic.telemetry.helpers.useWithoutActiveScope
import com.intellij.util.lang.HashMapZipFile
import io.opentelemetry.api.common.AttributeKey
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.PlatformJarNames
import org.jetbrains.intellij.build.io.INDEX_FILENAME
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.transformZipUsingTempFile
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

private val excludedLibJars = setOf(PlatformJarNames.TEST_FRAMEWORK_JAR, "junit.jar")

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
  val orderedNames = sourceToNames[relativePath] ?: return
  spanBuilder("reorder jar")
    .setAttribute("relativePath", relativePath)
    .setAttribute("file", file.toString())
    .useWithoutActiveScope {
      reorderJar(jarFile = file, orderedNames = orderedNames)
    }
}

fun generateClasspath(homeDir: Path, libDir: Path): List<String> {
  spanBuilder("generate classpath")
    .setAttribute("dir", homeDir.toString())
    .useWithoutActiveScope { span ->
      val files = computeAppClassPath(homeDir = homeDir, libDir = libDir)
      val result = files.map { libDir.relativize(it).toString() }
      span.setAttribute(AttributeKey.stringArrayKey("result"), result)
      return result
    }
}

private fun computeAppClassPath(homeDir: Path, libDir: Path): LinkedHashSet<Path> {
  val existing = HashSet<Path>()
  addJarsFromDir(libDir) { paths ->
    paths.filterTo(existing) { it.fileName.toString() !in excludedLibJars }
  }
  return computeAppClassPath(libDir, existing, homeDir)
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
        zipCreator.uncompressedData(entry.name, entry.getByteBuffer(sourceZip))
      }
      packageIndexBuilder.writePackageIndex(zipCreator)
    }
  }
}
