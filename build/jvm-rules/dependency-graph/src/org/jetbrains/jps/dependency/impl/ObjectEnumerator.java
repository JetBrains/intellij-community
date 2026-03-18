// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ObjectEnumerator<T> {
  private final Object2IntMap<T> myToIntMap = new Object2IntOpenHashMap<>();
  private final ArrayList<T> myTable = new ArrayList<>();
  private final Function<T, T> myInterner;
  private int myUnsavedIndex; // "everything saved" condition: "myUnsavedIndex == myTable.size()"

  public ObjectEnumerator() {
    this(List.of());
  }
  
  public ObjectEnumerator(Iterable<? extends T> initial) {
    this(initial, Function.identity());
  }
  
  public ObjectEnumerator(Iterable<? extends T> initial, Function<T, T> interner) {
    myInterner = interner;
    for (T obj : initial) {
      append(obj);
    }
    myUnsavedIndex = myTable.size();
  }

  public void append(T obj) {
    int num = myTable.size();
    T interned = myInterner.apply(obj);
    myTable.add(interned);
    myToIntMap.put(interned, num);
  }

  @Nullable
  public T lookup(int num) {
    return num < myTable.size()? myTable.get(num) : null;
  }

  public int toNumber(T obj) {
    T interned = myInterner.apply(obj);
    int nextAvailable = myTable.size();
    int num = myToIntMap.getOrDefault(interned, nextAvailable);
    if (num == nextAvailable) { // not in map yet
      myTable.add(interned);
      myToIntMap.put(interned, num);
    }
    return num;
  }

  public int getTableSize() {
    return myTable.size();
  }

  public int getUnsavedCount() {
    return myTable.size() - myUnsavedIndex;
  }

  public interface TableEntryConsumer<T> {
    void accept(int num, T obj) throws IOException;
  }

  public boolean drainUnsaved(TableEntryConsumer<? super T> consumer) throws IOException {
    int size = myTable.size();
    if (myUnsavedIndex == size) {
      return false;
    }
    while (myUnsavedIndex < size) {
      int num = myUnsavedIndex++;
      consumer.accept(num, myTable.get(num));
    }
    return true;
  }
}
