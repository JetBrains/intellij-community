// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.Collection;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

final class IntObjectTransientMultiMaplet<V> extends IntObjectMultiMaplet<V> {
  @SuppressWarnings("SSBasedInspection")
  private final Int2ObjectOpenHashMap<Collection<V>> myMap = new Int2ObjectOpenHashMap<>();
  private final Supplier<? extends Collection<V>> myCollectionFactory;

  IntObjectTransientMultiMaplet(Supplier<? extends Collection<V>> collectionFactory) {
    myCollectionFactory = collectionFactory;
  }

  @Override
  public boolean containsKey(final int key) {
    return myMap.containsKey(key);
  }

  @Override
  public Collection<V> get(final int key) {
    return myMap.get(key);
  }

  @Override
  public void putAll(IntObjectMultiMaplet<V> m) {
    m.forEachEntry((vs, value) -> put(value, vs));
  }

  @Override
  public void put(final int key, final Collection<V> value) {
    final Collection<V> x = myMap.get(key);
    if (x == null) {
      myMap.put(key, value);
    }
    else {
      x.addAll(value);
    }
  }

  @Override
  public void replace(int key, Collection<V> value) {
    if (value == null || value.isEmpty()) {
      myMap.remove(key);
    }
    else {
      myMap.put(key, value);
    }
  }

  @Override
  public void put(final int key, final V value) {
    final Collection<V> collection = myMap.get(key);
    if (collection == null) {
      final Collection<V> x = myCollectionFactory.get();
      x.add(value);
      myMap.put(key, x);
    }
    else {
      collection.add(value);
    }
  }

  @Override
  public void removeFrom(final int key, final V value) {
    final Collection<V> collection = myMap.get(key);
    if (collection != null) {
      if (collection.remove(value)) {
        if (collection.isEmpty()) {
          myMap.remove(key);
        }
      }
    }
  }

  @Override
  public void removeAll(int key, Collection<V> values) {
    final Collection<V> collection = myMap.get(key);
    if (collection != null) {
      if (collection.removeAll(values)) {
        if (collection.isEmpty()) {
          myMap.remove(key);
        }
      }
    }
  }

  @Override
  public void remove(final int key) {
    myMap.remove(key);
  }

  @Override
  public void replaceAll(IntObjectMultiMaplet<V> m) {
    m.forEachEntry((vs, value) -> replace(value, vs));
  }

  @Override
  void forEachEntry(ObjIntConsumer<? super Collection<V>> procedure) {
    myMap.int2ObjectEntrySet().fastForEach(entry -> procedure.accept(entry.getValue(), entry.getIntKey()));
  }

  @Override
  public void close(){
    myMap.clear(); // free memory
  }

  @Override
  public void flush(boolean memoryCachesOnly) {
  }
}
