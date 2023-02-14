// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import org.jetbrains.xxh3.Xx3UnencodedString

class PackageIndexBuilder {
  @JvmField
  internal val classPackageHashSet = LongOpenHashSet()
  @JvmField
  internal val resourcePackageHashSet = LongOpenHashSet()

  private val dirsToRegister = HashSet<String>()
  private var wasWritten = false

  fun addFile(name: String, addClassDir: Boolean = false) {
    val i = name.lastIndexOf('/')
    val packageNameHash = if (i == -1) 0 else Xx3UnencodedString.hashUnencodedStringRange(name, 0, i)
    if (name.endsWith(".class")) {
      classPackageHashSet.add(packageNameHash)
      if (addClassDir) {
        computeDirsToAddToIndex(name)
      }
    }
    else {
      resourcePackageHashSet.add(packageNameHash)
      computeDirsToAddToIndex(name)
    }
  }

  fun writePackageIndex(zipCreator: ZipFileWriter, addDirEntriesMode: AddDirEntriesMode = AddDirEntriesMode.NONE) {
    assert(!wasWritten)
    wasWritten = true

    if (!resourcePackageHashSet.isEmpty()) {
      // add empty package if top-level directory will be requested
      resourcePackageHashSet.add(0)
    }

    val classPackages = classPackageHashSet.toLongArray()
    val resourcePackages = resourcePackageHashSet.toLongArray()

    // same content for same data
    classPackages.sort()
    resourcePackages.sort()
    val sortedDirsToRegister = dirsToRegister.sorted()

    val stream = zipCreator.resultStream
    if (addDirEntriesMode == AddDirEntriesMode.NONE) {
      stream.addDirsToIndex(sortedDirsToRegister)
    }
    else {
      for (dir in sortedDirsToRegister) {
        stream.addDirEntry(dir)
      }
    }

    stream.setPackageIndex(classPackages = classPackages, resourcePackages = resourcePackages)
  }

  // add to index only directories where some non-class files are located (as it can be requested in runtime, e.g. stubs, fileTemplates)
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
      resourcePackageHashSet.add(Xx3UnencodedString.hashUnencodedString(dirName))

      slashIndex = dirName.lastIndexOf('/')
      if (slashIndex == -1) {
        break
      }

      dirName = name.substring(0, slashIndex)
    }
  }
}