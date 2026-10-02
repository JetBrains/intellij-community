// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.durablemap;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.io.MeasurableIndexStore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The implementation thread-safety relies on {@link DurableMap} implementation used to be a thread-safe
 * (Which is true for the currently existing impls)
 */
@ApiStatus.Internal
public class DurableMapBasedForwardIndex implements ForwardIndex, MeasurableIndexStore {

  private final @NotNull ThrowableComputable<? extends DurableMap<Integer, ByteArraySequence>, ? extends IOException> mapOpener;

  private volatile @NotNull DurableMap<Integer, ByteArraySequence> durableMap;


  public DurableMapBasedForwardIndex(@NotNull Path mapFile,
                                     @NotNull StorageFactory<? extends DurableMap<Integer, ByteArraySequence>> factory) throws IOException {
    this(() -> factory.open(mapFile));
  }

  public DurableMapBasedForwardIndex(
    @NotNull ThrowableComputable<? extends DurableMap<Integer, ByteArraySequence>, ? extends IOException> mapOpener
  ) throws IOException {
    this.mapOpener = mapOpener;
    durableMap = mapOpener.compute();
  }

  @Override
  public @Nullable ByteArraySequence get(@NotNull Integer key) throws IOException {
    return durableMap.get(key);
  }

  @Override
  public void put(@NotNull Integer key,
                  @Nullable ByteArraySequence value) throws IOException {
    if (value == null) {
      //MAYBE: durableMap.put(key, null) is the same as .remove() for current implementation
      durableMap.remove(key);
    }
    else {
      durableMap.put(key, value);
    }
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
  public int keysCountApproximately() {
    return MeasurableIndexStore.keysCountApproximatelyIfPossible(durableMap);
  }

  @Override
  public void clear() throws IOException {
    durableMap.closeAndClean();
    durableMap = mapOpener.compute();
  }

  @Override
  public void close() throws IOException {
    durableMap.close();
  }

  @Override
  public boolean isClosed() {
    return durableMap.isClosed();
  }

  public boolean containsMapping(int key) throws IOException {
    return durableMap.containsMapping(key);
  }

  public DurableMap<Integer, ByteArraySequence> getUnderlyingMap() {
    return durableMap;
  }
}
