/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.ex;

import com.intellij.diff.comparison.ByLine;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.comparison.TrimUtil;
import com.intellij.diff.comparison.iterables.DiffIterableUtil;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.ex.Range.InnerRange;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RangesBuilder {
  private static final Logger LOG = Logger.getInstance(RangesBuilder.class);

  @NotNull
  public static List<Range> createRanges(@NotNull Document current, @NotNull Document vcs) throws FilesTooBigForDiffException {
    return createRanges(current, vcs, false);
  }

  @NotNull
  public static List<Range> createRanges(@NotNull Document current, @NotNull Document vcs, boolean innerWhitespaceChanges)
    throws FilesTooBigForDiffException {
    return createRanges(DiffUtil.getLines(current), DiffUtil.getLines(vcs), 0, 0, innerWhitespaceChanges);
  }

  @NotNull
  public static List<Range> createRanges(@NotNull List<String> current,
                                         @NotNull List<String> vcs,
                                         int currentShift,
                                         int vcsShift,
                                         boolean innerWhitespaceChanges) throws FilesTooBigForDiffException {
    try {
      if (innerWhitespaceChanges) {
        return createRangesSmart(current, vcs, currentShift, vcsShift);
      }
      else {
        return createRangesSimple(current, vcs, currentShift, vcsShift);
      }
    }
    catch (DiffTooBigException e) {
      throw new FilesTooBigForDiffException();
    }
  }

  @NotNull
  private static List<Range> createRangesSimple(@NotNull List<String> current,
                                                @NotNull List<String> vcs,
                                                int currentShift,
                                                int vcsShift) throws DiffTooBigException {
    FairDiffIterable iterable = ByLine.compare(vcs, current, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE);

    List<Range> result = new ArrayList<>();
    for (com.intellij.diff.util.Range range : iterable.iterateChanges()) {
      int vcsLine1 = vcsShift + range.start1;
      int vcsLine2 = vcsShift + range.end1;
      int currentLine1 = currentShift + range.start2;
      int currentLine2 = currentShift + range.end2;

      result.add(new Range(currentLine1, currentLine2, vcsLine1, vcsLine2));
    }
    return result;
  }

  @NotNull
  private static List<Range> createRangesSmart(@NotNull List<String> current,
                                               @NotNull List<String> vcs,
                                               int currentShift,
                                               int vcsShift) throws DiffTooBigException {
    FairDiffIterable iwIterable = ByLine.compare(vcs, current, ComparisonPolicy.IGNORE_WHITESPACES, DumbProgressIndicator.INSTANCE);

    RangeBuilder rangeBuilder = new RangeBuilder(current, vcs, currentShift, vcsShift);

    for (com.intellij.diff.util.Range range : iwIterable.iterateUnchanged()) {
      int count = range.end1 - range.start1;
      for (int i = 0; i < count; i++) {
        int vcsIndex = range.start1 + i;
        int currentIndex = range.start2 + i;
        if (vcs.get(vcsIndex).equals(current.get(currentIndex))) {
          rangeBuilder.markEqual(vcsIndex, currentIndex);
        }
      }
    }

    return rangeBuilder.finish();
  }

  private static class RangeBuilder extends DiffIterableUtil.ChangeBuilderBase {
    @NotNull private final List<String> myCurrent;
    @NotNull private final List<String> myVcs;
    private final int myCurrentShift;
    private final int myVcsShift;

    @NotNull private final List<Range> myResult = new ArrayList<>();

    public RangeBuilder(@NotNull List<String> current,
                        @NotNull List<String> vcs,
                        int currentShift,
                        int vcsShift) {
      super(vcs.size(), current.size());
      myCurrent = current;
      myVcs = vcs;
      myCurrentShift = currentShift;
      myVcsShift = vcsShift;
    }

    @NotNull
    public List<Range> finish() {
      doFinish();
      return myResult;
    }

    @Override
    protected void addChange(int vcsStart, int currentStart, int vcsEnd, int currentEnd) {
      com.intellij.diff.util.Range range = TrimUtil.expand(myVcs, myCurrent, vcsStart, currentStart, vcsEnd, currentEnd);
      if (range.isEmpty()) return;

      List<InnerRange> innerRanges = calcInnerRanges(range);
      Range newRange = new Range(range.start2, range.end2, range.start1, range.end1, innerRanges);
      newRange.shift(myCurrentShift);
      newRange.vcsShift(myVcsShift);

      myResult.add(newRange);
    }

    @Nullable
    private List<InnerRange> calcInnerRanges(@NotNull com.intellij.diff.util.Range blockRange) {
      try {
        List<String> vcs = myVcs.subList(blockRange.start1, blockRange.end1);
        List<String> current = myCurrent.subList(blockRange.start2, blockRange.end2);

        ArrayList<InnerRange> result = new ArrayList<>();
        FairDiffIterable iwIterable = ByLine.compare(vcs, current, ComparisonPolicy.IGNORE_WHITESPACES, DumbProgressIndicator.INSTANCE);
        for (Pair<com.intellij.diff.util.Range, Boolean> pair : DiffIterableUtil.iterateAll(iwIterable)) {
          com.intellij.diff.util.Range range = pair.first;
          Boolean equals = pair.second;

          byte type = equals ? Range.EQUAL : getChangeType(range.start1, range.end1, range.start2, range.end2);
          result.add(new InnerRange(range.start2 + blockRange.start2, range.end2 + blockRange.start2,
                                    type));
        }
        result.trimToSize();
        return result;
      }
      catch (DiffTooBigException e) {
        return null;
      }
    }
  }

  private static byte getChangeType(int vcsStart, int vcsEnd, int currentStart, int currentEnd) {
    int deleted = vcsEnd - vcsStart;
    int inserted = currentEnd - currentStart;
    if (deleted > 0 && inserted > 0) return Range.MODIFIED;
    if (deleted > 0) return Range.DELETED;
    if (inserted > 0) return Range.INSERTED;
    LOG.error("Unknown change type");
    return Range.EQUAL;
  }
}
