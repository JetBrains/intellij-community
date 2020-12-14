// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.parallel.InputStreamSupplier
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.concurrent.Executors
import java.util.zip.ZipEntry

// symlinks not supported but can be easily implemented - see CollectingVisitor.visitFile
@JvmOverloads
fun zipForWindows(targetFile: Path, dirs: Iterable<Path>, addDirEntries: Boolean = false) {
  val zipCreator = ParallelScatterZipCreator(Executors.newWorkStealingPool())
  // note - dirs contain duplicated directories (you cannot simply add directory entry on visit - uniqueness must be preserved)
  // anyway, directory entry are not added
  for (dir in dirs) {
    val visitor = CollectingVisitor(zipCreator, dir.toAbsolutePath().normalize(), addDirEntries = addDirEntries)
    Files.walkFileTree(visitor.rootDir, visitor)
  }
  ZipArchiveOutputStream(Files.newByteChannel(targetFile, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE)))
    .use(zipCreator::writeTo)
}

private val emptyInputStream = object : InputStream() {
  override fun read() = -1
}

private val emptyInputStreamSupplier = InputStreamSupplier {
  emptyInputStream
}

private class CollectingVisitor(private val zipCreator: ParallelScatterZipCreator,
                                val rootDir: Path,
                                val addDirEntries: Boolean = false) : SimpleFileVisitor<Path>() {
  override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
    if (addDirEntries) {
      val entry = ZipArchiveEntry("${rootDir.relativize(dir).toString().replace('\\', '/')}/")
      entry.method = ZipEntry.STORED
      zipCreator.addArchiveEntry(entry, emptyInputStreamSupplier)
    }
    return super.preVisitDirectory(dir, attrs)
  }

  override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
    if (attrs.isSymbolicLink) {
      throw RuntimeException("Symlinks are not allowed for Windows archive")
    }

    val name = rootDir.relativize(file).toString().replace('\\', '/')
    val entry = ZipArchiveEntry(name)
    // Our JARs are not compressed, so, it should be compressed
    // even for 3rd-party libs there is a gain if compress again.
    // Same for PNGs. And in case of Android not all PNGs are optimized.
    // So, every file is compressed.
    // if skip jars+png+zip = 400MB overhead for IC (737MB -> 1100MB)
    // if skip zip+png = 40MB overhead for IC (737MB -> 782MB)
    entry.method = ZipEntry.DEFLATED
    entry.size = attrs.size()
    entry.lastModifiedTime = attrs.lastModifiedTime()
    zipCreator.addArchiveEntry(entry, InputStreamSupplier {
      BufferedInputStream(Files.newInputStream(file), 32_000)
    })
    return FileVisitResult.CONTINUE
  }
}
