// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;

import static com.intellij.util.containers.ContainerUtil.map;

/**
 * @deprecated Please use {@link PluginManagerConfigurable} directly.
 */
@Deprecated(since = "2020.2", forRemoval = true)
public final class PluginManagerConfigurableProxy {
  private PluginManagerConfigurableProxy() {
  }

  /**
   * @deprecated Please use {@link PluginManagerConfigurable#showPluginConfigurable(Component, Project, Collection)}.
   */
  @Deprecated(since = "2020.2", forRemoval = true)
  public static void showPluginConfigurable(@Nullable Component parent,
                                            @Nullable Project project,
                                            IdeaPluginDescriptor @NotNull ... descriptors) {
    PluginManagerConfigurable.showPluginConfigurable(parent,
                                                     project,
                                                     map(descriptors, IdeaPluginDescriptor::getPluginId));
  }
}
