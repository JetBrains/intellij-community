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
package com.intellij.diff.comparison.iterables;

import com.intellij.diff.util.Range;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

class SubiterableDiffIterable extends ChangeDiffIterableBase {
  @NotNull private final DiffIterable myIterable;
  private final int myStart1;
  private final int myStart2;
  private final int myEnd1;
  private final int myEnd2;

  public SubiterableDiffIterable(@NotNull DiffIterable iterable, int start1, int end1, int start2, int end2) {
    super(end1 - start1, end2 - start2);
    myIterable = iterable;
    myStart1 = start1;
    myStart2 = start2;
    myEnd1 = end1;
    myEnd2 = end2;
  }

  @NotNull
  @Override
  protected ChangeIterable createChangeIterable() {
    return new SubiterableChangeIterable();
  }

  private class SubiterableChangeIterable implements ChangeIterable {
    private final Iterator<Range> myIterator;
    private Range myLast;

    public SubiterableChangeIterable() {
      myIterator = myIterable.changes();

      next();
    }

    @Override
    public boolean valid() {
      return myLast != null;
    }

    @Override
    public void next() {
      myLast = null;

      while (myIterator.hasNext()) {
        Range range = myIterator.next();
        if (range.end1 < myStart1 || range.end2 < myStart2) continue;
        if (range.start1 > myEnd1 || range.start2 > myEnd2) break;

        myLast = new Range(Math.max(myStart1, range.start1) - myStart1, Math.min(myEnd1, range.end1) - myStart1,
                           Math.max(myStart2, range.start2) - myStart2, Math.min(myEnd2, range.end2) - myStart2);
        if (!myLast.isEmpty()) break;
        myLast = null;
      }
    }

    @Override
    public int getStart1() {
      return myLast.start1;
    }

    @Override
    public int getStart2() {
      return myLast.start2;
    }

    @Override
    public int getEnd1() {
      return myLast.end1;
    }

    @Override
    public int getEnd2() {
      return myLast.end2;
    }
  }
}
