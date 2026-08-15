// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.util.TextRange
import com.intellij.util.text.ImmutableCharSequence
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

/**
 * Document chars + lineSet
 */
@ApiStatus.Internal
interface DocumentText {

  /**
   * @see DocumentEx.getImmutableCharSequence
   */
  @Contract(pure = true)
  fun chars(): ImmutableCharSequence

  /**
   * Same characters as [chars], but returns an already-cached [String] (faster `charAt`) when available.
   * Unlike [string], never forces [String] materialization.
   */
  @Contract(pure = true)
  fun cachedChars(): CharSequence

  /**
   * @see DocumentEx.getText
   */
  @Contract(pure = true)
  fun string(range: TextRange): String

  /**
   * Pure in visible effects, but discouraged: materializing a [String] copies the whole text (O(n)).
   * Prefer [chars] or [cachedChars] when a [CharSequence] is enough.
   *
   * @see DocumentEx.getText
   */
  @Contract(pure = true)
  fun string(): String

  /**
   * @see DocumentEx.getTextLength
   */
  @Contract(pure = true)
  fun length(): Int

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
   * @see DocumentEx.createLineIterator
   */
  @Contract(pure = true)
  fun lineIterator(): LineIterator

  /**
   * Returns snapshot with [patch] applied: the result's text is this snapshot's text after replacing
   * `[patch.startOffset, patch.endOffset)` with [DocumentTextPatch.newFragment].
   */
  @Contract(pure = true)
  fun withPatch(patch: DocumentTextPatch): DocumentText
}
