// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.search;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import org.jetbrains.annotations.NotNull;

final class PluginSearchableOptionContributor extends SearchableOptionContributor {
  @Override
  public void processOptions(@NotNull SearchableOptionProcessor processor) {
    PluginManager.getVisiblePlugins(false).forEach(descriptor -> {
      String pluginName = descriptor.getName();
      addPluginOptions(processor, pluginName, pluginName);

      String description = descriptor.getDescription();
      if (description != null) {
        addPluginOptions(processor, description, pluginName);
      }
    });
  }

  private static void addPluginOptions(@NotNull SearchableOptionProcessor processor,
                                       @NotNull String text,
                                       @NotNull String pluginName) {
    processor.addOptions(text,
                         null,
                         pluginName,
                         PluginManagerConfigurable.ID,
                         IdeBundle.message("title.plugins"),
                         false);
  }
}
