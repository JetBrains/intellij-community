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
 * If filetype is a [SubstitutedFileType] there are two options how to invoke [acceptFileType]: with filetype before substitution as
 * an argument ([SubstitutedFileType.getOriginalFileType]), or filetype after substitution ([SubstitutedFileType.getFileType]).
 *
 * Directories are rejected by this filter
 *
 * @param fileTypeStrategy
 *   strategy to resolve [SubstitutedFileType]. When in doubt - prefer [FileTypeSubstitutionStrategy.BEFORE_SUBSTITUTION],
 *   because calculating substituted file type is not free.
 *
 * @see com.intellij.psi.LanguageSubstitutor
 */
@ApiStatus.Experimental
abstract class BaseFileTypeInputFilter(private val fileTypeStrategy: FileTypeSubstitutionStrategy) : FileBasedIndex.ProjectSpecificInputFilter,
                                                                                                     FileTypeIndexingHint {
  final override fun acceptsFileTypeFastPath(fileType: FileType): ThreeState {
    val fileTypeToUse: FileType =
      if (fileType is SubstitutedFileType) {
        if (fileTypeStrategy == FileTypeSubstitutionStrategy.BEFORE_SUBSTITUTION) fileType.originalFileType else fileType.fileType
      }
      else {
        fileType
      }

    return acceptFileType(fileTypeToUse)
  }

  final override fun acceptInput(file: IndexedFile): Boolean {
    if (file.file.isDirectory) return false
    return when (acceptsFileTypeFastPath(file.fileType)) {
      ThreeState.YES -> true
      ThreeState.NO -> false
      ThreeState.UNSURE -> slowPathIfFileTypeHintUnsure(file)
    }
  }

  abstract fun acceptFileType(fileType: FileType): ThreeState
}
