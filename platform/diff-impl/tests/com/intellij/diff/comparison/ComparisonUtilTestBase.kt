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

import com.intellij.diff.fragments.DiffFragment
import com.intellij.diff.fragments.LineFragment
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.containers.ContainerUtil
import java.util.BitSet
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public abstract class ComparisonUtilTestBase : UsefulTestCase() {
  private var oldRegistryValue: Boolean = false

  override fun setUp() {
    super.setUp()
    oldRegistryValue = REGISTRY.asBoolean()
    REGISTRY.setValue(true)
  }

  override fun tearDown() {
    REGISTRY.setValue(oldRegistryValue)
    super.tearDown()
  }

  //
  // Impl
  //

  private fun doLineTest(before: Document, after: Document, matchings: Couple<BitSet>?, expected: List<Change>?, policy: ComparisonPolicy) {
    val fragments = MANAGER.compareLines(before.getCharsSequence(), after.getCharsSequence(), policy, INDICATOR)
    checkConsistency(fragments, before, after)
    if (matchings != null) checkLineMatching(fragments, matchings)
    if (expected != null) checkLineChanges(fragments, expected)
  }

  private fun doWordTest(before: Document, after: Document, matchings: Couple<BitSet>?, expected: List<Change>?, policy: ComparisonPolicy) {
    val rawFragments = MANAGER.compareLinesInner(before.getCharsSequence(), after.getCharsSequence(), policy, INDICATOR)
    val fragments = MANAGER.squash(rawFragments)
    checkConsistencyWord(fragments, before, after)

    val diffFragments = fragments.get(0).getInnerFragments()!!
    if (matchings != null) checkDiffMatching(diffFragments, matchings)
    if (expected != null) checkDiffChanges(diffFragments, expected)
  }

  private fun doCharTest(before: Document, after: Document, matchings: Couple<BitSet>?, expected: List<Change>?, policy: ComparisonPolicy) {
    val fragments = MANAGER.compareChars(before.getCharsSequence(), after.getCharsSequence(), policy, INDICATOR)
    checkConsistency(fragments, before, after)
    if (matchings != null) checkDiffMatching(fragments, matchings)
    if (expected != null) checkDiffChanges(fragments, expected)
  }

  private fun doSplitterTest(before: Document, after: Document, matchings: Couple<BitSet>?, expected: List<Change>?, policy: ComparisonPolicy) {
    val fragments = MANAGER.compareLinesInner(before.getCharsSequence(), after.getCharsSequence(), policy, INDICATOR)
    checkConsistency(fragments, before, after)
    if (matchings != null) checkLineMatching(fragments, matchings)
    if (expected != null) checkLineChanges(fragments, expected)
  }

  private fun checkConsistencyWord(fragments: List<LineFragment>, before: Document, after: Document) {
    assertTrue(fragments.size() == 1)
    val fragment = fragments.get(0)

    assertTrue(fragment.getStartOffset1() == 0)
    assertTrue(fragment.getStartOffset2() == 0)
    assertTrue(fragment.getEndOffset1() == before.getTextLength())
    assertTrue(fragment.getEndOffset2() == after.getTextLength())

    // It could be null if there are no common words. We do not test such cases here.
    checkConsistency(fragment.getInnerFragments()!!, before, after)
  }

  private fun checkConsistency(fragments: List<DiffFragment>, before: Document, after: Document) {
    for (fragment in fragments) {
      assertTrue(fragment.getStartOffset1() <= fragment.getEndOffset1())
      assertTrue(fragment.getStartOffset2() <= fragment.getEndOffset2())

      if (fragment is LineFragment) {
        assertTrue(fragment.getStartLine1() <= fragment.getEndLine1())
        assertTrue(fragment.getStartLine2() <= fragment.getEndLine2())

        assertTrue(fragment.getStartLine1() != fragment.getEndLine1() || fragment.getStartLine2() != fragment.getEndLine2())

        assertTrue(fragment.getStartLine1() >= 0)
        assertTrue(fragment.getStartLine2() >= 0)
        assertTrue(fragment.getEndLine1() <= getLineCount(before))
        assertTrue(fragment.getEndLine2() <= getLineCount(after))

        checkLineOffsets(fragment, before, after)

        val innerFragments = fragment.getInnerFragments()
        innerFragments?.let { checkConsistency(innerFragments!!, before, after) }
      }
      else {
        assertTrue(fragment.getStartOffset1() != fragment.getEndOffset1() || fragment.getStartOffset2() != fragment.getEndOffset2())
      }
    }
  }

  private fun checkLineChanges(fragments: List<LineFragment>, expected: List<Change>) {
    val changes = convertLineFragments(fragments)
    UsefulTestCase.assertOrderedEquals(changes, expected)
  }

  private fun checkDiffChanges(fragments: List<DiffFragment>, expected: List<Change>) {
    val changes = convertDiffFragments(fragments)
    UsefulTestCase.assertOrderedEquals(changes, expected)
  }

  private fun checkLineMatching(fragments: List<LineFragment>, matchings: Couple<BitSet>) {
    val set1 = BitSet()
    val set2 = BitSet()
    for (fragment in fragments) {
      set1.set(fragment.getStartLine1(), fragment.getEndLine1())
      set2.set(fragment.getStartLine2(), fragment.getEndLine2())
    }

    assertEquals(matchings.first, set1)
    assertEquals(matchings.second, set2)
  }

  private fun checkDiffMatching(fragments: List<DiffFragment>, matchings: Couple<BitSet>) {
    val set1 = BitSet()
    val set2 = BitSet()
    for (fragment in fragments) {
      set1.set(fragment.getStartOffset1(), fragment.getEndOffset1())
      set2.set(fragment.getStartOffset2(), fragment.getEndOffset2())
    }

    assertEquals(matchings.first, set1)
    assertEquals(matchings.second, set2)
  }

  private fun convertDiffFragments(fragments: List<DiffFragment>): List<Change> {
    return fragments.map { Change(it.getStartOffset1(), it.getEndOffset1(), it.getStartOffset2(), it.getEndOffset2()) }
  }

  private fun convertLineFragments(fragments: List<LineFragment>): List<Change> {
    return fragments.map { Change(it.getStartLine1(), it.getEndLine1(), it.getStartLine2(), it.getEndLine2()) }
  }

  private fun checkLineOffsets(fragment: LineFragment, before: Document, after: Document) {
    checkLineOffsets(before, fragment.getStartLine1(), fragment.getEndLine1(), fragment.getStartOffset1(), fragment.getEndOffset1())

    checkLineOffsets(after, fragment.getStartLine2(), fragment.getEndLine2(), fragment.getStartOffset2(), fragment.getEndOffset2())
  }

  private fun checkLineOffsets(document: Document, startLine: Int, endLine: Int, startOffset: Int, endOffset: Int) {
    if (startLine != endLine) {
      assertEquals(document.getLineStartOffset(startLine), startOffset)
      var offset = document.getLineEndOffset(endLine - 1)
      if (offset < document.getTextLength()) offset++
      assertEquals(offset, endOffset)
    }
    else {
      val offset = if (startLine == getLineCount(document)) document.getTextLength() else document.getLineStartOffset(startLine)
      assertEquals(offset, startOffset)
      assertEquals(offset, endOffset)
    }
  }

  private fun getLineCount(document: Document): Int {
    return Math.max(1, document.getLineCount())
  }

  //
  // Test Builder
  //

  private enum class TestType {
    LINE, WORD, CHAR, SPLITTER
  }

  public inner class TestBuilder(private val type: TestType) {
    private var isExecuted: Boolean = false;

    private var before: Document? = null
    private var after: Document? = null

    private var defaultChanges: List<Change>? = null
    private var trimChanges: List<Change>? = null
    private var ignoreChanges: List<Change>? = null

    private var defaultMatching: Couple<BitSet>? = null
    private var trimMatching: Couple<BitSet>? = null
    private var ignoreMatching: Couple<BitSet>? = null

    private fun changes(policy: ComparisonPolicy): List<Change>? = when (policy) {
      ComparisonPolicy.IGNORE_WHITESPACES -> ignoreChanges ?: trimChanges ?: defaultChanges;
      ComparisonPolicy.TRIM_WHITESPACES -> trimChanges ?: defaultChanges;
      ComparisonPolicy.DEFAULT -> defaultChanges
    }

    private fun matchings(policy: ComparisonPolicy): Couple<BitSet>? = when (policy) {
      ComparisonPolicy.IGNORE_WHITESPACES -> ignoreMatching ?: trimMatching ?: defaultMatching;
      ComparisonPolicy.TRIM_WHITESPACES -> trimMatching ?: defaultMatching;
      ComparisonPolicy.DEFAULT -> defaultMatching
    }

    public fun assertExecuted() {
      assertTrue(isExecuted)
    }

    private fun run(policy: ComparisonPolicy) {
      try {
        isExecuted = true;

        val change = changes(policy)
        val matchings = matchings(policy)
        assertTrue(change != null || matchings != null)

        when (type) {
          TestType.LINE -> doLineTest(before!!, after!!, matchings, change, policy)
          TestType.WORD -> doWordTest(before!!, after!!, matchings, change, policy)
          TestType.CHAR -> doCharTest(before!!, after!!, matchings, change, policy)
          TestType.SPLITTER -> doSplitterTest(before!!, after!!, matchings, change, policy)
        }
      }
      catch (e: Throwable) {
        println("Policy: " + policy.name())
        throw e;
      }
    }

    private fun parseSource(string: String): Document = DocumentImpl(string.replace('_', '\n'))

    private fun parseMatching(before: String, after: String): Couple<BitSet> {
      return Couple.of(parseMatching(before), parseMatching(after))
    }

    private fun parseMatching(matching: String): BitSet {
      val set = BitSet()
      matching.forEachIndexed { i, c -> if (c != ' ') set.set(i) }
      return set
    }


    public fun testAll() {
      testDefault()
      testTrim()
      testIgnore()
    }

    public fun testDefault() {
      run(ComparisonPolicy.DEFAULT)
    }

    public fun testTrim() {
      if (type == TestType.CHAR) return // not supported
      run(ComparisonPolicy.TRIM_WHITESPACES)
    }

    public fun testIgnore() {
      run(ComparisonPolicy.IGNORE_WHITESPACES)
    }


    public fun String.minus(v: String): Helper {
      return Helper(this, v)
    }

    public inner class Helper(val before: String, val after: String) {
      init {
        val builder = this@TestBuilder
        if (builder.before == null && builder.after == null) {
          builder.before = parseSource(before)
          builder.after = parseSource(after)
        }
      }

      public fun default() {
        defaultMatching = parseMatching(before, after)
      }

      public fun trim() {
        trimMatching = parseMatching(before, after)
      }

      public fun ignore() {
        ignoreMatching = parseMatching(before, after)
      }
    }


    public fun default(vararg expected: Change): Unit {
      defaultChanges = ContainerUtil.list(*expected)
    }

    public fun trim(vararg expected: Change): Unit {
      trimChanges = ContainerUtil.list(*expected)
    }

    public fun ignore(vararg expected: Change): Unit {
      ignoreChanges = ContainerUtil.list(*expected)
    }

    public fun mod(line1: Int, line2: Int, count1: Int, count2: Int): Change {
      assert(count1 != 0)
      assert(count2 != 0)
      return Change(line1, line1 + count1, line2, line2 + count2)
    }

    public fun del(line1: Int, line2: Int, count1: Int): Change {
      assert(count1 != 0)
      return Change(line1, line1 + count1, line2, line2)
    }

    public fun ins(line1: Int, line2: Int, count2: Int): Change {
      assert(count2 != 0)
      return Change(line1, line1, line2, line2 + count2)
    }
  }

  public fun lines(f: TestBuilder.() -> Unit): Unit = doTest(TestType.LINE, f)

  public fun words(f: TestBuilder.() -> Unit): Unit = doTest(TestType.WORD, f)

  public fun chars(f: TestBuilder.() -> Unit): Unit = doTest(TestType.CHAR, f)

  public fun split(f: TestBuilder.() -> Unit): Unit = doTest(TestType.SPLITTER, f)

  private fun doTest(type: TestType, f: TestBuilder.() -> Unit) {
    val builder = TestBuilder(type)
    builder.f()
    builder.assertExecuted()
  }

  //
  // Helpers
  //

  public data class Change(val start1: Int, val end1: Int, val start2: Int, val end2: Int) {
    override fun toString(): String {
      return "(" + start1 + ", " + end1 + ") - (" + start2 + ", " + end2 + ")"
    }
  }

  companion object {
    private val REGISTRY = Registry.get("diff.verify.iterable");

    private val INDICATOR = DumbProgressIndicator.INSTANCE
    private val MANAGER = ComparisonManagerImpl()
  }
}
