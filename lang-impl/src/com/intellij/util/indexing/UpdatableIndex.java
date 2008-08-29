package com.intellij.util.indexing;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.locks.Lock;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public interface UpdatableIndex<Key, Value, Input> extends AbstractIndex<Key,Value> {

  void removeData(Key key) throws StorageException;
  
  void clear() throws StorageException;

  void flush();

  void update(int inputId, @Nullable Input content, @Nullable Input oldContent) throws StorageException;
  
  Lock getReadLock();
  
  Lock getWriteLock();
  
  void dispose();
}
