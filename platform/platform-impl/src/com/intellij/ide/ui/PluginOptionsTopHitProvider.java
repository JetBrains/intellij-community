/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.ui;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Konstantin Bulenkov
 */
public class PluginOptionsTopHitProvider extends OptionsTopHitProvider {

  private boolean myCacheExpired;

  public PluginOptionsTopHitProvider() {
    PluginManagerCore.addDisablePluginListener(() -> myCacheExpired = true);
  }

  @Override
  protected boolean isCacheExpired() {
    return myCacheExpired;
  }

  @Override
  protected void cacheUpdated() {
    myCacheExpired = false;
  }

  @NotNull
  @Override
  public Collection<OptionDescription> getOptions(@Nullable Project project) {
    ArrayList<OptionDescription> options = new ArrayList<>();
    ApplicationInfoEx applicationInfo = ApplicationInfoEx.getInstanceEx();

    IdeaPluginDescriptor[] descriptors = PluginManagerCore.getPlugins();
    Map<PluginId, IdeaPluginDescriptor> pluginsMap =
      Arrays.stream(descriptors).collect(Collectors.toMap(descriptor -> descriptor.getPluginId(), descriptor -> descriptor));

    for (IdeaPluginDescriptor pluginDescriptor : descriptors) {
      if (applicationInfo.isEssentialPlugin(pluginDescriptor.getPluginId().getIdString())) {
        continue;
      }

      boolean allDependentPluginsEnabled = Arrays.stream(pluginDescriptor.getDependentPluginIds())
                        .map(id -> pluginsMap.get(id))
                        .filter(Objects::nonNull)
                        .allMatch(descriptor -> descriptor.isEnabled());
      if (!allDependentPluginsEnabled) {
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
