// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.indexing.impl.forward.AbstractMapForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Internal
public abstract class MapReduceIndexBase<Key, Value, FileCache> extends MapReduceIndex<Key, Value, FileContent>
  implements UpdatableIndex<Key, Value, FileContent, FileCache> {
  private final boolean mySingleEntryIndex;

  protected MapReduceIndexBase(@NotNull IndexExtension<Key, Value, FileContent> extension,
                               @NotNull ThrowableComputable<? extends IndexStorage<Key, Value>, ? extends IOException> storageFactory,
                               @Nullable ThrowableComputable<? extends ForwardIndex, ? extends IOException> forwardIndexFactory,
                               @Nullable ForwardIndexAccessor<Key, Value> forwardIndexAccessor) throws IOException {
    super(extension, storageFactory, forwardIndexFactory, forwardIndexAccessor);
    IndexId<Key, Value> indexId = super.indexId();
    if (!(indexId instanceof ID<?, ?>)) {
      throw new IllegalArgumentException("extension.getName() (=" + indexId + ") should be instance of com.intellij.util.indexing.ID");
    }
    mySingleEntryIndex = extension instanceof SingleEntryFileBasedIndexExtension;
  }

  @Override
  public ID<Key, Value> indexId() {
    //ctor checks it is always an ID:
    return (ID<Key, Value>)super.indexId();
  }

  @Override
  public boolean processAllKeys(@NotNull Processor<? super Key> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter)
    throws StorageException {
    return ((VfsAwareIndexStorage<Key, Value>)getStorage()).processKeys(processor, scope, idFilter);
  }

  @Override
  public @NotNull Map<Key, Value> getIndexedFileData(int fileId) throws StorageException {
    try {
      // TODO remove Collections.unmodifiableMap when ContainerUtil started to return unmodifiable map in all cases
      //noinspection RedundantUnmodifiable
      return Collections.unmodifiableMap(ContainerUtil.notNullize(getNullableIndexedData(fileId)));
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  protected @Nullable Map<Key, Value> getNullableIndexedData(int fileId) throws IOException, StorageException {
    if (isDisposed()) {
      return null;//TODO RC: better throw CancellationException?
    }

    if (mySingleEntryIndex) {
      // there is no forward index for SingleEntryFileBasedIndexExtension, so get an entry from inverted index
      // (it _must_ be <=1 entry there, with Key=(Integer)fileId)
      @SuppressWarnings("unchecked")
      Key key = (Key)(Object)fileId;
      Ref<Map<Key, Value>> result = new Ref<>(Collections.emptyMap());
      boolean acceptNullValues = ((SingleEntryIndexer<?>)indexer()).isAcceptNullValues();
      withData(key, container -> {
        container.forEach((id, value) -> {
          if (value != null || acceptNullValues) {
            result.set(Collections.singletonMap(key, value));
          }
          return false;
        });
        return !result.get().isEmpty();
      });
      return result.get();
    }

    ForwardIndexAccessor<Key, Value> indexAccessor = getForwardIndexAccessor();
    if (indexAccessor instanceof AbstractMapForwardIndexAccessor<Key, Value, ?> forwardIndexAccessor) {
      ForwardIndex forwardIndex = getForwardIndex();
      assert forwardIndex != null : "forwardIndex must NOT be null if forwardIndexAccessor(" + forwardIndexAccessor + ") != null";
      ByteArraySequence serializedInputData = forwardIndex.get(fileId);
      return forwardIndexAccessor.convertToInputDataMap(fileId, serializedInputData);
    }

    //We expect only 2 valid index configurations:
    // 1. Both forwardIndex and forwardIndexAccessors are NOT null: regular index configuration
    // 2. Both forwardIndex and forwardIndexAccessors ARE null: single-entry index (=inverted index is used instead of forward)
    // Both are processed above -> all other combinations are invalid:
    getLogger().error("Can't fetch indexed data for index=" + indexId() + ", (accessor: " + indexAccessor + ")");
    return null;
  }

  @Override
  public void checkCanceled() {
    ProgressManager.checkCanceled();
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
