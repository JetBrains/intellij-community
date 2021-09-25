// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

// todo merge into PluginSetState?
public final class PluginManagerState {
  final Set<PluginId> effectiveDisabledIds;
  final Set<PluginId> disabledRequiredIds;
  final PluginSet pluginSet;

  PluginManagerState(@NotNull PluginSet pluginSet,
                     @NotNull Set<PluginId> disabledRequiredIds,
                     @NotNull Set<PluginId> effectiveDisabledIds) {
    this.pluginSet = pluginSet;
    this.disabledRequiredIds = disabledRequiredIds;
    this.effectiveDisabledIds = effectiveDisabledIds;
  }
}
