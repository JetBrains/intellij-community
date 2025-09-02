// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Describes a plugin which may be installed into IntelliJ-based IDE. Use {@link com.intellij.ide.plugins.PluginManagerCore#getPlugin(PluginId)}
 * to get a descriptor by a plugin ID and {@link com.intellij.ide.plugins.PluginManagerCore#getPlugins()} to get all plugins.
 */
@ApiStatus.NonExtendable
public interface IdeaPluginDescriptor extends PluginDescriptor {
  /**
   * aka {@code <depends>} elements from the plugin.xml
   */
  @NotNull List<IdeaPluginDependency> getDependencies();

  /**
   * Path to the descriptor file relative to the plugin location
   */
  @Nullable String getDescriptorPath();
}