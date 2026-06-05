// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalDependencies.impl;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginEnabler;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.ide.plugins.PluginModuleDescriptor;
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
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

final class CheckRequiredPluginsWorker {
  private static final Logger LOG = Logger.getInstance(CheckRequiredPluginsWorker.class);
  private static final String NOTIFICATION_GROUP_ID = "Required Plugins";

  CheckRequiredPluginsWorker() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      throw ExtensionNotApplicableException.create();
    }
  }

  private static List<DependencyOnPlugin> getRequiredPlugins(ExternalDependenciesManager dependencyManager) {
    var dependencies = new ArrayList<>(dependencyManager.getDependencies(DependencyOnPlugin.class));
    if (!PlatformUtils.isPyCharm()) return dependencies;
    if (!PluginManagerCore.isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID)) return dependencies;

    // Free mode
    var result = new ArrayList<DependencyOnPlugin>();
    for (var plugin : dependencies) {
      var pluginId = plugin.getPluginId();
      var descriptor = PluginManagerCore.findPlugin(PluginId.getId(pluginId));
      if (descriptor == null) continue;
      var canBeEnabled = !ContainerUtil.exists(descriptor.getDependencies(), it -> it.getPluginId().equals(PluginManagerCore.ULTIMATE_PLUGIN_ID));
      if (canBeEnabled) {
        result.add(plugin);
      }
    }
    return result;
  }

  public static void runCheck(@NotNull Project project, @NotNull ExternalDependenciesManager dependencyManager) {
    var dependencies = getRequiredPlugins(dependencyManager);
    if (dependencies.isEmpty()) {
      return;
    }

    var projectName = project.getName();

    var errorMessages = new ArrayList<@Nls String>();
    var disabled = new ArrayList<IdeaPluginDescriptor>();
    var notInstalled = new HashSet<PluginId>();
    var hasVersionConflicts = false;

    var applicationInfo = ApplicationInfo.getInstance();
    var pluginEnabler = PluginEnabler.getInstance();

    for (var dependency : dependencies) {
      var pluginId = PluginId.getId(dependency.getPluginId());
      var descriptor = (PluginModuleDescriptor)PluginManagerCore.findPlugin(pluginId);
      if (descriptor == null) {
        errorMessages.add(IdeBundle.message("error.plugin.required.for.project.not.installed", pluginId, projectName));
        notInstalled.add(pluginId);
        continue;
      }

      var pluginName = descriptor.getName();
      if (pluginEnabler.isDisabled(pluginId)) {
        errorMessages.add(IdeBundle.message("error.plugin.required.for.project.disabled", pluginName, projectName));
        disabled.add(descriptor);
        continue;
      }

      var minVersion = dependency.getMinVersion();
      var maxVersion = dependency.getMaxVersion();
      var pluginVersion = descriptor.getVersion();

      var currentIdeVersion = applicationInfo.getBuild();
      if (descriptor.isBundled() &&
          !descriptor.allowBundledUpdate() &&
          currentIdeVersion.asStringWithoutProductCode().equals(pluginVersion)) {
        var pluginFromString = PluginManagerCore.CORE_ID.equals(descriptor.getPluginId()) ? "" : "plugin '" + pluginName + "' from ";
        if (
          minVersion != null && !minVersion.isEmpty() &&
          currentIdeVersion.compareTo(requireNonNull(BuildNumber.fromString(minVersion))) < 0
        ) {
          errorMessages.add(IdeBundle.message("error.project.requires.newer.ide", projectName, pluginFromString, minVersion, pluginVersion));
          hasVersionConflicts = true;
        }
        if (
          maxVersion != null && !maxVersion.isEmpty() &&
          currentIdeVersion.compareTo(requireNonNull(BuildNumber.fromString(maxVersion))) > 0
        ) {
          errorMessages.add(IdeBundle.message("error.project.requires.older.ide", projectName, pluginFromString, maxVersion, pluginVersion));
          hasVersionConflicts = true;
        }
      }
      else {
        if (minVersion != null && VersionComparatorUtil.compare(pluginVersion, minVersion) < 0) {
          errorMessages.add(IdeBundle.message("error.project.requires.newer.plugin", projectName, pluginName, minVersion, pluginVersion));
          hasVersionConflicts = true;
        }
        if (maxVersion != null && VersionComparatorUtil.compare(pluginVersion, maxVersion) > 0) {
          errorMessages.add(IdeBundle.message("error.project.requires.older.plugin", projectName, pluginName, maxVersion, pluginVersion));
          hasVersionConflicts = true;
        }
      }
    }

    if (errorMessages.isEmpty()) {
      return;
    }

    LOG.warn(String.join("\n", errorMessages));

    @SuppressWarnings("HardCodedStringLiteral") var notificationMessage =
      hasVersionConflicts ? String.join("<br>", errorMessages) : buildRequiredPluginsMessage(projectName, disabled, notInstalled);

    var notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(IdeBundle.message("notification.title.required.plugins.not.loaded"), notificationMessage, NotificationType.ERROR)
      .setIcon(AllIcons.Ide.Notification.IdeUpdate);

    if (!disabled.isEmpty()) {
      var text = disabled.size() == 1 ? IdeBundle.message("link.enable.required.plugin", disabled.getFirst().getName()) : IdeBundle.message("link.enable.required.plugins");
      notification.addAction(NotificationAction.create(text, (_, n) -> {
        n.expire();
        if (!enablePlugins(disabled, pluginEnabler)) {
          PluginManagerMain.notifyPluginsUpdated(project);
        }
      }));
    }
    else if (!notInstalled.isEmpty()) {
      notification.addAction(NotificationAction.create(IdeBundle.message("link.install.required.plugins"), (_, n) -> {
        var pluginIds = new HashSet<>(notInstalled);
        pluginIds.addAll(notInstalled);
        for (var descriptor : disabled) {
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

  private static @Nls String buildRequiredPluginsMessage(
    String projectName,
    List<IdeaPluginDescriptor> disabledPlugins,
    Set<PluginId> notInstalledPlugins
  ) {
    if (disabledPlugins.isEmpty()) {
      if (notInstalledPlugins.size() == 1) {
        var pluginId = notInstalledPlugins.iterator().next();
        return IdeBundle.message("error.plugin.required.for.project.not.installed", pluginId, projectName);
      }

      var message = new HtmlBuilder();
      message.append(IdeBundle.message("error.project.requires.plugins.not.installed", projectName));

      message.appendRaw("<ul>");
      for (var p : notInstalledPlugins) {
        @NlsSafe var idString = p.getIdString();
        message.appendRaw("<li>").append(idString).appendRaw("</li>");
      }
      message.appendRaw("</ul>");

      return message.toString();
    }

    if (disabledPlugins.size() == 1 && notInstalledPlugins.isEmpty()) {
      var plugin = disabledPlugins.getFirst();
      return IdeBundle.message("error.plugin.required.for.project.disabled", plugin.getName(), projectName);
    }

    var message = new HtmlBuilder();
    message.append(IdeBundle.message("error.project.requires.plugins.not.enabled", projectName));

    message.appendRaw("<ul>");
    for (var p : disabledPlugins) {
      message.appendRaw("<li>").append(p.getName()).appendRaw("</li>");
    }
    for (var p : notInstalledPlugins) {
      @NlsSafe var idString = p.getIdString();
      message.appendRaw("<li>").append(idString).appendRaw("</li>");
    }
    message.appendRaw("</ul>");

    return message.toString();
  }

  private static boolean enablePlugins(List<IdeaPluginDescriptor> descriptors, PluginEnabler pluginEnabler) {
    LOG.info("Required plugins to enable: [" + StringUtil.join(descriptors, d -> d.getPluginId().getIdString(), ", ") + "]");
    return pluginEnabler.enable(descriptors);
  }
}
