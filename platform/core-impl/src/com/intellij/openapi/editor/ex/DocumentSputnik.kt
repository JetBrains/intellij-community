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
   * Returns the state of this sputnik consistent with [after], the snapshot produced by applying [diff] to [before].
   *
   * Runs on the writer thread, on the critical path of every document change: implementations must be fast, must
   * not throw (an exception aborts the change halfway through), must tolerate being called more than once per
   * change (a lost publish race rebuilds the sputniks again), and must return a sputnik of the same type as `this`.
   *
   * [before] is the snapshot prior to the change. [after] already has the final post-change text/mod state, but
   * its sputniks reflect only whichever ones were rebuilt earlier in this same call -- an incidental order, not a
   * declared dependency graph, so this sputnik's own key in [after] still holds the pre-change value too. An
   * implementation may opportunistically read an already-rebuilt sibling off [after], but must stay correct even
   * when none have been.
   */
  @Contract(pure = true)
  fun withTextChange(
    before: DocumentSnapshot,
    after: DocumentSnapshot,
    diff: DocumentTextPatch,
  ): DocumentSputnik
}
