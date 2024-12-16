// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import com.dynatrace.hash4j.hashing.Hashing

class PackageIndexBuilder {
  private val dirsToRegister = HashSet<String>()

  @JvmField
  val indexWriter: IkvIndexBuilder = IkvIndexBuilder()

  fun addFile(name: String, addClassDir: Boolean = false) {
    val i = name.lastIndexOf('/')
    val packageNameHash = if (i == -1) 0 else Hashing.xxh3_64().hashCharsToLong(name.substring(0, i))
    if (name.endsWith(".class")) {
      indexWriter.classPackages.add(packageNameHash)
      if (addClassDir) {
        computeDirsToAddToIndex(name)
      }
    }
    else {
      indexWriter.resourcePackages.add(packageNameHash)
      computeDirsToAddToIndex(name)
    }
  }

  fun writePackageIndex(writer: ZipFileWriter, addDirEntriesMode: AddDirEntriesMode = AddDirEntriesMode.NONE) {
    writePackageIndex(stream = writer.resultStream, addDirEntriesMode = addDirEntriesMode)
  }

  fun writePackageIndex(stream: ZipArchiveOutputStream, addDirEntriesMode: AddDirEntriesMode = AddDirEntriesMode.NONE) {
    if (!indexWriter.resourcePackages.isEmpty()) {
      // add empty package if top-level directory will be requested
      indexWriter.resourcePackages.add(0)
    }

    val sortedDirsToRegister = dirsToRegister.toTypedArray()
    sortedDirsToRegister.sort()

    if (addDirEntriesMode == AddDirEntriesMode.NONE) {
      for (dirName in sortedDirsToRegister) {
        val nameBytes = dirName.encodeToByteArray()
        indexWriter.add(IkvIndexEntry(longKey = Hashing.xxh3_64().hashBytesToLong(nameBytes), offset = 0, size = -1))
        indexWriter.names.add(nameBytes)
      }
    }
    else {
      for (dir in sortedDirsToRegister) {
        stream.addDirEntry(dir)
      }
    }

    stream.finish(indexWriter)
  }

  // add to index only directories where some non-class files are located (as it can be requested in runtime, e.g., stubs, fileTemplates)
  private fun computeDirsToAddToIndex(name: String) {
    if (name.endsWith("/package.html") || name == "META-INF/MANIFEST.MF") {
      return
    }

    var slashIndex = name.lastIndexOf('/')
    if (slashIndex == -1) {
      return
    }

    var dirName = name.substring(0, slashIndex)
    while (dirsToRegister.add(dirName)) {
      indexWriter.resourcePackages.add(Hashing.xxh3_64().hashCharsToLong(dirName))

      slashIndex = dirName.lastIndexOf('/')
      if (slashIndex == -1) {
        break
      }

      dirName = name.substring(0, slashIndex)
    }
  }
}