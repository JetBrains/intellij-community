// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Represents an ID of a plugin. A full descriptor of the plugin may be obtained via {@link com.intellij.ide.plugins.PluginManagerCore#getPlugin(PluginId)} method.
 */
public final class PluginId implements Comparable<PluginId> {
  public static final PluginId[] EMPTY_ARRAY = new PluginId[0];

  private static final Map<String, PluginId> ourRegisteredIds = new THashMap<>();

  @NotNull
  public static synchronized PluginId getId(@NotNull String idString) {
    return ourRegisteredIds.computeIfAbsent(idString, PluginId::new);
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

  @NotNull
  public static synchronized Map<String, PluginId> getRegisteredIds() {
    return new THashMap<>(ourRegisteredIds);
  }

  private final String myIdString;

  private PluginId(@NotNull String idString) {
    myIdString = idString;
  }

  @NotNull
  public String getIdString() {
    return myIdString;
  }

  @Override
  public int compareTo(@NotNull PluginId o) {
    return myIdString.compareTo(o.myIdString);
  }

  @Override
  public String toString() {
    return getIdString();
  }
}