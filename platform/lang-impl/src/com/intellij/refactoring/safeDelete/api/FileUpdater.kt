// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.api

import com.intellij.refactoring.rename.api.FileOperation

interface FileUpdater {
  fun prepareFileUpdate(): Collection<FileOperation> = emptyList()
  
  companion object {
    val EMPTY: FileUpdater = object : FileUpdater {}
  }
}