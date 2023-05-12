// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.hints

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ThreeState
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndex.InputFilter
import com.intellij.util.indexing.IndexedFile
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Provides a hint to indexing framework if this index accepts some specific filetype.
 *
 * Examples:
 *   * JsonSchemaFileValuesIndex filetype has a number of subtypes. This hint is as efficient as [FileBasedIndex.FileTypeSpecificInputFilter],
 *   but more convenient, because implementation can naturally use `instanceOf`, instead of explicitly listing all the known inheritors
 *   (see [FileTypeInputFilterPredicate]).
 *
 *   * HtmlScriptSrcIndex does not know all the applicable filetypes in advance, but wants to accept any filetype associated with TemplateLanguage.
 *
 *   * text-based indexers with no specific filetypes can quickly reject binary files (see [NonBinaryFileTypeInputFilter])
 *
 * See general rules in javadoc for [IndexingHint]
 */
@Experimental
interface FileTypeIndexingHint : IndexingHint {
  fun hintAcceptFileType(fileType: FileType): ThreeState
}

@Experimental
interface FileTypeProjectSpecificInputFilter : FileBasedIndex.ProjectSpecificInputFilter, FileTypeIndexingHint

/**
 * Returns `YES` or `NO` for given filetype predicate. Never returns `UNSURE`, therefore `acceptInput` is never invoked.
 */
@Experimental
class FileTypeInputFilterPredicate(private val predicate: (filetype: FileType) -> Boolean) : BaseFileTypeInputFilter() {
  override fun whenAllOtherHintsUnsure(file: IndexedFile): Boolean {
    throw AssertionError("Should not be invoked, because hintAcceptFileType for filetype never returns UNSURE");
  }

  override fun hintAcceptFileType(fileType: FileType): ThreeState = ThreeState.fromBoolean(predicate(fileType))
}

/**
 * Returns `NO` for binary file types, and `UNSURE` for others (i.e. delegates to [whenAllOtherHintsUnsure]).
 */
@Experimental
class NonBinaryFileTypeInputFilter(private val acceptInput: InputFilter) : BaseFileTypeInputFilter() {
  override fun hintAcceptFileType(fileType: FileType): ThreeState {
    return if (fileType.isBinary) ThreeState.NO else ThreeState.UNSURE;
  }

  override fun whenAllOtherHintsUnsure(file: IndexedFile): Boolean {
    return acceptInput.acceptInput(file.file)
  }
}

/**
 * Base class for FileTypeInputFilter. Contains default implementation of `acceptInput(file: VirtualFile)` which delegate to hints.
 */
@Experimental
abstract class BaseFileTypeInputFilter : FileTypeProjectSpecificInputFilter {
  final override fun acceptInput(file: IndexedFile): Boolean {
    return when (hintAcceptFileType(file.fileType)) {
      ThreeState.YES -> true
      ThreeState.NO -> false
      ThreeState.UNSURE -> whenAllOtherHintsUnsure(file)
    }
  }
}

/**
 * Base class for FileTypeInputFilter. Contains default implementation of `acceptInput(file: VirtualFile)` which delegate to hints.
 */
@Experimental
abstract class BaseFileTypeProjectSpecificInputFilter : FileTypeProjectSpecificInputFilter {
  final override fun acceptInput(file: IndexedFile): Boolean {
    return when (hintAcceptFileType(file.fileType)) {
      ThreeState.YES -> true
      ThreeState.NO -> false
      ThreeState.UNSURE -> whenAllOtherHintsUnsure(file)
    }
  }
}