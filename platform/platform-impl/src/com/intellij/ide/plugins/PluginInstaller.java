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
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.ZipUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author stathik
 * @since Nov 29, 2003
 */
public class PluginInstaller {
  public static final String UNKNOWN_HOST_MARKER = "__unknown_repository__";

  private static final Object ourLock = new Object();

  private PluginInstaller() { }

  public static boolean prepareToInstall(List<PluginNode> pluginsToInstall,
                                         List<PluginId> allPlugins,
                                         @NotNull ProgressIndicator indicator) {
    updateUrls(pluginsToInstall, indicator);
    Set<PluginNode> dependant = new THashSet<>();
    boolean install = prepareToInstall(pluginsToInstall, allPlugins, dependant, indicator);
    for (PluginNode node : dependant) {
      if (!pluginsToInstall.contains(node)) {
        pluginsToInstall.add(node);
      }
    }
    return install;
  }

  private static void updateUrls(List<PluginNode> pluginsToInstall, @NotNull ProgressIndicator indicator) {
    boolean unknownNodes = false;
    for (PluginNode node : pluginsToInstall) {
      if (node.getRepositoryName() == UNKNOWN_HOST_MARKER) {
        unknownNodes = true;
        break;
      }
    }
    if (!unknownNodes) return;

    List<String> hosts = ContainerUtil.newSmartList();
    ContainerUtil.addIfNotNull(hosts, ApplicationInfoEx.getInstanceEx().getBuiltinPluginsUrl());
    hosts.addAll(UpdateSettings.getInstance().getPluginHosts());
    Map<PluginId, IdeaPluginDescriptor> allPlugins = ContainerUtil.newHashMap();
    for (String host : hosts) {
      try {
        List<IdeaPluginDescriptor> descriptors = RepositoryHelper.loadPlugins(host, indicator);
        for (IdeaPluginDescriptor descriptor : descriptors) {
          allPlugins.put(descriptor.getPluginId(), descriptor);
        }
      }
      catch (IOException ignored) { }
    }

    for (PluginNode node : pluginsToInstall) {
      if (node.getRepositoryName() == UNKNOWN_HOST_MARKER) {
        IdeaPluginDescriptor descriptor = allPlugins.get(node.getPluginId());
        if (descriptor != null) {
          node.setRepositoryName(((PluginNode)descriptor).getRepositoryName());
          node.setDownloadUrl(((PluginNode)descriptor).getDownloadUrl());
        }
        else {
          node.setRepositoryName(null);
        }
      }
    }
  }

  private static boolean prepareToInstall(List<PluginNode> pluginsToInstall,
                                          List<PluginId> allPlugins,
                                          Set<PluginNode> installedDependant,
                                          @NotNull ProgressIndicator indicator) {
    List<PluginId> pluginIds = new SmartList<>();
    for (PluginNode pluginNode : pluginsToInstall) {
      pluginIds.add(pluginNode.getPluginId());
    }

    boolean result = false;
    for (PluginNode pluginNode : pluginsToInstall) {
      indicator.setText(pluginNode.getName());
      try {
        result |= prepareToInstall(pluginNode, pluginIds, allPlugins, installedDependant, indicator);
      }
      catch (IOException e) {
        String title = IdeBundle.message("title.plugin.error");
        Notifications.Bus.notify(new Notification(title, title, pluginNode.getName() + ": " + e.getMessage(), NotificationType.ERROR));
        return false;
      }
    }

    return result;
  }

  private static boolean prepareToInstall(PluginNode pluginNode,
                                          List<PluginId> pluginIds,
                                          List<PluginId> allPlugins,
                                          Set<PluginNode> installedDependant,
                                          @NotNull ProgressIndicator indicator) throws IOException {
    installedDependant.add(pluginNode);

    // check for dependent plugins at first.
    if (pluginNode.getDepends() != null && pluginNode.getDepends().size() > 0) {
      // prepare plugins list for install
      final PluginId[] optionalDependentPluginIds = pluginNode.getOptionalDependentPluginIds();
      final List<PluginNode> depends = new ArrayList<>();
      final List<PluginNode> optionalDeps = new ArrayList<>();
      for (int i = 0; i < pluginNode.getDepends().size(); i++) {
        PluginId depPluginId = pluginNode.getDepends().get(i);
        if (PluginManager.isPluginInstalled(depPluginId) || PluginManagerCore.isModuleDependency(depPluginId) ||
            InstalledPluginsState.getInstance().wasInstalled(depPluginId) ||
            (pluginIds != null && pluginIds.contains(depPluginId))) {
          // ignore installed or installing plugins
          continue;
        }

        PluginNode depPlugin = new PluginNode(depPluginId);
        depPlugin.setSize("-1");
        depPlugin.setName(depPluginId.getIdString()); //prevent from exceptions

        if (isPluginInRepo(depPluginId, allPlugins)) {
          if (ArrayUtil.indexOf(optionalDependentPluginIds, depPluginId) != -1) {
            optionalDeps.add(depPlugin);
          }
          else {
            depends.add(depPlugin);
          }
        }
      }

      if (depends.size() > 0) { // has something to install prior installing the plugin
        final boolean[] proceed = new boolean[1];
        try {
          GuiUtils.runOrInvokeAndWait(() -> {
            String title = IdeBundle.message("plugin.manager.dependencies.detected.title");
            String deps = StringUtil.join(depends, node -> node.getName(), ", ");
            String message = IdeBundle.message("plugin.manager.dependencies.detected.message", depends.size(), deps);
            proceed[0] = Messages.showYesNoDialog(message, title, Messages.getWarningIcon()) == Messages.YES;
          });
        }
        catch (Exception e) {
          return false;
        }
        if (!proceed[0] || !prepareToInstall(depends, allPlugins, installedDependant, indicator)) {
          return false;
        }
      }

      if (optionalDeps.size() > 0) {
        final boolean[] proceed = new boolean[1];
        try {
          GuiUtils.runOrInvokeAndWait(() -> {
            String title = IdeBundle.message("plugin.manager.dependencies.detected.title");
            String deps = StringUtil.join(optionalDeps, node -> node.getName(), ", ");
            String message = IdeBundle.message("plugin.manager.optional.dependencies.detected.message", optionalDeps.size(), deps);
            proceed[0] = Messages.showYesNoDialog(message, title, Messages.getWarningIcon()) == Messages.YES;
          });
        }
        catch (Exception e) {
          return false;
        }
        if (proceed[0] && !prepareToInstall(optionalDeps, allPlugins, installedDependant, indicator)) {
          return false;
        }
      }
    }

    PluginDownloader downloader = PluginDownloader.createDownloader(pluginNode, pluginNode.getRepositoryName(), null);

    if (downloader.prepareToInstall(indicator)) {
      synchronized (ourLock) {
        downloader.install();
      }
      pluginNode.setStatus(PluginNode.STATUS_DOWNLOADED);
    }
    else {
      return false;
    }

    return true;
  }

  private static boolean isPluginInRepo(PluginId depPluginId, List<PluginId> allPlugins) {
    return allPlugins.contains(depPluginId);
  }

  public static void prepareToUninstall(PluginId pluginId) throws IOException {
    synchronized (ourLock) {
      if (PluginManager.isPluginInstalled(pluginId)) {
        // add command to delete the 'action script' file
        IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(pluginId);
        if (pluginDescriptor != null) {
          StartupActionScriptManager.ActionCommand deleteOld = new StartupActionScriptManager.DeleteCommand(pluginDescriptor.getPath());
          StartupActionScriptManager.addActionCommand(deleteOld);
        }
        else {
          PluginManagerMain.LOG.error("Plugin not found: " + pluginId);
        }
      }
    }
  }

  public static void install(final File fromFile, final String pluginName, boolean deleteFromFile) throws IOException {
    //noinspection HardCodedStringLiteral
    if (fromFile.getName().endsWith(".jar")) {
      // add command to copy file to the IDEA/plugins path
      StartupActionScriptManager.ActionCommand copyPlugin =
        new StartupActionScriptManager.CopyCommand(fromFile, new File(PathManager.getPluginsPath() + File.separator + fromFile.getName()));
      StartupActionScriptManager.addActionCommand(copyPlugin);
    }
    else {
      // add command to unzip file to the IDEA/plugins path
      String unzipPath;
      if (ZipUtil.isZipContainsFolder(fromFile)) {
        unzipPath = PathManager.getPluginsPath();
      }
      else {
        unzipPath = PathManager.getPluginsPath() + File.separator + pluginName;
      }

      StartupActionScriptManager.ActionCommand unzip = new StartupActionScriptManager.UnzipCommand(fromFile, new File(unzipPath));
      StartupActionScriptManager.addActionCommand(unzip);
    }

    // add command to remove temp plugin file
    if (deleteFromFile) {
      StartupActionScriptManager.ActionCommand deleteTemp = new StartupActionScriptManager.DeleteCommand(fromFile);
      StartupActionScriptManager.addActionCommand(deleteTemp);
    }
  }
}
