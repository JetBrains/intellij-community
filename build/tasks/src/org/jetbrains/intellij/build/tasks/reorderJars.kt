// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.tasks

import com.intellij.util.lang.ImmutableZipEntry
import com.intellij.util.lang.ImmutableZipFile
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.intellij.build.io.ZipFileWriter
import org.jetbrains.intellij.build.io.writeNewZip
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.ForkJoinTask

internal const val PACKAGE_INDEX_NAME = "__packageIndex__"

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
private val excludedLibJars = java.util.Set.of("testFramework.core.jar", "testFramework.jar", "testFramework-java.jar")

private fun getClassLoadingLog(): InputStream {
  val osName = System.getProperty("os.name")
  val classifier = when {
    osName.startsWith("windows", ignoreCase = true) -> "windows"
    osName.startsWith("mac", ignoreCase = true) -> "mac"
    else -> "linux"
  }
  return PackageIndexBuilder::class.java.classLoader.getResourceAsStream("$classifier/class-report.txt")!!
}

private val sourceToNames: Map<String, MutableList<String>> by lazy {
  val sourceToNames = LinkedHashMap<String, MutableList<String>>()
  getClassLoadingLog().bufferedReader().forEachLine {
    val data = it.split(':', limit = 2)
    val sourcePath = data[1]
    // main jar is scrambled - doesn't make sense to reorder it
    if (sourcePath != "lib/idea.jar") {
      sourceToNames.computeIfAbsent(sourcePath) { mutableListOf() }.add(data[0])
    }
  }
  sourceToNames
}

internal fun reorderJar(relativePath: String, file: Path, traceContext: Context) {
  val orderedNames = sourceToNames.get(relativePath) ?: return
  tracer.spanBuilder("reorder jar")
    .setParent(traceContext)
    .setAttribute("relativePath", relativePath)
    .setAttribute("file", file.toString())
    .startSpan()
    .use {
      reorderJar(jarFile = file, orderedNames = orderedNames, resultJarFile = file)
    }
}

fun writeClasspath(homeDir: Path, mainJarName: String, antLibDir: Path?) {
  tracer.spanBuilder("generate classpath.txt")
    .setAttribute("dir", homeDir.toString())
    .startSpan()
    .use {
      val libDir = homeDir.resolve("lib")

      val osName = System.getProperty("os.name")
      val classifier = when {
        osName.startsWith("windows", ignoreCase = true) -> "windows"
        osName.startsWith("mac", ignoreCase = true) -> "mac"
        else -> "linux"
      }

      val sourceToNames = readClassLoadingLog(
        classLoadingLog = PackageIndexBuilder::class.java.classLoader.getResourceAsStream("$classifier/class-report.txt")!!,
        rootDir = homeDir,
        mainJarName = mainJarName
      )
      val coreClassLoaderFiles = computeAppClassPath(sourceToNames, libDir, antLibDir)
      val resultFile = libDir.resolve("classpath.txt")
      Files.writeString(resultFile, coreClassLoaderFiles.joinToString(separator = "\n") { libDir.relativize(it).toString() })
    }
}

private fun computeAppClassPath(sourceToNames: Map<Path, List<String>>, libDir: Path, antLibDir: Path?): LinkedHashSet<Path> {
  // sorted to ensure stable performance results
  val existing = TreeSet<Path>()
  addJarsFromDir(libDir) { paths ->
    paths.filterTo(existing) { !excludedLibJars.contains(it.fileName.toString()) }
  }

  val result = LinkedHashSet<Path>()
  // add first - should be listed first
  sourceToNames.keys.filterTo(result) { it.parent == libDir && existing.contains(it) }
  result.addAll(existing)

  if (antLibDir != null) {
    val distAntLib = libDir.resolve("ant/lib")
    addJarsFromDir(antLibDir) { paths ->
      // sort to ensure stable performance results
      result.addAll(paths.map { distAntLib.resolve(antLibDir.relativize(it)) }.sorted())
    }
  }
  return result
}

private inline fun addJarsFromDir(dir: Path, consumer: (Sequence<Path>) -> Unit) {
  Files.newDirectoryStream(dir).use { stream ->
    consumer(stream.asSequence().filter { it.toString().endsWith(".jar") })
  }
}

internal fun readClassLoadingLog(classLoadingLog: InputStream, rootDir: Path, mainJarName: String): Map<Path, List<String>> {
  val sourceToNames = LinkedHashMap<Path, MutableList<String>>()
  classLoadingLog.bufferedReader().forEachLine {
    val data = it.split(':', limit = 2)
    var sourcePath = data[1]
    if (sourcePath == "lib/idea.jar") {
      sourcePath = "lib/$mainJarName"
    }
    sourceToNames.computeIfAbsent(rootDir.resolve(sourcePath)) { mutableListOf() }.add(data[0])
  }
  return sourceToNames
}

internal fun doReorderJars(sourceToNames: Map<Path, List<String>>, sourceDir: Path, targetDir: Path) {
  ForkJoinTask.invokeAll(sourceToNames.mapNotNull { (jarFile, orderedNames) ->
    if (Files.notExists(jarFile)) {
      Span.current().addEvent("cannot find jar", Attributes.of(AttributeKey.stringKey("file"), sourceDir.relativize(jarFile).toString()))
      return@mapNotNull null
    }

    task(tracer.spanBuilder("reorder jar")
           .setAttribute("file", sourceDir.relativize(jarFile).toString())) {
      reorderJar(jarFile, orderedNames, if (targetDir == sourceDir) jarFile else targetDir.resolve(sourceDir.relativize(jarFile)))
    }
  })
}

data class PackageIndexEntry(val path: Path, val classPackageIndex: IntSet, val resourcePackageIndex: IntSet)

fun reorderJar(jarFile: Path, orderedNames: List<String>, resultJarFile: Path): PackageIndexEntry {
  val orderedNameToIndex = Object2IntOpenHashMap<String>(orderedNames.size)
  orderedNameToIndex.defaultReturnValue(-1)
  for ((index, orderedName) in orderedNames.withIndex()) {
    orderedNameToIndex.put(orderedName, index)
  }

  val tempJarFile = resultJarFile.resolveSibling("${resultJarFile.fileName}_reorder")

  val packageIndexBuilder = PackageIndexBuilder()

  ImmutableZipFile.load(jarFile).use { zipFile ->
    // ignore existing package index on reorder - a new one will be computed even if it is the same, do not optimize for simplicity
    val entries = zipFile.entries.toMutableList()
    // package index in the end
    for (i in (entries.size - 1) downTo 0) {
      if (entries.get(i).name == PACKAGE_INDEX_NAME) {
        entries.removeAt(i)
        break
      }
    }
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

    writeNewZip(tempJarFile) { zipCreator ->
      writeEntries(entries.iterator(), zipCreator, zipFile, packageIndexBuilder)
      packageIndexBuilder.writeDirs(zipCreator)
      packageIndexBuilder.writePackageIndex(zipCreator)
    }
  }

  try {
    Files.move(tempJarFile, resultJarFile, StandardCopyOption.REPLACE_EXISTING)
  }
  catch (e: Exception) {
    throw e
  }
  finally {
    Files.deleteIfExists(tempJarFile)
  }

  return PackageIndexEntry(path = resultJarFile, packageIndexBuilder.classPackageHashSet, packageIndexBuilder.resourcePackageHashSet)
}

internal fun writeEntries(entries: Iterator<ImmutableZipEntry>,
                          zipCreator: ZipFileWriter,
                          sourceZipFile: ImmutableZipFile,
                          packageIndexBuilder: PackageIndexBuilder?) {
  for (entry in entries) {
    if (entry.isDirectory) {
      continue
    }

    val name = entry.name
    packageIndexBuilder?.addFile(name)

    val data = entry.getByteBuffer(sourceZipFile)
    try {
      zipCreator.uncompressedData(name, data)
    }
    finally {
      entry.releaseBuffer(data)
    }
  }
}