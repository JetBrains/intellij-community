// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.editor.impl.DocumentTextImpl
import com.intellij.util.text.ImmutableCharSequence
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

/**
 * An immutable text with an index of its lines.
 *
 * A text never changes, so any thread can read it without a lock. Texts compare by identity.
 * A text does not change when a caller later changes a [CharSequence] that it passed in.
 *
 * A line ends with a line separator: `\n`, `\r` or `\r\n`. A separator belongs to the line that it ends.
 * The last line has no separator, so a text that ends with a separator ends with an empty line.
 * The empty text has no lines. Still, line 0 starts and ends at offset 0 with no separator, and offset 0 is on line 0.
 *
 * A method throws [IndexOutOfBoundsException] for an offset, a range or a line outside the text.
 */
@ApiStatus.Experimental
interface DocumentText {

  /**
   * Returns the characters of the text.
   */
  @Contract(pure = true)
  fun chars(): ImmutableCharSequence

  /**
   * Returns the same characters as [chars]. The result can give faster random access.
   * Do not rely on its type or its identity.
   */
  @Contract(pure = true)
  fun luckyChars(): CharSequence

  /**
   * Returns the characters in `[startOffset, endOffset)` as a [String].
   */
  @Contract(pure = true)
  fun string(startOffset: Int, endOffset: Int): String

  /**
   * Returns the whole text as a [String].
   * The call can copy the whole text, so it costs time and memory for a large text.
   * Prefer [chars] or [luckyChars] when a [CharSequence] is enough.
   */
  @Contract(pure = true)
  fun string(): String

  /**
   * Returns the number of characters.
   */
  @Contract(pure = true)
  fun length(): Int

  /**
   * Returns the number of lines.
   */
  @Contract(pure = true)
  fun lineCount(): Int

  /**
   * Returns the line that contains [offset].
   */
  @Contract(pure = true)
  fun lineNumber(offset: Int): Int

  /**
   * Returns the offset where [line] starts.
   */
  @Contract(pure = true)
  fun lineStartOffset(line: Int): Int

  /**
   * Returns the offset where [line] ends, without its line separator.
   */
  @Contract(pure = true)
  fun lineEndOffset(line: Int): Int

  /**
   * Returns the length of the line separator at the end of [line].
   */
  @Contract(pure = true)
  fun lineSeparatorLength(line: Int): Int

  /**
   * Returns a new iterator over the lines of the text.
   */
  @Contract(pure = true)
  fun lineIterator(): LineIterator

  /**
   * Returns a new text with [fragment] inserted at [offset], or this text when [fragment] is empty.
   */
  @Contract(pure = true)
  fun insert(offset: Int, fragment: CharSequence): DocumentText

  /**
   * Returns a new text without the characters in `[startOffset, endOffset)`, or this text when the range is empty.
   */
  @Contract(pure = true)
  fun delete(startOffset: Int, endOffset: Int): DocumentText

  /**
   * Returns a new text with the characters in `[startOffset, endOffset)` replaced by [fragment],
   * or this text when the range and [fragment] are both empty.
   */
  @Contract(pure = true)
  fun replace(startOffset: Int, endOffset: Int, fragment: CharSequence): DocumentText

  companion object {
    /**
     * Returns a text with the characters of [chars].
     */
    @JvmStatic
    fun createText(chars: CharSequence): DocumentText {
      return DocumentTextImpl(chars)
    }
  }
}
