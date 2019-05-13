// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.troubleshooting;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.project.Project;
import com.intellij.troubleshooting.GeneralTroubleInfoCollector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PluginTroubleInfoCollector implements GeneralTroubleInfoCollector {
  @NotNull
  @Override
  public String getTitle() {
    return "Plugins";
  }

  @NotNull
  @Override
  public String collectInfo(@NotNull Project project) {
    PluginManagerCore.getDisabledPlugins();
    IdeaPluginDescriptor[] ourPlugins = PluginManagerCore.getPlugins();
    List<String> loadedCustom = new ArrayList<>();
    List<String> disabled = new ArrayList<>();

    String SPECIAL_IDEA_PLUGIN = "IDEA CORE";
    for (IdeaPluginDescriptor descriptor : ourPlugins) {
      final String version = descriptor.getVersion();
      String s = descriptor.getName() + (version != null ? " (" + version + ")" : "");
      if (descriptor.isEnabled()) {
        if (!descriptor.isBundled() && !SPECIAL_IDEA_PLUGIN.equals(descriptor.getName())) {
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
