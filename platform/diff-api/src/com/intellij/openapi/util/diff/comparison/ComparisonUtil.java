package com.intellij.openapi.util.diff.comparison;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.diff.comparison.iterables.DiffIterableUtil.Range;
import com.intellij.openapi.util.diff.fragments.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.diff.comparison.iterables.DiffIterableUtil.convertIntoFragments;

/**
 * Class for the text comparison
 * CharSequences should to have '\n' as line separator
 * <p/>
 * It's good idea not to compare String due to expensive subSequence() implementation. Try to wrap into CharSequenceSubSequence.
 */
public class ComparisonUtil {
  public static final Logger LOG = Logger.getInstance(ComparisonUtil.class);

  // TODO: check ProgressIndicator in ByLine/ByWord/ByChar logic.

  @NotNull
  public static List<LineFragment> compareLines(@NotNull CharSequence text1,
                                                @NotNull CharSequence text2,
                                                @NotNull ComparisonPolicy policy,
                                                @NotNull ProgressIndicator indicator) {
    if (policy == ComparisonPolicy.IGNORE_WHITESPACES) {
      return ByLine.compare(text1, text2, policy, indicator);
    }
    else {
      return ByLine.compareTwoStep(text1, text2, policy, indicator);
    }
  }

  @NotNull
  public static List<FineLineFragment> compareFineLines(@NotNull CharSequence text1,
                                                        @NotNull CharSequence text2,
                                                        @NotNull ComparisonPolicy policy,
                                                        @NotNull ProgressIndicator indicator) {
    List<LineFragment> fragments = compareLines(text1, text2, policy, indicator);
    return compareFineLines(text1, text2, fragments, policy, indicator);
  }

  @NotNull
  public static List<FineLineFragment> compareFineLines(@NotNull CharSequence text1,
                                                        @NotNull CharSequence text2,
                                                        @NotNull List<? extends LineFragment> lineFragments,
                                                        @NotNull ComparisonPolicy policy,
                                                        @NotNull ProgressIndicator indicator) {
    List<FineLineFragment> fineFragments = new ArrayList<FineLineFragment>(lineFragments.size());
    int tooBigChunksCount = 0;

    for (LineFragment fragment : lineFragments) {
      if (tooBigChunksCount >= 3 || // Do not try to build fine blocks after few fails
          fragment.getStartLine1() == fragment.getEndLine1() ||
          fragment.getStartLine2() == fragment.getEndLine2()) {
        // TODO: skip newlines in case of IgnoreWhitespaces ? Trim empty lines in changed block as well ?
        fineFragments.add(new FineLineFragmentImpl(fragment, null));
        continue;
      }

      CharSequence subSequence1 = text1.subSequence(fragment.getStartOffset1(), fragment.getEndOffset1());
      CharSequence subSequence2 = text2.subSequence(fragment.getStartOffset2(), fragment.getEndOffset2());

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

          if (block.fragments.size() == 0) { // drop blocks, all changes in that are ignored
            currentStartLine1 = currentEndLine1;
            currentStartLine2 = currentEndLine2;
            continue;
          }

          // TODO: same for insert/delete blocks
          List<DiffFragment> fragments = block.fragments;
          if (block.fragments.size() == 1) {
            DiffFragment diffFragment = block.fragments.get(0);
            if (diffFragment.getStartOffset1() == 0 &&
                diffFragment.getStartOffset2() == 0 &&
                diffFragment.getEndOffset1() == offsets.end1 - offsets.start1 &&
                diffFragment.getEndOffset2() == offsets.end2 - offsets.start2) {
              fragments = null;
            }
          }

          fineFragments.add(new FineLineFragmentImpl(currentStartLine1, currentEndLine1, currentStartLine2, currentEndLine2,
                                                     offsets.start1 + startOffset1, offsets.end1 + startOffset1,
                                                     offsets.start2 + startOffset2, offsets.end2 + startOffset2,
                                                     fragments));

          currentStartLine1 = currentEndLine1;
          currentStartLine2 = currentEndLine2;
        }
      }
      catch (DiffTooBigException e) {
        fineFragments.add(new FineLineFragmentImpl(fragment, null));
        tooBigChunksCount++;
      }
    }

    return fineFragments;
  }

  @NotNull
  public static List<DiffFragment> compareWords(@NotNull CharSequence text1,
                                                @NotNull CharSequence text2,
                                                @NotNull ComparisonPolicy policy,
                                                @NotNull ProgressIndicator indicator) {
    return ByWord.compare(text1, text2, policy, indicator);
  }

  @NotNull
  public static List<DiffFragment> compareChars(@NotNull CharSequence text1,
                                                @NotNull CharSequence text2,
                                                @NotNull ComparisonPolicy policy,
                                                @NotNull ProgressIndicator indicator) {
    if (policy == ComparisonPolicy.IGNORE_WHITESPACES) {
      return convertIntoFragments(ByChar.compareIgnoreWhitespaces(text1, text2, indicator));
    }
    if (policy == ComparisonPolicy.DEFAULT) {
      return convertIntoFragments(ByChar.compareTwoStep(text1, text2, indicator));
    }
    LOG.warn(policy.toString() + " is not supported by ByChar comparison");
    return convertIntoFragments(ByChar.compareTwoStep(text1, text2, indicator));
  }

  //
  // Squash
  //

  @NotNull
  public static List<LineFragment> squash(@NotNull List<? extends LineFragment> oldFragments) {
    List<LineFragment> newFragments = new ArrayList<LineFragment>(oldFragments.size());

    for (LineFragment fragment : oldFragments) {
      LineFragment lastFragment = ContainerUtil.getLastItem(newFragments);

      if (lastFragment != null && canSquash(lastFragment, fragment)) {
        newFragments.remove(newFragments.size() - 1);
        newFragments.add(squash(lastFragment, fragment));
      }
      else {
        newFragments.add(fragment);
      }
    }

    return newFragments;
  }

  @NotNull
  public static List<FineLineFragment> squashFine(@NotNull List<? extends FineLineFragment> oldFragments) {
    List<FineLineFragment> newFragments = new ArrayList<FineLineFragment>(oldFragments.size());

    for (FineLineFragment fragment : oldFragments) {
      FineLineFragment lastFragment = ContainerUtil.getLastItem(newFragments);

      if (lastFragment != null && canSquash(lastFragment, fragment)) {
        newFragments.remove(newFragments.size() - 1);
        newFragments.add(squashFine(lastFragment, fragment));
      }
      else {
        newFragments.add(fragment);
      }
    }

    return newFragments;
  }

  private static boolean canSquash(@NotNull LineFragment beforeFragment, @NotNull LineFragment afterFragment) {
    if (beforeFragment.getEndLine1() != afterFragment.getStartLine1() ||
        beforeFragment.getEndLine2() != afterFragment.getStartLine2() ||
        beforeFragment.getEndOffset1() != afterFragment.getStartOffset1() ||
        beforeFragment.getEndOffset2() != afterFragment.getStartOffset2()) {
      return false;
    }

    return true;
  }

  @Nullable
  private static LineFragment squash(@NotNull LineFragment beforeFragment, @NotNull LineFragment afterFragment) {
    return new LineFragmentImpl(beforeFragment.getStartLine1(), afterFragment.getEndLine1(),
                                beforeFragment.getStartLine2(), afterFragment.getEndLine2(),
                                beforeFragment.getStartOffset1(), afterFragment.getEndOffset1(),
                                beforeFragment.getStartOffset2(), afterFragment.getEndOffset2());
  }

  @Nullable
  private static FineLineFragment squashFine(@NotNull FineLineFragment beforeFragment, @NotNull FineLineFragment afterFragment) {
    List<DiffFragment> newFragments;
    if (beforeFragment.getFineFragments() == null && afterFragment.getFineFragments() == null) {
      newFragments = null;
    }
    else {
      List<? extends DiffFragment> beforeFragments = extractFragments((FineLineFragment)beforeFragment);
      List<? extends DiffFragment> afterFragments = extractFragments((FineLineFragment)afterFragment);

      newFragments = new ArrayList<DiffFragment>(beforeFragments.size() + afterFragments.size());

      newFragments.addAll(beforeFragments);

      int shift1 = afterFragment.getStartOffset1() - beforeFragment.getStartOffset1();
      int shift2 = afterFragment.getStartOffset2() - beforeFragment.getStartOffset2();

      for (int i = 0; i < afterFragments.size(); i++) {
        DiffFragment fragment = afterFragments.get(i);
        int startOffset1 = fragment.getStartOffset1() + shift1;
        int endOffset1 = fragment.getEndOffset1() + shift1;
        int startOffset2 = fragment.getStartOffset2() + shift2;
        int endOffset2 = fragment.getEndOffset2() + shift2;

        newFragments.add(new DiffFragmentImpl(startOffset1, endOffset1, startOffset2, endOffset2));

        if (i == 0 && newFragments.size() > 1) {
          DiffFragment lastFragment = newFragments.get(newFragments.size() - 2);
          if (lastFragment.getEndOffset1() == startOffset1 && lastFragment.getEndOffset2() == startOffset2) {
            newFragments.remove(newFragments.size() - 1);
            newFragments.remove(newFragments.size() - 1);
            newFragments.add(new DiffFragmentImpl(lastFragment.getStartOffset1(), endOffset1, lastFragment.getStartOffset2(), endOffset2));
          }
        }
      }
    }

    return new FineLineFragmentImpl(beforeFragment.getStartLine1(), afterFragment.getEndLine1(),
                                    beforeFragment.getStartLine2(), afterFragment.getEndLine2(),
                                    beforeFragment.getStartOffset1(), afterFragment.getEndOffset1(),
                                    beforeFragment.getStartOffset2(), afterFragment.getEndOffset2(), newFragments);
  }

  @NotNull
  private static List<? extends DiffFragment> extractFragments(@NotNull FineLineFragment lineFragment) {
    if (lineFragment.getFineFragments() != null) return lineFragment.getFineFragments();

    int length1 = lineFragment.getEndOffset1() - lineFragment.getStartOffset1();
    int length2 = lineFragment.getEndOffset2() - lineFragment.getStartOffset2();
    return Collections.singletonList(new DiffFragmentImpl(0, length1, 0, length2));
  }
}
