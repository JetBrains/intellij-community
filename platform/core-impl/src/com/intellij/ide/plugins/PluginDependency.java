// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PluginDependency implements IdeaPluginDependency {
  public final PluginId id;
  public boolean isOptional;

  public String configFile;

  transient final boolean isDisabledOrBroken;
  // cleared as part of mergeOptionalDescriptors
  @Nullable transient IdeaPluginDescriptorImpl subDescriptor;

  PluginDependency(@NotNull PluginId id, @Nullable String configFile, boolean isDisabledOrBroken) {
    this.id = id;
    this.configFile = configFile;
    this.isDisabledOrBroken = isDisabledOrBroken;
  }

  @Override
  public PluginId getPluginId() {
    return id;
  }

  @Override
  public boolean isOptional() {
    return isOptional;
  }

  @Override
  public String toString() {
    return "PluginDependency(" +
           "id=" + id +
           ", isOptional=" + isOptional +
           ", configFile=" + configFile +
           ", isDisabledOrBroken=" + isDisabledOrBroken +
           ", subDescriptor=" + subDescriptor +
           ')';
  }
}
