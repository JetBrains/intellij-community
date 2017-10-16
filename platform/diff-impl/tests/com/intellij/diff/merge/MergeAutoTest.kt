/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.merge

import com.intellij.diff.DiffTestCase
import com.intellij.diff.util.Side
import com.intellij.diff.util.ThreeSide

class MergeAutoTest : MergeTestBase() {
  companion object {
    private val MODIFICATION_CYCLE_COUNT = 5
    private val MODIFICATION_CYCLE_SIZE = 3
  }

  fun testUndo() {
    doUndoTest(System.currentTimeMillis(), 10, 300)
  }

  private fun doUndoTest(seed: Long, runs: Int, maxLength: Int) {
    doTest(seed, runs, maxLength) { text1, text2, text3, debugData ->
      testN(text1, text2, text3) {
        if (changes.isEmpty()) {
          assertEquals(text1, text2)
          assertEquals(text1, text3)
          assertEquals(text2, text3)
          return@testN
        }

        for (m in 1..MODIFICATION_CYCLE_COUNT) {
          checkUndo(MODIFICATION_CYCLE_SIZE) {
            for (n in 1..MODIFICATION_CYCLE_SIZE) {
              val operation = RNG.nextInt(4)
              when (operation) {
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

  private fun TestBuilder.doApply(): Unit {
    val index = RNG.nextInt(changes.size)
    val change = changes[index]

    val side = Side.fromLeft(RNG.nextBoolean())
    val modifier = RNG.nextBoolean()

    command(change) { viewer.replaceChange(change, side, modifier) }
  }

  private fun TestBuilder.doIgnore(): Unit {
    val index = RNG.nextInt(changes.size)
    val change = changes[index]

    val side = Side.fromLeft(RNG.nextBoolean())
    val modifier = RNG.nextBoolean()

    command(change) { viewer.ignoreChange(change, side, modifier) }
  }

  private fun TestBuilder.doTryResolve(): Unit {
    val index = RNG.nextInt(changes.size)
    val change = changes[index]

    command(change) { viewer.resolveChangeAutomatically(change, ThreeSide.BASE) }
  }

  private fun TestBuilder.doModifyText(): Unit {
    val length = document.textLength

    var index1: Int = 0
    var index2: Int = 0
    if (length != 0) {
      index1 = RNG.nextInt(length)
      index2 = index1 + RNG.nextInt(length - index1)
    }

    val newText = generateText(30)

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
