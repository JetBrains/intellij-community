/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

public class ConfigFileMetaDataRegistryImpl implements ConfigFileMetaDataRegistry {
  private final List<ConfigFileMetaData> myMetaData = new ArrayList<>();
  private final Map<String, ConfigFileMetaData> myId2MetaData = new HashMap<>();
  private ConfigFileMetaData[] myCachedMetaData;

  public ConfigFileMetaDataRegistryImpl() {
  }

  public ConfigFileMetaDataRegistryImpl(ConfigFileMetaData[] metaDatas) {
    for (ConfigFileMetaData metaData : metaDatas) {
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
  @Nullable
  public ConfigFileMetaData findMetaData(@NonNls @NotNull final String id) {
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
