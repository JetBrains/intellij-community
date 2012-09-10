package org.jetbrains.jps.builders.java.dependencyView;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/10/12
 */
public class IntObjectTransientMaplet<V> extends IntObjectMaplet<V>{
  private final TIntObjectHashMap<V> myMap = new TIntObjectHashMap<V>();
  @Override
  boolean containsKey(int key) {
    return myMap.containsKey(key);
  }

  @Override
  V get(int key) {
    return myMap.get(key);
  }

  @Override
  void put(int key, V value) {
    myMap.put(key, value);
  }

  @Override
  void putAll(IntObjectMaplet<V> m) {
    m.forEachEntry(new TIntObjectProcedure<V>() {
      @Override
      public boolean execute(int key, V value) {
        myMap.put(key, value);
        return true;
      }
    });
  }

  @Override
  void remove(int key) {
    myMap.remove(key);
  }

  @Override
  void close() {
    myMap.clear();
  }

  @Override
  void forEachEntry(TIntObjectProcedure<V> proc) {
    myMap.forEachEntry(proc);
  }

  @Override
  void flush(boolean memoryCachesOnly) {
  }
}
