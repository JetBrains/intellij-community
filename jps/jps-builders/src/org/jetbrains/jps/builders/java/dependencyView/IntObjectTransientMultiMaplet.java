package org.jetbrains.jps.builders.java.dependencyView;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;

import java.util.Collection;

/**
 * @author: db
 * Date: 08.03.11
 */
class IntObjectTransientMultiMaplet<V extends Streamable> extends IntObjectMultiMaplet<V> {

  private final TIntObjectHashMap<Collection<V>> myMap = new TIntObjectHashMap<Collection<V>>();
  private final CollectionFactory<V> myCollectionFactory;

  public IntObjectTransientMultiMaplet(CollectionFactory<V> collectionFactory) {
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
    m.forEachEntry(new TIntObjectProcedure<Collection<V>>() {
      @Override
      public boolean execute(int key, Collection<V> value) {
        put(key, value);
        return true;
      }
    });
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
      final Collection<V> x = myCollectionFactory.create();
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
    m.forEachEntry(new TIntObjectProcedure<Collection<V>>() {
      @Override
      public boolean execute(int key, Collection<V> value) {
        replace(key, value);
        return true;
      }
    });
  }

  @Override
  public void forEachEntry(TIntObjectProcedure<Collection<V>> procedure) {
    myMap.forEachEntry(procedure);
  }

  @Override
  public void close(){
    myMap.clear(); // free memory
  }

  public void flush(boolean memoryCachesOnly) {
  }
}
