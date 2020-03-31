// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.search;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import org.jetbrains.annotations.NotNull;

final class PluginSearchableOptionContributor extends SearchableOptionContributor {
  @Override
  public void processOptions(@NotNull SearchableOptionProcessor processor) {
    ApplicationInfoEx applicationInfo = ApplicationInfoEx.getInstanceEx();
    boolean hideImplDetails = PluginManager.getInstance().hideImplementationDetails();
    for (IdeaPluginDescriptor plugin : PluginManagerCore.getPlugins()) {
      if (applicationInfo.isEssentialPlugin(plugin.getPluginId().getIdString())) {
        continue;
      }
      if (hideImplDetails && plugin.isImplementationDetail()) {
        continue;
      }
      final String pluginName = plugin.getName();
      processor.addOptions(pluginName, null, pluginName, PluginManagerConfigurable.ID, IdeBundle.message("title.plugins"), false);
      final String description = plugin.getDescription();
      if (description != null) {
        processor.addOptions(description, null, pluginName, PluginManagerConfigurable.ID, IdeBundle.message("title.plugins"), false);
      }
    }
  }
}
