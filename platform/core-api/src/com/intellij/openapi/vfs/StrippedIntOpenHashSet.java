/*
 * Copyright (C) 2002-2020 Sebastiano Vigna
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs;

import java.util.ArrayList;
import java.util.NoSuchElementException;

final class StrippedIntOpenHashSet {
  /**
   * The array of keys.
   */
  private transient int[] key;
  /**
   * The mask for wrapping a position counter.
   */
  private transient int mask;
  /**
   * Whether this set contains the null key.
   */
  private transient boolean containsNull;
  /**
   * The current table size. Note that an additional element is allocated for
   * storing the null key.
   */
  private transient int n;
  /**
   * Threshold after which we rehash. It must be the table size times {@link #f}.
   */
  private transient int maxFill;
  /**
   * We never resize below this threshold, which is the construction-time {#n}.
   */
  private final transient int minN;
  /**
   * Number of entries in the set (including the null key, if present).
   */
  private int size;
  /**
   * The acceptable load factor.
   */
  private final float f;

  /**
   * Creates a new hash set.
   *
   * <p>
   * The actual table size will be the least power of two greater than
   * {@code expected}/{@code f}.
   *
   * @param expected the expected number of elements in the hash set.
   * @param f        the load factor.
   */

  private StrippedIntOpenHashSet(final int expected, final float f) {
    if (f <= 0 || f > 1) {
      throw new IllegalArgumentException("Load factor must be greater than 0 and smaller than or equal to 1");
    }
    if (expected < 0) {
      throw new IllegalArgumentException("The expected number of elements must be nonnegative");
    }
    this.f = f;
    minN = n = Hash.arraySize(expected, f);
    mask = n - 1;
    maxFill = Hash.maxFill(n, f);
    key = new int[n + 1];
  }

  /**
   * Creates a new hash set with {@link Hash#DEFAULT_LOAD_FACTOR} as load factor.
   *
   * @param expected the expected number of elements in the hash set.
   */
  StrippedIntOpenHashSet(final int expected) {
    this(expected, Hash.DEFAULT_LOAD_FACTOR);
  }

  /**
   * Creates a new hash set with initial expected
   * {@link Hash#DEFAULT_INITIAL_SIZE} elements and
   * {@link Hash#DEFAULT_LOAD_FACTOR} as load factor.
   */
  StrippedIntOpenHashSet() {
    this(Hash.DEFAULT_INITIAL_SIZE, Hash.DEFAULT_LOAD_FACTOR);
  }

  private int realSize() {
    return containsNull ? size - 1 : size;
  }

  public boolean add(final int k) {
    int pos;
    if (k == 0) {
      if (containsNull) {
        return false;
      }
      containsNull = true;
    }
    else {
      int curr;
      final int[] key = this.key;
      // The starting point.
      if (!((curr = key[pos = Hash.mix(k) & mask]) == 0)) {
        if (curr == k) {
          return false;
        }
        while (!((curr = key[pos = pos + 1 & mask]) == 0)) {
          if (curr == k) {
            return false;
          }
        }
      }
      key[pos] = k;
    }
    if (size++ >= maxFill) {
      rehash(Hash.arraySize(size + 1, f));
    }
    assert contains(k);
    return true;
  }

  /**
   * Shifts left entries with the specified hash code, starting at the specified
   * position, and empties the resulting free entry.
   *
   * @param pos a starting position.
   */
  private void shiftKeys(int pos) {
    // Shift entries with the same hash.
    int last;
    int slot;
    int curr;
    final int[] key = this.key;
    for (; ; ) {
      pos = (last = pos) + 1 & mask;
      for (; ; ) {
        if ((curr = key[pos]) == 0) {
          key[last] = 0;
          return;
        }
        slot = Hash.mix(curr) & mask;
        if (last <= pos ? last >= slot || slot > pos : last >= slot && slot > pos) {
          break;
        }
        pos = pos + 1 & mask;
      }
      key[last] = curr;
    }
  }

  private boolean removeEntry(final int pos) {
    size--;
    shiftKeys(pos);
    if (n > minN && size < maxFill / 4 && n > Hash.DEFAULT_INITIAL_SIZE) {
      rehash(n / 2);
    }
    return true;
  }

  private boolean removeNullEntry() {
    containsNull = false;
    key[n] = 0;
    size--;
    if (n > minN && size < maxFill / 4 && n > Hash.DEFAULT_INITIAL_SIZE) {
      rehash(n / 2);
    }
    return true;
  }

  public boolean remove(final int k) {
    try {
      if (k == 0) {
        if (containsNull) {
          return removeNullEntry();
        }
        return false;
      }
      int curr;
      final int[] key = this.key;
      int pos;
      // The starting point.
      if ((curr = key[pos = Hash.mix(k) & mask]) == 0) {
        return false;
      }
      if (k == curr) {
        return removeEntry(pos);
      }
      while (true) {
        if ((curr = key[pos = pos + 1 & mask]) == 0) {
          return false;
        }
        if (k == curr) {
          return removeEntry(pos);
        }
      }
    }
    finally {
      assert !contains(k);
    }
  }

  public boolean contains(final int k) {
    if (k == 0) {
      return containsNull;
    }
    int curr;
    final int[] key = this.key;
    int pos;
    // The starting point.
    if ((curr = key[pos = Hash.mix(k) & mask]) == 0) {
      return false;
    }
    if (k == curr) {
      return true;
    }
    while (true) {
      if ((curr = key[pos = pos + 1 & mask]) == 0) {
        return false;
      }
      if (k == curr) {
        return true;
      }
    }
  }

  public int size() {
    return size;
  }

  public boolean isEmpty() {
    return size == 0;
  }

  public int[] toArray() {
    int[] result = new int[size];
    SetIterator iterator = iterator();
    int i = 0;
    while (iterator.hasNext()) {
      result[i++] = iterator.nextInt();
    }
    return result;
  }

  /**
   * An iterator over a hash set.
   */
  public final class SetIterator {
    /**
     * The index of the last entry returned, if positive or zero; initially,
     * {@link #n}
     */
    int pos = n;

    /**
     * A downward counter measuring how many entries must still be returned.
     */
    int last = -1;
    /**
     * A downward counter measuring how many entries must still be returned.
     */
    int c = size;
    /**
     * A boolean telling us whether we should return the null key.
     */
    boolean mustReturnNull = containsNull;
    /**
     * A lazily allocated list containing elements that have wrapped around the
     * table because of removals.
     */
    ArrayList<Integer> wrapped;

    public boolean hasNext() {
      return c != 0;
    }

    public int nextInt() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      c--;
      if (mustReturnNull) {
        mustReturnNull = false;
        last = n;
        return key[n];
      }
      final int key[] = StrippedIntOpenHashSet.this.key;
      for (; ; ) {
        if (--pos < 0) {
          // We are just enumerating elements from the wrapped list.
          last = Integer.MIN_VALUE;
          return wrapped.get(-pos - 1);
        }
        if (!((key[pos]) == (0))) {
          return key[last = pos];
        }
      }
    }

    private void shiftKeys(int pos) {
      // Shift entries with the same hash.
      int last, slot;
      int curr;
      final int[] key = StrippedIntOpenHashSet.this.key;
      for (; ; ) {
        pos = ((last = pos) + 1) & mask;
        for (; ; ) {
          if (((curr = key[pos]) == (0))) {
            key[last] = (0);
            return;
          }
          slot = (Hash.mix((curr))) & mask;
          if (last <= pos ? last >= slot || slot > pos : last >= slot && slot > pos) {
            break;
          }
          pos = (pos + 1) & mask;
        }
        if (pos < last) { // Wrapped entry.
          if (wrapped == null) {
            wrapped = new ArrayList<>(2);
          }
          wrapped.add(key[pos]);
        }
        key[last] = curr;
      }
    }

    public void remove() {
      if (last == -1) {
        throw new IllegalStateException();
      }
      if (last == n) {
        StrippedIntOpenHashSet.this.containsNull = false;
        StrippedIntOpenHashSet.this.key[n] = (0);
      }
      else if (pos >= 0) {
        shiftKeys(last);
      }
      else {
        // We're removing wrapped entries.
        StrippedIntOpenHashSet.this.remove(wrapped.get(-pos - 1));
        last = -1; // Note that we must not decrement size
        return;
      }
      size--;
      last = -1; // You can no longer remove this entry.
    }
  }

  public SetIterator iterator() {
    return new SetIterator();
  }

  /**
   * Rehashes the set.
   *
   * <p>
   * This method implements the basic rehashing strategy, and may be overriden by
   * subclasses implementing different rehashing strategies (e.g., disk-based
   * rehashing). However, you should not override this method unless you
   * understand the internal workings of this class.
   *
   * @param newN the new size
   */
  private void rehash(final int newN) {
    final int[] key = this.key;
    final int mask = newN - 1; // Note that this is used by the hashing macro
    final int[] newKey = new int[newN + 1];
    int i = n;
    int pos;
    for (int j = realSize(); j-- != 0; ) {
      while (key[--i] == 0) ;
      if (!(newKey[pos = Hash.mix(key[i]) & mask] == 0)) {
        while (!(newKey[pos = pos + 1 & mask] == 0)) ;
      }
      newKey[pos] = key[i];
    }
    n = newN;
    this.mask = mask;
    maxFill = Hash.maxFill(n, f);
    this.key = newKey;

    for (int k : key) {
      if (k != 0) {
        assert contains(k);
      }
    }
  }
}
