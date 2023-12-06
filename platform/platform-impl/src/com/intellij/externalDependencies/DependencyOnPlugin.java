// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalDependencies;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Describes a plugin (and optionally its versions range) which is required for a project to operate normally.
 */
public final class DependencyOnPlugin implements ProjectExternalDependency, Comparable<DependencyOnPlugin> {
  private static final @NonNls String IDE_BUILD_BASELINE_PLACEHOLDER = "<ide.build.baseline>";
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

  public @Nullable @NlsSafe String getRawMinVersion() {
    return myMinVersion;
  }

  public @Nullable @NlsSafe String getRawMaxVersion() {
    return myMaxVersion;
  }

  public @Nullable @NlsSafe String getMinVersion() {
    return preprocessVersionRequirement(myMinVersion);
  }

  public @Nullable @NlsSafe String getMaxVersion() {
    return preprocessVersionRequirement(myMaxVersion);
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

  private static String preprocessVersionRequirement(@NlsSafe String version) {
    String baseline = String.valueOf(ApplicationInfo.getInstance().getBuild().getBaselineVersion());
    return version != null ? version.replace(IDE_BUILD_BASELINE_PLACEHOLDER, baseline) : null;
  }
}
