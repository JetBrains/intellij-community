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
 * @see DocumentMutator.setSputnik
 */
@ApiStatus.Internal
interface DocumentSputnik {

  /**
   * Returns the state of this sputnik consistent with [after], the snapshot produced by applying [op] to [before].
   * This method is called only for a text-changing [DocumentTextPatch].
   *
   * Runs on the writer thread, on the critical path of every document change: must be fast, must not throw (an
   * exception aborts the change), must return a sputnik of the same type as `this`, and must tolerate being
   * called more than once for what looks like one change -- [before] is not guaranteed to be the snapshot from
   * right before it.
   *
   * [after]'s sputniks may be only partially rebuilt: other keys can still hold their pre-change value. An
   * implementation may opportunistically read an already-rebuilt sibling off [after], but must stay correct if
   * none have been.
   */
  @Contract(pure = true)
  fun applyOp(before: DocumentSnapshot, after: DocumentSnapshot, op: DocumentOp): DocumentSputnik
}
