// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.hints

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ThreeState
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.IndexedFile
import com.intellij.util.indexing.SubstitutedFileType
import org.jetbrains.annotations.ApiStatus

/**
 * Base implementation of filetype-hint-aware [com.intellij.util.indexing.FileBasedIndex.InputFilter].
 *
 * Contains default implementation of `acceptInput(file: VirtualFile)` which delegate to hints.
 *
 * If filetype is a [SubstitutedFileType] invokes [acceptFileType] with substituted filetype ([SubstitutedFileType.getFileType]).
 */
@ApiStatus.Experimental
abstract class BaseFileTypeInputFilter : FileBasedIndex.ProjectSpecificInputFilter, FileTypeIndexingHint {
  override fun hintAcceptFileType(fileType: FileType): ThreeState {
    return acceptFileType((fileType as? SubstitutedFileType)?.fileType ?: fileType)
  }

  final override fun acceptInput(file: IndexedFile): Boolean {
    return when (hintAcceptFileType(file.fileType)) {
      ThreeState.YES -> true
      ThreeState.NO -> false
      ThreeState.UNSURE -> whenAllOtherHintsUnsure(file)
    }
  }

  abstract fun acceptFileType(fileType: FileType): ThreeState
}
