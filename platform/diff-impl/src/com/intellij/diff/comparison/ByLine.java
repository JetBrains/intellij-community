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

import com.intellij.diff.comparison.iterables.DiffIterableUtil.TrimChangeBuilder;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.fragments.LineFragmentImpl;
import com.intellij.diff.util.IntPair;
import com.intellij.diff.util.Range;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.diff.comparison.TrimUtil.trimEnd;
import static com.intellij.diff.comparison.TrimUtil.trimStart;
import static com.intellij.diff.comparison.iterables.DiffIterableUtil.diff;
import static com.intellij.diff.comparison.iterables.DiffIterableUtil.fair;
import static com.intellij.openapi.util.text.StringUtil.isWhiteSpace;

public class ByLine {
  @NotNull
  public static List<LineFragment> compare(@NotNull CharSequence text1,
                                           @NotNull CharSequence text2,
                                           @NotNull ComparisonPolicy policy,
                                           @NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();

    List<Line> lines1 = getLines(text1, policy);
    List<Line> lines2 = getLines(text2, policy);

    FairDiffIterable changes = compareSmart(lines1, lines2, indicator);
    return convertIntoFragments(lines1, lines2, changes);
  }

  @NotNull
  public static List<LineFragment> compareTwoStep(@NotNull CharSequence text1,
                                                  @NotNull CharSequence text2,
                                                  @NotNull ComparisonPolicy policy,
                                                  @NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();

    List<Line> lines1 = getLines(text1, policy);
    List<Line> lines2 = getLines(text2, policy);

    List<Line> iwLines1 = convertToIgnoreWhitespace(lines1);
    List<Line> iwLines2 = convertToIgnoreWhitespace(lines2);

    FairDiffIterable iwChanges = compareSmart(iwLines1, iwLines2, indicator);
    FairDiffIterable changes = correctChangesSecondStep(lines1, lines2, iwChanges);
    return convertIntoFragments(lines1, lines2, changes);
  }

  @NotNull
  public static FairDiffIterable compareTwoStepFair(@NotNull CharSequence text1,
                                                    @NotNull CharSequence text2,
                                                    @NotNull ComparisonPolicy policy,
                                                    @NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();

    List<Line> lines1 = getLines(text1, policy);
    List<Line> lines2 = getLines(text2, policy);

    List<Line> iwLines1 = convertToIgnoreWhitespace(lines1);
    List<Line> iwLines2 = convertToIgnoreWhitespace(lines2);

    FairDiffIterable iwChanges = compareSmart(iwLines1, iwLines2, indicator);
    return correctChangesSecondStep(lines1, lines2, iwChanges);
  }

  //
  // Impl
  //

  @NotNull
  private static FairDiffIterable correctChangesSecondStep(@NotNull final List<Line> lines1,
                                                           @NotNull final List<Line> lines2,
                                                           @NotNull final FairDiffIterable changes) {
    /*
     * We want to fix invalid matching here:
     *
     * .{        ..{
     * ..{   vs  ...{
     * ...{
     *
     * first step will return matching (0,2)-(0,2). And we should adjust it to (1,3)-(0,2)
     *
     *
     * From the other hand, we don't want to reduce number of IW-matched lines.
     *
     * .{         ...{
     * ..{    vs  ..{
     * ...{       .{
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

    final TrimChangeBuilder builder = new TrimChangeBuilder(lines1, lines2);
    new Object() {
      private CharSequence sample = null;
      private int last1 = -1;
      private int last2 = -1;

      public void run() {
        for (Range range : changes.iterateUnchanged()) {
          int count = range.end1 - range.start1;
          for (int i = 0; i < count; i++) {
            int index1 = range.start1 + i;
            int index2 = range.start2 + i;
            Line line1 = lines1.get(index1);
            Line line2 = lines2.get(index2);

            if (line1.equals(line2)) {
              flush(index1, index2);
              builder.markEqual(index1, index2);
            }
            else {
              if (!StringUtil.equalsIgnoreWhitespaces(sample, line1.getContent())) {
                flush(index1, index2);
                sample = line1.getContent();
                last1 = index1;
                last2 = index2;
              }
            }
          }
        }
        flush(changes.getLength1(), changes.getLength2());
      }

      private void flush(int line1, int line2) {
        if (sample == null) return;

        TIntArrayList subLines1 = new TIntArrayList();
        TIntArrayList subLines2 = new TIntArrayList();
        for (int i = last1; i < line1; i++) {
          if (StringUtil.equalsIgnoreWhitespaces(sample, lines1.get(i).getContent())) {
            subLines1.add(i);
          }
        }
        for (int i = last2; i < line2; i++) {
          if (StringUtil.equalsIgnoreWhitespaces(sample, lines2.get(i).getContent())) {
            subLines2.add(i);
          }
        }

        assert subLines1.size() > 0 && subLines2.size() > 0;
        alignExactMatching(subLines1, subLines2);

        sample = null;
        last1 = -1;
        last2 = -1;
      }

      private void alignExactMatching(TIntArrayList subLines1, TIntArrayList subLines2) {
        if (subLines1.size() == subLines2.size()) return;

        int n = Math.max(subLines1.size(), subLines2.size());
        if (n > 10) return; // we use brute-force algorithm (C_n_k). This will limit search space by ~250 cases.

        if (subLines1.size() < subLines2.size()) {
          int[] matching = getBestMatchingAlignment(subLines1, subLines2, lines1, lines2);
          for (int i = 0; i < subLines1.size(); i++) {
            int index1 = subLines1.get(i);
            int index2 = subLines2.get(matching[i]);
            if (lines1.get(index1).equals(lines2.get(index2))) {
              builder.markEqual(index1, index2);
            }
          }
        }
        else {
          int[] matching = getBestMatchingAlignment(subLines2, subLines1, lines2, lines1);
          for (int i = 0; i < subLines2.size(); i++) {
            int index1 = subLines1.get(matching[i]);
            int index2 = subLines2.get(i);
            if (lines1.get(index1).equals(lines2.get(index2))) {
              builder.markEqual(index1, index2);
            }
          }
        }
      }
    }.run();

    return fair(builder.finish());
  }

  @NotNull
  private static int[] getBestMatchingAlignment(@NotNull final TIntArrayList subLines1,
                                                @NotNull final TIntArrayList subLines2,
                                                @NotNull final List<Line> lines1,
                                                @NotNull final List<Line> lines2) {
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
          int index1 = subLines1.get(i);
          int index2 = subLines2.get(comb[i]);
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
  private static List<LineFragment> convertIntoFragments(@NotNull List<Line> lines1,
                                                         @NotNull List<Line> lines2,
                                                         @NotNull FairDiffIterable changes) {
    List<LineFragment> fragments = new ArrayList<LineFragment>();
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

  /*
   * Compare lines in two steps:
   *  - compare ignoring "unimportant" lines
   *  - correct changes (compare all lines gaps between matched chunks)
   */
  @NotNull
  private static FairDiffIterable compareSmart(@NotNull List<Line> lines1,
                                               @NotNull List<Line> lines2,
                                               @NotNull ProgressIndicator indicator) {
    int threshold = Registry.intValue("diff.unimportant.line.char.count");
    if (threshold == 0) return diff(lines1, lines2, indicator);

    List<LineWrapper> newLines1 = new ArrayList<LineWrapper>(lines1.size());
    List<LineWrapper> newLines2 = new ArrayList<LineWrapper>(lines2.size());

    for (int i = 0; i < lines1.size(); i++) {
      Line line = lines1.get(i);
      if (line.getNonSpaceChars() > threshold) newLines1.add(new LineWrapper(line, i));
    }
    for (int i = 0; i < lines2.size(); i++) {
      Line line = lines2.get(i);
      if (line.getNonSpaceChars() > threshold) newLines2.add(new LineWrapper(line, i));
    }

    FairDiffIterable changes = diff(newLines1, newLines2, indicator);
    return new ChangeCorrector.SmartLineChangeCorrector(newLines1, newLines2, lines1, lines2, changes, indicator).build();
  }

  //
  // Lines
  //

  @NotNull
  private static List<Line> getLines(@NotNull CharSequence text, @NotNull ComparisonPolicy policy) {
    List<Line> lines = new ArrayList<Line>();

    int offset = 0;
    while (true) {
      Line line = createLine(text, offset, policy);
      lines.add(line);
      offset = line.getOffset2();
      if (!line.hasNewline()) break;
    }

    return lines;
  }

  @NotNull
  private static Line createLine(@NotNull CharSequence text, int offset, @NotNull ComparisonPolicy policy) {
    switch (policy) {
      case DEFAULT:
        return Line.createDefault(text, offset);
      case IGNORE_WHITESPACES:
        return Line.createIgnore(text, offset);
      case TRIM_WHITESPACES:
        return Line.createTrim(text, offset);
      default:
        throw new IllegalArgumentException(policy.name());
    }
  }

  @NotNull
  private static List<Line> convertToIgnoreWhitespace(@NotNull List<Line> original) {
    List<Line> result = new ArrayList<Line>(original.size());

    for (Line line : original) {
      result.add(Line.createIgnore(line));
    }

    return result;
  }

  static class Line extends TextChunk {
    enum Mode {DEFAULT, TRIM, IGNORE}

    @NotNull private final Mode myMode;
    private final int myHash;
    private final int myNonSpaceChars;
    private final boolean myNewline;

    public Line(@NotNull CharSequence text, int offset1, int offset2,
                @NotNull Mode mode, int hash, int nonSpaceChars, boolean newline) {
      super(text, offset1, offset2);
      myMode = mode;
      myHash = hash;
      myNonSpaceChars = nonSpaceChars;
      myNewline = newline;
    }

    public boolean hasNewline() {
      return myNewline;
    }

    @NotNull
    public CharSequence getContent() {
      return getText().subSequence(getOffset1(), getOffset2() - (myNewline ? 1 : 0));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Line line = (Line)o;
      assert myMode == line.myMode;

      if (hashCode() != line.hashCode()) return false;

      switch (myMode) {
        case DEFAULT:
          return StringUtil.equals(getContent(), line.getContent());
        case TRIM:
          return StringUtil.equalsTrimWhitespaces(getContent(), line.getContent());
        case IGNORE:
          return StringUtil.equalsIgnoreWhitespaces(getContent(), line.getContent());
        default:
          throw new IllegalArgumentException(myMode.toString());
      }
    }

    @Override
    public int hashCode() {
      return myHash;
    }

    public int getNonSpaceChars() {
      return myNonSpaceChars;
    }

    public static Line createDefault(@NotNull CharSequence text, int startOffset) {
      int len = text.length();

      int h = 0;
      int nonSpace = 0;
      boolean newline = false;

      int offset = startOffset;
      while (offset < len) {
        char c = text.charAt(offset);
        if (c == '\n') {
          offset++;
          newline = true;
          break;
        }
        if (!isWhiteSpace(c)) nonSpace++;
        h = 31 * h + c;
        offset++;
      }

      return new Line(text, startOffset, offset, Mode.DEFAULT, h, nonSpace, newline);
    }

    public static Line createIgnore(@NotNull CharSequence text, int startOffset) {
      int len = text.length();

      int h = 0;
      int nonSpace = 0;
      boolean newline = false;

      int offset = startOffset;
      while (offset < len) {
        char c = text.charAt(offset);
        if (c == '\n') {
          offset++;
          newline = true;
          break;
        }
        if (!isWhiteSpace(c)) {
          nonSpace++;
          h = 31 * h + c;
        }
        offset++;
      }

      return new Line(text, startOffset, offset, Mode.IGNORE, h, nonSpace, newline);
    }

    public static Line createTrim(@NotNull CharSequence text, int startOffset) {
      int len = text.length();

      int nonSpace = 0;
      boolean newline = false;

      int offset = startOffset;
      while (offset < len) {
        char c = text.charAt(offset);
        if (c == '\n') {
          offset++;
          newline = true;
          break;
        }
        if (!isWhiteSpace(c)) nonSpace++;
        offset++;
      }

      int h = calcTrimHash(text, startOffset, offset);

      return new Line(text, startOffset, offset, Mode.TRIM, h, nonSpace, newline);
    }


    private static int calcTrimHash(@NotNull CharSequence text, int offset1, int offset2) {
      offset1 = trimStart(text, offset1, offset2);
      offset2 = trimEnd(text, offset1, offset2);

      return StringUtil.stringHashCode(text, offset1, offset2);
    }

    public static Line createIgnore(@NotNull Line original) {
      return createIgnore(original.getText(), original.getOffset1());
    }
  }

  static class LineWrapper implements ChangeCorrector.CorrectableData {
    @NotNull private final Line myLine;
    private final int myIndex;

    public LineWrapper(@NotNull Line line, int index) {
      myLine = line;
      myIndex = index;
    }

    @NotNull
    public Line getLine() {
      return myLine;
    }

    public int getIndex() {
      return myIndex;
    }

    @Override
    public int hashCode() {
      return myLine.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) return true;
      if (o == null || getClass() != o.getClass()) return false;

      return myLine.equals(((LineWrapper)o).myLine);
    }

    @Override
    public int getOriginalIndex() {
      return myIndex;
    }
  }
}
