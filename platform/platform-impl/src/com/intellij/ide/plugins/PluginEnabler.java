// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.join;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;

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

    Set<PluginId> pluginIdsToEnable = mapPluginId(pluginsToEnable);
    LOG.info(getLogMessage(pluginIdsToEnable, true));

    Set<PluginId> pluginIdsToDisable = mapPluginId(pluginsToDisable);
    LOG.info(getLogMessage(pluginIdsToDisable, false));

    boolean requiresRestart =
      updateDisabledPluginsState && !DisabledPluginsState.updateDisabledPluginsState(pluginsToEnable, pluginsToDisable) ||
      !DynamicPlugins.loadUnloadPlugins(pluginsToEnable, pluginsToDisable, project, parentComponent);

    if (requiresRestart) {
      InstalledPluginsState.getInstance().setRestartRequired(true);
    }
    return !requiresRestart;
  }

  public static @NotNull Set<PluginId> mapPluginId(@NotNull List<? extends IdeaPluginDescriptor> descriptors) {
    return descriptors
      .stream()
      .map(IdeaPluginDescriptor::getPluginId)
      .filter(Objects::nonNull)
      .collect(toSet());
  }

  private static @NotNull String getLogMessage(@NotNull Collection<PluginId> plugins,
                                               boolean enable) {
    return getLogMessage(
      "Plugins to " + (enable ? "enable" : "disable"),
      plugins
    );
  }

  public static @NotNull String getLogMessage(@NotNull String message,
                                              @NotNull Collection<PluginId> plugins) {
    StringBuilder buffer = new StringBuilder(message)
      .append(':')
      .append(' ')
      .append('[');

    join(
      plugins,
      PluginId::getIdString,
      ", ",
      buffer
    );
    return buffer.append(']').toString();
  }
}
