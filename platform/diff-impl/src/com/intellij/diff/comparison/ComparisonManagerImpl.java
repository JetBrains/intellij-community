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
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.fragments.*;
import com.intellij.diff.util.IntPair;
import com.intellij.diff.util.MergeRange;
import com.intellij.diff.util.Range;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.text.CharSequenceSubSequence;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ComparisonManagerImpl extends ComparisonManager {
  public static final Logger LOG = Logger.getInstance(ComparisonManagerImpl.class);

  @NotNull
  @Override
  public List<LineFragment> compareLines(@NotNull CharSequence text1,
                                         @NotNull CharSequence text2,
                                         @NotNull ComparisonPolicy policy,
                                         @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    List<Line> lines1 = getLines(text1);
    List<Line> lines2 = getLines(text2);
    FairDiffIterable iterable = ByLine.compare(lines1, lines2, policy, indicator);
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
    List<MergeRange> ranges = ByLine.compare(lines1, lines2, lines3, policy, indicator);
    return convertIntoMergeLineFragments(ranges);
  }

  @NotNull
  @Override
  public List<LineFragment> compareLinesInner(@NotNull CharSequence text1,
                                              @NotNull CharSequence text2,
                                              @NotNull ComparisonPolicy policy,
                                              @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    List<LineFragment> lineFragments = compareLines(text1, text2, policy, indicator);

    List<LineFragment> fineFragments = new ArrayList<>(lineFragments.size());
    int tooBigChunksCount = 0;

    for (LineFragment fragment : lineFragments) {
      CharSequence subSequence1 = text1.subSequence(fragment.getStartOffset1(), fragment.getEndOffset1());
      CharSequence subSequence2 = text2.subSequence(fragment.getStartOffset2(), fragment.getEndOffset2());

      if (fragment.getStartLine1() == fragment.getEndLine1() ||
          fragment.getStartLine2() == fragment.getEndLine2()) { // Insertion / Deletion
        if (isEquals(subSequence1, subSequence2, policy)) {
          fineFragments.add(new LineFragmentImpl(fragment, Collections.<DiffFragment>emptyList()));
        }
        else {
          fineFragments.add(new LineFragmentImpl(fragment, null));
        }
        continue;
      }

      if (tooBigChunksCount >= FilesTooBigForDiffException.MAX_BAD_LINES) { // Do not try to build fine blocks after few fails)
        fineFragments.add(new LineFragmentImpl(fragment, null));
        continue;
      }

      try {
        List<ByWord.LineBlock> lineBlocks = ByWord.compareAndSplit(subSequence1, subSequence2, policy, indicator);
        assert lineBlocks.size() != 0;

        int startOffset1 = fragment.getStartOffset1();
        int startOffset2 = fragment.getStartOffset2();

        int currentStartLine1 = fragment.getStartLine1();
        int currentStartLine2 = fragment.getStartLine2();

        for (int i = 0; i < lineBlocks.size(); i++) {
          ByWord.LineBlock block = lineBlocks.get(i);
          Range offsets = block.offsets;

          // special case for last line to void problem with empty last line
          int currentEndLine1 = i != lineBlocks.size() - 1 ? currentStartLine1 + block.newlines1 : fragment.getEndLine1();
          int currentEndLine2 = i != lineBlocks.size() - 1 ? currentStartLine2 + block.newlines2 : fragment.getEndLine2();

          fineFragments.add(new LineFragmentImpl(currentStartLine1, currentEndLine1, currentStartLine2, currentEndLine2,
                                                 offsets.start1 + startOffset1, offsets.end1 + startOffset1,
                                                 offsets.start2 + startOffset2, offsets.end2 + startOffset2,
                                                 block.fragments));

          currentStartLine1 = currentEndLine1;
          currentStartLine2 = currentEndLine2;
        }
      }
      catch (DiffTooBigException e) {
        fineFragments.add(new LineFragmentImpl(fragment, null));
        tooBigChunksCount++;
      }
    }
    return fineFragments;
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

  @NotNull
  public List<Range> compareLines(@NotNull List<? extends CharSequence> lines1,
                                  @NotNull List<? extends CharSequence> lines2,
                                  @NotNull ComparisonPolicy policy,
                                  @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    FairDiffIterable iterable = ByLine.compare(lines1, lines2, policy, indicator);
    return ContainerUtil.newArrayList(iterable.iterateChanges());
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
      return Collections.singletonList(doSquash(fragments.subList(start, end)));
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
  private static List<? extends DiffFragment> extractInnerFragments(@NotNull LineFragment lineFragment) {
    if (lineFragment.getInnerFragments() != null) return lineFragment.getInnerFragments();

    int length1 = lineFragment.getEndOffset1() - lineFragment.getStartOffset1();
    int length2 = lineFragment.getEndOffset2() - lineFragment.getStartOffset2();
    return Collections.singletonList(new DiffFragmentImpl(0, length1, 0, length2));
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

  private static class Line extends CharSequenceSubSequence {
    private final int myOffset1;
    private final int myOffset2;
    private final boolean myNewline;

    public Line(@NotNull CharSequence chars, int offset1, int offset2, boolean newline) {
      super(chars, offset1, offset2);
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
  }
}
