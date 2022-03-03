// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import org.jetbrains.intellij.build.tasks.PackageIndexBuilder
import java.nio.file.*
import java.util.*

// symlinks not supported but can be easily implemented - see CollectingVisitor.visitFile
fun zip(targetFile: Path, dirs: Map<Path, String>, compress: Boolean, addDirEntries: Boolean = false) {
  // note - dirs contain duplicated directories (you cannot simply add directory entry on visit - uniqueness must be preserved)
  // anyway, directory entry are not added
  writeNewZip(targetFile, compress) { zipCreator ->
    val fileAdded: ((String) -> Boolean)?
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
        true
      }
    }
    else {
      fileAdded = null
      dirNameSetToAdd = emptySet()
    }

    val archiver = ZipArchiver(zipCreator, fileAdded)
    for ((dir, prefix) in dirs.entries) {
      val normalizedDir = dir.toAbsolutePath().normalize()
      archiver.setRootDir(normalizedDir, prefix)
      compressDir(normalizedDir, archiver, excludes = null)
    }

    if (dirNameSetToAdd.isNotEmpty()) {
      addDirForResourceFiles(zipCreator, dirNameSetToAdd)
    }
  }
}

private fun addDirForResourceFiles(out: ZipFileWriter, dirNameSetToAdd: Set<String>) {
  for (dir in dirNameSetToAdd) {
    out.dir(dir)
  }
}

internal class ZipArchiver(private val zipCreator: ZipFileWriter, val fileAdded: ((String) -> Boolean)? = null) : AutoCloseable {
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

  fun addFile(file: Path) {
    val name = archivePrefix + file.toString().substring(localPrefixLength).replace('\\', '/')
    if (fileAdded == null || fileAdded.invoke(name)) {
      zipCreator.file(name, file)
    }
  }

  override fun close() {
    zipCreator.close()
  }
}

internal fun compressDir(startDir: Path, archiver: ZipArchiver, excludes: List<PathMatcher>? = emptyList()) {
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
      if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
        dirCandidates.add(file)
      }
      else {
        archiver.addFile(file)
      }
    }
  }
}

internal fun copyZipRaw(sourceFile: Path, packageIndexBuilder: PackageIndexBuilder, zipCreator: ZipFileWriter) {
  readZipFile(sourceFile) { name, entry ->
    packageIndexBuilder.addFile(name)
    zipCreator.uncompressedData(name, entry.getByteBuffer())
  }
}