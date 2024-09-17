// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.util.CommonProcessors;
import com.intellij.util.io.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;

public abstract class AbstractStateStorage<Key, T> implements StorageOwner {
  private static final boolean DO_COMPRESS = Boolean.parseBoolean(System.getProperty("jps.storage.do.compression", "true"));

  protected final Object dataLock = new Object();
  private final @NotNull PersistentMapBuilder<Key, T> mapBuilder;
  private @NotNull PersistentMapImpl<Key, T> map;
  private final boolean isCompressed;

  public AbstractStateStorage(File storePath, KeyDescriptor<Key> keyDescriptor, DataExternalizer<T> stateExternalizer) throws IOException {
    this(PersistentMapBuilder.newBuilder(storePath.toPath(), keyDescriptor, stateExternalizer), DO_COMPRESS);
  }

  @ApiStatus.Internal
  protected AbstractStateStorage(@NotNull PersistentMapBuilder<Key, T> mapBuilder) throws IOException {
    this(mapBuilder, DO_COMPRESS);
  }

  @ApiStatus.Internal
  protected AbstractStateStorage(@NotNull PersistentMapBuilder<Key, T> mapBuilder, boolean isCompressed) throws IOException {
    this.isCompressed = isCompressed;
    this.mapBuilder = mapBuilder;
    map = createMap();
  }

  public final void force() {
    synchronized (dataLock) {
      try {
        map.force();
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @Override
  public final void close() throws IOException {
    synchronized (dataLock) {
      map.close();
    }
  }

  @Override
  public final void clean() throws IOException {
    wipe();
  }

  @SuppressWarnings("UnusedReturnValue")
  public final boolean wipe() {
    synchronized (dataLock) {
      map.closeAndDelete();
      try {
        map = createMap();
      }
      catch (IOException ignored) {
        return false;
      }
      return true;
    }
  }

  public void update(Key key, @Nullable T state) throws IOException {
    if (state != null) {
      synchronized (dataLock) {
        map.put(key, state);
      }
    }
    else {
      remove(key);
    }
  }

  public void appendData(final Key key, final T data) throws IOException {
    synchronized (dataLock) {
      map.appendData(key, out -> mapBuilder.getValueExternalizer().save(out, data));
    }
  }

  public void remove(Key key) throws IOException {
    synchronized (dataLock) {
      map.remove(key);
    }
  }

  public @Nullable T getState(Key key) throws IOException {
    synchronized (dataLock) {
      return map.get(key);
    }
  }

  /**
   * @deprecated Use {@link #getKeysIterator()}
   */
  @TestOnly
  @ApiStatus.Internal
  @Deprecated(forRemoval = true)
  public final Collection<Key> getKeys() throws IOException {
    return getAllKeys();
  }

  public @NotNull Iterator<Key> getKeysIterator() throws IOException {
    //noinspection TestOnlyProblems
    return getAllKeys().iterator();
  }

  @TestOnly
  @ApiStatus.Internal
  public final @NotNull List<Key> getAllKeys() throws IOException {
    synchronized (dataLock) {
      List<Key> result = new ArrayList<>();
      map.processExistingKeys(new CommonProcessors.CollectProcessor<>(result));
      return result.isEmpty() ? List.of() : result;
    }
  }

  protected final @NotNull Iterator<Key> getKeyIterator(@NotNull Function<Key, Key> mapper) throws IOException {
    synchronized (dataLock) {
      List<Key> result = new ArrayList<>();
      map.processExistingKeys(key -> {
        result.add(mapper.apply(key));
        return true;
      });
      return result.isEmpty() ? Collections.emptyIterator() : result.iterator();
    }
  }

  private @NotNull PersistentMapImpl<Key, T> createMap() throws IOException {
    Files.createDirectories(mapBuilder.getFile().getParent());
    return new PersistentMapImpl<>(mapBuilder, new PersistentHashMapValueStorage.CreationTimeOptions(false, false, false, isCompressed));
  }

  @Override
  public final void flush(boolean memoryCachesOnly) {
    if (!memoryCachesOnly) {
      force();
    }
  }
}
