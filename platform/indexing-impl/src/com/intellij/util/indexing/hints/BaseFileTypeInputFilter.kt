// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.hints

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ThreeState
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.IndexedFile
import com.intellij.util.indexing.SubstitutedFileType
import com.intellij.util.indexing.hints.BaseFileTypeInputFilter.FileTypeStrategy
import org.jetbrains.annotations.ApiStatus

/**
 * Base implementation of filetype-hint-aware [com.intellij.util.indexing.FileBasedIndex.InputFilter].
 *
 * Contains default implementation of `acceptInput(file: VirtualFile)` which delegate to hints.
 *
 * If filetype is a [SubstitutedFileType] there are two options how to invoke [acceptFileType]: with filetype before substitution as
 * an argument ([SubstitutedFileType.getOriginalFileType]), or filetype after substitution ([SubstitutedFileType.getFileType]).
 * Default behavior is to use filetype after substitution. This can be changed via [FileTypeStrategy]
 *
 * @see com.intellij.psi.LanguageSubstitutor
 */
@ApiStatus.Experimental
abstract class BaseFileTypeInputFilter(private val fileTypeStrategy: FileTypeStrategy) : FileBasedIndex.ProjectSpecificInputFilter,
                                                                                         FileTypeIndexingHint {
  enum class FileTypeStrategy { BEFORE_SUBSTITUTION, AFTER_SUBSTITUTION }

  constructor() : this(FileTypeStrategy.AFTER_SUBSTITUTION)

  final override fun hintAcceptFileType(fileType: FileType): ThreeState {
    val fileTypeToUse: FileType =
      if (fileType is SubstitutedFileType) {
        if (fileTypeStrategy == FileTypeStrategy.BEFORE_SUBSTITUTION) fileType.originalFileType else fileType.fileType
      }
      else {
        fileType
      }

    return acceptFileType(fileTypeToUse)
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
