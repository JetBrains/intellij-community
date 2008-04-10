package com.intellij.util.indexing;

import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This storage is needed for indexing yet unsaved data without saving those changes to 'main' backend storage
 * 
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public class MemoryIndexStorage<Key, Value> implements IndexStorage<Key, Value> {
  private final Map<Key, UpdatableValueContainer<Value>> myMap = new HashMap<Key,UpdatableValueContainer<Value>>();
  private final IndexStorage<Key, Value> myBackendStorage;
  private AtomicBoolean myBufferingEnabled = new AtomicBoolean(false);
  private final List<BufferingStateListener> myListeners = new CopyOnWriteArrayList<BufferingStateListener>();

  public static interface BufferingStateListener {
    void bufferingStateChanged(boolean newState);
  }
  
  public MemoryIndexStorage(IndexStorage<Key, Value> backend) {
    myBackendStorage = backend;
  }

  public void addBufferingStateListsner(BufferingStateListener listener) {
    myListeners.add(listener);
  }

  public void removeBufferingStateListsner(BufferingStateListener listener) {
    myListeners.remove(listener);
  }
  
  public void setBufferingEnabled(boolean enabled) {
    final boolean wasEnabled = myBufferingEnabled.getAndSet(enabled);
    if (wasEnabled && !enabled) {
      myMap.clear();
    }
    if (wasEnabled != enabled) {
      for (BufferingStateListener listener : myListeners) {
        listener.bufferingStateChanged(enabled);
      }
    }
  }

  public void close() throws StorageException {
    myBackendStorage.close();
  }

  public void clear() throws StorageException {
    myMap.clear();
    myBackendStorage.clear();
  }

  public void flush() throws IOException {
    myBackendStorage.flush();
  }

  public Collection<Key> getKeys() throws StorageException {
    final Collection<Key> backendKeys = myBackendStorage.getKeys();
    if (myBufferingEnabled.get()) {
      for (Key key : myMap.keySet()) {
        if (myMap.get(key).size() == 0) {
          backendKeys.remove(key);
        }
      }
    }
    return backendKeys;
  }

  public void addValue(final Key key, final int inputId, final Value value) throws StorageException {
    if (!myBufferingEnabled.get()) {
      myBackendStorage.addValue(key, inputId, value);
      return;
    }
    UpdatableValueContainer<Value> valueContainer = myMap.get(key);
    if (valueContainer == null) {
      valueContainer = new ChangeTrackingValueContainer<Value>(new Computable<ValueContainer<Value>>() {
        public ValueContainer<Value> compute() {
          try {
            return myBackendStorage.read(key);
          }
          catch (StorageException e) {
            throw new RuntimeException(e);
          }
        }
      });
      myMap.put(key, valueContainer);
    }
    valueContainer.addValue(inputId, value);
  }

  public void removeValue(final Key key, final int inputId, final Value value) throws StorageException {
    if (myBufferingEnabled.get()) {
      UpdatableValueContainer<Value> container = myMap.get(key);
      if (container == null) {
        container = new ChangeTrackingValueContainer<Value>(new Computable<ValueContainer<Value>>() {
          public ValueContainer<Value> compute() {
            try {
              return myBackendStorage.read(key);
            }
            catch (StorageException e) {
              throw new RuntimeException(e);
            }
          }
        });
        myMap.put(key, container);
      }
      container.removeValue(inputId, value);
    }
    else {
      myBackendStorage.removeValue(key, inputId, value);
    }
  }

  @NotNull
  public ValueContainer<Value> read(final Key key) throws StorageException {
    if (!myBufferingEnabled.get()) {
      return myBackendStorage.read(key);
    }
    final ValueContainer<Value> valueContainer = myMap.get(key);
    if (valueContainer != null) {
      return valueContainer;
    }
    return myBackendStorage.read(key);
  }

  public void remove(final Key key) throws StorageException {
    if (myBufferingEnabled.get()) {
      myMap.put(key, new ValueContainerImpl<Value>());
    }
    else {
      myBackendStorage.remove(key);
    }
  }
}