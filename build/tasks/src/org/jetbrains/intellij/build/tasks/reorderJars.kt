// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "BlockingMethodInNonBlockingContext")

package org.jetbrains.intellij.build.tasks

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.context.Context
import it.unimi.dsi.fastutil.longs.LongSet
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.intellij.build.io.*
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*

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

fun generateClasspath(homeDir: Path, mainJarName: String, antTargetFile: Path?): List<String> {
  val libDir = homeDir.resolve("lib")
  val appFile = libDir.resolve("app.jar")

  tracer.spanBuilder("generate app.jar")
    .setAttribute("dir", homeDir.toString())
    .setAttribute("mainJarName", mainJarName)
    .startSpan()
    .use {
      transformFile(appFile) { target ->
        writeNewZip(target) { zipCreator ->
          val packageIndexBuilder = PackageIndexBuilder()
          copyZipRaw(appFile, packageIndexBuilder, zipCreator)

          val mainJar = libDir.resolve(mainJarName)
          if (Files.exists(mainJar)) {
            // no such file in community (no closed sources)
            copyZipRaw(mainJar, packageIndexBuilder, zipCreator)
            Files.delete(mainJar)
          }

          // packing to product.jar maybe disabled
          val productJar = libDir.resolve("product.jar")
          if (Files.exists(productJar)) {
            copyZipRaw(productJar, packageIndexBuilder, zipCreator)
            Files.delete(productJar)
          }

          packageIndexBuilder.writePackageIndex(zipCreator)
        }
      }
    }
  reorderJar("lib/app.jar", appFile, Context.current())

  tracer.spanBuilder("generate classpath")
    .setAttribute("dir", homeDir.toString())
    .startSpan()
    .use { span ->
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
      val files = computeAppClassPath(sourceToNames, libDir)
      if (antTargetFile != null) {
        files.add(antTargetFile)
      }
      val result = files.map { libDir.relativize(it).toString() }
      span.setAttribute(AttributeKey.stringArrayKey("result"), result)
      return result
    }
}

private fun computeAppClassPath(sourceToNames: Map<Path, List<String>>, libDir: Path): LinkedHashSet<Path> {
  // sorted to ensure stable performance results
  val existing = TreeSet<Path>()
  addJarsFromDir(libDir) { paths ->
    paths.filterTo(existing) { !excludedLibJars.contains(it.fileName.toString()) }
  }

  val result = LinkedHashSet<Path>()
  // add first - should be listed first
  sourceToNames.keys.filterTo(result) { it.parent == libDir && existing.contains(it) }
  result.addAll(existing)
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

data class PackageIndexEntry(val path: Path, val classPackageIndex: LongSet, val resourcePackageIndex: LongSet)


private class EntryData(@JvmField val name: String, @JvmField val entry: ZipEntry)

fun reorderJar(jarFile: Path, orderedNames: List<String>, resultJarFile: Path): PackageIndexEntry {
  val orderedNameToIndex = Object2IntOpenHashMap<String>(orderedNames.size)
  orderedNameToIndex.defaultReturnValue(-1)
  for ((index, orderedName) in orderedNames.withIndex()) {
    orderedNameToIndex.put(orderedName, index)
  }

  val tempJarFile = resultJarFile.resolveSibling("${resultJarFile.fileName}_reorder")

  val packageIndexBuilder = PackageIndexBuilder()

  mapFileAndUse(jarFile) { sourceBuffer, fileSize ->
    val entries = mutableListOf<EntryData>()
    readZipEntries(sourceBuffer, fileSize) { name, entry ->
      entries.add(EntryData(name, entry))
    }
    // ignore existing package index on reorder - a new one will be computed even if it is the same, do not optimize for simplicity
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
      for (item in entries) {
        packageIndexBuilder.addFile(item.name)
        zipCreator.uncompressedData(item.name, item.entry.getByteBuffer())
      }
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