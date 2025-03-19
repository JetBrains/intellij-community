// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text

/**
 * Represents a fragment of document text with absolute character offset addressing.
 */
interface TextFragment {
  val fromChar: Long

  val toChar: Long

  fun text(from: Long, to: Long): String

  fun fragment(from: Long = this.fromChar, to: Long = this.toChar): TextFragment

  fun asCharSequence(): TextFragmentCharSequence

  override fun toString(): String

  val lengthL: Long
    get() = toChar - fromChar

  fun isEmpty(): Boolean =
    fromChar == toChar

  fun isNotEmpty(): Boolean =
    !isEmpty()

  fun subSequence(startIndex: Long, endIndex: Long = toChar - fromChar): TextFragment =
    fragment(fromChar + startIndex, fromChar + endIndex)

  val range: TextRange
    get() = TextRange(fromChar, toChar)
}