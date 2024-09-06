// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.CommonProcessors;
import com.intellij.util.io.AppendablePersistentMap;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public abstract class AbstractStateStorage<Key, T> implements StorageOwner {
  protected final Object dataLock = new Object();
  private final File baseFile;
  private final KeyDescriptor<Key> keyDescriptor;
  private final DataExternalizer<T> stateExternalizer;
  private PersistentHashMap<Key, T> map;

  public AbstractStateStorage(File storePath, KeyDescriptor<Key> keyDescriptor, DataExternalizer<T> stateExternalizer) throws IOException {
    baseFile = storePath;
    this.keyDescriptor = keyDescriptor;
    this.stateExternalizer = stateExternalizer;
    map = createMap(storePath);
  }

  public void force() {
    synchronized (dataLock) {
      map.force();
    }
  }

  @Override
  public void close() throws IOException {
    synchronized (dataLock) {
      map.close();
    }
  }

  @Override
  public void clean() throws IOException {
    wipe();
  }

  public boolean wipe() {
    synchronized (dataLock) {
      try {
        map.closeAndClean();
      }
      catch (IOException ignored) {
      }
      try {
        map = createMap(baseFile);
      }
      catch (IOException ignored) {
        return false;
      }
      return true;
    }
  }

  public void update(Key key, @Nullable T state) throws IOException {
    if (state != null) {
      synchronized (dataLock) {
        map.put(key, state);
      }
    }
    else {
      remove(key);
    }
  }

  public void appendData(final Key key, final T data) throws IOException {
    synchronized (dataLock) {
      map.appendData(key, (AppendablePersistentMap.ValueDataAppender)out -> stateExternalizer.save(out, data));
    }
  }

  public void remove(Key key) throws IOException {
    synchronized (dataLock) {
      map.remove(key);
    }
  }

  public @Nullable T getState(Key key) throws IOException {
    synchronized (dataLock) {
      return map.get(key);
    }
  }

  public Collection<Key> getKeys() throws IOException {
    synchronized (dataLock) {
      List<Key> result = new ArrayList<>();
      map.processKeysWithExistingMapping(new CommonProcessors.CollectProcessor<>(result));
      return result;
    }
  }

  public Iterator<Key> getKeysIterator() throws IOException {
    synchronized (dataLock) {
      List<Key> result = new ArrayList<>();
      map.processKeysWithExistingMapping(new CommonProcessors.CollectProcessor<>(result));
      return result.iterator();
    }
  }

  private PersistentHashMap<Key, T> createMap(@NotNull File file) throws IOException {
    FileUtilRt.createIfNotExists(file); //todo assert
    return new PersistentHashMap<>(file, keyDescriptor, stateExternalizer);
  }

  @Override
  public void flush(boolean memoryCachesOnly) {
    if (!memoryCachesOnly) {
      force();
    }
  }
}
