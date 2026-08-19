// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

/**
 * Immutable self-consistent snapshot of the whole document state.
 *
 * A snapshot owns the document [text] and the state derived from it that has to stay consistent with the
 * text across every change -- the sputniks. A document core publishes a new snapshot atomically, so a reader
 * observes the text and everything derived from it from the same document version.
 *
 * A `with*` method or [applyOp] returns `this` when it changes nothing, and callers depend on that: a no-op update leaves
 * the core's snapshot field pointing at the same instance, and caches keyed by snapshot identity, such as the
 * frozen document of [DocumentCore.frozen], keep hitting. [withMetadata] is the exception -- when the
 * characters survive it yields the newest snapshot, which is the other one rather than `this`.
 *
 * @see DocumentCore.snapshot
 * @see DocumentSputnik
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

  /**
   * Returns the sputnik associated with [key], or `null` if there is none.
   *
   * @see DocumentSputnik
   */
  @Contract(pure = true)
  fun <S : DocumentSputnik> sputnik(key: Key<S>): S?

  /**
   * Returns this snapshot with [op] applied: text and mod state reflect [op], and sputniks are rebuilt to
   * stay consistent with it.
   *
   * @see DocumentSputnik.applyOp
   */
  @Contract(pure = true)
  fun applyOp(op: DocumentOp): DocumentSnapshot

  /**
   * Returns this snapshot with [ops] applied in order via repeated [applyOp] calls. One logical change can
   * lower to several ops, so sputniks may be rebuilt more than once; see [DocumentSputnik.applyOp].
   */
  @Contract(pure = true)
  fun applyOps(ops: List<DocumentOp>): DocumentSnapshot {
    var newSnapshot = this
    for (op in ops) {
      newSnapshot = newSnapshot.applyOp(op)
    }
    return newSnapshot
  }

  /**
   * Returns [metadata] if it still has this snapshot's text, otherwise returns this snapshot unchanged
   * and drops [metadata] entirely.
   *
   * This is how a document mutator reconciles the snapshot a change was computed against with whatever
   * snapshot is current at publish time: a metadata-only update that raced the change -- a modification
   * stamp set from another thread, say -- shares this snapshot's text, so [metadata] survives and is taken
   * as a whole, sputniks included. But if [metadata]'s text has already moved on to some other change, it
   * is talking about a text this snapshot no longer has, so it is discarded wholesale -- stamp, sequence,
   * and sputniks alike -- rather than mixed with this snapshot's own.
   *
   * @param metadata latest version of the document
   */
  @Contract(pure = true)
  fun withMetadata(metadata: DocumentSnapshot): DocumentSnapshot

  /**
   * Returns a human-readable dump of the snapshot state, used for diagnostics only
   */
  @Contract(pure = true)
  fun dumpState(): String
}
