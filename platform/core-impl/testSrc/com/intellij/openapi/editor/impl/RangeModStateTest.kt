// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentModState
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentOp.Companion.cleanLinesOp
import com.intellij.openapi.editor.ex.DocumentOp.Companion.deleteOp
import com.intellij.openapi.editor.ex.DocumentOp.Companion.insertOp
import com.intellij.openapi.editor.ex.DocumentOp.Companion.modStampOp
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.applyOp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the rules of [RangeModState] that a random history reaches rarely: the results that change
 * nothing, the failures, and named examples of the line rules of [ModifiedLineSet].
 */
internal class RangeModStateTest {

  @Test
  fun `a new state marks no line, even past the text`() {
    val modState = RangeModState.create(DocumentText.createText("a\nb"))
    assertEquals(0, modState.sequence())
    assertFalse(modState.isLineModified(0))
    assertFalse(modState.isLineModified(100))
  }

  @Test
  fun `an op that changes nothing keeps the instance`() {
    val text = DocumentText.createText("abc")
    val modState = RangeModState.create(text)
    val sameStamp = modStampOp(modState.stamp(), false)
    val noModifiedLines = cleanLinesOp(0, 1, IntArray(0))
    assertSame(modState, modState.applyOp(text, text, insertOp(1, "")))
    assertSame(modState, modState.applyOp(text, text, deleteOp(1, 0)))
    assertSame(modState, modState.applyOp(text, text, sameStamp))
    assertSame(modState, modState.applyOp(text, text, noModifiedLines))
  }

  @Test
  fun `a stamp op sets the stamp and can count`() {
    val text = DocumentText.createText("abc")
    val stamped = RangeModState.create(text).applyOp(text, text, modStampOp(42, true))
    assertEquals(42, stamped.stamp())
    assertEquals(1, stamped.sequence())
  }

  @Test
  fun `a line past the text fails once a line is modified`() {
    val edited = editedFirstLine()
    assertThrows(IndexOutOfBoundsException::class.java) { edited.modState.isLineModified(2) }
    assertThrows(IndexOutOfBoundsException::class.java) { edited.modState.isLineModified(-1) }
  }

  @Test
  fun `a clear of a wrong line range fails`() {
    val edited = editedFirstLine()
    val reversed = cleanLinesOp(1, 0, IntArray(0))
    val pastEnd = cleanLinesOp(0, 3, IntArray(0))
    assertThrows(IllegalArgumentException::class.java) { edited.apply(reversed) }
    assertThrows(IndexOutOfBoundsException::class.java) { edited.apply(pastEnd) }
  }

  @Test
  fun `an op against another text fails`() {
    val edited = editedFirstLine()
    val longer = DocumentText.createText("a longer text\n")
    val insert = insertOp(0, "y")
    assertThrows(IllegalArgumentException::class.java) {
      edited.modState.applyOp(longer, longer.applyOp(insert), insert)
    }
  }

  @Test
  fun `the empty line after the final separator is never modified`() {
    val edited = assertSameAsLineSet("a\n", insertOp(2, "b\n"))
    assertTrue(edited.modState.isLineModified(1))
    assertFalse(edited.modState.isLineModified(2))
  }

  @Test
  fun `a delete of the text of the last line keeps that line modified`() {
    val edited = assertSameAsLineSet("a\nbc", deleteOp(2, 2))
    assertFalse(edited.modState.isLineModified(0))
    assertTrue(edited.modState.isLineModified(1))
  }

  @Test
  fun `a delete of whole lines through the end marks no line`() {
    val edited = assertSameAsLineSet("a\nb\nc", deleteOp(2, 3))
    assertFalse(edited.modState.isLineModified(0))
    assertFalse(edited.modState.isLineModified(1))
  }

  @Test
  fun `a change inside a CRLF pair marks the lines of the pair`() {
    val edited = assertSameAsLineSet("a\r\nb\r\nc", insertOp(5, "x"))
    assertTrue(edited.modState.isLineModified(1))
  }

  @Test
  fun `a clear keeps an except line that was modified`() {
    val edited = assertSameAsLineSet(
      "a\nb\nc",
      insertOp(4, "x"),
      cleanLinesOp(0, Int.MAX_VALUE, intArrayOf(1, 2)),
    )
    assertFalse(edited.modState.isLineModified(1))
    assertTrue(edited.modState.isLineModified(2))
  }

  @Test
  fun `a clear that changes no flag keeps the instance`() {
    // The line 1 of "a\nxb\nc" is modified.
    val edited = assertSameAsLineSet("a\nb\nc", insertOp(2, "x"))
    val clears = listOf(
      cleanLinesOp(2, 3, IntArray(0)),
      cleanLinesOp(0, Int.MAX_VALUE, intArrayOf(1)),
      cleanLinesOp(0, Int.MAX_VALUE, intArrayOf(1, 0, 1)),
      cleanLinesOp(1, 1, IntArray(0)),
      // The except lines lie outside the range or the text, next to the modified line.
      cleanLinesOp(2, 3, intArrayOf(-1, 0, 3)),
    )
    for (clear in clears) {
      assertSame(edited.modState, edited.apply(clear)) { "$clear" }
    }
    val cleared = Edited(edited.text, edited.apply(cleanLinesOp(0, Int.MAX_VALUE, IntArray(0))))
    assertSame(cleared.modState, cleared.apply(cleanLinesOp(0, Int.MAX_VALUE, IntArray(0))))
  }

  @Test
  fun `a clear that changes a flag gives a new state`() {
    // Each case modifies one line of "a\nb\nc" and clears it. The line is the last line of the range,
    // or it lies before an except line.
    val start = "a\nb\nc"
    val lastLine = ModifiedLine(insertOp(5, "y"), 2)
    val firstLine = ModifiedLine(insertOp(0, "z"), 0)
    val cases = listOf(
      lastLine to cleanLinesOp(1, 3, IntArray(0)),
      lastLine to cleanLinesOp(0, Int.MAX_VALUE, intArrayOf(1)),
      lastLine to cleanLinesOp(0, Int.MAX_VALUE, intArrayOf(0, 1)),
      firstLine to cleanLinesOp(0, Int.MAX_VALUE, intArrayOf(1)),
    )
    for ((case, clear) in cases) {
      val edited = assertSameAsLineSet(start, case.edit)
      val cleared = assertSameAsLineSet(start, case.edit, clear)
      assertNotSame(edited.modState, edited.apply(clear)) { "$clear" }
      assertFalse(cleared.modState.isLineModified(case.line)) { "$clear" }
    }
  }

  @Test
  fun `a clear of every line matches the line set`() {
    val insert = insertOp(0, "x\n")
    val clearAll = cleanLinesOp(0, Int.MAX_VALUE, IntArray(0))
    for (start in listOf("", "a", "a\n", "a\r\nb\r\n", "\n\n")) {
      val cleared = assertSameAsLineSet(start, insert, clearAll)
      assertFalse(cleared.modState.isLineModified(0)) { start }
    }
  }

  /**
   * Applies [ops] to a new [RangeModState] and to a new [DocumentModStateImpl], and checks that both
   * mark the same lines.
   */
  private fun assertSameAsLineSet(start: String, vararg ops: DocumentOp): Edited {
    var text = DocumentText.createText(start)
    var expected: DocumentModState = DocumentModStateImpl()
    var actual: DocumentModState = RangeModState.create(text)
    for (op in ops) {
      val after = when (op) {
        is DocumentOp.Text -> text.applyOp(op)
        is DocumentOp.ModStamp, is DocumentOp.CleanLines -> text
      }
      expected = expected.applyOp(text, after, op)
      actual = actual.applyOp(text, after, op)
      text = after
    }
    assertEquals(expected.sequence(), actual.sequence())
    for (line in 0 until text.lineCount()) {
      assertEquals(expected.isLineModified(line), actual.isLineModified(line)) { "line $line" }
    }
    return Edited(text, actual)
  }

  /**
   * The text `"xa\nb"` with its first line modified.
   */
  private fun editedFirstLine(): Edited {
    val start = DocumentText.createText("a\nb")
    val insert = insertOp(0, "x")
    val text = start.applyOp(insert)
    val modState = RangeModState.create(start).applyOp(start, text, insert)
    return Edited(text, modState)
  }

  /**
   * An [edit] that modifies the [line].
   */
  private class ModifiedLine(val edit: DocumentOp, val line: Int)

  /**
   * A text and its mod state after some ops.
   */
  private class Edited(val text: DocumentText, val modState: DocumentModState) {
    fun apply(op: DocumentOp.CleanLines): DocumentModState {
      return modState.applyOp(text, text, op)
    }
  }
}
