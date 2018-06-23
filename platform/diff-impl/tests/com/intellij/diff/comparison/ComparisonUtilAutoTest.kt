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
import com.intellij.diff.HeavyDiffTestCase
import com.intellij.diff.fragments.DiffFragment
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.fragments.MergeLineFragment
import com.intellij.diff.fragments.MergeWordFragment
import com.intellij.diff.tools.util.base.HighlightPolicy
import com.intellij.diff.tools.util.base.IgnorePolicy
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Range
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.ex.createRanges

class ComparisonUtilAutoTest : HeavyDiffTestCase() {
  val RUNS = 30
  val MAX_LENGTH = 300

  fun testChar() {
    doTestChar(System.currentTimeMillis(), RUNS, MAX_LENGTH)
  }

  fun testWord() {
    doTestWord(System.currentTimeMillis(), RUNS, MAX_LENGTH)
  }

  fun testLine() {
    doTestLine(System.currentTimeMillis(), RUNS, MAX_LENGTH)
  }

  fun testLineSquashed() {
    doTestLineSquashed(System.currentTimeMillis(), RUNS, MAX_LENGTH)
  }

  fun testLineTrimSquashed() {
    doTestLineTrimSquashed(System.currentTimeMillis(), RUNS, MAX_LENGTH)
  }

  fun testExplicitBlocks() {
    doTestExplicitBlocks(System.currentTimeMillis(), RUNS, MAX_LENGTH)
  }

  fun testMerge() {
    doTestMerge(System.currentTimeMillis(), RUNS, MAX_LENGTH)
  }

  private fun doTestLine(seed: Long, runs: Int, maxLength: Int) {
    val policies = listOf(ComparisonPolicy.DEFAULT, ComparisonPolicy.TRIM_WHITESPACES, ComparisonPolicy.IGNORE_WHITESPACES)

    doTest(seed, runs, maxLength, policies) { text1, text2, policy, debugData ->
      val sequence1 = text1.charsSequence
      val sequence2 = text2.charsSequence

      val fragments = MANAGER.compareLinesInner(sequence1, sequence2, policy, INDICATOR)
      debugData.put("Fragments", fragments)

      checkResultLine(text1, text2, fragments, policy, true)
    }
  }

  private fun doTestLineSquashed(seed: Long, runs: Int, maxLength: Int) {
    val policies = listOf(ComparisonPolicy.DEFAULT, ComparisonPolicy.TRIM_WHITESPACES, ComparisonPolicy.IGNORE_WHITESPACES)

    doTest(seed, runs, maxLength, policies) { text1, text2, policy, debugData ->
      val sequence1 = text1.charsSequence
      val sequence2 = text2.charsSequence

      val fragments = MANAGER.compareLinesInner(sequence1, sequence2, policy, INDICATOR)
      debugData.put("Fragments", fragments)

      val squashedFragments = MANAGER.squash(fragments)
      debugData.put("Squashed Fragments", squashedFragments)

      checkResultLine(text1, text2, squashedFragments, policy, false)
    }
  }

  private fun doTestLineTrimSquashed(seed: Long, runs: Int, maxLength: Int) {
    val policies = listOf(ComparisonPolicy.DEFAULT, ComparisonPolicy.TRIM_WHITESPACES, ComparisonPolicy.IGNORE_WHITESPACES)

    doTest(seed, runs, maxLength, policies) { text1, text2, policy, debugData ->
      val sequence1 = text1.charsSequence
      val sequence2 = text2.charsSequence

      val fragments = MANAGER.compareLinesInner(sequence1, sequence2, policy, INDICATOR)
      debugData.put("Fragments", fragments)

      val processed = MANAGER.processBlocks(fragments, sequence1, sequence2, policy, true, true)
      debugData.put("Processed Fragments", processed)

      checkResultLine(text1, text2, processed, policy, false)
    }
  }

  private fun doTestChar(seed: Long, runs: Int, maxLength: Int) {
    val policies = listOf(ComparisonPolicy.DEFAULT, ComparisonPolicy.IGNORE_WHITESPACES)

    doTest(seed, runs, maxLength, policies) { text1, text2, policy, debugData ->
      val sequence1 = text1.charsSequence
      val sequence2 = text2.charsSequence

      val fragments = MANAGER.compareChars(sequence1, sequence2, policy, INDICATOR)
      debugData.put("Fragments", fragments)

      checkResultChar(sequence1, sequence2, fragments, policy)
    }
  }

  private fun doTestWord(seed: Long, runs: Int, maxLength: Int) {
    val policies = listOf(ComparisonPolicy.DEFAULT, ComparisonPolicy.TRIM_WHITESPACES, ComparisonPolicy.IGNORE_WHITESPACES)

    doTest(seed, runs, maxLength, policies) { text1, text2, policy, debugData ->
      val sequence1 = text1.charsSequence
      val sequence2 = text2.charsSequence

      val fragments = MANAGER.compareWords(sequence1, sequence2, policy, INDICATOR)
      debugData.put("Fragments", fragments)

      checkResultWord(sequence1, sequence2, fragments, policy)
    }
  }

  private fun doTestExplicitBlocks(seed: Long, runs: Int, maxLength: Int) {
    val ignorePolicies = listOf(IgnorePolicy.DEFAULT, IgnorePolicy.TRIM_WHITESPACES, IgnorePolicy.IGNORE_WHITESPACES, IgnorePolicy.IGNORE_WHITESPACES_CHUNKS)
    val highlightPolicies = listOf(HighlightPolicy.BY_LINE, HighlightPolicy.BY_WORD, HighlightPolicy.BY_WORD_SPLIT)

    doTest(seed, runs, maxLength) { text1, text2, debugData ->
      for (highlightPolicy in highlightPolicies) {
        for (ignorePolicy in ignorePolicies) {
          debugData.put("HighlightPolicy", highlightPolicy)
          debugData.put("IgnorePolicy", ignorePolicy)

          val sequence1 = text1.charsSequence
          val sequence2 = text2.charsSequence

          val ranges = createRanges(sequence2, sequence1).map { Range(it.vcsLine1, it.vcsLine2, it.line1, it.line2) }
          debugData.put("Ranges", ranges)

          val fragments = compareExplicitBlocks(sequence1, sequence2, ranges, highlightPolicy, ignorePolicy)
          debugData.put("Fragments", fragments)

          checkResultLine(text1, text2, fragments, ignorePolicy.comparisonPolicy, !highlightPolicy.isShouldSquash)
        }
      }
    }
  }

  private fun doTestMerge(seed: Long, runs: Int, maxLength: Int) {
    val policies = listOf(ComparisonPolicy.DEFAULT, ComparisonPolicy.TRIM_WHITESPACES, ComparisonPolicy.IGNORE_WHITESPACES)

    doTest3(seed, runs, maxLength, policies) { text1, text2, text3, policy, debugData ->
      val sequence1 = text1.charsSequence
      val sequence2 = text2.charsSequence
      val sequence3 = text3.charsSequence

      val fragments = MANAGER.compareLines(sequence1, sequence2, sequence3, policy, INDICATOR)

      val fineFragments = fragments.map { f ->
        val chunk1 = DiffUtil.getLinesContent(text1, f.startLine1, f.endLine1)
        val chunk2 = DiffUtil.getLinesContent(text2, f.startLine2, f.endLine2)
        val chunk3 = DiffUtil.getLinesContent(text3, f.startLine3, f.endLine3)

        val wordFragments = ByWord.compare(chunk1, chunk2, chunk3, policy, INDICATOR)
        Pair(f, wordFragments)
      }
      debugData.put("Fragments", fineFragments)

      checkResultMerge(text1, text2, text3, fineFragments, policy)
    }
  }

  private fun doTest(seed: Long, runs: Int, maxLength: Int, policies: List<ComparisonPolicy>,
                     test: (Document, Document, ComparisonPolicy, DiffTestCase.DebugData) -> Unit) {
    doTest(seed, runs, maxLength) { text1, text2, debugData ->
      for (comparisonPolicy in policies) {
        debugData.put("Policy", comparisonPolicy)
        test(text1, text2, comparisonPolicy, debugData)
      }
    }
  }

  private fun doTest(seed: Long, runs: Int, maxLength: Int,
                     test: (Document, Document, DiffTestCase.DebugData) -> Unit) {
    doAutoTest(seed, runs) { debugData ->
      debugData.put("MaxLength", maxLength)

      val text1 = DocumentImpl(generateText(maxLength))
      val text2 = DocumentImpl(generateText(maxLength))

      debugData.put("Text1", textToReadableFormat(text1.charsSequence))
      debugData.put("Text2", textToReadableFormat(text2.charsSequence))

      test(text1, text2, debugData)
    }
  }

  private fun doTest3(seed: Long, runs: Int, maxLength: Int, policies: List<ComparisonPolicy>,
                      test: (Document, Document, Document, ComparisonPolicy, DiffTestCase.DebugData) -> Unit) {
    doAutoTest(seed, runs) { debugData ->
      debugData.put("MaxLength", maxLength)

      val text1 = DocumentImpl(generateText(maxLength))
      val text2 = DocumentImpl(generateText(maxLength))
      val text3 = DocumentImpl(generateText(maxLength))

      debugData.put("Text1", textToReadableFormat(text1.charsSequence))
      debugData.put("Text2", textToReadableFormat(text2.charsSequence))
      debugData.put("Text3", textToReadableFormat(text3.charsSequence))

      for (comparisonPolicy in policies) {
        debugData.put("Policy", comparisonPolicy)
        test(text1, text2, text2, comparisonPolicy, debugData)
      }
    }
  }

  private fun checkResultLine(text1: Document, text2: Document, fragments: List<LineFragment>, policy: ComparisonPolicy, allowNonSquashed: Boolean) {
    checkLineConsistency(text1, text2, fragments, allowNonSquashed)

    for (fragment in fragments) {
      if (fragment.innerFragments != null) {
        val sequence1 = text1.subSequence(fragment.startOffset1, fragment.endOffset1)
        val sequence2 = text2.subSequence(fragment.startOffset2, fragment.endOffset2)

        checkResultWord(sequence1, sequence2, fragment.innerFragments!!, policy)
      }
    }

    checkValidRanges(text1.charsSequence, text2.charsSequence, fragments, policy, true)
    checkCantTrimLines(text1, text2, fragments, policy, allowNonSquashed)
  }

  private fun checkResultWord(text1: CharSequence, text2: CharSequence, fragments: List<DiffFragment>, policy: ComparisonPolicy) {
    checkDiffConsistency(fragments)
    checkValidRanges(text1, text2, fragments, policy, false)
  }

  private fun checkResultChar(text1: CharSequence, text2: CharSequence, fragments: List<DiffFragment>, policy: ComparisonPolicy) {
    checkDiffConsistency(fragments)
    checkValidRanges(text1, text2, fragments, policy, false)
  }

  private fun checkResultMerge(text1: Document,
                               text2: Document,
                               text3: Document,
                               fragments: List<Pair<MergeLineFragment, List<MergeWordFragment>>>,
                               policy: ComparisonPolicy) {
    val lineFragments = fragments.map { it.first }
    checkLineConsistency3(text1, text2, text3, lineFragments)

    checkValidRanges3(text1, text2, text3, lineFragments, policy)
    checkCantTrimLines3(text1, text2, text3, lineFragments, policy)

    for (pair in fragments) {
      val f = pair.first
      val innerFragments = pair.second
      val chunk1 = DiffUtil.getLinesContent(text1, f.startLine1, f.endLine1)
      val chunk2 = DiffUtil.getLinesContent(text2, f.startLine2, f.endLine2)
      val chunk3 = DiffUtil.getLinesContent(text3, f.startLine3, f.endLine3)

      checkDiffConsistency3(innerFragments)
      checkValidRanges3(chunk1, chunk2, chunk3, innerFragments, policy)
    }
  }

  private fun checkLineConsistency(text1: Document, text2: Document, fragments: List<LineFragment>, allowNonSquashed: Boolean) {
    var last1 = -1
    var last2 = -1

    for (fragment in fragments) {
      val startOffset1 = fragment.startOffset1
      val startOffset2 = fragment.startOffset2
      val endOffset1 = fragment.endOffset1
      val endOffset2 = fragment.endOffset2

      val start1 = fragment.startLine1
      val start2 = fragment.startLine2
      val end1 = fragment.endLine1
      val end2 = fragment.endLine2

      assertTrue(startOffset1 >= 0)
      assertTrue(startOffset2 >= 0)
      assertTrue(endOffset1 <= text1.textLength)
      assertTrue(endOffset2 <= text2.textLength)

      assertTrue(start1 >= 0)
      assertTrue(start2 >= 0)
      assertTrue(end1 <= getLineCount(text1))
      assertTrue(end2 <= getLineCount(text2))

      assertTrue(startOffset1 <= endOffset1)
      assertTrue(startOffset2 <= endOffset2)

      assertTrue(start1 <= end1)
      assertTrue(start2 <= end2)
      assertTrue(start1 != end1 || start2 != end2)

      assertTrue(allowNonSquashed || start1 != last1 || start2 != last2)

      checkLineOffsets(fragment, text1, text2)

      last1 = end1
      last2 = end2
    }
  }

  private fun checkLineConsistency3(text1: Document, text2: Document, text3: Document, fragments: List<MergeLineFragment>) {
    var last1 = -1
    var last2 = -1
    var last3 = -1

    for (fragment in fragments) {
      val start1 = fragment.startLine1
      val start2 = fragment.startLine2
      val start3 = fragment.startLine3
      val end1 = fragment.endLine1
      val end2 = fragment.endLine2
      val end3 = fragment.endLine3

      assertTrue(start1 >= 0)
      assertTrue(start2 >= 0)
      assertTrue(start3 >= 0)
      assertTrue(end1 <= getLineCount(text1))
      assertTrue(end2 <= getLineCount(text2))
      assertTrue(end3 <= getLineCount(text3))

      assertTrue(start1 <= end1)
      assertTrue(start2 <= end2)
      assertTrue(start3 <= end3)
      assertTrue(start1 != end1 || start2 != end2 || start3 != end3)

      assertTrue(start1 != last1 || start2 != last2 || start3 != last3)

      last1 = end1
      last2 = end2
      last3 = end3
    }
  }

  private fun checkDiffConsistency(fragments: List<DiffFragment>) {
    var last1 = -1
    var last2 = -1

    for (diffFragment in fragments) {
      val start1 = diffFragment.startOffset1
      val start2 = diffFragment.startOffset2
      val end1 = diffFragment.endOffset1
      val end2 = diffFragment.endOffset2

      assertTrue(start1 <= end1)
      assertTrue(start2 <= end2)
      assertTrue(start1 != end1 || start2 != end2)

      assertTrue(start1 != last1 || start2 != last2)

      last1 = end1
      last2 = end2
    }
  }

  private fun checkDiffConsistency3(fragments: List<MergeWordFragment>) {
    var last1 = -1
    var last2 = -1
    var last3 = -1

    for (diffFragment in fragments) {
      val start1 = diffFragment.startOffset1
      val start2 = diffFragment.startOffset2
      val start3 = diffFragment.startOffset3
      val end1 = diffFragment.endOffset1
      val end2 = diffFragment.endOffset2
      val end3 = diffFragment.endOffset3

      assertTrue(start1 <= end1)
      assertTrue(start2 <= end2)
      assertTrue(start3 <= end3)
      assertTrue(start1 != end1 || start2 != end2 || start3 != end3)

      assertTrue(start1 != last1 || start2 != last2 || start3 != last3)

      last1 = end1
      last2 = end2
      last3 = end3
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
      val offset = if (startLine == getLineCount(document))
        document.textLength
      else
        document.getLineStartOffset(startLine)
      assertEquals(offset, startOffset)
      assertEquals(offset, endOffset)
    }
  }

  private fun checkValidRanges(text1: CharSequence, text2: CharSequence, fragments: List<DiffFragment>, policy: ComparisonPolicy, skipNewline: Boolean) {
    // TODO: better check for Trim spaces case ?
    val ignoreSpacesUnchanged = policy != ComparisonPolicy.DEFAULT
    val ignoreSpacesChanged = policy == ComparisonPolicy.IGNORE_WHITESPACES

    var last1 = 0
    var last2 = 0
    for (fragment in fragments) {
      val start1 = fragment.startOffset1
      val start2 = fragment.startOffset2
      val end1 = fragment.endOffset1
      val end2 = fragment.endOffset2

      val chunk1 = text1.subSequence(last1, start1)
      val chunk2 = text2.subSequence(last2, start2)
      assertEqualsCharSequences(chunk1, chunk2, ignoreSpacesUnchanged, skipNewline)

      val chunkContent1 = text1.subSequence(start1, end1)
      val chunkContent2 = text2.subSequence(start2, end2)
      if (!skipNewline) {
        assertNotEqualsCharSequences(chunkContent1, chunkContent2, ignoreSpacesChanged, skipNewline)
      }

      last1 = fragment.endOffset1
      last2 = fragment.endOffset2
    }
    val chunk1 = text1.subSequence(last1, text1.length)
    val chunk2 = text2.subSequence(last2, text2.length)
    assertEqualsCharSequences(chunk1, chunk2, ignoreSpacesUnchanged, skipNewline)
  }

  private fun checkValidRanges3(text1: Document, text2: Document, text3: Document, fragments: List<MergeLineFragment>, policy: ComparisonPolicy) {
    val ignoreSpaces = policy != ComparisonPolicy.DEFAULT

    var last1 = 0
    var last2 = 0
    var last3 = 0
    for (fragment in fragments) {
      val start1 = fragment.startLine1
      val start2 = fragment.startLine2
      val start3 = fragment.startLine3

      val content1 = DiffUtil.getLinesContent(text1, last1, start1)
      val content2 = DiffUtil.getLinesContent(text2, last2, start2)
      val content3 = DiffUtil.getLinesContent(text3, last3, start3)

      assertEqualsCharSequences(content2, content1, ignoreSpaces, false)
      assertEqualsCharSequences(content2, content3, ignoreSpaces, false)

      last1 = fragment.endLine1
      last2 = fragment.endLine2
      last3 = fragment.endLine3
    }

    val content1 = DiffUtil.getLinesContent(text1, last1, getLineCount(text1))
    val content2 = DiffUtil.getLinesContent(text2, last2, getLineCount(text2))
    val content3 = DiffUtil.getLinesContent(text3, last3, getLineCount(text3))

    assertEqualsCharSequences(content2, content1, ignoreSpaces, false)
    assertEqualsCharSequences(content2, content3, ignoreSpaces, false)
  }

  private fun checkValidRanges3(text1: CharSequence, text2: CharSequence, text3: CharSequence, fragments: List<MergeWordFragment>, policy: ComparisonPolicy) {
    val ignoreSpacesUnchanged = policy != ComparisonPolicy.DEFAULT
    val ignoreSpacesChanged = policy == ComparisonPolicy.IGNORE_WHITESPACES

    var last1 = 0
    var last2 = 0
    var last3 = 0
    for (fragment in fragments) {
      val start1 = fragment.startOffset1
      val start2 = fragment.startOffset2
      val start3 = fragment.startOffset3
      val end1 = fragment.endOffset1
      val end2 = fragment.endOffset2
      val end3 = fragment.endOffset3

      val content1 = text1.subSequence(last1, start1)
      val content2 = text2.subSequence(last2, start2)
      val content3 = text3.subSequence(last3, start3)
      assertEqualsCharSequences(content2, content1, ignoreSpacesUnchanged, false)
      assertEqualsCharSequences(content2, content3, ignoreSpacesUnchanged, false)

      val chunkContent1 = text1.subSequence(start1, end1)
      val chunkContent2 = text2.subSequence(start2, end2)
      val chunkContent3 = text3.subSequence(start3, end3)
      assertFalse(isEqualsCharSequences(chunkContent2, chunkContent1, ignoreSpacesChanged) &&
                  isEqualsCharSequences(chunkContent2, chunkContent3, ignoreSpacesChanged))

      last1 = fragment.endOffset1
      last2 = fragment.endOffset2
      last3 = fragment.endOffset3
    }

    val content1 = text1.subSequence(last1, text1.length)
    val content2 = text2.subSequence(last2, text2.length)
    val content3 = text3.subSequence(last3, text3.length)

    assertEqualsCharSequences(content2, content1, ignoreSpacesUnchanged, false)
    assertEqualsCharSequences(content2, content3, ignoreSpacesUnchanged, false)
  }

  private fun checkCantTrimLines(text1: Document, text2: Document, fragments: List<LineFragment>, policy: ComparisonPolicy, allowNonSquashed: Boolean) {
    for (fragment in fragments) {
      val sequence1 = getFirstLastLines(text1, fragment.startLine1, fragment.endLine1)
      val sequence2 = getFirstLastLines(text2, fragment.startLine2, fragment.endLine2)
      if (sequence1 == null || sequence2 == null) continue

      checkNonEqualsIfLongEnough(sequence1.first, sequence2.first, policy, allowNonSquashed)
      checkNonEqualsIfLongEnough(sequence1.second, sequence2.second, policy, allowNonSquashed)
    }
  }

  private fun checkCantTrimLines3(text1: Document, text2: Document, text3: Document, fragments: List<MergeLineFragment>, policy: ComparisonPolicy) {
    for (fragment in fragments) {
      val sequence1 = getFirstLastLines(text1, fragment.startLine1, fragment.endLine1)
      val sequence2 = getFirstLastLines(text2, fragment.startLine2, fragment.endLine2)
      val sequence3 = getFirstLastLines(text3, fragment.startLine3, fragment.endLine3)
      if (sequence1 == null || sequence2 == null || sequence3 == null) continue

      assertFalse(MANAGER.isEquals(sequence2.first, sequence1.first, policy) && MANAGER.isEquals(sequence2.first, sequence3.first, policy))
      assertFalse(MANAGER.isEquals(sequence2.second, sequence1.second, policy) && MANAGER.isEquals(sequence2.second, sequence3.second, policy))
    }
  }

  private fun checkNonEqualsIfLongEnough(line1: CharSequence, line2: CharSequence, policy: ComparisonPolicy, allowNonSquashed: Boolean) {
    // in non-squashed blocks non-trimmed elements are possible
    if (allowNonSquashed) {
      if (policy != ComparisonPolicy.IGNORE_WHITESPACES) return
      if (countNonWhitespaceCharacters(line1) <= ComparisonUtil.getUnimportantLineCharCount()) return
      if (countNonWhitespaceCharacters(line2) <= ComparisonUtil.getUnimportantLineCharCount()) return
    }

    assertFalse(MANAGER.isEquals(line1, line2, policy))
  }

  private fun countNonWhitespaceCharacters(line: CharSequence): Int {
    return (0 until line.length).count { !StringUtil.isWhiteSpace(line[it]) }
  }

  private fun getFirstLastLines(text: Document, start: Int, end: Int): Couple<CharSequence>? {
    if (start == end) return null

    val firstLineRange = DiffUtil.getLinesRange(text, start, start + 1)
    val lastLineRange = DiffUtil.getLinesRange(text, end - 1, end)

    val firstLine = firstLineRange.subSequence(text.charsSequence)
    val lastLine = lastLineRange.subSequence(text.charsSequence)

    return Couple.of(firstLine, lastLine)
  }

  private fun Document.subSequence(start: Int, end: Int): CharSequence {
    return this.charsSequence.subSequence(start, end)
  }

  private val MergeLineFragment.startLine1: Int get() = this.getStartLine(ThreeSide.LEFT)
  private val MergeLineFragment.startLine2: Int get() = this.getStartLine(ThreeSide.BASE)
  private val MergeLineFragment.startLine3: Int get() = this.getStartLine(ThreeSide.RIGHT)
  private val MergeLineFragment.endLine1: Int get() = this.getEndLine(ThreeSide.LEFT)
  private val MergeLineFragment.endLine2: Int get() = this.getEndLine(ThreeSide.BASE)
  private val MergeLineFragment.endLine3: Int get() = this.getEndLine(ThreeSide.RIGHT)

  private val MergeWordFragment.startOffset1: Int get() = this.getStartOffset(ThreeSide.LEFT)
  private val MergeWordFragment.startOffset2: Int get() = this.getStartOffset(ThreeSide.BASE)
  private val MergeWordFragment.startOffset3: Int get() = this.getStartOffset(ThreeSide.RIGHT)
  private val MergeWordFragment.endOffset1: Int get() = this.getEndOffset(ThreeSide.LEFT)
  private val MergeWordFragment.endOffset2: Int get() = this.getEndOffset(ThreeSide.BASE)
  private val MergeWordFragment.endOffset3: Int get() = this.getEndOffset(ThreeSide.RIGHT)
}
