package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

public abstract class AbstractStateStorage<Key, T> {
  private PersistentHashMap<Key, T> myMap;
  private final File myBaseFile;
  private final KeyDescriptor<Key> myKeyDescriptor;
  private final DataExternalizer<T> myStateExternalizer;
  protected final Object myDataLock = new Object();

  public AbstractStateStorage(@NonNls File storePath, KeyDescriptor<Key> keyDescriptor, DataExternalizer<T> stateExternalizer) throws IOException {
    myBaseFile = storePath;
    myKeyDescriptor = keyDescriptor;
    myStateExternalizer = stateExternalizer;
    myMap = createMap(storePath);
  }

  public void force() {
    synchronized (myDataLock) {
      myMap.force();
    }
  }

  public void dropMemoryCache() {
    synchronized (myDataLock) {
      if (myMap.isDirty()) {
        myMap.dropMemoryCaches();
      }
    }
  }

  public void close() throws IOException {
    synchronized (myDataLock) {
      myMap.close();
    }
  }

  public boolean wipe() {
    synchronized (myDataLock) {
      try {
        myMap.close();
      }
      catch (IOException ignored) {
      }
      PersistentHashMap.deleteFilesStartingWith(myBaseFile);
      try {
        myMap = createMap(myBaseFile);
      }
      catch (IOException ignored) {
        return false;
      }
      return true;
    }
  }

  public void update(Key key, @Nullable T state) throws IOException {
    if (state != null) {
      synchronized (myDataLock) {
        myMap.put(key, state);
      }
    }
    else {
      remove(key);
    }
  }

  public void appendData(final Key key, final T data) throws IOException {
    synchronized (myDataLock) {
      myMap.appendData(key, new PersistentHashMap.ValueDataAppender() {
        public void append(DataOutput out) throws IOException {
          myStateExternalizer.save(out, data);
        }
      });
    }
  }

  public void remove(Key key) throws IOException {
    synchronized (myDataLock) {
      myMap.remove(key);
    }
  }

  public T getState(Key key) throws IOException {
    synchronized (myDataLock) {
      return myMap.get(key);
    }
  }

  public Collection<Key> getKeys() throws IOException {
    synchronized (myDataLock) {
      return myMap.getAllKeysWithExistingMapping();
    }
  }

  public Iterator<Key> getKeysIterator() throws IOException {
    synchronized (myDataLock) {
      return myMap.getAllKeysWithExistingMapping().iterator();
    }
  }


  private PersistentHashMap<Key, T> createMap(final File file) throws IOException {
    FileUtil.createIfDoesntExist(file);
    return new PersistentHashMap<Key,T>(file, myKeyDescriptor, myStateExternalizer);
  }

  public void flush(boolean memoryCachesOnly) {
    if (memoryCachesOnly) {
      dropMemoryCache();
    }
    else {
      force();
    }
  }
}
