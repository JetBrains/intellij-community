// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.comparison;

import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.fragments.MergeLineFragmentImpl;
import com.intellij.diff.util.MergeRange;
import com.intellij.diff.util.Range;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.diff.comparison.ComparisonPolicy.DEFAULT;
import static com.intellij.diff.comparison.ComparisonPolicy.IGNORE_WHITESPACES;
import static com.intellij.diff.comparison.iterables.DiffIterableUtil.*;
import static com.intellij.openapi.util.text.StringUtil.isWhiteSpace;

public final class ByLine {
  @NotNull
  public static FairDiffIterable compare(@NotNull List<? extends CharSequence> lines1,
                                         @NotNull List<? extends CharSequence> lines2,
                                         @NotNull ComparisonPolicy policy,
                                         @NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();
    return doCompare(getLines(lines1, policy), getLines(lines2, policy), policy, indicator);
  }

  @NotNull
  public static List<MergeRange> compare(@NotNull List<? extends CharSequence> lines1,
                                         @NotNull List<? extends CharSequence> lines2,
                                         @NotNull List<? extends CharSequence> lines3,
                                         @NotNull ComparisonPolicy policy,
                                         @NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();
    return doCompare(getLines(lines1, policy), getLines(lines2, policy), getLines(lines3, policy), policy, indicator, false);
  }

  @NotNull
  public static List<MergeRange> merge(@NotNull List<? extends CharSequence> lines1,
                                       @NotNull List<? extends CharSequence> lines2,
                                       @NotNull List<? extends CharSequence> lines3,
                                       @NotNull ComparisonPolicy policy,
                                       @NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();
    return doCompare(getLines(lines1, policy), getLines(lines2, policy), getLines(lines3, policy), policy, indicator, true);
  }

  //
  // Impl
  //

  @NotNull
  static FairDiffIterable doCompare(@NotNull List<? extends Line> lines1,
                                    @NotNull List<? extends Line> lines2,
                                    @NotNull ComparisonPolicy policy,
                                    @NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();

    if (policy == IGNORE_WHITESPACES) {
      FairDiffIterable changes = compareSmart(lines1, lines2, indicator);
      changes = optimizeLineChunks(lines1, lines2, changes, indicator);
      return expandRanges(lines1, lines2, changes);
    }
    else {
      List<Line> iwLines1 = convertMode(lines1, IGNORE_WHITESPACES);
      List<Line> iwLines2 = convertMode(lines2, IGNORE_WHITESPACES);

      FairDiffIterable iwChanges = compareSmart(iwLines1, iwLines2, indicator);
      iwChanges = optimizeLineChunks(lines1, lines2, iwChanges, indicator);
      return correctChangesSecondStep(lines1, lines2, iwChanges);
    }
  }

  /**
   * @param keepIgnoredChanges if true, blocks of "ignored" changes will not be actually ignored (but will not be included into "conflict" blocks)
   */
  @NotNull
  static List<MergeRange> doCompare(@NotNull List<? extends Line> lines1,
                                    @NotNull List<? extends Line> lines2,
                                    @NotNull List<? extends Line> lines3,
                                    @NotNull ComparisonPolicy policy,
                                    @NotNull ProgressIndicator indicator,
                                    boolean keepIgnoredChanges) {
    indicator.checkCanceled();

    List<Line> iwLines1 = convertMode(lines1, IGNORE_WHITESPACES);
    List<Line> iwLines2 = convertMode(lines2, IGNORE_WHITESPACES);
    List<Line> iwLines3 = convertMode(lines3, IGNORE_WHITESPACES);

    FairDiffIterable iwChanges1 = compareSmart(iwLines2, iwLines1, indicator);
    iwChanges1 = optimizeLineChunks(lines2, lines1, iwChanges1, indicator);
    FairDiffIterable iterable1 = correctChangesSecondStep(lines2, lines1, iwChanges1);

    FairDiffIterable iwChanges2 = compareSmart(iwLines2, iwLines3, indicator);
    iwChanges2 = optimizeLineChunks(lines2, lines3, iwChanges2, indicator);
    FairDiffIterable iterable2 = correctChangesSecondStep(lines2, lines3, iwChanges2);

    if (keepIgnoredChanges && policy != DEFAULT) {
      return ComparisonMergeUtil.buildMerge(iterable1, iterable2,
                                            (index1, index2, index3) -> equalsDefaultPolicy(lines1, lines2, lines3, index1, index2, index3),
                                            indicator);
    }
    else {
      return ComparisonMergeUtil.buildSimple(iterable1, iterable2, indicator);
    }
  }

  private static boolean equalsDefaultPolicy(@NotNull List<? extends Line> lines1,
                                             @NotNull List<? extends Line> lines2,
                                             @NotNull List<? extends Line> lines3,
                                             int index1, int index2, int index3) {
    CharSequence content1 = lines1.get(index1).getContent();
    CharSequence content2 = lines2.get(index2).getContent();
    CharSequence content3 = lines3.get(index3).getContent();
    return ComparisonUtil.isEquals(content2, content1, DEFAULT) &&
           ComparisonUtil.isEquals(content2, content3, DEFAULT);
  }

  @NotNull
  private static FairDiffIterable correctChangesSecondStep(@NotNull final List<? extends Line> lines1,
                                                           @NotNull final List<? extends Line> lines2,
                                                           @NotNull final FairDiffIterable changes) {
    /*
     * We want to fix invalid matching here:
     *
     * .{        ..{
     * ..{   vs  ...{
     * ...{
     *
     * first step will return matching (0,2)-(0,2). And we should adjust it to (1,3)-(0,2)
     *
     *
     * From the other hand, we don't want to reduce number of IW-matched lines.
     *
     * .{         ...{
     * ..{    vs  ..{
     * ...{       .{
     *
     * first step will return (0,3)-(0,3) and 'correcting' it to (0,1)-(2,3) is wrong (and it will break ByWord highlighting).
     *
     *
     * Idea:
     * 1. lines are matched at first step and equal -> match them
     * 2. lines are not matched at first step -> do not match them
     * 3. lines are matched at first step and not equal ->
     *   a. find all IW-equal lines in the same unmatched block
     *   b. find a maximum matching between them, maximising amount of equal pairs in it
     *   c. match equal lines using result of the previous step
     */

    final ExpandChangeBuilder builder = new ExpandChangeBuilder(lines1, lines2);
    new Object() {
      private CharSequence sample = null;
      private int last1 = 0;
      private int last2 = 0;

      public void run() {
        for (Range range : changes.iterateUnchanged()) {
          int count = range.end1 - range.start1;
          for (int i = 0; i < count; i++) {
            int index1 = range.start1 + i;
            int index2 = range.start2 + i;
            Line line1 = lines1.get(index1);
            Line line2 = lines2.get(index2);

            if (!ComparisonUtil.isEquals(sample, line1.getContent(), IGNORE_WHITESPACES)) {
              if (line1.equals(line2)) {
                flush(index1, index2);
                builder.markEqual(index1, index2);
              }
              else {
                flush(index1, index2);
                sample = line1.getContent();
              }
            }
          }
        }
        flush(changes.getLength1(), changes.getLength2());
      }

      private void flush(int line1, int line2) {
        if (sample == null) return;

        int start1 = Math.max(last1, builder.getIndex1());
        int start2 = Math.max(last2, builder.getIndex2());

        IntList subLines1=new IntArrayList();
        IntList subLines2=new IntArrayList();
        for (int i = start1; i < line1; i++) {
          if (ComparisonUtil.isEquals(sample, lines1.get(i).getContent(), IGNORE_WHITESPACES)) {
            subLines1.add(i);
            last1 = i + 1;
          }
        }
        for (int i = start2; i < line2; i++) {
          if (ComparisonUtil.isEquals(sample, lines2.get(i).getContent(), IGNORE_WHITESPACES)) {
            subLines2.add(i);
            last2 = i + 1;
          }
        }

        assert subLines1.size() > 0 && subLines2.size() > 0;
        alignExactMatching(subLines1, subLines2);

        sample = null;
      }

      private void alignExactMatching(IntList subLines1, IntList subLines2) {
        int n = Math.max(subLines1.size(), subLines2.size());
        boolean skipAligning = n > 10 || // we use brute-force algorithm (C_n_k). This will limit search space by ~250 cases.
                               subLines1.size() == subLines2.size(); // nothing to do

        if (skipAligning) {
          int count = Math.min(subLines1.size(), subLines2.size());
          for (int i = 0; i < count; i++) {
            int index1 = subLines1.getInt(i);
            int index2 = subLines2.getInt(i);
            if (lines1.get(index1).equals(lines2.get(index2))) {
              builder.markEqual(index1, index2);
            }
          }
          return;
        }

        if (subLines1.size() < subLines2.size()) {
          int[] matching = getBestMatchingAlignment(subLines1, subLines2, lines1, lines2);
          for (int i = 0; i < subLines1.size(); i++) {
            int index1 = subLines1.getInt(i);
            int index2 = subLines2.getInt(matching[i]);
            if (lines1.get(index1).equals(lines2.get(index2))) {
              builder.markEqual(index1, index2);
            }
          }
        }
        else {
          int[] matching = getBestMatchingAlignment(subLines2, subLines1, lines2, lines1);
          for (int i = 0; i < subLines2.size(); i++) {
            int index1 = subLines1.getInt(matching[i]);
            int index2 = subLines2.getInt(i);
            if (lines1.get(index1).equals(lines2.get(index2))) {
              builder.markEqual(index1, index2);
            }
          }
        }
      }
    }.run();

    return fair(builder.finish());
  }

  private static int @NotNull [] getBestMatchingAlignment(@NotNull final IntList subLines1,
                                                          @NotNull final IntList subLines2,
                                                          @NotNull final List<? extends Line> lines1,
                                                          @NotNull final List<? extends Line> lines2) {
    assert subLines1.size() < subLines2.size();
    final int size = subLines1.size();

    final int[] comb = new int[size];
    final int[] best = new int[size];
    for (int i = 0; i < size; i++) {
      best[i] = i;
    }

    // find a combination with maximum weight (maximum number of equal lines)
    new Object() {
      int bestWeight = 0;

      public void run() {
        combinations(0, subLines2.size() - 1, 0);
      }

      private void combinations(int start, int n, int k) {
        if (k == size) {
          processCombination();
          return;
        }

        for (int i = start; i <= n; i++) {
          comb[k] = i;
          combinations(i + 1, n, k + 1);
        }
      }

      private void processCombination() {
        int weight = 0;
        for (int i = 0; i < size; i++) {
          int index1 = subLines1.getInt(i);
          int index2 = subLines2.getInt(comb[i]);
          if (lines1.get(index1).equals(lines2.get(index2))) weight++;
        }

        if (weight > bestWeight) {
          bestWeight = weight;
          System.arraycopy(comb, 0, best, 0, comb.length);
        }
      }
    }.run();

    return best;
  }

  @NotNull
  private static FairDiffIterable optimizeLineChunks(@NotNull List<? extends Line> lines1,
                                                     @NotNull List<? extends Line> lines2,
                                                     @NotNull FairDiffIterable iterable,
                                                     @NotNull ProgressIndicator indicator) {
    return new ChunkOptimizer.LineChunkOptimizer(lines1, lines2, iterable, indicator).build();
  }

  /*
   * Compare lines in two steps:
   *  - compare ignoring "unimportant" lines
   *  - correct changes (compare all lines gaps between matched chunks)
   */
  @NotNull
  private static FairDiffIterable compareSmart(@NotNull List<? extends Line> lines1,
                                               @NotNull List<? extends Line> lines2,
                                               @NotNull ProgressIndicator indicator) {
    int threshold = ComparisonUtil.getUnimportantLineCharCount();
    if (threshold == 0) return diff(lines1, lines2, indicator);

    Pair<List<Line>, IntList> bigLines1 = getBigLines(lines1, threshold);
    Pair<List<Line>, IntList> bigLines2 = getBigLines(lines2, threshold);

    FairDiffIterable changes = diff(bigLines1.first, bigLines2.first, indicator);
    return new ChangeCorrector.SmartLineChangeCorrector(bigLines1.second, bigLines2.second, lines1, lines2, changes, indicator).build();
  }

  @NotNull
  private static Pair<List<Line>, IntList> getBigLines(@NotNull List<? extends Line> lines, int threshold) {
    List<Line> bigLines = new ArrayList<>(lines.size());
    IntList indexes = new IntArrayList(lines.size());

    for (int i = 0; i < lines.size(); i++) {
      Line line = lines.get(i);
      if (line.getNonSpaceChars() > threshold) {
        bigLines.add(line);
        indexes.add(i);
      }
    }
    return Pair.create(bigLines, indexes);
  }

  @NotNull
  private static FairDiffIterable expandRanges(@NotNull List<? extends Line> lines1,
                                               @NotNull List<? extends Line> lines2,
                                               @NotNull FairDiffIterable iterable) {
    List<Range> changes = new ArrayList<>();

    for (Range ch : iterable.iterateChanges()) {
      Range expanded = TrimUtil.expand(lines1, lines2, ch.start1, ch.start2, ch.end1, ch.end2);
      if (!expanded.isEmpty()) changes.add(expanded);
    }

    return fair(create(changes, lines1.size(), lines2.size()));
  }

  //
  // Lines
  //

  @NotNull
  private static List<Line> getLines(@NotNull List<? extends CharSequence> text, @NotNull ComparisonPolicy policy) {
    //noinspection SSBasedInspection - Can't use ContainerUtil
    return text.stream().map(line -> new Line(line, policy)).collect(Collectors.toList());
  }

  @NotNull
  private static List<Line> convertMode(@NotNull List<? extends Line> original, @NotNull ComparisonPolicy policy) {
    List<Line> result = new ArrayList<>(original.size());
    for (Line line : original) {
      Line newLine = line.myPolicy != policy
                     ? new Line(line.getContent(), policy)
                     : line;
      result.add(newLine);
    }
    return result;
  }

  @NotNull
  public static List<MergeLineFragment> convertIntoMergeLineFragments(@NotNull List<? extends MergeRange> conflicts) {
    //noinspection SSBasedInspection - Can't use ContainerUtil
    return conflicts.stream().map(ch -> new MergeLineFragmentImpl(ch)).collect(Collectors.toList());
  }

  static class Line {
    @NotNull private final CharSequence myText;
    @NotNull private final ComparisonPolicy myPolicy;
    private final int myHash;
    private final int myNonSpaceChars;

    Line(@NotNull CharSequence text, @NotNull ComparisonPolicy policy) {
      myText = text;
      myPolicy = policy;
      myHash = ComparisonUtil.hashCode(text, policy);
      myNonSpaceChars = countNonSpaceChars(text);
    }

    @NotNull
    public CharSequence getContent() {
      return myText;
    }

    public int getNonSpaceChars() {
      return myNonSpaceChars;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Line line = (Line)o;
      assert myPolicy == line.myPolicy;

      if (hashCode() != line.hashCode()) return false;

      return ComparisonUtil.isEquals(getContent(), line.getContent(), myPolicy);
    }

    @Override
    public int hashCode() {
      return myHash;
    }

    private static int countNonSpaceChars(@NotNull CharSequence text) {
      int nonSpace = 0;

      int len = text.length();
      int offset = 0;

      while (offset < len) {
        char c = text.charAt(offset);
        if (!isWhiteSpace(c)) nonSpace++;
        offset++;
      }

      return nonSpace;
    }
  }
}
