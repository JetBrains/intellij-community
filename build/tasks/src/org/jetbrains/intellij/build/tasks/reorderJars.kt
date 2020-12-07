// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.tasks

import org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.parallel.InputStreamSupplier
import org.jetbrains.intellij.build.io.deleteDir
import org.jetbrains.intellij.build.io.info
import org.jetbrains.intellij.build.io.runJava
import java.io.ByteArrayInputStream
import java.lang.System.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry

// see JarMemoryLoader.SIZE_ENTRY
internal const val SIZE_ENTRY = "META-INF/jb/$\$size$$"

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
private val excludedLibJars = java.util.Set.of("testFramework.core.jar", "testFramework.jar", "testFramework-java.jar")

fun main(args: Array<String>) {
  System.setProperty("java.util.logging.SimpleFormatter.format", "%5\$s %n")
  reorderJars(homeDir = Paths.get(args[0]),
              targetDir = Paths.get(args[1]),
              bootClassPathJarNames = listOf("bootstrap.jar", "extensions.jar", "util.jar", "jdom.jar", "log4j.jar", "jna.jar"),
              stageDir = Paths.get(args[2]),
              antLibDir = null,
              platformPrefix = "idea", logger = System.getLogger(""))
}

fun reorderJars(homeDir: Path,
                targetDir: Path,
                bootClassPathJarNames: Iterable<String>,
                stageDir: Path,
                platformPrefix: String,
                antLibDir: Path?,
                logger: Logger): Path {
  val libDir = homeDir.resolve("lib")
  val ideaDirsParent = Files.createTempDirectory("idea-reorder-jars-")

  val classLoadingLogFile = stageDir.resolve("class-loading-log.txt")
  try {
    @Suppress("SpellCheckingInspection")
    runJava(
      mainClass = "com.intellij.idea.Main",
      args = listOf("jarOrder", classLoadingLogFile.toString()),
      jvmArgs = listOfNotNull("-Xmx1024m",
                              "-Didea.record.classpath.info=true",
                              "-Didea.system.path=${ideaDirsParent.resolve("system")}",
                              "-Didea.config.path=${ideaDirsParent.resolve("config")}",
                              "-Didea.home.path=$homeDir",
                              "-Didea.platform.prefix=$platformPrefix"),
      classPath = bootClassPathJarNames.map { libDir.resolve(it).toString() },
      logger = logger
    )
  }
  finally {
    deleteDir(ideaDirsParent)
  }

  val sourceToNames = readClassLoadingLog(classLoadingLogFile, homeDir)
  val appClassPathFile = stageDir.resolve("classpath.txt")
  val appClassPath = LinkedHashSet<Path>()
  // add first - should be listed first
  sourceToNames.keys.asSequence().filter { it.parent == libDir }.toCollection(appClassPath)
  addJarsFromDir(libDir) { paths ->
    // sort to ensure stable performance results
    appClassPath.addAll(paths.filter { !excludedLibJars.contains(it.fileName.toString()) }.sorted())
  }
  if (antLibDir != null) {
    val distAntLib = libDir.resolve("ant/lib")
    addJarsFromDir(antLibDir) { paths ->
      // sort to ensure stable performance results
      appClassPath.addAll(paths.map { distAntLib.resolve(antLibDir.relativize(it)) }.sorted())
    }
  }

  Files.writeString(appClassPathFile, appClassPath.joinToString(separator = "\n") { homeDir.relativize(it).toString() })

  logger.log(Logger.Level.INFO, "Reordering *.jar files in $homeDir")
  doReorderJars(sourceToNames = sourceToNames, sourceDir = homeDir, targetDir = targetDir, logger = logger)
  return appClassPathFile
}

private inline fun addJarsFromDir(dir: Path, consumer: (Sequence<Path>) -> Unit) {
  Files.newDirectoryStream(dir).use { stream ->
    consumer(stream.asSequence().filter { it.toString().endsWith(".jar") })
  }
}

internal fun readClassLoadingLog(classLoadingLogFile: Path, rootDir: Path): Map<Path, List<String>> {
  val sourceToNames = LinkedHashMap<Path, MutableList<String>>()
  Files.lines(classLoadingLogFile).use { lines ->
    lines.forEach {
      val data = it.split(':')
      val source = rootDir.resolve(data[1])
      sourceToNames.computeIfAbsent(source) { mutableListOf() }.add(data[0])
    }
  }
  return sourceToNames
}

internal fun doReorderJars(sourceToNames: Map<Path, List<String>>, sourceDir: Path, targetDir: Path, logger: Logger) {
  val executor = Executors.newWorkStealingPool(if (Runtime.getRuntime().availableProcessors() > 2) 4 else 2)
  val errorReference = AtomicReference<Throwable?>()
  for ((jarFile, orderedNames) in sourceToNames.entries) {
    if (errorReference.get() != null) {
      break
    }

    if (!Files.exists(jarFile)) {
      logger.log(Logger.Level.ERROR, "Cannot find jar: $jarFile")
      continue
    }

    executor.execute {
      if (errorReference.get() != null) {
        return@execute
      }

      logger.info("Reorder jar: $jarFile")
      try {
        reorderJar(jarFile, orderedNames, if (targetDir == sourceDir) jarFile else targetDir.resolve(sourceDir.relativize(jarFile)))
      }
      catch (e: Throwable) {
        errorReference.compareAndSet(null, e)
      }
    }
  }

  executor.shutdown()
  errorReference.get()?.let {
    throw it
  }

  executor.awaitTermination(8, TimeUnit.MINUTES)
}

private fun reorderJar(jarFile: Path, orderedNames: List<String>, resultJarFile: Path) {
  val orderedNameSet = HashSet(orderedNames)

  val tempJarFile = resultJarFile.resolveSibling("${resultJarFile.fileName}_reorder.jar")

  ZipFile(Files.newByteChannel(jarFile, EnumSet.of(StandardOpenOption.READ))).use { zipFile ->
    val entries = zipFile.entries.asSequence().filter { !it.isDirectory }.toMutableList()
    entries.sortWith(Comparator { o1, o2 ->
      val o2p = o2.name
      if ("META-INF/plugin.xml" == o2p) {
        return@Comparator Int.MAX_VALUE
      }

      val o1p = o1.name
      if ("META-INF/plugin.xml" == o1p) {
        -Int.MAX_VALUE
      }
      else if (orderedNameSet.contains(o1p)) {
        if (orderedNameSet.contains(o2p)) orderedNames.indexOf(o1p) - orderedNames.indexOf(o2p) else -1
      }
      else {
        if (orderedNameSet.contains(o2p)) 1 else 0
      }
    })

    val zipCreator = ParallelScatterZipCreator(Executors.newWorkStealingPool(2))
    addSizeEntry(orderedNames, zipCreator)
    for (originalEntry in entries) {
      // by intention not the whole original ZipArchiveEntry is copied,
      // but only selected properties are copied - that's enough and should be enough
      val entry = ZipArchiveEntry(originalEntry.name)
      entry.method = originalEntry.method
      entry.lastModifiedTime = originalEntry.lastModifiedTime
      entry.size = originalEntry.size
      zipCreator.addArchiveEntry(entry, InputStreamSupplier {
        zipFile.getInputStream(originalEntry)
      })
    }

    Files.createDirectories(resultJarFile.parent)
    ZipArchiveOutputStream(Files.newByteChannel(tempJarFile, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)))
      .use(zipCreator::writeTo)
  }

  try {
    Files.move(tempJarFile, resultJarFile)
  }
  catch (e: Exception) {
    throw e
  }
  finally {
    Files.deleteIfExists(tempJarFile)
  }
}

private fun addSizeEntry(orderedEntries: List<String>, zipCreator: ParallelScatterZipCreator) {
  val entry = ZipArchiveEntry(SIZE_ENTRY)
  val data = getZipShortBytes(orderedEntries.size)
  entry.method = ZipEntry.STORED
  entry.size = data.size.toLong()
  zipCreator.addArchiveEntry(entry, InputStreamSupplier {
    ByteArrayInputStream(data)
  })
}

// see ZipShort.getBytes
private fun getZipShortBytes(value: Int): ByteArray {
  return byteArrayOf((value and 0xFF).toByte(), ((value and 0xFF00) shr 8).toByte())
}