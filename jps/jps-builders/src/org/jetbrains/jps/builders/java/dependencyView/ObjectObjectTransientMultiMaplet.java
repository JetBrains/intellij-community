// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java.dependencyView;

import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectObjectProcedure;

import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 */
public class ObjectObjectTransientMultiMaplet<K, V extends Streamable> extends ObjectObjectMultiMaplet<K, V>{

  private final THashMap<K, Collection<V>> myMap;
  private final BuilderCollectionFactory<V> myCollectionFactory;

  public ObjectObjectTransientMultiMaplet(TObjectHashingStrategy<K> hashingStrategy, BuilderCollectionFactory<V> collectionFactory) {
    myMap = new THashMap<>(hashingStrategy);
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
    m.forEachEntry(new TObjectObjectProcedure<K, Collection<V>>() {
      @Override
      public boolean execute(K key, Collection<V> value) {
        put(key, value);
        return true;
      }
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
      final Collection<V> x = myCollectionFactory.create();
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
    if (collection != null) {
      if (collection.removeAll(values)) {
        if (collection.isEmpty()) {
          myMap.remove(key);
        }
      }
    }
  }

  @Override
  public void remove(final K key) {
    myMap.remove(key);
  }

  @Override
  public void replaceAll(ObjectObjectMultiMaplet<K, V> m) {
    m.forEachEntry(new TObjectObjectProcedure<K, Collection<V>>() {
      @Override
      public boolean execute(K key, Collection<V> value) {
        replace(key, value);
        return true;
      }
    });
  }

  @Override
  public void forEachEntry(TObjectObjectProcedure<K, Collection<V>> procedure) {
    myMap.forEachEntry(procedure);
  }

  @Override
  public void close(){
    myMap.clear(); // free memory
  }

  @Override
  public void flush(boolean memoryCachesOnly) {
  }
}
