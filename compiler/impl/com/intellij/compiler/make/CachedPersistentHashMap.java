package com.intellij.compiler.make;

import com.intellij.util.containers.SLRUMap;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 1, 2008
 */
public class CachedPersistentHashMap<Key, Value> extends PersistentHashMap<Key, Value> {
  protected final SLRUMap<Key, Value> myCache;
  private Key myKeyBeingRemoved = null;

  public CachedPersistentHashMap(File file, KeyDescriptor<Key> keyDescriptor, DataExternalizer<Value> valDescriptor, final int cacheSize) throws IOException {
    super(createIfNeeded(file), keyDescriptor, valDescriptor);
    myCache = new SLRUMap<Key,Value>(cacheSize * 2, cacheSize) {
      protected void onDropFromCache(Key key, Value value) {
        if (key != myKeyBeingRemoved && isValueDirty(value)) {
          put(key, value);
        }
      }
    };
  }

  private static File createIfNeeded(File file) throws IOException {
    if (!file.exists()) {
      file.getParentFile().mkdirs();
      file.createNewFile();
    }
    return file;
  }

  protected boolean isValueDirty(Value value) {
    return false;
  }

  public synchronized void put(Key key, Value value) throws IOException {
    myCache.remove(key);
    super.put(key, value);
  }

  @Nullable
  public synchronized Value get(Key key) throws IOException {
    Value value = myCache.get(key);
    if (value == null) {
      value = super.get(key);
      if (value != null) {
        myCache.put(key, value);
      }
    }
    return value;
  }

  public synchronized boolean containsMapping(Key key) throws IOException {
    return myCache.get(key) != null || super.containsMapping(key);
  }

  public synchronized void remove(Key key) throws IOException {
    myKeyBeingRemoved = key;
    try {
      myCache.remove(key);
    }
    finally {
      myKeyBeingRemoved = null;
    }
    super.remove(key);
  }

  public synchronized void force() {
    myCache.clear();
    super.force();
  }

  public synchronized void close() throws IOException {
    myCache.clear();
    super.close();
  }
}
