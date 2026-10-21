// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentOp.Companion.cleanLinesOp
import com.intellij.openapi.editor.ex.DocumentOp.Companion.deleteOp
import com.intellij.openapi.editor.ex.DocumentOp.Companion.insertOp
import com.intellij.openapi.editor.ex.DocumentOp.Companion.modStampOp
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.impl.DocumentSnapshotImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * Checks that a [DocumentBranch] keeps the mod state that a [DocumentSnapshot] keeps for the same ops.
 */
internal class DocBranchModStateFuzzTest {

  @Test
  fun `a branch keeps the mod state of a snapshot`() {
    var keptClears = 0
    repeat(SEEDS) { seed ->
      val random = Random(seed.toLong())
      // Both start from one stamp, so the stamps compare too.
      val startStamp = modStampOp(1, false)
      val text = DocumentText.createText(TEXT)
      var snapshot: DocumentSnapshot = DocumentSnapshotImpl(text).applyOp(startStamp)
      var branch = DocumentBranch.createBranch(TEXT, Agent.createAgent("user")).applyOp(startStamp)
      repeat(STEPS) { step ->
        val op = randomOp(snapshot.text(), random)
        val before = branch
        val keepsExceptLine = keepsModifiedExceptLine(snapshot, op)
        snapshot = snapshot.applyOp(op)
        branch = branch.applyOp(op)
        assertSameModState(snapshot, branch, "seed $seed, step $step, $op")
        if (keepsExceptLine && branch === before) {
          keptClears++
        }
      }
    }
    // A clear that changes no flag keeps the exact positions of a modified except line. The ops after
    // such a clear must still match the snapshot, so the fuzz must reach it often.
    assertTrue(keptClears > MIN_KEPT_CLEARS) { "only $keptClears clears keep a modified except line" }
  }

  /**
   * Whether [op] is a clear with an except line in its range that is modified in [snapshot].
   */
  private fun keepsModifiedExceptLine(snapshot: DocumentSnapshot, op: DocumentOp): Boolean {
    if (op !is DocumentOp.CleanLines) {
      return false
    }
    val lineCount = snapshot.text().lineCount()
    val clearedEnd = if (op.endLine() == Int.MAX_VALUE) lineCount else op.endLine()
    return op.exceptLines().any { line ->
      line >= op.startLine() && line < clearedEnd && snapshot.modState().isLineModified(line)
    }
  }

  private fun assertSameModState(snapshot: DocumentSnapshot, branch: DocumentBranch, message: String) {
    val expected = snapshot.modState()
    val actual = branch.modState()
    assertEquals(expected.stamp(), actual.stamp(), message)
    assertEquals(expected.sequence(), actual.sequence(), message)
    for (line in 0 until snapshot.text().lineCount()) {
      assertEquals(expected.isLineModified(line), actual.isLineModified(line)) { "$message, line $line" }
    }
  }

  private fun randomOp(text: DocumentText, random: Random): DocumentOp {
    return when (random.nextInt(6)) {
      0 -> modStampOp(random.nextLong(), random.nextBoolean())
      1 -> randomCleanLines(text, random)
      2, 3 -> randomInsert(text, random)
      else -> randomDelete(text, random)
    }
  }

  private fun randomInsert(text: DocumentText, random: Random): DocumentOp {
    val offset = random.nextInt(text.length() + 1)
    val fragment = FRAGMENTS[random.nextInt(FRAGMENTS.size)]
    return insertOp(offset, fragment)
  }

  /**
   * A delete of a few characters. Now and then it deletes up to the end of the text, or all of it.
   */
  private fun randomDelete(text: DocumentText, random: Random): DocumentOp {
    val length = text.length()
    if (length == 0) {
      return randomInsert(text, random)
    }
    val offset = random.nextInt(length)
    return when (random.nextInt(10)) {
      0 -> deleteOp(0, length)
      1 -> deleteOp(offset, length - offset)
      else -> {
        val count = 1 + random.nextInt(minOf(3, length - offset))
        deleteOp(offset, count)
      }
    }
  }

  /**
   * A clear of a random line range. An except line can lie outside the text, and the clear ignores it.
   */
  private fun randomCleanLines(text: DocumentText, random: Random): DocumentOp {
    val lineCount = text.lineCount()
    if (lineCount == 0) {
      val endLine = if (random.nextBoolean()) 0 else Int.MAX_VALUE
      return cleanLinesOp(0, endLine, IntArray(0))
    }
    val startLine = random.nextInt(lineCount)
    val endLine = if (random.nextBoolean()) {
      Int.MAX_VALUE
    } else {
      startLine + 1 + random.nextInt(lineCount - startLine)
    }
    val exceptLines = IntArray(random.nextInt(3)) {
      random.nextInt(lineCount + 2) - 1
    }
    return cleanLinesOp(startLine, endLine, exceptLines)
  }

  private companion object {
    const val TEXT = "one\ntwo\nthree\n"
    const val SEEDS = 200
    const val STEPS = 300
    const val MIN_KEPT_CLEARS = 300
    val FRAGMENTS = listOf("x", "yz", "\n", "a\nb", "\r", "\r\n", "c\r", "\nd", "e\r\nf\n")
  }
}
