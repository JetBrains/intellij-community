package com.intellij.util.indexing;

import com.intellij.cassandra.CassandraIndexTable;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.impl.UpdatableValueContainer;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.util.Collection;

public class ClientIndexStorage<K, V> implements VfsAwareIndexStorage<K, V>, BufferingIndexStorage {

  private final VfsAwareIndexStorage<K, V> myDelegate;
  private final ID<?, ?> myId;
  private final KeyDescriptor<K> myKd;
  private final DataExternalizer<V> myVd;
  private final THashSet<K> myCachedKeys = new THashSet<>();

  public ClientIndexStorage(VfsAwareIndexStorage<K, V> delegate, ID<?, ?> indexId, KeyDescriptor<K> kd, DataExternalizer<V> vd) {
    myDelegate = delegate;
    myId = indexId;
    myKd = kd;
    myVd = vd;
  }

  @Override
  public void addBufferingStateListener(@NotNull BufferingStateListener listener) {
    ((BufferingIndexStorage)myDelegate).addBufferingStateListener(listener);
  }

  @Override
  public void removeBufferingStateListener(@NotNull BufferingStateListener listener) {
    ((BufferingIndexStorage)myDelegate).removeBufferingStateListener(listener);
  }

  @Override
  public void setBufferingEnabled(boolean enabled) {
    ((BufferingIndexStorage)myDelegate).setBufferingEnabled(enabled);
  }

  @Override
  public boolean isBufferingEnabled() {
    return ((BufferingIndexStorage)myDelegate).isBufferingEnabled();
  }

  @Override
  public void clearMemoryMap() {
    ((BufferingIndexStorage)myDelegate).clearMemoryMap();
  }

  @Override
  public void fireMemoryStorageCleared() {
    ((BufferingIndexStorage)myDelegate).fireMemoryStorageCleared();
  }

  @Override
  public boolean processKeys(@NotNull Processor<K> processor, GlobalSearchScope scope, @Nullable IdFilter idFilter)
    throws StorageException {
    return myDelegate.processKeys(processor, scope, idFilter);
  }

  @Override
  public void addValue(K k, int inputId, V v) throws StorageException {
    myDelegate.addValue(k, inputId, v);
  }

  @Override
  public void removeAllValues(@NotNull K k, int inputId) throws StorageException {
    myDelegate.removeAllValues(k, inputId);
  }

  @Override
  public void clear() throws StorageException {

  }

  private void ensureCached(K k) throws StorageException {
    if (!myCachedKeys.contains(k)) {
      synchronized (myCachedKeys) {
        if (!myCachedKeys.contains(k)) {
          try {
            cacheKey(k);
          }
          catch (IOException e) {
            throw new StorageException(e);
          }
          myCachedKeys.add(k);
        }
      }
    }
  }

  @NotNull
  @Override
  public ValueContainer<V> read(K k) throws StorageException {
    ensureCached(k);
    return myDelegate.read(k);
  }

  private static <Value> void readInto(UpdatableValueContainer<Value> into, DataInputStream stream, DataExternalizer<Value> externalizer) throws IOException {
    while (stream.available() > 0) {
      final int valueCount = DataInputOutputUtil.readINT(stream);
      if (valueCount < 0) {
        final int inputId = -valueCount;
        into.removeAssociatedValue(inputId);
      }
      else {
        for (int valueIdx = 0; valueIdx < valueCount; valueIdx++) {
          final Value value = externalizer.read(stream);
          int idCountOrSingleValue = DataInputOutputUtil.readINT(stream);
          if (idCountOrSingleValue > 0) {
            into.addValue(idCountOrSingleValue, value);
          }
          else {
            idCountOrSingleValue = -idCountOrSingleValue;
            int prev = 0;
            for (int i = 0; i < idCountOrSingleValue; i++) {
              final int id = DataInputOutputUtil.readINT(stream);
              into.addValue(prev + id, value);
              prev += id;
            }
          }
        }
      }
    }
  }

  private void cacheKey(K k) throws IOException, StorageException {
    BufferExposingByteArrayOutputStream baos = new BufferExposingByteArrayOutputStream();
    myKd.save(new DataOutputStream(baos), k);
    ByteBuffer bb = ByteBuffer.wrap(baos.getInternalBuffer(), 0, baos.size());
    Collection<ByteBuffer> values = CassandraIndexTable.getInstance().readKey(myId.toString(), 1, bb);
    UpdatableValueContainer<V> container = (UpdatableValueContainer<V>)myDelegate.read(k);
    for (ByteBuffer value : values) {
      ByteBufferInputStream stream = new ByteBufferInputStream(value);
      readInto(container, new DataInputStream(stream), myVd);
    }
  }

  @Override
  public void clearCaches() {
    myDelegate.clearCaches();
  }

  @Override
  public void close() throws StorageException {
    myDelegate.close();
  }

  @Override
  public void flush() throws IOException {
    myDelegate.flush();
  }
}