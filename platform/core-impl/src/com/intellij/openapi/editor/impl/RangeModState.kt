// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentModState
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText

/**
 * A [DocumentModState] that keeps the modified positions of [text] in [ModifiedPositions], so a text
 * op needs no line data.
 *
 * A text of length `n` has `n + 1` positions. A line is modified when a position from the line start
 * to its separator is modified. These are the lines that [ModifiedLineSet] marks for the same ops.
 * Only [isLineModified] and [DocumentOp.UnmodifiedLines] read line numbers, and they read them from
 * [text]. So [text] builds line data only when a caller asks about a line.
 *
 * [positions] is `null` before the first text op, as [ModifiedLineSet] does not exist then.
 *
 * [lastLineHasSeparator] follows the same flag of [ModifiedLineSet]. It decides whether an empty last
 * line can be modified, and a change inside one line keeps it even when the text then ends with a
 * separator.
 */
internal class RangeModState private constructor(
  private val stamp: Long,
  private val sequence: Int,
  private val text: DocumentText,
  private val positions: ModifiedPositions?,
  private val lastLineHasSeparator: Boolean,
) : DocumentModState {

  override fun stamp(): Long {
    return stamp
  }

  override fun sequence(): Int {
    return sequence
  }

  override fun isLineModified(line: Int): Boolean {
    val positions = positions ?: return false
    return isModified(positions, line)
  }

  override fun applyOp(before: DocumentText, after: DocumentText, op: DocumentOp): DocumentModState {
    return when (op) {
      is DocumentOp.Insert -> applyTextChange(before, after, op.offset(), 0, op.fragment())
      is DocumentOp.Delete -> applyTextChange(before, after, op.offset(), op.length(), "")
      is DocumentOp.ModStamp -> applyModStamp(op)
      is DocumentOp.UnmodifiedLines -> applyUnmodifiedLines(op)
    }
  }

  private fun isModified(positions: ModifiedPositions, line: Int): Boolean {
    checkLine(text, line)
    if (isLastEmptyLine(line)) {
      return false
    }
    return positions.hasModified(text.lineStartOffset(line), text.lineEndOffset(line))
  }

  /**
   * Whether [line] is the empty line after a final separator, which is never modified.
   */
  private fun isLastEmptyLine(line: Int): Boolean {
    return lastLineHasSeparator && line == text.lineCount() - 1
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
    val oldPositions = positions ?: ModifiedPositions.clean(oldChars.length + 1)
    val newPositions = oldPositions.splice(change.start, change.end, change.length + 1)
    checkPositions(newPositions, newChars)
    return RangeModState(
      stamp = stamp,
      sequence = sequence + 1,
      text = after,
      positions = newPositions,
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
      positions = positions,
      lastLineHasSeparator = lastLineHasSeparator,
    )
  }

  /**
   * The state with the lines of [op] cleared, as [ModifiedLineSet.clearModificationFlags] checks and
   * clears them. An except line keeps its flag. A clear that changes no flag returns this state.
   */
  private fun applyUnmodifiedLines(op: DocumentOp.UnmodifiedLines): DocumentModState {
    val positions = positions ?: return this
    val clearedLines = clearedLines(op.startLine(), op.endLine())
    val exceptLines = op.exceptLines()
    if (clearsNoFlag(positions, clearedLines, exceptLines)) {
      return this
    }
    val lineCount = text.lineCount()
    val keptLines = exceptLines.filter { line ->
      line in 0 until lineCount && isModified(positions, line)
    }
    val from = text.lineStartOffset(clearedLines.first)
    val to = text.lineEndOffset(clearedLines.last)
    var cleared = positions.fill(from, to, modified = false)
    for (line in keptLines) {
      cleared = cleared.fill(text.lineStartOffset(line), text.lineEndOffset(line), modified = true)
    }
    return RangeModState(
      stamp = stamp,
      sequence = sequence,
      text = text,
      positions = cleared,
      lastLineHasSeparator = lastLineHasSeparator,
    )
  }

  /**
   * The lines that a clear from [startLine] to [endLine] covers. Fails for a range that [text] does not
   * hold, as [ModifiedLineSet.clearModificationFlags] does.
   */
  private fun clearedLines(startLine: Int, endLine: Int): IntRange {
    val lineCount = text.lineCount()
    require(startLine <= endLine) {
      "endLine < startLine: $endLine < $startLine; lineCount: $lineCount"
    }
    val isWholeEmptyText = endLine == 0 || endLine == Int.MAX_VALUE
    if (lineCount == 0 && startLine == 0 && isWholeEmptyText) {
      return IntRange.EMPTY
    }
    checkLine(text, startLine)
    val clearedEnd = if (endLine == Int.MAX_VALUE) lineCount else endLine
    checkLine(text, clearedEnd - 1)
    return startLine until clearedEnd
  }

  /**
   * Whether a clear of [clearedLines] keeps every flag, because each modified line among them is one of
   * [exceptLines]. It reads the positions that the clear sets clean, so it never misses a change.
   */
  private fun clearsNoFlag(
    positions: ModifiedPositions,
    clearedLines: IntRange,
    exceptLines: IntArray,
  ): Boolean {
    val exceptLinesInRange = exceptLines.filter { line -> line in clearedLines }.distinct().sorted()
    var gapStart = clearedLines.first
    for (exceptLine in exceptLinesInRange) {
      if (gapStart < exceptLine && hasModifiedLines(positions, gapStart, exceptLine - 1)) {
        return false
      }
      gapStart = exceptLine + 1
    }
    if (gapStart > clearedLines.last) {
      return true
    }
    return !hasModifiedLines(positions, gapStart, clearedLines.last)
  }

  /**
   * Whether a position from the start of [firstLine] to the separator of [lastLine] is modified.
   */
  private fun hasModifiedLines(positions: ModifiedPositions, firstLine: Int, lastLine: Int): Boolean {
    return positions.hasModified(text.lineStartOffset(firstLine), text.lineEndOffset(lastLine))
  }

  override fun toString(): String {
    val runs = positions?.runCount() ?: 0
    return "RangeModState{stamp=$stamp, sequence=$sequence, runs=$runs}"
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
        positions = null,
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

    private fun checkLine(text: DocumentText, line: Int) {
      val lineCount = text.lineCount()
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
