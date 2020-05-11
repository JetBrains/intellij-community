// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.externalDependencies.impl;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class CheckRequiredPluginsActivity implements StartupActivity {
  private static final Logger LOG = Logger.getInstance(CheckRequiredPluginsActivity.class);
  private static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup("Required Plugins", NotificationDisplayType.BALLOON, true);

  CheckRequiredPluginsActivity() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw ExtensionNotApplicableException.INSTANCE;
    }
  }

  @Override
  public void runActivity(@NotNull Project project) {
    // will trigger 'loadState' and run check if required plugins are specified
    ExternalDependenciesManager.getInstance(project);
  }

  public static void runCheck(@NotNull Project project, @NotNull ExternalDependenciesManager dependencyManager) {
    List<DependencyOnPlugin> dependencies = dependencyManager.getDependencies(DependencyOnPlugin.class);
    if (dependencies.isEmpty()) {
      return;
    }

    final List<String> errorMessages = new ArrayList<>();
    final List<IdeaPluginDescriptor> disabled = new ArrayList<>();
    final List<PluginId> notInstalled = new ArrayList<>();
    List<IdeaPluginDescriptor> pluginsToEnableWithoutRestart = new ArrayList<>();
    for (DependencyOnPlugin dependency : dependencies) {
      PluginId pluginId = PluginId.getId(dependency.getPluginId());
      IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(pluginId);
      if (plugin == null) {
        errorMessages.add("Plugin '" + dependency.getPluginId() + "' required for '" + project.getName() + "' project isn't installed.");
        notInstalled.add(pluginId);
        continue;
      }

      if (!plugin.isEnabled()) {
        boolean canEnableWithoutRestart = false;
        if (Registry.is("ide.plugins.load.automatically")) {
          IdeaPluginDescriptorImpl fullDescriptor = PluginDescriptorLoader.tryLoadFullDescriptor((IdeaPluginDescriptorImpl)plugin);
          String message = fullDescriptor == null
                           ? "Cannot load full descriptor for " + plugin.getPluginId()
                           : DynamicPlugins.checkCanUnloadWithoutRestart((IdeaPluginDescriptorImpl)plugin);
          if (message == null) {
            canEnableWithoutRestart = true;
            pluginsToEnableWithoutRestart.add(plugin);
          }
          else {
            LOG.info("Required plugin " + plugin.getPluginId() + " can't be enabled without restart: " + message);
          }
        }
        if (!canEnableWithoutRestart) {
          errorMessages.add("Plugin '" + plugin.getName() + "' required for '" + project.getName() + "' project is disabled.");
          disabled.add(plugin);
        }
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

    if (!pluginsToEnableWithoutRestart.isEmpty()) {
      LOG.info("Automatically enabling plugins required for this project: " +
               StringUtil.join(pluginsToEnableWithoutRestart, (plugin) -> plugin.getPluginId().toString(), ", "));
      for (IdeaPluginDescriptor descriptor : pluginsToEnableWithoutRestart) {
        ProjectPluginTracker.getInstance().registerProjectPlugin(project, descriptor);
      }
      ApplicationManager.getApplication().invokeLater(() -> PluginEnabler.enablePlugins(project, pluginsToEnableWithoutRestart, true));
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
        .createNotification(IdeBundle.message("notification.title.required.plugins.weren.t.loaded"), StringUtil.join(errorMessages, "<br>"), NotificationType.ERROR,
                            new NotificationListener() {
                              @Override
                              public void hyperlinkUpdate(@NotNull final Notification notification,
                                                          @NotNull HyperlinkEvent event) {
                                if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                                  if ("enable".equals(event.getDescription())) {
                                    notification.expire();
                                    DisabledPluginsState.enablePlugins(disabled, true);
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
