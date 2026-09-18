// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.durablemap;

import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.blobstorage.StreamlinedBlobStorageOverMMappedFile;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import com.intellij.platform.util.io.storages.durablemap.DurableMapFactory;
import com.intellij.platform.util.io.storages.durablemap.dev.DurableMapOverBlobStorage;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory;
import com.intellij.util.indexing.impl.UpdatableValueContainer;
import com.intellij.util.indexing.impl.ValueContainerExternalizer;
import com.intellij.util.indexing.impl.ValueContainerInputRemapping;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy.DataLengthPlusFixedPercentStrategy;
import com.intellij.util.io.blobstorage.StreamlinedBlobStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.io.IOUtil.MiB;

final class DurableMapIndexStorageFactories {
  private DurableMapIndexStorageFactories() { }

  static <Key, Value> @NotNull StorageFactory<? extends DurableMap<Key, UpdatableValueContainer<Value>>> fileBasedMapFactory(
    @NotNull KeyDescriptorEx<Key> keyDescriptor,
    @NotNull DataExternalizer<Value> valueExternalizer,
    boolean keyIsUniqueForIndexedFile
  ) {
    return fileBasedMapFactory(keyDescriptor, valueExternalizer, keyIsUniqueForIndexedFile, null);
  }

  static <Key, Value> @NotNull StorageFactory<? extends DurableMap<Key, UpdatableValueContainer<Value>>> fileBasedMapFactory(
    @NotNull KeyDescriptorEx<Key> keyDescriptor,
    @NotNull DataExternalizer<Value> valueExternalizer,
    boolean keyIsUniqueForIndexedFile,
    @Nullable ValueContainerInputRemapping inputRemapping
  ) {
    if (inputRemapping == null) {
      inputRemapping = ValueContainerInputRemapping.IDENTITY;
    }
    var valueContainerExternalizer = new ValueContainerExternalizer<>(valueExternalizer, inputRemapping);
    DataExternalizerEx<UpdatableValueContainer<Value>> adaptedValueExternalizer = LegacyAdapter.adapt(valueContainerExternalizer);

    if (keyIsUniqueForIndexedFile) {
      return DurableMapFactory.withDefaults(keyDescriptor, adaptedValueExternalizer);
    }

    var allocationStrategy = new DataLengthPlusFixedPercentStrategy(
      /*min: */ 24, /*default: */ 64,
      /*max: */ StreamlinedBlobStorageOverMMappedFile.MAX_CAPACITY,
      /*percentOnTop: */ 150
    );
    StorageFactory<? extends StreamlinedBlobStorage> blobStorageFactory = MMappedFileStorageFactory.withDefaults()
      .pageSize(32 * MiB)
      .compose(mappedFileStorage -> new StreamlinedBlobStorageOverMMappedFile(mappedFileStorage, allocationStrategy));
    return DurableMapOverBlobStorage.Factory.defaults(
      blobStorageFactory,
      keyDescriptor,
      adaptedValueExternalizer
    );
  }
}
