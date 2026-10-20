// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentModState
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText

/**
 * A [DocumentModState] that keeps the modified positions of [text] in [ModifiedPositions], so an op
 * needs no line data.
 *
 * A text of length `n` has `n + 1` positions. A line is modified when a position from the line start
 * to its separator is modified. These are the lines that [ModifiedLineSet] marks for the same ops.
 * Only [isLineModified] and [DocumentOp.UnmodifiedLines] read line numbers, and they read them from a
 * copy of [text], so that [text] builds no line data.
 *
 * [lastLineHasSeparator] follows the same flag of [ModifiedLineSet]. It decides whether an empty last
 * line can be modified, and a change inside one line keeps it even when the text then ends with a
 * separator.
 */
internal class RangeModState private constructor(
  private val stamp: Long,
  private val sequence: Int,
  private val text: DocumentText,
  private val edits: Edits,
  private val lastLineHasSeparator: Boolean,
) : DocumentModState {

  /**
   * The copy of [text] that answers line queries. Non-volatile on purpose: a racy reader makes its own
   * copy, which is equal.
   */
  private var cachedLineText: DocumentText? = null

  override fun stamp(): Long {
    return stamp
  }

  override fun sequence(): Int {
    return sequence
  }

  override fun isLineModified(line: Int): Boolean {
    return when (edits) {
      Edits.None -> false
      is Edits.Some -> isModified(edits.positions, lineText(), line)
    }
  }

  override fun applyOp(before: DocumentText, after: DocumentText, op: DocumentOp): DocumentModState {
    return when (op) {
      is DocumentOp.Insert -> applyTextChange(before, after, op.offset(), 0, op.fragment())
      is DocumentOp.Delete -> applyTextChange(before, after, op.offset(), op.length(), "")
      is DocumentOp.ModStamp -> applyModStamp(op)
      is DocumentOp.UnmodifiedLines -> applyUnmodifiedLines(op)
    }
  }

  private fun isModified(positions: ModifiedPositions, lines: DocumentText, line: Int): Boolean {
    checkLine(lines, line)
    if (isLastEmptyLine(lines, line)) {
      return false
    }
    return positions.hasModified(lines.lineStartOffset(line), lines.lineEndOffset(line))
  }

  /**
   * Whether [line] is the empty line after a final separator, which is never modified.
   */
  private fun isLastEmptyLine(lines: DocumentText, line: Int): Boolean {
    return lastLineHasSeparator && line == lines.lineCount() - 1
  }

  /**
   * The state after a replace of [removed] characters at [offset] by [inserted].
   */
  private fun applyTextChange(
    before: DocumentText,
    after: DocumentText,
    offset: Int,
    removed: Int,
    inserted: CharSequence,
  ): DocumentModState {
    if (removed == 0 && inserted.isEmpty()) {
      return this
    }
    val oldChars = before.chars()
    val newChars = after.chars()
    val change = widen(oldChars, newChars, offset, removed, inserted.length)
    val oldPositions = when (edits) {
      Edits.None -> ModifiedPositions.clean(oldChars.length + 1)
      is Edits.Some -> edits.positions
    }
    val newPositions = oldPositions.splice(change.start, change.end, change.length + 1)
    checkPositions(newPositions, newChars)
    return RangeModState(
      stamp = stamp,
      sequence = sequence + 1,
      text = after,
      edits = Edits.Some(newPositions),
      lastLineHasSeparator = lastLineHasSeparatorAfter(oldChars, newChars, change, inserted),
    )
  }

  /**
   * The flag of [ModifiedLineSet] after [change] of [inserted]: a change inside one line keeps it, and
   * any other change takes it from the new end of the text.
   */
  private fun lastLineHasSeparatorAfter(
    oldChars: CharSequence,
    newChars: CharSequence,
    change: Change,
    inserted: CharSequence,
  ): Boolean {
    val isWholeTextDelete = change.start == 0 && change.end == oldChars.length && change.length == 0
    // A change widened over a "\r\n" pair replaces a line break, so it joins lines.
    val joinsLines = oldChars.containsLineBreak(change.start, change.end)
    val addsLines = inserted.containsLineBreak(0, inserted.length)
    val startsOnLastEmptyLine = lastLineHasSeparator && change.start == oldChars.length
    val isSingleLineChange = !isWholeTextDelete && !joinsLines && !addsLines && !startsOnLastEmptyLine
    if (isSingleLineChange || change.end < oldChars.length) {
      return lastLineHasSeparator
    }
    if (change.length > 0) {
      return isLineBreak(newChars[change.start + change.length - 1])
    }
    return change.start > 0 && isLineBreak(oldChars[change.start - 1])
  }

  private fun applyModStamp(op: DocumentOp.ModStamp): DocumentModState {
    val newSequence = if (op.incSequence()) {
      sequence + 1
    } else {
      sequence
    }
    val newStamp = op.modStamp()
    if (newStamp == stamp && newSequence == sequence) {
      return this
    }
    return RangeModState(
      stamp = newStamp,
      sequence = newSequence,
      text = text,
      edits = edits,
      lastLineHasSeparator = lastLineHasSeparator,
    )
  }

  /**
   * The state with the lines of [op] cleared, as [ModifiedLineSet.clearModificationFlags] checks and
   * clears them. An except line keeps its flag. A clear that changes no flag returns this state.
   */
  private fun applyUnmodifiedLines(op: DocumentOp.UnmodifiedLines): DocumentModState {
    val positions = when (edits) {
      Edits.None -> return this
      is Edits.Some -> edits.positions
    }
    val lines = lineText()
    val clearedLines = clearedLines(lines, op.startLine(), op.endLine())
    val exceptLines = op.exceptLines()
    if (clearsNoFlag(positions, lines, clearedLines, exceptLines)) {
      return this
    }
    val lineCount = lines.lineCount()
    val keptLines = exceptLines.filter { line ->
      line in 0 until lineCount && isModified(positions, lines, line)
    }
    val from = lines.lineStartOffset(clearedLines.first)
    val to = lines.lineEndOffset(clearedLines.last)
    var cleared = positions.fill(from, to, modified = false)
    for (line in keptLines) {
      cleared = cleared.fill(lines.lineStartOffset(line), lines.lineEndOffset(line), modified = true)
    }
    return RangeModState(
      stamp = stamp,
      sequence = sequence,
      text = text,
      edits = Edits.Some(cleared),
      lastLineHasSeparator = lastLineHasSeparator,
    )
  }

  /**
   * The lines that a clear from [startLine] to [endLine] covers. Fails for a range that [lines] does not
   * hold, as [ModifiedLineSet.clearModificationFlags] does.
   */
  private fun clearedLines(lines: DocumentText, startLine: Int, endLine: Int): IntRange {
    val lineCount = lines.lineCount()
    require(startLine <= endLine) {
      "endLine < startLine: $endLine < $startLine; lineCount: $lineCount"
    }
    val isWholeEmptyText = endLine == 0 || endLine == Int.MAX_VALUE
    if (lineCount == 0 && startLine == 0 && isWholeEmptyText) {
      return IntRange.EMPTY
    }
    checkLine(lines, startLine)
    val clearedEnd = if (endLine == Int.MAX_VALUE) lineCount else endLine
    checkLine(lines, clearedEnd - 1)
    return startLine until clearedEnd
  }

  /**
   * Whether a clear of [clearedLines] keeps every flag, because each modified line among them is one of
   * [exceptLines]. It reads the positions that the clear sets clean, so it never misses a change.
   */
  private fun clearsNoFlag(
    positions: ModifiedPositions,
    lines: DocumentText,
    clearedLines: IntRange,
    exceptLines: IntArray,
  ): Boolean {
    val exceptLinesInRange = exceptLines.filter { line -> line in clearedLines }.distinct().sorted()
    var gapStart = clearedLines.first
    for (exceptLine in exceptLinesInRange) {
      if (gapStart < exceptLine && hasModifiedLines(positions, lines, gapStart, exceptLine - 1)) {
        return false
      }
      gapStart = exceptLine + 1
    }
    if (gapStart > clearedLines.last) {
      return true
    }
    return !hasModifiedLines(positions, lines, gapStart, clearedLines.last)
  }

  /**
   * Whether a position from the start of [firstLine] to the separator of [lastLine] is modified.
   */
  private fun hasModifiedLines(
    positions: ModifiedPositions,
    lines: DocumentText,
    firstLine: Int,
    lastLine: Int,
  ): Boolean {
    return positions.hasModified(lines.lineStartOffset(firstLine), lines.lineEndOffset(lastLine))
  }

  /**
   * A copy of [text] that holds the line data. A text with line data updates it on every later op, so
   * [text] itself must not build any.
   */
  private fun lineText(): DocumentText {
    var copy = cachedLineText
    if (copy == null) {
      copy = DocumentText.createText(text.chars())
      cachedLineText = copy
    }
    return copy
  }

  /**
   * The two states of the modified positions: [None] before the first text op, as [ModifiedLineSet]
   * does not exist then, and [Some] after it.
   */
  private sealed interface Edits {
    object None : Edits

    class Some(val positions: ModifiedPositions) : Edits
  }

  /**
   * A replace of the old positions from [start] to [end] by [length] new characters.
   */
  private class Change(val start: Int, val end: Int, val length: Int)

  companion object {
    /**
     * The mod state of a new document with [text]: a new stamp, the sequence 0, and no modified lines.
     */
    fun create(text: DocumentText): RangeModState {
      return RangeModState(
        stamp = DocumentModStamp.next(),
        sequence = 0,
        text = text,
        edits = Edits.None,
        lastLineHasSeparator = endsWithLineBreak(text.chars()),
      )
    }

    /**
     * The replace of [removed] characters at [offset] by [inserted] characters, widened by one
     * character on a side where it breaks or makes a `"\r\n"` pair, as in [ModifiedLineSet.update].
     */
    private fun widen(
      oldChars: CharSequence,
      newChars: CharSequence,
      offset: Int,
      removed: Int,
      inserted: Int,
    ): Change {
      var start = offset
      var end = offset + removed
      var length = inserted
      if (breaksPairAtStart(oldChars, newChars, start)) {
        start--
        length++
      }
      if (breaksPairAtEnd(oldChars, newChars, end, start + length - 1)) {
        end++
        length++
      }
      return Change(start, end, length)
    }

    /**
     * Whether a change at [start] breaks or makes a `"\r\n"` pair whose `'\r'` lies before [start].
     */
    private fun breaksPairAtStart(oldChars: CharSequence, newChars: CharSequence, start: Int): Boolean {
      if (!oldChars.hasChar(start - 1, '\r')) {
        return false
      }
      return oldChars.hasChar(start, '\n') || newChars.hasChar(start, '\n')
    }

    /**
     * Whether a change that ends at [end] breaks or makes a `"\r\n"` pair whose `'\n'` lies at [end].
     * [lastReplaced] is the new offset of the last replaced character.
     */
    private fun breaksPairAtEnd(
      oldChars: CharSequence,
      newChars: CharSequence,
      end: Int,
      lastReplaced: Int,
    ): Boolean {
      if (!oldChars.hasChar(end, '\n')) {
        return false
      }
      return oldChars.hasChar(end - 1, '\r') || newChars.hasChar(lastReplaced, '\r')
    }

    private fun checkPositions(positions: ModifiedPositions, chars: CharSequence) {
      require(positions.size() == chars.length + 1) {
        "The positions do not fit the text: ${positions.size()} positions, ${chars.length} chars"
      }
    }

    private fun checkLine(lines: DocumentText, line: Int) {
      val lineCount = lines.lineCount()
      if (line !in 0 until lineCount) {
        throw IndexOutOfBoundsException("Wrong line: $line. Available lines count: $lineCount")
      }
    }

    private fun endsWithLineBreak(chars: CharSequence): Boolean {
      return chars.isNotEmpty() && isLineBreak(chars[chars.length - 1])
    }

    private fun CharSequence.containsLineBreak(from: Int, to: Int): Boolean {
      for (index in from until to) {
        if (isLineBreak(this[index])) {
          return true
        }
      }
      return false
    }

    private fun isLineBreak(char: Char): Boolean {
      return char == '\n' || char == '\r'
    }

    private fun CharSequence.hasChar(index: Int, char: Char): Boolean {
      return index in indices && this[index] == char
    }
  }
}
