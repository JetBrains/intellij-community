// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

final class StrippedIntOpenHashMap {
  /**
   * The array of keys.
   */
  private transient int[] key;
  private transient int[] value;
  /**
   * The mask for wrapping a position counter.
   */
  private transient int mask;
  /**
   * Whether this set contains the null key.
   */
  private transient boolean containsNull;
  private transient int nullValue;
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

  private StrippedIntOpenHashMap(int expected, float f) {
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
    value = new int[n + 1];
  }

  /**
   * Creates a new hash set with initial expected
   * {@link Hash#DEFAULT_INITIAL_SIZE} elements and
   * {@link Hash#DEFAULT_LOAD_FACTOR} as load factor.
   */
  StrippedIntOpenHashMap() {
    this(Hash.DEFAULT_INITIAL_SIZE, Hash.DEFAULT_LOAD_FACTOR);
  }

  private int realSize() {
    return containsNull ? size - 1 : size;
  }

  public void put(int k, int v) {
    assert v != 0;
    int pos;
    int curr;
    int[] key = this.key;
    if (k == 0) {
      nullValue = v;
      containsNull = true;
    }
    else {
      // The starting point.
      if (!((curr = key[pos = Hash.mix(k) & mask]) == 0)) {
        if (curr == k) {
          value[pos] = v;
          return;
        }
        while (!((curr = key[pos = pos + 1 & mask]) == 0)) {
          if (curr == k) {
            value[pos] = v;
            return;
          }
        }
      }
      key[pos] = k;
      value[pos] = v;
    }
    if (size++ >= maxFill) {
      rehash(Hash.arraySize(size + 1, f));
    }
    assert get(k, -1) == v && get(k, 0) == v;
  }

  public int size() {
    return size;
  }

  public boolean isEmpty() {
    return size == 0;
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
  private void rehash(int newN) {
    int[] key = this.key;
    int[] value = this.value;
    int mask = newN - 1; // Note that this is used by the hashing macro
    int[] newKey = new int[newN + 1];
    int[] newValue = new int[newN + 1];
    int i = n, pos;
    for (int j = realSize(); j-- != 0; ) {
      while (key[--i] == 0) ;
      if (!(newKey[pos = Hash.mix(key[i]) & mask] == 0)) {
        while (!(newKey[pos = pos + 1 & mask] == 0)) ;
      }
      newKey[pos] = key[i];
      newValue[pos] = value[i];
    }
    n = newN;
    this.mask = mask;
    maxFill = Hash.maxFill(n, f);
    this.key = newKey;
    this.value = newValue;
    for (i = 0; i < key.length; i++) {
      int k = key[i];
      int v = value[i];
      assert k==0 || get(k,-1)==v;
    }
  }

  public int get(int k, int absentValue) {
    if (k == 0) {
      return containsNull ? nullValue : absentValue;
    }
    int curr;
    final int[] key = this.key;
    int pos;
    // The starting point.
    if ((curr = value[pos = Hash.mix(k) & mask]) == 0) {
      return absentValue;
    }
    if (k == key[pos]) {
      return curr;
    }
    while (true) {
      if ((curr = value[pos = pos + 1 & mask]) == 0) {
        return absentValue;
      }
      if (k == key[pos]) {
        return curr;
      }
    }
  }
}