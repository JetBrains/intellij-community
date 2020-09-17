// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class PluginManagerConfigurableServiceImpl implements PluginManagerConfigurableService {
  @Override
  public void showPluginConfigurableAndEnable(@Nullable Project project,
                                              PluginId @NotNull ... plugins) {
    IdeaPluginDescriptor[] descriptors = new IdeaPluginDescriptor[plugins.length];
    for (int i = 0; i < plugins.length; i++) {
      IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(plugins[i]);
      if (descriptor == null) {
        throw new IllegalArgumentException("Plugin " + plugins[i] + " not found");
      }
      descriptors[i] = descriptor;
    }
    PluginManagerConfigurable configurable = new PluginManagerConfigurable(project);
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable, () -> {
      configurable.getPluginModel()
        .enablePlugins(Set.of(descriptors));
      configurable.select(descriptors);
    });
  }
}
