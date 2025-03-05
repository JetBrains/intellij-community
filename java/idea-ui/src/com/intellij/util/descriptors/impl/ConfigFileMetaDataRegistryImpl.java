// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.descriptors.impl;

import com.intellij.util.descriptors.ConfigFileMetaData;
import com.intellij.util.descriptors.ConfigFileMetaDataRegistry;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConfigFileMetaDataRegistryImpl implements ConfigFileMetaDataRegistry {
  private final List<ConfigFileMetaData> myMetaData = new ArrayList<>();
  private final Map<String, ConfigFileMetaData> myId2MetaData = new HashMap<>();
  private ConfigFileMetaData[] myCachedMetaData;

  public ConfigFileMetaDataRegistryImpl() {
  }

  public ConfigFileMetaDataRegistryImpl(ConfigFileMetaData[] metadataList) {
    for (ConfigFileMetaData metaData : metadataList) {
      registerMetaData(metaData);
    }
  }

  @Override
  public ConfigFileMetaData @NotNull [] getMetaData() {
    if (myCachedMetaData == null) {
      myCachedMetaData = myMetaData.toArray(new ConfigFileMetaData[0]);
    }
    return myCachedMetaData;
  }

  @Override
  public @Nullable ConfigFileMetaData findMetaData(final @NonNls @NotNull String id) {
    return myId2MetaData.get(id);
  }

  @Override
  public void registerMetaData(final ConfigFileMetaData @NotNull ... metaData) {
    for (ConfigFileMetaData data : metaData) {
      myMetaData.add(data);
      myId2MetaData.put(data.getId(), data);
    }
    myCachedMetaData = null;
  }

  @Override
  public void unregisterMetaData(@NotNull ConfigFileMetaData metaData) {
    boolean changed = myMetaData.remove(metaData);
    ConfigFileMetaData actual = myId2MetaData.remove(metaData.getId());
    changed |= (actual != null);
    if (changed) {
      myCachedMetaData = null;
    }
  }
}
