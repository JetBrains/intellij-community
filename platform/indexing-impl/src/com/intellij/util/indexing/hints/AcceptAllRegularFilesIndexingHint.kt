// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.hints

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ThreeState
import com.intellij.util.indexing.IndexedFile
import com.intellij.util.indexing.hints.FileTypeSubstitutionStrategy.BEFORE_SUBSTITUTION

object AcceptAllRegularFilesIndexingHint : BaseFileTypeInputFilter(BEFORE_SUBSTITUTION) {
  override fun acceptFileType(fileType: FileType): ThreeState = ThreeState.YES

  override fun slowPathIfFileTypeHintUnsure(file: IndexedFile): Boolean {
    thisLogger().error("Should not be invoked. acceptFileType never returns UNSURE.")
    return true
  }
}