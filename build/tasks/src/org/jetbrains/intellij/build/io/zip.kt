// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import java.nio.channels.FileChannel
import java.nio.file.*
import java.util.*
import java.util.zip.Deflater

private val W_OVERWRITE = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)

enum class AddDirEntriesMode {
  NONE,
  RESOURCE_ONLY,
  ALL
}

fun zipWithCompression(
  targetFile: Path,
  dirs: Map<Path, String>,
  compressionLevel: Int = Deflater.DEFAULT_COMPRESSION,
  addDirEntriesMode: AddDirEntriesMode = AddDirEntriesMode.NONE,
  overwrite: Boolean = false,
  fileFilter: ((name: String) -> Boolean)? = null,
) {
  Files.createDirectories(targetFile.parent)
  ZipFileWriter(
    channel = FileChannel.open(targetFile, if (overwrite) W_OVERWRITE else W_CREATE_NEW),
    deflater = if (compressionLevel == Deflater.NO_COMPRESSION) null else Deflater(compressionLevel, true),
  ).use { zipFileWriter ->
    if (addDirEntriesMode == AddDirEntriesMode.NONE) {
      doArchive(
        zipFileWriter = zipFileWriter,
        fileAdded = if (fileFilter == null) null else { name, _ -> fileFilter(name) },
        dirs = dirs,
        indexWriter = null,
      )
    }
    else {
      val dirNameSetToAdd = LinkedHashSet<String>()
      val fileAdded = { name: String, _: Path ->
        if (fileFilter != null && !fileFilter(name)) {
          false
        }
        else {
          if (addDirEntriesMode == AddDirEntriesMode.ALL ||
              (addDirEntriesMode == AddDirEntriesMode.RESOURCE_ONLY &&
               !name.endsWith(".class") && !name.endsWith("/package.html") && name != "META-INF/MANIFEST.MF")) {
            addDirWithParents(name, dirNameSetToAdd)
          }
          true
        }
      }

      doArchive(zipFileWriter = zipFileWriter, fileAdded = fileAdded, dirs = dirs, indexWriter = null)
      for (dir in dirNameSetToAdd) {
        zipFileWriter.dir(name = dir, indexWriter = null)
      }
    }
  }
}

// symlinks are not supported but can be easily implemented - see CollectingVisitor.visitFile
fun zip(
  targetFile: Path,
  dirs: Map<Path, String>,
  addDirEntriesMode: AddDirEntriesMode = AddDirEntriesMode.RESOURCE_ONLY,
  overwrite: Boolean = false,
  fileFilter: ((name: String) -> Boolean)? = null,
) {
  Files.createDirectories(targetFile.parent)
  ZipFileWriter(channel = FileChannel.open(targetFile, if (overwrite) W_OVERWRITE else W_CREATE_NEW)).use { zipFileWriter ->
    if (addDirEntriesMode == AddDirEntriesMode.NONE) {
      doArchive(
        zipFileWriter = zipFileWriter,
        fileAdded = if (fileFilter == null) null else { name, _ -> fileFilter(name) },
        dirs = dirs,
        indexWriter = null,
      )
    }
    else {
      val packageIndexBuilder = PackageIndexBuilder()
      doArchive(
        zipFileWriter = zipFileWriter,
        fileAdded = { name, _ ->
          if (fileFilter != null && !fileFilter(name)) {
            false
          }
          else {
            packageIndexBuilder.addFile(name, addClassDir = addDirEntriesMode == AddDirEntriesMode.ALL)
            true
          }
        },
        dirs = dirs,
        indexWriter = packageIndexBuilder.indexWriter,
      )
      packageIndexBuilder.writePackageIndex(zipFileWriter, addDirEntriesMode = addDirEntriesMode)
    }
  }
}

private fun doArchive(zipFileWriter: ZipFileWriter, fileAdded: ((String, Path) -> Boolean)?, dirs: Map<Path, String>, indexWriter: IkvIndexBuilder?) {
  val archiver = ZipArchiver(zipFileWriter, fileAdded)
  for ((dir, prefix) in dirs.entries) {
    val normalizedDir = dir.toAbsolutePath().normalize()
    archiver.setRootDir(normalizedDir, prefix)
    archiveDir(normalizedDir, archiver, excludes = null, indexWriter = indexWriter)
  }
}

private fun addDirWithParents(name: String, dirNameSetToAdd: MutableSet<String>) {
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

class ZipArchiver(private val zipCreator: ZipFileWriter, @JvmField val fileAdded: ((String, Path) -> Boolean)? = null) : AutoCloseable {
  private var localPrefixLength = -1
  private var archivePrefix = ""

  // rootDir must be absolute and normalized
  fun setRootDir(rootDir: Path, prefix: String = "") {
    archivePrefix = when {
      prefix.isNotEmpty() && !prefix.endsWith('/') -> "$prefix/"
      prefix == "/" -> ""
      else -> prefix
    }

    localPrefixLength = rootDir.toString().length + 1
  }

  fun addFile(file: Path, indexWriter: IkvIndexBuilder?) {
    val name = archivePrefix + file.toString().substring(localPrefixLength).replace('\\', '/')
    if (fileAdded == null || fileAdded.invoke(name, file)) {
      zipCreator.file(name, file, indexWriter)
    }
  }

  override fun close() {
    zipCreator.close()
  }
}

fun archiveDir(startDir: Path, archiver: ZipArchiver, indexWriter: IkvIndexBuilder?, excludes: List<PathMatcher>? = emptyList()) {
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
      if (excludes == null) {
        tempList.addAll(it)
      }
      else {
        l@ for (child in it) {
          val relative = startDir.relativize(child)
          for (exclude in excludes) {
            if (exclude.matches(relative)) {
              continue@l
            }
          }
          tempList.add(child)
        }
      }
    }

    tempList.sort()
    for (file in tempList) {
      if (Files.isDirectory(file)) {
        dirCandidates.add(file)
      }
      else {
        archiver.addFile(file, indexWriter)
      }
    }
  }
}