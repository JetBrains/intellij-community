// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.tasks

import com.intellij.diagnostic.telemetry.use
import io.opentelemetry.api.common.AttributeKey
import it.unimi.dsi.fastutil.longs.LongSet
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import org.jetbrains.intellij.build.io.*
import org.jetbrains.intellij.build.tracer
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
    val sourcePath = data.get(1)
    // the main jar is scrambled - doesn't make sense to reorder it
    sourceToNames.computeIfAbsent(sourcePath) { mutableListOf() }.add(data.get(0))
  }
  sourceToNames
}

fun reorderJar(relativePath: String, file: Path) {
  val orderedNames = sourceToNames.get(relativePath) ?: return
  tracer.spanBuilder("reorder jar")
    .setAttribute("relativePath", relativePath)
    .setAttribute("file", file.toString())
    .use {
      reorderJar(jarFile = file, orderedNames = orderedNames, resultJarFile = file)
    }
}

fun generateClasspath(homeDir: Path, antTargetFile: Path?): PersistentList<String> {
  val libDir = homeDir.resolve("lib")
  val appFile = libDir.resolve("app.jar")

  tracer.spanBuilder("generate app.jar")
    .setAttribute("dir", homeDir.toString())
    .use {
      transformFile(appFile) { target ->
        writeNewZip(target) { zipCreator ->
          val packageIndexBuilder = PackageIndexBuilder()
          copyZipRaw(appFile, packageIndexBuilder, zipCreator) { entryName ->
            entryName != "module-info.class"
          }

          // packing to product.jar maybe disabled
          val productJar = libDir.resolve("product.jar")
          if (Files.exists(productJar)) {
            copyZipRaw(productJar, packageIndexBuilder, zipCreator) { entryName ->
              entryName != "module-info.class"
            }
            Files.delete(productJar)
          }

          packageIndexBuilder.writePackageIndex(zipCreator)
        }
      }
    }
  reorderJar("lib/app.jar", appFile)

  tracer.spanBuilder("generate classpath")
    .setAttribute("dir", homeDir.toString())
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
      )
      val files = computeAppClassPath(sourceToNames, libDir)
      if (antTargetFile != null) {
        files.add(antTargetFile)
      }
      val result = files.map { libDir.relativize(it).toString() }
      span.setAttribute(AttributeKey.stringArrayKey("result"), result)
      return result.toPersistentList()
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

internal fun readClassLoadingLog(classLoadingLog: InputStream, rootDir: Path): Map<Path, List<String>> {
  val sourceToNames = LinkedHashMap<Path, MutableList<String>>()
  classLoadingLog.bufferedReader().forEachLine {
    val data = it.split(':', limit = 2)
    val sourcePath = data[1]
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
