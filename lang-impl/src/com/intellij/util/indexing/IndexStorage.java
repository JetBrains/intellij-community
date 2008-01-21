package com.intellij.util.indexing;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public interface IndexStorage<Key, Value> {
  
  void addValue(Key key, int inputId, Value value) throws StorageException;

  void removeValue(Key key, int inputId, Value value) throws StorageException;

  void remove(Key key) throws StorageException;

  @NotNull
  ValueContainer<Value> read(Key key) throws StorageException;
}
