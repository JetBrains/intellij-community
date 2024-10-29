// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.forward;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.io.VoidDataExternalizer;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Single-entry index is when the Indexer maps a file to a single value V -- i.e. the index is basically a (fileId->V) cache.
 * <p/>
 * Naturally it should be just forwardIndex, without inverted index -- but we process it in a different way:
 * Indexer maps a fileId => singletonMap(key: fileId, value: V), and we create regular _inverted_ index from that mapping
 * -- which will be exactly (fileId -> ValueContainer(V, fileId) ) mapping. This is done, probably, to keep generic Index interface,
 * there forward index is always an implementation detail?
 * <p/>
 * In this setup, forwardIndex become totally useless: we could use inverted index mapping for updates. Hence
 * DataType=Void: nothing to save in forwardIndex.
 */
@Internal
public class SingleEntryIndexForwardIndexAccessor<V> extends AbstractMapForwardIndexAccessor<Integer, V, Void> {

  private final NotNullLazyValue<UpdatableIndex<Integer, V, ?, ?>> myIndex;

  public SingleEntryIndexForwardIndexAccessor(SingleEntryFileBasedIndexExtension<V> extension) {
    super(VoidDataExternalizer.INSTANCE);
    ID<Integer, V> name = extension.getName();
    FileBasedIndexEx fileBasedIndex = (FileBasedIndexEx)FileBasedIndex.getInstance();
    myIndex = NotNullLazyValue.volatileLazy(() -> fileBasedIndex.getIndex(name));
  }

  @Override
  public @NotNull InputDataDiffBuilder<Integer, V> createDiffBuilderByMap(int inputId, @Nullable Map<Integer, V> map) throws IOException {
    return new SingleValueDiffBuilder<>(inputId, ContainerUtil.notNullize(map));
  }

  @Override
  public final @NotNull InputDataDiffBuilder<Integer, V> getDiffBuilder(int inputId,
                                                                        @Nullable ByteArraySequence sequence) throws IOException {
      Map<Integer, V> data = fetchInputDataFromIndex(inputId);
      return createDiffBuilderByMap(inputId, data);
  }

  @Override
  protected @Nullable Map<Integer, V> convertToMap(int inputId, @Nullable Void inputData) throws IOException {
    return fetchInputDataFromIndex(inputId);
  }

  private Map<Integer, V> fetchInputDataFromIndex(int inputId) throws IOException {
    try {
      return ProgressManager.getInstance().computeInNonCancelableSection(() -> myIndex.getValue().getIndexedFileData(inputId));
    }
    catch (StorageException e) {
      throw new IOException(e);
    }
  }

  @Override
  public @Nullable Void convertToDataType(@NotNull InputData<Integer, V> data) {
    return null;
  }

  @Override
  public final @Nullable ByteArraySequence serializeIndexedData(@NotNull InputData<Integer, V> data) {
    return null;
  }

  @Internal
  public static final class SingleValueDiffBuilder<V> extends DirectInputDataDiffBuilder<Integer, V> {
    private final boolean myContainsValue;
    private final @Nullable V myCurrentValue;

    public SingleValueDiffBuilder(int inputId, @NotNull Map<Integer, V> currentData) {
      this(inputId, !currentData.isEmpty(), ContainerUtil.getFirstItem(currentData.values()));
    }

    private SingleValueDiffBuilder(int inputId, boolean containsValue, @Nullable V currentValue) {
      super(inputId);
      myContainsValue = containsValue;
      myCurrentValue = currentValue;
    }

    @Override
    public @NotNull Collection<Integer> getKeys() {
      return myContainsValue ? Collections.singleton(myInputId) : Collections.emptySet();
    }

    @Override
    public boolean differentiate(@NotNull Map<Integer, V> newData,
                                 @NotNull UpdatedEntryProcessor<? super Integer, ? super V> changesProcessor) throws StorageException {
      boolean newValueExists = !newData.isEmpty();
      V newValue = ContainerUtil.getFirstItem(newData.values());
      if (myContainsValue) {
        if (!newValueExists) {
          changesProcessor.removed(myInputId, myInputId);
          return true;
        }
        else if (Comparing.equal(myCurrentValue, newValue)) {
          return false;
        }
        else {
          changesProcessor.updated(myInputId, newValue, myInputId);
          return true;
        }
      }
      else {
        if (newValueExists) {
          changesProcessor.added(myInputId, newValue, myInputId);
          return true;
        }
        else {
          return false;
        }
      }
    }
  }
}
