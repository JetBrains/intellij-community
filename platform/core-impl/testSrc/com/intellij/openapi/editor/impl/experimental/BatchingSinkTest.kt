// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.applyOp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests [BatchingSink]: which reports join the pending op, which flush it, how a move leaves, and
 * what the sink rejects. The ops are the op stream of a merge, so every test compares them with the
 * edits that the text really took, and checks that they fold to the result.
 */
internal class BatchingSinkTest {

  @Test
  fun `inserts that meet end to end leave as one op`() {
    val sink = Recorded("[]")
    sink.insert(1, "ab")
    sink.insert(3, "cd")
    sink.insert(5, "e")
    sink.check("[abcde]", "ins(1, \"abcde\")")
  }

  @Test
  fun `deletes at one position leave as one op`() {
    val sink = Recorded("0123456789")
    sink.delete(2, 3)
    sink.delete(2, 1)
    sink.check("016789", "del(2, len=4)")
  }

  @Test
  fun `deletes that end where the pending delete starts leave as one op`() {
    // Three backspaces: each removes the character before the one the last removed.
    val sink = Recorded("0123456")
    sink.delete(5, 1)
    sink.delete(4, 1)
    sink.delete(3, 1)
    sink.check("0126", "del(3, len=3)")
  }

  @Test
  fun `a Delete key press and a backspace at one place leave as one op`() {
    val sink = Recorded("0123456")
    sink.delete(3, 1)
    sink.delete(3, 1)
    sink.delete(2, 1)
    sink.check("0156", "del(2, len=3)")
  }

  @Test
  fun `a delete of the end of a pending insert shortens it`() {
    val sink = Recorded("[]")
    sink.insert(1, "abcd")
    sink.delete(4, 1)
    sink.delete(2, 2)
    sink.check("[a]", "ins(1, \"a\")")
  }

  @Test
  fun `a delete of the whole pending insert leaves no op`() {
    val sink = Recorded("[]")
    sink.insert(1, "ab")
    sink.delete(2, 1)
    sink.delete(1, 1)
    sink.check("[]")
    assertSame(sink.initial, sink.inner.result())
  }

  @Test
  fun `a report after an emptied insert starts a new op`() {
    val sink = Recorded("0123")
    sink.insert(2, "ab")
    sink.delete(2, 2)
    sink.insert(4, "x")
    sink.delete(0, 1)
    sink.check("123x", "ins(4, \"x\")", "del(0, len=1)")
  }

  @Test
  fun `a delete inside a pending insert that misses its end flushes it`() {
    val sink = Recorded("[]")
    sink.insert(1, "abc")
    sink.delete(1, 1)
    sink.check("[bc]", "ins(1, \"abc\")", "del(1, len=1)")
  }

  @Test
  fun `a delete that starts before a pending insert flushes it`() {
    val sink = Recorded("0123")
    sink.insert(2, "ab")
    sink.delete(1, 3)
    sink.check("023", "ins(2, \"ab\")", "del(1, len=3)")
  }

  @Test
  fun `a report that does not continue the pending op flushes it first`() {
    val sink = Recorded("0123456789")
    sink.insert(2, "x")
    // Before the pending insert, so it does not continue it.
    sink.insert(2, "y")
    sink.delete(0, 1)
    // Neither at the pending delete nor just before it.
    sink.delete(5, 2)
    sink.insert(0, "z")
    // An insert at the position of a pending delete is not a delete.
    sink.delete(3, 1)
    sink.insert(3, "w")
    sink.check(
      "z1yw236789",
      "ins(2, \"x\")",
      "ins(2, \"y\")",
      "del(0, len=1)",
      "del(5, len=2)",
      "ins(0, \"z\")",
      "del(3, len=1)",
      "ins(3, \"w\")",
    )
  }

  @Test
  fun `a move insert and its delete leave as a pair with move offsets`() {
    // "01" moves after "3", so the source sits before the copy.
    val sink = Recorded("0123456789")
    sink.moveInsert(4, "01", 0)
    sink.moveDelete(0, 2, 4)
    sink.check("2301456789", "ins(4, \"01\", move=0)", "del(0, len=2, move=4)")
  }

  @Test
  fun `a move to an earlier place leaves as a pair with move offsets`() {
    // "89" moves before "6", so the source sits after the copy.
    val sink = Recorded("0123456789")
    sink.moveInsert(6, "89", 10)
    sink.moveDelete(10, 2, 6)
    sink.check("0123458967", "ins(6, \"89\", move=10)", "del(10, len=2, move=6)")
  }

  @Test
  fun `a move delete in pieces leaves as one op`() {
    // A source of three items arrives as three reports at one position.
    val sink = Recorded("0123456789")
    sink.moveInsert(10, "123", 1)
    repeat(3) {
      sink.moveDelete(1, 1, 10)
    }
    sink.check("0456789123", "ins(10, \"123\", move=1)", "del(1, len=3, move=10)")
  }

  @Test
  fun `a move insert without its delete leaves as a plain insert`() {
    val atFinish = Recorded("0123")
    atFinish.moveInsert(4, "01", 0)
    atFinish.check("012301", "ins(4, \"01\")")
    val beforeAnotherReport = Recorded("0123")
    beforeAnotherReport.moveInsert(4, "01", 0)
    beforeAnotherReport.insert(0, "x")
    beforeAnotherReport.check("x012301", "ins(4, \"01\")", "ins(0, \"x\")")
  }

  @Test
  fun `a plain report inside a pending move turns it into plain ops`() {
    // The move delete stops after one of two characters.
    val sink = Recorded("0123")
    sink.moveInsert(4, "01", 0)
    sink.moveDelete(0, 1, 4)
    sink.delete(2, 1)
    sink.check(
      "1201",
      "ins(4, \"01\")",
      "del(0, len=1)",
      "del(2, len=1)",
    )
  }

  @Test
  fun `a move delete that does not continue the pending move is a plain delete`() {
    val otherCopy = Recorded("0123")
    otherCopy.moveInsert(4, "01", 0)
    otherCopy.moveDelete(0, 2, 5)
    otherCopy.check("2301", "ins(4, \"01\")", "del(0, len=2)")
    val otherSource = Recorded("0123")
    otherSource.moveInsert(4, "01", 0)
    otherSource.moveDelete(1, 1, 4)
    otherSource.check("02301", "ins(4, \"01\")", "del(1, len=1)")
    val longerThanCopy = Recorded("0123")
    longerThanCopy.moveInsert(4, "01", 0)
    longerThanCopy.moveDelete(0, 3, 4)
    longerThanCopy.check("301", "ins(4, \"01\")", "del(0, len=3)")
  }

  @Test
  fun `a move delete without a pending move acts as a plain delete`() {
    val withNothing = Recorded("0123")
    withNothing.moveDelete(0, 2, 4)
    withNothing.check("23", "del(0, len=2)")
    val afterInsert = Recorded("0123")
    afterInsert.insert(4, "ab")
    afterInsert.moveDelete(0, 1, 4)
    afterInsert.check("123ab", "ins(4, \"ab\")", "del(0, len=1)")
    // It joins a pending delete at its position, as a plain delete does.
    val afterDelete = Recorded("0123")
    afterDelete.delete(0, 1)
    afterDelete.moveDelete(0, 1, 5)
    afterDelete.check("23", "del(0, len=2)")
  }

  @Test
  fun `a move insert flushes a pending delete and a pending move`() {
    val sink = Recorded("0123456789")
    sink.delete(9, 1)
    sink.moveInsert(0, "12", 3)
    // The first move never gets its delete, so it leaves as a plain insert.
    sink.moveInsert(0, "ab", 5)
    sink.check(
      "ab12012345678",
      "del(9, len=1)",
      "ins(0, \"12\")",
      "ins(0, \"ab\")",
    )
  }

  @Test
  fun `the length of a pending move counts the removed part of the source`() {
    // "23" moves to the front. After one piece of its delete, the text has 5 characters.
    val sink = Recorded("0123")
    sink.moveInsert(0, "23", 4)
    sink.moveDelete(4, 1, 0)
    assertThrows(IllegalArgumentException::class.java) {
      sink.inner.delete(4, 2)
    }
    // The rejected report changed nothing, so the last piece still completes the move.
    sink.moveDelete(4, 1, 0)
    sink.check("2301", "ins(0, \"23\", move=4)", "del(4, len=2, move=0)")
  }

  @Test
  fun `a move whose source does not hold the fragment leaves as plain ops`() {
    // A faulty event: the move copied "01", but its delete removes "23".
    val sink = Recorded("0123")
    sink.moveInsert(4, "01", 2)
    sink.moveDelete(2, 2, 4)
    sink.check("0101", "ins(4, \"01\")", "del(2, len=2)")
  }

  @Test
  fun `a move whose source overlaps the copy leaves as plain ops`() {
    // The source holds the fragment, but it overlaps the copy.
    val sink = Recorded("aaaa")
    sink.moveInsert(1, "aa", 2)
    sink.moveDelete(2, 2, 1)
    sink.check("aaaa", "ins(1, \"aa\")", "del(2, len=2)")
  }

  @Test
  fun `a move op never joins a neighbour`() {
    val sink = Recorded("0123456789")
    // The move insert starts at the end of the pending insert.
    sink.insert(4, "x")
    sink.moveInsert(5, "01", 0)
    sink.moveDelete(0, 2, 5)
    // An insert at the end of the copy, and a delete at the position of the move delete.
    sink.insert(5, "y")
    sink.delete(0, 1)
    sink.check(
      "3x01y456789",
      "ins(4, \"x\")",
      "ins(5, \"01\", move=0)",
      "del(0, len=2, move=5)",
      "ins(5, \"y\")",
      "del(0, len=1)",
    )
  }

  @Test
  fun `a result without reports is the text itself`() {
    val sink = Recorded("abc")
    assertSame(sink.initial, sink.inner.result())
    sink.check("abc")
  }

  @Test
  fun `a second result or ops call applies nothing more`() {
    val sink = Recorded("abc")
    sink.insert(3, "d")
    val first = sink.inner.result()
    assertSame(first, sink.inner.result())
    assertEquals(sink.inner.ops(), sink.inner.ops())
    sink.check("abcd", "ins(3, \"d\")")
  }

  @Test
  fun `a report after the sink finished is rejected`() {
    val byResult = BatchingSink(DocumentText.createText("abc"))
    byResult.result()
    assertThrows(IllegalArgumentException::class.java) { byResult.insert(0, "x") }
    val byOps = BatchingSink(DocumentText.createText("abc"))
    byOps.ops()
    assertThrows(IllegalArgumentException::class.java) { byOps.delete(0, 1) }
    assertThrows(IllegalArgumentException::class.java) { byOps.moveInsert(0, "x", 1) }
    assertThrows(IllegalArgumentException::class.java) { byOps.moveDelete(0, 1, 2) }
  }

  @Test
  fun `a report outside the current text is rejected`() {
    val sink = BatchingSink(DocumentText.createText("xyz"))
    assertThrows(IllegalArgumentException::class.java) { sink.insert(-1, "a") }
    assertThrows(IllegalArgumentException::class.java) { sink.insert(4, "a") }
    assertThrows(IllegalArgumentException::class.java) { sink.delete(-1, 1) }
    assertThrows(IllegalArgumentException::class.java) { sink.delete(3, 1) }
    assertThrows(IllegalArgumentException::class.java) { sink.delete(1, Int.MAX_VALUE) }
    // The length counts the pending op: "xyz" with "ab" pending has 5 characters.
    sink.insert(3, "ab")
    sink.delete(4, 1)
    assertThrows(IllegalArgumentException::class.java) { sink.delete(4, 1) }
    sink.insert(4, "c")
    // Then "xyzac" with 2 deleted pending has 3.
    sink.delete(0, 2)
    assertThrows(IllegalArgumentException::class.java) { sink.insert(4, "q") }
    sink.insert(3, "q")
    assertEquals("zacq", sink.result().string())
  }

  @Test
  fun `a move report outside the current text is rejected`() {
    val sink = BatchingSink(DocumentText.createText("0123"))
    assertThrows(IllegalArgumentException::class.java) { sink.moveInsert(5, "01", 0) }
    // The delete continues the pending move, but the text has only 6 characters.
    sink.moveInsert(4, "01", 9)
    assertThrows(IllegalArgumentException::class.java) { sink.moveDelete(9, 1, 4) }
    assertEquals("012301", sink.result().string())
  }

  @Test
  fun `an empty report is rejected`() {
    val sink = BatchingSink(DocumentText.createText("abc"))
    assertThrows(IllegalArgumentException::class.java) { sink.insert(0, "") }
    assertThrows(IllegalArgumentException::class.java) { sink.delete(0, 0) }
    assertThrows(IllegalArgumentException::class.java) { sink.delete(0, -1) }
    assertEquals(emptyList<DocumentOp.Text>(), sink.ops())
  }

  @Test
  fun `the ops cannot change`() {
    val sink = BatchingSink(DocumentText.createText("abc"))
    sink.insert(0, "x")
    @Suppress("UNCHECKED_CAST")
    val ops = sink.ops() as MutableList<DocumentOp.Text>
    assertThrows(UnsupportedOperationException::class.java) { ops.add(DocumentOp.deleteOp(0, 1)) }
    assertThrows(UnsupportedOperationException::class.java) { ops.clear() }
  }

  @Test
  fun `a long pending fragment stays short in a message`() {
    val sink = BatchingSink(DocumentText.createText(""))
    sink.insert(0, "x".repeat(1_000))
    val message = sink.toString()
    assertTrue(message.contains("(1000 chars)"), message)
    assertTrue(message.length < 200, message)
  }

  @Test
  fun `a pending move shows in a message`() {
    val sink = BatchingSink(DocumentText.createText("0123"))
    sink.moveInsert(4, "01", 0)
    sink.moveDelete(0, 1, 4)
    val message = sink.toString()
    assertTrue(message.contains("pending=move of \"01\" to 4 from 0, 1 deleted"), message)
  }

  /**
   * A sink over a text that records every edit it applies. [check] compares those edits with the ops
   * the sink reports, and folds the ops over the start text.
   */
  private class Recorded(text: String) {
    private val start = DocumentText.createText(text)
    private val applied = ArrayList<String>()

    /**
     * The text the sink starts from, which records the edits.
     */
    val initial: DocumentText = RecordingText(start, applied)
    val inner = BatchingSink(initial)

    fun insert(effectPos: Int, fragment: String) {
      inner.insert(effectPos, fragment)
    }

    fun delete(effectPos: Int, count: Int) {
      inner.delete(effectPos, count)
    }

    fun moveInsert(effectPos: Int, fragment: String, sourceEffectPos: Int) {
      inner.moveInsert(effectPos, fragment, sourceEffectPos)
    }

    fun moveDelete(effectPos: Int, count: Int, copyEffectPos: Int) {
      inner.moveDelete(effectPos, count, copyEffectPos)
    }

    fun check(expectedText: String, vararg expectedOps: String) {
      val result = inner.result()
      assertEquals(expectedText, result.string())
      assertEquals(expectedOps.toList(), inner.ops().map { it.toString() })
      assertEquals(inner.ops().map { withoutMove(it) }, applied)
      val folded = inner.ops().fold(start) { text, op -> text.applyOp(op) }
      assertEquals(expectedText, folded.string())
    }

    /**
     * The record that [RecordingText] makes for [op]. A text edit has no move offset.
     */
    private fun withoutMove(op: DocumentOp.Text): String {
      val edit = when (op) {
        is DocumentOp.Insert -> DocumentOp.insertOp(op.offset(), op.fragment())
        is DocumentOp.Delete -> DocumentOp.deleteOp(op.offset(), op.length())
      }
      return edit.toString()
    }
  }

  /**
   * A text that records every insert and delete it applies, and passes the rest to [inner].
   */
  private class RecordingText(
    private val inner: DocumentText,
    private val ops: MutableList<String>,
  ) : DocumentText by inner {
    override fun insert(offset: Int, fragment: CharSequence): DocumentText {
      ops.add(DocumentOp.insertOp(offset, fragment).toString())
      return RecordingText(inner.insert(offset, fragment), ops)
    }

    override fun delete(startOffset: Int, endOffset: Int): DocumentText {
      ops.add(DocumentOp.deleteOp(startOffset, endOffset - startOffset).toString())
      return RecordingText(inner.delete(startOffset, endOffset), ops)
    }
  }
}
