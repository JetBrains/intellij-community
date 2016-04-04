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

import com.intellij.diff.comparison.ComparisonManagerImpl
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.testFramework.UsefulTestCase

class UnifiedFragmentBuilderTest : UsefulTestCase() {
  fun testEquals() {
    val document1 = DocumentImpl("A\nB\nC")
    val document2 = DocumentImpl("A\nB\nC")

    val fragments = MANAGER.compareLinesInner(document1.charsSequence, document2.charsSequence, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE)

    val builder = UnifiedFragmentBuilder(fragments, document1, document2, Side.LEFT)
    builder.exec()

    assertTrue(builder.isEqual)
    assertEquals(builder.text.toString(), "A\nB\nC\n")
    assertEmpty(builder.changedLines)
    assertEmpty(builder.blocks)
  }

  fun testWrongEndLineTypoBug() {
    val document1 = DocumentImpl("A\nB\nC\nD")
    val document2 = DocumentImpl("A\nD")

    val fragments = MANAGER.compareLinesInner(document1.charsSequence, document2.charsSequence, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE)

    val builder = UnifiedFragmentBuilder(fragments, document1, document2, Side.RIGHT)
    builder.exec()

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

  companion object {
    private val MANAGER = ComparisonManagerImpl()
  }
}
