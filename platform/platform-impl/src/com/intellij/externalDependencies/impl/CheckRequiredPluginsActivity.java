// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalDependencies.impl;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

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

  private static List<DependencyOnPlugin> getRequiredPlugins(@NotNull ExternalDependenciesManager dependencyManager) {
    List<DependencyOnPlugin> dependencies = new ArrayList<>(dependencyManager.getDependencies(DependencyOnPlugin.class));
    if (!PluginManagerCore.isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID)) {
      return dependencies;
    }
    // Free mode
    List<DependencyOnPlugin> result = new ArrayList<>();
    for (DependencyOnPlugin plugin : dependencies) {
      String pluginId = plugin.getPluginId();
      IdeaPluginDescriptorImpl descriptor = PluginManagerCore.findPlugin(PluginId.getId(pluginId));
      if (descriptor == null) {
        continue;
      }
      var canBeEnabled =
        !ContainerUtil.exists(descriptor.getDependencies(), it -> it.getPluginId().equals(PluginManagerCore.ULTIMATE_PLUGIN_ID));
      if (canBeEnabled) {
        result.add(plugin);
      }
    }
    return result;
  }

  public static void runCheck(@NotNull Project project, @NotNull ExternalDependenciesManager dependencyManager) {
    List<DependencyOnPlugin> dependencies = getRequiredPlugins(dependencyManager);

    if (dependencies.isEmpty()) {
      return;
    }

    String projectName = project.getName();

    List<@Nls String> errorMessages = new ArrayList<>();
    List<IdeaPluginDescriptor> disabled = new ArrayList<>();
    Set<PluginId> notInstalled = new HashSet<>();
    boolean hasVersionConflicts = false;

    List<IdeaPluginDescriptor> pluginsToEnableWithoutRestart = new ArrayList<>();

    ApplicationInfo applicationInfo = ApplicationInfo.getInstance();
    PluginEnabler pluginEnabler = PluginEnabler.getInstance();

    for (DependencyOnPlugin dependency : dependencies) {
      PluginId pluginId = PluginId.getId(dependency.getPluginId());
      IdeaPluginDescriptorImpl descriptor = PluginManagerCore.findPlugin(pluginId);
      if (descriptor == null) {
        errorMessages.add(IdeBundle.message("error.plugin.required.for.project.not.installed", pluginId, projectName));
        notInstalled.add(pluginId);
        continue;
      }

      String pluginName = descriptor.getName();
      if (pluginEnabler.isDisabled(pluginId)) {
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
      if (descriptor.isBundled() &&
          !descriptor.allowBundledUpdate() &&
          currentIdeVersion.asStringWithoutProductCode().equals(pluginVersion)) {
        String pluginFromString = PluginManagerCore.CORE_ID.equals(descriptor.getPluginId()) ? "" : "plugin '" + pluginName + "' from ";
        if (minVersion != null && !minVersion.isEmpty()
            && currentIdeVersion.compareTo(requireNonNull(BuildNumber.fromString(minVersion))) < 0) {
          errorMessages.add(
            IdeBundle.message("error.project.requires.newer.ide", projectName, pluginFromString, minVersion, pluginVersion));

          hasVersionConflicts = true;
        }
        if (maxVersion != null && !maxVersion.isEmpty()
            && currentIdeVersion.compareTo(requireNonNull(BuildNumber.fromString(maxVersion))) > 0) {
          errorMessages.add(
            IdeBundle.message("error.project.requires.older.ide", projectName, pluginFromString, maxVersion, pluginVersion));

          hasVersionConflicts = true;
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

    LOG.warn(StringUtil.join(errorMessages, "\n"));

    var notificationMessage = hasVersionConflicts ?
                              StringUtil.join(errorMessages, "<br>") : buildRequiredPluginsMessage(projectName, disabled, notInstalled);

    var notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(IdeBundle.message("notification.title.required.plugins.not.loaded"), notificationMessage,
                          NotificationType.ERROR)
      .setIcon(AllIcons.Ide.Notification.IdeUpdate);

    if (!disabled.isEmpty()) {
      notification.addAction(NotificationAction.create(disabled.size() == 1 ?
                                                       IdeBundle.message("link.enable.required.plugin", disabled.get(0).getName()) :
                                                       IdeBundle.message("link.enable.required.plugins"), (event, n) -> {
        n.expire();
        if (!enablePlugins(disabled, pluginEnabler)) {
          PluginManagerMain.notifyPluginsUpdated(project);
        }
      }));
    }
    else if (!notInstalled.isEmpty()) {
      notification.addAction(NotificationAction.create(IdeBundle.message("link.install.required.plugins"), (event, n) -> {
        HashSet<PluginId> pluginIds = new HashSet<>(notInstalled);
        pluginIds.addAll(notInstalled);
        for (IdeaPluginDescriptor descriptor : disabled) {
          pluginIds.add(descriptor.getPluginId());
        }

        PluginsAdvertiser.installAndEnable(project, pluginIds, true, true, () -> n.expire());
      }));
    }

    if (!notInstalled.isEmpty() || !disabled.isEmpty()) {
      notification.addAction(NotificationAction.createSimple(IdeBundle.message("link.required.plugins.settings"), () -> {
        ShowSettingsUtil.getInstance().editConfigurable(project, new ExternalDependenciesConfigurable(project));
      }));
    }

    notification.setSuggestionType(true).notify(project);
  }

  private static @Nls @NotNull String buildRequiredPluginsMessage(@NotNull String projectName,
                                                                  List<IdeaPluginDescriptor> disabledPlugins,
                                                                  Set<PluginId> notInstalledPlugins) {
    if (disabledPlugins.isEmpty()) {
      if (notInstalledPlugins.size() == 1) {
        PluginId pluginId = notInstalledPlugins.iterator().next();
        return IdeBundle.message("error.plugin.required.for.project.not.installed", pluginId, projectName);
      }

      HtmlBuilder message = new HtmlBuilder();
      message.append(IdeBundle.message("error.project.requires.plugins.not.installed", projectName));

      message.appendRaw("<ul>");
      for (PluginId p : notInstalledPlugins) {
        message.appendRaw("<li>").append(p.getIdString()).append("</li>");
      }
      message.appendRaw("</ul>");

      return message.toString();
    }

    if (disabledPlugins.size() == 1 && notInstalledPlugins.isEmpty()) {
      var plugin = disabledPlugins.get(0);
      return IdeBundle.message("error.plugin.required.for.project.disabled", plugin.getName(), projectName);
    }

    HtmlBuilder message = new HtmlBuilder();
    message.append(IdeBundle.message("error.project.requires.plugins.not.enabled", projectName));

    message.appendRaw("<ul>");
    for (var p : disabledPlugins) {
      message.appendRaw("<li>").append(p.getName()).appendRaw("</li>");
    }
    for (var p : notInstalledPlugins) {
      message.appendRaw("<li>").append(p.getIdString()).appendRaw("</li>");
    }
    message.appendRaw("</ul>");

    return message.toString();
  }

  private static boolean enablePlugins(@NotNull List<? extends IdeaPluginDescriptor> descriptors,
                                       @NotNull PluginEnabler pluginEnabler) {
    LOG.info("Required plugins to enable: [" + StringUtil.join(descriptors, d -> d.getPluginId().getIdString(), ", ") + "]");
    return pluginEnabler.enable(descriptors);
  }
}
