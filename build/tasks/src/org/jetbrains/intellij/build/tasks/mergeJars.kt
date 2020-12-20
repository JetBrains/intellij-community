// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.tasks

import com.intellij.util.zip.ImmutableZipFile
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.jetbrains.intellij.build.io.RW_CREATE_NEW
import org.jetbrains.intellij.build.io.ZipFileWriter
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path

fun mergeJars(targetFile: Path, files: List<Path>) {
  Files.createDirectories(targetFile.parent)
  FileChannel.open(targetFile, RW_CREATE_NEW).use { outChannel ->
    val classPackageHashSet = IntOpenHashSet()
    val resourcePackageHashSet = IntOpenHashSet()

    val zipCreator = ZipFileWriter(outChannel, deflater = null)
    val dirsToCreate = HashSet<String>()
    for (file in files) {
      ImmutableZipFile.load(file).use { zipFile ->
        val entries = zipFile.entries.asSequence().filter { it.name != "META-INF/MANIFEST.MF" }.toList()
        computeDirsToCreate(entries, resourcePackageHashSet, dirsToCreate)
        writeEntries(entries, zipCreator, zipFile, classPackageHashSet, resourcePackageHashSet)
      }
    }
    writeDirs(dirsToCreate, zipCreator)
    writePackageIndex(zipCreator, classPackageHashSet, resourcePackageHashSet)
    zipCreator.finish()
  }
}