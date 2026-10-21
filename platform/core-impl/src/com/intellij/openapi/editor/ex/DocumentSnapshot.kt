// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

/**
 * Immutable self-consistent snapshot of the whole document state.
 *
 * A snapshot owns the document [text] and the state derived from it that has to stay consistent with the
 * text across every change -- the sputniks. A document core publishes a new snapshot atomically, so a reader
 * observes the text and everything derived from it from the same document version.
 *
 * [applyOp] returns `this` when it changes nothing, and callers depend on that: a no-op update leaves
 * the core's snapshot field pointing at the same instance, and caches keyed by snapshot identity, such as the
 * range marker roots, keep hitting.
 *
 * @see DocumentCore.snapshot
 */
@ApiStatus.Internal
interface DocumentSnapshot {

  /**
   * Returns the text of this snapshot
   */
  @Contract(pure = true)
  fun text(): DocumentText

  /**
   * Returns the modification-tracking state of this snapshot
   */
  @Contract(pure = true)
  fun modState(): DocumentModState

  @Contract(pure = true)
  fun applyOp(op: DocumentOp): DocumentSnapshot

  @Contract(pure = true)
  fun newInstance(): DocumentSnapshot
}
