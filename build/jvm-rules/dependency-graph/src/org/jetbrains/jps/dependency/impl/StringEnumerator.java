// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class StringEnumerator {
  private final Object2IntMap<String> myToIntMap = new Object2IntOpenHashMap<>();
  private final ArrayList<String> myTable = new ArrayList<>();
  private int myUnsavedIndex; // "everything saved" condition: "myUnsavedIndex == myTable.size()"

  public StringEnumerator() {
    this(List.of());
  }
  
  public StringEnumerator(Iterable<String> initial) {
    for (String str : initial) {
      addString(str);
    }
    myUnsavedIndex = myTable.size();
  }

  public void addString(String str) {
    int num = myTable.size();
    myTable.add(str);
    myToIntMap.put(str, num);
  }

  @Nullable
  public String lookupString(int num) {
    return num < myTable.size()? myTable.get(num) : null;
  }

  public int toNumber(String str) {
    int nextAvailable = myTable.size();
    int num = myToIntMap.getOrDefault(str, nextAvailable);
    if (num == nextAvailable) { // not in map yet
      myTable.add(str);
      myToIntMap.put(str, num);
    }
    return num;
  }

  public int getTableSize() {
    return myTable.size();
  }

  public int getUnsavedCount() {
    return myTable.size() - myUnsavedIndex;
  }

  public interface EntryConsumer {
    void accept(int num, String str) throws IOException;
  }

  public boolean drainUnsaved(EntryConsumer consumer) throws IOException {
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
