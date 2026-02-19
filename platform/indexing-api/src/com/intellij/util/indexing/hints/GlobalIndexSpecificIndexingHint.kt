// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.hints

import com.intellij.util.indexing.FileBasedIndex.InputFilter
import com.intellij.util.indexing.IndexId
import org.jetbrains.annotations.ApiStatus

/**
 * Same as [FileTypeIndexingHint], but to be used with [com.intellij.util.indexing.GlobalIndexFilter]. All the general hint rules are applicable
 * here and work the same way as they do for [FileTypeIndexingHint].
 *
 * This API is internal, because [com.intellij.util.indexing.GlobalIndexFilter] is internal
 *
 * @see FileTypeIndexingHint
 */
@ApiStatus.Internal
interface GlobalIndexSpecificIndexingHint {
  /**
   * @return index-specific hint. There may be several global hints. All the hints will be ANDed with all the other global and
   * non-global hints applicable to given [indexId]. Therefore, if current hint does not care about provided [indexId],
   * it should return [com.intellij.util.indexing.hints.AcceptAllIndexingHint].
   */
  fun globalInputFilterForIndex(indexId: IndexId<*, *>): InputFilter
}
