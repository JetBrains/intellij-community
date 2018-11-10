// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.index;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.InvertedIndexValueIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.ToIntFunction;

public class BTreeIndexStorageManagerDelegatingIndexStorage<Key, Value> implements VfsAwareIndexStorage<Key, Value> {

  private final BTreeIndexStorageManager myStorageManager;
  private final ID<?, ?> myID;
  private final ToIntFunction<Integer> localToRemoteId;
  private final ToIntFunction<Integer> remoteToLocalId;

  public BTreeIndexStorageManagerDelegatingIndexStorage(BTreeIndexStorageManager storageManager,
                                                        ID<?, ?> id,
                                                        ToIntFunction<Integer> localToRemoteId,
                                                        ToIntFunction<Integer> remoteToLocalId) {
    myStorageManager = storageManager;
    myID = id;
    this.localToRemoteId = localToRemoteId;
    this.remoteToLocalId = remoteToLocalId;
  }

  @Override
  public boolean processKeys(@NotNull Processor<? super Key> processor, GlobalSearchScope scope, @Nullable IdFilter idFilter)
    throws StorageException {
    return delegate().processKeys(processor, scope, idFilter == null ? null : new IdFilter() {
      @Override
      public boolean containsFileId(int id) {
        return idFilter.containsFileId(remoteToLocalId.applyAsInt(id));
      }
    });
  }

  @Override
  public void addValue(Key key, int inputId, Value value) throws StorageException {
    delegate().addValue(key, localToRemoteId.applyAsInt(inputId), value);
  }

  @Override
  public void removeAllValues(@NotNull Key key, int inputId) throws StorageException {
    delegate().removeAllValues(key, localToRemoteId.applyAsInt(inputId));
  }

  @Override
  public void clear() throws StorageException {
    delegate().clear();
  }

  @NotNull
  @Override
  public ValueContainer<Value> read(Key key) throws StorageException {
    final ValueContainer<Value> valueContainer = delegate().read(key);

    return new ValueContainer<Value>() {
      @NotNull
      @Override
      public ValueIterator<Value> getValueIterator() {
        return new InvertedIndexValueIterator<Value>() {
          private final ValueIterator<Value> sourceIterator = valueContainer.getValueIterator();

          @NotNull
          @Override
          public IntIterator getInputIdsIterator() {
            return new IntIteratorWrapper(sourceIterator.getInputIdsIterator());
          }

          @NotNull
          @Override
          public IntPredicate getValueAssociationPredicate() {
            return new IntPredicate() {
              private final IntPredicate sourcePredicate = sourceIterator.getValueAssociationPredicate();

              @Override
              public boolean contains(int id) {
                return sourcePredicate.contains(localToRemoteId.applyAsInt(id));
              }
            };
          }

          @Override
          public boolean hasNext() {
            return sourceIterator.hasNext();
          }

          @Override
          public Value next() {
            return sourceIterator.next();
          }

          @Override
          public Object getFileSetObject() {
            return ((InvertedIndexValueIterator)sourceIterator).getFileSetObject();
          }
        };
      }

      @Override
      public int size() {
        return valueContainer.size();
      }
    };
  }

  @Override
  public void clearCaches() {
    delegate().clearCaches();
  }

  @Override
  public void close() throws StorageException {
    delegate().close();
  }

  @Override
  public void flush() throws IOException {
    delegate().flush();
  }

  @SuppressWarnings("unchecked")
  private BTreeIndexStorage<Key, Value> delegate() {
    return (BTreeIndexStorage<Key, Value>)myStorageManager.stateRef.get().indexStorages.get(myID.getName());
  }

  private class IntIteratorWrapper implements ValueContainer.IntIterator {

    private final ValueContainer.IntIterator sourceIdsIterator;

    public IntIteratorWrapper(ValueContainer.IntIterator sourceIdsIterator) {
      this.sourceIdsIterator = sourceIdsIterator;
    }

    @Override
    public boolean hasNext() {
      return sourceIdsIterator.hasNext();
    }

    @Override
    public int next() {
      return remoteToLocalId.applyAsInt(sourceIdsIterator.next());
    }

    @Override
    public int size() {
      return sourceIdsIterator.size();
    }
  }
}
