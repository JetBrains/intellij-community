// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
final class PluginOptionsTopHitProvider implements OptionsTopHitProvider.ApplicationLevelProvider {
  @NotNull
  @Override
  public Collection<OptionDescription> getOptions() {
    ApplicationInfoEx applicationInfo = ApplicationInfoEx.getInstanceEx();
    IdeaPluginDescriptor[] plugins = PluginManagerCore.getPlugins();
    List<OptionDescription> options = new ArrayList<>(plugins.length);
    for (IdeaPluginDescriptor descriptor : plugins) {
      if (applicationInfo.isEssentialPlugin(descriptor.getPluginId())) {
        continue;
      }

      options.add(new PluginBooleanOptionDescriptor(descriptor));
    }
    return options;
  }

  @NotNull
  @Override
  public String getId() {
    return "plugins";
  }
}
