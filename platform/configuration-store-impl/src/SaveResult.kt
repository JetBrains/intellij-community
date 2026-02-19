// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.addSuppressed

internal class SaveResult {
  private var error: Throwable? = null

  @JvmField
  val readonlyFiles: MutableList<SaveSessionAndFile> = mutableListOf()

  @Synchronized
  fun addError(t: Throwable) {
    error = addSuppressed(error, t)
  }

  @Synchronized
  fun addReadOnlyFile(info: SaveSessionAndFile) {
    readonlyFiles.add(info)
  }

  @Synchronized
  fun rethrow() {
    error?.let {
      throw it
    }
  }
}

internal data class SaveSessionAndFile(@JvmField val session: SaveSession, @JvmField val file: VirtualFile)