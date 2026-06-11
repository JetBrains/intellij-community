// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.LineIterator
import com.intellij.openapi.util.TextRange
import com.intellij.util.text.CharArrayUtil
import com.intellij.util.text.ImmutableCharSequence
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import java.lang.ref.SoftReference

internal class DocumentSnapshotImpl private constructor(
  private val text: ImmutableCharSequence,
  private val modStamp: Long,
  private val modSequence: Int,
  private var lineSet: LineSet?,                  // non-volatile intentionally, see getLineSet()
  private var textString: SoftReference<String>?, // non-volatile intentionally, see string()
) : DocumentSnapshot {

  constructor(chars: CharSequence) : this(
    text = CharArrayUtil.createImmutableCharSequence(chars),
    modStamp = DocumentModStamp.next(),
    modSequence = 0,
    lineSet = null,
    textString = null,
  )

  override fun text(): ImmutableCharSequence {
    return text
  }

  override fun charSequence(): CharSequence {
    // TODO: use it in EditorPainter because String.charAt may improve performance during painting
    val string = textString?.get()
    if (string != null) {
      return string
    }
    return text
  }

  override fun string(range: TextRange): String {
    val textInRange = text.subSequence(range.startOffset, range.endOffset)
    return textInRange.toString()
  }

  /**
   * Lazy cache read/assigned without synchronization. Safe because [String] is a final-field immutable (JLS 17.5):
   * a racy reader sees either no value (and recomputes) or a fully constructed [String].
   *
   * Unlike [lineSet], [textString] is usually not performance-critical, so it could be a `volatile` field with a
   * double-checked approach. It is kept non-volatile only to stay consistent with the similar [lineSet] field.
   */
  override fun string(): String {
    var string = textString?.get()
    if (string != null) {
      return string
    }
    string = text.toString()
    textString = SoftReference(string)
    return string
  }

  override fun textLength(): Int {
    // TODO: hot method, optimize
    //  the length is constant, create a field textLength?
    return text.length
  }

  override fun modStamp(): Long {
    return modStamp
  }

  override fun modSequence(): Int {
    return modSequence
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
    if (line == 0 && textLength() == 0) {
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

  override fun isLineModified(line: Int): Boolean {
    val lineSet = this.lineSet
    return lineSet != null && lineSet.isModified(line)
  }

  override fun lineIterator(): LineIterator {
    return getLineSet().createIterator()
  }

  override fun dumpState(): String {
    val dump = StringBuilder()
    dump.append("intervals:\n")
    val lineCount: Int = lineCount()
    for (line in 0..<lineCount) {
      dump
        .append(line)
        .append(": ")
        .append(lineStartOffset(line))
        .append("-")
        .append(lineEndOffset(line))
        .append(", ")
    }
    if (lineCount > 0) {
      dump.setLength(dump.length - 2)
    }
    return dump.toString()
  }

  override fun withModStamp(newModStamp: Long, incrementModSeq: Boolean): DocumentSnapshot {
    val newModSequence = if (incrementModSeq) nextModSequence() else modSequence
    if (modStamp == newModStamp && modSequence == newModSequence) {
      return this
    }
    return DocumentSnapshotImpl(text, newModStamp, newModSequence, lineSet, textString)
  }

  override fun withClearedLineFlags(
    startLine: Int,
    endLine: Int,
    exceptLines: IntArray,
  ): DocumentSnapshotImpl {
    if (this.lineSet == null) {
      // there were no text changes if line set is not created yet
      return this
    }
    var lineSet = getLineSet()
    val modifiedLines: IntList
    if (exceptLines.isEmpty()) {
      modifiedLines = EMPTY_INDICES
    } else {
      modifiedLines = IntArrayList(exceptLines.size)
      for (line in exceptLines) {
        // TODO: why line < 0 || line >= lineSet.lineCount
        //  silently ignored not IndexOutOfBoundsException?
        if (0 <= line && line < lineSet.lineCount) {
          if (lineSet.isModified(line)) {
            modifiedLines.add(line)
          }
        }
      }
    }
    lineSet = lineSet.clearModificationFlags(startLine, endLine)
    lineSet = lineSet.setModified(modifiedLines)
    return withLineSet(lineSet)
  }

  override fun withMetadata(metadata: DocumentSnapshot): DocumentSnapshot {
    if (this === metadata) {
      return this
    }
    if (this.text === metadata.text()) {
      return metadata
    }
    // discard metadata.text, see doc [com.intellij.openapi.editor.ex.DocumentMutator]
    return DocumentSnapshotImpl(
      this.text,
      metadata.modStamp(),
      metadata.modSequence(),
      this.lineSet,
      this.textString,
    )
  }

  override fun withText(
    newWholeText: ImmutableCharSequence,
    startOffset: Int,
    endOffset: Int,
    newFragment: CharSequence,
    newModStamp: Long,
    wholeTextReplaced: Boolean,
    clearLineFlags: Boolean,
  ): DocumentSnapshotImpl {
    val oldFragmentLength = endOffset - startOffset
    val newFragmentLength = newFragment.length
    val diff = newFragmentLength - oldFragmentLength
    val oldText = text
    val oldTextLength = oldText.length
    val newTextLength = newWholeText.length
    assert((oldTextLength + diff) == newTextLength) {
      "prevTextLength = " + oldTextLength +
      "; event.getNewLength() = " + newFragmentLength +
      "; event.getOldLength() = " + oldFragmentLength +
      "; nextTextLength = " + newTextLength
    }
    val oldLineSet = getLineSet()
    var newLineSet = oldLineSet.update(
      oldText,
      startOffset,
      endOffset,
      newFragment,
      wholeTextReplaced,
    )
    assert(newTextLength == newLineSet.length) {
      "nextTextLength = " + newTextLength +
      "; nextLineSet.getLength() = " + newLineSet.length
    }
    if (clearLineFlags) {
      newLineSet = newLineSet.clearModificationFlags(0, Int.MAX_VALUE)
    }
    val newModSequence = nextModSequence()
    return DocumentSnapshotImpl(newWholeText, newModStamp, newModSequence, newLineSet, null)
  }

  private fun withLineSet(newLineSet: LineSet?): DocumentSnapshotImpl {
    if (this.lineSet === newLineSet) {
      return this
    }
    return DocumentSnapshotImpl(text, modStamp, modSequence, newLineSet, textString)
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
    lineSet = LineSet.createLineSet(text)
    this.lineSet = lineSet
    return lineSet
  }

  private fun nextModSequence(): Int {
    return modSequence + 1
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
    val ms = modStamp
    val mq = modSequence
    val tx = presentation(text)
    val ls = presentation(lineSet)
    val st = presentation(textString)
    return "DocumentSnapshot" + id + '{' +
           "modStamp=" + ms +
           ", modSequence=" + mq +
           ", text=" + tx +
           ", lineSet=" + ls +
           ", string=" + st +
           '}'
  }

  companion object {
    private val EMPTY_INDICES: IntList = IntArrayList(0)
  }
}
