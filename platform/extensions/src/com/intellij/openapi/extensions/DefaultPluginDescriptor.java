// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NotNull;

public final class DefaultPluginDescriptor implements PluginDescriptor {
  @NotNull
  private final PluginId myPluginId;
  private final ClassLoader myPluginClassLoader;

  public DefaultPluginDescriptor(@NotNull String pluginId) {
    myPluginId = PluginId.getId(pluginId);
    myPluginClassLoader = null;
  }

  public DefaultPluginDescriptor(@NotNull PluginId pluginId) {
    myPluginId = pluginId;
    myPluginClassLoader = null;
  }

  public DefaultPluginDescriptor(@NotNull PluginId pluginId, final ClassLoader pluginClassLoader) {
    myPluginId = pluginId;
    myPluginClassLoader = pluginClassLoader;
  }

  @Override
  @NotNull
  public PluginId getPluginId() {
    return myPluginId;
  }

  @Override
  public ClassLoader getPluginClassLoader() {
    return myPluginClassLoader;
  }
}
