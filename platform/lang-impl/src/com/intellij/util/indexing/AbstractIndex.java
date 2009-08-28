package com.intellij.util.indexing;

import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 24, 2007
 */
public interface AbstractIndex<Key, Value> {
  @NotNull
  ValueContainer<Value> getData(Key key) throws StorageException;
  
  Collection<Key> getAllKeys() throws StorageException;

  boolean processAllKeys(Processor<Key> processor) throws StorageException;
}
