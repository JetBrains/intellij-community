package com.intellij.compiler.make;

import com.intellij.reference.SoftReference;
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
  protected final SLRUMap<Key, SoftReference<Value>> myCache;

  public CachedPersistentHashMap(File file, KeyDescriptor<Key> keyDescriptor, DataExternalizer<Value> valDescriptor, final int cacheSize) throws IOException {
    super(file, keyDescriptor, valDescriptor);
    myCache = new SLRUMap<Key,SoftReference<Value>>(cacheSize * 2, cacheSize);
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
    final SoftReference<Value> ref = myCache.get(key);
    Value value = ref == null? null : ref.get();
    if (value == null) {
      value = super.get(key);
      if (value != null) {
        myCache.put(key, new SoftReference<Value>(value));
      }
    }
    return value;
  }

  public synchronized boolean containsMapping(Key key) throws IOException {
    final SoftReference<Value> ref = myCache.get(key);
    return (ref != null && ref.get() != null) || super.containsMapping(key);
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
