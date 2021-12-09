// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.ReviseWhenPortedToJDK;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an ID of a plugin. A full descriptor of the plugin may be obtained
 * via {@link com.intellij.ide.plugins.PluginManagerCore#getPlugin(PluginId)} method.
 */
public final class PluginId implements Comparable<PluginId> {
  public static final PluginId[] EMPTY_ARRAY = new PluginId[0];

  private static final Map<String, PluginId> registeredIds = new ConcurrentHashMap<>();

  public static @NotNull PluginId getId(@NotNull String idString) {
    return registeredIds.computeIfAbsent(idString, PluginId::new);
  }

  public static @Nullable PluginId findId(@NotNull String idString) {
    return registeredIds.get(idString);
  }

  public static @Nullable PluginId findId(String @NotNull ... idStrings) {
    for (String idString : idStrings) {
      PluginId pluginId = registeredIds.get(idString);
      if (pluginId != null) {
        return pluginId;
      }
    }
    return null;
  }

  @ReviseWhenPortedToJDK(value = "10", description = "Collectors.toUnmodifiableSet()")
  public static @NotNull Set<PluginId> getRegisteredIds() {
    return Collections.unmodifiableSet(new HashSet<>(registeredIds.values()));
  }

  private final @NotNull String idString;

  private PluginId(@NotNull String idString) {
    this.idString = idString;
  }

  public @NotNull String getIdString() {
    return idString;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PluginId)) return false;

    PluginId pluginId = (PluginId)o;
    return idString.equals(pluginId.idString);
  }

  @Override
  public int hashCode() {
    return idString.hashCode();
  }

  @Override
  public int compareTo(@NotNull PluginId o) {
    return idString.compareTo(o.idString);
  }

  @Override
  public String toString() {
    return idString;
  }
}
