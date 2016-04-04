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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ComparisonMergeUtil {
  @NotNull
  public static List<MergeRange> buildFair(@NotNull FairDiffIterable fragments1,
                                           @NotNull FairDiffIterable fragments2,
                                           @NotNull ProgressIndicator indicator) {
    assert fragments1.getLength1() == fragments2.getLength1();

    FairMergeBuilder builder = new FairMergeBuilder();

    PeekIterator<Range> unchanged1 = new PeekIterator<Range>(fragments1.unchanged());
    PeekIterator<Range> unchanged2 = new PeekIterator<Range>(fragments2.unchanged());

    while (!unchanged1.atEnd() || !unchanged2.atEnd()) {
      indicator.checkCanceled();

      boolean left;
      if (unchanged1.atEnd()) {
        left = false;
      }
      else if (unchanged2.atEnd()) {
        left = true;
      }
      else {
        Range range1 = unchanged1.peek();
        Range range2 = unchanged2.peek();

        left = range1.start1 < range2.start1;
      }

      if (left) {
        builder.add(unchanged1.peek(), Side.LEFT);
        unchanged1.advance();
      }
      else {
        builder.add(unchanged2.peek(), Side.RIGHT);
        unchanged2.advance();
      }
    }

    return builder.finish(fragments1.getLength2(), fragments1.getLength1(), fragments2.getLength2());
  }

  private static class FairMergeBuilder {
    @NotNull private final ArrayList<MergeRange> myResult = new ArrayList<MergeRange>();

    @NotNull private final EqualPair[] myPairs = new EqualPair[2]; // LEFT, RIGHT
    @NotNull private final int[] myProcessed = new int[]{0, 0, 0}; // LEFT, RIGHT, BASE

    public void add(@NotNull Range range, @NotNull Side side) {
      int index = side.getIndex();
      int otherIndex = side.other().getIndex();
      EqualPair pair = new EqualPair(range, side);

      assert myPairs[index] == null || pair.getBaseStart() - myPairs[index].getBaseEnd() >= 0; // '==' can be in case of insertion
      assert myPairs[otherIndex] == null || pair.getBaseStart() >= myPairs[otherIndex].getBaseStart();

      myPairs[index] = pair;
      if (myPairs[otherIndex] != null && myPairs[index].getBaseStart() >= myPairs[otherIndex].getBaseEnd()) myPairs[otherIndex] = null;

      process();
    }

    @NotNull
    public List<MergeRange> finish(int leftLength, int baseLength, int rightLength) {
      if (!compare(new int[]{leftLength, rightLength, baseLength}, myProcessed)) {
        processConflict(leftLength, baseLength, rightLength);
      }
      return myResult;
    }

    // see "A Formal Investigation of Diff3"
    private void process() {
      while (myPairs[0] != null && myPairs[1] != null) {
        if (myPairs[0].startsFrom(myProcessed) && myPairs[1].startsFrom(myProcessed)) {
          // process stable
          int len = Math.min(myPairs[0].getLength(), myPairs[1].getLength());
          if (!myPairs[0].cutHead(len)) {
            myPairs[0] = null;
          }
          if (!myPairs[1].cutHead(len)) {
            myPairs[1] = null;
          }
          myProcessed[0] += len;
          myProcessed[1] += len;
          myProcessed[2] += len;
        }
        else {
          //process unstable
          int nextBase = Math.max(myPairs[0].getBaseStart(), myPairs[1].getBaseStart());
          int[] nextVersion = new int[2];
          nextVersion[0] = nextBase - myPairs[0].getBaseStart() + myPairs[0].getVersionStart();
          nextVersion[1] = nextBase - myPairs[1].getBaseStart() + myPairs[1].getVersionStart();

          processConflict(nextVersion[0], nextBase, nextVersion[1]);

          if (!myPairs[0].cutHead(nextBase - myPairs[0].getBaseStart())) {
            myPairs[0] = null;
          }
          if (!myPairs[1].cutHead(nextBase - myPairs[1].getBaseStart())) {
            myPairs[1] = null;
          }
        }
      }
    }

    private void processConflict(int nextLeft, int nextBase, int nextRight) {
      myResult.add(new MergeRange(
        myProcessed[0], nextLeft,
        myProcessed[2], nextBase,
        myProcessed[1], nextRight
      ));

      myProcessed[0] = nextLeft;
      myProcessed[2] = nextBase;
      myProcessed[1] = nextRight;
    }

    private static boolean compare(@NotNull int[] lengths, @NotNull int[] processed) {
      for (int i = 0; i < lengths.length; i++) {
        if (lengths[i] != processed[i]) return false;
      }
      return true;
    }
  }

  private static class EqualPair {
    private int myBaseStart;
    private int myVersionStart;
    private int myLength;
    @NotNull private final Side mySide;

    public EqualPair(@NotNull Range range, @NotNull Side side) {
      myBaseStart = range.start1;
      myVersionStart = range.start2;
      myLength = range.end1 - range.start1;
      mySide = side;
    }

    public int getBaseStart() {
      return myBaseStart;
    }

    public int getBaseEnd() {
      return myBaseStart + myLength;
    }

    public int getVersionStart() {
      return myVersionStart;
    }

    public int getVersionEnd() {
      return myVersionStart + myLength;
    }

    public int getLength() {
      return myLength;
    }

    public boolean startsFrom(int[] bound) {
      return myVersionStart == bound[mySide.getIndex()] && myBaseStart == bound[2];
    }

    public boolean cutHead(int delta) {
      assert myLength >= delta;
      assert delta >= 0;

      myBaseStart += delta;
      myVersionStart += delta;
      myLength -= delta;
      return myLength > 0;
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
}