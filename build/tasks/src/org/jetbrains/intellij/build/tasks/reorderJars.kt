// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.tasks

import com.intellij.util.lang.ImmutableZipEntry
import com.intellij.util.lang.ImmutableZipFile
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.intellij.build.io.RW_CREATE_NEW
import org.jetbrains.intellij.build.io.ZipFileWriter
import org.jetbrains.intellij.build.io.info
import org.jetbrains.intellij.build.io.warn
import java.io.InputStream
import java.lang.System.Logger
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.Callable
import java.util.concurrent.ForkJoinTask

internal const val PACKAGE_INDEX_NAME = "__packageIndex__"

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
private val excludedLibJars = java.util.Set.of("testFramework.core.jar", "testFramework.jar", "testFramework-java.jar")

fun main(args: Array<String>) {
  System.setProperty("java.util.logging.SimpleFormatter.format", "%5\$s %n")
  reorderJars(homeDir = Path.of(args[0]),
              targetDir = Path.of(args[1]),
              bootClassPathJarNames = listOf("bootstrap.jar", "util.jar", "jdom.jar", "log4j.jar", "jna.jar"),
              stageDir = Path.of(args[2]),
              antLibDir = null,
              mainJarName = "idea.jar", logger = System.getLogger(""))
}

fun reorderJars(homeDir: Path,
                targetDir: Path,
                bootClassPathJarNames: Iterable<String>,
                stageDir: Path,
                mainJarName: String,
                antLibDir: Path?,
                logger: Logger) {
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

  logger.info("Reordering *.jar files in $homeDir")
  doReorderJars(sourceToNames = sourceToNames, sourceDir = homeDir, targetDir = targetDir, logger = logger)
  val resultFile = libDir.resolve("classpath.txt")
  Files.writeString(resultFile, coreClassLoaderFiles.joinToString(separator = "\n") { libDir.relativize(it).toString() })
}

private fun computeAppClassPath(sourceToNames: Map<Path, List<String>>, libDir: Path, antLibDir: Path?): LinkedHashSet<Path> {
  val result = LinkedHashSet<Path>()
  // add first - should be listed first
  sourceToNames.keys.asSequence().filter { it.parent == libDir }.toCollection(result)
  addJarsFromDir(libDir) { paths ->
    // sort to ensure stable performance results
    result.addAll(paths.filter { !excludedLibJars.contains(it.fileName.toString()) }.sorted())
  }
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

internal fun doReorderJars(sourceToNames: Map<Path, List<String>>,
                           sourceDir: Path,
                           targetDir: Path,
                           logger: Logger): List<PackageIndexEntry> {
  val tasks = mutableListOf<ForkJoinTask<PackageIndexEntry?>>()
  for ((jarFile, orderedNames) in sourceToNames.entries) {
    if (!Files.exists(jarFile)) {
      logger.warn("Cannot find jar: $jarFile")
      continue
    }

    tasks.add(ForkJoinTask.adapt(Callable {
      logger.info("Reorder jar: $jarFile")
      reorderJar(jarFile, orderedNames, if (targetDir == sourceDir) jarFile else targetDir.resolve(sourceDir.relativize(jarFile)))
    }))
  }

  ForkJoinTask.invokeAll(tasks)

  val index = ArrayList<PackageIndexEntry>(sourceToNames.size)
  for (task in tasks) {
    index.add(task.rawResult ?: continue)
  }
  return index
}

data class PackageIndexEntry(val path: Path, val classPackageIndex: IntSet, val resourcePackageIndex: IntSet)

fun reorderJar(jarFile: Path, orderedNames: List<String>, resultJarFile: Path): PackageIndexEntry {
  val orderedNameToIndex = HashMap<String, Int>(orderedNames.size)
  for ((index, orderedName) in orderedNames.withIndex()) {
    orderedNameToIndex.put(orderedName, index)
  }

  val tempJarFile = resultJarFile.resolveSibling("${resultJarFile.fileName}_reorder.jar")

  val packageIndexBuilder = PackageIndexBuilder()

  ImmutableZipFile.load(jarFile).use { zipFile ->
    // ignore existing package index on reorder - a new one will computed even if it is the same, do not optimize for simplicity
    val entries = zipFile.entries.asSequence().filter { it.name != PACKAGE_INDEX_NAME }.toMutableList()
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
        val i1 = orderedNameToIndex.get(o1p)
        if (i1 == null) {
          if (orderedNameToIndex.containsKey(o2p)) 1 else 0
        }
        else {
          val i2 = orderedNameToIndex.get(o2p)
          if (i2 == null) -1 else (i1 - i2)
        }
      }
    })

    Files.createDirectories(tempJarFile.parent)
    FileChannel.open(tempJarFile, RW_CREATE_NEW).use { outChannel ->
      val zipCreator = ZipFileWriter(outChannel, deflater = null)
      writeEntries(entries.iterator(), zipCreator, zipFile, packageIndexBuilder)
      packageIndexBuilder.writeDirs(zipCreator)
      packageIndexBuilder.writePackageIndex(zipCreator)
      zipCreator.finish()
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
                          packageIndexBuilder: PackageIndexBuilder) {
  for (entry in entries) {
    if (entry.isDirectory) {
      continue
    }

    val name = entry.name
    packageIndexBuilder.addFile(name)

    // by intention not the whole original ZipArchiveEntry is copied,
    // but only name, method and size are copied - that's enough and should be enough
    val data = entry.getByteBuffer(sourceZipFile)
    try {
      zipCreator.writeUncompressedEntry(name, data)
    }
    finally {
      entry.releaseBuffer(data)
    }
  }
}