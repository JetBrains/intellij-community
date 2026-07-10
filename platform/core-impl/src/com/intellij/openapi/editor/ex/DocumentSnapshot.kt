// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.editor.impl.modTree.ModificationTree
import com.intellij.openapi.util.TextRange
import com.intellij.util.text.ImmutableCharSequence
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

/**
 * Immutable self-consistent snapshot of document content (text + metadata).
 *
 * Metadata: [modStamp], [modSequence], [isLineModified].
 * The rest -- text.
 *
 * Metadata is used to track the "timeline" of the text.
 */
@ApiStatus.Internal
interface DocumentSnapshot {

  /**
   * @see DocumentEx.getImmutableCharSequence
   */
  @Contract(pure = true)
  fun text(): ImmutableCharSequence

  /**
   * Same characters as [text], but returns an already-cached [String] (faster `charAt`) when available.
   * Unlike [string], never forces [String] materialization.
   */
  @Contract(pure = true)
  fun charSequence(): CharSequence

  /**
   * @see DocumentEx.getText
   */
  @Contract(pure = true)
  fun string(range: TextRange): String

  /**
   * Pure in visible effects, but discouraged: materializing a [String] copies the whole text (O(n)).
   * Prefer [text] or [charSequence] when a [CharSequence] is enough.
   *
   * @see DocumentEx.getText
   */
  @Contract(pure = true)
  fun string(): String

  /**
   * @see DocumentEx.getTextLength
   */
  @Contract(pure = true)
  fun textLength(): Int

  /**
   * Part of the document metadata tracking text timeline
   *
   * @see DocumentEx.getModificationStamp
   */
  @Contract(pure = true)
  fun modStamp(): Long

  /**
   * Part of the document metadata tracking text timeline.
   * Always increases from snapshot to snapshot if text is changed.
   *
   * @see DocumentEx.getModificationSequence
   */
  @Contract(pure = true)
  fun modSequence(): Int

  /**
   * @see DocumentEx.getLineCount
   */
  @Contract(pure = true)
  fun lineCount(): Int

  /**
   * @see DocumentEx.getLineNumber
   */
  @Contract(pure = true)
  fun lineNumber(offset: Int): Int

  /**
   * @see DocumentEx.getLineStartOffset
   */
  @Contract(pure = true)
  fun lineStartOffset(line: Int): Int

  /**
   * @see DocumentEx.getLineEndOffset
   */
  @Contract(pure = true)
  fun lineEndOffset(line: Int): Int

  /**
   * @see DocumentEx.getLineSeparatorLength
   */
  @Contract(pure = true)
  fun lineSeparatorLength(line: Int): Int

  /**
   * Part of the document metadata tracking text timeline
   *
   * @see DocumentEx.isLineModified
   */
  @Contract(pure = true)
  fun isLineModified(line: Int): Boolean

  /**
   * @see DocumentEx.createLineIterator
   */
  @Contract(pure = true)
  fun lineIterator(): LineIterator

  @Contract(pure = true)
  fun modTree(): ModificationTree

  @Contract(pure = true)
  fun dumpState(): String

  /**
   * Returns snapshot with specified `newModStamp`.
   *
   * @param incrementModSeq whether [modSequence] should be incremented
   */
  @Contract(pure = true)
  fun withModStamp(newModStamp: Long, incrementModSeq: Boolean): DocumentSnapshot

  /**
   * Returns snapshot with cleared specified line flags.
   *
   * @param endLine is exclusive. Two special values `0` and `Int.MAX_VALUE` ignoring range checks
   */
  @Contract(pure = true)
  fun withClearedLineFlags(startLine: Int, endLine: Int, exceptLines: IntArray): DocumentSnapshot

  /**
   * Returns snapshot with the same text and metadata from the other snapshot.
   * This method is used to preserve the semantics of metadata being a tracker of text timeline
   *
   * @param metadata latest version of the document
   */
  @Contract(pure = true)
  fun withMetadata(metadata: DocumentSnapshot): DocumentSnapshot

  /**
   * Returns snapshot with specified text and metadata.
   * [newWholeText] should be consistent with the rest parameters:
   * `newWholeText == snapshot.text.replace(startOffset, endOffset, newFragment)`.
   *
   * In the current implementation it is hard to remove "redundant" [newWholeText]
   * because [DocumentEx.moveText] precomputes it
   */
  @Contract(pure = true)
  fun withText(
    newWholeText: ImmutableCharSequence,
    startOffset: Int,
    endOffset: Int,
    newFragment: CharSequence,
    newModStamp: Long,
    wholeTextReplaced: Boolean,
    clearLineFlags: Boolean,
    clearModTree: Boolean,
  ): DocumentSnapshot
}
