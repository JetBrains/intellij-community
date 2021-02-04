// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.util.containers.ContainerUtil.map2SetNotNull;

final class CheckRequiredPluginsActivity implements StartupActivity.RequiredForSmartMode {
  private static final Logger LOG = Logger.getInstance(CheckRequiredPluginsActivity.class);
  private static final @NonNls String NOTIFICATION_GROUP_ID = "Required Plugins";
  private static final @NonNls String ENABLE = "enable";
  private static final @NonNls String INSTALL = "install";

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

    final List<@Nls String> errorMessages = new ArrayList<>();
    final List<IdeaPluginDescriptor> disabled = new ArrayList<>();
    final Set<PluginId> notInstalled = new HashSet<>();
    List<IdeaPluginDescriptor> pluginsToEnableWithoutRestart = new ArrayList<>();
    ProjectPluginTracker pluginTracker = ProjectPluginTrackerManager.getInstance().getPluginTracker(project);

    for (DependencyOnPlugin dependency : dependencies) {
      PluginId pluginId = PluginId.getId(dependency.getPluginId());
      IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(pluginId);
      if (plugin == null) {
        errorMessages.add(IdeBundle.message("error.plugin.required.for.project.not.installed", pluginId, project.getName()));
        notInstalled.add(pluginId);
        continue;
      }

      if (!plugin.isEnabled() || pluginTracker.isDisabled(pluginId)) {
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
          errorMessages.add(IdeBundle.message("error.plugin.required.for.project.disabled", plugin.getName(), project.getName()));
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
          errorMessages
            .add(IdeBundle.message("error.project.requires.newer.ide", project.getName(), pluginFromString, minVersion, pluginVersion));
        }
        if (maxVersion != null && currentIdeVersion.compareTo(BuildNumber.fromString(maxVersion)) > 0) {
          errorMessages
            .add(IdeBundle.message("error.project.requires.older.ide", project.getName(), pluginFromString, maxVersion, pluginVersion));
        }
      }
      else {
        if (minVersion != null && VersionComparatorUtil.compare(pluginVersion, minVersion) < 0) {
          errorMessages
            .add(IdeBundle.message("error.project.requires.newer.plugin", project.getName(), plugin.getName(), minVersion, pluginVersion));
        }
        if (maxVersion != null && VersionComparatorUtil.compare(pluginVersion, maxVersion) > 0) {
          errorMessages
            .add(IdeBundle.message("error.project.requires.older.plugin", project.getName(), plugin.getName(), maxVersion, pluginVersion));
        }
      }
    }

    if (!pluginsToEnableWithoutRestart.isEmpty()) {
      ApplicationManager.getApplication().invokeLater(() -> enablePlugins(project, pluginsToEnableWithoutRestart));
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

    NotificationListener listener = notInstalled.isEmpty() ?
                                    createEnableNotificationListener(project, disabled) :
                                    createInstallNotificationListener(notInstalled, disabled);

    NotificationGroupManager.getInstance()
      .getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(
        IdeBundle.message("notification.title.required.plugins.not.loaded"),
        join(errorMessages, "<br>"),
        NotificationType.ERROR,
        listener
      ).notify(project);
  }

  private static void enablePlugins(@NotNull Project project,
                                    @NotNull List<? extends IdeaPluginDescriptor> plugins) {
    Set<PluginId> pluginIds = map2SetNotNull(plugins, IdeaPluginDescriptor::getPluginId);
    LOG.info("Required plugins to enable: [" + join(pluginIds, ", ") + "]");

    ProjectPluginTrackerManager
      .getInstance()
      .updatePluginsState(plugins,
                          PluginEnableDisableAction.ENABLE_GLOBALLY,
                          project);
  }

  private static @NotNull NotificationListener createEnableNotificationListener(@NotNull Project project,
                                                                                @NotNull List<? extends IdeaPluginDescriptor> disabled) {
    return (notification, event) -> {
      if (!isApplicable(event, ENABLE)) return;

      notification.expire();
      enablePlugins(project, disabled);
      PluginManagerMain.notifyPluginsUpdated(project);
    };
  }

  private static @NotNull NotificationListener createInstallNotificationListener(@NotNull Set<PluginId> notInstalled,
                                                                                 @NotNull List<? extends IdeaPluginDescriptor> disabled) {

    HashSet<PluginId> pluginIds = new HashSet<>(notInstalled);
    pluginIds.addAll(notInstalled);
    for (IdeaPluginDescriptor descriptor : disabled) {
      pluginIds.add(descriptor.getPluginId());
    }

    return (notification, event) -> {
      if (!isApplicable(event, INSTALL)) return;

      PluginsAdvertiser.installAndEnable(pluginIds, () -> notification.expire());
    };
  }

  private static boolean isApplicable(@NotNull HyperlinkEvent event,
                                      @NotNull @NonNls String description) {
    return HyperlinkEvent.EventType.ACTIVATED == event.getEventType() &&
           description.equals(event.getDescription());
  }
}
