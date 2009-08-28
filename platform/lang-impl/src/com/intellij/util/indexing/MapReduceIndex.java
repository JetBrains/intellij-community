package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Alarm;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.io.storage.HeavyProcessLatch;
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
  private final ID<Key, Value> myIndexId;
  private final DataIndexer<Key, Value, Input> myIndexer;
  private final IndexStorage<Key, Value> myStorage;
  
  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();
  
  private final Alarm myFlushAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private final Runnable myFlushRequest = new Runnable() {
    public void run() {
      if (canFlush()) {
        try {
          flush();
        }
        catch (StorageException e) {
          LOG.info(e);
          if (myIndexId != null) {
            FileBasedIndex.getInstance().requestRebuild(myIndexId);
          }
        }
      }
      else {
        myFlushAlarm.addRequest(myFlushRequest, 20000 /* 20 sec */);  // postpone flushing
      }
    }

    private boolean canFlush() {
      if (HeavyProcessLatch.INSTANCE.isRunning()) {
        final Runtime runtime = Runtime.getRuntime();
        final float used = runtime.totalMemory() - runtime.freeMemory();
        final float memUsage = used / runtime.maxMemory();
        return memUsage >= 0.95f;
      }
      return true;
    }
  };


  public MapReduceIndex(@Nullable final ID<Key, Value> indexId, DataIndexer<Key, Value, Input> indexer, final IndexStorage<Key, Value> storage) {
    myIndexId = indexId;
    myIndexer = indexer;
    myStorage = storage;
  }

  public IndexStorage<Key, Value> getStorage() {
    return myStorage;
  }

  public void clear() throws StorageException {
    try {
      getWriteLock().lock();
      myStorage.clear();
    }
    catch (StorageException e) {
      LOG.error(e);
    }
    finally {
      getWriteLock().unlock();
    }
  }

  public void flush() throws StorageException{
    try {
      getReadLock().lock();
      myStorage.flush();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof StorageException || cause instanceof IOException) {
        throw new StorageException(cause);
      }
      else {
        throw e;
      }
    }
    finally {
      getReadLock().unlock();
    }
  }

  public void dispose() {
    final Lock lock = getWriteLock();
    try {
      lock.lock();
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

  public Collection<Key> getAllKeys() throws StorageException {
    Set<Key> allKeys = new HashSet<Key>();
    processAllKeys(new CommonProcessors.CollectProcessor<Key>(allKeys));
    return allKeys;
  }

  public boolean processAllKeys(Processor<Key> processor) throws StorageException {
    final Lock lock = getReadLock();
    try {
      lock.lock();
      return myStorage.processKeys(processor);
    }
    finally {
      lock.unlock();
    }
  }

  @NotNull
  public ValueContainer<Value> getData(final Key key) throws StorageException {
    final Lock lock = getReadLock();
    try {
      lock.lock();
      return myStorage.read(key);
    }
    finally {
      lock.unlock();
    }
  }

  public void removeData(final Key key) throws StorageException {
    final Lock lock = getWriteLock();
    try {
      lock.lock();
      myStorage.remove(key);
    }
    finally {
      lock.unlock();
      scheduleFlush();
    }
  }

  public void update(int inputId, @Nullable Input content, @Nullable Input oldContent) throws StorageException {
    final Map<Key, Value> oldData = mapOld(oldContent);
    final Map<Key, Value> data = mapNew(content);

    updateWithMap(inputId, oldData, data);
  }

  protected Map<Key, Value> mapNew(final Input content) throws StorageException {
    return content != null ? myIndexer.map(content) : Collections.<Key, Value>emptyMap();
  }

  protected Map<Key, Value> mapOld(final Input oldContent) throws StorageException {
    return oldContent != null ? myIndexer.map(oldContent) : Collections.<Key, Value>emptyMap();
  }

  protected void updateWithMap(final int inputId, final Map<Key, Value> oldData, final Map<Key, Value> newData) throws StorageException {
    final Set<Key> allKeys = new HashSet<Key>(oldData.size() + newData.size());
    allKeys.addAll(oldData.keySet());
    allKeys.addAll(newData.keySet());

    if (allKeys.size() > 0) {
      final Lock writeLock = getWriteLock();

      //if (getClass().getName().contains("StubUpdatingIndex")) {
      //  final VirtualFile file = IndexInfrastructure.findFileById((PersistentFS)ManagingFS.getInstance(), inputId);
      //  System.out.print("FILE [" + inputId+ "]=" + (file != null? file.getPresentableUrl() : "null"));
      //  if (oldData.containsKey(inputId)) {
      //    System.out.print(" REMOVE");
      //  }
      //  if (newData.containsKey(inputId)) {
      //    System.out.print(" ADD");
      //  }
      //  System.out.println("");
      //  if (allKeys.size() > 1) {
      //    System.out.println("More than one key for stub updating index: " + allKeys);
      //  }
      //}

      for (Key key : allKeys) {
        assert key != null : "Null keys are not allowed. Index: " + myIndexId; 
        // remove outdated values
        try {
          writeLock.lock();
          if (oldData.containsKey(key)) {
            final Value oldValue = oldData.get(key);
            //if (myIndexId != null && "js.index".equals(myIndexId.toString())) {
            //  System.out.println(key + ": BEFORE REMOVE: " + myStorage.read(key).toValueList());
            //}
            myStorage.removeValue(key, inputId, oldValue);
            //if (myIndexId != null && "js.index".equals(myIndexId.toString())) {
            //  System.out.println(key + ": AFTER REMOVE: " + myStorage.read(key).toValueList());
            //}
          }
          // add new values
          if (newData.containsKey(key)) {
            final Value newValue = newData.get(key);
            //if (myIndexId != null && "js.index".equals(myIndexId.toString())) {
            //  System.out.println(key + ": BEFORE ADD: " + myStorage.read(key).toValueList());
            //}
            myStorage.addValue(key, inputId, newValue);
            //if (myIndexId != null && "js.index".equals(myIndexId.toString())) {
            //  System.out.println(key + ": AFTER ADD: " + myStorage.read(key).toValueList());
            //}
          }
        }
        finally {
          writeLock.unlock();
        }
      }
      scheduleFlush();
    }
  }

  private void scheduleFlush() {
    myFlushAlarm.cancelAllRequests();
    myFlushAlarm.addRequest(myFlushRequest, 15000 /* 15 sec */);
  }

}
