package org.jetbrains.jps.incremental.storage;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

public abstract class AbstractStateStorage<Key, T> {
  private PersistentHashMap<Key, T> myMap;
  private final File myBaseFile;
  private final KeyDescriptor<Key> myKeyDescriptor;
  private final DataExternalizer<T> myStateExternalizer;

  public AbstractStateStorage(@NonNls File storePath, KeyDescriptor<Key> keyDescriptor, DataExternalizer<T> stateExternalizer) throws IOException {
    myBaseFile = storePath;
    myKeyDescriptor = keyDescriptor;
    myStateExternalizer = stateExternalizer;
    myMap = createMap(storePath);
  }

  public void force() {
    myMap.force();
  }

  public void close() throws IOException {
    myMap.close();
  }

  public boolean wipe() {
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

  public void update(Key key, T state) throws IOException {
    if (state != null) {
      myMap.put(key, state);
    }
    else {
      remove(key);
    }
  }

  public void remove(Key key) throws IOException {
    myMap.remove(key);
  }

  public T getState(Key key) throws IOException {
    return myMap.get(key);
  }

  public Collection<Key> getKeys() throws IOException {
    return myMap.getAllKeysWithExistingMapping();
  }

  public Iterator<Key> getKeysIterator() throws IOException {
    return myMap.getAllKeysWithExistingMapping().iterator();
  }


  private PersistentHashMap<Key, T> createMap(final File file) throws IOException {
    return new PersistentHashMap<Key,T>(file, myKeyDescriptor, myStateExternalizer);
  }

}
