// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.logsUploader

import com.intellij.openapi.diagnostic.thisLogger
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun ZipOutputStream.addFolder(folder: File, entryName: String) {
  folder.listFiles()?.forEach { file ->
    val childEntryName = if (entryName.isEmpty()) {
      file.name
    }
    else "$entryName/${file.name}"
    if (file.isDirectory) {
      addFolder(file, childEntryName)
    }
    else {
      addFile(childEntryName, file)
    }
  }
}

fun ZipOutputStream.addFile(entryName: String, file: File) {
  val zipEntry = ZipEntry(entryName)
  try {
    putNextEntry(zipEntry)
    FileInputStream(file).use { fileInputStream ->
      fileInputStream.copyTo(this)
    }
  }
  catch (ex: Exception) {
    thisLogger().error("Filed to add file to stream", ex)
  }
  finally {
    closeEntry()
  }
}

fun ZipOutputStream.addFile(entryName: String, byteArray: ByteArray) {
  val zipEntry = ZipEntry(entryName)
  try {
    putNextEntry(zipEntry)
    write(byteArray)
  }
  catch (ex: Exception) {
    thisLogger().error("Filed to add file to stream", ex)
  }
  finally {
    closeEntry()
  }
}