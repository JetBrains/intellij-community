// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.ex.LineIterator
import com.intellij.openapi.util.TextRange
import com.intellij.util.text.CharArrayUtil
import com.intellij.util.text.ImmutableCharSequence
import java.lang.ref.SoftReference

internal class DocumentTextImpl private constructor(
  private val chars: ImmutableCharSequence,
  private var lineSet: LineSet?,                    // non-volatile intentionally, see getLineSet()
  private var cachedString: SoftReference<String>?, // non-volatile intentionally, see string()
) : DocumentText {

  constructor(chars: CharSequence) : this(
    chars = CharArrayUtil.createImmutableCharSequence(chars),
    lineSet = null,
    cachedString = null,
  )

  override fun chars(): ImmutableCharSequence {
    return chars
  }

  override fun cachedChars(): CharSequence {
    // TODO: use it in EditorPainter because String.charAt may improve performance during painting
    val string = cachedString?.get()
    if (string != null) {
      return string
    }
    return chars
  }

  override fun string(range: TextRange): String {
    val textInRange = chars.subSequence(range.startOffset, range.endOffset)
    return textInRange.toString()
  }

  /**
   * Lazy cache read/assigned without synchronization. Safe because [String] is a final-field immutable (JLS 17.5):
   * a racy reader sees either no value (and recomputes) or a fully constructed [String].
   */
  override fun string(): String {
    var string = cachedString?.get()
    if (string != null) {
      return string
    }
    string = chars.toString()
    cachedString = SoftReference(string)
    return string
  }

  override fun length(): Int {
    // TODO: hot method, optimize
    //  the length is constant, create a field length?
    return chars.length
  }

  override fun lineCount(): Int {
    val lineCount = getLineSet().lineCount
    assert(lineCount >= 0)
    return lineCount
  }

  override fun lineNumber(offset: Int): Int {
    return getLineSet().findLineIndex(offset)
  }

  override fun lineStartOffset(line: Int): Int {
    if (line == 0) {
      return 0 // otherwise, it would crash for the zero-length text
    }
    return getLineSet().getLineStart(line)
  }

  override fun lineEndOffset(line: Int): Int {
    if (line == 0 && length() == 0) {
      return 0
    }
    val lineSet = getLineSet()
    val result = lineSet.getLineEnd(line) - lineSet.getSeparatorLength(line)
    assert(result >= 0)
    return result
  }

  override fun lineSeparatorLength(line: Int): Int {
    val separatorLength = getLineSet().getSeparatorLength(line)
    assert(separatorLength >= 0)
    return separatorLength
  }

  override fun lineIterator(): LineIterator {
    return getLineSet().createIterator()
  }

  override fun withPatch(patch: DocumentTextPatch): DocumentTextImpl {
    val startOffset = patch.startOffset()
    val endOffset = patch.endOffset()
    val newFragment = patch.newFragment()
    val oldFragmentLength = endOffset - startOffset
    val newFragmentLength = newFragment.length
    val diff = newFragmentLength - oldFragmentLength
    val oldText = chars
    val oldTextLength = oldText.length
    val newText = updateText(startOffset, endOffset, oldTextLength, newFragment)
    val newTextLength = newText.length
    assert((oldTextLength + diff) == newTextLength) {
      "prevTextLength = " + oldTextLength +
      "; newFragmentLength = " + newFragmentLength +
      "; oldFragmentLength = " + oldFragmentLength +
      "; nextTextLength = " + newTextLength
    }
    val oldLineSet = getLineSet()
    val newLineSet = oldLineSet.update(
      oldText,
      startOffset,
      endOffset,
      newFragment,
    )
    assert(newTextLength == newLineSet.length) {
      "nextTextLength = " + newTextLength +
      "; nextLineSet.getLength() = " + newLineSet.length
    }
    return DocumentTextImpl(newText, newLineSet, null)
  }

  /**
   * Lazy cache read/assigned without synchronization. Safe because [LineSet] is a final-field immutable (JLS 17.5):
   * a racy reader sees either `null` (and recomputes) or a fully constructed instance.
   *
   * Performance-critical: [lineSet] backs the very frequently called [lineStartOffset]/[lineEndOffset],
   * so the field is intentionally non-volatile to avoid per-read volatile overhead on this hot path.
   */
  private fun getLineSet(): LineSet {
    var lineSet = this.lineSet
    if (lineSet != null) {
      return lineSet
    }
    lineSet = LineSet.createLineSet(chars)
    this.lineSet = lineSet
    return lineSet
  }

  private fun updateText(
    startOffset: Int,
    endOffset: Int,
    oldTextLength: Int,
    newFragment: CharSequence,
  ): ImmutableCharSequence {
    val canUseNewFragment = startOffset == 0 && endOffset == oldTextLength && newFragment is ImmutableCharSequence
    if (canUseNewFragment) {
      return newFragment
    }
    return chars.replace(startOffset, endOffset, newFragment)
  }

  private fun presentation(obj: Any?): String {
    if (obj == null) {
      return "null"
    }
    if (obj is SoftReference<*>) {
      return if (obj.get() == null) {
        "<null>"
      } else {
        "<not-null>"
      }
    }
    val hex = Integer.toHexString(System.identityHashCode(obj))
    return "@$hex"
  }

  override fun toString(): String {
    val id = presentation(this)
    val ch = presentation(chars)
    val ls = presentation(lineSet)
    val cs = presentation(cachedString)
    return "DocumentText" + id + '{' +
           "chars=" + ch +
           ", lineSet=" + ls +
           ", string=" + cs +
           '}'
  }
}
