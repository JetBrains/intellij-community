// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PluginManagerState {
  final Set<PluginId> effectiveDisabledIds;
  final Set<PluginId> disabledRequiredIds;
  final IdeaPluginDescriptorImpl @NotNull [] sortedPlugins;
  final List<IdeaPluginDescriptorImpl> sortedEnabledPlugins;

  final Map<PluginId, IdeaPluginDescriptorImpl> idMap;

  PluginManagerState(@NotNull IdeaPluginDescriptorImpl @NotNull [] sortedPlugins,
                     @NotNull List<IdeaPluginDescriptorImpl> sortedEnabledPlugins,
                     @NotNull Set<PluginId> disabledRequiredIds,
                     @NotNull Set<PluginId> effectiveDisabledIds,
                     @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idMap) {
    this.sortedPlugins = sortedPlugins;
    this.sortedEnabledPlugins = sortedEnabledPlugins;
    this.disabledRequiredIds = disabledRequiredIds;
    this.effectiveDisabledIds = effectiveDisabledIds;
    this.idMap = idMap;
  }
}
