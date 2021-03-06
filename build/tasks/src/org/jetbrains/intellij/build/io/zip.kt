// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.Deflater
import java.util.zip.ZipEntry

// symlinks not supported but can be easily implemented - see CollectingVisitor.visitFile
fun zip(targetFile: Path, dirs: Map<Path, String>, compress: Boolean = true, addDirEntries: Boolean = false, logger: System.Logger? = null) {
  // note - dirs contain duplicated directories (you cannot simply add directory entry on visit - uniqueness must be preserved)
  // anyway, directory entry are not added
  Files.createDirectories(targetFile.parent)
  val start = System.currentTimeMillis()
  FileChannel.open(targetFile, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE)).use {
    val zipCreator = ZipFileWriter(it, if (compress) Deflater(Deflater.DEFAULT_COMPRESSION, true) else null)

    val fileAdded: ((String) -> Unit)?
    val dirNameSetToAdd: Set<String>
    if (addDirEntries) {
      dirNameSetToAdd = LinkedHashSet()
      fileAdded = { name ->
        if (!name.endsWith(".class") && !name.endsWith("/package.html") && name != "META-INF/MANIFEST.MF") {
          var slashIndex = name.lastIndexOf('/')
          if (slashIndex != -1) {
            while (dirNameSetToAdd.add(name.substring(0, slashIndex))) {
              slashIndex = name.lastIndexOf('/', slashIndex - 2)
              if (slashIndex == -1) {
                break
              }
            }
          }
        }
      }
    }
    else {
      fileAdded = null
      dirNameSetToAdd = emptySet()
    }

    val archiver = ZipArchiver(method = if (compress) ZipEntry.DEFLATED else ZipEntry.STORED, zipCreator, fileAdded)
    for ((dir, prefix) in dirs.entries) {
      val normalizedDir = dir.toAbsolutePath().normalize()
      archiver.setRootDir(normalizedDir, prefix)
      compressDir(normalizedDir, archiver)
    }

    if (dirNameSetToAdd.isNotEmpty()) {
      addDirForResourceFiles(zipCreator, dirNameSetToAdd)
    }
    zipCreator.finish(null)
  }

  logger?.info("${targetFile.fileName} created in ${formatDuration(System.currentTimeMillis() - start)}")
}

fun bulkZipWithPrefix(commonSourceDir: Path, items: List<Map.Entry<String, Path>>, compress: Boolean, logger: System.Logger) {
  val pool = Executors.newWorkStealingPool()
  logger.debug { "Create ${items.size} archives in parallel (commonSourceDir=$commonSourceDir)" }
  val error = AtomicReference<Throwable>()
  for (item in items) {
    pool.execute {
      if (error.get() != null) {
        return@execute
      }

      try {
        zip(item.value, mapOf(commonSourceDir.resolve(item.key) to item.key), compress, logger = logger)
      }
      catch (e: Throwable) {
        error.compareAndSet(null, e)
      }
    }
  }

  pool.shutdown()
  pool.awaitTermination(1, TimeUnit.HOURS)
  error.get()?.let {
    throw it
  }
}

private fun formatDuration(value: Long): String {
  return Duration.ofMillis(value).toString().substring(2)
    .replace(Regex("(\\d[HMS])(?!$)"), "$1 ")
    .toLowerCase()
}

private fun addDirForResourceFiles(out: ZipFileWriter, dirNameSetToAdd: Set<String>) {
  for (dir in dirNameSetToAdd) {
    out.addDirEntry("$dir/")
  }
}

private class ZipArchiver(private val method: Int, val zipCreator: ZipFileWriter, val fileAdded: ((String) -> Unit)?) {
  private var localPrefixLength = -1
  private var archivePrefix = ""

  // rootDir must be absolute and normalized
  fun setRootDir(rootDir: Path, prefix: String) {
    archivePrefix = when {
      prefix.isNotEmpty() && !prefix.endsWith('/') -> "$prefix/"
      prefix == "/" -> ""
      else -> prefix
    }

    localPrefixLength = rootDir.toString().length + 1
  }

  fun addFile(file: Path) {
    val name = archivePrefix + file.toString().substring(localPrefixLength).replace('\\', '/')
    fileAdded?.invoke(name)
    zipCreator.writeEntry(name, method, file)
  }
}

private fun compressDir(startDir: Path, archiver: ZipArchiver) {
  val dirCandidates = ArrayDeque<Path>()
  dirCandidates.add(startDir)
  val tempList = ArrayList<Path>()
  while (true) {
    val dir = dirCandidates.pollFirst() ?: break
    tempList.clear()
    Files.newDirectoryStream(dir).use {
      tempList.addAll(it)
    }

    tempList.sort()
    for (file in tempList) {
      if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
        dirCandidates.add(file)
      }
      else {
        archiver.addFile(file)
      }
    }
  }
}

internal fun createIoTaskExecutorPool(): ExecutorService {
  return Executors.newWorkStealingPool(if (Runtime.getRuntime().availableProcessors() > 2) 4 else 2)
}