// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import kotlin.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class PluginUtilImpl implements PluginUtil {
  @Override
  public @Nullable PluginId findPluginId(@NotNull Throwable t) {
    PluginSet pluginSet = PluginManagerCore.getPluginSetOrNull();
    Pair<PluginId, IdeaPluginDescriptorImpl> pair = PluginUtils.findPlugin(t, pluginSet);
    return pair == null ? null : pair.getFirst();
  }

  @Override
  public @Nls @Nullable String findPluginName(@NotNull PluginId pluginId) {
    IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(pluginId);
    return plugin == null ? null : plugin.getName();
  }
}
