// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.util.text.ImmutableCharSequence
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

/**
 * Immutable state attached to [DocumentSnapshot] and rebuilt on every text change to stay consistent with the text.
 *
 * Aspects are identified by [com.intellij.openapi.util.Key] identity.
 * Keep keys in static fields: a garbage-collected key leaves its aspect unreachable and unremovable.
 *
 * @see DocumentSnapshot.aspect
 * @see DocumentSnapshot.withAspect
 */
@ApiStatus.Internal
interface DocumentAspect {

  /**
   * Returns the state of this aspect consistent with [newWholeText].
   *
   * The method runs on the writer thread inside the document mutation producing the next [DocumentSnapshot],
   * so implementations must:
   * - be fast: this call is on the critical path of every document change;
   * - not throw: an exception aborts the text change halfway through;
   * - compute the new state only from [oldSnapshot] (the snapshot before the change) and the change parameters:
   *   the snapshot holding the result does not exist yet;
   * - return an aspect of the same type as `this`: the result stays associated with the same key.
   *
   * [newWholeText] is consistent with the rest parameters:
   * `newWholeText == oldSnapshot.text().replace(startOffset, endOffset, newFragment)`
   */
  @Contract(pure = true)
  fun withText(
    oldSnapshot: DocumentSnapshot,
    newWholeText: ImmutableCharSequence,
    startOffset: Int,
    endOffset: Int,
    newFragment: CharSequence,
    newModStamp: Long,
    wholeTextReplaced: Boolean,
  ): DocumentAspect
}
