// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.comparison;

import com.intellij.diff.comparison.iterables.DiffIterable;
import com.intellij.diff.comparison.iterables.DiffIterableUtil;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.fragments.*;
import com.intellij.diff.tools.util.text.LineOffsets;
import com.intellij.diff.tools.util.text.LineOffsetsUtil;
import com.intellij.diff.util.MergeRange;
import com.intellij.diff.util.Range;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Consumer;
import com.intellij.util.IntPair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.DiffConfig;
import com.intellij.util.text.CharSequenceSubSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import static com.intellij.diff.comparison.iterables.DiffIterableUtil.fair;
import static java.util.Collections.singletonList;

public final class ComparisonManagerImpl extends ComparisonManager {
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
    LineOffsets lineOffsets1 = LineOffsetsUtil.create(text1);
    LineOffsets lineOffsets2 = LineOffsetsUtil.create(text2);

    return compareLines(text1, text2, lineOffsets1, lineOffsets2, policy, indicator);
  }

  @NotNull
  public List<LineFragment> compareLines(@NotNull CharSequence text1,
                                         @NotNull CharSequence text2,
                                         @NotNull LineOffsets lineOffsets1,
                                         @NotNull LineOffsets lineOffsets2,
                                         @NotNull ComparisonPolicy policy,
                                         @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    Range range = new Range(0, lineOffsets1.getLineCount(),
                            0, lineOffsets2.getLineCount());
    return compareLines(range, text1, text2, lineOffsets1, lineOffsets2, policy, indicator);
  }

  @NotNull
  public List<LineFragment> compareLines(@NotNull Range range,
                                         @NotNull CharSequence text1,
                                         @NotNull CharSequence text2,
                                         @NotNull LineOffsets lineOffsets1,
                                         @NotNull LineOffsets lineOffsets2,
                                         @NotNull ComparisonPolicy policy,
                                         @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    List<CharSequence> lineTexts1 = getLineContents(range.start1, range.end1, text1, lineOffsets1);
    List<CharSequence> lineTexts2 = getLineContents(range.start2, range.end2, text2, lineOffsets2);

    FairDiffIterable iterable = ByLine.compare(lineTexts1, lineTexts2, policy, new IndicatorCancellationChecker(indicator));
    return convertIntoLineFragments(range, lineOffsets1, lineOffsets2, iterable);
  }

  @NotNull
  @Override
  public List<MergeLineFragment> compareLines(@NotNull CharSequence text1,
                                              @NotNull CharSequence text2,
                                              @NotNull CharSequence text3,
                                              @NotNull ComparisonPolicy policy,
                                              @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    List<CharSequence> lineTexts1 = getLineContents(text1);
    List<CharSequence> lineTexts2 = getLineContents(text2);
    List<CharSequence> lineTexts3 = getLineContents(text3);

    List<MergeRange> ranges = ByLine.compare(lineTexts1, lineTexts2, lineTexts3, policy, new IndicatorCancellationChecker(indicator));
    return ByLine.convertIntoMergeLineFragments(ranges);
  }

  @NotNull
  @Override
  public List<MergeLineFragment> mergeLines(@NotNull CharSequence text1,
                                            @NotNull CharSequence text2,
                                            @NotNull CharSequence text3,
                                            @NotNull ComparisonPolicy policy,
                                            @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    List<CharSequence> lineTexts1 = getLineContents(text1);
    List<CharSequence> lineTexts2 = getLineContents(text2);
    List<CharSequence> lineTexts3 = getLineContents(text3);

    List<MergeRange> ranges = ByLine.merge(lineTexts1, lineTexts2, lineTexts3, policy, new IndicatorCancellationChecker(indicator));
    return ByLine.convertIntoMergeLineFragments(ranges);
  }

  @NotNull
  @Override
  public List<LineFragment> compareLinesInner(@NotNull CharSequence text1,
                                              @NotNull CharSequence text2,
                                              @NotNull ComparisonPolicy policy,
                                              @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    List<LineFragment> lineFragments = compareLines(text1, text2, policy, indicator);
    return createInnerFragments(lineFragments, text1, text2, policy, InnerFragmentsPolicy.WORDS, indicator);
  }

  @NotNull
  public List<LineFragment> compareLinesInner(@NotNull CharSequence text1,
                                              @NotNull CharSequence text2,
                                              @NotNull LineOffsets lineOffsets1,
                                              @NotNull LineOffsets lineOffsets2,
                                              @NotNull ComparisonPolicy policy,
                                              @NotNull InnerFragmentsPolicy fragmentsPolicy,
                                              @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    List<LineFragment> lineFragments = compareLines(text1, text2, lineOffsets1, lineOffsets2, policy, indicator);
    if (fragmentsPolicy != InnerFragmentsPolicy.NONE) {
      return createInnerFragments(lineFragments, text1, text2, policy, fragmentsPolicy, indicator);
    }
    else {
      return lineFragments;
    }
  }

  @NotNull
  public List<LineFragment> compareLinesInner(@NotNull Range range,
                                              @NotNull CharSequence text1,
                                              @NotNull CharSequence text2,
                                              @NotNull LineOffsets lineOffsets1,
                                              @NotNull LineOffsets lineOffsets2,
                                              @NotNull ComparisonPolicy policy,
                                              boolean innerFragments,
                                              @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    InnerFragmentsPolicy fragmentsPolicy = innerFragments ? InnerFragmentsPolicy.WORDS : InnerFragmentsPolicy.NONE;
    return compareLinesInner(range, text1, text2, lineOffsets1, lineOffsets2, policy, fragmentsPolicy, indicator);
  }

  @NotNull
  public List<LineFragment> compareLinesInner(@NotNull Range range,
                                              @NotNull CharSequence text1,
                                              @NotNull CharSequence text2,
                                              @NotNull LineOffsets lineOffsets1,
                                              @NotNull LineOffsets lineOffsets2,
                                              @NotNull ComparisonPolicy policy,
                                              @NotNull InnerFragmentsPolicy fragmentsPolicy,
                                              @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    List<LineFragment> lineFragments = compareLines(range, text1, text2, lineOffsets1, lineOffsets2, policy, indicator);
    if (fragmentsPolicy != InnerFragmentsPolicy.NONE) {
      return createInnerFragments(lineFragments, text1, text2, policy, fragmentsPolicy, indicator);
    }
    else {
      return lineFragments;
    }
  }

  private static List<LineFragment> createInnerFragments(@NotNull List<? extends LineFragment> lineFragments,
                                                         @NotNull CharSequence text1,
                                                         @NotNull CharSequence text2,
                                                         @NotNull ComparisonPolicy policy,
                                                         @NotNull InnerFragmentsPolicy fragmentsPolicy,
                                                         @NotNull ProgressIndicator indicator) {
    List<LineFragment> result = new ArrayList<>(lineFragments.size());

    int tooBigChunksCount = 0;
    for (LineFragment fragment : lineFragments) {
      assert fragment.getInnerFragments() == null;

      try {
        // Do not try to build fine blocks after few fails
        boolean tryComputeDifferences = tooBigChunksCount < DiffConfig.MAX_BAD_LINES;
        result.addAll(createInnerFragments(fragment, text1, text2, policy, fragmentsPolicy, indicator, tryComputeDifferences));
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
                                                         @NotNull InnerFragmentsPolicy fragmentsPolicy,
                                                         @NotNull ProgressIndicator indicator,
                                                         boolean tryComputeDifferences) throws DiffTooBigException {
    if (fragmentsPolicy == InnerFragmentsPolicy.NONE) {
      return singletonList(fragment);
    }

    CharSequence subSequence1 = text1.subSequence(fragment.getStartOffset1(), fragment.getEndOffset1());
    CharSequence subSequence2 = text2.subSequence(fragment.getStartOffset2(), fragment.getEndOffset2());

    if (fragment.getStartLine1() == fragment.getEndLine1() ||
        fragment.getStartLine2() == fragment.getEndLine2()) { // Insertion / Deletion
      if (ComparisonUtil.isEqualTexts(subSequence1, subSequence2, policy)) {
        return singletonList(new LineFragmentImpl(fragment, Collections.emptyList()));
      }
      else {
        return singletonList(fragment);
      }
    }

    if (!tryComputeDifferences) return singletonList(fragment);

    if (fragmentsPolicy == InnerFragmentsPolicy.WORDS) {
      return createInnerWordFragments(fragment, subSequence1, subSequence2, policy, indicator);
    }
    else if (fragmentsPolicy == InnerFragmentsPolicy.CHARS) {
      return createInnerCharFragments(fragment, subSequence1, subSequence2, policy, indicator);
    }
    else {
      throw new IllegalArgumentException(fragmentsPolicy.name());
    }
  }

  @NotNull
  private static List<LineFragment> createInnerWordFragments(@NotNull LineFragment fragment,
                                                             @NotNull CharSequence subSequence1,
                                                             @NotNull CharSequence subSequence2,
                                                             @NotNull ComparisonPolicy policy,
                                                             @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    List<ByWord.LineBlock> lineBlocks = ByWord.compareAndSplit(subSequence1, subSequence2, policy,
                                                               new IndicatorCancellationChecker(indicator));
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
  private static List<LineFragment> createInnerCharFragments(@NotNull LineFragment fragment,
                                                             @NotNull CharSequence subSequence1,
                                                             @NotNull CharSequence subSequence2,
                                                             @NotNull ComparisonPolicy policy,
                                                             @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    List<DiffFragment> innerChanges = doCompareChars(subSequence1, subSequence2, policy, indicator);
    return singletonList(new LineFragmentImpl(fragment, innerChanges));
  }

  @NotNull
  private static List<DiffFragment> doCompareChars(@NotNull CharSequence text1,
                                                   @NotNull CharSequence text2,
                                                   @NotNull ComparisonPolicy policy,
                                                   @NotNull ProgressIndicator indicator) {
    DiffIterable iterable;
    if (policy == ComparisonPolicy.DEFAULT) {
      iterable = ByChar.compareTwoStep(text1, text2, new IndicatorCancellationChecker(indicator));
    }
    else if (policy == ComparisonPolicy.TRIM_WHITESPACES) {
      iterable = ByChar.compareTrimWhitespaces(text1, text2, new IndicatorCancellationChecker(indicator));
    }
    else {
      iterable = ByChar.compareIgnoreWhitespaces(text1, text2, new IndicatorCancellationChecker(indicator));
    }

    return ByWord.convertIntoDiffFragments(iterable);
  }

  @NotNull
  @Override
  public List<DiffFragment> compareWords(@NotNull CharSequence text1,
                                         @NotNull CharSequence text2,
                                         @NotNull ComparisonPolicy policy,
                                         @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    return ByWord.compare(text1, text2, policy, new IndicatorCancellationChecker(indicator));
  }

  @NotNull
  @Override
  public List<DiffFragment> compareChars(@NotNull CharSequence text1,
                                         @NotNull CharSequence text2,
                                         @NotNull ComparisonPolicy policy,
                                         @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    return doCompareChars(text1, text2, policy, indicator);
  }

  @Override
  public boolean isEquals(@NotNull CharSequence text1, @NotNull CharSequence text2, @NotNull ComparisonPolicy policy) {
    return ComparisonUtil.isEqualTexts(text1, text2, policy);
  }

  //
  // Fragments
  //

  @NotNull
  public static List<LineFragment> convertIntoLineFragments(@NotNull LineOffsets lineOffsets1,
                                                            @NotNull LineOffsets lineOffsets2,
                                                            @NotNull DiffIterable changes) {
    Range range = new Range(0, lineOffsets1.getLineCount(),
                            0, lineOffsets2.getLineCount());
    return convertIntoLineFragments(range, lineOffsets1, lineOffsets2, changes);
  }

  @NotNull
  public static List<LineFragment> convertIntoLineFragments(@NotNull Range range,
                                                            @NotNull LineOffsets lineOffsets1,
                                                            @NotNull LineOffsets lineOffsets2,
                                                            @NotNull DiffIterable changes) {
    List<LineFragment> fragments = new ArrayList<>();
    for (Range ch : changes.iterateChanges()) {
      int startLine1 = ch.start1 + range.start1;
      int startLine2 = ch.start2 + range.start2;
      int endLine1 = ch.end1 + range.start1;
      int endLine2 = ch.end2 + range.start2;

      IntPair offsets1 = getOffsets(lineOffsets1, startLine1, endLine1);
      IntPair offsets2 = getOffsets(lineOffsets2, startLine2, endLine2);

      fragments.add(new LineFragmentImpl(startLine1, endLine1, startLine2, endLine2,
                                         offsets1.first, offsets1.second, offsets2.first, offsets2.second));
    }
    return fragments;
  }

  @NotNull
  private static IntPair getOffsets(@NotNull LineOffsets lineOffsets, int startIndex, int endIndex) {
    if (startIndex == endIndex) {
      int offset;
      if (startIndex < lineOffsets.getLineCount()) {
        offset = lineOffsets.getLineStart(startIndex);
      }
      else {
        offset = lineOffsets.getLineEnd(lineOffsets.getLineCount() - 1, true);
      }
      return new IntPair(offset, offset);
    }
    else {
      int offset1 = lineOffsets.getLineStart(startIndex);
      int offset2 = lineOffsets.getLineEnd(endIndex - 1, true);
      return new IntPair(offset1, offset2);
    }
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

  private static void processAdjoining(@NotNull List<? extends LineFragment> oldFragments,
                                       @NotNull Consumer<? super List<? extends LineFragment>> consumer) {
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
  private static List<? extends LineFragment> processAdjoining(@NotNull List<? extends LineFragment> fragments,
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
            !ComparisonUtil.isEquals(sequence1, sequence2, ComparisonPolicy.IGNORE_WHITESPACES)) {
          break;
        }
        start++;
      }
      while (start < end) {
        LineFragment fragment = fragments.get(end - 1);
        CharSequenceSubSequence sequence1 = new CharSequenceSubSequence(text1, fragment.getStartOffset1(), fragment.getEndOffset1());
        CharSequenceSubSequence sequence2 = new CharSequenceSubSequence(text2, fragment.getStartOffset2(), fragment.getEndOffset2());

        if ((fragment.getInnerFragments() == null || !fragment.getInnerFragments().isEmpty()) &&
            !ComparisonUtil.isEquals(sequence1, sequence2, ComparisonPolicy.IGNORE_WHITESPACES)) {
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
  private static LineFragment doSquash(@NotNull List<? extends LineFragment> oldFragments) {
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
  private static List<CharSequence> getLineContents(@NotNull CharSequence text) {
    LineOffsets lineOffsets = LineOffsetsUtil.create(text);
    return getLineContents(0, lineOffsets.getLineCount(), text, lineOffsets);
  }

  @NotNull
  private static List<CharSequence> getLineContents(int start, int end, @NotNull CharSequence text, @NotNull LineOffsets lineOffsets) {
    List<CharSequence> lines = new ArrayList<>(end - start);
    for (int line = start; line < end; line++) {
      lines.add(new CharSequenceSubSequence(text, lineOffsets.getLineStart(line), lineOffsets.getLineEnd(line)));
    }
    return lines;
  }

  @NotNull
  private static List<CharSequence> getNotIgnoredLineContents(int start, int end,
                                                              @NotNull CharSequence text,
                                                              @NotNull LineOffsets lineOffsets,
                                                              @NotNull BitSet ignored) {
    StringBuilder sb = new StringBuilder();
    List<CharSequence> lines = new ArrayList<>(end - start);
    for (int line = start; line < end; line++) {
      for (int offset = lineOffsets.getLineStart(line); offset < lineOffsets.getLineEnd(line); offset++) {
        if (ignored.get(offset)) continue;
        sb.append(text.charAt(offset));
      }
      lines.add(sb.toString());
      sb.setLength(0);
    }
    return lines;
  }


  @NotNull
  public List<LineFragment> compareLinesWithIgnoredRanges(@NotNull CharSequence text1,
                                                          @NotNull CharSequence text2,
                                                          @NotNull LineOffsets lineOffsets1,
                                                          @NotNull LineOffsets lineOffsets2,
                                                          @NotNull BitSet ignored1,
                                                          @NotNull BitSet ignored2,
                                                          @NotNull InnerFragmentsPolicy fragmentsPolicy,
                                                          @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    Range range = new Range(0, lineOffsets1.getLineCount(),
                            0, lineOffsets2.getLineCount());
    return compareLinesWithIgnoredRanges(range, text1, text2, lineOffsets1, lineOffsets2, ignored1, ignored2,
                                         fragmentsPolicy, indicator);
  }

  /**
   * Compare two texts by-line and then compare changed fragments by-word
   */
  @NotNull
  public List<LineFragment> compareLinesWithIgnoredRanges(@NotNull Range range,
                                                          @NotNull CharSequence text1,
                                                          @NotNull CharSequence text2,
                                                          @NotNull LineOffsets lineOffsets1,
                                                          @NotNull LineOffsets lineOffsets2,
                                                          @NotNull BitSet ignored1,
                                                          @NotNull BitSet ignored2,
                                                          @NotNull InnerFragmentsPolicy fragmentsPolicy,
                                                          @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    List<CharSequence> lineTexts1 = getNotIgnoredLineContents(range.start1, range.end1, text1, lineOffsets1, ignored1);
    List<CharSequence> lineTexts2 = getNotIgnoredLineContents(range.start2, range.end2, text2, lineOffsets2, ignored2);

    FairDiffIterable iterable = ByLine.compare(lineTexts1, lineTexts2, ComparisonPolicy.DEFAULT,
                                               new IndicatorCancellationChecker(indicator));

    FairDiffIterable correctedIterable = correctIgnoredRangesSecondStep(range, iterable, text1, text2, lineOffsets1, lineOffsets2,
                                                                        ignored1, ignored2);

    DiffIterable trimmedIterable = trimIgnoredLines(range, correctedIterable, text1, text2, lineOffsets1, lineOffsets2,
                                                    ignored1, ignored2);

    List<LineFragment> lineFragments = convertIntoLineFragments(range, lineOffsets1, lineOffsets2, trimmedIterable);

    if (fragmentsPolicy != InnerFragmentsPolicy.NONE) {
      lineFragments = createInnerFragments(lineFragments, text1, text2, ComparisonPolicy.DEFAULT, fragmentsPolicy, indicator);
    }

    return ContainerUtil.mapNotNull(lineFragments, fragment -> trimIgnoredInnerFragments(fragment, ignored1, ignored2));
  }

  @NotNull
  public static BitSet collectIgnoredRanges(@NotNull List<? extends TextRange> ignoredRanges) {
    BitSet set = new BitSet();
    for (TextRange range : ignoredRanges) {
      set.set(range.getStartOffset(), range.getEndOffset());
    }
    return set;
  }

  @NotNull
  private static FairDiffIterable correctIgnoredRangesSecondStep(@NotNull Range range,
                                                                 @NotNull FairDiffIterable iterable,
                                                                 @NotNull CharSequence text1,
                                                                 @NotNull CharSequence text2,
                                                                 @NotNull LineOffsets lineOffsets1,
                                                                 @NotNull LineOffsets lineOffsets2,
                                                                 @NotNull BitSet ignored1,
                                                                 @NotNull BitSet ignored2) {
    DiffIterableUtil.ChangeBuilder builder = new DiffIterableUtil.ChangeBuilder(iterable.getLength1(), iterable.getLength2());
    for (Range ch : iterable.iterateUnchanged()) {
      int count = ch.end1 - ch.start1;
      for (int i = 0; i < count; i++) {
        int index1 = ch.start1 + i;
        int index2 = ch.start2 + i;
        if (areIgnoredEqualLines(range.start1 + index1, range.start2 + index2, text1, text2, lineOffsets1, lineOffsets2, ignored1, ignored2)) {
          builder.markEqual(index1, index2);
        }
      }
    }
    return fair(builder.finish());
  }

  @NotNull
  private static DiffIterable trimIgnoredLines(@NotNull Range range,
                                               @NotNull FairDiffIterable iterable,
                                               @NotNull CharSequence text1,
                                               @NotNull CharSequence text2,
                                               @NotNull LineOffsets lineOffsets1,
                                               @NotNull LineOffsets lineOffsets2,
                                               @NotNull BitSet ignored1,
                                               @NotNull BitSet ignored2) {
    List<Range> changedRanges = new ArrayList<>();

    for (Range ch : iterable.iterateChanges()) {
      Range trimmedRange = TrimUtil.trimExpandRange(ch.start1, ch.start2,
                                                    ch.end1, ch.end2,
                                                    (index1, index2) -> areIgnoredEqualLines(range.start1 + index1, range.start2 + index2, text1, text2,
                                                                                             lineOffsets1, lineOffsets2,
                                                                                             ignored1, ignored2),
                                                    index -> isIgnoredLine(range.start1 + index, lineOffsets1, ignored1),
                                                    index -> isIgnoredLine(range.start2 + index, lineOffsets2, ignored2));

      if (!trimmedRange.isEmpty()) changedRanges.add(trimmedRange);
    }

    return DiffIterableUtil.create(changedRanges, iterable.getLength1(), iterable.getLength2());
  }

  @Nullable
  private static LineFragment trimIgnoredInnerFragments(@NotNull LineFragment fragment,
                                                        @NotNull BitSet ignored1,
                                                        @NotNull BitSet ignored2) {
    if (fragment.getInnerFragments() == null) return fragment;

    int startOffset1 = fragment.getStartOffset1();
    int startOffset2 = fragment.getStartOffset2();

    List<DiffFragment> newInner = ContainerUtil.mapNotNull(fragment.getInnerFragments(), it -> {
      TextRange range1 = trimIgnoredRange(it.getStartOffset1(), it.getEndOffset1(), ignored1, startOffset1);
      TextRange range2 = trimIgnoredRange(it.getStartOffset2(), it.getEndOffset2(), ignored2, startOffset2);

      if (range1.isEmpty() && range2.isEmpty()) return null;
      return new DiffFragmentImpl(range1.getStartOffset(), range1.getEndOffset(),
                                  range2.getStartOffset(), range2.getEndOffset());
    });

    if (newInner.isEmpty()) return null;
    return new LineFragmentImpl(fragment, newInner);
  }

  private static boolean isIgnoredLine(int index, @NotNull LineOffsets lineOffsets, @NotNull BitSet ignored) {
    return isIgnoredRange(ignored, lineOffsets.getLineStart(index), lineOffsets.getLineEnd(index, true));
  }

  private static boolean areIgnoredEqualLines(int index1, int index2,
                                              @NotNull CharSequence text1, @NotNull CharSequence text2,
                                              @NotNull LineOffsets lineOffsets1, @NotNull LineOffsets lineOffsets2,
                                              @NotNull BitSet ignored1, @NotNull BitSet ignored2) {
    int start1 = lineOffsets1.getLineStart(index1);
    int end1 = lineOffsets1.getLineEnd(index1, true);
    int start2 = lineOffsets2.getLineStart(index2);
    int end2 = lineOffsets2.getLineEnd(index2, true);
    Range range = TrimUtil.trimExpandText(text1, text2,
                                          start1, start2, end1, end2,
                                          ignored1, ignored2);
    if (!range.isEmpty()) return false;

    List<ByWord.InlineChunk> words1 = getNonIgnoredWords(index1, text1, lineOffsets1, ignored1);
    List<ByWord.InlineChunk> words2 = getNonIgnoredWords(index2, text2, lineOffsets2, ignored2);
    if (words1.size() != words2.size()) return false;

    for (int i = 0; i < words1.size(); i++) {
      CharSequence word1 = getWordContent(index1, text1, lineOffsets1, words1.get(i));
      CharSequence word2 = getWordContent(index2, text2, lineOffsets2, words2.get(i));
      if (!ComparisonUtil.isEquals(word1, word2, ComparisonPolicy.DEFAULT)) return false;
    }

    return true;
  }

  @NotNull
  private static List<ByWord.InlineChunk> getNonIgnoredWords(int index,
                                                             @NotNull CharSequence text,
                                                             @NotNull LineOffsets lineOffsets,
                                                             @NotNull BitSet ignored) {
    int offset = lineOffsets.getLineStart(index);
    List<ByWord.InlineChunk> innerChunks = ByWord.getInlineChunks(getLineContent(index, text, lineOffsets));
    return ContainerUtil.filter(innerChunks, it -> ByWord.isWordChunk(it) &&
                                                   !isIgnoredRange(ignored, offset + it.getOffset1(), offset + it.getOffset2()));
  }

  @NotNull
  private static CharSequence getWordContent(int index,
                                             @NotNull CharSequence text,
                                             @NotNull LineOffsets lineOffsets,
                                             @NotNull ByWord.InlineChunk word) {
    return getLineContent(index, text, lineOffsets).subSequence(word.getOffset1(), word.getOffset2());
  }

  @NotNull
  private static TextRange trimIgnoredRange(int start, int end, @NotNull BitSet ignored, int offset) {
    IntPair intPair = TrimUtil.trim(offset + start, offset + end, ignored);
    return new TextRange(intPair.first - offset, intPair.second - offset);
  }

  private static boolean isIgnoredRange(@NotNull BitSet ignored, int start, int end) {
    return ignored.nextClearBit(start) >= end;
  }

  @NotNull
  private static CharSequence getLineContent(int index, @NotNull CharSequence text, @NotNull LineOffsets lineOffsets) {
    return text.subSequence(lineOffsets.getLineStart(index), lineOffsets.getLineEnd(index, true));
  }
}
