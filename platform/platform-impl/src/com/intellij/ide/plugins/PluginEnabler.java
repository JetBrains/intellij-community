// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

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
      null,
      true
    );
  }

  /**
   * @return true if the requested enabled state was applied without restart, false if restart is required
   */
  public static boolean updatePluginEnabledState(@Nullable Project project,
                                                 @NotNull List<? extends IdeaPluginDescriptor> pluginsToEnable,
                                                 @NotNull List<? extends IdeaPluginDescriptor> pluginsToDisable,
                                                 @Nullable JComponent parentComponent,
                                                 boolean updateDisabledPluginsState) {
    if (pluginsToEnable.isEmpty() &&
        pluginsToDisable.isEmpty()) {
      return true;
    }
    LOG.info(getLogMessage(pluginsToEnable, true));
    LOG.info(getLogMessage(pluginsToDisable, false));

    boolean requiresRestart =
      updateDisabledPluginsState && !DisabledPluginsState.updateDisabledPluginsState(pluginsToEnable, pluginsToDisable) ||
      !DynamicPlugins.loadUnloadPlugins(pluginsToEnable, pluginsToDisable, project, parentComponent);

    if (requiresRestart) {
      InstalledPluginsState.getInstance().setRestartRequired(true);
    }
    return !requiresRestart;
  }

  private static @NotNull String getLogMessage(@NotNull List<? extends IdeaPluginDescriptor> pluginsToEnable,
                                               boolean enable) {
    return getLogMessage(
      "Plugins to " + (enable ? "enable" : "disable"),
      pluginsToEnable
    );
  }

  public static @NotNull String getLogMessage(@NotNull String message,
                                              @NotNull List<? extends IdeaPluginDescriptor> pluginsToEnable) {
    StringBuilder buffer = new StringBuilder(message)
      .append(':')
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
