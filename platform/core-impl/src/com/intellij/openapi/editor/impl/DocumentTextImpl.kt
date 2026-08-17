// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentTextOp
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

  override fun applyOp(op: DocumentTextOp): DocumentText {
    return when(op) {
      is DocumentTextOp.Insert -> applyInsert(op)
      is DocumentTextOp.Delete -> applyDelete(op)
    }
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

  private fun applyInsert(op: DocumentTextOp.Insert): DocumentText {
    val offset = op.offset()
    val newFragment = op.fragment()
    if (newFragment.isEmpty()) {
      return this
    }
    val oldText = chars
    val canReuseFragment = (offset == 0 && oldText.isEmpty() && newFragment is ImmutableCharSequence)
    val newText = if (canReuseFragment) {
      newFragment
    } else {
      oldText.insert(offset, newFragment)
    }
    val oldLineSet = this.lineSet
    if (oldLineSet == null) {
      return DocumentTextImpl(newText, null, null)
    }
    val newLineSet = oldLineSet.update(
      oldText,
      offset,
      offset,
      newFragment,
    )
    assert(newLineSet.length == newText.length) {
      "LineSet length mismatch: $newLineSet != $newText"
    }
    return DocumentTextImpl(newText, newLineSet, null)
  }

  private fun applyDelete(op: DocumentTextOp.Delete): DocumentText {
    val offset = op.offset()
    val length = op.length()
    if (length == 0) {
      return this
    }
    val oldText = chars
    val newText = oldText.delete(offset, offset + length)
    val oldLineSet = this.lineSet
    if (oldLineSet == null) {
      return DocumentTextImpl(newText, null, null)
    }
    val newLineSet = oldLineSet.update(
      oldText,
      offset,
      offset + length,
      "",
    )
    assert(newLineSet.length == newText.length) {
      "LineSet length mismatch: $newLineSet != $newText"
    }
    return DocumentTextImpl(newText, newLineSet, null)
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
