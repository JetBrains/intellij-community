// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.api

import com.intellij.refactoring.rename.api.FileOperation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface FileUpdater {
  fun prepareFileUpdate(): Collection<FileOperation> = emptyList()
  
  companion object {
    val EMPTY: FileUpdater = object : FileUpdater {}
  }
}