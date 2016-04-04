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

import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.DiffFragmentImpl;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.fragments.LineFragmentImpl;
import com.intellij.diff.util.Range;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.text.CharSequenceSubSequence;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.diff.comparison.iterables.DiffIterableUtil.convertIntoFragments;

public class ComparisonManagerImpl extends ComparisonManager {
  public static final Logger LOG = Logger.getInstance(ComparisonManagerImpl.class);

  @NotNull
  @Override
  public List<LineFragment> compareLines(@NotNull CharSequence text1,
                                         @NotNull CharSequence text2,
                                         @NotNull ComparisonPolicy policy,
                                         @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    if (policy == ComparisonPolicy.IGNORE_WHITESPACES) {
      return ByLine.compare(text1, text2, policy, indicator);
    }
    else {
      return ByLine.compareTwoStep(text1, text2, policy, indicator);
    }
  }

  @NotNull
  @Override
  public List<LineFragment> compareLinesInner(@NotNull CharSequence text1,
                                              @NotNull CharSequence text2,
                                              @NotNull ComparisonPolicy policy,
                                              @NotNull ProgressIndicator indicator) throws DiffTooBigException {
    List<LineFragment> lineFragments = compareLines(text1, text2, policy, indicator);

    List<LineFragment> fineFragments = new ArrayList<LineFragment>(lineFragments.size());
    int tooBigChunksCount = 0;

    for (LineFragment fragment : lineFragments) {
      CharSequence subSequence1 = text1.subSequence(fragment.getStartOffset1(), fragment.getEndOffset1());
      CharSequence subSequence2 = text2.subSequence(fragment.getStartOffset2(), fragment.getEndOffset2());

      if (fragment.getStartLine1() == fragment.getEndLine1() ||
          fragment.getStartLine2() == fragment.getEndLine2()) { // Do not try to build fine blocks after few fails)
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
      return convertIntoFragments(ByChar.compareIgnoreWhitespaces(text1, text2, indicator));
    }
    if (policy == ComparisonPolicy.DEFAULT) {
      return convertIntoFragments(ByChar.compareTwoStep(text1, text2, indicator));
    }
    LOG.warn(policy.toString() + " is not supported by ByChar comparison");
    return convertIntoFragments(ByChar.compareTwoStep(text1, text2, indicator));
  }

  @Override
  public boolean isEquals(@NotNull CharSequence text1, @NotNull CharSequence text2, @NotNull ComparisonPolicy policy) {
    switch (policy) {
      case DEFAULT:
        return StringUtil.equals(text1, text2);
      case TRIM_WHITESPACES:
        return equalsTrimWhitespaces(text1, text2);
      case IGNORE_WHITESPACES:
        return StringUtil.equalsIgnoreWhitespaces(text1, text2);
      default:
        throw new IllegalArgumentException(policy.name());
    }
  }

  @Contract(pure = true)
  private static boolean equalsTrimWhitespaces(@NotNull CharSequence s1, @NotNull CharSequence s2) {
    int index1 = 0;
    int index2 = 0;

    while (true) {
      boolean lastLine1 = false;
      boolean lastLine2 = false;

      int end1 = StringUtil.indexOf(s1, '\n', index1) + 1;
      int end2 = StringUtil.indexOf(s2, '\n', index2) + 1;
      if (end1 == 0) {
        end1 = s1.length();
        lastLine1 = true;
      }
      if (end2 == 0) {
        end2 = s2.length();
        lastLine2 = true;
      }
      if (lastLine1 ^ lastLine2) return false;

      CharSequence line1 = s1.subSequence(index1, end1);
      CharSequence line2 = s2.subSequence(index2, end2);
      if (!StringUtil.equalsTrimWhitespaces(line1, line2)) return false;

      index1 = end1;
      index2 = end2;
      if (lastLine1) return true;
    }
  }

  //
  // Post process line fragments
  //

  @NotNull
  @Override
  public List<LineFragment> squash(@NotNull List<LineFragment> oldFragments) {
    if (oldFragments.isEmpty()) return oldFragments;

    final List<LineFragment> newFragments = new ArrayList<LineFragment>();
    processAdjoining(oldFragments, new Consumer<List<LineFragment>>() {
      @Override
      public void consume(List<LineFragment> fragments) {
        newFragments.add(doSquash(fragments));
      }
    });
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

    final List<LineFragment> newFragments = new ArrayList<LineFragment>();
    processAdjoining(oldFragments, new Consumer<List<LineFragment>>() {
      @Override
      public void consume(List<LineFragment> fragments) {
        newFragments.addAll(processAdjoining(fragments, text1, text2, policy, squash, trim));
      }
    });
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

    List<DiffFragment> newInnerFragments = new ArrayList<DiffFragment>();
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
}
