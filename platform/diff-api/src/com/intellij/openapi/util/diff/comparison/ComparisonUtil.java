package com.intellij.openapi.util.diff.comparison;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.diff.comparison.iterables.DiffIterableUtil.Range;
import com.intellij.openapi.util.diff.fragments.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharSequenceSubSequence;
import org.jetbrains.annotations.NotNull;

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

          fineFragments.add(new FineLineFragmentImpl(currentStartLine1, currentEndLine1, currentStartLine2, currentEndLine2,
                                                     offsets.start1 + startOffset1, offsets.end1 + startOffset1,
                                                     offsets.start2 + startOffset2, offsets.end2 + startOffset2,
                                                     block.fragments));

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
  // Post process line fragments
  //

  @NotNull
  public static List<? extends LineFragment> squash(@NotNull final List<? extends LineFragment> oldFragments) {
    if (oldFragments.isEmpty()) return oldFragments;

    final List<LineFragment> newFragments = new ArrayList<LineFragment>();
    processAdjoining(oldFragments, new Consumer<List<? extends LineFragment>>() {
      @Override
      public void consume(List<? extends LineFragment> fragments) {
        newFragments.add(doSquash(fragments));
      }
    });
    return newFragments;
  }

  @NotNull
  public static List<? extends FineLineFragment> squashFine(@NotNull List<? extends FineLineFragment> oldFragments) {
    if (oldFragments.isEmpty()) return oldFragments;

    final List<FineLineFragment> newFragments = new ArrayList<FineLineFragment>();
    processAdjoining(oldFragments, new Consumer<List<? extends FineLineFragment>>() {
      @Override
      public void consume(List<? extends FineLineFragment> fragments) {
        newFragments.add(doSquashFine(fragments));
      }
    });
    return newFragments;
  }

  @NotNull
  public static List<? extends LineFragment> processBlocks(@NotNull List<? extends LineFragment> oldFragments,
                                                           @NotNull final CharSequence text1, @NotNull final CharSequence text2,
                                                           @NotNull final ComparisonPolicy policy,
                                                           final boolean squash, final boolean trim) {
    if (!squash && !trim) return oldFragments;
    if (oldFragments.isEmpty()) return oldFragments;

    final List<LineFragment> newFragments = new ArrayList<LineFragment>();
    processAdjoining(oldFragments, new Consumer<List<? extends LineFragment>>() {
      @Override
      public void consume(List<? extends LineFragment> fragments) {
        newFragments.addAll(processAdjoining(fragments, text1, text2, policy, squash, trim));
      }
    });
    return newFragments;
  }

  @NotNull
  public static List<? extends FineLineFragment> processBlocksFine(@NotNull List<? extends FineLineFragment> oldFragments,
                                                                   @NotNull final CharSequence text1, @NotNull final CharSequence text2,
                                                                   @NotNull final ComparisonPolicy policy,
                                                                   final boolean squash, final boolean trim) {
    if (!squash && !trim) return oldFragments;
    if (oldFragments.isEmpty()) return oldFragments;

    final List<FineLineFragment> newFragments = new ArrayList<FineLineFragment>();
    processAdjoining(oldFragments, new Consumer<List<? extends FineLineFragment>>() {
      @Override
      public void consume(List<? extends FineLineFragment> fragments) {
        newFragments.addAll(processAdjoiningFine(fragments, text1, text2, policy, squash, trim));
      }
    });
    return newFragments;
  }

  private static <T extends LineFragment> void processAdjoining(@NotNull List<? extends T> oldFragments,
                                                                @NotNull Consumer<List<? extends T>> consumer) {
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

        if (!StringUtil.equalsIgnoreWhitespaces(sequence1, sequence2)) break;
        start++;
      }
      while (start < end) {
        LineFragment fragment = fragments.get(end - 1);
        CharSequenceSubSequence sequence1 = new CharSequenceSubSequence(text1, fragment.getStartOffset1(), fragment.getEndOffset1());
        CharSequenceSubSequence sequence2 = new CharSequenceSubSequence(text2, fragment.getStartOffset2(), fragment.getEndOffset2());

        if (!StringUtil.equalsIgnoreWhitespaces(sequence1, sequence2)) break;
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
  private static List<? extends FineLineFragment> processAdjoiningFine(@NotNull List<? extends FineLineFragment> fragments,
                                                                       @NotNull CharSequence text1, @NotNull CharSequence text2,
                                                                       @NotNull ComparisonPolicy policy, boolean squash, boolean trim) {
    int start = 0;
    int end = fragments.size();

    if (trim && policy == ComparisonPolicy.IGNORE_WHITESPACES) {
      while (start < end) {
        FineLineFragment fragment = fragments.get(start);
        CharSequenceSubSequence sequence1 = new CharSequenceSubSequence(text1, fragment.getStartOffset1(), fragment.getEndOffset1());
        CharSequenceSubSequence sequence2 = new CharSequenceSubSequence(text2, fragment.getStartOffset2(), fragment.getEndOffset2());

        if ((fragment.getFineFragments() == null || !fragment.getFineFragments().isEmpty()) &&
            !StringUtil.equalsIgnoreWhitespaces(sequence1, sequence2)) {
          break;
        }
        start++;
      }
      while (start < end) {
        FineLineFragment fragment = fragments.get(end - 1);
        CharSequenceSubSequence sequence1 = new CharSequenceSubSequence(text1, fragment.getStartOffset1(), fragment.getEndOffset1());
        CharSequenceSubSequence sequence2 = new CharSequenceSubSequence(text2, fragment.getStartOffset2(), fragment.getEndOffset2());

        if ((fragment.getFineFragments() == null || !fragment.getFineFragments().isEmpty()) &&
            !StringUtil.equalsIgnoreWhitespaces(sequence1, sequence2)) {
          break;
        }
        end--;
      }
    }

    if (start == end) return Collections.emptyList();
    if (squash) {
      return Collections.singletonList(doSquashFine(fragments.subList(start, end)));
    }
    return fragments.subList(start, end);
  }

  @NotNull
  private static LineFragment doSquash(@NotNull List<? extends LineFragment> oldFragments) {
    assert !oldFragments.isEmpty();
    if (oldFragments.size() == 1) return oldFragments.get(0);

    LineFragment firstFragment = oldFragments.get(0);
    LineFragment lastFragment = oldFragments.get(oldFragments.size() - 1);

    return new LineFragmentImpl(firstFragment.getStartLine1(), lastFragment.getEndLine1(),
                                firstFragment.getStartLine2(), lastFragment.getEndLine2(),
                                firstFragment.getStartOffset1(), lastFragment.getEndOffset1(),
                                firstFragment.getStartOffset2(), lastFragment.getEndOffset2());
  }

  @NotNull
  private static FineLineFragment doSquashFine(@NotNull List<? extends FineLineFragment> oldFragments) {
    assert !oldFragments.isEmpty();
    if (oldFragments.size() == 1) return oldFragments.get(0);

    LineFragment firstFragment = oldFragments.get(0);
    LineFragment lastFragment = oldFragments.get(oldFragments.size() - 1);

    List<DiffFragment> newInnerFragments = new ArrayList<DiffFragment>();
    for (FineLineFragment fragment : oldFragments) {
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

    return new FineLineFragmentImpl(firstFragment.getStartLine1(), lastFragment.getEndLine1(),
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
  private static List<? extends DiffFragment> extractInnerFragments(@NotNull FineLineFragment lineFragment) {
    if (lineFragment.getFineFragments() != null) return lineFragment.getFineFragments();

    int length1 = lineFragment.getEndOffset1() - lineFragment.getStartOffset1();
    int length2 = lineFragment.getEndOffset2() - lineFragment.getStartOffset2();
    return Collections.singletonList(new DiffFragmentImpl(0, length1, 0, length2));
  }
}
