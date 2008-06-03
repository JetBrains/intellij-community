package com.intellij.compiler.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NonNls;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public abstract class StateCache<T> {
  private PersistentHashMap<String, T> myMap;
  private SoftReference<Map<String, T>> myMemCache = null;
  private final File myBaseFile;

  public StateCache(@NonNls File storePath) throws IOException {
    myBaseFile = storePath;
    myMap = createMap(storePath);
  }

  protected abstract T read(DataInput stream) throws IOException;

  protected abstract void write(T t, DataOutput out) throws IOException;

  public void force() {
    myMap.force();
  }
  
  public void close() throws IOException {
    clearCache();
    myMap.close();
  }
  
  public boolean wipe() {
    try {
      clearCache();
      myMap.close();
    }
    catch (IOException ignored) {
    }
    final String baseName = myBaseFile.getName();
    for (File file : myBaseFile.getParentFile().listFiles()) {
      if (file.getName().startsWith(baseName)) {
        FileUtil.delete(file);
      }
    }
    try {
      myMap = createMap(myBaseFile);
    }
    catch (IOException ignored) {
      return false;
    }
    return true;
  }

  public void update(@NonNls String url, T state) throws IOException {
    if (state != null) {
      updateCache(url, state);
      myMap.put(url, state);
    }
    else {
      remove(url);
    }
  }

  public void remove(String url) throws IOException {
    removeCached(url);
    myMap.remove(url);
  }

  public T getState(String url) throws IOException {
    T state = getCached(url);
    if (state != null) {
      return state;
    }
    try {
      return state = myMap.get(url);
    }
    finally {
      if (state != null) {
        updateCache(url, state);
      }
    }
  }

  public Collection<String> getUrls() throws IOException {
    return myMap.allKeys();
  }

  public Iterator<String> getUrlsIterator() throws IOException {
    return myMap.allKeys().iterator();
  }


  private PersistentHashMap<String, T> createMap(final File file) throws IOException {
    return new PersistentHashMap<String,T>(file, new EnumeratorStringDescriptor(), new DataExternalizer<T>() {
      public void save(final DataOutput out, final T value) throws IOException {
        StateCache.this.write(value, out);
      }

      public T read(final DataInput in) throws IOException {
        return StateCache.this.read(in);
      }
    });
  }

  private void updateCache(@NonNls String url, T state) {
    Map<String, T> map = myMemCache != null? myMemCache.get() : null;
    if (map == null) {
      map = new HashMap<String,T>();
      myMemCache = new SoftReference<Map<String,T>>(map);
    }
    map.put(url, state);
  }

  private void removeCached(@NonNls String url) {
    final Map<String, T> map = myMemCache != null? myMemCache.get() : null;
    if (map != null) {
      map.remove(url);
    }
  }
  
  private T getCached(@NonNls String url) {
    Map<String, T> map = myMemCache != null? myMemCache.get() : null;
    return map != null? map.get(url) : null;
  }
  
  private void clearCache() {
    myMemCache = null;
  }
}