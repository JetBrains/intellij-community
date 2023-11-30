// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.hints

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ThreeState
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.IndexedFile
import com.intellij.util.indexing.hints.FileTypeSubstitutionStrategy.AFTER_SUBSTITUTION
import org.jetbrains.annotations.ApiStatus


/**
 * Returns `NO` for binary file types, and `UNSURE` for others (i.e. delegates to [slowPathIfFileTypeHintUnsure]).
 */
@ApiStatus.Experimental
class NonBinaryFileTypeInputFilter(private val acceptInput: FileBasedIndex.InputFilter) : BaseFileTypeInputFilter(AFTER_SUBSTITUTION) {
  override fun acceptFileType(fileType: FileType): ThreeState {
    return if (fileType.isBinary) ThreeState.NO else ThreeState.UNSURE
  }

  override fun slowPathIfFileTypeHintUnsure(file: IndexedFile): Boolean {
    return acceptInput.acceptInput(file.file)
  }
}