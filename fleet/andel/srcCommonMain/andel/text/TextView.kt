// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text

/**
 * Accessor object for [Text]. It leverages locality by amortizing Rope scans, which makes repeated local access cheaper.
 * The object is thus stateful, but nevertheless thread-safe.
 * */
interface TextView {

  /**
   * Number of UTF-16 characters in this text
   * Has O(1) complexity
   * */
  val charCount: Int

  /**
   * Number of lines in this text.
   * It is equal to number of newline character \n + 1
   * Empty text contains a single empty line.
   * Has O(1) complexity
   * */
  val lineCount: LineNumber

  /**
   * Returns a UTF-16 character at specified offset.
   * Has O(log N) complexity
   * */
  operator fun get(offset: Int): Char

  /**
   * Builds a substring of the text, starting with a character at [from], spanning until [to], not including offset at [to].
   * */
  fun string(from: Int, to: Int): String

  /**
   * Returns a line number at given [offset].
   * It is defined as number of line separators (\n) preceding the [offset]
   * Has O(log N) complexity
   * */
  fun lineAt(offset: Int): LineNumber

  /**
   * Returns the start offset of the line.
   * Has O(log N) complexity.
   * */
  fun lineStartOffset(lineNumber: LineNumber): Int

  /**
   * Returns the corresponding [Text] object.
   * */
  fun text(): Text
}
