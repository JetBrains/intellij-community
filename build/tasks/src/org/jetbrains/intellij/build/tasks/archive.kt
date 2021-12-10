// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.tasks

import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.jetbrains.intellij.build.io.isWindows
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.io.writeNewFile
import org.jetbrains.intellij.build.io.writeNewZip
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.*
import java.util.function.BiConsumer
import java.util.zip.ZipEntry

// 0100000
private const val fileFlag = 32768
// 0755
const val executableFileUnixMode = fileFlag or 493
// 0644
const val regularFileUnixMode = fileFlag or 420

internal fun packInternalUtilities(outFile: Path, files: List<Path>) {
  writeNewZip(outFile, compress = true) { writer ->
    for (file in files) {
      writer.file(file.fileName.toString(), file)
    }

    readZipFile(files.last()) { name, entry ->
      if (name.endsWith(".xml")) {
        writer.uncompressedData(name, entry.getByteBuffer())
      }
    }
  }
}

@Suppress("unused")
fun crossPlatformZip(macDistDir: Path,
                     linuxDistDir: Path,
                     winDistDir: Path,
                     targetFile: Path,
                     executableName: String,
                     productJson: ByteArray,
                     macExtraExecutables: List<String>,
                     linuxExtraExecutables: List<String>,
                     distFiles: Collection<Map.Entry<Path, String>>,
                     extraFiles: Map<String, Path>,
                     distAllDir: Path) {
  writeNewFile(targetFile) { outFileChannel ->
    ZipArchiveOutputStream(outFileChannel).use { out ->
      out.setUseZip64(Zip64Mode.Never)

      out.entryToDir(winDistDir.resolve("bin/idea.properties"), "bin/win")
      out.entryToDir(linuxDistDir.resolve("bin/idea.properties"), "bin/linux")
      out.entryToDir(macDistDir.resolve("bin/idea.properties"), "bin/mac")

      out.entryToDir(macDistDir.resolve("bin/${executableName}.vmoptions"), "bin/mac")
      out.entry("bin/mac/${executableName}64.vmoptions", macDistDir.resolve("bin/${executableName}.vmoptions"))

      extraFiles.forEach(BiConsumer { p, f ->
        out.entry(p, f)
      })

      out.entry("product-info.json", productJson)

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
            out.entry("bin/${file.fileName}", file, unixMode = executableFileUnixMode)
          }
          else {
            val fileName = file.fileName.toString()
            @Suppress("SpellCheckingInspection")
            if (fileName.startsWith("fsnotifier")) {
              out.entry("bin/linux/$fileName", file, unixMode = executableFileUnixMode)
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
              out.entry("bin/$fileName", file, unixMode = executableFileUnixMode)
            }
            else if (fileName.startsWith("fsnotifier")) {
              out.entry("bin/mac/$fileName", file, unixMode = executableFileUnixMode)
            }
          }
        }
      }

      val extraExecutablesSet = java.util.Set.copyOf(macExtraExecutables + linuxExtraExecutables)
      val entryCustomizer: EntryCustomizer = { entry, _, relativeFile ->
        if (extraExecutablesSet.contains(relativeFile.toString())) {
          entry.unixMode = executableFileUnixMode
        }
      }

      out.dir(startDir = distAllDir, prefix = "", fileFilter = { _, relativeFile ->
        relativeFile.toString() != "bin/idea.properties"
      }, entryCustomizer = entryCustomizer)

      out.dir(startDir = macDistDir, prefix = "", fileFilter = { _, relativeFile ->
        val p = relativeFile.toString()
        @Suppress("SpellCheckingInspection")
        !p.startsWith("bin/fsnotifier") &&
        !p.startsWith("bin/restarter") &&
        !p.startsWith("bin/printenv") &&
        p != "bin/idea.properties" &&
        !(p.startsWith("bin/") && (p.endsWith(".sh") || p.endsWith(".vmoptions"))) &&
        // do not copy common files
        !Files.exists(linuxDistDir.resolve(p))
      }, entryCustomizer = entryCustomizer)

      out.dir(startDir = linuxDistDir, prefix = "", fileFilter = { _, relativeFile ->
        val p = relativeFile.toString()
        @Suppress("SpellCheckingInspection")
        !p.startsWith("bin/fsnotifier") &&
        !p.startsWith("bin/printenv") &&
        !p.startsWith("help/") &&
        p != "bin/idea.properties" &&
        !(p.startsWith("bin/") && (p.endsWith(".sh") || p.endsWith(".vmoptions") || p.endsWith(".py")))
      }, entryCustomizer = entryCustomizer)

      val winExcludes = distFiles.mapTo(HashSet(distFiles.size)) { "${it.value}/${it.key.fileName}" }
      out.dir(startDir = winDistDir, prefix = "", fileFilter = { _, relativeFile ->
        val p = relativeFile.toString()
        @Suppress("SpellCheckingInspection")
        !p.startsWith("bin/fsnotifier") &&
        !p.startsWith("bin/printenv") &&
        !p.startsWith("help/") &&
        p != "bin/idea.properties" &&
        p != "build.txt" &&
        !(p.startsWith("bin/") && p.endsWith(".exe.vmoptions")) &&
        !(p.startsWith("bin/$executableName") && p.endsWith(".exe")) &&
        !winExcludes.contains(p)
      }, entryCustomizer = entryCustomizer)
    }
  }
}

fun consumeDataByPrefix(file: Path, prefixWithEndingSlash: String, consumer: BiConsumer<String, ByteArray>) {
  readZipFile(file) { name, entry ->
    if (name.startsWith(prefixWithEndingSlash)) {
      consumer.accept(name, entry.getData())
    }
  }
}

typealias EntryCustomizer = (entry: ZipArchiveEntry, file: Path, relativeFile: Path) -> Unit

private val fsUnixMode: EntryCustomizer = { entry, file, relativeFile ->
  if (!relativeFile.toString().endsWith(".jar") && Files.isExecutable(file)) {
    entry.unixMode = executableFileUnixMode
  }
}

internal fun ZipArchiveOutputStream.dir(startDir: Path,
                                        prefix: String,
                                        fileFilter: ((sourceFile: Path, relativeFile: Path) -> Boolean)? = null,
                                        entryCustomizer: EntryCustomizer = if (isWindows) { _, _, _ ->  } else fsUnixMode) {
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