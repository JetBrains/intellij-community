// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import java.nio.file.Files
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.*
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import kotlin.math.min

// symlinks not supported but can be easily implemented - see CollectingVisitor.visitFile
fun zip(targetFile: Path, dirs: Map<Path, String>, compress: Boolean = true, addDirEntries: Boolean = false, logger: System.Logger? = null) {
  // note - dirs contain duplicated directories (you cannot simply add directory entry on visit - uniqueness must be preserved)
  // anyway, directory entry are not added
  val executorService = Executors.newWorkStealingPool(if (compress) min(Runtime.getRuntime().availableProcessors() - 1, 2) else 2)
  val zipCreator = ParallelScatterZipCreator(executorService = executorService, compress = compress)
  val archiver = ZipArchiver(method = if (compress) ZipEntry.DEFLATED else ZipEntry.STORED, zipCreator)
  for ((dir, prefix) in dirs.entries) {
    val normalizedDir = dir.toAbsolutePath().normalize()
    archiver.setRootDir(normalizedDir, prefix)
    compressDir(normalizedDir, archiver)
  }
  executorService.shutdown()

  Files.createDirectories(targetFile.parent)
  ZipArchiveOutputStream(Files.newByteChannel(targetFile, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE)))
    .use { out ->
      zipCreator.writeTo(out)
      if (addDirEntries) {
        addDirForResourceFiles(out)
      }
    }

  val stats = zipCreator.statisticsMessage
  logger?.info("${targetFile.fileName} created in ${formatDuration(stats.compressionElapsed)} (merged in ${formatDuration(stats.mergingElapsed)})")
}

private fun formatDuration(value: Long): String {
  return Duration.ofMillis(value).toString().substring(2)
    .replace(Regex("(\\d[HMS])(?!$)"), "$1 ")
    .toLowerCase()
}

private fun addDirForResourceFiles(out: ZipArchiveOutputStream) {
  val dirSetWithoutClassFiles = HashSet<String>()
  @Suppress("DuplicatedCode")
  for (item in out.entries) {
    val name = item.entry.name
    assert(!name.endsWith('/'))
    if (name.endsWith(".class") || name.endsWith("/package.html") || name == "META-INF/MANIFEST.MF") {
      continue
    }

    var slashIndex = name.lastIndexOf('/')
    if (slashIndex != -1) {
      while (dirSetWithoutClassFiles.add(name.substring(0, slashIndex))) {
        slashIndex = name.lastIndexOf('/', slashIndex - 2)
        if (slashIndex == -1) {
          break
        }
      }
    }
  }

  val dirs = ArrayList(dirSetWithoutClassFiles)
  dirs.sort()
  for (dir in dirs) {
    out.addDirEntry(dir)
  }
}

private class ZipArchiver(private val method: Int, val zipCreator: ParallelScatterZipCreator) {
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
    val entry = ZipEntry(name)
    entry.method = method
    zipCreator.addEntry(entry, file)
  }
}

private fun compressDir(startDir: Path, archiver: ZipArchiver) {
  val dirCandidates = ArrayDeque<Path>()
  dirCandidates.add(startDir)
  val tempList = ArrayList<Path>()
  while (true) {
    val dir = dirCandidates.pollFirst() ?: break
    tempList.clear()
    try {
      Files.newDirectoryStream(dir).use {
        tempList.addAll(it)
      }
    }
    catch (e: NotDirectoryException) {
      // ok, it is file
      archiver.addFile(dir)
      continue
    }

    tempList.sort()
    for (file in tempList) {
      val path = file.toString()
      if (path.endsWith(".class") || (path.length > 4 && path[path.length - 3] == '.' &&
                                      (path.endsWith("xml") || path.endsWith("svg") || path.endsWith("png"))) ||
          path.endsWith(".properties")) {
        archiver.addFile(file)
      }
      else {
        dirCandidates.add(file)
      }
    }
  }
}