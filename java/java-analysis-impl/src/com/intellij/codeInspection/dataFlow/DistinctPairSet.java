/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.RelationType;
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

  boolean addOrdered(int firstIndex, int secondIndex) {
    TLongHashSet toAdd = new TLongHashSet();
    toAdd.add(createPair(firstIndex, secondIndex, true));
    for(DistinctPair pair : this) {
      if (!pair.isOrdered()) continue;
      if (pair.myFirst == secondIndex) {
        if (pair.mySecond == firstIndex || myData.contains(createPair(pair.mySecond, firstIndex, true))) return false;
        toAdd.add(createPair(firstIndex, pair.mySecond, true));
      } else if (pair.mySecond == firstIndex) {
        if (myData.contains(createPair(secondIndex, pair.myFirst, true))) return false;
        toAdd.add(createPair(pair.myFirst, secondIndex, true));
      }
    }
    myData.addAll(toAdd.toArray());
    return true;
  }

  void addUnordered(int firstIndex, int secondIndex) {
    if (!myData.contains(createPair(firstIndex, secondIndex, true)) &&
        !myData.contains(createPair(secondIndex, firstIndex, true))) {
      myData.add(createPair(firstIndex, secondIndex, false));
    }
  }

  @Override
  public boolean remove(Object o) {
    if (o instanceof DistinctPair) {
      DistinctPair dp = (DistinctPair)o;
      return myData.remove(createPair(dp.myFirst, dp.mySecond, dp.myOrdered));
    }
    return false;
  }

  @Override
  public boolean contains(Object o) {
    if (!(o instanceof DistinctPair)) return false;
    DistinctPair dp = (DistinctPair)o;
    EqClass first = dp.getFirst();
    EqClass second = dp.getSecond();
    if (first.isEmpty() || second.isEmpty()) return false;
    int firstVal = first.get(0);
    int secondVal = second.get(0);
    int firstIndex = myState.getEqClassIndex(myState.getFactory().getValue(firstVal));
    if (firstIndex == -1) return false;
    int secondIndex = myState.getEqClassIndex(myState.getFactory().getValue(secondVal));
    if (secondIndex == -1) return false;
    long pair = createPair(firstIndex, secondIndex, dp.isOrdered());
    return myData.contains(pair) && decode(pair).equals(dp);
  }

  @Override
  public Iterator<DistinctPair> iterator() {
    return new Iterator<>() {
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
        if (distinct < 0) {
          if (pc1 == c1Index && myData.contains(createPair(pc2, c2Index, true)) ||
              pc2 == c1Index && myData.contains(createPair(c2Index, pc1, true))) {
            return false;
          }
        }
      }

      if (pc1 == c2Index || pc2 == c2Index) {
        if (addedToC1) return false;
        c2Pairs.add(distinct);
      }
    }

    for (int i = 0; i < c2Pairs.size(); i++) {
      long c = c2Pairs.get(i);
      myData.remove(c);
      if (c >= 0) {
        myData.add(createPair(c1Index, low(c) == c2Index ? high(c) : low(c), false));
      }
      else if (low(c) == c2Index) {
        myData.add(createPair(c1Index, high(c), true));
      }
      else {
        myData.add(createPair(low(c), c1Index, true));
      }
    }
    return true;
  }

  public void splitClass(int index, int[] splitIndices) {
    TLongArrayList toAdd = new TLongArrayList();
    for(TLongIterator iterator = myData.iterator(); iterator.hasNext(); ) {
      DistinctPair pair = decode(iterator.next());
      if (pair.myFirst == index) {
        for (int splitIndex : splitIndices) {
          toAdd.add(createPair(splitIndex, pair.mySecond, pair.isOrdered()));
        }
        iterator.remove();
      } else if (pair.mySecond == index) {
        for (int splitIndex : splitIndices) {
          toAdd.add(createPair(pair.myFirst, splitIndex, pair.isOrdered()));
        }
        iterator.remove();
      }
    }
    myData.addAll(toAdd.toNativeArray());
  }

  public boolean areDistinctUnordered(int c1Index, int c2Index) {
    return myData.contains(createPair(c1Index, c2Index, false));
  }

  @Nullable
  RelationType getRelation(int c1Index, int c2Index) {
    if (areDistinctUnordered(c1Index, c2Index)) {
      return RelationType.NE;
    }
    if (myData.contains(createPair(c1Index, c2Index, true))) {
      return RelationType.LT;
    }
    if (myData.contains(createPair(c2Index, c1Index, true))) {
      return RelationType.GT;
    }
    return null;
  }

  private DistinctPair decode(long encoded) {
    boolean ordered = encoded < 0;
    encoded = Math.abs(encoded);
    return new DistinctPair(low(encoded), high(encoded), ordered, myState.getEqClasses());
  }

  public void dropOrder(DistinctPair pair) {
    if (remove(pair)) {
      addUnordered(pair.myFirst, pair.mySecond);
    }
  }

  private static long createPair(int low, int high, boolean ordered) {
    if (ordered) {
      return -(((long)high << 32) + low);
    }
    return low < high ? ((long)low << 32) + high : ((long)high << 32) + low;
  }

  private static int low(long l) {
    return (int)(Math.abs(l));
  }

  private static int high(long l) {
    return (int)((Math.abs(l) & 0xFFFFFFFF00000000L) >> 32);
  }

  static final class DistinctPair {
    private final int myFirst;
    private final int mySecond;
    private final boolean myOrdered;
    private final List<EqClass> myList;

    private DistinctPair(int first, int second, boolean ordered, List<EqClass> list) {
      myFirst = first;
      mySecond = second;
      myOrdered = ordered;
      myList = list;
    }

    public @NotNull EqClass getFirst() {
      return myList.get(myFirst);
    }

    public int getFirstIndex() {
      return myFirst;
    }

    public @NotNull EqClass getSecond() {
      return myList.get(mySecond);
    }

    public int getSecondIndex() {
      return mySecond;
    }

    public void check() {
      if (myList.get(myFirst) == null) {
        throw new IllegalStateException(this + ": EqClass " + myFirst + " is missing");
      }
      if (myList.get(mySecond) == null) {
        throw new IllegalStateException(this + ": EqClass " + mySecond + " is missing");
      }
    }

    public boolean isOrdered() {
      return myOrdered;
    }

    public @Nullable EqClass getOtherClass(int eqClassIndex) {
      if (myFirst == eqClassIndex) {
        return getSecond();
      }
      if (mySecond == eqClassIndex) {
        return getFirst();
      }
      return null;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (!(obj instanceof DistinctPair)) return false;
      DistinctPair that = (DistinctPair)obj;
      if (that.myOrdered != this.myOrdered) return false;
      return that.getFirst().equals(this.getFirst()) && that.getSecond().equals(this.getSecond()) ||
             (!myOrdered && that.getSecond().equals(this.getFirst()) && that.getFirst().equals(this.getSecond()));
    }

    @Override
    public int hashCode() {
      return getFirst().hashCode() * (myOrdered ? 31 : 1) + getSecond().hashCode();
    }

    @Override
    public String toString() {
      return "{" + myList.get(myFirst) + (myOrdered ? "<" : "!=") + myList.get(mySecond) + "}";
    }
  }
}
