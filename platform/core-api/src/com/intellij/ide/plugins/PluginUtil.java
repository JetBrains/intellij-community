// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PluginUtil {
  static PluginUtil getInstance() {
    return ApplicationManager.getApplication().getService(PluginUtil.class);
  }

  @Nullable PluginId findPluginId(@NotNull Throwable t);

  @Nullable @Nls String findPluginName(@NotNull PluginId pluginId);

  @Internal
  static @NotNull PluginId getPluginId(@NotNull ClassLoader classLoader) {
    if (classLoader instanceof PluginAwareClassLoader) {
      return ((PluginAwareClassLoader)classLoader).getPluginId();
    }
    return PluginId.getId("com.intellij"); // com.intellij.ide.plugins.PluginManagerCore.CORE_ID
  }
}
