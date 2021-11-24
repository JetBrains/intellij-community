// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.externalDependencies.impl;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
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
import com.intellij.openapi.util.text.HtmlChunk;
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
  private static final @NonNls String ENABLE = "enable";
  private static final @NonNls String INSTALL = "install";

  CheckRequiredPluginsActivity() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
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

    String target = notInstalled.isEmpty() ?
                    disabled.isEmpty() ? null : ENABLE :
                    INSTALL;

    if (target != null) {
      String text = INSTALL.equals(target) ?
                    IdeBundle.message("link.install.required.plugins") :
                    disabled.size() == 1 ?
                    IdeBundle.message("link.enable.required.plugin", disabled.get(0).getName()) :
                    IdeBundle.message("link.enable.required.plugins");

      errorMessages.add(HtmlChunk.link(target, text).toString());
    }

    NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(IdeBundle.message("notification.title.required.plugins.not.loaded"), StringUtil.join(errorMessages, "<br>"), NotificationType.ERROR)
      .setListener(notInstalled.isEmpty() ?
                   createEnableNotificationListener(project, disabled, pluginEnabler) :
                   createInstallNotificationListener(project, notInstalled, disabled))
      .notify(project);
  }

  private static boolean enablePlugins(@NotNull List<? extends IdeaPluginDescriptor> descriptors,
                                       @NotNull PluginEnabler pluginEnabler) {
    LOG.info("Required plugins to enable: [" + StringUtil.join(descriptors, d -> d.getPluginId().getIdString(), ", ") + "]");
    return pluginEnabler.enable(descriptors);
  }

  private static @NotNull NotificationListener createEnableNotificationListener(@NotNull Project project,
                                                                                @NotNull List<? extends IdeaPluginDescriptor> disabled,
                                                                                @NotNull PluginEnabler pluginEnabler) {
    return (notification, event) -> {
      if (!isApplicable(event, ENABLE)) return;

      notification.expire();

      if (!enablePlugins(disabled, pluginEnabler)) {
        PluginManagerMain.notifyPluginsUpdated(project);
      }
    };
  }

  private static @NotNull NotificationListener createInstallNotificationListener(@NotNull Project project,
                                                                                 @NotNull Set<PluginId> notInstalled,
                                                                                 @NotNull List<? extends IdeaPluginDescriptor> disabled) {

    HashSet<PluginId> pluginIds = new HashSet<>(notInstalled);
    pluginIds.addAll(notInstalled);
    for (IdeaPluginDescriptor descriptor : disabled) {
      pluginIds.add(descriptor.getPluginId());
    }

    return (notification, event) -> {
      if (!isApplicable(event, INSTALL)) return;

      PluginsAdvertiser.installAndEnable(project, pluginIds, true, true, () -> notification.expire());
    };
  }

  private static boolean isApplicable(@NotNull HyperlinkEvent event,
                                      @NotNull @NonNls String description) {
    return HyperlinkEvent.EventType.ACTIVATED == event.getEventType() &&
           description.equals(event.getDescription());
  }
}
