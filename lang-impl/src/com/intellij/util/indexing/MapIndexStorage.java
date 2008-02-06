package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ObjectCache;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Iterator;

/**
 * @author Eugene Zhuravlev
*         Date: Dec 20, 2007
*/
final class MapIndexStorage<Key, Value> implements IndexStorage<Key, Value>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.MapIndexStorage");
  private PersistentHashMap<Key, ValueContainerImpl<Value>> myMap;
  private final DataExternalizer<Value> myValueExternalizer;
  private final ObjectCache<Key, ValueContainerImpl<Value>> myCache;
  private Key myKeyBeingRemoved = null;
  private final File myStorageFile;
  private final PersistentEnumerator.DataDescriptor<Key> myKeyDescriptor;
  private final ValueContainerExternalizer<Value> myValueContainerExternalizer;

  public MapIndexStorage(
    File storageFile, 
    final PersistentEnumerator.DataDescriptor<Key> keyDescriptor, 
    final DataExternalizer<Value> valueExternalizer) throws IOException {
    myValueExternalizer = valueExternalizer;

    myStorageFile = storageFile;
    myKeyDescriptor = keyDescriptor;
    myValueContainerExternalizer = new ValueContainerExternalizer<Value>(valueExternalizer);
    myMap = new PersistentHashMap<Key,ValueContainerImpl<Value>>(myStorageFile, myKeyDescriptor, myValueContainerExternalizer);
    myCache = new ObjectCache<Key, ValueContainerImpl<Value>>(1024);
    myCache.addDeletedPairsListener(new ObjectCache.DeletedPairsListener() {
      public void objectRemoved(final Object key, final Object value) {
        final Key _key = (Key)key;
        if (_key.equals(myKeyBeingRemoved)) {
          return;
        }
        try {
          //noinspection unchecked
          if (myMap != null) {
            myMap.put(_key, (ValueContainerImpl<Value>)value);
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });
  }
  
  public void flush() {
    //System.out.println("Cache hit rate = " + myCache.hitRate());
    try {
      myCache.removeAll();
      myMap.flush();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public void close() throws StorageException {
    try {
      flush();
      myMap.close();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  public void clear() throws StorageException{
    try {
      myMap.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
    myMap = null;
    myCache.removeAll();
    FileUtil.delete(myStorageFile);
    try {
      myMap = new PersistentHashMap<Key,ValueContainerImpl<Value>>(myStorageFile, myKeyDescriptor, myValueContainerExternalizer);
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @NotNull
  public ValueContainer<Value> read(final Key key) throws StorageException {
    final ValueContainer<Value> container = myCache.get(key);
    if (container != null) {
      return container;
    }
    return readAndCache(key);
  }

  public void addValue(final Key key, final int inputId, final Value value) throws StorageException {
    try {
      final ValueContainerImpl<Value> container = myCache.get(key);
      if (container != null) {
        container.addValue(inputId, value);
      }
      else {
        myMap.appendData(key, new PersistentHashMap.ValueDataAppender() {
          public void append(final DataOutput out) throws IOException {
            myValueExternalizer.save(out, value);
            DataInputOutputUtil.writeSINT(out, -inputId);
          }
        });
      }
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  public void removeValue(final Key key, final int inputId, final Value value) throws StorageException {
    try {
      ValueContainerImpl<Value> container = myCache.get(key);
      if (container == null) {
        container = myMap.get(key);
        if (container != null) {
          myCache.cacheObject(key, container);
        }
      }
      if (container != null) {
        container.removeValue(inputId, value);
      }
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @NotNull
  private ValueContainerImpl<Value> readAndCache(final Key key) throws StorageException {
    try {
      ValueContainerImpl<Value> value = myMap.get(key);
      if (value == null) {
        value = new ValueContainerImpl<Value>();
      }
      myCache.cacheObject(key, value);
      return value;
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  public void remove(final Key key) throws StorageException {
    try {
      myKeyBeingRemoved = key;
      myCache.remove(key);
      myMap.remove(key);
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    finally {
      myKeyBeingRemoved = null;
    }
  }
  
  private static final class ValueContainerExternalizer<T> implements DataExternalizer<ValueContainerImpl<T>> {
    private final DataExternalizer<T> myExternalizer;

    private ValueContainerExternalizer(DataExternalizer<T> externalizer) {
      myExternalizer = externalizer;
    }

    public void save(final DataOutput out, final ValueContainerImpl<T> container) throws IOException {
      for (final Iterator<T> valueIterator = container.getValueIterator(); valueIterator.hasNext();) {
        final T value = valueIterator.next(); 
        myExternalizer.save(out, value);

        final ValueContainer.IntIterator ids = container.getInputIdsIterator(value);
        if (ids != null) {
          if (ids.size() == 1) {
            DataInputOutputUtil.writeSINT(out, -ids.next());
          }
          else {
            DataInputOutputUtil.writeSINT(out, ids.size());
            while (ids.hasNext()) {
              DataInputOutputUtil.writeSINT(out, ids.next());
            }
          }
        }
        else {
          DataInputOutputUtil.writeSINT(out, 0);
        }
      }
    }

    public ValueContainerImpl<T> read(final DataInput in) throws IOException {
      DataInputStream stream = (DataInputStream)in;
      final ValueContainerImpl<T> valueContainer = new ValueContainerImpl<T>();
      
      while (stream.available() > 0) {
        final T value = myExternalizer.read(in);
        final int idCount = DataInputOutputUtil.readSINT(in);
        if (idCount < 0) {
          valueContainer.addValue(-idCount, value);
        }
        else if (idCount > 0){
          for (int i = 0; i < idCount; i++) {
            valueContainer.addValue(DataInputOutputUtil.readSINT(in), value);
          }
        }
      }
      
      return valueContainer;
    }
  }
}
