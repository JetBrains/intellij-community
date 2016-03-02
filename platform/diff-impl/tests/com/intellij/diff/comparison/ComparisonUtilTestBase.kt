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
import com.intellij.diff.fragments.DiffFragment
import com.intellij.diff.fragments.LineFragment
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.Couple
import com.intellij.util.containers.ContainerUtil
import java.util.*

abstract class ComparisonUtilTestBase : DiffTestCase() {
  private fun doLineTest(before: Document, after: Document, matchings: Couple<BitSet>?, expected: List<Change>?, policy: ComparisonPolicy) {
    val fragments = MANAGER.compareLines(before.charsSequence, after.charsSequence, policy, INDICATOR)
    checkConsistency(fragments, before, after)
    if (matchings != null) checkLineMatching(fragments, matchings)
    if (expected != null) checkLineChanges(fragments, expected)
  }

  private fun doWordTest(before: Document, after: Document, matchings: Couple<BitSet>?, expected: List<Change>?, policy: ComparisonPolicy) {
    val rawFragments = MANAGER.compareLinesInner(before.charsSequence, after.charsSequence, policy, INDICATOR)
    val fragments = MANAGER.squash(rawFragments)
    checkConsistencyWord(fragments, before, after)

    val diffFragments = fragments[0].innerFragments!!
    if (matchings != null) checkDiffMatching(diffFragments, matchings)
    if (expected != null) checkDiffChanges(diffFragments, expected)
  }

  private fun doCharTest(before: Document, after: Document, matchings: Couple<BitSet>?, expected: List<Change>?, policy: ComparisonPolicy) {
    val fragments = MANAGER.compareChars(before.charsSequence, after.charsSequence, policy, INDICATOR)
    checkConsistency(fragments, before, after)
    if (matchings != null) checkDiffMatching(fragments, matchings)
    if (expected != null) checkDiffChanges(fragments, expected)
  }

  private fun doSplitterTest(before: Document, after: Document, squash: Boolean, trim: Boolean, expected: List<Change>?, policy: ComparisonPolicy) {
    val text1 = before.charsSequence
    val text2 = after.charsSequence

    var fragments = MANAGER.compareLinesInner(text1, text2, policy, INDICATOR)
    checkConsistency(fragments, before, after)

    fragments = MANAGER.processBlocks(fragments, text1, text2, policy, squash, trim)
    checkConsistency(fragments, before, after)

    if (expected != null) checkLineChanges(fragments, expected)
  }

  private fun checkConsistencyWord(fragments: List<LineFragment>, before: Document, after: Document) {
    assertTrue(fragments.size == 1)
    val fragment = fragments[0]

    assertTrue(fragment.startOffset1 == 0)
    assertTrue(fragment.startOffset2 == 0)
    assertTrue(fragment.endOffset1 == before.textLength)
    assertTrue(fragment.endOffset2 == after.textLength)

    // It could be null if there are no common words. We do not test such cases here.
    checkConsistency(fragment.innerFragments!!, before, after)
  }

  private fun checkConsistency(fragments: List<DiffFragment>, before: Document, after: Document) {
    for (fragment in fragments) {
      assertTrue(fragment.startOffset1 <= fragment.endOffset1)
      assertTrue(fragment.startOffset2 <= fragment.endOffset2)

      if (fragment is LineFragment) {
        assertTrue(fragment.startLine1 <= fragment.endLine1)
        assertTrue(fragment.startLine2 <= fragment.endLine2)

        assertTrue(fragment.startLine1 != fragment.endLine1 || fragment.startLine2 != fragment.endLine2)

        assertTrue(fragment.startLine1 >= 0)
        assertTrue(fragment.startLine2 >= 0)
        assertTrue(fragment.endLine1 <= getLineCount(before))
        assertTrue(fragment.endLine2 <= getLineCount(after))

        checkLineOffsets(fragment, before, after)

        val innerFragments = fragment.innerFragments
        innerFragments?.let { checkConsistency(innerFragments, before, after) }
      }
      else {
        assertTrue(fragment.startOffset1 != fragment.endOffset1 || fragment.startOffset2 != fragment.endOffset2)
      }
    }
  }

  private fun checkLineChanges(fragments: List<LineFragment>, expected: List<Change>) {
    val changes = convertLineFragments(fragments)
    assertOrderedEquals(changes, expected)
  }

  private fun checkDiffChanges(fragments: List<DiffFragment>, expected: List<Change>) {
    val changes = convertDiffFragments(fragments)
    assertOrderedEquals(changes, expected)
  }

  private fun checkLineMatching(fragments: List<LineFragment>, matchings: Couple<BitSet>) {
    val set1 = BitSet()
    val set2 = BitSet()
    for (fragment in fragments) {
      set1.set(fragment.startLine1, fragment.endLine1)
      set2.set(fragment.startLine2, fragment.endLine2)
    }

    assertEquals(matchings.first, set1)
    assertEquals(matchings.second, set2)
  }

  private fun checkDiffMatching(fragments: List<DiffFragment>, matchings: Couple<BitSet>) {
    val set1 = BitSet()
    val set2 = BitSet()
    for (fragment in fragments) {
      set1.set(fragment.startOffset1, fragment.endOffset1)
      set2.set(fragment.startOffset2, fragment.endOffset2)
    }

    assertEquals(matchings.first, set1)
    assertEquals(matchings.second, set2)
  }

  private fun convertDiffFragments(fragments: List<DiffFragment>): List<Change> {
    return fragments.map { Change(it.startOffset1, it.endOffset1, it.startOffset2, it.endOffset2) }
  }

  private fun convertLineFragments(fragments: List<LineFragment>): List<Change> {
    return fragments.map { Change(it.startLine1, it.endLine1, it.startLine2, it.endLine2) }
  }

  private fun checkLineOffsets(fragment: LineFragment, before: Document, after: Document) {
    checkLineOffsets(before, fragment.startLine1, fragment.endLine1, fragment.startOffset1, fragment.endOffset1)

    checkLineOffsets(after, fragment.startLine2, fragment.endLine2, fragment.startOffset2, fragment.endOffset2)
  }

  private fun checkLineOffsets(document: Document, startLine: Int, endLine: Int, startOffset: Int, endOffset: Int) {
    if (startLine != endLine) {
      assertEquals(document.getLineStartOffset(startLine), startOffset)
      var offset = document.getLineEndOffset(endLine - 1)
      if (offset < document.textLength) offset++
      assertEquals(offset, endOffset)
    }
    else {
      val offset = if (startLine == getLineCount(document)) document.textLength else document.getLineStartOffset(startLine)
      assertEquals(offset, startOffset)
      assertEquals(offset, endOffset)
    }
  }

  //
  // Test Builder
  //

  internal enum class TestType {
    LINE, WORD, CHAR, SPLITTER
  }

  internal inner class TestBuilder(private val type: TestType) {
    private var isExecuted: Boolean = false

    private var before: Document? = null
    private var after: Document? = null

    private var defaultChanges: List<Change>? = null
    private var trimChanges: List<Change>? = null
    private var ignoreChanges: List<Change>? = null

    private var defaultMatching: Couple<BitSet>? = null
    private var trimMatching: Couple<BitSet>? = null
    private var ignoreMatching: Couple<BitSet>? = null

    private var shouldSquash: Boolean = false
    private var shouldTrim: Boolean = false

    private fun changes(policy: ComparisonPolicy): List<Change>? = when (policy) {
      ComparisonPolicy.IGNORE_WHITESPACES -> ignoreChanges ?: trimChanges ?: defaultChanges
      ComparisonPolicy.TRIM_WHITESPACES -> trimChanges ?: defaultChanges
      ComparisonPolicy.DEFAULT -> defaultChanges
    }

    private fun matchings(policy: ComparisonPolicy): Couple<BitSet>? = when (policy) {
      ComparisonPolicy.IGNORE_WHITESPACES -> ignoreMatching ?: trimMatching ?: defaultMatching
      ComparisonPolicy.TRIM_WHITESPACES -> trimMatching ?: defaultMatching
      ComparisonPolicy.DEFAULT -> defaultMatching
    }

    fun assertExecuted() {
      assertTrue(isExecuted)
    }

    private fun run(policy: ComparisonPolicy) {
      try {
        isExecuted = true

        val change = changes(policy)
        val matchings = matchings(policy)
        assertTrue(change != null || matchings != null)

        when (type) {
          TestType.LINE -> doLineTest(before!!, after!!, matchings, change, policy)
          TestType.WORD -> doWordTest(before!!, after!!, matchings, change, policy)
          TestType.CHAR -> doCharTest(before!!, after!!, matchings, change, policy)
          TestType.SPLITTER -> {
            assertNull(matchings)
            doSplitterTest(before!!, after!!, shouldSquash, shouldTrim, change, policy)
          }
        }
      }
      catch (e: Throwable) {
        println("Policy: " + policy.name)
        throw e
      }
    }


    fun testAll() {
      testDefault()
      testTrim()
      testIgnore()
    }

    fun testDefault() {
      run(ComparisonPolicy.DEFAULT)
    }

    fun testTrim() {
      if (type == TestType.CHAR) return // not supported
      run(ComparisonPolicy.TRIM_WHITESPACES)
    }

    fun testIgnore() {
      run(ComparisonPolicy.IGNORE_WHITESPACES)
    }


    operator fun String.minus(v: String): Helper {
      return Helper(this, v)
    }

    inner class Helper(val before: String, val after: String) {
      init {
        val builder = this@TestBuilder
        if (builder.before == null && builder.after == null) {
          builder.before = DocumentImpl(parseSource(before))
          builder.after = DocumentImpl(parseSource(after))
        }
      }

      fun plainSource() {
        val builder = this@TestBuilder
        builder.before = DocumentImpl(before)
        builder.after = DocumentImpl(after)
      }

      fun default() {
        defaultMatching = parseMatching(before, after)
      }

      fun trim() {
        trimMatching = parseMatching(before, after)
      }

      fun ignore() {
        ignoreMatching = parseMatching(before, after)
      }

      private fun parseMatching(before: String, after: String): Couple<BitSet> {
        if (type == TestType.LINE) {
          val builder = this@TestBuilder
          return Couple.of(parseLineMatching(before, builder.before!!), parseLineMatching(after, builder.after!!))
        }
        else {
          return Couple.of(parseMatching(before), parseMatching(after))
        }
      }

      fun parseLineMatching(matching: String, document: Document): BitSet {
        assertEquals(matching.length, document.textLength)

        val lines1 = matching.split('_', '*')
        val lines2 = document.charsSequence.split('\n')
        assertEquals(lines1.size, lines2.size)
        for (i in 0..lines1.size - 1) {
          assertEquals(lines1[i].length, lines2[i].length, "line $i")
        }


        val set = BitSet()

        var index = 0
        var lineNumber = 0
        while (index < matching.length) {
          var end = matching.indexOfAny(listOf("_", "*"), index) + 1
          if (end == 0) end = matching.length

          val line = matching.subSequence(index, end)
          if (line.find { it != ' ' && it != '_' } != null) {
            assert(!line.contains(' '))
            set.set(lineNumber)
          }
          lineNumber++
          index = end
        }

        return set
      }
    }


    fun default(vararg expected: Change): Unit {
      defaultChanges = ContainerUtil.list(*expected)
    }

    fun trim(vararg expected: Change): Unit {
      trimChanges = ContainerUtil.list(*expected)
    }

    fun ignore(vararg expected: Change): Unit {
      ignoreChanges = ContainerUtil.list(*expected)
    }

    fun mod(line1: Int, line2: Int, count1: Int, count2: Int): Change {
      assert(count1 != 0)
      assert(count2 != 0)
      return Change(line1, line1 + count1, line2, line2 + count2)
    }

    fun del(line1: Int, line2: Int, count1: Int): Change {
      assert(count1 != 0)
      return Change(line1, line1 + count1, line2, line2)
    }

    fun ins(line1: Int, line2: Int, count2: Int): Change {
      assert(count2 != 0)
      return Change(line1, line1, line2, line2 + count2)
    }


    fun postprocess(squash: Boolean, trim: Boolean): Unit {
      shouldSquash = squash
      shouldTrim = trim
    }
  }

  internal fun lines(f: TestBuilder.() -> Unit): Unit = doTest(TestType.LINE, f)

  internal fun words(f: TestBuilder.() -> Unit): Unit = doTest(TestType.WORD, f)

  internal fun chars(f: TestBuilder.() -> Unit): Unit = doTest(TestType.CHAR, f)

  internal  fun splitter(squash: Boolean = false, trim: Boolean = false, f: TestBuilder.() -> Unit): Unit {
    doTest(TestType.SPLITTER, {
      postprocess(squash, trim)
      f()
    })
  }

  private fun doTest(type: TestType, f: TestBuilder.() -> Unit) {
    val builder = TestBuilder(type)
    builder.f()
    builder.assertExecuted()
  }

  //
  // Helpers
  //

  data class Change(val start1: Int, val end1: Int, val start2: Int, val end2: Int) {
    override fun toString(): String {
      return "($start1, $end1) - ($start2, $end2)"
    }
  }
}
