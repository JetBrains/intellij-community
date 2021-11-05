// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.tasks

import com.intellij.util.io.Murmur3_32Hash
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.jetbrains.intellij.build.io.ZipFileWriter

internal class PackageIndexBuilder {
  val classPackageHashSet = IntOpenHashSet()
  val resourcePackageHashSet = IntOpenHashSet()

  private val dirsToCreate = HashSet<String>()

  private var wasWritten = false

  // @TestOnly
  @Suppress("FunctionName")
  fun _getDirsToCreate(): Set<String> = dirsToCreate

  fun addFile(name: String) {
    if (name.endsWith(".class")) {
      classPackageHashSet.add(getPackageNameHash(name))
    }
    else {
      resourcePackageHashSet.add(getPackageNameHash(name))
      computeDirsToCreate(name)
    }
  }

  fun writeDirs(zipCreator: ZipFileWriter) {
    if (dirsToCreate.isEmpty()) {
      return
    }

    val list = dirsToCreate.toMutableList()
    list.sort()
    for (name in list) {
      // name in our ImmutableZipEntry doesn't have ending slash
      zipCreator.dir(name)
    }
  }

  fun writePackageIndex(zipCreator: ZipFileWriter) {
    assert(!wasWritten)
    wasWritten = true

    if (!resourcePackageHashSet.isEmpty()) {
      // add empty package if top-level directory will be requested
      resourcePackageHashSet.add(0)
    }

    zipCreator.uncompressedData(PACKAGE_INDEX_NAME,
                                      (2 * Int.SIZE_BYTES) + ((classPackageHashSet.size + resourcePackageHashSet.size) * Int.SIZE_BYTES)) {
      val classPackages = classPackageHashSet.toIntArray()
      val resourcePackages = resourcePackageHashSet.toIntArray()
      // same content for same data
      classPackages.sort()
      resourcePackages.sort()
      it.putInt(classPackages.size)
      it.putInt(resourcePackages.size)
      val intBuffer = it.asIntBuffer()
      intBuffer.put(classPackages)
      intBuffer.put(resourcePackages)
      it.position(it.position() + (intBuffer.position() * Int.SIZE_BYTES))
    }
  }

  // leave only directories where some non-class files are located (as it can be requested in runtime, e.g. stubs, fileTemplates)
  private fun computeDirsToCreate(name: String) {
    if (name.endsWith("/package.html") || name == "META-INF/MANIFEST.MF") {
      return
    }

    var slashIndex = name.lastIndexOf('/')
    if (slashIndex == -1) {
      return
    }

    var dirName = name.substring(0, slashIndex)
    while (dirsToCreate.add(dirName)) {
      resourcePackageHashSet.add(Murmur3_32Hash.MURMUR3_32.hashString(dirName, 0, dirName.length))

      slashIndex = dirName.lastIndexOf('/')
      if (slashIndex == -1) {
        break
      }

      dirName = name.substring(0, slashIndex)
    }
  }
}

private fun getPackageNameHash(name: String): Int {
  val i = name.lastIndexOf('/')
  if (i == -1) {
    return 0
  }
  return Murmur3_32Hash.MURMUR3_32.hashString(name, 0, i)
}