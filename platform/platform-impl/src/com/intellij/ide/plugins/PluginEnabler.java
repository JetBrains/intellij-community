// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.join;
import static java.util.Collections.emptyList;

/**
 * @author yole
 */
public final class PluginEnabler {
  private static final Logger LOG = Logger.getInstance(PluginEnabler.class);

  private PluginEnabler() {
  }

  public static boolean enablePlugins(@Nullable Project project,
                                      @NotNull List<? extends IdeaPluginDescriptor> plugins,
                                      boolean enable) {
    return updatePluginEnabledState(
      project,
      enable ? plugins : emptyList(),
      enable ? emptyList() : plugins,
      null
    );
  }

  /**
   * @return true if the requested enabled state was applied without restart, false if restart is required
   */
  public static boolean updatePluginEnabledState(@Nullable Project project,
                                                 @NotNull List<? extends IdeaPluginDescriptor> pluginsToEnable,
                                                 @NotNull List<? extends IdeaPluginDescriptor> pluginsToDisable,
                                                 @Nullable JComponent parentComponent) {
    if (pluginsToEnable.isEmpty() &&
        pluginsToDisable.isEmpty()) {
      return true;
    }
    LOG.info(getLogMessage(pluginsToEnable, true));
    LOG.info(getLogMessage(pluginsToDisable, false));

    ProjectPluginTracker pluginTracker = project != null ?
                                         ProjectPluginTracker.getInstance(project) :
                                         null;

    Set<PluginId> disabledIds = DisabledPluginsState.getDisabledIds();

    for (IdeaPluginDescriptor descriptor : pluginsToEnable) {
      PluginId pluginId = descriptor.getPluginId();
      if (pluginTracker == null ||
          !pluginTracker.isEnabled(pluginId)) {
        descriptor.setEnabled(true);
        disabledIds.remove(pluginId);
      }
    }

    for (IdeaPluginDescriptor descriptor : pluginsToDisable) {
      PluginId pluginId = descriptor.getPluginId();
      if (pluginTracker == null ||
          !pluginTracker.isDisabled(pluginId)) {
        descriptor.setEnabled(false);
        disabledIds.add(pluginId);
      }
    }

    try {
      DisabledPluginsState.saveDisabledPlugins(disabledIds, false);
    }
    catch (IOException e) {
      LOG.error(e);
    }

    boolean applied = DynamicPlugins.loadUnloadPlugins(
      pluginsToEnable,
      pluginsToDisable,
      project,
      parentComponent
    );

    if (!applied) {
      InstalledPluginsState.getInstance().setRestartRequired(true);
    }
    return applied;
  }

  private static @NotNull String getLogMessage(@NotNull List<? extends IdeaPluginDescriptor> pluginsToEnable,
                                               boolean enable) {
    StringBuilder buffer = new StringBuilder("Plugins to ")
      .append(enable ? "enable" : "disable")
      .append(' ')
      .append('[');
    join(
      pluginsToEnable,
      descriptor -> descriptor.getPluginId().getIdString(),
      ", ",
      buffer
    );
    return buffer.append(']').toString();
  }
}
