// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.tasks

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.jetbrains.intellij.build.io.writeNewFile
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.*
import java.util.zip.ZipEntry

@Suppress("unused")
fun crossPlatformZip(winDistDir: Path,
                     linuxDistDir: Path,
                     macDistDir: Path,
                     targetFile: Path,
                     executableName: String,
                     productJson: ByteArray,
                     extraExecutables: List<String>,
                     distAllDir: Path) {
  writeNewFile(targetFile) { outFileChannel ->
    ZipArchiveOutputStream(outFileChannel).use { out ->
      out.setUseZip64(Zip64Mode.Never)

      out.entryToDir(winDistDir.resolve("bin/idea.properties"), "bin/win")
      out.entryToDir(linuxDistDir.resolve("bin/idea.properties"), "bin/linux")
      out.entryToDir(macDistDir.resolve("bin/idea.properties"), "linux/mac")

      out.entryToDir(macDistDir.resolve("bin/${executableName}.vmoptions"), "bin/mac")
      out.entry("bin/mac/${executableName}64.vmoptions", macDistDir.resolve("bin/${executableName}.vmoptions"))

      Files.newDirectoryStream(winDistDir.resolve("bin")).use {
        for (file in it) {
          val path = file.toString()
          if (path.endsWith(".exe.vmoptions")) {
            out.entryToDir(file, "bin/win")
          }
          else {
            val fileName = file.fileName.toString()
            @Suppress("SpellCheckingInspection")
            if (fileName.startsWith("fsnotifier") && fileName.endsWith(".exe")) {
              out.entry("bin/win/$fileName", file)
            }
          }
        }
      }

      Files.newDirectoryStream(linuxDistDir.resolve("bin")).use {
        for (file in it) {
          val path = file.toString()
          if (path.endsWith(".vmoptions")) {
            out.entryToDir(file, "bin/linux")
          }
          else if (path.endsWith(".sh") || path.endsWith(".py")) {
            out.entry("bin/${file.fileName}", file, unixMode = 509)
          }
          else {
            val fileName = file.fileName.toString()
            @Suppress("SpellCheckingInspection")
            if (fileName.startsWith("fsnotifier")) {
              out.entry("bin/linux/$fileName", file, unixMode = 509)
            }
          }
        }
      }

      Files.newDirectoryStream(macDistDir.resolve("bin")).use {
        for (file in it) {
          if (file.toString().endsWith(".jnilib")) {
            out.entry("bin/mac/${file.fileName.toString().removeSuffix(".jnilib")}.dylib", file)
          }
          else {
            val fileName = file.fileName.toString()
            @Suppress("SpellCheckingInspection")
            if (fileName.startsWith("restarter") || fileName.startsWith("printenv")) {
              out.entry("bin/$fileName", file, unixMode = 509)
            }
            else if (fileName.startsWith("fsnotifier")) {
              out.entry("bin/mac/$fileName", file, unixMode = 509)
            }
          }
        }
      }

      out.entry("product-info.json", productJson)

      for (extraExecutable in extraExecutables) {
        val file = distAllDir.resolve(extraExecutable)
        if (Files.exists(file)) {
          out.entry(extraExecutable, file, unixMode = 509)
        }
        else {
          Span.current().addEvent("extra executable doesn't exist",
                                  Attributes.of(AttributeKey.stringKey("extraExecutable"), extraExecutable))
        }
      }
    }
  }
}

typealias EntryCustomizer = (entry: ZipArchiveEntry, file: Path, relativeFile: Path) -> Unit

private val fsUnixMode: EntryCustomizer = { entry, file, _ ->
  entry.unixMode = Files.readAttributes(file, "unix:mode").get("mode") as Int
}

internal fun ZipArchiveOutputStream.dir(startDir: Path,
                                        prefix: String,
                                        fileFilter: ((sourceFile: Path, relativeFile: Path) -> Boolean)? = null,
                                        entryCustomizer: EntryCustomizer = fsUnixMode) {
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
        val entry = ZipArchiveEntry(prefix + startDir.relativize(file))
        entry.method = ZipEntry.STORED
        entry.lastModifiedTime = zeroTime
        entry.unixMode = Files.readAttributes(file, "unix:mode", LinkOption.NOFOLLOW_LINKS).get("mode") as Int
        val data = (prefix + startDir.relativize(Files.readSymbolicLink(file))).toByteArray()
        entry.size = data.size.toLong()
        putArchiveEntry(entry)
        write(data)
        closeArchiveEntry()
      }
      else {
        assert(attributes.isRegularFile)

        val relativeFile = startDir.relativize(file)
        if (fileFilter != null && !fileFilter(file, relativeFile)) {
          continue
        }

        val entry = ZipArchiveEntry(prefix + relativeFile)
        entry.size = attributes.size()
        entryCustomizer(entry, file, relativeFile)
        writeFileEntry(file, entry, this)
      }
    }
  }
}

private fun ZipArchiveOutputStream.entryToDir(file: Path, zipPath: String) {
  entry("$zipPath/${file.fileName}", file)
}

private val zeroTime = FileTime.fromMillis(0)

internal fun ZipArchiveOutputStream.entry(name: String, file: Path, unixMode: Int = -1) {
  val entry = ZipArchiveEntry(name)
  if (unixMode != -1) {
    entry.unixMode = unixMode
  }
  writeFileEntry(file, entry, this)
}

internal fun ZipArchiveOutputStream.entry(name: String, data: ByteArray) {
  val entry = ZipArchiveEntry(name)
  entry.size = data.size.toLong()
  entry.lastModifiedTime = zeroTime
  putArchiveEntry(entry)
  write(data)
  closeArchiveEntry()
}

private fun writeFileEntry(file: Path, entry: ZipArchiveEntry, out: ZipArchiveOutputStream) {
  entry.lastModifiedTime = zeroTime
  out.putArchiveEntry(entry)
  Files.copy(file, out)
  out.closeArchiveEntry()
}