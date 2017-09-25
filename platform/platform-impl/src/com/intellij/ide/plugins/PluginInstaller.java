/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.ide.startup.StartupActionScriptManager.DeleteCommand;
import com.intellij.ide.startup.StartupActionScriptManager.UnzipCommand;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author stathik
 * @since Nov 29, 2003
 */
public class PluginInstaller {
  private static final Logger LOG = Logger.getInstance(PluginInstaller.class);
  public static final String UNKNOWN_HOST_MARKER = "__unknown_repository__";

  private static final Object ourLock = new Object();

  private PluginInstaller() { }

  public static boolean prepareToInstall(List<PluginNode> pluginsToInstall,
                                         List<IdeaPluginDescriptor> allPlugins,
                                         PluginManagerMain.PluginEnabler pluginEnabler,
                                         @NotNull ProgressIndicator indicator) {
    updateUrls(pluginsToInstall, indicator);
    Set<PluginNode> dependant = new THashSet<>();
    boolean install = prepareToInstall(pluginsToInstall, allPlugins, dependant, pluginEnabler, indicator);
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
                                          List<IdeaPluginDescriptor> allPlugins,
                                          Set<PluginNode> installedDependant,
                                          PluginManagerMain.PluginEnabler pluginEnabler,
                                          @NotNull ProgressIndicator indicator) {
    List<PluginId> pluginIds = new SmartList<>();
    for (PluginNode pluginNode : pluginsToInstall) {
      pluginIds.add(pluginNode.getPluginId());
    }

    boolean result = false;
    for (PluginNode pluginNode : pluginsToInstall) {
      indicator.setText(pluginNode.getName());
      try {
        result |= prepareToInstall(pluginNode, pluginIds, allPlugins, installedDependant, pluginEnabler, indicator);
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
                                          List<IdeaPluginDescriptor> allPlugins,
                                          Set<PluginNode> installedDependant,
                                          PluginManagerMain.PluginEnabler pluginEnabler,
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

        IdeaPluginDescriptor depPluginDescriptor = findPluginInRepo(depPluginId, allPlugins);
        PluginNode depPlugin;
        if (depPluginDescriptor instanceof PluginNode) {
          depPlugin = (PluginNode) depPluginDescriptor;
        } else {
          depPlugin = new PluginNode(depPluginId, depPluginId.getIdString(), "-1");
        }

        if (depPluginDescriptor != null) {
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
        if (!proceed[0] || !prepareToInstall(depends, allPlugins, installedDependant, pluginEnabler, indicator)) {
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
        if (proceed[0] && !prepareToInstall(optionalDeps, allPlugins, installedDependant, pluginEnabler, indicator)) {
          return false;
        }
      }
    }

    Ref<IdeaPluginDescriptor> toDisable = Ref.create(null);
    Optional<PluginReplacement> replacement = StreamEx.of(PluginReplacement.EP_NAME.getExtensions())
      .findFirst(r -> r.getNewPluginId().equals(pluginNode.getPluginId().getIdString()));
    if (replacement.isPresent()) {
      PluginReplacement pluginReplacement = replacement.get();
      IdeaPluginDescriptor oldPlugin = PluginManager.getPlugin(pluginReplacement.getOldPluginDescriptor().getPluginId());
      if (oldPlugin == null) {
        LOG.warn("Plugin with id '" + pluginReplacement.getOldPluginDescriptor().getPluginId() + "' not found");
      }
      else if (!pluginEnabler.isDisabled(oldPlugin.getPluginId())) {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          String title = IdeBundle.message("plugin.manager.obsolete.plugins.detected.title");
          String message = pluginReplacement.getReplacementMessage(oldPlugin, pluginNode);
          if (Messages.showYesNoDialog(message, title, Messages.getWarningIcon()) == Messages.YES) {
            toDisable.set(oldPlugin);
          }
        });
      }
    }

    PluginDownloader downloader = PluginDownloader.createDownloader(pluginNode, pluginNode.getRepositoryName(), null);

    if (downloader.prepareToInstall(indicator)) {
      synchronized (ourLock) {
        downloader.install();
      }
      pluginNode.setStatus(PluginNode.STATUS_DOWNLOADED);
      if (!toDisable.isNull()) {
        pluginEnabler.disablePlugins(Collections.singleton(toDisable.get()));
      }
    }
    else {
      return false;
    }

    return true;
  }

  @Nullable
  private static IdeaPluginDescriptor findPluginInRepo(PluginId depPluginId, List<IdeaPluginDescriptor> allPlugins) {
    return allPlugins.stream().parallel().filter(p -> p.getPluginId().equals(depPluginId)).findAny().orElse(null);
  }

  public static void prepareToUninstall(PluginId pluginId) throws IOException {
    synchronized (ourLock) {
      if (PluginManager.isPluginInstalled(pluginId)) {
        // add command to delete the 'action script' file
        IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(pluginId);
        if (pluginDescriptor != null) {
          StartupActionScriptManager.ActionCommand deleteOld = new DeleteCommand(pluginDescriptor.getPath());
          StartupActionScriptManager.addActionCommand(deleteOld);

          fireState(pluginDescriptor, false);
        }
        else {
          PluginManagerMain.LOG.error("Plugin not found: " + pluginId);
        }
      }
    }
  }

  public static void install(@NotNull File fromFile,
                             @NotNull String pluginName,
                             boolean deleteFromFile,
                             @NotNull IdeaPluginDescriptor descriptor) throws IOException {
    List<StartupActionScriptManager.ActionCommand> commands = new ArrayList<>();

    if (fromFile.getName().endsWith(".jar")) {
      // add command to copy file to the IDEA/plugins path
      commands.add(new StartupActionScriptManager.CopyCommand(fromFile, new File(PathManager.getPluginsPath(), fromFile.getName())));
    }
    else {
      // add command to unzip file to the IDEA/plugins path
      String unzipPath;
      String dir = findFirstTopLevelDirectoryName(fromFile);
      if (dir != null) {
        unzipPath = PathManager.getPluginsPath();
        commands.add(new DeleteCommand(new File(unzipPath, dir)));
      }
      else {
        unzipPath = PathManager.getPluginsPath() + File.separator + pluginName;
        commands.add(new DeleteCommand(new File(unzipPath)));
      }

      commands.add(new UnzipCommand(fromFile, new File(unzipPath)));
    }

    // add command to remove temp plugin file
    if (deleteFromFile) {
      commands.add(new DeleteCommand(fromFile));
    }

    StartupActionScriptManager.addActionCommands(commands);

    fireState(descriptor, true);
  }

  @Nullable
  public static String findFirstTopLevelDirectoryName(@NotNull File zip) throws IOException {
    try (ZipFile zipFile = new ZipFile(zip)) {
      Enumeration en = zipFile.entries();
      while (en.hasMoreElements()) {
        ZipEntry zipEntry = (ZipEntry)en.nextElement();
        // we do not necessarily get a separate entry for the subdirectory when the file
        // in the ZIP archive is placed in a subdirectory, so we need to check if the slash
        // is found anywhere in the path
        String name = zipEntry.getName();
        int i = name.indexOf('/');
        if (i >= 0) return name.substring(0, i);
      }
    }
    return null;
  }

  private static List<PluginStateListener> myStateListeners;

  public static void addStateListener(@NotNull PluginStateListener listener) {
    if (myStateListeners == null) {
      myStateListeners = new ArrayList<>();
    }
    myStateListeners.add(listener);
  }

  public static void removeStateListener(@NotNull PluginStateListener listener) {
    if (myStateListeners != null) {
      myStateListeners.remove(listener);
    }
  }

  private static void fireState(@NotNull IdeaPluginDescriptor descriptor, boolean install) {
    if (myStateListeners != null) {
      UIUtil.invokeLaterIfNeeded(() -> {
        for (PluginStateListener listener : myStateListeners) {
          if (install) {
            listener.install(descriptor);
          }
          else {
            listener.uninstall(descriptor);
          }
        }
      });
    }
  }
}
