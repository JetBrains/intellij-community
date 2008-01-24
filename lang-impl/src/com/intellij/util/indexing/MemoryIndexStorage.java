package com.intellij.util.indexing;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This storage is needed for indexing yet unsaved data without saving those changes to 'main' backend storage
 * 
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public class MemoryIndexStorage<Key, Value> implements IndexStorage<Key, Value> {
  private final Map<Key, ValueContainerImpl<Value>> myMap = new HashMap<Key,ValueContainerImpl<Value>>();
  private final Set<Key> myRemovedKeys = new HashSet<Key>();
  private final IndexStorage<Key, Value> myBackendStorage;
  private AtomicBoolean myBufferingEnabled = new AtomicBoolean(false);

  public MemoryIndexStorage(IndexStorage<Key, Value> backend) {
    myBackendStorage = backend;
  }

  public void setBufferingEnabled(boolean enabled) {
    final boolean wasEnabled = myBufferingEnabled.getAndSet(enabled);
    if (wasEnabled && !enabled) {
      myMap.clear();
      myRemovedKeys.clear();
    }
  }

  public void flush() throws IOException {
    myBackendStorage.flush();
  }

  public void addValue(final Key key, final int inputId, final Value value) throws StorageException {
    if (!myBufferingEnabled.get()) {
      myBackendStorage.addValue(key, inputId, value);
      return;
    }
    ValueContainerImpl<Value> valueContainer = myMap.get(key);
    if (valueContainer == null) {
      if (myRemovedKeys.contains(key)) {
        myRemovedKeys.remove(key);
        valueContainer = new ValueContainerImpl<Value>();
      }
      else {
        final ValueContainer<Value> backendContainer = myBackendStorage.read(key);
        valueContainer = backendContainer != null? copyOf(backendContainer) : new ValueContainerImpl<Value>();
      }
      myMap.put(key, valueContainer);
    }
    valueContainer.addValue(inputId, value);
  }

  private ValueContainerImpl<Value> copyOf(final ValueContainer<Value> from) {
    final ValueContainerImpl<Value> container = new ValueContainerImpl<Value>();
    for (final Iterator<Value> valueIterator = from.getValueIterator(); valueIterator.hasNext();) {
      final Value value = valueIterator.next();
      for (final ValueContainer.IntIterator intIterator = from.getInputIdsIterator(value); intIterator.hasNext();) {
        container.addValue(intIterator.next(), value);
      }
    }
    return container;
  }

  public void removeValue(final Key key, final int inputId, final Value value) throws StorageException {
    if (myBufferingEnabled.get()) {
      ValueContainerImpl<Value> container = myMap.get(key);
      if (container == null && !myRemovedKeys.contains(key)) {
        final ValueContainer<Value> backendContainer = myBackendStorage.read(key);
        if (backendContainer != null) {
          container = copyOf(backendContainer);
        }
      }
      if (container != null) {
        container.removeValue(inputId, value);
        if (container.size() == 0) {
          remove(key);
        }
      }
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
    ValueContainer<Value> valueContainer = myMap.get(key);
    if (valueContainer == null) {
      valueContainer = myRemovedKeys.contains(key)? new ValueContainerImpl<Value>() : myBackendStorage.read(key);
    }
    return valueContainer;
  }

  public void remove(final Key key) throws StorageException {
    if (myBufferingEnabled.get()) {
      myMap.remove(key);
      myRemovedKeys.add(key);
    }
    else {
      myBackendStorage.remove(key);
    }
  }
}