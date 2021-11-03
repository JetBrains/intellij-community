// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import org.apache.tools.tar.TarEntry
import org.apache.tools.tar.TarInputStream
import org.jetbrains.annotations.Nullable

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

final class ArchiveUtils {
  static boolean archiveContainsEntry(String archivePath, String entryPath) {
    File archiveFile = new File(archivePath)
    String fileName = archiveFile.name
    if (isZipFile(fileName)) {
      return new ZipFile(archiveFile).withCloseable {
        it.getEntry(entryPath) != null
      }
    }

    if (fileName.endsWith(".tar.gz")) {
      return archiveFile.withInputStream {
        TarInputStream inputStream = new TarInputStream(new GZIPInputStream(it))
        TarEntry entry
        String altEntryPath = "./$entryPath"
        while (null != (entry = inputStream.nextEntry)) {
          if (entry.name == entryPath || entry.name == altEntryPath) {
            return true
          }
        }
        return false
      }
    }
    return false
  }

  static @Nullable String loadEntry(Path archiveFile, String entryPath) {
    String fileName = archiveFile.fileName.toString()
    if (isZipFile(fileName)) {
      ZipFile zipFile = new ZipFile(archiveFile.toFile())
      try {
        def zipEntry = zipFile.getEntry(entryPath)
        if (zipEntry == null) return null
        InputStream inputStream = zipFile.getInputStream(zipEntry)
        return inputStream == null ? null : new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
      }
      finally {
        zipFile.close()
      }
    }
    else if (fileName.endsWith(".tar.gz")) {
      TarInputStream inputStream = new TarInputStream(new GZIPInputStream(Files.newInputStream(archiveFile)))
      try {
        TarEntry entry
        String altEntryPath = "./$entryPath"
        while (null != (entry = inputStream.getNextEntry())) {
          if (entry.name == entryPath || entry.name == altEntryPath) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
          }
        }
      }
      finally {
        inputStream.close()
      }
    }
    return null
  }

  private static boolean isZipFile(String fileName) {
    fileName.endsWith(".zip") || fileName.endsWith(".jar")
  }
}
