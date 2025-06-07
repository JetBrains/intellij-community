// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text

import andel.text.impl.*
import fleet.util.normalizeLineEndings
import fleet.util.serialization.DataSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.jvm.JvmStatic

internal class TextSerializer : DataSerializer<Text, String>(String.serializer()) {
  override fun fromData(data: String): Text = Text.fromString(data)

  override fun toData(value: Text): String = value.view().charSequence().toString()
}

/**
 * String-line immutable data structure suitable for efficient insertions and deletions of substrings.
 * In addition it allows for efficient line-based navigation. See [TextView]
 * */
@Serializable(TextSerializer::class)
class Text internal constructor(private val rope: TextRope) {
  /**
   * Number of lines in this text.
   * It is equal to number of newline character \n + 1
   * Empty text contains a single empty line.
   * Has O(1) complexity
   * */
  val lineCount: LineNumber
    get() = rope.linesCount.line

  /**
   * Number of UTF-16 characters in this text
   * Has O(1) complexity
   * */
  val charCount: Int
    get() = rope.charCount

  /**
   * Returns a [TextView] of this Text.
   * Has O(1) complexity
   * */
  fun view(): TextView =
    TextViewImpl(rope)

  /**
   * Returns a mutable version of [TextView]
   * Has O(1) complexity
   * */
  fun mutableView(): MutableTextView =
    TextViewImpl(rope)

  companion object {
    /**
     * Creates a Text out of passed String, leaving line separators as-is.
     * */
    @JvmStatic
    fun fromStringExact(text: String): Text =
      Text(TextMonoid.ropeOf(if (text.length > MAX_LEAF_SIZE) TextMonoid.split(text) else listOf(text)))

    /**
     * Constructs a new Text out of passed String, normalizing line endings to always be \n character
     * */
    @JvmStatic
    fun fromString(text: String): Text =
      fromStringExact(text.normalizeLineEndings())
  }

  override fun equals(other: Any?): Boolean =
    this === other || (other is Text && rope == other.rope)

  override fun hashCode(): Int =
    rope.hashCode() + 1
}
