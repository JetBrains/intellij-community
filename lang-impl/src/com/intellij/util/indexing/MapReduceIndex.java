package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public class MapReduceIndex<Key, Value, Input> implements UpdatableIndex<Key,Value, Input> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.MapReduceIndex");
  private final DataIndexer<Key, Value, Input> myIndexer;
  private final IndexStorage<Key, Value> myStorage;
  
  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();
  
  private final Alarm myFlushAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private final Runnable myFlushRequest = new Runnable() {
    public void run() {
      final Lock lock = getWriteLock();
      lock.lock();
      try {
        myStorage.flush();
      }
      catch (IOException e) {
        LOG.error(e);
      }
      finally {
        lock.unlock();
      }
    }
  };

  public MapReduceIndex(DataIndexer<Key, Value, Input> indexer, final IndexStorage<Key, Value> storage) {
    myIndexer = indexer;
    myStorage = storage;
  }

  public IndexStorage<Key, Value> getStorage() {
    return myStorage;
  }

  public void clear() throws StorageException {
    final Lock lock = getWriteLock();
    lock.lock();
    try {
      myStorage.clear();
    }
    catch (StorageException e) {
      LOG.error(e);
    }
    finally {
      lock.unlock();
    }
  }

  public void dispose() {
    final Lock lock = getWriteLock();
    lock.lock();
    try {
      myStorage.close();
    }
    catch (StorageException e) {
      LOG.error(e);
    }
    finally {
      lock.unlock();
    }
  }

  public Lock getReadLock() {
    return myLock.readLock();
  }

  public Lock getWriteLock() {
    return myLock.writeLock();
  }

  @NotNull
  public ValueContainer<Value> getData(final Key key) throws StorageException {
    final Lock lock = getReadLock();
    lock.lock();
    try {
      return myStorage.read(key);
    }
    finally {
      lock.unlock();
      scheduleFlush();
    }
  }

  public void removeData(final Key key) throws StorageException {
    final Lock lock = getWriteLock();
    lock.lock();
    try {
      myStorage.remove(key);
    }
    finally {
      lock.unlock();
      scheduleFlush();
    }
  }

  public void update(int inputId, @Nullable Input content, @Nullable Input oldContent) throws StorageException {
    final Map<Key, Set<Value>> oldData = oldContent != null? processInput(oldContent) : Collections.<Key, Set<Value>>emptyMap();
    final Map<Key, Set<Value>> data    = content != null? processInput(content) : Collections.<Key, Set<Value>>emptyMap();

    final Set<Key> allKeys = new HashSet<Key>(oldData.size() + data.size());
    allKeys.addAll(oldData.keySet());
    allKeys.addAll(data.keySet());

    if (allKeys.size() > 0) {
      for (Key key : allKeys) {
        // remove outdated values
        final Set<Value> oldValues = oldData.get(key);
        final Lock writeLock = getWriteLock();
        if (oldValues != null && oldValues.size() > 0) {
          writeLock.lock();
          try {
            for (Value oldValue : oldValues) {
              myStorage.removeValue(key, inputId, oldValue);
            }
          }
          finally {
            writeLock.unlock();
          }
        }
        // add new values
        final Set<Value> newValues = data.get(key);
        if (newValues != null) {
          writeLock.lock();
          try {
            for (Value value : newValues) {
              myStorage.addValue(key, inputId, value);
            }
          }
          finally {
            writeLock.unlock();
          }
        }
      }
      scheduleFlush();
    }
  }
  
  private Map<Key, Set<Value>> processInput(@NotNull Input content) {
    final MyDataConsumer<Key, Value> consumer = new MyDataConsumer<Key, Value>();
    myIndexer.map(content, consumer);
    return consumer.getResult();
  }
  
  private void scheduleFlush() {
    myFlushAlarm.cancelAllRequests();
    myFlushAlarm.addRequest(myFlushRequest, 15000 /* 15 sec */);
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
