// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import com.intellij.util.lang.Xx3UnencodedString
import com.intellij.util.lang.Xxh3

class PackageIndexBuilder {
  private val dirsToRegister = HashSet<String>()

  @JvmField val indexWriter: IkvIndexBuilder = IkvIndexBuilder()

  fun addFile(name: String, addClassDir: Boolean = false) {
    val i = name.lastIndexOf('/')
    val packageNameHash = if (i == -1) 0 else Xx3UnencodedString.hashUnencodedStringRange(name, i)
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

  fun writePackageIndex(zipCreator: ZipFileWriter, addDirEntriesMode: AddDirEntriesMode = AddDirEntriesMode.NONE) {
    @Suppress("UsePropertyAccessSyntax")
    if (!indexWriter.resourcePackages.isEmpty()) {
      // add empty package if top-level directory will be requested
      indexWriter.resourcePackages.add(0)
    }

    val sortedDirsToRegister = dirsToRegister.sorted()

    val stream = zipCreator.resultStream
    if (addDirEntriesMode == AddDirEntriesMode.NONE) {
      for (dirName in sortedDirsToRegister) {
        val nameBytes = dirName.encodeToByteArray()
        indexWriter.add(IkvIndexEntry(longKey = Xxh3.hash(nameBytes), offset = 0, size = -1))
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
      indexWriter.resourcePackages.add(Xx3UnencodedString.hashUnencodedString(dirName))

      slashIndex = dirName.lastIndexOf('/')
      if (slashIndex == -1) {
        break
      }

      dirName = name.substring(0, slashIndex)
    }
  }
}