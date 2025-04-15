// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import com.dynatrace.hash4j.hashing.Hashing

private val emptyStringArray: Array<String> = emptyArray()

class PackageIndexBuilder(
  private val addDirEntriesMode: AddDirEntriesMode,
  writeCrc32: Boolean = true,
) {
  private val addClassDir: Boolean = addDirEntriesMode == AddDirEntriesMode.ALL
  private val dirsToRegister = HashSet<String>()

  @JvmField
  val indexWriter: IkvIndexBuilder = IkvIndexBuilder(writeCrc32)

  fun addFile(name: String) {
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

  internal inline fun writePackageIndex(writeDirEntries: (Array<String>) -> Unit) {
    if (!indexWriter.resourcePackages.isEmpty()) {
      // add empty package if top-level directory will be requested
      indexWriter.resourcePackages.add(0)
    }

    if (dirsToRegister.isEmpty()) {
      return
    }

    val sortedDirsToRegister = dirsToRegister.toArray(emptyStringArray)
    sortedDirsToRegister.sort()

    if (addDirEntriesMode == AddDirEntriesMode.NONE) {
      for (dirName in sortedDirsToRegister) {
        val nameBytes = dirName.encodeToByteArray()
        indexWriter.add(IkvIndexEntry(longKey = Hashing.xxh3_64().hashBytesToLong(nameBytes), offset = 0, size = -1))
        indexWriter.names.add(nameBytes)
      }
    }
    else {
      writeDirEntries(sortedDirsToRegister)
    }
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