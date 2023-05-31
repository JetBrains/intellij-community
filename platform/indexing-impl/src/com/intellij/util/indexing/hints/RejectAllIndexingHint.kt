// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.hints

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ThreeState
import com.intellij.util.indexing.IndexedFile

class RejectAllIndexingHint : BaseFileTypeInputFilter() {
  override fun acceptFileType(fileType: FileType): ThreeState = ThreeState.NO

  override fun whenAllOtherHintsUnsure(file: IndexedFile): Boolean = false // for directories
}