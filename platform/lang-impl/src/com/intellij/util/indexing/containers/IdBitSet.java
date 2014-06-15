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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.indexing.ValueContainer;

/**
* Created by Maxim.Mossienko on 5/27/2014.
*/
class IdBitSet implements Cloneable, RandomAccessIntContainer {
  private static final int SHIFT = 6;
  private static final int BITS_PER_WORD = 1 << SHIFT;
  private static final int MASK = BITS_PER_WORD - 1;
  private long[] myBitMask;
  private int myBitsSet;
  private int myLastUsedSlot;
  private int myBase = -1;

  public IdBitSet(int capacity) {
    myBitMask = new long[(calcCapacity(capacity) >> SHIFT) + 1];
  }

  public IdBitSet(int[] set, int count, int additional) {
    this(ChangeBufferingList.calcMinMax(set, count), additional);
    for(int i = 0; i < count; ++i) add(set[i]);
  }

  public IdBitSet(RandomAccessIntContainer set, int additionalCount) {
    this(calcMax(set), additionalCount);
    ValueContainer.IntIterator iterator = set.intIterator();
    while(iterator.hasNext()) {
      add(iterator.next());
    }
  }

  private static int[] calcMax(RandomAccessIntContainer set) {
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    ValueContainer.IntIterator iterator = set.intIterator();
    while(iterator.hasNext()) {
      int next = iterator.next();
      min = Math.min(min, next);
      max = Math.max(max, next);
    }

    return new int[] {min, max};
  }

  IdBitSet(int[] minMax, int additionalCount) {
    int min = minMax[0];
    int base = roundToNearest(min);
    myBase = base;
    myBitMask = new long[((calcCapacity(minMax[1] - base) + additionalCount) >> SHIFT) + 1];
  }

  static int roundToNearest(int min) {
    return (min >> SHIFT) << SHIFT;
  }

  public boolean add(int bitIndex) {
    boolean set = contains(bitIndex);
    if (!set) {
      if (myBase < 0) {
        myBase = roundToNearest(bitIndex);
      } else if (bitIndex < myBase) {
        int newBase = roundToNearest(bitIndex);
        int wordDiff = (myBase - newBase) >> SHIFT;
        long[] n = new long[wordDiff + myBitMask.length];
        System.arraycopy(myBitMask, 0, n, wordDiff, myBitMask.length);
        myBitMask = n;
        myBase = newBase;
        myLastUsedSlot += wordDiff;
      }
      ++myBitsSet;
      bitIndex -= myBase;
      int wordIndex = bitIndex >> SHIFT;
      if (wordIndex >= myBitMask.length) {
        long[] n = new long[Math.max(calcCapacity(myBitMask.length), wordIndex + 1)];
        System.arraycopy(myBitMask, 0, n, 0, myBitMask.length);
        myBitMask = n;
      }
      myBitMask[wordIndex] |= 1L << (bitIndex & MASK);
      myLastUsedSlot = Math.max(myLastUsedSlot, wordIndex);
    }
    return !set;
  }

  private static int calcCapacity(int length) {
    return length + 3 * (length / 5);
  }

  public int size() {
    return myBitsSet;
  }

  public boolean remove(int bitIndex) {
    if (bitIndex < myBase || myBase < 0) return false;
    if (!contains(bitIndex)) return false;
    --myBitsSet;
    bitIndex -= myBase;
    int wordIndex = bitIndex >> SHIFT;
    myBitMask[wordIndex] &= ~(1L << (bitIndex & MASK));
    if (wordIndex == myLastUsedSlot) {
      while(myLastUsedSlot >= 0 && myBitMask[myLastUsedSlot] == 0) --myLastUsedSlot;
    }
    return true;
  }

  @Override
  public ValueContainer.IntIterator intIterator() {
    return new Iterator();
  }

  @Override
  public ValueContainer.IntPredicate intPredicate() {
    return new ValueContainer.IntPredicate() {
      @Override
      public boolean contains(int id) {
        return IdBitSet.this.contains(id);
      }
    };
  }

  @Override
  public void compact() {}

  public boolean contains(int bitIndex) {
    if (bitIndex < myBase || myBase < 0) return false;
    bitIndex -= myBase;
    int wordIndex = bitIndex >> SHIFT;
    boolean result = false;
    if (wordIndex < myBitMask.length) {
      result = (myBitMask[wordIndex] & (1L << (bitIndex & MASK))) != 0;
    }

    return result;
  }

  @Override
  public RandomAccessIntContainer ensureContainerCapacity(int diff) {
    return this; // todo
  }

  public IdBitSet clone() {
    try {
      IdBitSet clone = (IdBitSet)super.clone();
      if (myBitMask.length != myLastUsedSlot + 1) { // trim to size
        long[] longs = new long[myLastUsedSlot + 1];
        System.arraycopy(myBitMask, 0, longs, 0, longs.length);
        myBitMask = longs;
      }
      clone.myBitMask = myBitMask.clone();
      return clone;
    } catch (CloneNotSupportedException ex) {
      Logger.getInstance(getClass().getName()).error(ex);
      return null;
    }
  }

  private int nextSetBit(int bitIndex) {
    assert myBase >= 0;
    if (bitIndex >= myBase) bitIndex -= myBase;
    int wordIndex = bitIndex >> SHIFT;
    if (wordIndex > myLastUsedSlot) {
      return -1;
    }

    long word = myBitMask[wordIndex] & (-1L << bitIndex);

    while (true) {
      if (word != 0) {
        return (wordIndex * BITS_PER_WORD) + Long.numberOfTrailingZeros(word) + myBase;
      }
      if (++wordIndex > myLastUsedSlot) {
        return -1;
      }
      word = myBitMask[wordIndex];
    }
  }

  public static int sizeInBytes(int max, int min) {
    return calcCapacity(((roundToNearest(max) - roundToNearest(min)) >> SHIFT) + 1) * 8;
  }

  private class Iterator implements ValueContainer.IntIterator {
    private int nextSetBit = nextSetBit(0);

    @Override
    public boolean hasNext() {
      return nextSetBit != -1;
    }

    @Override
    public int next() {
      int setBit = nextSetBit;
      nextSetBit = nextSetBit(setBit + 1);
      return setBit;
    }

    @Override
    public int size() {
      return IdBitSet.this.size();
    }

    @Override
    public boolean hasAscendingOrder() {
      return true;
    }

    @Override
    public ValueContainer.IntIterator createCopyInInitialState() {
      return new Iterator();
    }
  }
}
