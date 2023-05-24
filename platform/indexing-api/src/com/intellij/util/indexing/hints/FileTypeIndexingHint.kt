// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.hints

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ThreeState
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Provides a hint to indexing framework if this index filter accepts some specific filetype. Note that filetype can be either real
 * filetype, or [com.intellij.util.indexing.SubstitutedFileType]. Use [com.intellij.util.indexing.hints.BaseFileTypeInputFilter]
 * when in doubt - it always resolves artificial `SubstitutedFileType` by preferring target filetype (which is the best strategy
 * in most cases).
 *
 * A few words about filetype substitution.
 *
 * Languages can be adjusted on the fly (usually, more generic languages are substituted with more specific languages,
 * for example, YAML language can be interpreted as EERbLanguage, or SQL can be substituted with concrete dialect).
 *
 * If a language for given file is substituted, IDE has two options how to index the file: either using original file type, or
 * default filetype of substituted language.
 * I.e. in the case when particular YAML is substituted with EERb language, [FileTypeIndexingHint] will
 * be invoked with `SubstitutedFileType{ERbFileType, YAMLFileType}` (because ERbFileType is the default filetype for EERbLanguage).
 * [com.intellij.util.indexing.hints.BaseFileTypeInputFilter] in its turn will resolve `SubstitutedFileType` to `ERbFileType`
 * (this simplifies `BaseFileTypeInputFilter` subclasses implementation so that they don't need to care much about `SubstitutedFileType`)
 *
 *
 * Hint usage examples:
 *   * JsonSchemaFileValuesIndex filetype has a number of subtypes. This hint is as efficient as [FileBasedIndex.FileTypeSpecificInputFilter],
 *   but more convenient, because implementation can naturally use `instanceOf`, instead of explicitly listing all the known inheritors
 *   (see [com.intellij.util.indexing.hints.FileTypeInputFilterPredicate]).
 *
 *   * HtmlScriptSrcIndex does not know all the applicable filetypes in advance, but wants to accept any filetype associated with TemplateLanguage.
 *
 *   * text-based indexers with no specific filetypes can quickly reject binary files (see [com.intellij.util.indexing.hints.NonBinaryFileTypeInputFilter])
 *
 * See general rules in javadoc for [IndexingHint]
 *
 * @see IndexingHint
 * @see com.intellij.psi.LanguageSubstitutor
 */
@Experimental
interface FileTypeIndexingHint : IndexingHint {
  fun hintAcceptFileType(fileType: FileType): ThreeState
}
