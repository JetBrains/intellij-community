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

import com.intellij.diff.comparison.ByLine.Line;
import com.intellij.diff.comparison.ByWord.InlineChunk;
import com.intellij.diff.comparison.ByWord.NewlineChunk;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.util.Range;
import com.intellij.diff.util.Side;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.diff.comparison.TrimUtil.expandBackward;
import static com.intellij.diff.comparison.TrimUtil.expandForward;
import static com.intellij.diff.comparison.iterables.DiffIterableUtil.createUnchanged;
import static com.intellij.diff.comparison.iterables.DiffIterableUtil.fair;
import static com.intellij.openapi.util.text.StringUtil.isWhiteSpace;

abstract class ChunkOptimizer<T> {
  @NotNull protected final List<T> myData1;
  @NotNull protected final List<T> myData2;
  @NotNull private final FairDiffIterable myIterable;

  @NotNull protected final ProgressIndicator myIndicator;

  @NotNull private final List<Range> myRanges;

  public ChunkOptimizer(@NotNull List<T> data1,
                        @NotNull List<T> data2,
                        @NotNull FairDiffIterable iterable,
                        @NotNull ProgressIndicator indicator) {
    myData1 = data1;
    myData2 = data2;
    myIterable = iterable;
    myIndicator = indicator;

    myRanges = new ArrayList<Range>();
  }

  @NotNull
  public FairDiffIterable build() {
    for (Range range : myIterable.iterateUnchanged()) {
      myRanges.add(range);
      processLastRanges();
    }

    return fair(createUnchanged(myRanges, myData1.size(), myData2.size()));
  }

  private void processLastRanges() {
    if (myRanges.size() < 2) return; // nothing to do

    Range range1 = myRanges.get(myRanges.size() - 2);
    Range range2 = myRanges.get(myRanges.size() - 1);
    if (range1.end1 != range2.start1 && range1.end2 != range2.start2) {
      // if changes do not touch and we still can perform one of these optimisations,
      // it means that given DiffIterable is not LCS (because we can build a smaller one). This should not happen.
      return;
    }

    int count1 = range1.end1 - range1.start1;
    int count2 = range2.end1 - range2.start1;

    int equalForward = expandForward(myData1, myData2, range1.end1, range1.end2, range1.end1 + count2, range1.end2 + count2);
    int equalBackward = expandBackward(myData1, myData2, range2.start1 - count1, range2.start2 - count1, range2.start1, range2.start2);

    // nothing to do
    if (equalForward == 0 && equalBackward == 0) return;

    // merge chunks left [A]B[B] -> [AB]B
    if (equalForward == count2) {
      myRanges.remove(myRanges.size() - 1);
      myRanges.remove(myRanges.size() - 1);
      myRanges.add(new Range(range1.start1, range1.end1 + count2, range1.start2, range1.end2 + count2));
      processLastRanges();
      return;
    }

    // merge chunks right [A]A[B] -> A[AB]
    if (equalBackward == count1) {
      myRanges.remove(myRanges.size() - 1);
      myRanges.remove(myRanges.size() - 1);
      myRanges.add(new Range(range2.start1 - count1, range2.end1, range2.start2 - count1, range2.end2));
      processLastRanges();
      return;
    }


    Side touchSide = Side.fromLeft(range1.end1 == range2.start1);

    int shift = getShift(touchSide, equalForward, equalBackward, range1, range2);
    if (shift != 0) {
      myRanges.remove(myRanges.size() - 1);
      myRanges.remove(myRanges.size() - 1);
      myRanges.add(new Range(range1.start1, range1.end1 + shift, range1.start2, range1.end2 + shift));
      myRanges.add(new Range(range2.start1 + shift, range2.end1, range2.start2 + shift, range2.end2));
    }
  }

  // 0 - do nothing
  // >0 - shift forward
  // <0 - shift backward
  protected abstract int getShift(@NotNull Side touchSide, int equalForward, int equalBackward,
                                  @NotNull Range range1, @NotNull Range range2);

  //
  // Implementations
  //

  /*
   * 1. Minimise amount of chunks
   *      good: "AX[AB]" - "[AB]"
   *      bad: "[A]XA[B]" - "[A][B]"
   *
   * 2. Minimise amount of modified 'sentences', where sentence is a sequence of words, that are not separated by whitespace
   *      good: "[AX] [AZ]" - "[AX] AY [AZ]"
   *      bad: "[AX A][Z]" - "[AX A]Y A[Z]"
   *      ex: "1.0.123 1.0.155" vs "1.0.123 1.0.134 1.0.155"
   */
  public static class WordChunkOptimizer extends ChunkOptimizer<InlineChunk> {
    @NotNull private final CharSequence myText1;
    @NotNull private final CharSequence myText2;

    public WordChunkOptimizer(@NotNull List<InlineChunk> words1,
                              @NotNull List<InlineChunk> words2,
                              @NotNull CharSequence text1,
                              @NotNull CharSequence text2,
                              @NotNull FairDiffIterable changes,
                              @NotNull ProgressIndicator indicator) {
      super(words1, words2, changes, indicator);
      myText1 = text1;
      myText2 = text2;
    }

    @Override
    protected int getShift(@NotNull Side touchSide, int equalForward, int equalBackward, @NotNull Range range1, @NotNull Range range2) {
      List<InlineChunk> touchWords = touchSide.select(myData1, myData2);
      CharSequence touchText = touchSide.select(myText1, myText2);
      int touchStart = touchSide.select(range2.start1, range2.start2);

      // check if chunks are already separated by whitespaces
      if (isSeparatedWithWhitespace(touchText, touchWords.get(touchStart - 1), touchWords.get(touchStart))) return 0;

      // shift chunks left [X]A Y[A ZA] -> [XA] YA [ZA]
      //                   [X][A ZA] -> [XA] [ZA]
      int leftShift = findSequenceEdgeShift(touchText, touchWords, touchStart, equalForward, true);
      if (leftShift > 0) return leftShift;

      // shift chunks right [AX A]Y A[Z] -> [AX] AY [AZ]
      //                    [AX A][Z] -> [AX] [AZ]
      int rightShift = findSequenceEdgeShift(touchText, touchWords, touchStart - 1, equalBackward, false);
      if (rightShift > 0) return -rightShift;

      // nothing to do
      return 0;
    }

    private static int findSequenceEdgeShift(@NotNull CharSequence text, @NotNull List<InlineChunk> words, int offset, int count,
                                             boolean leftToRight) {
      for (int i = 0; i < count; i++) {
        InlineChunk word1;
        InlineChunk word2;
        if (leftToRight) {
          word1 = words.get(offset + i);
          word2 = words.get(offset + i + 1);
        }
        else {
          word1 = words.get(offset - i - 1);
          word2 = words.get(offset - i);
        }
        if (isSeparatedWithWhitespace(text, word1, word2)) return i + 1;
      }
      return -1;
    }

    private static boolean isSeparatedWithWhitespace(@NotNull CharSequence text, @NotNull InlineChunk word1, @NotNull InlineChunk word2) {
      if (word1 instanceof NewlineChunk || word2 instanceof NewlineChunk) return true;

      int offset1 = word1.getOffset2();
      int offset2 = word2.getOffset1();

      for (int i = offset1; i < offset2; i++) {
        if (isWhiteSpace(text.charAt(i))) return true;
      }
      return false;
    }
  }

  /*
   * 1. Minimise amount of chunks
   *      good: "AX[AB]" - "[AB]"
   *      bad: "[A]XA[B]" - "[A][B]"
   *
   * 2. Prefer insertions/deletions, that are bounded by empty(or 'unimportant') line
   *      good: "ABooYZ [ABuuYZ ]ABzzYZ" - "ABooYZ []ABzzYZ"
   *      bad: "ABooYZ AB[uuYZ AB]zzYZ" - "ABooYZ AB[]zzYZ"
   */
  public static class LineChunkOptimizer extends ChunkOptimizer<Line> {
    private final int myThreshold;

    public LineChunkOptimizer(@NotNull List<Line> lines1,
                              @NotNull List<Line> lines2,
                              @NotNull FairDiffIterable changes,
                              @NotNull ProgressIndicator indicator) {
      super(lines1, lines2, changes, indicator);
      myThreshold = Registry.intValue("diff.unimportant.line.char.count");
    }

    @Override
    protected int getShift(@NotNull Side touchSide, int equalForward, int equalBackward, @NotNull Range range1, @NotNull Range range2) {
      Integer shift;

      shift = getUnchangedBoundaryShift(touchSide, equalForward, equalBackward, range1, range2, 0);
      if (shift != null) return shift;

      shift = getChangedBoundaryShift(touchSide, equalForward, equalBackward, range1, range2, 0);
      if (shift != null) return shift;

      shift = getUnchangedBoundaryShift(touchSide, equalForward, equalBackward, range1, range2, myThreshold);
      if (shift != null) return shift;

      shift = getChangedBoundaryShift(touchSide, equalForward, equalBackward, range1, range2, myThreshold);
      if (shift != null) return shift;

      return 0;
    }

    /**
     * search for an empty line boundary in unchanged lines
     * ie: we want insertion/deletion to go right before/after of an empty line
     */
    @Nullable
    private Integer getUnchangedBoundaryShift(@NotNull Side touchSide,
                                              int equalForward, int equalBackward,
                                              @NotNull Range range1, @NotNull Range range2,
                                              int threshold) {
      List<Line> touchLines = touchSide.select(myData1, myData2);
      int touchStart = touchSide.select(range2.start1, range2.start2);

      int shiftForward = findNextUnimportantLine(touchLines, touchStart, equalForward + 1, threshold);
      int shiftBackward = findPrevUnimportantLine(touchLines, touchStart - 1, equalBackward + 1, threshold);

      return getShift(shiftForward, shiftBackward);
    }

    /**
     * search for an empty line boundary in changed lines
     * ie: we want insertion/deletion to start/end with an empty line
     */
    @Nullable
    private Integer getChangedBoundaryShift(@NotNull Side touchSide,
                                            int equalForward, int equalBackward,
                                            @NotNull Range range1, @NotNull Range range2,
                                            int threshold) {
      Side nonTouchSide = touchSide.other();
      List<Line> nonTouchLines = nonTouchSide.select(myData1, myData2);
      int changeStart = nonTouchSide.select(range1.end1, range1.end2);
      int changeEnd = nonTouchSide.select(range2.start1, range2.start2);

      int shiftForward = findNextUnimportantLine(nonTouchLines, changeStart, equalForward + 1, threshold);
      int shiftBackward = findPrevUnimportantLine(nonTouchLines, changeEnd - 1, equalBackward + 1, threshold);

      return getShift(shiftForward, shiftBackward);
    }

    private static int findNextUnimportantLine(@NotNull List<Line> lines, int offset, int count, int threshold) {
      for (int i = 0; i < count; i++) {
        if (lines.get(offset + i).getNonSpaceChars() <= threshold) return i;
      }
      return -1;
    }

    private static int findPrevUnimportantLine(@NotNull List<Line> lines, int offset, int count, int threshold) {
      for (int i = 0; i < count; i++) {
        if (lines.get(offset - i).getNonSpaceChars() <= threshold) return i;
      }
      return -1;
    }

    @Nullable
    private static Integer getShift(int shiftForward, int shiftBackward) {
      if (shiftForward == -1 && shiftBackward == -1) return null;
      if (shiftForward == 0 || shiftBackward == 0) return 0;

      return shiftForward != -1 ? shiftForward : -shiftBackward;
    }
  }
}
