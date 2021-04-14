// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an ID of a plugin. A full descriptor of the plugin may be obtained
 * via {@link com.intellij.ide.plugins.PluginManagerCore#getPlugin(PluginId)} method.
 */
public final class PluginId implements Comparable<PluginId> {
  public static final PluginId[] EMPTY_ARRAY = new PluginId[0];

  private static final ConcurrentHashMap<String, PluginId> registeredIds = new ConcurrentHashMap<>();

  public static @NotNull PluginId getId(@NotNull String idString) {
    return registeredIds.computeIfAbsent(idString, PluginId::new);
  }

  public static synchronized @Nullable PluginId findId(String @NotNull ... idStrings) {
    for (String idString : idStrings) {
      PluginId pluginId = registeredIds.get(idString);
      if (pluginId != null) {
        return pluginId;
      }
    }
    return null;
  }

  public static @NotNull Collection<PluginId> getRegisteredIdList() {
    return new ArrayList<>(registeredIds.values());
  }

  private final String idString;

  private PluginId(@NotNull String idString) {
    this.idString = idString;
  }

  public @NotNull String getIdString() {
    return idString;
  }

  @Override
  public int compareTo(@NotNull PluginId o) {
    return idString.compareTo(o.idString);
  }

  @Override
  public String toString() {
    return getIdString();
  }
}
