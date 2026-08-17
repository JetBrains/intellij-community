// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

/**
 * Immutable state attached to [DocumentSnapshot] and rebuilt on every text change to stay consistent with the text.
 *
 * Sputniks are identified by [com.intellij.openapi.util.Key] identity.
 * Keep keys in static fields: a garbage-collected key leaves its sputnik unreachable and unremovable.
 *
 * @see DocumentSnapshot.sputnik
 * @see DocumentSnapshot.withSputnik
 */
@ApiStatus.Internal
interface DocumentSputnik {

  /**
   * Returns the state of this sputnik consistent with [after], the text produced by applying [diff] to [before].
   *
   * The method runs on the writer thread inside the document mutation producing the next [DocumentSnapshot],
   * so implementations must:
   * - be fast: this call is on the critical path of every document change;
   * - not throw: an exception aborts the text change halfway through;
   * - tolerate being called more than once per change: the mutation publishes its snapshot with a
   *   compare-and-set, and a lost race re-applies the whole update, rebuilding the sputniks again;
   * - compute the new state only from [before], [after] and [diff]: the snapshot holding the result
   *   does not exist yet, so a sputnik cannot observe the other sputniks of the document;
   * - return a sputnik of the same type as `this`: the result stays associated with the same key.
   *
   * [DocumentTextPatch.originStartOffset]/[DocumentTextPatch.originEndOffset] expose the requested range before
   * prefix/suffix trimming, so an implementation can distinguish a narrowed change from an untrimmed one.
   */
  @Contract(pure = true)
  fun withTextChange(
    before: DocumentText,
    after: DocumentText,
    diff: DocumentTextPatch,
  ): DocumentSputnik
}
