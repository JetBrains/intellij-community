// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * Represents an ID of a plugin. A full descriptor of the plugin may be obtained via {@link com.intellij.ide.plugins.PluginManagerCore#getPlugin(PluginId)} method.
 */
public final class PluginId implements Comparable<PluginId> {
  public static final PluginId[] EMPTY_ARRAY = new PluginId[0];

  private static final Map<String, PluginId> ourRegisteredIds = CollectionFactory.createMap();

  public static synchronized @NotNull PluginId getId(@NotNull String idString) {
    return ourRegisteredIds.computeIfAbsent(idString, PluginId::new);
  }

  public static synchronized @Nullable PluginId findId(String @NotNull ... idStrings) {
    for (String idString : idStrings) {
      PluginId pluginId = ourRegisteredIds.get(idString);
      if (pluginId != null) {
        return pluginId;
      }
    }
    return null;
  }

  /**
   * @deprecated Use {@link #getRegisteredIdList}.
   */
  @Deprecated
  public static synchronized @NotNull Map<String, PluginId> getRegisteredIds() {
    return CollectionFactory.createMap(ourRegisteredIds);
  }

  public static synchronized @NotNull Collection<PluginId> getRegisteredIdList() {
    return new ArrayList<>(ourRegisteredIds.values());
  }

  private final String myIdString;

  private PluginId(@NotNull String idString) {
    myIdString = idString;
  }

  public @NotNull String getIdString() {
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