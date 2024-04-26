// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.AbstractUpdateData;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.indexing.impl.forward.AbstractMapForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@ApiStatus.Experimental
public abstract class MapReduceIndexBase<Key, Value, FileCache> extends MapReduceIndex<Key, Value, FileContent>
  implements UpdatableIndex<Key, Value, FileContent, FileCache> {
  private final boolean mySingleEntryIndex;

  protected MapReduceIndexBase(@NotNull IndexExtension<Key, Value, FileContent> extension,
                               @NotNull ThrowableComputable<? extends IndexStorage<Key, Value>, ? extends IOException> storage,
                               @Nullable ThrowableComputable<? extends ForwardIndex, ? extends IOException> forwardIndex,
                               @Nullable ForwardIndexAccessor<Key, Value> forwardIndexAccessor) throws IOException {
    super(extension, storage, forwardIndex, forwardIndexAccessor);
    if (!(myIndexId instanceof ID<?, ?>)) {
      throw new IllegalArgumentException("myIndexId should be instance of com.intellij.util.indexing.ID");
    }
    mySingleEntryIndex = extension instanceof SingleEntryFileBasedIndexExtension;
  }

  @Override
  public boolean processAllKeys(@NotNull Processor<? super Key> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter) throws StorageException {
    return ConcurrencyUtil.withLock(getLock().readLock(), () ->
      ((VfsAwareIndexStorage<Key, Value>)myStorage).processKeys(processor, scope, idFilter)
    );
  }

  @Override
  public @NotNull Map<Key, Value> getIndexedFileData(int fileId) throws StorageException {
    return ConcurrencyUtil.withLock(getLock().readLock(), () -> {
      try {
        // TODO remove Collections.unmodifiableMap when ContainerUtil started to return unmodifiable map in all cases
        //noinspection RedundantUnmodifiable
        return Collections.unmodifiableMap(ContainerUtil.notNullize(getNullableIndexedData(fileId)));
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    });
  }

  protected @Nullable Map<Key, Value> getNullableIndexedData(int fileId) throws IOException, StorageException {
    if (isDisposed()) {
      return null;
    }
    // in future we will get rid of forward index for SingleEntryFileBasedIndexExtension
    if (mySingleEntryIndex) {
      @SuppressWarnings("unchecked")
      Key key = (Key)(Object)fileId;
      Ref<Map<Key, Value>> result = new Ref<>(Collections.emptyMap());
      ValueContainer<Value> container = getData(key);
      container.forEach((id, value) -> {
        boolean acceptNullValues = ((SingleEntryIndexer<?>)myIndexer).isAcceptNullValues();
        if (value != null || acceptNullValues) {
          result.set(Collections.singletonMap(key, value));
        }
        return false;
      });
      return result.get();
    }
    if (getForwardIndexAccessor() instanceof AbstractMapForwardIndexAccessor<Key, Value, ?> forwardIndexAccessor) {
      ByteArraySequence serializedInputData = getForwardIndex().get(fileId);
      return forwardIndexAccessor.convertToInputDataMap(fileId, serializedInputData);
    }
    getLogger().error("Can't fetch indexed data for index " + myIndexId.getName());
    return null;
  }

  @Override
  public void checkCanceled() {
    ProgressManager.checkCanceled();
  }

  @Override
  public void updateWithMap(@NotNull AbstractUpdateData<Key, Value> updateData) throws StorageException {
    try {
      super.updateWithMap(updateData);
    }
    catch (ProcessCanceledException e) {
      getLogger().error("ProcessCanceledException is not expected here!", e);
      throw e;
    }
  }

  @Override
  public void setBufferingEnabled(boolean enabled) {
    // TODO to be removed
    throw new UnsupportedOperationException();
  }

  @Override
  public void cleanupMemoryStorage() {
    // TODO to be removed
    throw new UnsupportedOperationException();
  }

  @Override
  public void cleanupForNextTest() {
    // TODO to be removed
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeTransientDataForFile(int inputId) {
    // TODO to be removed
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeTransientDataForKeys(int inputId,
                                         @NotNull InputDataDiffBuilder<Key, Value> diffBuilder) {
    // TODO to be removed
    throw new UnsupportedOperationException();
  }

  protected abstract Logger getLogger();
}
