// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ObjectEnumerator<T> {
  private final Object2IntMap<T> myToIntMap = new Object2IntOpenHashMap<>();
  private final ArrayList<T> myTable = new ArrayList<>();
  private int myUnsavedIndex; // "everything saved" condition: "myUnsavedIndex == myTable.size()"

  public ObjectEnumerator() {
    this(List.of());
  }
  
  public ObjectEnumerator(Iterable<? extends T> initial) {
    for (T str : initial) {
      append(str);
    }
    myUnsavedIndex = myTable.size();
  }

  public void append(T obj) {
    int num = myTable.size();
    myTable.add(obj);
    myToIntMap.put(obj, num);
  }

  @Nullable
  public T lookup(int num) {
    return num < myTable.size()? myTable.get(num) : null;
  }

  public int toNumber(T obj) {
    int nextAvailable = myTable.size();
    int num = myToIntMap.getOrDefault(obj, nextAvailable);
    if (num == nextAvailable) { // not in map yet
      myTable.add(obj);
      myToIntMap.put(obj, num);
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
