// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.tasks

import com.intellij.util.zip.ImmutableZipFile
import org.jetbrains.intellij.build.io.RW_CREATE_NEW
import org.jetbrains.intellij.build.io.ZipFileWriter
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path

fun mergeJars(targetFile: Path, files: List<Path>) {
  Files.createDirectories(targetFile.parent)
  FileChannel.open(targetFile, RW_CREATE_NEW).use { outChannel ->
    val packageIndexBuilder = PackageIndexBuilder()

    val zipCreator = ZipFileWriter(outChannel, deflater = null)
    for (file in files) {
      ImmutableZipFile.load(file).use { zipFile ->
        val entries = zipFile.entries.asSequence().filter { it.name != "META-INF/MANIFEST.MF" && it.name != PACKAGE_INDEX_NAME }.toList()
        writeEntries(entries, zipCreator, zipFile)
        packageIndexBuilder.add(entries)
      }
    }
    writeDirs(packageIndexBuilder.dirsToCreate, zipCreator)
    packageIndexBuilder.writePackageIndex(zipCreator)
    zipCreator.finish()
  }
}