package com.intellij.util.indexing;

import org.jetbrains.annotations.NotNull;

import java.io.Flushable;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public interface IndexStorage<Key, Value> extends Flushable{
  
  void addValue(Key key, int inputId, Value value) throws StorageException;

  void removeValue(Key key, int inputId, Value value) throws StorageException;

  void remove(Key key) throws StorageException;

  void clear() throws StorageException;
  
  @NotNull
  ValueContainer<Value> read(Key key) throws StorageException;

  Collection<Key> getKeys() throws StorageException;
  
  void close() throws StorageException;
}
