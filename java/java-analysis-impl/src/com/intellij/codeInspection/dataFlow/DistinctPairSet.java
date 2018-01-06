/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.dataFlow;

import gnu.trove.TLongArrayList;
import gnu.trove.TLongHashSet;
import gnu.trove.TLongIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;

final class DistinctPairSet extends AbstractSet<DistinctPairSet.DistinctPair> {
  private final DfaMemoryStateImpl myState;
  private final TLongHashSet myData;

  DistinctPairSet(DfaMemoryStateImpl state) {
    myState = state;
    myData = new TLongHashSet();
  }

  DistinctPairSet(DfaMemoryStateImpl state, DistinctPairSet other) {
    myData = new TLongHashSet(other.size());
    myState = state;
    other.myData.forEach(myData::add);
  }

  void add(int firstIndex, int secondIndex) {
    myData.add(createPair(firstIndex, secondIndex));
  }

  @Override
  public boolean contains(Object o) {
    if(!(o instanceof DistinctPair)) return false;
    DistinctPair dp = (DistinctPair)o;
    EqClass first = dp.getFirst();
    EqClass second = dp.getSecond();
    if(first.isEmpty() || second.isEmpty()) return false;
    int firstVal = first.get(0);
    int secondVal = second.get(0);
    int firstIndex = myState.getEqClassIndex(myState.getFactory().getValue(firstVal));
    int secondIndex = myState.getEqClassIndex(myState.getFactory().getValue(secondVal));
    if (firstIndex == -1 || secondIndex == -1) return false;
    long pair = createPair(firstIndex, secondIndex);
    return myData.contains(pair) && decode(pair).equals(dp);
  }

  @Override
  public Iterator<DistinctPair> iterator() {
    return new Iterator<DistinctPair>() {
      final TLongIterator iterator = myData.iterator();

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public DistinctPair next() {
        return decode(iterator.next());
      }

      @Override
      public void remove() {
        iterator.remove();
      }
    };
  }

  @Override
  public int size() {
    return myData.size();
  }

  /**
   * Merge c2Index class into c1Index
   *
   * @param c1Index index of resulting class
   * @param c2Index index of class which becomes equivalent to c1Index
   * @return true if merge is successful, false if classes were distinct
   */
  public boolean unite(int c1Index, int c2Index) {
    TLongArrayList c2Pairs = new TLongArrayList();
    long[] distincts = myData.toArray();
    for (long distinct : distincts) {
      int pc1 = low(distinct);
      int pc2 = high(distinct);
      boolean addedToC1 = false;

      if (pc1 == c1Index || pc2 == c1Index) {
        addedToC1 = true;
      }

      if (pc1 == c2Index || pc2 == c2Index) {
        if (addedToC1) return false;
        c2Pairs.add(distinct);
      }
    }

    for (int i = 0; i < c2Pairs.size(); i++) {
      long c = c2Pairs.get(i);
      myData.remove(c);
      myData.add(createPair(c1Index, low(c) == c2Index ? high(c) : low(c)));
    }
    return true;
  }

  public boolean areDistinct(int c1Index, int c2Index) {
    long pair = createPair(c1Index, c2Index);
    return myData.contains(pair);
  }

  private DistinctPair decode(long encoded) {
    return new DistinctPair(low(encoded), high(encoded), myState.getEqClasses());
  }

  private static long createPair(int i1, int i2) {
    if (i1 < i2) {
      long l = i1;
      l <<= 32;
      l += i2;
      return l;
    }
    else {
      long l = i2;
      l <<= 32;
      l += i1;
      return l;
    }
  }

  private static int low(long l) {
    return (int)l;
  }

  private static int high(long l) {
    return (int)((l & 0xFFFFFFFF00000000L) >> 32);
  }

  static final class DistinctPair {
    private final int myFirst;
    private final int mySecond;
    private List<EqClass> myList;

    private DistinctPair(int first, int second, List<EqClass> list) {
      myFirst = first;
      mySecond = second;
      myList = list;
    }

    @NotNull
    public EqClass getFirst() {
      return myList.get(myFirst);
    }

    @NotNull
    public EqClass getSecond() {
      return myList.get(mySecond);
    }

    public int getOtherClass(int eqClassIndex) {
      if(myFirst == eqClassIndex) {
        return mySecond;
      }
      if(mySecond == eqClassIndex) {
        return myFirst;
      }
      return -1;
    }

    @Nullable
    EqClass getOtherClass(EqClass eqClass) {
      if (getFirst() == eqClass) {
        return getSecond();
      }
      if (getSecond() == eqClass) {
        return getFirst();
      }
      return null;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (!(obj instanceof DistinctPair)) return false;
      DistinctPair that = (DistinctPair)obj;
      return that.getFirst().equals(this.getFirst()) && that.getSecond().equals(this.getSecond()) ||
             that.getSecond().equals(this.getFirst()) && that.getFirst().equals(this.getSecond());
    }

    @Override
    public int hashCode() {
      return getFirst().hashCode() + getSecond().hashCode();
    }

    @Override
    public String toString() {
      return "{" + myFirst + ", " + mySecond + "}";
    }
  }
}
