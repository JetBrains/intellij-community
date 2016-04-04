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
package com.intellij.diff.comparison

import com.intellij.diff.DiffTestCase
import com.intellij.diff.util.IntPair
import com.intellij.diff.util.MergeRange
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.Couple
import com.intellij.util.containers.ContainerUtil
import java.util.*

abstract class ComparisonMergeUtilTestBase : DiffTestCase() {
  private fun doCharTest(texts: Trio<Document>, expected: List<Change>?, matchings: Trio<BitSet>?) {
    val iterable1 = ByChar.compare(texts.data2.charsSequence, texts.data1.charsSequence, INDICATOR)
    val iterable2 = ByChar.compare(texts.data2.charsSequence, texts.data3.charsSequence, INDICATOR)

    val fragments = ComparisonMergeUtil.buildFair(iterable1, iterable2, INDICATOR)
    val actual = convertDiffFragments(fragments)

    checkConsistency(actual, texts)
    if (matchings != null) checkDiffMatching(actual, matchings)
    if (expected != null) checkDiffChanges(actual, expected)
  }

  private fun checkConsistency(actual: List<Change>, texts: Trio<Document>) {
    var lasts = Trio(-1, -1, -1)

    for (change in actual) {
      val starts = change.starts
      val ends = change.ends

      var empty = true
      var squashed = true
      ThreeSide.values().forEach {
        val start = starts(it)
        val end = ends(it)
        val last = lasts(it)

        assertTrue(last <= start)
        assertTrue(start <= end)
        empty = empty && (start == end)
        squashed = squashed && (start == last)
      }

      assertTrue(!empty)
      assertTrue(!squashed)

      lasts = ends
    }
  }

  private fun checkDiffChanges(actual: List<Change>, expected: List<Change>) {
    assertOrderedEquals(expected, actual)
  }

  private fun checkDiffMatching(changes: List<Change>, matchings: Trio<BitSet>) {
    val sets = Trio(BitSet(), BitSet(), BitSet())

    for (change in changes) {
      sets.forEach({ set: BitSet, side: ThreeSide -> set.set(change.start(side), change.end(side)) })
    }

    assertEquals(matchings.data1, sets.data1)
    assertEquals(matchings.data2, sets.data2)
    assertEquals(matchings.data3, sets.data3)
  }

  private fun convertDiffFragments(fragments: List<MergeRange>): List<Change> {
    return fragments.map {
      Change(
          it.start1, it.end1,
          it.start2, it.end2,
          it.start3, it.end3)
    }
  }


  internal enum class TestType {
    CHAR
  }

  internal inner class MergeTestBuilder(val type: TestType) {
    private var isExecuted: Boolean = false

    private var texts: Trio<Document>? = null

    private var changes: List<Change>? = null
    private var matching: Trio<BitSet>? = null

    fun assertExecuted() {
      assertTrue(isExecuted)
    }

    fun test() {
      isExecuted = true

      assertTrue(changes != null || matching != null)

      when (type) {
        TestType.CHAR -> doCharTest(texts!!, changes, matching)
      }
    }


    operator fun String.minus(v: String): Couple<String> {
      return Couple(this, v)
    }

    operator fun Couple<String>.minus(v: String): Helper {
      return Helper(Trio(this.first, this.second, v))
    }

    inner class Helper(val texts: Trio<String>) {
      init {
        val builder = this@MergeTestBuilder
        if (builder.texts == null) {
          builder.texts = texts.map { it -> DocumentImpl(it) }
        }
      }

      fun matching() {
        matching = texts.map { it -> parseMatching(it) }
      }
    }


    fun changes(vararg expected: Change): Unit {
      changes = ContainerUtil.list(*expected)
    }


    fun mod(line1: Int, line2: Int, line3: Int, count1: Int, count2: Int, count3: Int): Change {
      return Change(line1, line1 + count1, line2, line2 + count2, line3, line3 + count3)
    }
  }

  internal fun chars(f: MergeTestBuilder.() -> Unit) {
    doTest(TestType.CHAR, f)
  }

  internal fun doTest(type: TestType, f: MergeTestBuilder.() -> Unit) {
    val builder = MergeTestBuilder(type)
    builder.f()
    builder.assertExecuted()
  }


  class Change(start1: Int, end1: Int, start2: Int, end2: Int, start3: Int, end3: Int)
  : Trio<IntPair>(IntPair(start1, end1), IntPair(start2, end2), IntPair(start3, end3)) {

    val start1 = start(ThreeSide.LEFT)
    val start2 = start(ThreeSide.BASE)
    val start3 = start(ThreeSide.RIGHT)

    val end1 = end(ThreeSide.LEFT)
    val end2 = end(ThreeSide.BASE)
    val end3 = end(ThreeSide.RIGHT)

    val starts = Trio(start1, start2, start3)
    val ends = Trio(end1, end2, end3)

    fun start(side: ThreeSide): Int = this(side).val1
    fun end(side: ThreeSide): Int = this(side).val2

    override fun toString(): String {
      return "($start1, $end1) - ($start2, $end2) - ($start3, $end3)"
    }
  }
}
