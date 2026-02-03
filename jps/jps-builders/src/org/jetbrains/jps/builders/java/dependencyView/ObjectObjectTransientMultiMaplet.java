// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.PairProcessor;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

final class ObjectObjectTransientMultiMaplet<K, V extends Streamable> extends ObjectObjectMultiMaplet<K, V>{
  private final Object2ObjectOpenCustomHashMap<K, Collection<V>> myMap;
  private final Supplier<? extends Collection<V>> myCollectionFactory;

  ObjectObjectTransientMultiMaplet(Hash.Strategy<K> hashingStrategy, Supplier<? extends Collection<V>> collectionFactory) {
    myMap = new Object2ObjectOpenCustomHashMap<>(hashingStrategy);
    myCollectionFactory = collectionFactory;
  }

  @Override
  public boolean containsKey(final K key) {
    return myMap.containsKey(key);
  }

  @Override
  public Collection<V> get(final K key) {
    return myMap.get(key);
  }

  @Override
  public void putAll(ObjectObjectMultiMaplet<K, V> m) {
    m.forEachEntry((key, value) -> {
      put(key, value);
      return true;
    });
  }

  @Override
  public void put(final K key, final Collection<V> value) {
    final Collection<V> x = myMap.get(key);
    if (x == null) {
      myMap.put(key, value);
    }
    else {
      x.addAll(value);
    }
  }

  @Override
  public void replace(K key, Collection<V> value) {
    if (value == null || value.isEmpty()) {
      myMap.remove(key);
    }
    else {
      myMap.put(key, value);
    }
  }

  @Override
  public void put(final K key, final V value) {
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
  public void removeFrom(final K key, final V value) {
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
  public void removeAll(K key, Collection<V> values) {
    final Collection<V> collection = myMap.get(key);
    if (collection != null && collection.removeAll(values) && collection.isEmpty()) {
      myMap.remove(key);
    }
  }

  @Override
  public void remove(final K key) {
    myMap.remove(key);
  }

  @Override
  public void replaceAll(ObjectObjectMultiMaplet<K, V> m) {
    m.forEachEntry((key, value) -> {
      replace(key, value);
      return true;
    });
  }

  @Override
  public void forEachEntry(@NotNull PairProcessor<? super K, ? super Collection<V>> procedure) {
    for (Map.Entry<K, Collection<V>> entry : myMap.entrySet()) {
      if (!procedure.process(entry.getKey(), entry.getValue())) break;
    }
  }

  @Override
  public void close(){
    myMap.clear(); // free memory
  }

  @Override
  public void flush(boolean memoryCachesOnly) {
  }
}
