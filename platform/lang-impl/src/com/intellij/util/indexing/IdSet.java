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
package com.intellij.util.indexing;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;

import java.util.BitSet;

/**
* @author peter
*/
class IdSet {
  /**
   * Where BitSet will take less space than TIntHashSet if file ids are not greater than 1 million
   */
  private static final int ourToBitThreshold = 20000;
  private static final int ourToSetThreshold = 18000;
  private Object mySet;
  private int mySize;

  private IdSet() {
  }

  IdSet(final int initialCapacity) {
    mySet = initialCapacity > ourToBitThreshold ? new BitSet() : createIntSet(initialCapacity);
  }

  private static TIntHashSet createIntSet(final int initialCapacity) {
    return new TIntHashSet(initialCapacity, 0.98f) {
      @Override
      public void compact() {
        if (((int)(capacity() * _loadFactor)/ Math.max(1, size())) >= 3) {
          super.compact();
        }
      }
    };
  }

  void ensureCapacity(int desiredCapacity) {
    if (mySet instanceof TIntHashSet) {
      if (desiredCapacity > ourToBitThreshold) {
        mySet = convertToBitSet();
      } else {
        ((TIntHashSet)mySet).ensureCapacity(desiredCapacity);
      }
    }
  }

  boolean add(int val) {
    assert val >= 0;
    if (mySet instanceof TIntHashSet) {
      TIntHashSet intSet = (TIntHashSet)mySet;
      if (intSet.contains(val)) return false;
      
      intSet.add(val);
      if (intSet.size() > ourToBitThreshold) {
        mySet = convertToBitSet();
      }
    } else {
      if (((BitSet)mySet).get(val)) return false;
      
      ((BitSet)mySet).set(val);
    }

    mySize++;
    return true;
  }

  private BitSet convertToBitSet() {
    BitSet bitSet = new BitSet();
    ValueContainer.IntIterator iterator = createIterator();
    while (iterator.hasNext()) {
      bitSet.set(iterator.next());
    }
    return bitSet;
  }

  boolean remove(int val) {
    assert val >= 0;
    if (mySet instanceof BitSet) {
      BitSet bitSet = (BitSet)mySet;
      if (!bitSet.get(val)) {
        return false;
      }
      
      mySize--;
      bitSet.clear(val);
      if (mySize < ourToSetThreshold) {
        mySet = convertToHashSet();
      }
      return true;
    }

    if (((TIntHashSet)mySet).remove(val)) {
      mySize--;
      return true;
    }
    return false;
  }

  private TIntHashSet convertToHashSet() {
    TIntHashSet intSet = new TIntHashSet(mySize);
    ValueContainer.IntIterator iterator = createIterator();
    while (iterator.hasNext()) {
      intSet.add(iterator.next());
    }
    return intSet;
  }

  void compact() {
    if (mySet instanceof TIntHashSet) {
      ((TIntHashSet)mySet).compact();
    }
  }

  boolean contains(int val) {
    if (mySet instanceof TIntHashSet) {
      return ((TIntHashSet)mySet).contains(val);
    }
    return ((BitSet)mySet).get(val);
  }

  @Override
  protected IdSet clone() {
    IdSet copy = new IdSet();
    copy.mySet = mySet instanceof TIntHashSet ? ((TIntHashSet)mySet).clone() : ((BitSet)mySet).clone();
    copy.mySize = mySize;
    return copy;
  }

  ValueContainer.IntIterator createIterator() {
    if (mySet instanceof TIntHashSet) {
      return new IntSetIterator((TIntHashSet)mySet);
    }
    return new BitSetIterator((BitSet)mySet);
  }

  boolean isEmpty() {
    return mySize == 0;
  }

  private static class IntSetIterator implements ValueContainer.IntIterator {
    private final TIntIterator mySetIterator;
    private final int mySize;

    public IntSetIterator(final TIntHashSet set) {
      mySetIterator = set.iterator();
      mySize = set.size();
    }

    @Override
    public boolean hasNext() {
      return mySetIterator.hasNext();
    }

    @Override
    public int next() {
      return mySetIterator.next();
    }

    @Override
    public int size() {
      return mySize;
    }
  }

  private class BitSetIterator implements ValueContainer.IntIterator {
    static final int notCalculated = -2;
    static final int notFound = -1;
    private final BitSet myBitSet;
    private int currentBit = -1;
    private int nextBit = notCalculated;

    public BitSetIterator(BitSet bitSet) {
      myBitSet = bitSet;
    }

    @Override
    public boolean hasNext() {
      return findNextBit() != notFound;
    }

    private int findNextBit() {
      if (nextBit == notCalculated) {
        nextBit = myBitSet.nextSetBit(currentBit + 1);
      }
      return nextBit;
    }

    @Override
    public int next() {
      currentBit = findNextBit();
      nextBit = currentBit > 0 ? notCalculated : notFound;
      return currentBit;
    }

    @Override
    public int size() {
      return mySize;
    }
  }
}
