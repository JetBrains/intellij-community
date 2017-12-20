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
package com.intellij.diff.comparison;

import com.intellij.diff.comparison.iterables.DiffIterable;
import com.intellij.diff.comparison.iterables.DiffIterableUtil;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.fragments.*;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.IntPair;
import com.intellij.diff.util.MergeRange;
import com.intellij.diff.util.Range;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.text.CharSequenceSubSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import static com.intellij.diff.comparison.iterables.DiffIterableUtil.fair;
import static java.util.Collections.singletonList;

public class ComparisonManagerImpl extends ComparisonManager {
  private static final Logger LOG = Logger.getInstance(ComparisonManagerImpl.class);

  @NotNull
  public static ComparisonManagerImpl getInstanceImpl() {
    return (ComparisonManagerImpl)getInstance();
  }

  @NotNull
  @Override
  public List<LineFragment> compareLines(@NotNull CharSequence text1,
                                         @NotNull CharSequence text2,
                                         @NotNull ComparisonPolicy policy,
                                         @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    List<Line> lines1 = getLines(text1);
    List<Line> lines2 = getLines(text2);

    List<CharSequence> lineTexts1 = ContainerUtil.map(lines1, Line::getContent);
    List<CharSequence> lineTexts2 = ContainerUtil.map(lines2, Line::getContent);

    FairDiffIterable iterable = ByLine.compare(lineTexts1, lineTexts2, policy, indicator);
    return convertIntoLineFragments(lines1, lines2, iterable);
  }

  @NotNull
  @Override
  public List<MergeLineFragment> compareLines(@NotNull CharSequence text1,
                                              @NotNull CharSequence text2,
                                              @NotNull CharSequence text3,
                                              @NotNull ComparisonPolicy policy,
                                              @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    List<Line> lines1 = getLines(text1);
    List<Line> lines2 = getLines(text2);
    List<Line> lines3 = getLines(text3);

    List<CharSequence> lineTexts1 = ContainerUtil.map(lines1, Line::getContent);
    List<CharSequence> lineTexts2 = ContainerUtil.map(lines2, Line::getContent);
    List<CharSequence> lineTexts3 = ContainerUtil.map(lines3, Line::getContent);

    List<MergeRange> ranges = ByLine.compare(lineTexts1, lineTexts2, lineTexts3, policy, indicator);
    return convertIntoMergeLineFragments(ranges);
  }

  @NotNull
  @Override
  public List<LineFragment> compareLinesInner(@NotNull CharSequence text1,
                                              @NotNull CharSequence text2,
                                              @NotNull ComparisonPolicy policy,
                                              @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    List<LineFragment> lineFragments = compareLines(text1, text2, policy, indicator);
    return createInnerFragments(lineFragments, text1, text2, policy, indicator);
  }

  private static List<LineFragment> createInnerFragments(@NotNull List<LineFragment> lineFragments,
                                                         @NotNull CharSequence text1,
                                                         @NotNull CharSequence text2,
                                                         @NotNull ComparisonPolicy policy,
                                                         @NotNull ProgressIndicator indicator) {
    List<LineFragment> result = new ArrayList<>(lineFragments.size());

    int tooBigChunksCount = 0;
    for (LineFragment fragment : lineFragments) {
      assert fragment.getInnerFragments() == null;

      try {
        // Do not try to build fine blocks after few fails
        boolean tryComputeDifferences = tooBigChunksCount < FilesTooBigForDiffException.MAX_BAD_LINES;
        result.addAll(createInnerFragments(fragment, text1, text2, policy, indicator, tryComputeDifferences));
      }
      catch (DiffTooBigException e) {
        result.add(fragment);
        tooBigChunksCount++;
      }
    }

    return result;
  }

  @NotNull
  private static List<LineFragment> createInnerFragments(@NotNull LineFragment fragment,
                                                         @NotNull CharSequence text1,
                                                         @NotNull CharSequence text2,
                                                         @NotNull ComparisonPolicy policy,
                                                         @NotNull ProgressIndicator indicator,
                                                         boolean tryComputeDifferences) throws DiffTooBigException {
    CharSequence subSequence1 = text1.subSequence(fragment.getStartOffset1(), fragment.getEndOffset1());
    CharSequence subSequence2 = text2.subSequence(fragment.getStartOffset2(), fragment.getEndOffset2());

    if (fragment.getStartLine1() == fragment.getEndLine1() ||
        fragment.getStartLine2() == fragment.getEndLine2()) { // Insertion / Deletion
      if (ComparisonUtil.isEquals(subSequence1, subSequence2, policy)) {
        return singletonList(new LineFragmentImpl(fragment, Collections.emptyList()));
      }
      else {
        return singletonList(fragment);
      }
    }

    if (!tryComputeDifferences) return singletonList(fragment);

    List<ByWord.LineBlock> lineBlocks = ByWord.compareAndSplit(subSequence1, subSequence2, policy, indicator);
    assert lineBlocks.size() != 0;

    int startOffset1 = fragment.getStartOffset1();
    int startOffset2 = fragment.getStartOffset2();

    int currentStartLine1 = fragment.getStartLine1();
    int currentStartLine2 = fragment.getStartLine2();

    List<LineFragment> chunks = new ArrayList<>();
    for (int i = 0; i < lineBlocks.size(); i++) {
      ByWord.LineBlock block = lineBlocks.get(i);
      Range offsets = block.offsets;

      // special case for last line to void problem with empty last line
      int currentEndLine1 = i != lineBlocks.size() - 1 ? currentStartLine1 + block.newlines1 : fragment.getEndLine1();
      int currentEndLine2 = i != lineBlocks.size() - 1 ? currentStartLine2 + block.newlines2 : fragment.getEndLine2();

      chunks.add(new LineFragmentImpl(currentStartLine1, currentEndLine1, currentStartLine2, currentEndLine2,
                                      offsets.start1 + startOffset1, offsets.end1 + startOffset1,
                                      offsets.start2 + startOffset2, offsets.end2 + startOffset2,
                                      block.fragments));

      currentStartLine1 = currentEndLine1;
      currentStartLine2 = currentEndLine2;
    }
    return chunks;
  }

  @NotNull
  @Override
  @Deprecated
  public List<LineFragment> compareLinesInner(@NotNull CharSequence text1,
                                              @NotNull CharSequence text2,
                                              @NotNull List<LineFragment> lineFragments,
                                              @NotNull ComparisonPolicy policy,
                                              @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    return compareLinesInner(text1, text2, policy, indicator);
  }

  @NotNull
  @Override
  public List<DiffFragment> compareWords(@NotNull CharSequence text1,
                                         @NotNull CharSequence text2,
                                         @NotNull ComparisonPolicy policy,
                                         @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    return ByWord.compare(text1, text2, policy, indicator);
  }

  @NotNull
  @Override
  public List<DiffFragment> compareChars(@NotNull CharSequence text1,
                                         @NotNull CharSequence text2,
                                         @NotNull ComparisonPolicy policy,
                                         @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    if (policy == ComparisonPolicy.IGNORE_WHITESPACES) {
      return convertIntoDiffFragments(ByChar.compareIgnoreWhitespaces(text1, text2, indicator));
    }
    if (policy == ComparisonPolicy.DEFAULT) {
      return convertIntoDiffFragments(ByChar.compareTwoStep(text1, text2, indicator));
    }
    LOG.warn(policy.toString() + " is not supported by ByChar comparison");
    return convertIntoDiffFragments(ByChar.compareTwoStep(text1, text2, indicator));
  }

  @Override
  public boolean isEquals(@NotNull CharSequence text1, @NotNull CharSequence text2, @NotNull ComparisonPolicy policy) {
    return ComparisonUtil.isEquals(text1, text2, policy);
  }

  //
  // Fragments
  //

  @NotNull
  public static List<DiffFragment> convertIntoDiffFragments(@NotNull DiffIterable changes) {
    final List<DiffFragment> fragments = new ArrayList<>();
    for (Range ch : changes.iterateChanges()) {
      fragments.add(new DiffFragmentImpl(ch.start1, ch.end1, ch.start2, ch.end2));
    }
    return fragments;
  }

  @NotNull
  public static List<LineFragment> convertIntoLineFragments(@NotNull List<Line> lines1,
                                                            @NotNull List<Line> lines2,
                                                            @NotNull FairDiffIterable changes) {
    List<LineFragment> fragments = new ArrayList<>();
    for (Range ch : changes.iterateChanges()) {
      IntPair offsets1 = getOffsets(lines1, ch.start1, ch.end1);
      IntPair offsets2 = getOffsets(lines2, ch.start2, ch.end2);

      fragments.add(new LineFragmentImpl(ch.start1, ch.end1, ch.start2, ch.end2,
                                         offsets1.val1, offsets1.val2, offsets2.val1, offsets2.val2));
    }
    return fragments;
  }

  @NotNull
  private static IntPair getOffsets(@NotNull List<Line> lines, int startIndex, int endIndex) {
    if (startIndex == endIndex) {
      int offset;
      if (startIndex < lines.size()) {
        offset = lines.get(startIndex).getOffset1();
      }
      else {
        offset = lines.get(lines.size() - 1).getOffset2();
      }
      return new IntPair(offset, offset);
    }
    else {
      int offset1 = lines.get(startIndex).getOffset1();
      int offset2 = lines.get(endIndex - 1).getOffset2();
      return new IntPair(offset1, offset2);
    }
  }

  @NotNull
  public static List<MergeLineFragment> convertIntoMergeLineFragments(@NotNull List<MergeRange> conflicts) {
    return ContainerUtil.map(conflicts, ch -> new MergeLineFragmentImpl(ch.start1, ch.end1, ch.start2, ch.end2, ch.start3, ch.end3));
  }

  @NotNull
  public static List<MergeWordFragment> convertIntoMergeWordFragments(@NotNull List<MergeRange> conflicts) {
    return ContainerUtil.map(conflicts, ch -> new MergeWordFragmentImpl(ch.start1, ch.end1, ch.start2, ch.end2, ch.start3, ch.end3));
  }

  //
  // Post process line fragments
  //

  @NotNull
  @Override
  public List<LineFragment> squash(@NotNull List<LineFragment> oldFragments) {
    if (oldFragments.isEmpty()) return oldFragments;

    final List<LineFragment> newFragments = new ArrayList<>();
    processAdjoining(oldFragments, fragments -> newFragments.add(doSquash(fragments)));
    return newFragments;
  }

  @NotNull
  @Override
  public List<LineFragment> processBlocks(@NotNull List<LineFragment> oldFragments,
                                          @NotNull final CharSequence text1, @NotNull final CharSequence text2,
                                          @NotNull final ComparisonPolicy policy,
                                          final boolean squash, final boolean trim) {
    if (!squash && !trim) return oldFragments;
    if (oldFragments.isEmpty()) return oldFragments;

    final List<LineFragment> newFragments = new ArrayList<>();
    processAdjoining(oldFragments, fragments -> newFragments.addAll(processAdjoining(fragments, text1, text2, policy, squash, trim)));
    return newFragments;
  }

  private static void processAdjoining(@NotNull List<LineFragment> oldFragments,
                                       @NotNull Consumer<List<LineFragment>> consumer) {
    int startIndex = 0;
    for (int i = 1; i < oldFragments.size(); i++) {
      if (!isAdjoining(oldFragments.get(i - 1), oldFragments.get(i))) {
        consumer.consume(oldFragments.subList(startIndex, i));
        startIndex = i;
      }
    }
    if (startIndex < oldFragments.size()) {
      consumer.consume(oldFragments.subList(startIndex, oldFragments.size()));
    }
  }

  @NotNull
  private static List<LineFragment> processAdjoining(@NotNull List<LineFragment> fragments,
                                                     @NotNull CharSequence text1, @NotNull CharSequence text2,
                                                     @NotNull ComparisonPolicy policy, boolean squash, boolean trim) {
    int start = 0;
    int end = fragments.size();

    // TODO: trim empty leading/trailing lines
    if (trim && policy == ComparisonPolicy.IGNORE_WHITESPACES) {
      while (start < end) {
        LineFragment fragment = fragments.get(start);
        CharSequenceSubSequence sequence1 = new CharSequenceSubSequence(text1, fragment.getStartOffset1(), fragment.getEndOffset1());
        CharSequenceSubSequence sequence2 = new CharSequenceSubSequence(text2, fragment.getStartOffset2(), fragment.getEndOffset2());

        if ((fragment.getInnerFragments() == null || !fragment.getInnerFragments().isEmpty()) &&
            !StringUtil.equalsIgnoreWhitespaces(sequence1, sequence2)) {
          break;
        }
        start++;
      }
      while (start < end) {
        LineFragment fragment = fragments.get(end - 1);
        CharSequenceSubSequence sequence1 = new CharSequenceSubSequence(text1, fragment.getStartOffset1(), fragment.getEndOffset1());
        CharSequenceSubSequence sequence2 = new CharSequenceSubSequence(text2, fragment.getStartOffset2(), fragment.getEndOffset2());

        if ((fragment.getInnerFragments() == null || !fragment.getInnerFragments().isEmpty()) &&
            !StringUtil.equalsIgnoreWhitespaces(sequence1, sequence2)) {
          break;
        }
        end--;
      }
    }

    if (start == end) return Collections.emptyList();
    if (squash) {
      return singletonList(doSquash(fragments.subList(start, end)));
    }
    return fragments.subList(start, end);
  }

  @NotNull
  private static LineFragment doSquash(@NotNull List<LineFragment> oldFragments) {
    assert !oldFragments.isEmpty();
    if (oldFragments.size() == 1) return oldFragments.get(0);

    LineFragment firstFragment = oldFragments.get(0);
    LineFragment lastFragment = oldFragments.get(oldFragments.size() - 1);

    List<DiffFragment> newInnerFragments = new ArrayList<>();
    for (LineFragment fragment : oldFragments) {
      for (DiffFragment innerFragment : extractInnerFragments(fragment)) {
        int shift1 = fragment.getStartOffset1() - firstFragment.getStartOffset1();
        int shift2 = fragment.getStartOffset2() - firstFragment.getStartOffset2();

        DiffFragment previousFragment = ContainerUtil.getLastItem(newInnerFragments);
        if (previousFragment == null || !isAdjoiningInner(previousFragment, innerFragment, shift1, shift2)) {
          newInnerFragments.add(new DiffFragmentImpl(innerFragment.getStartOffset1() + shift1, innerFragment.getEndOffset1() + shift1,
                                                     innerFragment.getStartOffset2() + shift2, innerFragment.getEndOffset2() + shift2));
        }
        else {
          newInnerFragments.remove(newInnerFragments.size() - 1);
          newInnerFragments.add(new DiffFragmentImpl(previousFragment.getStartOffset1(), innerFragment.getEndOffset1() + shift1,
                                                     previousFragment.getStartOffset2(), innerFragment.getEndOffset2() + shift2));
        }
      }
    }

    return new LineFragmentImpl(firstFragment.getStartLine1(), lastFragment.getEndLine1(),
                                firstFragment.getStartLine2(), lastFragment.getEndLine2(),
                                firstFragment.getStartOffset1(), lastFragment.getEndOffset1(),
                                firstFragment.getStartOffset2(), lastFragment.getEndOffset2(),
                                newInnerFragments);
  }

  private static boolean isAdjoining(@NotNull LineFragment beforeFragment, @NotNull LineFragment afterFragment) {
    if (beforeFragment.getEndLine1() != afterFragment.getStartLine1() ||
        beforeFragment.getEndLine2() != afterFragment.getStartLine2() ||
        beforeFragment.getEndOffset1() != afterFragment.getStartOffset1() ||
        beforeFragment.getEndOffset2() != afterFragment.getStartOffset2()) {
      return false;
    }

    return true;
  }

  private static boolean isAdjoiningInner(@NotNull DiffFragment beforeFragment, @NotNull DiffFragment afterFragment,
                                          int shift1, int shift2) {
    if (beforeFragment.getEndOffset1() != afterFragment.getStartOffset1() + shift1 ||
        beforeFragment.getEndOffset2() != afterFragment.getStartOffset2() + shift2) {
      return false;
    }

    return true;
  }

  @NotNull
  private static List<DiffFragment> extractInnerFragments(@NotNull LineFragment lineFragment) {
    if (lineFragment.getInnerFragments() != null) return lineFragment.getInnerFragments();

    int length1 = lineFragment.getEndOffset1() - lineFragment.getStartOffset1();
    int length2 = lineFragment.getEndOffset2() - lineFragment.getStartOffset2();
    return singletonList(new DiffFragmentImpl(0, length1, 0, length2));
  }

  @NotNull
  private static List<Line> getLines(@NotNull CharSequence text) {
    List<Line> lines = new ArrayList<>();

    int offset = 0;
    while (true) {
      int lineEnd = StringUtil.indexOf(text, '\n', offset);
      if (lineEnd != -1) {
        lines.add(new Line(text, offset, lineEnd, true));
        offset = lineEnd + 1;
      }
      else {
        lines.add(new Line(text, offset, text.length(), false));
        break;
      }
    }

    return lines;
  }


  /**
   * Compare two texts by-line and then compare changed fragments by-word
   */
  @NotNull
  public List<LineFragment> compareLinesWithIgnoredRanges(@NotNull CharSequence text1,
                                                          @NotNull CharSequence text2,
                                                          @NotNull List<TextRange> ignoredRanges1,
                                                          @NotNull List<TextRange> ignoredRanges2,
                                                          boolean innerFragments,
                                                          @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    BitSet ignored1 = collectIgnoredRanges(ignoredRanges1);
    BitSet ignored2 = collectIgnoredRanges(ignoredRanges2);

    List<Line> lines1 = getLines(text1);
    List<Line> lines2 = getLines(text2);

    List<CharSequence> lineTexts1 = ContainerUtil.map(lines1, line -> line.getNotIgnoredContent(ignored1));
    List<CharSequence> lineTexts2 = ContainerUtil.map(lines2, line -> line.getNotIgnoredContent(ignored2));
    FairDiffIterable iterable = ByLine.compare(lineTexts1, lineTexts2, ComparisonPolicy.DEFAULT, indicator);

    FairDiffIterable correctedIterable = correctIgnoredRangesSecondStep(iterable, lines1, lines2, ignored1, ignored2);

    List<LineFragment> lineFragments = convertIntoLineFragments(lines1, lines2, correctedIterable);

    if (innerFragments) {
      lineFragments = createInnerFragments(lineFragments, text1, text2, ComparisonPolicy.DEFAULT, indicator);
    }

    return ContainerUtil.mapNotNull(lineFragments, fragment -> {
      return trimIgnoredChanges(fragment, lines1, lines2, ignored1, ignored2);
    });
  }

  @NotNull
  public static BitSet collectIgnoredRanges(@NotNull List<TextRange> ignoredRanges) {
    BitSet set = new BitSet();
    for (TextRange range : ignoredRanges) {
      set.set(range.getStartOffset(), range.getEndOffset());
    }
    return set;
  }

  @NotNull
  private static FairDiffIterable correctIgnoredRangesSecondStep(@NotNull FairDiffIterable iterable,
                                                                 @NotNull List<Line> lines1,
                                                                 @NotNull List<Line> lines2,
                                                                 @NotNull BitSet ignored1,
                                                                 @NotNull BitSet ignored2) {
    DiffIterableUtil.ChangeBuilder builder = new DiffIterableUtil.ChangeBuilder(lines1.size(), lines2.size());
    for (Range range : iterable.iterateUnchanged()) {
      int count = range.end1 - range.start1;
      for (int i = 0; i < count; i++) {
        int index1 = range.start1 + i;
        int index2 = range.start2 + i;
        if (areIgnoredEqualLines(lines1.get(index1), lines2.get(index2), ignored1, ignored2)) {
          builder.markEqual(index1, index2);
        }
      }
    }
    return fair(builder.finish());
  }

  @Nullable
  private static LineFragment trimIgnoredChanges(@NotNull LineFragment fragment,
                                                 @NotNull List<Line> lines1,
                                                 @NotNull List<Line> lines2,
                                                 @NotNull BitSet ignored1,
                                                 @NotNull BitSet ignored2) {
    // trim ignored lines
    Range range = TrimUtil.trimExpandList(lines1, lines2,
                                          fragment.getStartLine1(), fragment.getStartLine2(),
                                          fragment.getEndLine1(), fragment.getEndLine2(),
                                          (line1, line2) -> areIgnoredEqualLines(line1, line2, ignored1, ignored2),
                                          line -> isIgnoredLine(line, ignored1),
                                          line -> isIgnoredLine(line, ignored2));

    int startLine1 = range.start1;
    int startLine2 = range.start2;
    int endLine1 = range.end1;
    int endLine2 = range.end2;

    if (startLine1 == endLine1 && startLine2 == endLine2) return null;

    IntPair offsets1 = getOffsets(lines1, startLine1, endLine1);
    IntPair offsets2 = getOffsets(lines2, startLine2, endLine2);
    int startOffset1 = offsets1.val1;
    int endOffset1 = offsets1.val2;
    int startOffset2 = offsets2.val1;
    int endOffset2 = offsets2.val2;

    List<DiffFragment> newInner = null;
    if (fragment.getInnerFragments() != null) {
      int shift1 = startOffset1 - fragment.getStartOffset1();
      int shift2 = startOffset2 - fragment.getStartOffset2();
      int newCount1 = endOffset1 - startOffset1;
      int newCount2 = endOffset2 - startOffset2;

      newInner = ContainerUtil.mapNotNull(fragment.getInnerFragments(), it -> {
        // update offsets, as some lines might have been ignored completely
        int start1 = DiffUtil.bound(it.getStartOffset1() - shift1, 0, newCount1);
        int start2 = DiffUtil.bound(it.getStartOffset2() - shift2, 0, newCount2);
        int end1 = DiffUtil.bound(it.getEndOffset1() - shift1, 0, newCount1);
        int end2 = DiffUtil.bound(it.getEndOffset2() - shift2, 0, newCount2);

        // trim inner fragments
        TextRange range1 = trimIgnoredRange(start1, end1, ignored1, startOffset1);
        TextRange range2 = trimIgnoredRange(start2, end2, ignored2, startOffset2);

        if (range1.isEmpty() && range2.isEmpty()) return null;
        return new DiffFragmentImpl(range1.getStartOffset(), range1.getEndOffset(),
                                    range2.getStartOffset(), range2.getEndOffset());
      });
      if (newInner.isEmpty()) return null;
    }

    return new LineFragmentImpl(startLine1, endLine1, startLine2, endLine2,
                                startOffset1, endOffset1, startOffset2, endOffset2,
                                newInner);
  }

  private static boolean isIgnoredLine(@NotNull Line line, @NotNull BitSet ignored) {
    return isIgnoredRange(ignored, line.getOffset1(), line.getOffset2());
  }

  private static boolean areIgnoredEqualLines(@NotNull Line line1, @NotNull Line line2,
                                              @NotNull BitSet ignored1, @NotNull BitSet ignored2) {
    int start1 = line1.getOffset1();
    int end1 = line1.getOffset2();
    int start2 = line2.getOffset1();
    int end2 = line2.getOffset2();
    Range range = TrimUtil.trimExpandText(line1.getOriginalText(), line2.getOriginalText(),
                                          start1, start2, end1, end2,
                                          ignored1, ignored2);
    if (!range.isEmpty()) return false;

    List<ByWord.InlineChunk> words1 = getNonIgnoredWords(line1, ignored1);
    List<ByWord.InlineChunk> words2 = getNonIgnoredWords(line2, ignored2);
    if (words1.size() != words2.size()) return false;

    for (int i = 0; i < words1.size(); i++) {
      CharSequence word1 = getWordContent(line1, words1.get(i));
      CharSequence word2 = getWordContent(line2, words2.get(i));
      if (!StringUtil.equals(word1, word2)) return false;
    }

    return true;
  }

  @NotNull
  private static List<ByWord.InlineChunk> getNonIgnoredWords(@NotNull Line line, @NotNull BitSet ignored) {
    int offset = line.getOffset1();
    List<ByWord.InlineChunk> innerChunks = ByWord.getInlineChunks(line.getContent());
    return ContainerUtil.filter(innerChunks, it -> it instanceof ByWord.WordChunk &&
                                                   !isIgnoredRange(ignored, offset + it.getOffset1(), offset + it.getOffset2()));
  }

  @NotNull
  private static CharSequence getWordContent(@NotNull Line line, @NotNull ByWord.InlineChunk word) {
    return line.getContent().subSequence(word.getOffset1(), word.getOffset2());
  }

  @NotNull
  private static TextRange trimIgnoredRange(int start, int end, @NotNull BitSet ignored, int offset) {
    IntPair intPair = TrimUtil.trim(offset + start, offset + end, ignored);
    return new TextRange(intPair.val1 - offset, intPair.val2 - offset);
  }

  private static boolean isIgnoredRange(@NotNull BitSet ignored, int start, int end) {
    return ignored.nextClearBit(start) >= end;
  }

  private static class Line {
    @NotNull private final CharSequence myChars;
    private final int myOffset1;
    private final int myOffset2;
    private final boolean myNewline;

    public Line(@NotNull CharSequence chars, int offset1, int offset2, boolean newline) {
      myChars = chars;
      myOffset1 = offset1;
      myOffset2 = offset2;
      myNewline = newline;
    }

    public int getOffset1() {
      return myOffset1;
    }

    public int getOffset2() {
      return myOffset2 + (myNewline ? 1 : 0);
    }

    @NotNull
    public CharSequence getContent() {
      return new CharSequenceSubSequence(myChars, myOffset1, myOffset2);
    }

    @NotNull
    public CharSequence getNotIgnoredContent(@NotNull BitSet ignored) {
      StringBuilder sb = new StringBuilder();
      for (int i = myOffset1; i < myOffset2; i++) {
        if (ignored.get(i)) continue;
        sb.append(myChars.charAt(i));
      }
      return sb.toString();
    }

    @NotNull
    public CharSequence getOriginalText() {
      return myChars;
    }
  }
}
