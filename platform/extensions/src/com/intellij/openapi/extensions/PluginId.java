/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.extensions;

import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author max
 */
public class PluginId implements Comparable<PluginId> {
  public static final PluginId[] EMPTY_ARRAY = new PluginId[0];

  private static final Map<String, PluginId> ourRegisteredIds = new THashMap<String, PluginId>();

  private final String myIdString;

  private PluginId(@NotNull String idString) {
    myIdString = idString;
  }

  @Override
  public int compareTo(@NotNull PluginId o) {
    return myIdString.compareTo(o.myIdString);
  }

  @NotNull
  public static synchronized PluginId getId(@NotNull String idString) {
    PluginId pluginId = ourRegisteredIds.get(idString);
    if (pluginId == null) {
      pluginId = new PluginId(idString);
      ourRegisteredIds.put(idString, pluginId);
    }
    return pluginId;
  }

  @Nullable
  public static synchronized PluginId findId(@NotNull String... idStrings) {
    for (String idString : idStrings) {
      PluginId pluginId = ourRegisteredIds.get(idString);
      if (pluginId != null) {
        return pluginId;
      }
    }
    return null;
  }

  @NonNls
  @NotNull
  public String getIdString() {
    return myIdString;
  }

  @Override
  public String toString() {
    return getIdString();
  }

  @NotNull
  public static synchronized Map<String, PluginId> getRegisteredIds() {
    return new THashMap<String, PluginId>(ourRegisteredIds);
  }
}
