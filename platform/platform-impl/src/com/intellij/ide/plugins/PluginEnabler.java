// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;

public interface PluginEnabler {

  void setEnabledState(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                       @NotNull PluginEnableDisableAction action);

  boolean isDisabled(@NotNull PluginId pluginId);

  default void enablePlugins(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors) {
    setEnabledState(descriptors, PluginEnableDisableAction.ENABLE_GLOBALLY);
  }

  default void disablePlugins(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors) {
    setEnabledState(descriptors, PluginEnableDisableAction.DISABLE_GLOBALLY);
  }

  PluginEnabler HEADLESS = new PluginEnabler() {

    @Override
    public void setEnabledState(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                                @NotNull PluginEnableDisableAction action) {
      boolean enabled = action.isEnable();
      LinkedHashSet<PluginId> pluginIds = new LinkedHashSet<>(descriptors.size());
      for (IdeaPluginDescriptor descriptor : descriptors) {
        descriptor.setEnabled(enabled);
        pluginIds.add(descriptor.getPluginId());
      }

      DisabledPluginsState.setEnabledState(pluginIds, enabled);
    }

    @Override
    public boolean isDisabled(@NotNull PluginId pluginId) {
      return DisabledPluginsState.isDisabled(pluginId);
    }
  };
}
