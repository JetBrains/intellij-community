// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.id

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ThreeState
import com.intellij.util.indexing.IndexedFile
import com.intellij.util.indexing.hints.BaseFileTypeInputFilter
import com.intellij.util.indexing.hints.FileTypeIndexingHint
import com.intellij.util.indexing.hints.FileTypeSubstitutionStrategy

/**
 * 'Smart' file-filter for {@link IdIndex}: allows extending filtering patterns with {@link IndexFilterExcludingExtension}.
 * The current use of it: exclude .java source-files in libraries (index .class-files instead).
 */
internal class IdIndexFilter : BaseFileTypeInputFilter(FileTypeSubstitutionStrategy.BEFORE_SUBSTITUTION) {

  override fun acceptFileType(fileType: FileType): ThreeState {
    //'.class' fileType is also handled here:
    val fileTypeIndexer = IdTableBuilding.getFileTypeIndexer(fileType)

    if (fileTypeIndexer == null) {
      return ThreeState.NO
    }

    if (fileTypeIndexer is FileTypeIndexingHint) {
      //give subIndexer a chance to override default filtering
      return fileTypeIndexer.acceptsFileTypeFastPath(fileType)
    }
    
    return ThreeState.YES
  }

  override fun slowPathIfFileTypeHintUnsure(indexedFile: IndexedFile): Boolean {
    val file = indexedFile.file
    val fileType = file.fileType

    //'.class' fileType is also handled here:
    val fileTypeIndexer = IdTableBuilding.getFileTypeIndexer(fileType)
    check(fileTypeIndexer != null) { "slowPathIfFileTypeHintUnsure($indexedFile): must be called only with filetypes which have associated fileTypeIndexer" }
    check(fileTypeIndexer is FileTypeIndexingHint) { "slowPathIfFileTypeHintUnsure($indexedFile): $fileTypeIndexer must implement FileTypeIndexingHint" }

    return fileTypeIndexer.slowPathIfFileTypeHintUnsure(indexedFile)
  }
}