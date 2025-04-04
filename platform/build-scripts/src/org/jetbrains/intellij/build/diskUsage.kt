// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build

import com.intellij.openapi.util.text.StringUtilRt
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import java.io.IOException
import java.nio.file.FileStore
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

internal class NoDiskSpaceLeftException(message: String, e: IOException) : RuntimeException(message, e)

internal inline fun <T> checkForNoDiskSpace(context: BuildContext, task: () -> T): T {
  try {
    return task()
  }
  catch (e: NoDiskSpaceLeftException) {
    throw IOException(getDiskUsageDiagnostics(context.paths), e)
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
private fun getDiskInfo(fileStore: FileStore, builder: StringBuilder) {
  builder.appendLine("Disk info of ${fileStore.name()}")
  builder.appendLine("  Total space: " + StringUtilRt.formatFileSize(fileStore.totalSpace))
  builder.appendLine("  Unallocated space: " + StringUtilRt.formatFileSize(fileStore.unallocatedSpace))
  builder.appendLine("  Usable space: " + StringUtilRt.formatFileSize(fileStore.usableSpace))
}

private fun describe(dir: Path, builder: StringBuilder) {
  if (Files.notExists(dir)) {
    return
  }

  builder.appendLine("Disk usage by $dir")
  builder.append(getDiskInfo(Files.getFileStore(dir), builder))
  listDirectoryContent(dir, maxDepth = 3, builder)
  builder.append('\n')
}

private fun getDiskUsageDiagnostics(paths: BuildPaths): String {
  val builder = StringBuilder()
  describe(paths.distAllDir, builder)
  if (!paths.tempDir.startsWith(paths.distAllDir)) {
    describe(paths.tempDir, builder)
  }
  return builder.toString()
}

internal object TestListing {
  @JvmStatic
  fun main(args: Array<String>) {
    val builder = StringBuilder()
    listDirectoryContent(Path.of(args[0]), maxDepth = 3, result = builder)
    println(builder)
  }
}

private fun listDirectoryContent(directoryPath: Path, @Suppress("SameParameterValue") maxDepth: Int = 1, result: StringBuilder): String {
  if (Files.notExists(directoryPath)) {
    return "Directory does not exist: $directoryPath"
  }

  if (!Files.isDirectory(directoryPath)) {
    return "$directoryPath is not a directory"
  }

  // map to store directory paths and their sizes
  val directorySizes = Object2LongOpenHashMap<Path>()

  // list to store entries for display
  data class Entry(
    @JvmField val path: Path,
    @JvmField val size: Long,
    @JvmField val depth: Int,
  )

  val entries = mutableListOf<Entry>()

  Files.walkFileTree(directoryPath, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      // initialize size to 0 for all directories
      directorySizes.put(dir, 0L)

      val depth = directoryPath.relativize(dir).nameCount

      // add directory to entries for display if within max depth
      if (dir != directoryPath && depth <= maxDepth) {
        entries.add(Entry(path = dir, size = -1, depth = depth))
      }

      // skip traversing deeper than needed for display purposes
      if (depth > maxDepth) {
        return FileVisitResult.SKIP_SUBTREE
      }

      return FileVisitResult.CONTINUE
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      val size = attrs.size()
      val depth = directoryPath.relativize(file).nameCount

      // add a file to entries for display if within max depth
      if (depth <= maxDepth) {
        entries.add(Entry(path = file, size = size, depth = depth))
      }

      // add size to all parent directories
      var currentPath = file.parent
      while (currentPath != null && currentPath.startsWith(directoryPath)) {
        directorySizes.addTo(currentPath, size)
        currentPath = currentPath.parent
      }

      return FileVisitResult.CONTINUE
    }

    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
      val depth = directoryPath.relativize(file).nameCount

      if (depth <= maxDepth) {
        entries.add(Entry(path = file, size = 0, depth = depth))
      }

      return FileVisitResult.CONTINUE
    }
  })

  // format the output
  result.appendLine("Content of: $directoryPath")

  // group entries by parent directory to track last items
  val entriesByDepth = entries.groupByTo(Int2ObjectOpenHashMap()) { it.depth }

  // track the last entry at each depth level
  val lastEntryByDepth = Int2ObjectOpenHashMap<Path>()
  for (depth in 1..maxDepth) {
    entriesByDepth.get(depth)?.let { entriesAtDepth ->
      if (entriesAtDepth.isNotEmpty()) {
        lastEntryByDepth.put(depth, entriesAtDepth.last().path)
      }
    }
  }

  // Sort entries by path for tree-like structure
  entries.sortWith(compareBy({ it.path.parent }, { -it.size }, { it.path }))

  // display entries with their sizes
  for (entry in entries) {
    val depth = entry.depth
    val name = entry.path.fileName
    val isLast = entry.path == lastEntryByDepth.get(depth)

    // Create ASCII guide indentation
    val indent = StringBuilder()
    for (i in 1 until depth) {
      indent.append(if (lastEntryByDepth.get(i) != entry.path.subpath(0, i).toAbsolutePath()) "│   " else "    ")
    }

    // add connector for the current item
    val connector = if (isLast) "└── " else "├── "
    if (depth > 0) {
      indent.append(connector)
    }

    if (entry.size == -1L) {
      val size = directorySizes.getLong(entry.path)
      result.appendLine("$indent[DIR] $name: ${StringUtilRt.formatFileSize(size)}")
    }
    else {
      result.appendLine("$indent$name: ${StringUtilRt.formatFileSize(entry.size)}")
    }
  }
  // add total size
  result.appendLine("Total size: ${StringUtilRt.formatFileSize(directorySizes.getLong(directoryPath))}")

  return result.toString()
}