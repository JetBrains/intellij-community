// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import com.intellij.util.lang.Xxh3
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

@Throws(IOException::class)
fun getFileHash(file: File): Long {
  var fileHash: Long
  FileInputStream(file).use { fis ->
    fileHash = Xxh3.hash(fis, Math.toIntExact(file.length()))
  }
  return fileHash
}

@Throws(IOException::class)
fun getFileHash(file: Path): Long {
  var fileHash: Long
  Files.newInputStream(file).use { fis ->
    fileHash = Xxh3.hash(fis, Math.toIntExact(Files.size(file)))
  }
  return fileHash
}
