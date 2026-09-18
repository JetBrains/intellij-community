// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.durablemap.database;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.platform.util.io.storages.CommonKeyDescriptors;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.database.DurableDatabase;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.impl.ChangeTrackingValueContainer;
import com.intellij.util.indexing.impl.UpdatableValueContainer;
import com.intellij.util.indexing.impl.storage.durablemap.PatchableValueContainerExternalizer;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/** Creates accessors for maps that have a logical index address. */
@ApiStatus.Internal
public final class DurableDatabaseMapAccessors {
  private static final int VALUE_MAP_DATA_VERSION = 1;
  private static final int FORWARD_MAP_DATA_VERSION = 1;

  private DurableDatabaseMapAccessors() {}

  public static <Key, Value>
  @NotNull StorageAccessor<PatchableDurableMap<Key, UpdatableValueContainer<Value>, ChangeTrackingValueContainer<Value>>>
  valueMapAccessor(
    @NotNull DurableDatabase database,
    @NotNull IndexId<?, ?> indexId,
    @NotNull KeyDescriptorEx<Key> keyDescriptor,
    @NotNull DataExternalizer<Value> valueExternalizer
  ) {
    return createValueMapAccessor(database, indexId, /*shardNo: */null, keyDescriptor, valueExternalizer);
  }

  public static <Key, Value>
  @NotNull StorageAccessor<PatchableDurableMap<Key, UpdatableValueContainer<Value>, ChangeTrackingValueContainer<Value>>>
  valueMapAccessor(
    @NotNull DurableDatabase database,
    @NotNull IndexId<?, ?> indexId,
    int shardNo,
    @NotNull KeyDescriptorEx<Key> keyDescriptor,
    @NotNull DataExternalizer<Value> valueExternalizer
  ) {
    checkShardNo(shardNo);
    return createValueMapAccessor(database, indexId, shardNo, keyDescriptor, valueExternalizer);
  }

  public static @NotNull StorageAccessor<DurableMap<Integer, ByteArraySequence>> forwardMapAccessor(
    @NotNull DurableDatabase database,
    @NotNull IndexId<?, ?> indexId
  ) {
    return createForwardMapAccessor(database, indexId, /*shardNo: */null);
  }

  public static @NotNull StorageAccessor<DurableMap<Integer, ByteArraySequence>> forwardMapAccessor(
    @NotNull DurableDatabase database,
    @NotNull IndexId<?, ?> indexId,
    int shardNo
  ) {
    checkShardNo(shardNo);
    return createForwardMapAccessor(database, indexId, shardNo);
  }

  private static <Key, Value>
  @NotNull StorageAccessor<PatchableDurableMap<Key, UpdatableValueContainer<Value>, ChangeTrackingValueContainer<Value>>>
  createValueMapAccessor(
    @NotNull DurableDatabase database,
    @NotNull IndexId<?, ?> indexId,
    @Nullable Integer shardNo,
    @NotNull KeyDescriptorEx<Key> keyDescriptor,
    @NotNull DataExternalizer<Value> valueExternalizer
  ) {
    var mapName = mapName(indexId, MapRole.INVERTED, shardNo);
    var containerExternalizer = new PatchableValueContainerExternalizer<>(valueExternalizer);
    return accessor(
      () -> database.openMap(mapName, VALUE_MAP_DATA_VERSION, keyDescriptor, containerExternalizer),
      () -> database.dropMap(mapName)
    );
  }

  private static @NotNull StorageAccessor<DurableMap<Integer, ByteArraySequence>> createForwardMapAccessor(
    @NotNull DurableDatabase database,
    @NotNull IndexId<?, ?> indexId,
    Integer shardNo
  ) {
    var mapName = mapName(indexId, MapRole.FORWARD, shardNo);
    return accessor(
      () -> database.openMap(mapName, FORWARD_MAP_DATA_VERSION,
                             CommonKeyDescriptors.integer(),
                             CommonKeyDescriptors.byteArraySequence()),
      () -> database.dropMap(mapName)
    );
  }

  private static <M extends DurableMap<?, ?>> @NotNull StorageAccessor<M> accessor(
    @NotNull ThrowableComputable<? extends M, ? extends IOException> mapOpener,
    @NotNull ThrowableRunnable<IOException> mapRemover
  ) {
    return new StorageAccessor<>() {
      @Override
      public @NotNull M open() throws IOException {
        return mapOpener.compute();
      }

      @Override
      public void cleanData() throws IOException {
        mapRemover.run();
      }
    };
  }

  /// Removes all the maps that belongs to the index (currently or in the past)
  public static void cleanIndexData(@NotNull DurableDatabase database, @NotNull IndexId<?, ?> indexId) throws IOException {
    //Removes all maps that may belong to the index, _including_ maps for obsolete shard numbers
    for (var mapName : database.mapNames()) {
      if (belongsToIndex(mapName, indexId.getName())) {
        database.dropMap(mapName);
      }
    }
  }

  private static @NotNull String mapName(@NotNull IndexId<?, ?> indexId, @NotNull MapRole role, Integer shardNo) {
    var indexName = indexId.getName();
    var shard = shardNo == null ? "unsharded" : shardNo.toString();
    return "index:" + indexName.length() + ':' + indexName + ':' + role.id + ':' + shard;
  }

  private static boolean belongsToIndex(@NotNull String mapName, @NotNull String indexName) {
    var namespace = "index:" + indexName.length() + ':' + indexName + ':';
    if (!mapName.startsWith(namespace)) {
      return false;
    }

    var address = mapName.substring(namespace.length());
    for (var role : MapRole.values()) {
      var rolePrefix = role.id + ':';
      if (address.startsWith(rolePrefix)) {
        return isShardId(address.substring(rolePrefix.length()));
      }
    }
    return false;
  }

  private static boolean isShardId(@NotNull String shardId) {
    if (shardId.equals("unsharded")) {
      return true;
    }
    try {
      var shardNo = Integer.parseInt(shardId);
      return shardNo >= 0 && Integer.toString(shardNo).equals(shardId);
    }
    catch (NumberFormatException ignored) {
      return false;
    }
  }

  private static void checkShardNo(int shardNo) {
    if (shardNo < 0) {
      throw new IllegalArgumentException("The shard number must not be negative: " + shardNo);
    }
  }

  private enum MapRole {
    INVERTED("inverted"),
    FORWARD("forward");

    private final @NotNull String id;

    MapRole(@NotNull String id) {
      this.id = id;
    }
  }
}
