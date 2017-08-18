package com.intellij.util.indexing;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.impl.ChangeTrackingValueContainer;
import com.intellij.util.indexing.impl.UpdatableValueContainer;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;

public class IndexerIndexStorage<K, V> implements VfsAwareIndexStorage<K, V>, BufferingIndexStorage {

  private final VfsAwareIndexStorage<K,V> myDelegate;
  private final ID<?, ?> myIndexId;
  private final KeyDescriptor<K> myKd;
  private final DataExternalizer<V> myVd;
  private final Set<K> changedKeys = ContainerUtil.newConcurrentSet();

  public IndexerIndexStorage(VfsAwareIndexStorage<K,V> delegate, ID<?, ?> indexId, KeyDescriptor<K> kd, DataExternalizer<V> vd){
    myDelegate = delegate;
    myIndexId = indexId;
    myKd = kd;
    myVd = vd;
  }

  @Override
  public void addBufferingStateListener(@NotNull BufferingStateListener listener) {
    ((BufferingIndexStorage) myDelegate).addBufferingStateListener(listener);
  }

  @Override
  public void removeBufferingStateListener(@NotNull BufferingStateListener listener) {
    ((BufferingIndexStorage) myDelegate).removeBufferingStateListener(listener);
  }

  @Override
  public void setBufferingEnabled(boolean enabled) {
    ((BufferingIndexStorage) myDelegate).setBufferingEnabled(enabled);
  }

  @Override
  public boolean isBufferingEnabled() {
    return ((BufferingIndexStorage) myDelegate).isBufferingEnabled();
  }

  @Override
  public void clearMemoryMap() {
    ((BufferingIndexStorage) myDelegate).clearMemoryMap();
  }

  @Override
  public void fireMemoryStorageCleared() {
    ((BufferingIndexStorage) myDelegate).fireMemoryStorageCleared();
  }

  @Override
  public boolean processKeys(@NotNull Processor<K> processor, GlobalSearchScope scope, @Nullable IdFilter idFilter)
    throws StorageException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addValue(K k, int inputId, V v) throws StorageException {
    myDelegate.addValue(k, inputId, v);
    changedKeys.add(k);
  }

  @Override
  public void removeAllValues(@NotNull K k, int inputId) throws StorageException {
    myDelegate.removeAllValues(k, inputId);
    changedKeys.add(k);
  }

  @Override
  public void clear() throws StorageException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public ValueContainer<V> read(K k) throws StorageException {
    return myDelegate.read(k);
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
    System.out.println("flushing " + myIndexId + "changedKeys: " + changedKeys.size());
    try {
      CassandraIndexTable.getInstance().bulkInsert(myIndexId.toString(), 1, 1, changedKeys.stream().map(k -> {
        try {
          UpdatableValueContainer<V> read = (UpdatableValueContainer<V>)read(k);
          if (read instanceof ChangeTrackingValueContainer) {
            read = ((ChangeTrackingValueContainer)read).getMergedData();
          }

          BufferExposingByteArrayOutputStream baos = new BufferExposingByteArrayOutputStream();
          DataOutputStream os = new DataOutputStream(baos);
          read.saveTo(os, myVd);
          ByteBuffer values = ByteBuffer.wrap(baos.getInternalBuffer(), 0, baos.size());

          BufferExposingByteArrayOutputStream baosk = new BufferExposingByteArrayOutputStream();
          DataOutputStream dosk = new DataOutputStream(baosk);
          myKd.save(dosk, k);
          ByteBuffer kbb = ByteBuffer.wrap(baosk.getInternalBuffer(), 0, baosk.size());

          return Pair.create(kbb, values);
        }
        catch (IOException | StorageException e) {
          throw new RuntimeException(e);
        }
      }));
    } catch (Throwable e)  {
      e.printStackTrace();
    }
    myDelegate.flush();
    changedKeys.clear();
    System.out.println("done flushing " + myIndexId);
  }
}