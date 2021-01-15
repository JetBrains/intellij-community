// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.externalDependencies;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Describes a plugin (and optionally its versions range) which is required for a project to operate normally.
 */
public class DependencyOnPlugin implements ProjectExternalDependency, Comparable<DependencyOnPlugin> {
  private final String myPluginId;
  private final @NlsSafe String myMinVersion;
  private final @NlsSafe String myMaxVersion;

  public DependencyOnPlugin(@NotNull String pluginId, @NlsSafe @Nullable String minVersion, @NlsSafe @Nullable String maxVersion) {
    myPluginId = pluginId;
    myMinVersion = minVersion;
    myMaxVersion = maxVersion;
  }

  public @NlsSafe String getPluginId() {
    return myPluginId;
  }

  public @NlsSafe String getMinVersion() {
    return myMinVersion;
  }

  public @NlsSafe String getMaxVersion() {
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
