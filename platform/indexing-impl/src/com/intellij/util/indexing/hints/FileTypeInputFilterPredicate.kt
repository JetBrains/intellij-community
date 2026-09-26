// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.hints

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ThreeState
import com.intellij.util.indexing.IndexedFile
import org.jetbrains.annotations.ApiStatus


/**
 * Returns `YES` or `NO` for given filetype predicate. Never returns `UNSURE`, therefore `acceptInput` is never invoked.
 */
@ApiStatus.Experimental
class FileTypeInputFilterPredicate : BaseFileTypeInputFilter {
  private val predicate: (filetype: FileType) -> Boolean

  constructor(predicate: (filetype: FileType) -> Boolean) : super(FileTypeSubstitutionStrategy.AFTER_SUBSTITUTION) {
    this.predicate = predicate
  }

  constructor(fileTypeStrategy: FileTypeSubstitutionStrategy, predicate: (filetype: FileType) -> Boolean) : super(fileTypeStrategy) {
    this.predicate = predicate
  }

  constructor(vararg fileTypes: FileType) : this({ fileType -> fileTypes.contains(fileType) })

  override fun slowPathIfFileTypeHintUnsure(file: IndexedFile): Boolean = false // for directories

  override fun acceptFileType(fileType: FileType): ThreeState = ThreeState.fromBoolean(predicate(fileType))
}