// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.impl.stores.SaveSessionAndFile
import com.intellij.util.addSuppressed
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SaveResult {
  private var error: Throwable? = null

  @JvmField
  internal val readonlyFiles: MutableList<SaveSessionAndFile> = mutableListOf()

  @Synchronized
  internal fun addError(t: Throwable) {
    error = addSuppressed(error, t)
  }

  @Synchronized
  internal fun addReadOnlyFile(info: SaveSessionAndFile) {
    readonlyFiles.add(info)
  }

  @Synchronized
  internal fun rethrow() {
    val e = error
    if (e != null) {
      throw e
    }
  }
}
