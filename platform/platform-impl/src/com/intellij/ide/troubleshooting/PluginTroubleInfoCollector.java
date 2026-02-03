// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.troubleshooting;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.project.Project;
import com.intellij.troubleshooting.GeneralTroubleInfoCollector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

final class PluginTroubleInfoCollector implements GeneralTroubleInfoCollector {
  @Override
  public @NotNull String getTitle() {
    return "Plugins";
  }

  @Override
  public @NotNull String collectInfo(@NotNull Project project) {
    IdeaPluginDescriptor[] ourPlugins = PluginManagerCore.getPlugins();
    List<String> loadedCustom = new ArrayList<>();
    List<String> disabled = new ArrayList<>();

    for (IdeaPluginDescriptor descriptor : ourPlugins) {
      final String version = descriptor.getVersion();
      String s = descriptor.getName() + (version != null ? " (" + version + ")" : "");
      if (descriptor.isEnabled()) {
        if (!descriptor.isBundled() && !PluginManagerCore.SPECIAL_IDEA_PLUGIN_ID.getIdString().equals(descriptor.getName())) {
          loadedCustom.add(s);
        }
      }
      else {
        disabled.add(s);
      }
    }
    return "Custom plugins: " + loadedCustom + '\n' + "Disabled plugins:" + disabled + '\n';
  }
}
