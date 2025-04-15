// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher

import kotlin.math.max
import kotlin.math.min

/**
 * A text range defined by start and end (exclusive) offset.
 *
 * @see ProperTextRange
 *
 * @see com.intellij.util.text.TextRangeUtil
 */
open class TextRange protected constructor(override val startOffset: Int, override val endOffset: Int, checkForProperTextRange: Boolean) : Segment {
  /**
   * @see .create
   * @see .from
   * @see .allOf
   */
  constructor(startOffset: Int, endOffset: Int) : this(startOffset, endOffset, true)

  /**
   * @param checkForProperTextRange `true` if offsets should be checked by [.assertProperRange]
   * @see UnfairTextRange
   */
  init {
    if (checkForProperTextRange) {
      assertProperRange(this)
    }
  }

  val length: Int
    get() = this.endOffset - this.startOffset

  override fun equals(obj: Any?): Boolean {
    if (obj !is TextRange) return false
    val range = obj
    return this.startOffset == range.startOffset && this.endOffset == range.endOffset
  }

  override fun hashCode(): Int {
    return this.startOffset + this.endOffset
  }

  /**
   * Checks if the specified text range is fully contained within this TextRange.
   *
   * @param range the text range to check for containment
   * @return `true` if the specified text range is contained within this object,
   * `false` otherwise
   */
  fun contains(range: TextRange): Boolean {
    return contains(range as Segment)
  }

  /**
   * Checks if the given segment is fully contained within this TextRange.
   *
   * @param segment the segment to be checked for containment
   * @return true if the given range is contained within this segment, false otherwise
   */
  fun contains(segment: Segment): Boolean {
    return containsRange(segment.startOffset, segment.endOffset)
  }

  /**
   * Checks if the given range is fully contained within this TextRange.
   *
   * @param startOffset the start offset of the range to check
   * @param endOffset the end offset of the range to check
   * @return true if the given range is contained within this TextRange, false otherwise
   */
  fun containsRange(startOffset: Int, endOffset: Int): Boolean {
    return this.startOffset <= startOffset && endOffset <= this.endOffset
  }

  /**
   * Checks if the given offset is contained within the range
   * (unlike [.contains], offset at the end of the range is considered to be inside).
   *
   * @param offset the offset to check
   * @return true if the given offset is within the range, false otherwise
   * @see .contains
   */

  fun containsOffset(offset: Int): Boolean {
    return this.startOffset <= offset && offset <= this.endOffset
  }

  override fun toString(): String {
    return "(" + this.startOffset + "," + this.endOffset + ")"
  }

  /**
   * Checks if the given offset is contained within the range
   * (unlike [.containsOffset], offset at the end of the range is considered to be outside).
   *
   * @param offset the offset to check
   * @return true if the given offset is within the range, false otherwise
   * @see .containsOffset
   */

  fun contains(offset: Int): Boolean {
    return this.startOffset <= offset && offset < this.endOffset
  }

  /**
   * Returns a substring of the given string that is covered by this TextRange.
   *
   * @param str the string from which the substring will be extracted
   * @return the substring
   */
  fun substring(str: String): String {
    return str.substring(this.startOffset, this.endOffset)
  }


  fun subSequence(str: CharSequence): CharSequence {
    return str.subSequence(this.startOffset, this.endOffset)
  }


  fun cutOut(subRange: TextRange): TextRange {
    require(subRange.startOffset <= this.length) { "SubRange: $subRange; this=$this" }
    require(subRange.endOffset <= this.length) { "SubRange: $subRange; this=$this" }
    assertProperRange(subRange)
    return TextRange(this.startOffset + subRange.startOffset,
                     min(endOffset.toDouble(), (this.startOffset + subRange.endOffset).toDouble()).toInt())
  }


  open fun shiftRight(delta: Int): TextRange {
    if (delta == 0) return this
    return TextRange(this.startOffset + delta, this.endOffset + delta)
  }


  fun shiftLeft(delta: Int): TextRange {
    if (delta == 0) return this
    return TextRange(this.startOffset - delta, this.endOffset - delta)
  }


  fun grown(lengthDelta: Int): TextRange {
    if (lengthDelta == 0) {
      return this
    }
    return from(this.startOffset, this.length + lengthDelta)
  }


  fun replace(original: String, replacement: String): String {
    val beginning = original.substring(0, this.startOffset)
    val ending = original.substring(this.endOffset)
    return beginning + replacement + ending
  }


  fun intersects(textRange: TextRange): Boolean {
    return intersects(textRange as Segment)
  }


  fun intersects(textRange: Segment): Boolean {
    return intersects(textRange.startOffset, textRange.endOffset)
  }


  fun intersects(startOffset: Int, endOffset: Int): Boolean {
    return max(startOffset.toDouble(), startOffset.toDouble()) <= min(endOffset.toDouble(), endOffset.toDouble())
  }


  fun intersectsStrict(textRange: TextRange): Boolean {
    return intersectsStrict(textRange.startOffset, textRange.endOffset)
  }


  fun intersectsStrict(startOffset: Int, endOffset: Int): Boolean {
    return max(startOffset.toDouble(), startOffset.toDouble()) < min(endOffset.toDouble(), endOffset.toDouble())
  }


  fun intersection(range: TextRange): TextRange? {
    if (equals(range)) {
      return this
    }
    val newStart = max(startOffset.toDouble(), range.startOffset.toDouble()).toInt()
    val newEnd = min(endOffset.toDouble(), range.endOffset.toDouble()).toInt()
    return if (isProperRange(newStart, newEnd)) TextRange(newStart, newEnd) else null
  }

  val isEmpty: Boolean
    get() = this.startOffset >= this.endOffset


  fun union(textRange: TextRange): TextRange {
    if (equals(textRange)) {
      return this
    }
    return TextRange(min(startOffset.toDouble(), textRange.startOffset.toDouble()).toInt(), max(
      endOffset.toDouble(), textRange.endOffset.toDouble()).toInt())
  }


  fun equalsToRange(startOffset: Int, endOffset: Int): Boolean {
    return startOffset == this.startOffset && endOffset == this.endOffset
  }

  companion object {
    private const val serialVersionUID = -670091356599757430L

    val EMPTY_RANGE: TextRange = TextRange(0, 0)
    val EMPTY_ARRAY: Array<TextRange?> = arrayOfNulls<TextRange>(0)


    fun containsRange(outer: Segment, inner: Segment): Boolean {
      return outer.startOffset <= inner.startOffset && inner.endOffset <= outer.endOffset
    }


    fun from(offset: Int, length: Int): TextRange {
      return create(offset, offset + length)
    }


    fun create(startOffset: Int, endOffset: Int): TextRange {
      return TextRange(startOffset, endOffset)
    }


    fun create(segment: Segment): TextRange {
      return create(segment.startOffset, segment.endOffset)
    }


    fun areSegmentsEqual(segment1: Segment, segment2: Segment): Boolean {
      return segment1.startOffset == segment2.startOffset
             && segment1.endOffset == segment2.endOffset
    }


    fun allOf(s: String): TextRange {
      return TextRange(0, s.length)
    }

    @Throws(AssertionError::class)
    fun assertProperRange(range: Segment) {
      assertProperRange(range, "")
    }

    @Throws(AssertionError::class)
    fun assertProperRange(range: Segment, message: Any) {
      assertProperRange(range.startOffset, range.endOffset, message)
    }

    fun assertProperRange(startOffset: Int, endOffset: Int, message: Any) {
      require(isProperRange(startOffset, endOffset)) { "Invalid range specified: ($startOffset, $endOffset); $message" }
    }

    fun isProperRange(startOffset: Int, endOffset: Int): Boolean {
      return startOffset <= endOffset && startOffset >= 0
    }
  }
}
