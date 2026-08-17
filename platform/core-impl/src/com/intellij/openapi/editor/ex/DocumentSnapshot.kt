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
 * A `with*` method returns `this` when it changes nothing, and callers depend on that: a no-op update leaves
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
   * Returns snapshot with specified `newModStamp`. The sputniks are kept, the characters do not change.
   *
   * @param incrementModSeq whether [DocumentModState.sequence] should be incremented
   * @see DocumentModState.withStamp
   */
  @Contract(pure = true)
  fun withModStamp(newModStamp: Long, incrementModSeq: Boolean): DocumentSnapshot

  /**
   * Returns snapshot with cleared specified line flags. The sputniks are kept, the characters do not change.
   *
   * @param endLine is exclusive. Two special values `0` and `Int.MAX_VALUE` ignoring range checks
   * @see DocumentModState.withClearedLineFlags
   */
  @Contract(pure = true)
  fun withClearedLineFlags(startLine: Int, endLine: Int, exceptLines: IntArray): DocumentSnapshot

  /**
   * Returns the sputnik associated with [key], or `null` if there is none.
   *
   * @see DocumentSputnik
   */
  @Contract(pure = true)
  fun <S : DocumentSputnik> sputnik(key: Key<S>): S?

  /**
   * Returns a snapshot where [key] is associated with [sputnik], replacing the current association if any,
   * or without any association for [key] if [sputnik] is `null`.
   *
   * Keep [key] in a static field: keys are compared by identity,
   * and a garbage-collected key leaves its sputnik unreachable and unremovable
   */
  @Contract(pure = true)
  fun <S : DocumentSputnik> withSputnik(key: Key<S>, sputnik: S?): DocumentSnapshot

  /**
   * Returns snapshot with the text of this snapshot and the text metadata taken from the other snapshot.
   * This method is used to preserve the semantics of metadata being a tracker of text timeline.
   *
   * Sputniks follow the newest snapshot whose text survives:
   * - if [metadata] has the same characters as this snapshot, the result takes [metadata]'s sputniks --
   *   the latest document state, including sputniks attached during before-change listeners
   *   (sputnik updates never change the text instance);
   * - otherwise this snapshot's sputniks are kept, because [metadata]'s sputniks
   *   correspond to its discarded text
   *
   * The [DocumentModState.stamp]/[DocumentModState.sequence] always come from [metadata], but
   * line-modification tracking always stays this snapshot's own: it is tied to the text that survives,
   * never to [metadata]'s discarded one.
   *
   * @param metadata latest version of the document
   * @see DocumentModState.withMetadata
   */
  @Contract(pure = true)
  fun withMetadata(metadata: DocumentSnapshot): DocumentSnapshot

  /**
   * Returns snapshot with [patch] applied: the text is this snapshot's text after the replacement described
   * by [patch], and every sputnik is rebuilt against the same patch to stay consistent with the new text.
   *
   * @see DocumentText.applyOp
   * @see DocumentTextPatch.toOps
   * @see DocumentSputnik.withTextChange
   */
  @Contract(pure = true)
  fun withPatch(patch: DocumentTextPatch): DocumentSnapshot

  /**
   * Returns a human-readable dump of the snapshot state, used for diagnostics only
   */
  @Contract(pure = true)
  fun dumpState(): String
}
