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
class FileTypeInputFilterPredicate(private val predicate: (filetype: FileType) -> Boolean) : BaseFileTypeInputFilter() {

  constructor(vararg fileTypes: FileType) : this({ fileType -> fileTypes.contains(fileType) })

  override fun whenAllOtherHintsUnsure(file: IndexedFile): Boolean {
    throw AssertionError("Should not be invoked, because hintAcceptFileType for filetype never returns UNSURE");
  }

  override fun acceptFileType(fileType: FileType): ThreeState = ThreeState.fromBoolean(predicate(fileType))
}