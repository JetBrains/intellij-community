// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.externalDependencies;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.ContainerUtil;
import java.util.Arrays;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes a plugin (and optionally its versions range) which is required for a project to operate normally.
 */
public class DependencyOnPlugin implements ProjectExternalDependency, Comparable<DependencyOnPlugin> {
  private final String myPluginId;
  private final String myMinVersion;
  private final String myMaxVersion;

  /**
   * @deprecated use {@link #DependencyOnPlugin(String, String, String)} instead
   */
  @Deprecated
  public DependencyOnPlugin(@NotNull String pluginId, @Nullable String minVersion, @Nullable String maxVersion, @Nullable String channel) {
    this(pluginId, minVersion, maxVersion);
  }

  public DependencyOnPlugin(@NotNull String pluginId, @Nullable String minVersion, @Nullable String maxVersion) {
    myPluginId = pluginId;
    myMinVersion = minVersion;
    myMaxVersion = maxVersion;
  }

  public String getPluginId() {
    return myPluginId;
  }

  public String getMinVersion() {
    return myMinVersion;
  }

  public String getMaxVersion() {
    return myMaxVersion;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DependencyOnPlugin plugin = (DependencyOnPlugin)o;

    return myPluginId.equals(plugin.myPluginId)
           && Objects.equals(myMinVersion, plugin.myMinVersion)
           && Objects.equals(myMaxVersion, plugin.myMaxVersion);
  }

  @Override
  public int hashCode() {
    return 31 * (31 * myPluginId.hashCode() + Comparing.hashcode(myMinVersion)) + Comparing.hashcode(myMaxVersion);
  }

  @Override
  public int compareTo(DependencyOnPlugin o) {
    return ContainerUtil.compareLexicographically(Arrays.asList(myPluginId, myMinVersion, myMaxVersion),
                                                  Arrays.asList(o.myPluginId, o.myMinVersion, o.myMaxVersion));
  }
}
