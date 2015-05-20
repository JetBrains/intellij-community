/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.externalDependencies.impl;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.notification.*;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class CheckRequiredPluginsActivity implements StartupActivity {
  private static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup("Required Plugins", NotificationDisplayType.BALLOON, true);

  @Override
  public void runActivity(@NotNull final Project project) {
    List<DependencyOnPlugin> dependencies = ExternalDependenciesManager.getInstance(project).getDependencies(DependencyOnPlugin.class);
    if (dependencies.isEmpty()) return;

    final List<String> errorMessages = new ArrayList<String>();
    final List<IdeaPluginDescriptor> disabled = new ArrayList<IdeaPluginDescriptor>();
    final List<PluginId> notInstalled = new ArrayList<PluginId>();
    for (DependencyOnPlugin dependency : dependencies) {
      PluginId pluginId = PluginId.getId(dependency.getPluginId());
      IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
      if (plugin == null) {
        errorMessages.add("Plugin '" + dependency.getPluginId() + "' required for '" + project.getName() + "' project isn't installed.");
        notInstalled.add(pluginId);
        continue;
      }
      if (!plugin.isEnabled()) {
        errorMessages.add("Plugin '" + plugin.getName() + "' required for '" + project.getName() + "' project is disabled.");
        disabled.add(plugin);
        continue;
      }
      String minVersion = dependency.getMinVersion();
      if (minVersion != null && VersionComparatorUtil.compare(plugin.getVersion(), minVersion) < 0) {
        errorMessages.add("Project '" + project.getName() + "' requires '" + plugin.getName() + "' plugin of version at least '" + minVersion + "', but '" + plugin.getVersion() + "' is installed.");
      }
      String maxVersion = dependency.getMaxVersion();
      if (maxVersion != null && VersionComparatorUtil.compare(plugin.getVersion(), maxVersion) > 0) {
        errorMessages.add("Project '" + project.getName() + "' requires '" + plugin.getName() + "' plugin of version at most '" + maxVersion + "', but '" + plugin.getVersion() + "' is installed.");
      }
    }

    if (!errorMessages.isEmpty()) {
      if (!disabled.isEmpty() && notInstalled.isEmpty()) {
        String plugins = disabled.size() == 1 ? disabled.get(0).getName() : "required plugins";
        errorMessages.add("<a href=\"enable\">Enable " + plugins + "</a>");
      }
      else if (!disabled.isEmpty() || !notInstalled.isEmpty()) {
        errorMessages.add("<a href=\"install\">Install required plugins</a>");
      }
      NOTIFICATION_GROUP
        .createNotification("Required plugins weren't loaded", StringUtil.join(errorMessages, "<br>"), NotificationType.ERROR,
                            new NotificationListener() {
                              @Override
                              public void hyperlinkUpdate(@NotNull final Notification notification,
                                                          @NotNull HyperlinkEvent event) {
                                if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                                  if ("enable".equals(event.getDescription())) {
                                    notification.expire();
                                    for (IdeaPluginDescriptor descriptor : disabled) {
                                      PluginManagerCore.enablePlugin(descriptor.getPluginId().getIdString());
                                    }
                                    PluginManagerMain.notifyPluginsUpdated(project);
                                  }
                                  else {
                                    Set<String> pluginIds = new HashSet<String>();
                                    for (IdeaPluginDescriptor descriptor : disabled) {
                                      pluginIds.add(descriptor.getPluginId().getIdString());
                                    }
                                    for (PluginId pluginId : notInstalled) {
                                      pluginIds.add(pluginId.getIdString());
                                    }
                                    PluginsAdvertiser.installAndEnablePlugins(pluginIds, new Runnable() {
                                      @Override
                                      public void run() {
                                        notification.expire();
                                      }
                                    });
                                  }
                                }
                              }
                            }).notify(project);
    }
  }
}
