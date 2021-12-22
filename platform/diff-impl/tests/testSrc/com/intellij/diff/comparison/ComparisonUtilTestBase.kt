// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.comparison

import com.intellij.diff.DiffTestCase
import com.intellij.diff.fragments.DiffFragment
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.fragments.MergeWordFragment
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.Couple
import com.intellij.util.IntPair
import java.util.*

abstract class ComparisonUtilTestBase : DiffTestCase() {
  private fun doLineTest(text: Couple<Document>, matchings: Couple<BitSet>?, expected: List<Couple<IntPair>>?, policy: ComparisonPolicy) {
    val before = text.first
    val after = text.second
    val fragments = MANAGER.compareLines(before.charsSequence, after.charsSequence, policy, INDICATOR)
    checkConsistency(fragments, before, after)
    if (matchings != null) checkLineMatching(fragments, matchings)
    if (expected != null) checkLineChanges(fragments, expected)
  }

  private fun doLineInnerTest(text: Couple<Document>, matchings: Couple<BitSet>?, expected: List<Couple<IntPair>>?, policy: ComparisonPolicy) {
    val before = text.first
    val after = text.second
    val rawFragments = MANAGER.compareLinesInner(before.charsSequence, after.charsSequence, policy, INDICATOR)
    val fragments = MANAGER.squash(rawFragments)
    checkConsistencyLineInner(fragments, before, after)

    val diffFragments = fragments[0].innerFragments!!
    if (matchings != null) checkDiffMatching(diffFragments, matchings)
    if (expected != null) checkDiffChanges(diffFragments, expected)
  }

  private fun doWordTest(text: Couple<Document>, matchings: Couple<BitSet>?, expected: List<Couple<IntPair>>?, policy: ComparisonPolicy) {
    val before = text.first
    val after = text.second
    val fragments = MANAGER.compareWords(before.charsSequence, after.charsSequence, policy, INDICATOR)
    checkConsistency(fragments, before, after)

    if (matchings != null) checkDiffMatching(fragments, matchings)
    if (expected != null) checkDiffChanges(fragments, expected)
  }

  private fun doWordTest(text: Trio<Document>, matchings: Trio<BitSet>?, expected: List<Trio<IntPair>>?, policy: ComparisonPolicy) {
    val before = text.data1
    val base = text.data2
    val after = text.data3
    val fragments = ByWord.compare(before.charsSequence, base.charsSequence, after.charsSequence, policy, INDICATOR)
    checkConsistency(fragments)

    if (matchings != null) checkMergeMatching(fragments, matchings)
    if (expected != null) checkMergeChanges(fragments, expected)
  }

  private fun doCharTest(text: Couple<Document>, matchings: Couple<BitSet>?, expected: List<Couple<IntPair>>?, policy: ComparisonPolicy) {
    val before = text.first
    val after = text.second
    val fragments = MANAGER.compareChars(before.charsSequence, after.charsSequence, policy, INDICATOR)
    checkConsistency(fragments, before, after)
    if (matchings != null) checkDiffMatching(fragments, matchings)
    if (expected != null) checkDiffChanges(fragments, expected)
  }

  private fun doCharRawTest(text: Couple<Document>, matchings: Couple<BitSet>?, expected: List<Couple<IntPair>>?) {
    val before = text.first
    val after = text.second
    val iterable = ByChar.compare(before.charsSequence, after.charsSequence, INDICATOR)
    val fragments = ByWord.convertIntoDiffFragments(iterable)
    checkConsistency(fragments, before, after)
    if (matchings != null) checkDiffMatching(fragments, matchings)
    if (expected != null) checkDiffChanges(fragments, expected)
  }

  private fun doSplitterTest(text: Couple<Document>,
                             squash: Boolean,
                             trim: Boolean,
                             expected: List<Couple<IntPair>>?,
                             policy: ComparisonPolicy) {
    val before = text.first
    val after = text.second
    val text1 = before.charsSequence
    val text2 = after.charsSequence

    var fragments = MANAGER.compareLinesInner(text1, text2, policy, INDICATOR)
    checkConsistency(fragments, before, after)

    fragments = MANAGER.processBlocks(fragments, text1, text2, policy, squash, trim)
    checkConsistency(fragments, before, after)

    if (expected != null) checkLineChanges(fragments, expected)
  }

  private fun checkConsistencyLineInner(fragments: List<LineFragment>, before: Document, after: Document) {
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

  private fun checkConsistency(fragments: List<MergeWordFragment>) {
    for (fragment in fragments) {
      assertTrue(fragment.getStartOffset(ThreeSide.LEFT) <= fragment.getEndOffset(ThreeSide.LEFT))
      assertTrue(fragment.getStartOffset(ThreeSide.BASE) <= fragment.getEndOffset(ThreeSide.BASE))
      assertTrue(fragment.getStartOffset(ThreeSide.RIGHT) <= fragment.getEndOffset(ThreeSide.RIGHT))

      assertTrue(fragment.getStartOffset(ThreeSide.LEFT) != fragment.getEndOffset(ThreeSide.LEFT) ||
                 fragment.getStartOffset(ThreeSide.BASE) != fragment.getEndOffset(ThreeSide.BASE) ||
                 fragment.getStartOffset(ThreeSide.RIGHT) != fragment.getEndOffset(ThreeSide.RIGHT))
    }
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
    LINE, LINE_INNER, WORD, CHAR, CHAR_SMART, CHAR_RAW, SPLITTER
  }

  internal inner class TestBuilder(private val type: TestType) {
    private var isExecuted: Boolean = false

    private var text: Data<Document> = Data()
    private var changes: PolicyData<List<Data<IntPair>>> = PolicyData()
    private var matchings: PolicyData<Data<BitSet>> = PolicyData()

    private var shouldSquash: Boolean = false
    private var shouldTrim: Boolean = false

    fun assertExecuted() {
      assertTrue(isExecuted)
    }

    private fun run(policy: ComparisonPolicy) {
      try {
        isExecuted = true

        if (text.isTwoSide()) {
          val text = text.asCouple()
          val changes = changes.get(policy)?.map { it.asCouple() }
          val matchings = matchings.get(policy)?.asCouple()
          assertTrue(changes != null || matchings != null)

          when (type) {
            TestType.LINE -> doLineTest(text, matchings, changes, policy)
            TestType.LINE_INNER -> {
              doLineInnerTest(text, matchings, changes, policy)
              doWordTest(text, matchings, changes, policy)
            }
            TestType.WORD -> doWordTest(text, matchings, changes, policy)
            TestType.CHAR -> {
              doCharTest(text, matchings, changes, policy)
              if (policy == ComparisonPolicy.DEFAULT) doCharRawTest(text, matchings, changes)
            }
            TestType.CHAR_SMART -> {
              doCharTest(text, matchings, changes, policy)
            }
            TestType.CHAR_RAW -> {
              if (policy == ComparisonPolicy.DEFAULT) doCharRawTest(text, matchings, changes)
            }
            TestType.SPLITTER -> {
              assertNull(matchings)
              doSplitterTest(text, shouldSquash, shouldTrim, changes, policy)
            }
            else -> assert(false)
          }
        }
        else {
          val text = text.asTrio()
          val changes = changes.get(policy)?.map { it.asTrio() }
          val matchings = matchings.get(policy)?.asTrio()
          assertTrue(changes != null || matchings != null)

          when (type) {
            TestType.WORD -> doWordTest(text, matchings, changes, policy)
            else -> assert(false)
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
      run(ComparisonPolicy.TRIM_WHITESPACES)
    }

    fun testIgnore() {
      run(ComparisonPolicy.IGNORE_WHITESPACES)
    }


    operator fun String.minus(v: String): Helper {
      return Helper(this, v)
    }

    operator fun Helper.minus(v: String): Helper {
      return Helper(before, v, after)
    }

    inner class Helper(val before: String, val after: String, val base: String? = null) {
      init {
        val builder = this@TestBuilder
        if (builder.text.before == null && builder.text.after == null ||
            base != null && builder.text.base == null) {
          builder.text.before = DocumentImpl(parseSource(before))
          builder.text.after = DocumentImpl(parseSource(after))
          if (base != null) builder.text.base = DocumentImpl(parseSource(base))
        }
      }

      fun plainSource() {
        val builder = this@TestBuilder
        builder.text.before = DocumentImpl(before)
        builder.text.after = DocumentImpl(after)
        if (base != null) {
          builder.text.base = DocumentImpl(base)
        }
      }

      fun default() {
        assertNull(matchings.default)
        matchings.default = parseMatching(before, after, base)
      }

      fun trim() {
        assertNull(matchings.trim)
        matchings.trim = parseMatching(before, after, base)
      }

      fun ignore() {
        assertNull(matchings.ignore)
        matchings.ignore = parseMatching(before, after, base)
      }

      private fun parseMatching(before: String, after: String, base: String?): Data<BitSet> {
        val builder = this@TestBuilder
        if (type == TestType.LINE) {
          return Data(parseLineMatching(before, builder.text.before!!),
                      if (base != null) parseLineMatching(base, builder.text.base!!) else null,
                      parseLineMatching(after, builder.text.after!!))
        }
        else {
          return Data(parseMatching(before, builder.text.before!!),
                      if (base != null) parseMatching(base, builder.text.base!!) else null,
                      parseMatching(after, builder.text.after!!))
        }
      }
    }


    fun default(vararg expected: Couple<IntPair>) {
      assertNull(changes.default)
      changes.default = listOf(*expected).map { Data(it.first, it.second) }
    }

    fun trim(vararg expected: Couple<IntPair>) {
      assertNull(changes.trim)
      changes.trim = listOf(*expected).map { Data(it.first, it.second) }
    }

    fun ignore(vararg expected: Couple<IntPair>) {
      assertNull(changes.ignore)
      changes.ignore = listOf(*expected).map { Data(it.first, it.second) }
    }


    fun postprocess(squash: Boolean, trim: Boolean) {
      shouldSquash = squash
      shouldTrim = trim
    }
  }

  internal fun lines(f: TestBuilder.() -> Unit): Unit = doTest(TestType.LINE, f)

  internal fun lines_inner(f: TestBuilder.() -> Unit): Unit = doTest(TestType.LINE_INNER, f)

  internal fun words(f: TestBuilder.() -> Unit): Unit = doTest(TestType.WORD, f)

  internal fun chars(f: TestBuilder.() -> Unit): Unit = doTest(TestType.CHAR, f)

  internal fun chars_raw(f: TestBuilder.() -> Unit): Unit = doTest(TestType.CHAR_RAW, f)

  internal fun chars_smart(f: TestBuilder.() -> Unit): Unit = doTest(TestType.CHAR_SMART, f)

  internal fun splitter(squash: Boolean = false, trim: Boolean = false, f: TestBuilder.() -> Unit) {
    doTest(TestType.SPLITTER) {
      postprocess(squash, trim)
      f()
    }
  }

  private fun doTest(type: TestType, f: TestBuilder.() -> Unit) {
    val builder = TestBuilder(type)
    builder.f()
    builder.assertExecuted()
  }

  //
  // Helpers
  //

  private data class Data<T>(var before: T?, var base: T?, var after: T?) {
    constructor() : this(null, null, null)
    constructor(before: T?, after : T?) : this(before, null, after)
    fun isTwoSide(): Boolean = before != null && after != null && base == null
    fun isThreeSide(): Boolean = before != null && after != null && base != null
    fun asCouple(): Couple<T> {
      assert(isTwoSide())
      return Couple(before!!, after!!)
    }

    fun asTrio(): Trio<T> {
      assert(isThreeSide())
      return Trio(before!!, base!!, after!!)
    }
  }

  private data class PolicyData<T>(var default: T? = null, var trim: T? = null, var ignore: T? = null) {
    fun get(policy: ComparisonPolicy): T? =
      when (policy) {
        ComparisonPolicy.IGNORE_WHITESPACES -> ignore ?: trim ?: default
        ComparisonPolicy.TRIM_WHITESPACES -> trim ?: default
        ComparisonPolicy.DEFAULT -> default
      }
  }

  companion object {
    fun checkLineChanges(fragments: List<LineFragment>, expected: List<Couple<IntPair>>) {
      val changes = convertLineFragments(fragments)
      assertOrderedEquals(expected, changes)
    }

    fun checkDiffChanges(fragments: List<DiffFragment>, expected: List<Couple<IntPair>>) {
      val changes = convertDiffFragments(fragments)
      assertOrderedEquals(expected, changes)
    }

    fun checkMergeChanges(fragments: List<MergeWordFragment>, expected: List<Trio<IntPair>>) {
      val changes = convertMergeFragments(fragments)
      assertOrderedEquals(expected, changes)
    }

    fun checkLineMatching(fragments: List<LineFragment>, matchings: Couple<BitSet>) {
      val set1 = BitSet()
      val set2 = BitSet()
      for (fragment in fragments) {
        set1.set(fragment.startLine1, fragment.endLine1)
        set2.set(fragment.startLine2, fragment.endLine2)
      }

      assertSetsEquals(matchings.first, set1, "Before")
      assertSetsEquals(matchings.second, set2, "After")
    }

    fun checkDiffMatching(fragments: List<DiffFragment>, matchings: Couple<BitSet>) {
      val set1 = BitSet()
      val set2 = BitSet()
      for (fragment in fragments) {
        set1.set(fragment.startOffset1, fragment.endOffset1)
        set2.set(fragment.startOffset2, fragment.endOffset2)
      }

      assertSetsEquals(matchings.first, set1, "Before")
      assertSetsEquals(matchings.second, set2, "After")
    }

    fun checkMergeMatching(fragments: List<MergeWordFragment>, matchings: Trio<BitSet>) {
      val set1 = BitSet()
      val set2 = BitSet()
      val set3 = BitSet()
      for (fragment in fragments) {
        set1.set(fragment.getStartOffset(ThreeSide.LEFT), fragment.getEndOffset(ThreeSide.LEFT))
        set2.set(fragment.getStartOffset(ThreeSide.BASE), fragment.getEndOffset(ThreeSide.BASE))
        set3.set(fragment.getStartOffset(ThreeSide.RIGHT), fragment.getEndOffset(ThreeSide.RIGHT))
      }

      assertSetsEquals(matchings.data1, set1, "Left")
      assertSetsEquals(matchings.data2, set2, "Base")
      assertSetsEquals(matchings.data3, set3, "Right")
    }

    fun convertDiffFragments(fragments: List<DiffFragment>): List<Couple<IntPair>> {
      return fragments.map { Couple(IntPair(it.startOffset1, it.endOffset1), IntPair(it.startOffset2, it.endOffset2)) }
    }

    fun convertLineFragments(fragments: List<LineFragment>): List<Couple<IntPair>> {
      return fragments.map { Couple(IntPair(it.startLine1, it.endLine1), IntPair(it.startLine2, it.endLine2)) }
    }

    fun convertMergeFragments(fragments: List<MergeWordFragment>): List<Trio<IntPair>> {
      return fragments.map {
        Trio(IntPair(it.getStartOffset(ThreeSide.LEFT), it.getEndOffset(ThreeSide.LEFT)),
             IntPair(it.getStartOffset(ThreeSide.BASE), it.getEndOffset(ThreeSide.BASE)),
             IntPair(it.getStartOffset(ThreeSide.RIGHT), it.getEndOffset(ThreeSide.RIGHT)))
      }
    }


    fun mod(line1: Int, line2: Int, count1: Int, count2: Int): Couple<IntPair> {
      assert(count1 != 0)
      assert(count2 != 0)
      return Couple(IntPair(line1, line1 + count1), IntPair(line2, line2 + count2))
    }

    fun del(line1: Int, line2: Int, count1: Int): Couple<IntPair> {
      assert(count1 != 0)
      return Couple(IntPair(line1, line1 + count1), IntPair(line2, line2))
    }

    fun ins(line1: Int, line2: Int, count2: Int): Couple<IntPair> {
      assert(count2 != 0)
      return Couple(IntPair(line1, line1), IntPair(line2, line2 + count2))
    }
  }
}
