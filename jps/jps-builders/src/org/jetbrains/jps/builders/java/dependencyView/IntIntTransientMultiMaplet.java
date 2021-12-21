// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.function.ObjIntConsumer;

final class IntIntTransientMultiMaplet extends IntIntMultiMaplet {
  private final Int2ObjectMap<IntSet> myMap = new Int2ObjectOpenHashMap<>();

  @Override
  public boolean containsKey(final int key) {
    return myMap.containsKey(key);
  }

  @Override
  public IntSet get(final int key) {
    return myMap.get(key);
  }

  @Override
  public void putAll(IntIntMultiMaplet m) {
    m.forEachEntry((integers, value) -> put(value, integers));
  }

  @Override
  public void put(final int key, final IntSet value) {
    final IntSet x = myMap.get(key);
    if (x == null) {
      myMap.put(key, value);
    }
    else {
      x.addAll(value);
    }
  }

  @Override
  public void replace(int key, IntSet value) {
    if (value == null || value.isEmpty()) {
      myMap.remove(key);
    }
    else {
      myMap.put(key, value);
    }
  }

  @Override
  public void put(final int key, final int value) {
    final IntSet collection = myMap.get(key);
    if (collection == null) {
      final IntSet x = new IntOpenHashSet();
      x.add(value);
      myMap.put(key, x);
    }
    else {
      collection.add(value);
    }
  }

  @Override
  public void removeFrom(final int key, final int value) {
    final IntSet collection = myMap.get(key);
    if (collection != null) {
      if (collection.remove(value)) {
        if (collection.isEmpty()) {
          myMap.remove(key);
        }
      }
    }
  }

  @Override
  public void removeAll(int key, IntSet values) {
    final IntSet collection = myMap.get(key);
    if (collection != null) {
      collection.removeAll(values);
      if (collection.isEmpty()) {
        myMap.remove(key);
      }
    }
  }

  @Override
  public void remove(final int key) {
    myMap.remove(key);
  }

  @Override
  public void replaceAll(IntIntMultiMaplet m) {
    m.forEachEntry((integers, value) -> replace(value, integers));
  }

  @Override
  void forEachEntry(ObjIntConsumer<? super IntSet> proc) {
    myMap.forEach((integer, integers) -> {
      proc.accept(integers, integer);
    });
  }

  @Override
  public void close(){
    myMap.clear(); // free memory
  }

  @Override
  public void flush(boolean memoryCachesOnly) {
  }
}
