// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.DiffTestCase
import com.intellij.diff.tools.util.base.IgnorePolicy
import com.intellij.diff.util.Side
import com.intellij.diff.util.ThreeSide

class MergeAutoTest : MergeTestBase() {
  companion object {
    private const val RUNS = 10
    private const val MODIFICATION_CYCLE_COUNT = 5
    private const val MODIFICATION_CYCLE_SIZE = 3
    private const val MAX_TEXT_LENGTH = 300
  }

  fun `test undo - default policy`() {
    doUndoTest(System.currentTimeMillis(), RUNS, MAX_TEXT_LENGTH, IgnorePolicy.DEFAULT)
  }

  fun `test undo - trim whitespaces`() {
    doUndoTest(System.currentTimeMillis(), RUNS, MAX_TEXT_LENGTH, IgnorePolicy.TRIM_WHITESPACES)
  }

  fun `test undo - ignore whitespaces`() {
    doUndoTest(System.currentTimeMillis(), RUNS, MAX_TEXT_LENGTH, IgnorePolicy.IGNORE_WHITESPACES)
  }

  private fun doUndoTest(seed: Long, runs: Int, maxLength: Int, policy: IgnorePolicy) {
    doTest(seed, runs, maxLength) { text1, text2, text3, debugData ->
      debugData.put("IgnorePolicy", policy)

      doTest(text1, text2, text3, -1, policy) inner@ {
        if (changes.isEmpty()) {
          assertEquals(text1, text2)
          assertEquals(text1, text3)
          assertEquals(text2, text3)
          return@inner
        }

        for (m in 1..MODIFICATION_CYCLE_COUNT) {
          checkUndo(MODIFICATION_CYCLE_SIZE) {
            for (n in 1..MODIFICATION_CYCLE_SIZE) {
              when (RNG.nextInt(4)) {
                0 -> doApply()
                1 -> doIgnore()
                2 -> doTryResolve()
                3 -> doModifyText()
                else -> fail()
              }
              checkChangesRangeOrdering(changes)
            }
          }
        }
      }
    }
  }

  private fun TestBuilder.doApply() {
    val index = RNG.nextInt(changes.size)
    val change = changes[index]

    val side = Side.fromLeft(RNG.nextBoolean())
    val modifier = RNG.nextBoolean()

    command(change) { viewer.replaceChange(change, side, modifier) }
  }

  private fun TestBuilder.doIgnore() {
    val index = RNG.nextInt(changes.size)
    val change = changes[index]

    val side = Side.fromLeft(RNG.nextBoolean())
    val modifier = RNG.nextBoolean()

    command(change) { viewer.ignoreChange(change, side, modifier) }
  }

  private fun TestBuilder.doTryResolve() {
    val index = RNG.nextInt(changes.size)
    val change = changes[index]

    command(change) { viewer.resolveChangeAutomatically(change, ThreeSide.BASE) }
  }

  private fun TestBuilder.doModifyText() {
    val length = document.textLength

    var index1 = 0
    var index2 = 0
    if (length != 0) {
      index1 = RNG.nextInt(length)
      index2 = index1 + RNG.nextInt(length - index1)
    }
    val oldText = document.charsSequence.subSequence(index1, index2).toString()

    var newText = generateText(30)

    // Ensure non-identical modification
    if (newText == oldText) newText += "?"

    write { document.replaceString(index1, index2, newText) }
  }

  private fun doTest(seed: Long, runs: Int, maxLength: Int, test: (String, String, String, DiffTestCase.DebugData) -> Unit) {
    doAutoTest(seed, runs) { debugData ->
      debugData.put("MaxLength", maxLength)

      val text1 = generateText(maxLength)
      val text2 = generateText(maxLength)
      val text3 = generateText(maxLength)

      debugData.put("Text1", textToReadableFormat(text1))
      debugData.put("Text2", textToReadableFormat(text2))
      debugData.put("Text3", textToReadableFormat(text3))

      test(text1, text2, text3, debugData)
    }
  }

  private fun checkChangesRangeOrdering(changes: List<TextMergeChange>) {
    for (i in 1..changes.size - 1) {
      val lastEnd = changes[i - 1].getEndLine(ThreeSide.BASE)
      val start = changes[i].getStartLine(ThreeSide.BASE)
      assertTrue(lastEnd <= start, "lastEnd: $lastEnd, start: $start")
    }
  }
}
