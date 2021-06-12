// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.tasks

import com.intellij.util.lang.ImmutableZipFile
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
        val entries = zipFile.entries.asSequence().filter {
          it.name != "META-INF/MANIFEST.MF" && it.name != PACKAGE_INDEX_NAME &&
          it.name != "license" && !it.name.startsWith("license/") &&
          it.name != "META-INF/services/javax.xml.parsers.SAXParserFactory" &&
          it.name != "META-INF/services/javax.xml.stream.XMLEventFactory" &&
          it.name != "META-INF/services/javax.xml.parsers.DocumentBuilderFactory" &&
          it.name != "META-INF/services/javax.xml.datatype.DatatypeFactory" &&
          it.name != "native-image" && !it.name.startsWith("native-image/") &&
          it.name != "native" && !it.name.startsWith("native/") &&
          it.name != "licenses" && !it.name.startsWith("licenses/") &&
          it.name != ".gitkeep" &&
          it.name != "LICENSE" &&
          it.name != "LICENSE.md" &&
          it.name != "module-info.class" &&
          it.name != "META-INF/maven" && !it.name.startsWith("META-INF/maven/")
        }.toList()
        writeEntries(entries, zipCreator, zipFile)
        packageIndexBuilder.add(entries)
      }
    }
    writeDirs(packageIndexBuilder.dirsToCreate, zipCreator)
    packageIndexBuilder.writePackageIndex(zipCreator)
    zipCreator.finish()
  }
}