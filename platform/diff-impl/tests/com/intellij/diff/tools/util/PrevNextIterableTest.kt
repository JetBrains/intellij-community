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
package com.intellij.diff.tools.util

import com.intellij.diff.DiffTestCase
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.EditorEx
import org.mockito.Mockito
import kotlin.test.assertEquals

class PrevNextIterableTest : DiffTestCase() {
  fun testIterable() {
    test(1) {
      check(!0 - 0, null, null)
    }

    test(10) {
      check(!0 - 9, null, null)
    }

    test(10, !5 - 6) {
      check(!0 - 4, null, 0)
      check(!5 - 6, null, null)
      check(!7 - 9, 0, null)
    }

    test(10, !5) {
      check(!0 - 4, null, 0)
      check(!5 - 5, null, null)
      check(!6 - 9, 0, null)
    }

    test(10, !3 - 4, !6 - 7) {
      check(!0 - 2, null, 0)
      check(!3 - 4, null, 1)
      check(!5 - 5, 0, 1)
      check(!6 - 7, 0, null)
      check(!8 - 9, 1, null)
    }

    test(25, !1 - 3, !6 - 7, !9 - 12, !15 - 20) {
      check(!0 - 0, null, 0)
      check(!1 - 3, null, 1)
      check(!4 - 5, 0, 1)
      check(!6 - 7, 0, 2)
      check(!8 - 8, 1, 2)
      check(!9 - 12, 1, 3)
      check(!13 - 14, 2, 3)
      check(!15 - 20, 2, null)
      check(!21 - 24, 3, null)
    }

    test(11, !10) {
      check(!0 - 9, null, 0)
      check(!10, null, null)
    }

    test(10, !10) {
      check(!0 - 8, null, 0)
      check(!9, null, null)
    }

    test(10, !0) {
      check(!0, null, null)
      check(!1 - 9, 0, null)
    }

    test(10, !3 - 4, !5 - 6) {
      check(!0 - 2, null, 0)
      check(!3 - 4, null, 1)
      check(!5 - 6, 0, null)
      check(!7 - 9, 1, null)
    }

    test(10, !3 - 4, !5, !5 - 6) {
      check(!0 - 2, null, 0)
      check(!3 - 4, null, 1)
      check(!5 - 5, 0, null)
      check(!6 - 6, 1, null)
      check(!7 - 9, 2, null)
    }

    test(10, !3, !5, !6, !7) {
      check(!0 - 2, null, 0)
      check(!3 - 3, null, 1)
      check(!4 - 4, 0, 1)
      check(!5 - 5, 0, 2)
      check(!6 - 6, 1, 3)
      check(!7 - 7, 2, null)
      check(!8 - 9, 3, null)
    }
  }

  private fun test(total: Int, vararg changes: Range, handler: MyIterable.() -> Unit) {
    val testHelper = MyIterable(changes.asList(), total)
    testHelper.handler()

    // first and last line
    testHelper.checkPrev(0, null)
    testHelper.checkNext(total - 1, null)
    testHelper.checkNext(total, null)
  }
}

private class MyIterable(private val ranges: List<Range>, private val total: Int) : PrevNextDifferenceIterableBase<Range>() {
  private var current: Int = 0
  private var lastTarget: Int? = null

  override fun getChanges(): List<Range> = ranges
  override fun getStartLine(change: Range): Int = change.start
  override fun getEndLine(change: Range): Int = change.end
  override fun scrollToChange(change: Range) {
    lastTarget = ranges.indexOf(change)
  }

  override fun getEditor(): EditorEx {
    val document = Mockito.mock(DocumentEx::class.java)
    val editorEx = Mockito.mock(EditorEx::class.java)
    val caretModel = Mockito.mock(CaretModel::class.java)
    Mockito.`when`(document.lineCount).thenReturn(total)
    Mockito.`when`(editorEx.document).thenReturn(document)
    Mockito.`when`(editorEx.caretModel).thenReturn(caretModel)
    Mockito.`when`(caretModel.logicalPosition).thenReturn(LogicalPosition(current, 0))
    return editorEx
  }


  fun check(range: Range, prev: Int?, next: Int?) {
    for (current in range.start..range.end - 1) {
      check(current, prev, next)
    }
  }

  fun check(current: Int, prev: Int?, next: Int?) {
    checkPrev(current, prev)
    checkNext(current, next)
  }

  fun checkPrev(current: Int, prev: Int?) {
    this.current = current

    if (prev == null) {
      assert(!canGoPrev())
    }
    else {
      assert(canGoPrev())
      lastTarget = null
      goPrev()
      assertEquals(prev, lastTarget)
    }
  }

  fun checkNext(current: Int, next: Int?) {
    this.current = current

    if (next == null) {
      assert(!canGoNext())
    }
    else {
      assert(canGoNext())
      lastTarget = null
      goNext()
      assertEquals(next, lastTarget)
    }
  }
}

private data class Range(val start: Int, var end: Int = start)

private operator fun Int.not(): Range = Range(this)
private operator fun Range.minus(col: Int): Range = Range(this.start, col + 1)
