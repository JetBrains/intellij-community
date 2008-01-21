package com.intellij.util.indexing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public class MapReduceIndex<Key, Value, Input> implements UpdatableIndex<Key,Value, Input> {
  private final DataIndexer<Key, Value, Input> myMap;
  private final IndexStorage<Key, Value> myStorage;

  public MapReduceIndex(DataIndexer<Key, Value, Input> map, final IndexStorage<Key, Value> storage) {
    myMap = map;
    myStorage = storage;
  }

  @NotNull
  public ValueContainer<Value> getData(final Key key) throws StorageException {
    return myStorage.read(key);
  }

  public void removeData(final Key key) throws StorageException {
    myStorage.remove(key);
  }

  public void update(int inputId, @Nullable Input content, @Nullable Input oldContent) throws StorageException {
    final Map<Key, Set<Value>> oldData = oldContent != null? processInput(oldContent) : Collections.<Key, Set<Value>>emptyMap();
    final Map<Key, Set<Value>> data    = content != null? processInput(content) : Collections.<Key, Set<Value>>emptyMap();

    final Set<Key> allKeys = new HashSet<Key>(oldData.size() + data.size());
    allKeys.addAll(oldData.keySet());
    allKeys.addAll(data.keySet());

    for (Key key : allKeys) {
      // remove outdated values
      final Set<Value> oldValues = oldData.get(key);
      if (oldValues != null && oldValues.size() > 0) {
        for (Value oldValue : oldValues) {
          myStorage.removeValue(key, inputId, oldValue);
        }
      }
      // add new values
      final Set<Value> newValues = data.get(key);
      if (newValues != null) {
        for (Value value : newValues) {
          myStorage.addValue(key, inputId, value);
        }
      }
    }
  }
  
  private Map<Key, Set<Value>> processInput(@NotNull Input content) {
    final MyDataConsumer<Key, Value> consumer = new MyDataConsumer<Key, Value>();
    myMap.map(content, consumer);
    return consumer.getResult();
  }
  
  private static final class MyDataConsumer<K, V> implements IndexDataConsumer<K, V> {
    final Map<K, Set<V>> myResult = new HashMap<K, Set<V>>();
    
    public void consume(final K key, final V value) {
      Set<V> set = myResult.get(key);
      if (set == null) {
        set = new HashSet<V>();
        myResult.put(key, set);
      }
      set.add(value);
    }

    public Map<K, Set<V>> getResult() {
      return myResult;
    }
  }
}
