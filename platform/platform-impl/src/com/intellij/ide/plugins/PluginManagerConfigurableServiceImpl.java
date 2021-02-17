// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;

final class PluginManagerConfigurableServiceImpl implements PluginManagerConfigurableService {

  @Override
  public void showPluginConfigurableAndEnable(@Nullable Project project,
                                              String @NotNull ... plugins) {
    Map<PluginId, IdeaPluginDescriptorImpl> descriptorsById = PluginManagerCore.buildPluginIdMap();
    LinkedHashSet<IdeaPluginDescriptor> descriptors = new LinkedHashSet<>();
    for (String plugin : plugins) {
      IdeaPluginDescriptor descriptor = descriptorsById.get(PluginId.getId(plugin));
      if (descriptor == null) {
        throw new IllegalArgumentException("Plugin " + plugin + " not found");
      }
      descriptors.add(descriptor);
    }

    PluginManagerConfigurable.showPluginConfigurableAndEnable(project, descriptors);
  }
}
