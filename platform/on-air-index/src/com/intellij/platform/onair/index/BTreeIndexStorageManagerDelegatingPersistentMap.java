// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.index;

import com.intellij.util.Processor;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.PersistentMap;

import java.io.IOException;
import java.util.function.ToIntFunction;

public class BTreeIndexStorageManagerDelegatingPersistentMap<V> implements PersistentMap<Integer, V> {

  private final BTreeIndexStorageManager myStorageManager;
  private final ID<?, ?> myID;
  private final ToIntFunction<Integer> localToRemoteId;
  private final ToIntFunction<Integer> remoteToLocalId;

  public BTreeIndexStorageManagerDelegatingPersistentMap(BTreeIndexStorageManager storageManager,
                                                         ID<?, ?> id,
                                                         ToIntFunction<Integer> remoteId,
                                                         ToIntFunction<Integer> localId) {
    myStorageManager = storageManager;
    myID = id;
    localToRemoteId = remoteId;
    remoteToLocalId = localId;
  }

  @Override
  public V get(Integer key) throws IOException {
    return delegate().get(localToRemoteId.applyAsInt(key));
  }

  @Override
  public void put(Integer key, V value) throws IOException {
    delegate().put(localToRemoteId.applyAsInt(key), value);
  }

  @Override
  public void remove(Integer key) throws IOException {
    delegate().remove(localToRemoteId.applyAsInt(key));
  }

  @Override
  public boolean processKeys(final Processor<? super Integer> processor) throws IOException {
    return delegate().processKeys(integer -> processor.process(remoteToLocalId.applyAsInt(integer)));
  }

  @Override
  public boolean isClosed() {
    return delegate().isClosed();
  }

  @Override
  public boolean isDirty() {
    return delegate().isDirty();
  }

  @Override
  public void force() {
    delegate().force();
  }

  @Override
  public void close() throws IOException {
    delegate().close();
  }

  @Override
  public void clear() throws IOException {
    delegate().clear();
  }

  @Override
  public void markDirty() throws IOException {
    delegate().markDirty();
  }

  @SuppressWarnings("unchecked")
  private BTreeForwardIndexStorage<V> delegate() {
    return (BTreeForwardIndexStorage<V>)myStorageManager.stateRef.get().forwardIndices.get(myID.getName());
  }
}
