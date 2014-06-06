/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.indexing.containers;

import com.intellij.util.indexing.ValueContainer;
import gnu.trove.TIntProcedure;

import java.util.Arrays;

/**
 * Class buffers changes in 2 modes:
 * - Accumulating up to MAX_FILES changes appending them *sequentially* to changes array
 * - Adding changes to randomAccessContainer once it is available: later happens if we accumulated many changes or external client queried
 * state of the changes: asked for predicate, iterator, isEmpty, etc. We are trying hard to delay transformation of state upon 2nd reason for
 * performance reasons.
 * It is assumed that add / remove operations as well as read only operations are externally synchronized, the only synchronization is
 * performed upon transforming changes array into randomAccessContainer because it can be done during read only operations in several threads
 */
public class ChangeBufferingList implements Cloneable {
  static final int MAX_FILES = 20000; // less than Short.MAX_VALUE
  //static final int MAX_FILES = 100;
  private volatile int[] changes;
  private short length;
  private short removals;
  private volatile RandomAccessIntContainer randomAccessContainer;

  private static final boolean DEBUG = false;
  //private static final boolean DEBUG = false;
  private IdSet checkSet;

  public ChangeBufferingList() { this(3); }
  public ChangeBufferingList(int length) {
    if (length > MAX_FILES) {
      randomAccessContainer = new IdBitSet(length);
    } else {
      changes = new int[length];
    }
    checkSet = DEBUG ? new IdSet(length) : null;
  }

  static int[] calcMinMax(int[] set, int length) {
    int max = Integer.MIN_VALUE;
    int min = Integer.MAX_VALUE;
    for(int i = 0; i < length; ++i) {
      max = Math.max(max, set[i]);
      min = Math.min(min, set[i]);
    }
    return new int[] {min, max};
  }

  public void add(int value) {
    ensureCapacity(1);
    if (DEBUG) checkSet.add(value);
    RandomAccessIntContainer intContainer = randomAccessContainer;
    if (intContainer == null) {
      addChange(value);
    } else {
      intContainer.add(value);
    }
  }

  private void addChange(int value) {
    changes[length++] = value;
    if (value < 0) ++removals;
  }

  public void remove(int value) {
    if (DEBUG) checkSet.remove(value);
    RandomAccessIntContainer intContainer = randomAccessContainer;
    if (intContainer == null) {
      ensureCapacity(1);
      addChange(-value);
    }
    else {
      boolean removed = intContainer.remove(value);
      if (removed) intContainer.compact();
    }
  }

  @Override
  public Object clone() {
    try {
      ChangeBufferingList clone = (ChangeBufferingList)super.clone();
      if (changes != null) clone.changes = changes.clone();
      if (randomAccessContainer != null) {
        clone.randomAccessContainer = (RandomAccessIntContainer)randomAccessContainer.clone();
      }
      if (checkSet != null) clone.checkSet = (IdSet)checkSet.clone();
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  private RandomAccessIntContainer getRandomAccessContainer() {
    int[] currentChanges = changes;
    if (currentChanges == null) return randomAccessContainer;

    synchronized (currentChanges) {
      currentChanges = changes;
      if (currentChanges == null) return randomAccessContainer;
      boolean copyChanges = true;
      RandomAccessIntContainer idSet;

      if (randomAccessContainer == null) {
        int someElementsNumberEstimation = length - removals;
        int[] minMax = calcMinMax(changes, length);

        // todo we can check these lengths instead of only relying upon reaching MAX_FILES
        int lengthOfBitSet = IdBitSet.sizeInBytes(minMax[1], minMax[0]);
        int lengthOfIntSet = 4 * length;

        if (someElementsNumberEstimation < MAX_FILES) {
          if (removals == 0) {
            Arrays.sort(currentChanges, 0, length);
            idSet = new SortedIdSet(currentChanges, length);
            copyChanges = false;
          } else {
            idSet = new SortedIdSet(Math.max(someElementsNumberEstimation, 3));
          }
        }
        else if (removals == 0) {
          if (lengthOfBitSet > lengthOfIntSet) {
            int a = 1;
          }
          idSet = new IdBitSet(changes, length, 0);
          copyChanges = false;
        } else {
          idSet = new IdBitSet(minMax, 0);
        }
      } else if (DEBUG) {
        idSet = (RandomAccessIntContainer)randomAccessContainer.clone();
      } else {
        idSet = randomAccessContainer;
      }

      assert idSet != null;

      if (copyChanges) {
        for(int i = 0, len = length; i < len; ++i) {
          int id = currentChanges[i];
          if (id > 0) {
            idSet.add(id);
          } else {
            idSet.remove(-id);
          }
        }
      }

      if (DEBUG) {
        if(checkSet.size() != idSet.size()) {
          int a = 1; assert false;
        }
        final RandomAccessIntContainer finalIdSet = idSet;
        checkSet.forEach(new TIntProcedure() {
          @Override
          public boolean execute(int value) {
            if (!finalIdSet.contains(value)) {
              int a = 1; assert false;
            }
            return true;
          }
        });
      }

      length = 0;
      removals = 0;
      randomAccessContainer = idSet;
      changes = null;
      return randomAccessContainer;
    }
  }

  public void ensureCapacity(int diff) {
    RandomAccessIntContainer intContainer = randomAccessContainer;
    if (length == MAX_FILES) {
      intContainer = getRandomAccessContainer(); // transform into more compact storage
    }
    if (intContainer != null) {
      randomAccessContainer = intContainer.ensureContainerCapacity(diff);
      return;
    }
    if (changes == null) {
      changes = new int[Math.max(3, diff)];
    } else if (length + diff > changes.length) {
      int[] newChanges = new int[calcNextArraySize(changes.length, length + diff)];
      System.arraycopy(changes, 0, newChanges, 0, length);
      changes = newChanges;
    }
  }

  static int calcNextArraySize(int currentSize, int wantedSize) {
    return Math.min(
          Math.max(currentSize < 1024 ? currentSize << 1 : currentSize + currentSize / 5, wantedSize),
          MAX_FILES
        );
  }

  public boolean isEmpty() {
    if (randomAccessContainer == null) {
      if (changes == null) return true;
      if (removals == 0) return length == 0;
    }
    // todo we can calculate isEmpty in more cases (without container)
    RandomAccessIntContainer intContainer = getRandomAccessContainer();
    return intContainer.size() == 0;
  }

  public ValueContainer.IntPredicate intPredicate() {
    final ValueContainer.IntPredicate predicate = getRandomAccessContainer().intPredicate();
    if (DEBUG) {
      return new ValueContainer.IntPredicate() {
        @Override
        public boolean contains(int id) {
          boolean answer = predicate.contains(id);
          if (answer != checkSet.contains(id)) {
            int a = 1; assert false;
          }
          return answer;
        }
      };
    }
    return predicate;
  }

  public ValueContainer.IntIterator intIterator() {
    RandomAccessIntContainer intContainer = randomAccessContainer;
    if (intContainer == null && removals == 0) {
      return new ValueContainer.IntIterator() {
        int cursor;
        @Override
        public boolean hasNext() {
          return cursor < length;
        }

        @Override
        public int next() {
          int current = cursor;
          ++cursor;
          return changes[current];
        }

        @Override
        public int size() {
          return length;
        }

        @Override
        public boolean hasAscendingOrder() {
          return false;
        }
      };
    }
    return getRandomAccessContainer().intIterator();
  }

  public IdSet getCheckSet() {
    return checkSet;
  }
}
