// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  PluginEnabler HEADLESS = new DisabledPluginsState();

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
    return enable(IdeaPluginDescriptorImplKt.toPluginDescriptors(pluginIds));
  }

  default boolean disableById(@NotNull Set<PluginId> pluginIds) {
    return disable(IdeaPluginDescriptorImplKt.toPluginDescriptors(pluginIds));
  }

  /**
   * @deprecated Renamed to {@link #enable(Collection)}.
   */
  @Deprecated
  default void enablePlugins(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors) {
    enable(descriptors);
  }

  /**
   * @deprecated Renamed to {@link #disable(Collection)}.
   */
  @Deprecated
  default void disablePlugins(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors) {
    disable(descriptors);
  }
}
