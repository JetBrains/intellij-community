// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ConstPropertyName")

package org.jetbrains.intellij.build.impl

import com.intellij.util.PathUtilRt
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.io.unmapBuffer
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.zip.ZipEntry
import kotlin.io.path.readText

private const val fileFlag = 32768  // 0100000

internal const val executableFileUnixMode: Int = fileFlag or 493  // 0755

fun filterFileIfAlreadyInZip(relativePath: String, file: Path, zipFiles: MutableMap<String, Path>): Boolean {
  val old = zipFiles.putIfAbsent(relativePath, file) ?: return true
  if (compareByMemoryMappedFiles(file, old)) {
    return false
  }

  val file1Text = file.readText()
  val file2Text = old.readText()
  val isAsciiText: (Char) -> Boolean = { it == '\t' || it == '\n' || it == '\r' || it.code in 32..126 }
  val message = "Two files '${old}' and '${file}' with the same target path '${relativePath}' have different content"
  if (file1Text.take(1024).all(isAsciiText) && file2Text.take(1024).all(isAsciiText)) {
    throw RuntimeException("$message\n\nFile 1: ${"-".repeat(80)}\n$file1Text\n\nFile 2 ${"-".repeat(80)}\n$file2Text")
  }
  else {
    throw RuntimeException(message)
  }
}

private fun compareByMemoryMappedFiles(path1: Path, path2: Path): Boolean {
  FileChannel.open(path1, StandardOpenOption.READ).use { channel1 ->
    FileChannel.open(path2, StandardOpenOption.READ).use { channel2 ->
      val size = channel1.size()
      if (size != channel2.size()) {
        return false
      }

      val m1 = channel1.map(FileChannel.MapMode.READ_ONLY, 0, size)
      try {
        val m2 = channel2.map(FileChannel.MapMode.READ_ONLY, 0, size)
        try {
          return m1 == m2
        }
        finally {
          unmapBuffer(m2)
        }
      }
      finally {
        unmapBuffer(m1)
      }
    }
  }
}

fun consumeDataByPrefix(file: Path, prefixWithEndingSlash: String, consumer: BiConsumer<String, ByteArray>) {
  readZipFile(file) { name, dataSupplier ->
    if (name.startsWith(prefixWithEndingSlash)) {
      val buffer = dataSupplier()
      val array = ByteArray(buffer.remaining())
      buffer.get(array)
      consumer.accept(name, array)
    }
  }
}

fun ZipArchiveOutputStream.dir(startDir: Path,
                               prefix: String,
                               fileFilter: ((sourceFile: Path, relativePath: String) -> Boolean)? = null,
                               entryCustomizer: ((entry: ZipArchiveEntry, sourceFile: Path, relativePath: String) -> Unit)? = null) {
  val dirCandidates = ArrayDeque<Path>()
  dirCandidates.add(startDir)
  val tempList = ArrayList<Path>()
  while (true) {
    val dir = dirCandidates.pollFirst() ?: break
    tempList.clear()

    val dirStream = try {
      Files.newDirectoryStream(dir)
    }
    catch (e: NoSuchFileException) {
      continue
    }

    dirStream.use {
      tempList.addAll(it)
    }

    tempList.sort()
    for (file in tempList) {
      val attributes = Files.readAttributes(file, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
      if (attributes.isDirectory) {
        dirCandidates.add(file)
      }
      else if (attributes.isSymbolicLink) {
        val entry = zipArchiveEntry(prefix + startDir.relativize(file).toString().replace('\\', '/'))
        entry.method = ZipEntry.STORED
        entry.lastModifiedTime = buildTime
        entry.unixMode = Files.readAttributes(file, "unix:mode", LinkOption.NOFOLLOW_LINKS)["mode"] as Int
        val path = Files.readSymbolicLink(file).let { if (it.isAbsolute) prefix + startDir.relativize(it) else it.toString() }
        val data = path.toByteArray()
        entry.size = data.size.toLong()
        putArchiveEntry(entry)
        write(data)
        closeArchiveEntry()
      }
      else {
        assert(attributes.isRegularFile)

        val relativePath = startDir.relativize(file).toString().replace('\\', '/')
        if (fileFilter != null && !fileFilter(file, relativePath)) {
          continue
        }

        val entry = zipArchiveEntry(prefix + relativePath)
        entry.size = attributes.size()
        entryCustomizer?.invoke(entry, file, relativePath)
        writeFileEntry(file, entry, this)
      }
    }
  }
}

internal fun ZipArchiveOutputStream.entryToDir(file: Path, zipPath: String) {
  entry("$zipPath/${file.fileName}", file)
}

/**
 * Cannot be set to zero, see [BuildOptions.buildDateInSeconds] initialization code.
 */
private val buildTime = FileTime.from(BuildOptions().buildDateInSeconds, TimeUnit.SECONDS)

internal fun ZipArchiveOutputStream.entry(name: String, file: Path, unixMode: Int = -1) {
  val entry = zipArchiveEntry(name)
  if (unixMode != -1) {
    entry.unixMode = unixMode
  }
  writeFileEntry(file = file, entry = entry, out = this)
}

internal fun ZipArchiveOutputStream.entry(name: String, data: ByteArray) {
  val entry = zipArchiveEntry(name)
  entry.size = data.size.toLong()
  entry.lastModifiedTime = buildTime
  putArchiveEntry(entry)
  write(data)
  closeArchiveEntry()
}

private fun assertRelativePathIsCorrectForPackaging(relativeName: String) {
  if (relativeName.isEmpty()) {
    throw IllegalArgumentException("relativeName must not be empty")
  }

  if (relativeName.startsWith('/')) {
    throw IllegalArgumentException("relativeName must not be an absolute path: $relativeName")
  }

  if (relativeName.contains('\\')) {
    throw IllegalArgumentException("relativeName must not contain backslash '\\': $relativeName")
  }

  for (component in relativeName.split('/')) {
    if (!component.all { it.code in 32..127 }) {
      throw IllegalArgumentException("relativeName must contain only ASCII (32 <= c <= 127) chars: $relativeName")
    }

    if (component.endsWith('.')) {
      throw IllegalArgumentException("path component '$component' must not end with dot (.), it fails under Windows: $relativeName")
    }

    if (component.endsWith(' ')) {
      throw IllegalArgumentException("path component '$component' must not end with space, it fails under Windows: $relativeName")
    }

    // Windows is the most restrictive
    if (!PathUtilRt.isValidFileName(component, PathUtilRt.Platform.WINDOWS, true, null)) {
      throw IllegalArgumentException("path component '$component' is not valid for Windows: $relativeName")
    }
  }
}

private fun writeFileEntry(file: Path, entry: ZipArchiveEntry, out: ZipArchiveOutputStream) {
  entry.lastModifiedTime = buildTime
  out.putArchiveEntry(entry)
  Files.copy(file, out)
  out.closeArchiveEntry()
}

private fun zipArchiveEntry(name: String): ZipArchiveEntry {
  assertRelativePathIsCorrectForPackaging(name)
  return ZipArchiveEntry(name)
}

internal class NoDuplicateZipArchiveOutputStream(channel: SeekableByteChannel, compress: Boolean) : ZipArchiveOutputStream(channel) {
  private val existingNames = HashSet<String>()

  init {
    if (!compress) {
      setMethod(ZipEntry.STORED)
    }
  }

  override fun putArchiveEntry(archiveEntry: ArchiveEntry) {
    val entryName = archiveEntry.name
    assertRelativePathIsCorrectForPackaging(entryName)
    if (!existingNames.add(entryName)) {
      throw IllegalStateException("File $entryName already exists")
    }
    super.putArchiveEntry(archiveEntry)
  }
}