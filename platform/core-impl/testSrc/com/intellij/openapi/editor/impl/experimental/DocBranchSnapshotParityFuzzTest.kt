// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentOp.Companion.deleteOp
import com.intellij.openapi.editor.ex.DocumentOp.Companion.insertOp
import com.intellij.openapi.editor.ex.DocumentOp.Companion.modStampOp
import com.intellij.openapi.editor.ex.DocumentOp.Companion.unmodifiedLinesOp
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.impl.DocumentSnapshotImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * A differential fuzz of the mod state against the ops that a document core sends. A [DocumentSnapshot]
 * is the reference, because the core uses it today. A [DocumentBranch] gets the same ops, and after each op
 * it must give the same text, stamp, sequence, and line flags.
 *
 * A step is one of these:
 * - a patch that [DocumentPatch.simple] builds, as the mutator applies it;
 * - a whole-text replace with its clear of the line flags, untrimmed or narrowed;
 * - a text move, as the two patches of the mutator;
 * - a stamp op, a clear of line flags that can be wrong, or an empty op;
 * - a trim of the branch, which must keep the mod state;
 * - a probe of the lines outside the text.
 *
 * An op that throws must throw the same exception class on both sides. When the snapshot keeps its
 * instance, the branch must keep its instance too, because the marker stores rely on that.
 */
internal class DocBranchSnapshotParityFuzzTest {

  @Test
  fun `a branch keeps the mod state of a snapshot for the ops of a document core`() {
    val coverage = Coverage()
    repeat(SEEDS) { seed ->
      val start = START_TEXTS[seed % START_TEXTS.size]
      val sides = Sides(start, coverage)
      val random = Random(SEED + seed)
      repeat(STEPS) { step ->
        try {
          sides.step(random)
        } catch (e: Throwable) {
          throw AssertionError("The seed ${SEED + seed}, step $step, from ${start.quoted()} failed: ${e.message}", e)
        }
      }
    }
    coverage.check()
  }

  /**
   * The rare cases of the fuzz, each with the least count that it must pass.
   */
  private enum class Case(val bound: Int) {
    WHOLE_TEXT_REPLACE(2000),
    MOVE(2000),
    EMPTY_OP(1000),
    TRIM(2000),
    FAILED_OP(800),
    FAILED_PROBE(6000),
    KEPT_INSTANCE(1000),
  }

  /**
   * How often the fuzz reaches each [Case]. Each count must pass its bound, so a change of the
   * generator cannot drop a case quietly.
   */
  private class Coverage {
    private val counts = IntArray(Case.entries.size)

    fun count(case: Case) {
      counts[case.ordinal]++
    }

    fun check() {
      val listed = Case.entries.joinToString { case -> "$case ${counts[case.ordinal]}" }
      for (case in Case.entries) {
        assertTrue(counts[case.ordinal] > case.bound) { "$case is rare: $listed" }
      }
    }
  }

  /**
   * The reference snapshot and the branch, which get the same ops.
   */
  private class Sides(start: String, private val coverage: Coverage) {
    private var snapshot: DocumentSnapshot = DocumentSnapshotImpl(DocumentText.createText(start)).applyOp(START_STAMP)
    private var branch: DocumentBranch = DocumentBranch.createBranch(start, Agent.createAgent("user")).applyOp(START_STAMP)

    fun step(random: Random) {
      when (random.nextInt(20)) {
        in 0..5 -> applyPatch(randomPatch(random))
        6, 7 -> wholeTextReplace(random)
        8, 9 -> move(random)
        10, 11 -> apply(modStampOp(random.nextLong(), random.nextBoolean()))
        12, 13, 14 -> apply(randomClear(random))
        15 -> emptyOp(random)
        16, 17 -> trim()
        else -> probeOutsideLines()
      }
    }

    private fun text(): String {
      return snapshot.text().string()
    }

    /**
     * A replace of up to 3 characters by a fragment, or a delete of them. A patch always changes the
     * text, as the mutator sends no patch for a change of nothing.
     */
    private fun randomPatch(random: Random): List<DocumentOp> {
      val length = text().length
      val start = random.nextInt(length + 1)
      val end = start + random.nextInt(minOf(3, length - start) + 1)
      val fragment = if (end > start && random.nextInt(3) == 0) {
        ""
      } else {
        randomFragment(random)
      }
      return DocumentPatch.simple(start, end, fragment, random.nextLong(), false).ops()
    }

    /**
     * A whole-text replace with the clear of the line flags. A fragment that is an immutable char
     * sequence keeps the untrimmed patch. Any other fragment is narrowed to the changed part.
     */
    private fun wholeTextReplace(random: Random) {
      val old = text()
      val new = changed(old, random)
      val stamp = random.nextLong()
      val ops = if (random.nextBoolean()) {
        DocumentPatch.simple(0, old.length, new, stamp, true).ops()
      } else {
        val prefix = old.commonPrefixWith(new).length
        val suffix = old.substring(prefix).commonSuffixWith(new.substring(prefix)).length
        val fragment = new.substring(prefix, new.length - suffix)
        DocumentPatch.complex(prefix, old.length - suffix, fragment, stamp, true, 0, old.length, prefix).ops()
      }
      applyPatch(ops)
      coverage.count(Case.WHOLE_TEXT_REPLACE)
    }

    /**
     * A move of up to 3 characters as the mutator does it: an insert of a copy that names its source,
     * then a delete of the source that names the copy.
     */
    private fun move(random: Random) {
      val text = text()
      if (text.length < 2) {
        applyPatch(randomPatch(random))
        return
      }
      val sourceStart = random.nextInt(text.length - 1)
      val sourceEnd = sourceStart + 1 + random.nextInt(minOf(3, text.length - sourceStart))
      val targets = (0..text.length).filter { offset -> offset !in sourceStart..sourceEnd }
      if (targets.isEmpty()) {
        applyPatch(randomPatch(random))
        return
      }
      val target = targets[random.nextInt(targets.size)]
      val moved = text.substring(sourceStart, sourceEnd)
      val shift = if (target < sourceStart) moved.length else 0
      val shiftedStart = sourceStart + shift
      val shiftedEnd = sourceEnd + shift
      val copy = DocumentPatch.complex(target, target, moved, random.nextLong(), false, target, target, shiftedStart)
      applyPatch(copy.ops())
      val removal = DocumentPatch.complex(shiftedStart, shiftedEnd, "", random.nextLong(), false, shiftedStart, shiftedEnd, target)
      applyPatch(removal.ops())
      coverage.count(Case.MOVE)
    }

    /**
     * A clear of a random line range with up to 3 except lines, or now and then a wrong range. An except
     * line can lie outside the text.
     */
    private fun randomClear(random: Random): DocumentOp {
      val lineCount = snapshot.text().lineCount()
      if (lineCount == 0 || random.nextInt(4) == 0) {
        return wrongClear(lineCount, random)
      }
      val startLine = random.nextInt(lineCount)
      val endLine = if (random.nextBoolean()) {
        Int.MAX_VALUE
      } else {
        startLine + random.nextInt(lineCount - startLine + 1)
      }
      val exceptLines = IntArray(random.nextInt(4)) {
        random.nextInt(lineCount + 2) - 1
      }
      return unmodifiedLinesOp(startLine, endLine, exceptLines)
    }

    /**
     * A reversed range, a range past the end, or a range that starts after the last line.
     */
    private fun wrongClear(lineCount: Int, random: Random): DocumentOp {
      return when (random.nextInt(3)) {
        0 -> unmodifiedLinesOp(1, 0, IntArray(0))
        1 -> unmodifiedLinesOp(0, lineCount + 1, IntArray(0))
        else -> unmodifiedLinesOp(lineCount, lineCount + 1, IntArray(0))
      }
    }

    private fun emptyOp(random: Random) {
      val offset = random.nextInt(text().length + 1)
      val op = if (random.nextBoolean()) {
        insertOp(offset, "")
      } else {
        deleteOp(offset, 0)
      }
      apply(op)
      coverage.count(Case.EMPTY_OP)
    }

    /**
     * The trim that the document core does after each patch. It must keep the text and the mod state.
     */
    private fun trim() {
      val trimmed = branch.trimmedTo(branch.version())
      assertSame(branch.text(), trimmed.text(), "the trim changed the text")
      assertSame(branch.modState(), trimmed.modState(), "the trim changed the mod state")
      branch = trimmed
      coverage.count(Case.TRIM)
    }

    /**
     * Asks both sides for the lines just outside the text. Both must give the same answer or throw
     * the same exception class.
     */
    private fun probeOutsideLines() {
      val lineCount = snapshot.text().lineCount()
      for (line in intArrayOf(-1, lineCount, lineCount + 1)) {
        val expected = runCatching { snapshot.modState().isLineModified(line) }
        val actual = runCatching { branch.modState().isLineModified(line) }
        val expectedError = expected.exceptionOrNull()
        if (expectedError != null) {
          assertEquals(expectedError.javaClass, actual.exceptionOrNull()?.javaClass) { "the line $line: $actual" }
          coverage.count(Case.FAILED_PROBE)
        } else {
          assertEquals(expected.getOrNull(), actual.getOrNull()) { "the line $line: $actual" }
        }
      }
    }

    private fun applyPatch(ops: List<DocumentOp>) {
      for (op in ops) {
        apply(op)
      }
    }

    private fun apply(op: DocumentOp) {
      val snapshotResult = runCatching { snapshot.applyOp(op) }
      val branchResult = runCatching { branch.applyOp(op) }
      val expectedError = snapshotResult.exceptionOrNull()
      if (expectedError != null) {
        val actualError = branchResult.exceptionOrNull()
        assertEquals(expectedError.javaClass, actualError?.javaClass) { "$op: the snapshot threw $expectedError" }
        coverage.count(Case.FAILED_OP)
        return
      }
      val newSnapshot = snapshotResult.getOrThrow()
      val newBranch = branchResult.getOrElse { error ->
        throw AssertionError("$op: the branch threw, but the snapshot did not", error)
      }
      if (newSnapshot === snapshot) {
        assertSame(branch, newBranch) { "$op: the snapshot kept its instance, but the branch did not" }
        coverage.count(Case.KEPT_INSTANCE)
      }
      snapshot = newSnapshot
      branch = newBranch
      assertSameState(op)
    }

    private fun assertSameState(op: DocumentOp) {
      val text = snapshot.text()
      assertEquals(text.string(), branch.text().string()) { "$op: the text" }
      val expected = snapshot.modState()
      val actual = branch.modState()
      assertEquals(expected.stamp(), actual.stamp()) { "$op: the stamp" }
      assertEquals(expected.sequence(), actual.sequence()) { "$op: the sequence" }
      for (line in 0 until text.lineCount()) {
        assertEquals(expected.isLineModified(line), actual.isLineModified(line)) { "$op: the line $line" }
      }
    }
  }

  private companion object {
    const val SEEDS = 210
    const val STEPS = 200
    const val SEED = 20261020L
    val START_STAMP: DocumentOp = modStampOp(1, false)

    /**
     * The start texts: empty, one line, separators of each kind, no final separator, and separators only.
     */
    val START_TEXTS = listOf("", "a", "one\ntwo\nthree\n", "a\r\nb\r\n", "a\rb", "x\n\n\ny", "\n\n")
    val FRAGMENTS = listOf("x", "yz", "\n", "a\nb", "\r", "\r\n", "c\r", "\nd", "e\r\nf\n")

    fun randomFragment(random: Random): String {
      return FRAGMENTS[random.nextInt(FRAGMENTS.size)]
    }

    /**
     * [old] with one random part replaced by a fragment or removed, or now and then a new text.
     */
    fun changed(old: String, random: Random): String {
      if (old.isEmpty() || random.nextInt(4) == 0) {
        return randomFragment(random) + randomFragment(random)
      }
      val start = random.nextInt(old.length + 1)
      val end = start + random.nextInt(old.length - start + 1)
      val fragment = if (random.nextBoolean()) randomFragment(random) else ""
      return old.substring(0, start) + fragment + old.substring(end)
    }

    fun String.quoted(): String {
      return "\"" + replace("\r", "\\r").replace("\n", "\\n") + "\""
    }
  }
}
