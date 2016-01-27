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

abstract class ChangeDiffIterableBase extends DiffIterableBase {
  private final int myLength1;
  private final int myLength2;

  public ChangeDiffIterableBase(int length1, int length2) {
    myLength1 = length1;
    myLength2 = length2;
  }

  @Override
  public int getLength1() {
    return myLength1;
  }

  @Override
  public int getLength2() {
    return myLength2;
  }

  @NotNull
  @Override
  public Iterator<Range> changes() {
    return new MyIterator<Range>() {
      @NotNull private final ChangeIterable myIterable = createChangeIterable();

      @Override
      public boolean hasNext() {
        return myIterable.valid();
      }

      @Override
      public Range next() {
        Range range = new Range(myIterable.getStart1(), myIterable.getEnd1(), myIterable.getStart2(), myIterable.getEnd2());
        myIterable.next();
        return range;
      }
    };
  }

  @NotNull
  @Override
  public Iterator<Range> unchanged() {
    return new MyIterator<Range>() {
      @NotNull private final ChangeIterable myIterable = createChangeIterable();

      int lastIndex1 = 0;
      int lastIndex2 = 0;

      {
        if (myIterable.valid()) {
          if (myIterable.getStart1() == 0 && myIterable.getStart2() == 0) {
            lastIndex1 = myIterable.getEnd1();
            lastIndex2 = myIterable.getEnd2();
            myIterable.next();
          }
        }
      }

      @Override
      public boolean hasNext() {
        return myIterable.valid() || (lastIndex1 != myLength1 || lastIndex2 != myLength2);
      }

      @Override
      public Range next() {
        if (myIterable.valid()) {
          assert (myIterable.getStart1() - lastIndex1 != 0) || (myIterable.getStart2() - lastIndex2 != 0);
          Range chunk = new Range(lastIndex1, myIterable.getStart1(), lastIndex2, myIterable.getStart2());

          lastIndex1 = myIterable.getEnd1();
          lastIndex2 = myIterable.getEnd2();

          myIterable.next();

          return chunk;
        }
        else {
          assert (myLength1 - lastIndex1 != 0) || (myLength2 - lastIndex2 != 0);
          Range chunk = new Range(lastIndex1, myLength1, lastIndex2, myLength2);

          lastIndex1 = myLength1;
          lastIndex2 = myLength2;

          return chunk;
        }
      }
    };
  }

  @NotNull
  protected abstract ChangeIterable createChangeIterable();

  protected interface ChangeIterable {
    boolean valid();

    void next();

    int getStart1();

    int getStart2();

    int getEnd1();

    int getEnd2();
  }
}
