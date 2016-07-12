/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.plugins;

import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.util.Condition;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class NonBundledPluginsUsagesCollector extends UsagesCollector {
  private static final String GROUP_ID = "non-bundled-plugins";

  @NotNull
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create(GROUP_ID, GroupDescriptor.HIGHER_PRIORITY);
  }

  @NotNull
  public Set<UsageDescriptor> getUsages() {
    final IdeaPluginDescriptor[] plugins = PluginManagerCore.getPlugins();
    final List<IdeaPluginDescriptor> nonBundledEnabledPlugins = ContainerUtil.filter(plugins, d -> d.isEnabled() && !d.isBundled() && d.getPluginId() != null);

    return ContainerUtil.map2Set(nonBundledEnabledPlugins, descriptor -> new UsageDescriptor(descriptor.getPluginId().getIdString(), 1));
  }

}
