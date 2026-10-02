// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.durablemap;

import com.intellij.openapi.Forceable;
import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap;
import com.intellij.platform.util.io.storages.durablemap.dev.DurableMapOverBlobStorage;
import com.intellij.util.Processor;
import com.intellij.util.indexing.impl.ChangeTrackingValueContainer;
import com.intellij.util.indexing.impl.UpdatableValueContainer;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import com.intellij.util.io.CleanableStorage;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.MeasurableIndexStore;
import com.intellij.util.io.UnsyncByteArrayOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.BiPredicate;

/**
 * Adapts {@link DurableMap} to the needs of {@link DurableMapIndexStorage}.
 * Right now not so much adaptation is really done -- the implementation is inspired by
 * {@link com.intellij.util.indexing.impl.ValueContainerMap}, but it seems like {@link DurableMap} needs less customization,
 * so in future this class may be 'inlined' into the use-site, and removed.
 */
final class ValueContainerDurableMap<Key, Value> implements Closeable, CleanableStorage, Forceable, MeasurableIndexStore {

  private final @NotNull DurableMap<Key, UpdatableValueContainer<Value>> durableMap;
  private final @NotNull DataExternalizer<Value> valueExternalizer;


  private final boolean keyIsUniqueForIndexedFile;

  /// A patchable map must accept [ChangeTrackingValueContainer] patches encoded by [PatchableValueContainerExternalizer]
  ValueContainerDurableMap(@NotNull DurableMap<Key, UpdatableValueContainer<Value>> durableMap,
                           @NotNull DataExternalizer<Value> externalizer,
                           boolean keyIsUniqueForIndexedFile) {
    this.durableMap = durableMap;
    valueExternalizer = externalizer;
    this.keyIsUniqueForIndexedFile = keyIsUniqueForIndexedFile;
  }

  void merge(Key key,
             ChangeTrackingValueContainer<Value> valueContainer) throws IOException {
    if (!valueContainer.needsCompacting() && !keyIsUniqueForIndexedFile) {

      //TODO RC: this is temporary solution -- in final design it should be either 2 different ValueContainerDurableMap
      //         implementations, one per each variant -- or, more likely, the older DurableMapOverBlobStorage variant,
      //         which was really a prototype, not fully-developed solution -- should be dropped entirely

      if (durableMap instanceof PatchableDurableMap<?, ?, ?>) {
        var patchableMap = (PatchableDurableMap<Key, UpdatableValueContainer<Value>, ChangeTrackingValueContainer<Value>>)durableMap;
        patchableMap.patchValue(key, valueContainer);
        return;
      }

      if (durableMap instanceof DurableMapOverBlobStorage<Key, UpdatableValueContainer<Value>> durableMapOverBlobStorage){
        UnsyncByteArrayOutputStream stream = new UnsyncByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(stream)) {
          valueContainer.saveDiffTo(dos, valueExternalizer);
        }

        DataExternalizerEx.KnownSizeRecordWriter appender = DataExternalizerEx.fromBytes(stream.toByteArraySequence());
        durableMapOverBlobStorage.append(key, appender);
        return;
      }
    }


    durableMap.put(key, valueContainer);
  }

  void put(Key key,
           UpdatableValueContainer<Value> valueContainer) throws IOException {
    durableMap.put(key, valueContainer);
  }

  void remove(Key key) throws IOException {
    durableMap.remove(key);
  }

  boolean processKeys(@NotNull Processor<? super Key> processor) throws IOException {
    return durableMap.processKeys(processor);
  }

  boolean processEntries(@NotNull BiPredicate<? super Key, ? super UpdatableValueContainer<Value>> processor) throws IOException {
    return durableMap.forEachEntry(processor);
  }

  @NotNull
  ChangeTrackingValueContainer<Value> getModifiableValueContainer(Key key) {
    return new ChangeTrackingValueContainer<>(() -> {
      try {
        UpdatableValueContainer<Value> value = durableMap.get(key);
        if (value == null) {
          value = ValueContainerImpl.createNewValueContainer();
        }
        return value;
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  @Override
  public void force() throws IOException {
    durableMap.force();
  }

  @Override
  public boolean isDirty() {
    return durableMap.isDirty();
  }

  @Override
  public void close() throws IOException {
    durableMap.close();
  }

  @Override
  public void closeAndClean() throws IOException {
    durableMap.closeAndClean();
  }

  boolean isClosed() {
    return durableMap.isClosed();
  }

  @Override
  public int keysCountApproximately() {
    try {
      return durableMap.size();
    }
    catch (Exception e) {
      return -1;
    }
  }

  @Override
  public String toString() {
    return "ValueContainerDurableMap[" + durableMap + ']';
  }
}
