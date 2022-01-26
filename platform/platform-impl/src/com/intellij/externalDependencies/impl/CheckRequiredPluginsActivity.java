// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalDependencies.impl;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class CheckRequiredPluginsActivity implements StartupActivity.RequiredForSmartMode {
  private static final Logger LOG = Logger.getInstance(CheckRequiredPluginsActivity.class);
  private static final @NonNls String NOTIFICATION_GROUP_ID = "Required Plugins";

  CheckRequiredPluginsActivity() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      throw ExtensionNotApplicableException.create();
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

    String projectName = project.getName();

    final List<@Nls String> errorMessages = new ArrayList<>();
    final List<IdeaPluginDescriptor> disabled = new ArrayList<>();
    final Set<PluginId> notInstalled = new HashSet<>();
    List<IdeaPluginDescriptor> pluginsToEnableWithoutRestart = new ArrayList<>();

    ApplicationInfo applicationInfo = ApplicationInfo.getInstance();
    PluginEnabler pluginEnabler = PluginEnabler.getInstance();
    ProjectPluginTracker pluginTracker = DynamicPluginEnabler.findPluginTracker(project, pluginEnabler);

    for (DependencyOnPlugin dependency : dependencies) {
      PluginId pluginId = PluginId.getId(dependency.getPluginId());
      IdeaPluginDescriptorImpl descriptor = PluginManagerCore.findPlugin(pluginId);
      if (descriptor == null) {
        errorMessages.add(IdeBundle.message("error.plugin.required.for.project.not.installed", pluginId, projectName));
        notInstalled.add(pluginId);
        continue;
      }

      String pluginName = descriptor.getName();
      if (pluginEnabler.isDisabled(pluginId) ||
          pluginTracker != null && pluginTracker.isDisabled(pluginId)) {
        boolean canEnableWithoutRestart = Registry.is("ide.plugins.load.automatically") &&
                                          DynamicPlugins.allowLoadUnloadWithoutRestart(descriptor);
        if (canEnableWithoutRestart) {
          pluginsToEnableWithoutRestart.add(descriptor);
        }
        else {
          errorMessages.add(IdeBundle.message("error.plugin.required.for.project.disabled", pluginName, projectName));
          disabled.add(descriptor);
        }
        continue;
      }

      String minVersion = dependency.getMinVersion();
      String maxVersion = dependency.getMaxVersion();
      String pluginVersion = descriptor.getVersion();

      BuildNumber currentIdeVersion = applicationInfo.getBuild();
      if (descriptor.isBundled() && !descriptor.allowBundledUpdate() && currentIdeVersion.asStringWithoutProductCode().equals(pluginVersion)) {
        String pluginFromString = PluginManagerCore.CORE_ID.equals(descriptor.getPluginId()) ? "" : "plugin '" + pluginName + "' from ";
        if (minVersion != null && currentIdeVersion.compareTo(BuildNumber.fromString(minVersion)) < 0) {
          errorMessages.add(IdeBundle.message("error.project.requires.newer.ide", projectName, pluginFromString, minVersion, pluginVersion));
        }
        if (maxVersion != null && currentIdeVersion.compareTo(BuildNumber.fromString(maxVersion)) > 0) {
          errorMessages.add(IdeBundle.message("error.project.requires.older.ide", projectName, pluginFromString, maxVersion, pluginVersion));
        }
      }
      else {
        if (minVersion != null && VersionComparatorUtil.compare(pluginVersion, minVersion) < 0) {
          errorMessages.add(IdeBundle.message("error.project.requires.newer.plugin", projectName, pluginName, minVersion, pluginVersion));
        }
        if (maxVersion != null && VersionComparatorUtil.compare(pluginVersion, maxVersion) > 0) {
          errorMessages.add(IdeBundle.message("error.project.requires.older.plugin", projectName, pluginName, maxVersion, pluginVersion));
        }
      }
    }

    if (!pluginsToEnableWithoutRestart.isEmpty()) {
      ApplicationManager.getApplication().invokeLater(() -> {
        enablePlugins(pluginsToEnableWithoutRestart, pluginEnabler);
      });
    }

    if (errorMessages.isEmpty()) {
      return;
    }

    Notification notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(IdeBundle.message("notification.title.required.plugins.not.loaded"), StringUtil.join(errorMessages, "<br>"),
                          NotificationType.ERROR);

    if (!disabled.isEmpty()) {
      notification.addAction(new NotificationAction(disabled.size() == 1 ?
                                                    IdeBundle.message("link.enable.required.plugin", disabled.get(0).getName()) :
                                                    IdeBundle.message("link.enable.required.plugins")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          notification.expire();

          if (!enablePlugins(disabled, pluginEnabler)) {
            PluginManagerMain.notifyPluginsUpdated(project);
          }
        }
      });
    }
    else if (!notInstalled.isEmpty()) {
      notification.addAction(new NotificationAction(IdeBundle.message("link.install.required.plugins")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          HashSet<PluginId> pluginIds = new HashSet<>(notInstalled);
          pluginIds.addAll(notInstalled);
          for (IdeaPluginDescriptor descriptor : disabled) {
            pluginIds.add(descriptor.getPluginId());
          }

          PluginsAdvertiser.installAndEnable(project, pluginIds, true, true, () -> notification.expire());
        }
      });
    }

    notification.notify(project);
  }

  private static boolean enablePlugins(@NotNull List<? extends IdeaPluginDescriptor> descriptors,
                                       @NotNull PluginEnabler pluginEnabler) {
    LOG.info("Required plugins to enable: [" + StringUtil.join(descriptors, d -> d.getPluginId().getIdString(), ", ") + "]");
    return pluginEnabler.enable(descriptors);
  }

  private static boolean isApplicable(@NotNull HyperlinkEvent event,
                                      @NotNull @NonNls String description) {
    return HyperlinkEvent.EventType.ACTIVATED == event.getEventType() &&
           description.equals(event.getDescription());
  }
}
