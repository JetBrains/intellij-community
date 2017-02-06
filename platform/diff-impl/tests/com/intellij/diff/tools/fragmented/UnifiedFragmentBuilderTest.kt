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
package com.intellij.diff.tools.fragmented

import com.intellij.diff.DiffTestCase
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.progress.DumbProgressIndicator

class UnifiedFragmentBuilderTest : DiffTestCase() {
  fun testEquals() {
    val builder = createBuilder("A\nB\nC", "A\nB\nC", Side.LEFT)

    assertTrue(builder.isEqual)
    assertEquals(builder.text.toString(), "A\nB\nC\n")
    assertEmpty(builder.changedLines)
    assertEmpty(builder.blocks)
  }

  fun testWrongEndLineTypoBug() {
    val builder = createBuilder("A\nB\nC\nD", "A\nD", Side.RIGHT)

    assertFalse(builder.isEqual)
    assertEquals(builder.text.toString(), "A\nB\nC\nD\n")
    assertEquals(builder.changedLines, listOf(LineRange(1, 3)))

    assertEquals(builder.blocks.size, 1)
    val block = builder.blocks[0]
    assertEquals(block.line1, 1)
    assertEquals(block.line2, 3)
    assertEquals(block.range1.start, 1)
    assertEquals(block.range1.end, 3)
    assertEquals(block.range2.start, 3)
    assertEquals(block.range2.end, 3)
  }

  fun testFirstLineChange() {
    val builder = createBuilder("X\nB\nC", "A\nB\nC", Side.RIGHT)

    assertFalse(builder.isEqual)
    assertEquals(builder.text.toString(), "X\nA\nB\nC\n")
    assertEquals(builder.changedLines, listOf(LineRange(0, 1), LineRange(1, 2)))

    assertEquals(builder.blocks.size, 1)
    val block = builder.blocks[0]
    assertEquals(block.line1, 0)
    assertEquals(block.line2, 2)
    assertEquals(block.range1.start, 0)
    assertEquals(block.range1.end, 1)
    assertEquals(block.range2.start, 1)
    assertEquals(block.range2.end, 2)
  }

  fun testDeletion() {
    val builder = createBuilder("A\n", "", Side.LEFT)

    assertFalse(builder.isEqual)
    assertEquals(builder.text.toString(), "A\n\n")
    assertEquals(builder.changedLines, listOf(LineRange(0, 1)))

    assertEquals(builder.blocks.size, 1)
    val block = builder.blocks[0]
    assertEquals(block.line1, 0)
    assertEquals(block.line2, 1)
    assertEquals(block.range1.start, 0)
    assertEquals(block.range1.end, 1)
    assertEquals(block.range2.start, 1)
    assertEquals(block.range2.end, 1)
  }


  private fun createBuilder(text1: String, text2: String, side: Side): UnifiedFragmentBuilder {
    val document1 = DocumentImpl(text1)
    val document2 = DocumentImpl(text2)

    val fragments = MANAGER.compareLinesInner(document1.charsSequence, document2.charsSequence,
                                              ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE)

    val builder = UnifiedFragmentBuilder(fragments, document1, document2, side)
    builder.exec()

    return builder
  }
}
