// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.comparison;

import com.intellij.diff.comparison.ByLine.Line;
import com.intellij.diff.comparison.iterables.DiffIterableUtil;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.util.Range;
import com.intellij.util.IntPair;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.diff.comparison.TrimUtil.expand;
import static com.intellij.diff.comparison.iterables.DiffIterableUtil.diff;
import static com.intellij.diff.comparison.iterables.DiffIterableUtil.fair;

/*
 * Base class for two-step diff algorithms.
 * Given matching between some sub-sequences of base sequences - build matching on whole base sequences
 */
abstract class ChangeCorrector {
  private final int myLength1;
  private final int myLength2;
  @NotNull private final FairDiffIterable myChanges;

  @NotNull protected final CancellationChecker myIndicator;

  @NotNull protected final DiffIterableUtil.ChangeBuilder myBuilder;

  ChangeCorrector(int length1,
                         int length2,
                         @NotNull FairDiffIterable changes,
                         @NotNull CancellationChecker indicator) {
    myLength1 = length1;
    myLength2 = length2;
    myChanges = changes;
    myIndicator = indicator;

    myBuilder = new DiffIterableUtil.ChangeBuilder(length1, length2);
  }

  @NotNull
  public FairDiffIterable build() {
    execute();
    return fair(myBuilder.finish());
  }

  protected void execute() {
    int last1 = 0;
    int last2 = 0;

    for (Range ch : myChanges.iterateUnchanged()) {
      int count = ch.end1 - ch.start1;
      for (int i = 0; i < count; i++) {
        IntPair range1 = getOriginalRange1(ch.start1 + i);
        IntPair range2 = getOriginalRange2(ch.start2 + i);

        int start1 = range1.first;
        int start2 = range2.first;
        int end1 = range1.second;
        int end2 = range2.second;

        matchGap(last1, start1, last2, start2);
        myBuilder.markEqual(start1, start2, end1, end2);

        last1 = end1;
        last2 = end2;
      }
    }
    matchGap(last1, myLength1, last2, myLength2);
  }

  // match elements in range [start1 - end1) -> [start2 - end2)
  protected abstract void matchGap(int start1, int end1, int start2, int end2);

  protected abstract IntPair getOriginalRange1(int index);

  protected abstract IntPair getOriginalRange2(int index);

  //
  // Implementations
  //

  public static class DefaultCharChangeCorrector extends ChangeCorrector {
    @NotNull private final ByChar.CodePointsOffsets myCodePoints1;
    @NotNull private final ByChar.CodePointsOffsets myCodePoints2;
    @NotNull private final CharSequence myText1;
    @NotNull private final CharSequence myText2;

    public DefaultCharChangeCorrector(@NotNull ByChar.CodePointsOffsets codePoints1,
                                      @NotNull ByChar.CodePointsOffsets codePoints2,
                                      @NotNull CharSequence text1,
                                      @NotNull CharSequence text2,
                                      @NotNull FairDiffIterable changes,
                                      @NotNull CancellationChecker indicator) {
      super(text1.length(), text2.length(), changes, indicator);
      myCodePoints1 = codePoints1;
      myCodePoints2 = codePoints2;
      myText1 = text1;
      myText2 = text2;
    }

    @Override
    protected void matchGap(int start1, int end1, int start2, int end2) {
      CharSequence inner1 = myText1.subSequence(start1, end1);
      CharSequence inner2 = myText2.subSequence(start2, end2);
      FairDiffIterable innerChanges = ByChar.compare(inner1, inner2, myIndicator);

      for (Range chunk : innerChanges.iterateUnchanged()) {
        myBuilder.markEqual(start1 + chunk.start1, start2 + chunk.start2, chunk.end1 - chunk.start1);
      }
    }

    @Override
    protected IntPair getOriginalRange1(int index) {
      int startOffset = myCodePoints1.charOffset(index);
      int endOffset = myCodePoints1.charOffsetAfter(index);
      return new IntPair(startOffset, endOffset);
    }

    @Override
    protected IntPair getOriginalRange2(int index) {
      int startOffset = myCodePoints2.charOffset(index);
      int endOffset = myCodePoints2.charOffsetAfter(index);
      return new IntPair(startOffset, endOffset);
    }
  }

  public static final class SmartLineChangeCorrector extends ChangeCorrector {
    @NotNull private final IntList myIndexes1;
    @NotNull private final IntList myIndexes2;
    @NotNull private final List<? extends Line> myLines1;
    @NotNull private final List<? extends Line> myLines2;

    public SmartLineChangeCorrector(@NotNull IntList indexes1,
                                    @NotNull IntList indexes2,
                                    @NotNull List<? extends Line> lines1,
                                    @NotNull List<? extends Line> lines2,
                                    @NotNull FairDiffIterable changes,
                                    @NotNull CancellationChecker indicator) {
      super(lines1.size(), lines2.size(), changes, indicator);
      myIndexes1 = indexes1;
      myIndexes2 = indexes2;
      myLines1 = lines1;
      myLines2 = lines2;
    }

    @Override
    protected void matchGap(int start1, int end1, int start2, int end2) {
      Range expand = expand(myLines1, myLines2, start1, start2, end1, end2);

      List<? extends Line> inner1 = myLines1.subList(expand.start1, expand.end1);
      List<? extends Line> inner2 = myLines2.subList(expand.start2, expand.end2);
      FairDiffIterable innerChanges = diff(inner1, inner2, myIndicator);

      myBuilder.markEqual(start1, start2, expand.start1, expand.start2);

      for (Range chunk : innerChanges.iterateUnchanged()) {
        myBuilder.markEqual(expand.start1 + chunk.start1, expand.start2 + chunk.start2, chunk.end1 - chunk.start1);
      }

      myBuilder.markEqual(expand.end1, expand.end2, end1, end2);
    }

    @Override
    protected IntPair getOriginalRange1(int index) {
      int offset = myIndexes1.getInt(index);
      return new IntPair(offset, offset + 1);
    }

    @Override
    protected IntPair getOriginalRange2(int index) {
      int offset = myIndexes2.getInt(index);
      return new IntPair(offset, offset + 1);
    }
  }
}
