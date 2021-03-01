// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.tasks

import com.intellij.util.zip.ImmutableZipEntry
import com.intellij.util.zip.ImmutableZipFile
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.intellij.build.io.*
import java.lang.System.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

// see JarMemoryLoader.SIZE_ENTRY
internal const val SIZE_ENTRY = "META-INF/jb/$\$size$$"
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
              platformPrefix = "idea", logger = System.getLogger(""))
}

fun reorderJars(homeDir: Path,
                targetDir: Path,
                bootClassPathJarNames: Iterable<String>,
                stageDir: Path,
                platformPrefix: String,
                antLibDir: Path?,
                logger: Logger) {
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
  val coreClassLoaderFiles = computeAppClassPath(sourceToNames, libDir, antLibDir)

  logger.log(Logger.Level.INFO, "Reordering *.jar files in $homeDir")
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

internal fun readClassLoadingLog(classLoadingLogFile: Path, rootDir: Path): Map<Path, List<String>> {
  val sourceToNames = LinkedHashMap<Path, MutableList<String>>()
  Files.lines(classLoadingLogFile).use { lines ->
    lines.forEach {
      val data = it.split(':', limit = 2)
      val source = rootDir.resolve(data[1])
      sourceToNames.computeIfAbsent(source) { mutableListOf() }.add(data[0])
    }
  }
  return sourceToNames
}

internal fun doReorderJars(sourceToNames: Map<Path, List<String>>,
                           sourceDir: Path,
                           targetDir: Path,
                           logger: Logger): List<PackageIndexEntry> {
  val executor = createIoTaskExecutorPool()

  val results = mutableListOf<Future<PackageIndexEntry?>>()
  val errorOccurred = AtomicBoolean()
  for ((jarFile, orderedNames) in sourceToNames.entries) {
    if (!Files.exists(jarFile)) {
      logger.log(Logger.Level.ERROR, "Cannot find jar: $jarFile")
      continue
    }

    results.add(executor.submit(Callable {
      if (errorOccurred.get()) {
        return@Callable null
      }

      try {
        logger.info("Reorder jar: $jarFile")
        reorderJar(jarFile, orderedNames, if (targetDir == sourceDir) jarFile else targetDir.resolve(sourceDir.relativize(jarFile)))
      }
      catch (e: Throwable) {
        errorOccurred.set(true)
        throw e
      }
    }))
  }

  executor.shutdown()

  val index = ArrayList<PackageIndexEntry>(sourceToNames.size)
  for (future in results) {
    index.add(future.get() ?: continue)
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

    packageIndexBuilder.add(entries)

    Files.createDirectories(tempJarFile.parent)
    FileChannel.open(tempJarFile, RW_CREATE_NEW).use { outChannel ->
      val zipCreator = ZipFileWriter(outChannel, deflater = null)
      zipCreator.writeUncompressedEntry(SIZE_ENTRY, 2) {
        it.putShort((orderedNames.size and 0xffff).toShort())
      }

      packageIndexBuilder.writePackageIndex(zipCreator)
      writeEntries(entries, zipCreator, zipFile)
      writeDirs(packageIndexBuilder.dirsToCreate, zipCreator)

      val comment = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
      comment.putInt(1759251304)
      comment.putShort(orderedNames.size.toShort())
      comment.flip()

      zipCreator.finish(comment)
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

internal fun writeDirs(dirsToCreate: Set<String>, zipCreator: ZipFileWriter) {
  if (dirsToCreate.isEmpty()) {
    return
  }

  val list = dirsToCreate.toMutableList()
  list.sort()
  for (name in list) {
    // name in our ImmutableZipEntry doesn't have ending slash
    zipCreator.addDirEntry(if (name.endsWith('/')) name else "$name/")
  }
}

internal fun writeEntries(entries: List<ImmutableZipEntry>, zipCreator: ZipFileWriter, sourceZipFile: ImmutableZipFile) {
  for (entry in entries) {
    val name = entry.name
    if (entry.isDirectory) {
      continue
    }

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