// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.tasks

import com.intellij.util.lang.ImmutableZipFile
import org.jetbrains.intellij.build.io.RW_CREATE_NEW
import org.jetbrains.intellij.build.io.ZipFileWriter
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path

fun mergeJars(targetFile: Path, files: List<Path>): Map<Path, Int> {
  Files.createDirectories(targetFile.parent)
  FileChannel.open(targetFile, RW_CREATE_NEW).use { outChannel ->
    val packageIndexBuilder = PackageIndexBuilder()

    val zipCreator = ZipFileWriter(outChannel, deflater = null)
    val sizes = HashMap<Path, Int>()
    for (file in files) {
      ImmutableZipFile.load(file).use { zipFile ->
        @Suppress("SpellCheckingInspection")
        val entries = zipFile.entries.filter {
          val name = it.name
          !name.endsWith(".kotlin_metadata") &&
          name != "META-INF/MANIFEST.MF" && name != PACKAGE_INDEX_NAME &&
          name != "license" && !name.startsWith("license/") &&
          name != "META-INF/services/javax.xml.parsers.SAXParserFactory" &&
          name != "META-INF/services/javax.xml.stream.XMLEventFactory" &&
          name != "META-INF/services/javax.xml.parsers.DocumentBuilderFactory" &&
          name != "META-INF/services/javax.xml.datatype.DatatypeFactory" &&
          name != "native-image" && !name.startsWith("native-image/") &&
          name != "native" && !name.startsWith("native/") &&
          name != "licenses" && !name.startsWith("licenses/") &&
          name != ".gitkeep" &&
          name != "META-INF/README.md" &&
          name != "META-INF/NOTICE" &&
          name != "META-INF/NOTICE.txt" &&
          name != "LICENSE" &&
          name != "LICENSE.md" &&
          name != "module-info.class" &&
          name != "META-INF/maven" &&
          !name.startsWith("META-INF/maven/") &&
          !name.startsWith("META-INF/INDEX.LIST") &&
          (!name.startsWith("META-INF/") || (!name.endsWith(".DSA") && !name.endsWith(".SF") && !name.endsWith(".RSA")))
        }
        writeEntries(entries, zipCreator, zipFile)
        packageIndexBuilder.add(entries)
        
        sizes.put(file, entries.asSequence().map { it.size }.sum())
      }
    }
    writeDirs(packageIndexBuilder.dirsToCreate, zipCreator)
    packageIndexBuilder.writePackageIndex(zipCreator)
    zipCreator.finish()
    return sizes
  }
}