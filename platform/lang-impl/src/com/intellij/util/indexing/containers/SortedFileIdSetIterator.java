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

import com.intellij.openapi.util.ThreadLocalCachedIntArray;
import com.intellij.util.indexing.ValueContainer;

/**
* Created by Maxim.Mossienko on 6/12/2014.
*/
public class SortedFileIdSetIterator implements ValueContainer.IntIterator {
  private final int[] myBits;
  private final int myBitsLength;
  private final int myOffset;
  private int myPosition;
  private final int mySize;

  private SortedFileIdSetIterator(int[] bits, int bitsLength, int offset, int size) {
    myBits = bits;
    myBitsLength = bitsLength;
    myOffset = offset;
    myPosition = nextSetBit(0, myBits, myBitsLength);
    mySize = size;
  }

  @Override
  public boolean hasNext() {
    return myPosition != -1;
  }

  @Override
  public int next() {
    int next = myPosition + myOffset;
    myPosition = nextSetBit(myPosition + 1, myBits, myBitsLength);
    return next;
  }

  @Override
  public int size() {
    return mySize;
  }

  @Override
  public boolean hasAscendingOrder() {
    return true;
  }

  @Override
  public ValueContainer.IntIterator createCopyInInitialState() {
    return new SortedFileIdSetIterator(myBits, myBitsLength, myOffset, mySize);
  }

  public static ValueContainer.IntIterator getTransientIterator(ValueContainer.IntIterator intIterator) {
    final ValueContainer.IntIterator intIteratorCloned = intIterator.createCopyInInitialState();
    int max = 0, min = Integer.MAX_VALUE;

    while(intIterator.hasNext()) {
      int nextInt = intIterator.next();
      max = Math.max(max, nextInt);
      min = Math.min(min, nextInt);
    }

    assert min > 0;

    final int offset = (min >> INT_BITS_SHIFT) << INT_BITS_SHIFT;
    final int bitsLength = ((max - offset) >> INT_BITS_SHIFT) + 1;
    final int[] bits = ourSpareBuffer.getBuffer(bitsLength);
    for(int i = 0; i < bitsLength; ++i) bits[i] = 0;

    intIterator = intIteratorCloned;
    int size = 0;
    while(intIterator.hasNext()) {
      final int id = intIterator.next() - offset;
      int mask = 1 << id;
      if ((bits[id >> INT_BITS_SHIFT] & mask) == 0) {
        bits[id >> INT_BITS_SHIFT] |= mask;
        ++size;
      }
    }

    return new SortedFileIdSetIterator(bits, bitsLength, offset, size);
  }

  private static final ThreadLocalCachedIntArray ourSpareBuffer = new ThreadLocalCachedIntArray();

  private static final int INT_BITS_SHIFT = 5;
  private static int nextSetBit(int bitIndex, int[] bits, int bitsLength) {
    int wordIndex = bitIndex >> INT_BITS_SHIFT;
    if (wordIndex >= bitsLength) {
      return -1;
    }

    int word = bits[wordIndex] & (-1 << bitIndex);

    while (true) {
      if (word != 0) {
        return (wordIndex << INT_BITS_SHIFT) + Long.numberOfTrailingZeros(word);
      }
      if (++wordIndex == bitsLength) {
        return -1;
      }
      word = bits[wordIndex];
    }
  }

}
