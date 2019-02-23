// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Konstantin Bulenkov
 */
final class PluginOptionsTopHitProvider implements OptionsTopHitProvider.ApplicationLevelProvider {
  @NotNull
  @Override
  public Collection<OptionDescription> getOptions() {
    ArrayList<OptionDescription> options = new ArrayList<>();
    ApplicationInfoEx applicationInfo = ApplicationInfoEx.getInstanceEx();

    IdeaPluginDescriptor[] descriptors = PluginManagerCore.getPlugins();

    for (IdeaPluginDescriptor pluginDescriptor : descriptors) {
      if (applicationInfo.isEssentialPlugin(pluginDescriptor.getPluginId().getIdString())) {
        continue;
      }

      options.add(new PluginBooleanOptionDescriptor(pluginDescriptor.getPluginId()));

    }
    return options;
  }

  @Override
  public String getId() {
    return "plugins";
  }
}
