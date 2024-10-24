// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.platform.eel.EelArchiveApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.util.io.Decompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.path.Path

internal object LocalEelArchiveApiImpl : EelArchiveApi {
  override suspend fun extract(archive: EelPath.Absolute, target: EelPath.Absolute) {
    // This task occupies both CPU and IO resources, so the CPU-bound dispatcher is chosen.
    withContext(Dispatchers.Default) {
      // TODO Use file magic?
      val fileName = archive.fileName.lowercase()
      val decompressor = when {
        fileName.endsWith(".zip") -> Decompressor.Zip(Path(archive.toString()))

        fileName.endsWith(".tar.gz")
        || fileName.endsWith(".tar.bz2")
        || fileName.endsWith(".tar.xz")
          -> Decompressor.Tar(Path(archive.toString()))

        else -> TODO("Unsupported archive: $archive")
      }

      decompressor.extract(Path(target.toString()))
    }
  }
}