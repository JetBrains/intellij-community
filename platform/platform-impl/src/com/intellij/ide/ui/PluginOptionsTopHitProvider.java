// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
final class PluginOptionsTopHitProvider implements OptionsTopHitProvider.ApplicationLevelProvider {

  @Override
  public @NotNull Collection<OptionDescription> getOptions() {
    ApplicationInfoEx applicationInfo = ApplicationInfoEx.getInstanceEx();
    List<IdeaPluginDescriptorImpl> descriptors = PluginManagerCore.getPluginSet().allPlugins;
    List<PluginBooleanOptionDescriptor> options = new ArrayList<>(descriptors.size());
    boolean hideImplDetails = PluginManager.getInstance().hideImplementationDetails();

    for (IdeaPluginDescriptorImpl descriptor : descriptors) {
      if (applicationInfo.isEssentialPlugin(descriptor.getPluginId())) {
        continue;
      }
      if (hideImplDetails && descriptor.isImplementationDetail()) {
        continue;
      }

      options.add(new PluginBooleanOptionDescriptor(descriptor));
    }
    return Collections.unmodifiableList(options);
  }

  @Override
  public @NotNull String getId() {
    return "plugins";
  }
}
