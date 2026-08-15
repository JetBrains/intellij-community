// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

/**
 * Immutable self-consistent snapshot of the whole document state.
 *
 * A snapshot owns the document [text] and the state derived from it that has to stay consistent with the
 * text across every change -- the aspects. A document core publishes a new snapshot atomically, so a reader
 * observes the text and everything derived from it from the same document version.
 *
 * A `with*` method returns `this` when it changes nothing, and callers depend on that: a no-op update leaves
 * the core's snapshot field pointing at the same instance, and caches keyed by snapshot identity, such as the
 * frozen document of [DocumentCore.frozen], keep hitting. [withMetadata] is the exception -- when the
 * characters survive it yields the newest snapshot, which is the other one rather than `this`.
 *
 * @see DocumentCore.snapshot
 * @see DocumentAspect
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
   * Returns snapshot with specified `newModStamp`. The aspects are kept, the characters do not change.
   *
   * @param incrementModSeq whether [DocumentModState.sequence] should be incremented
   * @see DocumentModState.withStamp
   */
  @Contract(pure = true)
  fun withModStamp(newModStamp: Long, incrementModSeq: Boolean): DocumentSnapshot

  /**
   * Returns snapshot with cleared specified line flags. The aspects are kept, the characters do not change.
   *
   * @param endLine is exclusive. Two special values `0` and `Int.MAX_VALUE` ignoring range checks
   * @see DocumentModState.withClearedLineFlags
   */
  @Contract(pure = true)
  fun withClearedLineFlags(startLine: Int, endLine: Int, exceptLines: IntArray): DocumentSnapshot

  /**
   * Returns the aspect associated with [key], or `null` if there is none.
   *
   * @see DocumentAspect
   */
  @Contract(pure = true)
  fun <A : DocumentAspect> aspect(key: Key<A>): A?

  /**
   * Returns a snapshot where [key] is associated with [aspect], replacing the current association if any,
   * or without any association for [key] if [aspect] is `null`.
   *
   * Keep [key] in a static field: keys are compared by identity,
   * and a garbage-collected key leaves its aspect unreachable and unremovable
   */
  @Contract(pure = true)
  fun <A : DocumentAspect> withAspect(key: Key<A>, aspect: A?): DocumentSnapshot

  /**
   * Returns snapshot with the text of this snapshot and the text metadata taken from the other snapshot.
   * This method is used to preserve the semantics of metadata being a tracker of text timeline.
   *
   * Aspects follow the newest snapshot whose text survives:
   * - if [metadata] has the same characters as this snapshot, the result takes [metadata]'s aspects --
   *   the latest document state, including aspects attached during before-change listeners
   *   (aspect updates never change the text instance);
   * - otherwise this snapshot's aspects are kept, because [metadata]'s aspects
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
   * by [patch], and every aspect is rebuilt against the same patch to stay consistent with the new text.
   *
   * @see DocumentText.withPatch
   * @see DocumentAspect.withTextChange
   */
  @Contract(pure = true)
  fun withPatch(patch: DocumentTextPatch): DocumentSnapshot

  /**
   * Returns a human-readable dump of the snapshot state, used for diagnostics only
   */
  @Contract(pure = true)
  fun dumpState(): String
}
