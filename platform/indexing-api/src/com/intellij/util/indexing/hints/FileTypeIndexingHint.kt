// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.hints

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ThreeState
import com.intellij.util.indexing.FileBasedIndex.InputFilter
import com.intellij.util.indexing.FileBasedIndex.ProjectSpecificInputFilter
import com.intellij.util.indexing.IndexedFile
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.OverrideOnly

/**
 * **TL;DR;**
 * Use [FileTypeIndexingHint] with [InputFilter] or [ProjectSpecificInputFilter] to speed up scanning.
 *
 * [FileTypeIndexingHint] provides a hint to indexing framework if this index filter accepts some specific filetype. Note that filetype
 * can be either real filetype, or [com.intellij.util.indexing.SubstitutedFileType].
 *
 * Use [com.intellij.util.indexing.hints.BaseFileTypeInputFilter] as a base class instead of implementing this interface directly.
 *
 * **Long story**
 *
 * The main goal of the hints framework is to avoid iterating the whole VFS on startup just to evaluate if particular
 * file should be indexed by a particular indexer. That's why we try to shift the focus from individual files to large group of files.
 *
 * In runtime indexing framework first evaluates [acceptsFileTypeFastPath]. If [acceptsFileTypeFastPath] returns `YES` or `NO`, this value is used
 * for any file with the same filetype. If [acceptsFileTypeFastPath] returns [ThreeState.UNSURE], the framework will switch to "slow"
 * mode and will invoke [slowPathIfFileTypeHintUnsure] for each indexable file.
 *
 * Hint results are cached (at least until IDE restart). In particular, if hint uses ExtensionPoints to evaluate result, changes
 * in relevant extension points (e.g. loading/unloading plugins) should reset caches.
 * Use [com.intellij.util.indexing.FileBasedIndexEx.resetHints] to reset cached indexing hints.
 *
 * [slowPathIfFileTypeHintUnsure] is only invoked when [acceptsFileTypeFastPath] returned `UNSURE`, there is no need to add the logic from [acceptsFileTypeFastPath]
 * to [slowPathIfFileTypeHintUnsure]. But this logic still should be added to [InputFilter.acceptInput] as explained below.
 *
 * **When used with [InputFilter] or [ProjectSpecificInputFilter]:**
 *
 * Indexing framework evaluates [acceptsFileTypeFastPath] and falls back to [slowPathIfFileTypeHintUnsure].
 * [slowPathIfFileTypeHintUnsure] must answer either `true` or `false`. This means that indexing framework
 * will not invoke [InputFilter.acceptInput] or [ProjectSpecificInputFilter.acceptInput]. However, there may be other clients which may
 * invoke `acceptInput` without analyzing any hints. Therefore, `acceptInput` should provide answer just like if the filter didn't have any
 * hints in the first place.
 *
 * When using hint-aware base classes (like [com.intellij.util.indexing.hints.FileTypeInputFilterPredicate]) these classes implement
 * `acceptInput` method for you. Usually you cannot override this method, default implementation boils down to a code like:
 *
 * ```
 * fun acceptInput(file: IndexedFile): Boolean {
 *   val res = hintSomethingMethod(...)
 *   return if (res == ThreeState.UNSURE) whenAllHintsUnsure(file) else res.toBoolean()
 * }
 * ```
 *
 * **When used with [com.intellij.util.indexing.GlobalIndexFilter]:**
 *
 * You cannot use this [FileTypeIndexingHint] with [com.intellij.util.indexing.GlobalIndexFilter] directly.
 * Please use [GlobalIndexSpecificIndexingHint] instead.
 *
 * **A few words about filetype substitution.**
 *
 * Languages can be adjusted on the fly (usually, more generic languages are substituted with more specific languages,
 * for example, YAML language can be interpreted as EERbLanguage, or SQL can be substituted with concrete dialect).
 *
 * If a language for given file is substituted, IDE has two options how to index the file: either using original file type, or
 * default filetype of substituted language.
 * I.e. in the case when particular YAML is substituted with EERb language, [FileTypeIndexingHint] will
 * be invoked with `SubstitutedFileType{ErbFileType, YAMLFileType}` (because ERbFileType is the default filetype for EERbLanguage).
 * [com.intellij.util.indexing.hints.BaseFileTypeInputFilter] in its turn will resolve `SubstitutedFileType` to `ErbFileType`
 * (this simplifies `BaseFileTypeInputFilter` subclasses implementation so that they don't need to care much about `SubstitutedFileType`)
 *
 *
 * **Hint usage examples:**
 *   * JsonSchemaFileValuesIndex filetype has a number of subtypes. This hint is as efficient as [FileBasedIndex.FileTypeSpecificInputFilter],
 *   but more convenient, because implementation can naturally use `instanceOf`, instead of explicitly listing all the known inheritors
 *   (see [com.intellij.util.indexing.hints.FileTypeInputFilterPredicate]).
 *
 *   * HtmlScriptSrcIndex does not know all the applicable filetypes in advance, but wants to accept any filetype associated with TemplateLanguage.
 *
 *   * text-based indexers with no specific filetypes can quickly reject binary files (see [com.intellij.util.indexing.hints.NonBinaryFileTypeInputFilter])
 *
 *
 * @see com.intellij.psi.LanguageSubstitutor
 */
@Experimental
@OverrideOnly
interface FileTypeIndexingHint {
  /**
   * @return {@link ThreeState#YES} if this filter accepts a file of given [fileType], {@link ThreeState#NO} if it doesn't, and
   *         {@link ThreeState#UNSURE} if {@link #slowPathIfFileTypeHintUnsure()} must be called to find out
   */
  fun acceptsFileTypeFastPath(fileType: FileType): ThreeState

  /** @return true if a file should be included in indexing, false otherwise */
  fun slowPathIfFileTypeHintUnsure(file: IndexedFile): Boolean
}
