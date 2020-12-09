// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.tasks

import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.*
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.IntConsumer
import java.util.zip.ZipEntry

// see JarMemoryLoader.SIZE_ENTRY
internal const val SIZE_ENTRY = "META-INF/jb/$\$size$$"

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
private val excludedLibJars = java.util.Set.of("testFramework.core.jar", "testFramework.jar", "testFramework-java.jar")

private val simpleClassPathIndex = System.getProperty("build.simple.index", "true").toBoolean()

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
  val coreClassLoaderFiles = computeAppClassPath(sourceToNames, libDir, antLibDir)

  logger.log(Logger.Level.INFO, "Reordering *.jar files in $homeDir")
  val packageIndex = doReorderJars(sourceToNames = sourceToNames, sourceDir = homeDir, targetDir = targetDir, logger = logger)
  return writeClassLoaderData(packageIndex, coreClassLoaderFiles, homeDir, stageDir)
}

private fun writeClassLoaderData(packageIndex: List<PackageIndexEntry>,
                                 coreClassLoaderFiles: LinkedHashSet<Path>,
                                 homeDir: Path,
                                 stageDir: Path): Path {
  // to simplify code for now we build package index on the fly and merge it on the fly
  // not yet clear - does it worth to build unified package index for all JARs or not
  if (simpleClassPathIndex) {
    val resultFile = stageDir.resolve("classpath.txt")
    Files.writeString(resultFile, coreClassLoaderFiles.joinToString(separator = "\n") { homeDir.relativize(it).toString() })
    return resultFile
  }

  val byteBuffer = ByteBuffer.allocate(
    256 +
    packageIndex.sumBy { Short.SIZE_BYTES + (it.path.toString().length * 2) + (it.index.size * (Int.SIZE_BYTES + Short.SIZE_BYTES)) } +
    coreClassLoaderFiles.sumBy { Short.SIZE_BYTES + (it.toString().length * 2) }
  ).order(ByteOrder.LITTLE_ENDIAN)
  // version
  byteBuffer.put(0)
  // files for core class loader class path
  byteBuffer.putShort(coreClassLoaderFiles.size.toShort())
  for (file in coreClassLoaderFiles) {
    writePath(file, homeDir, byteBuffer)
  }

  // package index (currently, only for JARs that were used during start-up)
  byteBuffer.putShort(packageIndex.size.toShort())
  for (item in packageIndex) {
    writePath(item.path, homeDir, byteBuffer)

    byteBuffer.putInt(item.index.size)
    item.index.forEach(IntConsumer {
      byteBuffer.putInt(it)
    })
  }

  byteBuffer.flip()
  val appClassPathFile = stageDir.resolve("classpath.db")
  Files.newByteChannel(appClassPathFile, EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE)).use {
    do {
      it.write(byteBuffer)
    }
    while (byteBuffer.hasRemaining())
  }
  return appClassPathFile
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

private fun writePath(file: Path, homeDir: Path, byteBuffer: ByteBuffer) {
  val nameBytes = homeDir.relativize(file).toString().toByteArray()
  byteBuffer.putShort(nameBytes.size.toShort())
  byteBuffer.put(nameBytes)
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
  val executor = Executors.newWorkStealingPool(if (Runtime.getRuntime().availableProcessors() > 2) 4 else 2)

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

internal data class PackageIndexEntry(val path: Path, val index: IntOpenHashSet)

private fun reorderJar(jarFile: Path, orderedNames: List<String>, resultJarFile: Path): PackageIndexEntry {
  val orderedNameSet = HashSet(orderedNames)

  val tempJarFile = resultJarFile.resolveSibling("${resultJarFile.fileName}_reorder.jar")

  val hasher = Hashing.murmur3_32()
  val packageHashSet: IntOpenHashSet

  ZipFile(Files.newByteChannel(jarFile, EnumSet.of(StandardOpenOption.READ))).use { zipFile ->
    val entries = zipFile.entries.asSequence().toMutableList()
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

    packageHashSet = IntOpenHashSet(entries.size)

    // leave only directories where some non-class files are located (as it can be requested in runtime, e.g. stubs, fileTemplates)
    val dirSetWithoutClassFiles = HashSet<String>()
    for (entry in entries) {
      val name = entry.name
      if (!entry.isDirectory && !name.endsWith(".class") && !name.endsWith("/package.html") && name != "META-INF/MANIFEST.MF") {
        val slashIndex = name.lastIndexOf('/')
        if (slashIndex != -1) {
          dirSetWithoutClassFiles.add(name.substring(0, slashIndex))
        }
      }
    }

    val zipCreator = ParallelScatterZipCreator(Executors.newWorkStealingPool(2))
    addSizeEntry(orderedNames, zipCreator)
    for (originalEntry in entries) {
      val name = originalEntry.name

      if (originalEntry.isDirectory &&
          (dirSetWithoutClassFiles.isEmpty() || !dirSetWithoutClassFiles.contains(name.substring(0, name.length - 1)))) {
        continue
      }

      // by intention not the whole original ZipArchiveEntry is copied,
      // but only name, method and size are copied - that's enough and should be enough
      val entry = ZipArchiveEntry(name)
      entry.method = originalEntry.method
      entry.size = originalEntry.size
      zipCreator.addArchiveEntry(entry, InputStreamSupplier {
        zipFile.getInputStream(originalEntry)
      })

      packageHashSet.add(getPackageNameHash(name, hasher))
    }

    Files.createDirectories(resultJarFile.parent)
    (Files.newByteChannel(tempJarFile, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.READ,
                                                  StandardOpenOption.CREATE_NEW)) as FileChannel).use { outChannel ->
      val stream = ZipArchiveOutputStream(outChannel)
      zipCreator.writeTo(stream)
      stream.finish()
      stream.flush()

      // apache doesn't allow to set comment as raw bytes - write on own
      val size = outChannel.size()
      val buffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
      buffer.limit(22)
      if (outChannel.read(buffer, size - buffer.limit()) != buffer.limit()) {
        throw IllegalStateException()
      }

      if (buffer.getInt(0) != 101010256) {
        throw IllegalStateException("Expected end of central directory signature")
      }
      if (buffer.getShort(20) != 0.toShort()) {
        throw IllegalStateException("Comment length expected to be 0")
      }

      buffer.position(0)
      buffer.putShort((Int.SIZE_BYTES + Short.SIZE_BYTES).toShort())
      buffer.putInt(1759251304)
      buffer.putShort(orderedNames.size.toShort())
      buffer.flip()

      do {
        outChannel.write(buffer, (size - Short.SIZE_BYTES) + buffer.position())
      }
      while (buffer.hasRemaining())

      stream.close()
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

  return PackageIndexEntry(path = resultJarFile, packageHashSet)
}

private fun getPackageNameHash(name: String, hasher: HashFunction): Int {
  val i = name.lastIndexOf('/')
  if (i == -1) {
    return 0
  }
  return hasher.hashString(name.substring(0, i), Charsets.UTF_8).asInt()
}

private fun addSizeEntry(orderedNames: List<String>, zipCreator: ParallelScatterZipCreator) {
  val entry = ZipArchiveEntry(SIZE_ENTRY)
  val data = intToLittleEndian(orderedNames.size)
  entry.method = ZipEntry.STORED
  entry.size = data.size.toLong()
  zipCreator.addArchiveEntry(entry, InputStreamSupplier {
    ByteArrayInputStream(data)
  })
}

private fun intToLittleEndian(value: Int): ByteArray {
  return byteArrayOf((value and 0xFF).toByte(), ((value and 0xFF00) shr 8).toByte())
}