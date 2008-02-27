package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author Eugene Zhuravlev
*         Date: Dec 20, 2007
*/
public final class MapIndexStorage<Key, Value> implements IndexStorage<Key, Value>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.MapIndexStorage");
  private PersistentHashMap<Key, ValueContainer<Value>> myMap;
  private final SLRUCache<Key, ChangeTrackingValueContainer<Value>> myCache;
  private Key myKeyBeingRemoved = null;
  private final File myStorageFile;
  private final PersistentEnumerator.DataDescriptor<Key> myKeyDescriptor;
  private final ValueContainerExternalizer<Value> myValueContainerExternalizer;

  public MapIndexStorage(
    File storageFile, 
    final PersistentEnumerator.DataDescriptor<Key> keyDescriptor, 
    final DataExternalizer<Value> valueExternalizer) throws IOException {

    myStorageFile = storageFile;
    myKeyDescriptor = keyDescriptor;
    myValueContainerExternalizer = new ValueContainerExternalizer<Value>(valueExternalizer);
    myMap = new PersistentHashMap<Key,ValueContainer<Value>>(myStorageFile, myKeyDescriptor, myValueContainerExternalizer);
    myCache = new SLRUCache<Key, ChangeTrackingValueContainer<Value>>(16 * 1024, 4 * 1024) {
      @NotNull
      public ChangeTrackingValueContainer<Value> createValue(final Key key) {
        return new ChangeTrackingValueContainer<Value>(new ChangeTrackingValueContainer.Computable<Value>() {
          public ValueContainer<Value> compute() throws StorageException {
            ValueContainer<Value> value = null;
            try {
              value = myMap.get(key);
              if (value == null) {
                value = new ValueContainerImpl<Value>();
              }
            }
            catch (IOException e) {
              throw new StorageException(e);
            }
            return value;
          }
        });
      }

      protected void onDropFromCache(final Key key, final ChangeTrackingValueContainer<Value> valueContainer) {
        if (key.equals(myKeyBeingRemoved) || myMap == null || !valueContainer.isDirty()) {
          return;
        }
        try {
          if (valueContainer.canUseDataAppend()) {
            final ValueContainer<Value> toAppend = valueContainer.getDataToAppend();
            if (toAppend.size() > 0) {
              final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
              //noinspection IOResourceOpenedButNotSafelyClosed
              myValueContainerExternalizer.save(new DataOutputStream(bytes), toAppend);
                  
              myMap.appendData(key, new PersistentHashMap.ValueDataAppender() {
                public void append(final DataOutput out) throws IOException {
                  final byte[] barr = bytes.toByteArray();
                  System.out.println("[INDEX STORAGE] Appending " + barr.length + " bytes to key " + key);
                  out.write(barr);
                }
              });
            }
          }
          else {
            myMap.put(key, valueContainer);
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    };
  }
  
  public void flush() {
    //System.out.println("Cache hit rate = " + myCache.hitRate());
    myCache.clear();
    myMap.force();
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
    myCache.clear();
    FileUtil.delete(myStorageFile);
    try {
      myMap = new PersistentHashMap<Key,ValueContainer<Value>>(myStorageFile, myKeyDescriptor, myValueContainerExternalizer);
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  public Collection<Key> getKeys() throws StorageException {
    try {
      myCache.clear(); // this will ensure that all new keys are made into the map
      return myMap.allKeys();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @NotNull
  public ValueContainer<Value> read(final Key key) throws StorageException {
    return myCache.get(key);
  }

  public void addValue(final Key key, final int inputId, final Value value) throws StorageException {
    myCache.get(key).addValue(inputId, value);
  }

  public void removeValue(final Key key, final int inputId, final Value value) throws StorageException {
    myCache.get(key).removeValue(inputId, value);
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
  
  private static final class ValueContainerExternalizer<T> implements DataExternalizer<ValueContainer<T>> {
    private final DataExternalizer<T> myExternalizer;

    private ValueContainerExternalizer(DataExternalizer<T> externalizer) {
      myExternalizer = externalizer;
    }

    public void save(final DataOutput out, final ValueContainer<T> container) throws IOException {
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
