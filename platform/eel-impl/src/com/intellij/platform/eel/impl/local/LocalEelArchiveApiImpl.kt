// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.platform.eel.EelArchiveApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.util.io.Decompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object LocalEelArchiveApiImpl : EelArchiveApi {

  @Throws(IOException::class)
  override suspend fun extract(archive: EelPath, target: EelPath) {
    // This task occupies both CPU and IO resources, so the CPU-bound dispatcher is chosen.
    withContext(Dispatchers.Default) {
      val archivePath = archive.asNioPath()
      val fileName = archive.fileName.lowercase()
      val decompressor = when {
        fileName.endsWith(".zip") -> Decompressor.Zip(archivePath)

        fileName.endsWith(".tar")
        || fileName.endsWith(".tar.gz")
        || fileName.endsWith(".tgz")
        || fileName.endsWith(".tar.bz2")
        || fileName.endsWith(".tbz2")
        || fileName.endsWith(".tar.xz")
        || fileName.endsWith(".txz")
          -> Decompressor.Tar(archivePath)

        else -> throw IOException("Unsupported archive: $archive")
      }

      decompressor.extract(target.asNioPath())
    }
  }
}