// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.*;

/**
 * @author yole
 */
public final class PluginEnabler {
  private static final Logger LOG = Logger.getInstance(PluginEnabler.class);

  public static boolean enablePlugins(@Nullable Project project, Collection<IdeaPluginDescriptor> plugins, boolean enable) {
    return updatePluginEnabledState(project, enable ? plugins : Collections.emptyList(),
                                    enable ? Collections.emptyList() : plugins,
                                    null);
  }

  /**
   * @return true if the requested enabled state was applied without restart, false if restart is required
   */
  public static boolean updatePluginEnabledState(@Nullable Project project,
                                                 Collection<IdeaPluginDescriptor> pluginsToEnable,
                                                 Collection<IdeaPluginDescriptor> pluginsToDisable,
                                                 @Nullable JComponent parentComponent) {
    List<IdeaPluginDescriptorImpl> pluginDescriptorsToEnable = loadFullDescriptors(pluginsToEnable);
    List<IdeaPluginDescriptorImpl> pluginDescriptorsToDisable = loadFullDescriptors(pluginsToDisable);

    Set<PluginId> disabledIds = DisabledPluginsState.getDisabledIds();
    for (PluginDescriptor descriptor : pluginsToEnable) {
      descriptor.setEnabled(true);
      disabledIds.remove(descriptor.getPluginId());
    }
    for (PluginDescriptor descriptor : pluginsToDisable) {
      if (!PluginManagerCore.getLoadedPlugins().contains(descriptor)) {
        // don't try to unload plugin which wasn't loaded
        pluginDescriptorsToDisable.removeIf(plugin -> plugin.getPluginId().equals(descriptor.getPluginId()));
      }
      descriptor.setEnabled(false);
      disabledIds.add(descriptor.getPluginId());
    }

    try {
      DisabledPluginsState.saveDisabledPlugins(disabledIds, false);
    }
    catch (IOException e) {
      LOG.error(e);
    }

    if (DynamicPlugins.allowLoadUnloadAllWithoutRestart(pluginDescriptorsToDisable) &&
        DynamicPlugins.allowLoadUnloadAllWithoutRestart(pluginDescriptorsToEnable)) {

      List<IdeaPluginDescriptorImpl> sortedDescriptorsToDisable = PluginManagerCore.getPluginsSortedByDependency(pluginDescriptorsToDisable, true);
      Collections.reverse(sortedDescriptorsToDisable);
      boolean needRestart = false;
      for (IdeaPluginDescriptorImpl descriptor : sortedDescriptorsToDisable) {
        if (!DynamicPlugins.unloadPluginWithProgress(project, parentComponent, descriptor, new DynamicPlugins.UnloadPluginOptions().withDisable(true))) {
          needRestart = true;
          break;
        }
      }

      if (!needRestart) {
        List<IdeaPluginDescriptorImpl> sortedDescriptorsToEnable = PluginManagerCore.getPluginsSortedByDependency(pluginDescriptorsToEnable, true);
        for (IdeaPluginDescriptor descriptor : sortedDescriptorsToEnable) {
          if (!DynamicPlugins.loadPlugin((IdeaPluginDescriptorImpl)descriptor)) {
            needRestart = true;
            break;
          }
        }
        if (!needRestart) {
          return true;
        }
      }
    }
    InstalledPluginsState.getInstance().setRestartRequired(true);
    return false;
  }

  private static List<IdeaPluginDescriptorImpl> loadFullDescriptors(Collection<IdeaPluginDescriptor> pluginsToEnable) {
    List<IdeaPluginDescriptorImpl> result = new ArrayList<>();
    for (IdeaPluginDescriptor descriptor : pluginsToEnable) {
      if (descriptor instanceof IdeaPluginDescriptorImpl) {
        result.add(PluginDescriptorLoader.loadFullDescriptor((IdeaPluginDescriptorImpl) descriptor));
      }
    }
    return result;
  }
}
