package com.intellij.util.indexing;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.locks.Lock;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public interface UpdatableIndex<Key, Value, Input> extends AbstractIndex<Key,Value> {

  void clear() throws StorageException;

  void flush() throws StorageException;

  void update(int inputId, @Nullable Input content) throws StorageException;
  
  Lock getReadLock();
  
  Lock getWriteLock();
  
  void dispose();
}
