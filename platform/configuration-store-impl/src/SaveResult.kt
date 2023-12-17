// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.impl.stores.SaveSessionAndFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SaveResult {
  companion object {
    @JvmField
    internal val EMPTY: SaveResult = SaveResult()
  }

  private var error: Throwable? = null

  @JvmField
  internal val readonlyFiles: MutableList<SaveSessionAndFile> = mutableListOf()

  @Synchronized
  internal fun addError(error: Throwable) {
    val existingError = this.error
    if (existingError == null) {
      this.error = error
    }
    else {
      existingError.addSuppressed(error)
    }
  }

  @Synchronized
  internal fun addReadOnlyFile(info: SaveSessionAndFile) {
    readonlyFiles.add(info)
  }

  @Synchronized
  internal fun appendTo(saveResult: SaveResult) {
    if (this === EMPTY) {
      return
    }

    synchronized(saveResult) {
      if (error != null) {
        if (saveResult.error == null) {
          saveResult.error = error
        }
        else {
          saveResult.error!!.addSuppressed(error)
        }
      }
      saveResult.readonlyFiles.addAll(readonlyFiles)
    }
  }

  @Synchronized
  internal fun throwIfErrored() {
    error?.let {
      throw it
    }
  }
}