// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.hints

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ThreeState
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Provides a hint to indexing framework if this index accepts some specific filetype.
 *
 * Examples:
 *   * JsonSchemaFileValuesIndex filetype has a number of subtypes. This hint is as efficient as [FileBasedIndex.FileTypeSpecificInputFilter],
 *   but more convenient, because implementation can naturally use `instanceOf`, instead of explicitly listing all the known inheritors
 *   (see [com.intellij.util.indexing.hints.FileTypeInputFilterPredicate]).
 *
 *   * HtmlScriptSrcIndex does not know all the applicable filetypes in advance, but wants to accept any filetype associated with TemplateLanguage.
 *
 *   * text-based indexers with no specific filetypes can quickly reject binary files (see [com.intellij.util.indexing.hints.NonBinaryFileTypeInputFilter])
 *
 * See general rules in javadoc for [IndexingHint]
 */
@Experimental
interface FileTypeIndexingHint : IndexingHint {
  fun hintAcceptFileType(fileType: FileType): ThreeState
}
