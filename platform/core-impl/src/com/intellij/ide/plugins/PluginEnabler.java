// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

@ApiStatus.Experimental
public interface PluginEnabler {
  interface Headless extends PluginEnabler {
    boolean isIgnoredDisabledPlugins();

    void setIgnoredDisabledPlugins(boolean ignoredDisabledPlugins);
  }

  /**
   * Manages the persistent disabled/enabled flag of plugins, does not load/unload plugins.
   */
  Headless HEADLESS = new DisabledPluginsState();

  static @NotNull PluginEnabler getInstance() {
    if (!LoadingState.COMPONENTS_LOADED.isOccurred()) {
      return HEADLESS;
    }

    Application application = ApplicationManager.getApplication();
    if (application == null || application.isDisposed()) {
      return HEADLESS;
    }

    return application.getService(PluginEnabler.class);
  }

  boolean isDisabled(@NotNull PluginId pluginId);

  boolean enable(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors);

  boolean disable(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors);

  default boolean enableById(@NotNull Set<PluginId> pluginIds) {
    return enable(PluginUtils.toPluginDescriptors(pluginIds));
  }

  default boolean disableById(@NotNull Set<PluginId> pluginIds) {
    return disable(PluginUtils.toPluginDescriptors(pluginIds));
  }
}
