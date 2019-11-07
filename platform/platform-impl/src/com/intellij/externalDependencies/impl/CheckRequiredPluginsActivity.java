// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.externalDependencies.impl;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser;
import com.intellij.openapi.util.BuildNumber;
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
public final class CheckRequiredPluginsActivity implements StartupActivity.DumbAware {
  private static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup("Required Plugins", NotificationDisplayType.BALLOON, true);

  @Override
  public void runActivity(@NotNull final Project project) {
    //will trigger 'loadState' and run check if required plugins are specified
    ExternalDependenciesManager.getInstance(project);
  }

  public static void runCheck(@NotNull final Project project) {
    List<DependencyOnPlugin> dependencies = ExternalDependenciesManager.getInstance(project).getDependencies(DependencyOnPlugin.class);
    if (dependencies.isEmpty()) return;

    final List<String> errorMessages = new ArrayList<>();
    final List<IdeaPluginDescriptor> disabled = new ArrayList<>();
    final List<PluginId> notInstalled = new ArrayList<>();
    for (DependencyOnPlugin dependency : dependencies) {
      PluginId pluginId = PluginId.getId(dependency.getPluginId());
      IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(pluginId);
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
      String maxVersion = dependency.getMaxVersion();
      String pluginVersion = plugin.getVersion();
      BuildNumber currentIdeVersion = ApplicationInfo.getInstance().getBuild();
      if (plugin.isBundled() && !plugin.allowBundledUpdate() && currentIdeVersion.asStringWithoutProductCode().equals(pluginVersion)) {
        String pluginFromString = PluginManagerCore.CORE_ID == plugin.getPluginId() ? "" : "plugin '" + plugin.getName() + "' from ";
        if (minVersion != null && currentIdeVersion.compareTo(BuildNumber.fromString(minVersion)) < 0) {
          errorMessages.add("Project '" + project.getName() + "' requires " + pluginFromString +
                            "'" + minVersion + "' or newer build of the IDE, but the current build is '" + pluginVersion + "'.");
        }
        if (maxVersion != null && currentIdeVersion.compareTo(BuildNumber.fromString(maxVersion)) > 0) {
          errorMessages.add("Project '" + project.getName() + "' requires " + pluginFromString +
                            "'" + maxVersion + "' or older build of the IDE, but the current build is '" + pluginVersion + "'.");
        }
      }
      else {
        if (minVersion != null && VersionComparatorUtil.compare(pluginVersion, minVersion) < 0) {
          errorMessages.add("Project '" + project.getName() + "' requires plugin  '" + plugin.getName() + "' version '" + minVersion + "' or higher, but '" +
                            pluginVersion + "' is installed.");
        }
        if (maxVersion != null && VersionComparatorUtil.compare(pluginVersion, maxVersion) > 0) {
          errorMessages.add("Project '" + project.getName() + "' requires plugin  '" + plugin.getName() + "' version '" + maxVersion + "' or lower, but '" +
                            pluginVersion + "' is installed.");
        }
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
                                    PluginManager.getInstance().enablePlugins(disabled, true);
                                    PluginManagerMain.notifyPluginsUpdated(project);
                                  }
                                  else {
                                    Set<PluginId> pluginIds = new HashSet<>();
                                    for (IdeaPluginDescriptor descriptor : disabled) {
                                      pluginIds.add(descriptor.getPluginId());
                                    }
                                    pluginIds.addAll(notInstalled);
                                    PluginsAdvertiser.installAndEnable(pluginIds, () -> notification.expire());
                                  }
                                }
                              }
                            }).notify(project);
    }
  }
}
