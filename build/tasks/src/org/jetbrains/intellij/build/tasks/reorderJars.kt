// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.tasks

import com.intellij.util.io.DirectByteBufferPool
import com.intellij.util.io.Murmur3_32Hash
import com.intellij.util.zip.ImmutableZipEntry
import com.intellij.util.zip.ImmutableZipFile
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
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
import kotlin.math.min

// see JarMemoryLoader.SIZE_ENTRY
internal const val SIZE_ENTRY = "META-INF/jb/$\$size$$"
private const val PACKAGE_INDEX_NAME = "__packageIndex__"

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
  doReorderJars(sourceToNames = sourceToNames, sourceDir = homeDir, targetDir = targetDir, logger = logger)
  return writeClassLoaderData(coreClassLoaderFiles, homeDir, stageDir)
}

private fun writeClassLoaderData(coreClassLoaderFiles: LinkedHashSet<Path>,
                                 homeDir: Path,
                                 stageDir: Path): Path {
  val resultFile = stageDir.resolve("classpath.txt")
  Files.writeString(resultFile, coreClassLoaderFiles.joinToString(separator = "\n") { homeDir.relativize(it).toString() })
  return resultFile
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

internal data class PackageIndexEntry(val path: Path, val classPackageIndex: IntOpenHashSet, val resourcePackageIndex: IntOpenHashSet)

private fun reorderJar(jarFile: Path, orderedNames: List<String>, resultJarFile: Path): PackageIndexEntry {
  val orderedNameSet = HashSet(orderedNames)

  val tempJarFile = resultJarFile.resolveSibling("${resultJarFile.fileName}_reorder.jar")

  val classPackageHashSet: IntOpenHashSet
  val resourcePackageHashSet: IntOpenHashSet

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
      else if (orderedNameSet.contains(o1p)) {
        if (orderedNameSet.contains(o2p)) orderedNames.indexOf(o1p) - orderedNames.indexOf(o2p) else -1
      }
      else {
        if (orderedNameSet.contains(o2p)) 1 else 0
      }
    })

    classPackageHashSet = IntOpenHashSet(min(entries.size / 4, 10))
    resourcePackageHashSet = IntOpenHashSet(min(entries.size / 4, 10))

    val dirsToCreate = HashSet<String>()
    computeDirsToCreate(entries, resourcePackageHashSet, dirsToCreate)

    Files.createDirectories(tempJarFile.parent)
    FileChannel.open(tempJarFile, RW_CREATE_NEW).use { outChannel ->
      val zipCreator = ZipFileWriter(outChannel, deflater = null)
      zipCreator.writeUncompressedEntry(SIZE_ENTRY, 2) {
        it.putShort((orderedNames.size and 0xffff).toShort())
      }
      writeEntries(entries, zipCreator, zipFile, classPackageHashSet, resourcePackageHashSet)
      writeDirs(dirsToCreate, zipCreator)
      writePackageIndex(zipCreator, classPackageHashSet, resourcePackageHashSet)

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

  return PackageIndexEntry(path = resultJarFile, classPackageHashSet, resourcePackageHashSet)
}

internal fun writeDirs(dirsToCreate: Set<String>, zipCreator: ZipFileWriter) {
  val list = dirsToCreate.toMutableList()
  list.sort()
  for (name in list) {
    // name in our ImmutableZipEntry doesn't have ending slash
    zipCreator.addDirEntry(if (name.endsWith('/')) name else "$name/")
  }
}

internal fun writePackageIndex(zipCreator: ZipFileWriter,
                               classPackageHashSet: IntOpenHashSet,
                               resourcePackageHashSet: IntOpenHashSet) {
  zipCreator.writeUncompressedEntry(PACKAGE_INDEX_NAME,
                                    (2 * Int.SIZE_BYTES) + ((classPackageHashSet.size + resourcePackageHashSet.size) * Int.SIZE_BYTES)) {
    val classPackages = classPackageHashSet.toIntArray()
    val resourcePackages = resourcePackageHashSet.toIntArray()
    // same content for same data
    classPackages.sort()
    resourcePackages.sort()
    it.putInt(classPackages.size)
    it.putInt(resourcePackages.size)
    val intBuffer = it.asIntBuffer()
    intBuffer.put(classPackages)
    intBuffer.put(resourcePackages)
    it.position(it.position() + (intBuffer.position() * Int.SIZE_BYTES))
  }
}

internal fun writeEntries(entries: List<ImmutableZipEntry>,
                          zipCreator: ZipFileWriter,
                          sourceZipFile: ImmutableZipFile,
                          classPackageHashSet: IntOpenHashSet,
                          resourcePackageHashSet: IntOpenHashSet) {
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
      DirectByteBufferPool.DEFAULT_POOL.release(data)
    }

    if (name.endsWith(".class")) {
      classPackageHashSet.add(getPackageNameHash(name))
    }
    else {
      resourcePackageHashSet.add(getPackageNameHash(name))
    }
  }
}

// leave only directories where some non-class files are located (as it can be requested in runtime, e.g. stubs, fileTemplates)
internal fun computeDirsToCreate(entries: List<ImmutableZipEntry>, resourcePackageHashSet: IntOpenHashSet, result: MutableSet<String>) {
  for (entry in entries) {
    val name = entry.name
    if (entry.isDirectory || name.endsWith(".class") || name.endsWith("/package.html") || name == "META-INF/MANIFEST.MF") {
      continue
    }

    @Suppress("DuplicatedCode")
    var slashIndex = name.lastIndexOf('/')
    if (slashIndex != -1) {
      var dirName = name.substring(0, slashIndex)
      while (result.add(dirName)) {
        resourcePackageHashSet.add(Murmur3_32Hash.MURMUR3_32.hashString(dirName, 0, dirName.length))

        slashIndex = dirName.lastIndexOf('/')
        if (slashIndex == -1) {
          break
        }

        dirName = name.substring(0, slashIndex)
      }
    }
  }
}

private fun getPackageNameHash(name: String): Int {
  val i = name.lastIndexOf('/')
  if (i == -1) {
    return 0
  }
  return Murmur3_32Hash.MURMUR3_32.hashString(name, 0, i)
}