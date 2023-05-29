// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.hints

import com.intellij.util.ThreeState
import com.intellij.util.indexing.FileBasedIndex.InputFilter
import com.intellij.util.indexing.FileBasedIndex.ProjectSpecificInputFilter
import com.intellij.util.indexing.IndexedFile

/**
 *  **General rules applicable to any hint:**
 *
 * All the hints are evaluated before [IndexingHint.whenAllOtherHintsUnsure]. It is guaranteed that [IndexingHint.whenAllOtherHintsUnsure]
 * is only evaluated for files for which all hints returned [ThreeState.UNSURE]. I.e. there is no need to add the logic from hints to
 * [IndexingHint.whenAllOtherHintsUnsure]. But this logic still should be added to [InputFilter.acceptInput] as explained below.
 *
 * If a hint returns either [ThreeState.YES] or [ThreeState.NO], neither [IndexingHint.whenAllOtherHintsUnsure] nor other hints are evaluated.
 *
 * Hint results are cached (at least until IDE restart). In particular, if hint uses ExtensionPoints to evaluate result, changes
 * in relevant extension points (e.g. loading/unloading plugins) should reset caches.
 * Use [com.intellij.util.indexing.FileBasedIndexEx.resetHints] to reset cached indexing hints.
 *
 * Hints should not assume that they are evaluated in any specific order.
 *
 * If a hint returns [ThreeState.UNSURE], the indexing framework will first evaluate remaining hints (if any), if all of them return
 * [ThreeState.UNSURE], then [IndexingHint.whenAllOtherHintsUnsure] will be invoked for individual files.
 *
 * The main goal of the hints framework is to avoid iterating the whole VFS on startup just to evaluate if particular
 * file should be indexed by a particular indexer. That's why we try to shift the focus from individual files to large group of files.
 *
 * **When used with [InputFilter] or [ProjectSpecificInputFilter]:**
 *
 * Indexing framework evaluates all the hints (if available), and falls back to [IndexingHint.whenAllOtherHintsUnsure] if no hint
 * returned `YES` or `NO`. [IndexingHint.whenAllOtherHintsUnsure] must answer either `true` or `false`. This means that indexing framework
 * will not invoke [InputFilter.acceptInput] or [ProjectSpecificInputFilter.acceptInput]. However, there may be other clients which may
 * invoke `acceptInput` without analyzing any hints. Therefore, `acceptInput` should provide answer just like if the filter didn't have any
 * hints in the first place.
 *
 * When using hint-aware base classes (like [FileTypeInputFilterPredicate]) these classes implement `acceptInput` methods for you.
 * Usually you don't need to override this method, default implementation boils down to a code like:
 *
 * ```
 * fun acceptInput(file: IndexedFile): Boolean {
 *   val res = hintSomethingMethod(...)
 *   return if (res == ThreeState.UNSURE) whenAllHintsUnsure(file) else fromBoolean(res)
 * }
 * ```
 *
 * **When used with [com.intellij.util.indexing.GlobalIndexFilter]:**
 *
 * Same as when used with [InputFilter] or [ProjectSpecificInputFilter]. This section is mostly to draw your attention that hints can be
 * used with [com.intellij.util.indexing.GlobalIndexFilter]
 */
interface IndexingHint {
  fun whenAllOtherHintsUnsure(file: IndexedFile): Boolean;
}