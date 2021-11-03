// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.tasks

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.jetbrains.intellij.build.io.writeNewFile
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.*

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
      addPlainFileToDir(winDistDir.resolve("bin/idea.properties"), "bin/win", out)
      addPlainFileToDir(linuxDistDir.resolve("bin/idea.properties"), "bin/linux", out)
      addPlainFileToDir(macDistDir.resolve("bin/idea.properties"), "linux/mac", out)

      addPlainFileToDir(macDistDir.resolve("bin/${executableName}.vmoptions"), "bin/mac", out)
      addPlainFile(macDistDir.resolve("bin/${executableName}.vmoptions"), "bin/mac/${executableName}64.vmoptions", out)

      Files.newDirectoryStream(winDistDir.resolve("bin")).use {
        for (file in it) {
          val path = file.toString()
          if (path.endsWith(".exe.vmoptions")) {
            addPlainFileToDir(file, "bin/win", out)
          }
          else {
            val fileName = file.fileName.toString()
            @Suppress("SpellCheckingInspection")
            if (fileName.startsWith("fsnotifier") && fileName.endsWith(".exe")) {
              addPlainFile(file, "bin/win/$fileName", out)
            }
          }
        }
      }

      Files.newDirectoryStream(linuxDistDir.resolve("bin")).use {
        for (file in it) {
          val path = file.toString()
          if (path.endsWith(".vmoptions")) {
            addPlainFileToDir(file, "bin/linux", out)
          }
          else if (path.endsWith(".sh") || path.endsWith(".py")) {
            addPlainFile(file, "bin/${file.fileName}", out, unixMode = 509)
          }
          else {
            val fileName = file.fileName.toString()
            @Suppress("SpellCheckingInspection")
            if (fileName.startsWith("fsnotifier")) {
              addPlainFile(file, "bin/linux/$fileName", out, unixMode = 509)
            }
          }
        }
      }

      Files.newDirectoryStream(macDistDir.resolve("bin")).use {
        for (file in it) {
          if (file.toString().endsWith(".jnilib")) {
            addPlainFile(file, "bin/mac/${file.fileName.toString().removeSuffix(".jnilib")}.dylib", out)
          }
          else {
            val fileName = file.fileName.toString()
            @Suppress("SpellCheckingInspection")
            if (fileName.startsWith("restarter") || fileName.startsWith("printenv")) {
              addPlainFile(file, "bin/$fileName", out, unixMode = 509)
            }
            else if (fileName.startsWith("fsnotifier")) {
              addPlainFile(file, "bin/mac/$fileName", out, unixMode = 509)
            }
          }
        }
      }

      addEntry("product-info.json", productJson, out)

      for (extraExecutable in extraExecutables) {
        try {
          addPlainFile(distAllDir.resolve(extraExecutable), extraExecutable, out, unixMode = 509)
        }
        catch (ignore: NoSuchFileException) {
        }
      }

      //addDirToZip(winDistDir, out, "", setUnixMode = false)
    }
  }
}

@Suppress("SameParameterValue")
private fun addEntry(name: String, data: ByteArray, out: ZipArchiveOutputStream) {
  val entry = ZipArchiveEntry(name)
  out.putArchiveEntry(entry)
  out.write(data)
  out.closeArchiveEntry()
}

typealias EntryCustomizer = (entry: ZipArchiveEntry, file: Path, relativeFile: Path) -> Unit

private val fsUnixMode: EntryCustomizer = { entry, file, _ ->
  entry.unixMode = Files.readAttributes(file, "unix:mode").get("mode") as Int
}

internal fun ZipArchiveOutputStream.dir(startDir: Path,
                                        prefix: String,
                                        fileFilter: ((relativeFile: Path) -> Boolean)? = null,
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
        putArchiveEntry(entry)
        write((prefix + startDir.relativize(Files.readSymbolicLink(file))).toByteArray())
        closeArchiveEntry()
      }
      else {
        assert(attributes.isRegularFile)

        val relativeFile = startDir.relativize(file)

        if (fileFilter != null && !fileFilter(relativeFile)) {
          continue
        }

        val entry = ZipArchiveEntry(prefix + relativeFile)
        entry.size = attributes.size()
        putArchiveEntry(entry)
        entryCustomizer(entry, file, relativeFile)
        Files.copy(file, this)
        closeArchiveEntry()
      }
    }
  }
}

private fun addPlainFileToDir(file: Path, zipPath: String, out: ZipArchiveOutputStream) {
  addPlainFile(file, "$zipPath/${file.fileName}", out)
}

private fun addPlainFile(file: Path, zipPath: String, out: ZipArchiveOutputStream, unixMode: Int = -1) {
  val entry = ZipArchiveEntry(zipPath)
  entry.size = Files.size(file)
  if (unixMode != -1) {
    entry.unixMode = unixMode
  }
  out.putArchiveEntry(entry)
  Files.copy(file, out)
  out.closeArchiveEntry()
}