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

import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.util.MergeRange;
import com.intellij.diff.util.Range;
import com.intellij.diff.util.Side;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ComparisonMergeUtil {
  @NotNull
  public static List<MergeRange> buildFair(@NotNull FairDiffIterable fragments1,
                                           @NotNull FairDiffIterable fragments2,
                                           @NotNull ProgressIndicator indicator) {
    assert fragments1.getLength1() == fragments2.getLength1();
    return new FairMergeBuilder().execute(fragments1, fragments2);
  }

  private static class FairMergeBuilder {
    @NotNull private final ChangeBuilder myChangesBuilder = new ChangeBuilder();

    @NotNull
    public List<MergeRange> execute(@NotNull FairDiffIterable fragments1,
                                    @NotNull FairDiffIterable fragments2) {
      PeekIterator<Range> unchanged1 = new PeekIterator<>(fragments1.unchanged());
      PeekIterator<Range> unchanged2 = new PeekIterator<>(fragments2.unchanged());

      while (!unchanged1.atEnd() && !unchanged2.atEnd()) {
        Side side = add(unchanged1.peek(), unchanged2.peek());
        side.select(unchanged1, unchanged2).advance();
      }
      return finish(fragments1, fragments2);
    }

    @NotNull
    private Side add(@NotNull Range range1, @NotNull Range range2) {
      int start1 = range1.start1;
      int end1 = range1.end1;

      int start2 = range2.start1;
      int end2 = range2.end1;

      if (end1 <= start2) return Side.LEFT;
      if (end2 <= start1) return Side.RIGHT;

      int startBase = Math.max(start1, start2);
      int endBase = Math.min(end1, end2);

      int startShift1 = startBase - start1;
      int endCut1 = end1 - endBase;
      int startShift2 = startBase - start2;
      int endCut2 = end2 - endBase;

      int startLeft = range1.start2 + startShift1;
      int endLeft = range1.end2 - endCut1;
      int startRight = range2.start2 + startShift2;
      int endRight = range2.end2 - endCut2;

      myChangesBuilder.markEqual(startLeft, startBase, startRight, endLeft, endBase, endRight);

      return Side.fromLeft(end1 <= end2);
    }

    @NotNull
    private List<MergeRange> finish(@NotNull FairDiffIterable fragments1, @NotNull FairDiffIterable fragments2) {
      int length1 = fragments1.getLength2();
      int length2 = fragments1.getLength1();
      int length3 = fragments2.getLength2();

      return myChangesBuilder.finish(length1, length2, length3);
    }
  }

  private static class ChangeBuilder {
    @NotNull private final List<MergeRange> myChanges = new ArrayList<>();

    private int myIndex1 = 0;
    private int myIndex2 = 0;
    private int myIndex3 = 0;

    private void addChange(int start1, int start2, int start3, int end1, int end2, int end3) {
      if (start1 == end1 && start2 == end2 && start3 == end3) return;
      myChanges.add(new MergeRange(start1, end1, start2, end2, start3, end3));
    }

    public void markEqual(int start1, int start2, int start3, int end1, int end2, int end3) {
      assert myIndex1 <= start1;
      assert myIndex2 <= start2;
      assert myIndex3 <= start3;
      assert start1 <= end1;
      assert start2 <= end2;
      assert start3 <= end3;

      addChange(myIndex1, myIndex2, myIndex3, start1, start2, start3);

      myIndex1 = end1;
      myIndex2 = end2;
      myIndex3 = end3;
    }

    @NotNull
    public List<MergeRange> finish(int length1, int length2, int length3) {
      assert myIndex1 <= length1;
      assert myIndex2 <= length2;
      assert myIndex3 <= length3;

      addChange(myIndex1, myIndex2, myIndex3, length1, length2, length3);

      return myChanges;
    }
  }

  private static class PeekIterator<T> {
    @NotNull private final Iterator<T> myIterator;
    private T myValue = null;

    public PeekIterator(@NotNull Iterator<T> iterator) {
      myIterator = iterator;
      advance();
    }

    public boolean atEnd() {
      return myValue == null;
    }

    public boolean hasNext() {
      return myIterator.hasNext();
    }

    public T peek() {
      return myValue;
    }

    public void advance() {
      myValue = myIterator.hasNext() ? myIterator.next() : null;
    }
  }

  @Nullable
  public static CharSequence tryResolveConflict(@NotNull CharSequence leftText,
                                                @NotNull CharSequence baseText,
                                                @NotNull CharSequence rightText) {
    return MergeResolveUtil.tryResolveConflict(leftText, baseText, rightText);
  }
}