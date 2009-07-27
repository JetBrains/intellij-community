package com.intellij.compiler.make;

import com.intellij.openapi.diagnostic.Logger;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.CachedPersistentHashMap");
  protected final SLRUMap<Key, Value> myCache;

  public CachedPersistentHashMap(File file, KeyDescriptor<Key> keyDescriptor, DataExternalizer<Value> valDescriptor, final int cacheSize) throws IOException {
    super(file, keyDescriptor, valDescriptor);
    myCache = new SLRUMap<Key,Value>(cacheSize * 2, cacheSize) {
      protected void onDropFromCache(Key key, Value value) {
        if (isValueDirty(value)) {
          try {
            CachedPersistentHashMap.super.put(key, value);
          }
          catch (IOException e) {
            LOG.info(e);
          }
        }
      }
    };
  }

  protected boolean isValueDirty(Value value) {
    return false;
  }

  public synchronized void put(Key key, Value value) throws IOException {
    myCache.remove(key);
    super.put(key, value);
  }

  public synchronized void appendData(Key key, ValueDataAppender appender) throws IOException {
    myCache.remove(key);
    super.appendData(key, appender);
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
    final Value value = myCache.get(key);
    return value != null || super.containsMapping(key);
  }

  public synchronized void remove(Key key) throws IOException {
    myCache.remove(key);
    super.remove(key);
  }

  public synchronized void force() {
    clearCache();
    super.force();
  }

  public synchronized void close() throws IOException {
    clearCache();
    super.close();
  }

  public void clearCache() {
    myCache.clear();
  }
}
