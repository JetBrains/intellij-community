// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.ide.plugins.marketplace.PluginModulesHelper;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

public class PluginInstallOperation {
  private static final Logger LOG = Logger.getInstance(PluginInstallOperation.class);

  private final List<PluginNode> myPluginsToInstall;
  private final Collection<? extends IdeaPluginDescriptor> myCustomReposPlugins;
  private final PluginManagerMain.PluginEnabler myPluginEnabler;
  private final ProgressIndicator myIndicator;
  private boolean mySuccess = true;
  private final Set<PluginInstallCallbackData> myDependant = new HashSet<>();
  private boolean myAllowInstallWithoutRestart = false;
  private final List<PendingDynamicPluginInstall> myPendingDynamicPluginInstalls = new ArrayList<>();
  private boolean myRestartRequired = false;
  private boolean myShownErrors;

  /**
   * @deprecated use {@link #PluginInstallOperation(List, Collection, PluginManagerMain.PluginEnabler, ProgressIndicator)} instead
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @Deprecated
  public PluginInstallOperation(@NotNull List<PluginNode> pluginsToInstall,
                                List<? extends IdeaPluginDescriptor> customReposPlugins,
                                PluginManagerMain.PluginEnabler pluginEnabler,
                                @NotNull ProgressIndicator indicator) {
    this(pluginsToInstall, (Collection<? extends IdeaPluginDescriptor>)customReposPlugins, pluginEnabler, indicator);
  }

  public PluginInstallOperation(@NotNull List<PluginNode> pluginsToInstall,
                                Collection<? extends IdeaPluginDescriptor> customReposPlugins,
                                PluginManagerMain.PluginEnabler pluginEnabler,
                                @NotNull ProgressIndicator indicator) {

    myPluginsToInstall = pluginsToInstall;
    myCustomReposPlugins = customReposPlugins;
    myPluginEnabler = pluginEnabler;
    myIndicator = indicator;
  }

  public void setAllowInstallWithoutRestart(boolean allowInstallWithoutRestart) {
    myAllowInstallWithoutRestart = allowInstallWithoutRestart;
  }

  public List<PendingDynamicPluginInstall> getPendingDynamicPluginInstalls() {
    return myPendingDynamicPluginInstalls;
  }

  public boolean isRestartRequired() {
    return myRestartRequired;
  }

  public void run() {
    updateUrls();
    mySuccess = prepareToInstall(myPluginsToInstall);
  }

  public boolean isSuccess() {
    return mySuccess;
  }

  public Set<PluginInstallCallbackData> getInstalledDependentPlugins() {
    return myDependant;
  }

  public boolean isShownErrors() {
    return myShownErrors;
  }

  private void updateUrls() {
    boolean unknownNodes = false;
    for (PluginNode node : myPluginsToInstall) {
      if (node.getRepositoryName() == PluginInstaller.UNKNOWN_HOST_MARKER) {
        unknownNodes = true;
        break;
      }
    }
    if (!unknownNodes) return;

    List<String> hosts = new SmartList<>();
    ContainerUtil.addIfNotNull(hosts, ApplicationInfoEx.getInstanceEx().getBuiltinPluginsUrl());
    hosts.addAll(UpdateSettings.getInstance().getPluginHosts());
    Map<PluginId, IdeaPluginDescriptor> allPlugins = new HashMap<>();
    for (String host : hosts) {
      try {
        List<IdeaPluginDescriptor> descriptors = RepositoryHelper.loadPlugins(host, myIndicator);
        for (IdeaPluginDescriptor descriptor : descriptors) {
          allPlugins.put(descriptor.getPluginId(), descriptor);
        }
      }
      catch (IOException ignored) {
      }
    }

    for (PluginNode node : myPluginsToInstall) {
      if (node.getRepositoryName() == PluginInstaller.UNKNOWN_HOST_MARKER) {
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

  private boolean prepareToInstall(@NotNull List<PluginNode> pluginsToInstall) {
    List<PluginId> pluginIds = new SmartList<>();
    for (PluginNode pluginNode : pluginsToInstall) {
      pluginIds.add(pluginNode.getPluginId());
    }

    boolean result = false;
    for (PluginNode pluginNode : pluginsToInstall) {
      myIndicator.setText(pluginNode.getName());
      try {
        result |= prepareToInstall(pluginNode, pluginIds);
      }
      catch (IOException e) {
        String title = IdeBundle.message("title.plugin.error");
        Notifications.Bus.notify(
          new Notification(NotificationGroup.createIdWithTitle("Plugin Error", title), title, pluginNode.getName() + ": " + e.getMessage(),
                           NotificationType.ERROR));
        return false;
      }
    }

    return result;
  }


  private boolean prepareToInstall(PluginNode pluginNode, List<PluginId> pluginIds) throws IOException {
    Ref<IdeaPluginDescriptor> toDisable = checkDependenciesAndReplacements(pluginNode, pluginIds);
    if (toDisable == null) return false;

    myShownErrors = false;

    PluginDownloader downloader = PluginDownloader.createDownloader(pluginNode, pluginNode.getRepositoryName(), null);

    IdeaPluginDescriptorImpl descriptor = downloader.prepareToInstallAndLoadDescriptor(myIndicator);
    if (descriptor != null) {
      boolean allowNoRestart = myAllowInstallWithoutRestart && DynamicPlugins.allowLoadUnloadWithoutRestart(descriptor);
      if (allowNoRestart) {
        myPendingDynamicPluginInstalls.add(new PendingDynamicPluginInstall(downloader.getFile(), descriptor));
        InstalledPluginsState state = InstalledPluginsState.getInstanceIfLoaded();
        if (state != null) {
          state.onPluginInstall(downloader.getDescriptor(), false, false);
        }
      }
      else {
        myRestartRequired = true;
        synchronized (PluginInstaller.ourLock) {
          downloader.install();
        }
      }
      myDependant.add(new PluginInstallCallbackData(downloader.getFile(), descriptor, !allowNoRestart));
      pluginNode.setStatus(PluginNode.Status.DOWNLOADED);
      if (!toDisable.isNull()) {
        myPluginEnabler.disablePlugins(Collections.singleton(toDisable.get()));
      }
    }
    else {
      myShownErrors = downloader.isShownErrors();
      return false;
    }

    return true;
  }

  @Nullable
  public Ref<IdeaPluginDescriptor> checkDependenciesAndReplacements(IdeaPluginDescriptor pluginNode, @Nullable List<PluginId> pluginIds) {
    // check for dependent plugins at first.
    List<IdeaPluginDependency> dependencies = pluginNode.getDependencies();
    if (!dependencies.isEmpty()) {
      // prepare plugins list for install
      final List<PluginNode> depends = new ArrayList<>();
      final List<PluginNode> optionalDeps = new ArrayList<>();
      for (IdeaPluginDependency dependency : dependencies) {
        PluginId depPluginId = dependency.getPluginId();

        if (PluginManagerCore.isModuleDependency(depPluginId)) {
          PluginId pluginIdByModule = PluginModulesHelper.getInstance().getMarketplacePluginIdByModule(depPluginId);
          if (pluginIdByModule == null) continue;
          depPluginId = pluginIdByModule;
        }
        if (PluginManagerCore.isPluginInstalled(depPluginId) ||
            InstalledPluginsState.getInstance().wasInstalled(depPluginId) ||
            InstalledPluginsState.getInstance().wasInstalledWithoutRestart(depPluginId) ||
            pluginIds != null && pluginIds.contains(depPluginId)) {
          // ignore installed or installing plugins
          continue;
        }

        IdeaPluginDescriptor depPluginDescriptor = findPluginInRepo(depPluginId);
        PluginNode depPlugin;
        if (depPluginDescriptor instanceof PluginNode) {
          depPlugin = (PluginNode)depPluginDescriptor;
        }
        else {
          depPlugin = new PluginNode(depPluginId, depPluginId.getIdString(), "-1");
        }

        if (depPluginDescriptor != null) {
          if (dependency.isOptional()) {
            optionalDeps.add(depPlugin);
          }
          else {
            depends.add(depPlugin);
          }
        }
      }

      if (!depends.isEmpty()) { // has something to install prior installing the plugin
        final boolean[] proceed = new boolean[1];
        try {
          ApplicationManager.getApplication().invokeAndWait(() -> {
            String title = IdeBundle.message("plugin.manager.dependencies.detected.title");
            String deps = getPluginsText(depends);
            String message = IdeBundle.message("plugin.manager.dependencies.detected.message", pluginNode.getName(), deps);
            proceed[0] = Messages.showYesNoDialog(message, title, IdeBundle.message("button.install"), Messages.getNoButton(),
                                                  Messages.getWarningIcon()) == Messages.YES;
          }, ModalityState.any());
        }
        catch (Exception e) {
          return null;
        }
        if (!proceed[0] || !prepareToInstall(depends)) {
          return null;
        }
      }

      if (!optionalDeps.isEmpty()) {
        final boolean[] proceed = new boolean[1];
        try {
          ApplicationManager.getApplication().invokeAndWait(() -> {
            String title = IdeBundle.message("plugin.manager.optional.dependencies.detected.title");
            String deps = getPluginsText(optionalDeps);
            String message = IdeBundle.message("plugin.manager.optional.dependencies.detected.message", pluginNode.getName(), deps);
            proceed[0] = Messages.showYesNoDialog(message, title, IdeBundle.message("button.install"), Messages.getNoButton(),
                                                  Messages.getWarningIcon()) == Messages.YES;
          }, ModalityState.any());
        }
        catch (Exception e) {
          return null;
        }
        if (proceed[0] && !prepareToInstall(optionalDeps)) {
          return null;
        }
      }
    }

    Ref<IdeaPluginDescriptor> toDisable = Ref.create(null);
    PluginReplacement pluginReplacement = ContainerUtil.find(PluginReplacement.EP_NAME.getExtensions(),
                                                             r -> r.getNewPluginId().equals(pluginNode.getPluginId().getIdString()));
    if (pluginReplacement != null) {
      IdeaPluginDescriptor oldPlugin = PluginManagerCore.getPlugin(pluginReplacement.getOldPluginDescriptor().getPluginId());
      if (oldPlugin == null) {
        LOG.warn("Plugin with id '" + pluginReplacement.getOldPluginDescriptor().getPluginId() + "' not found");
      }
      else if (!myPluginEnabler.isDisabled(oldPlugin.getPluginId())) {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          String title = IdeBundle.message("plugin.manager.obsolete.plugins.detected.title");
          String message = pluginReplacement.getReplacementMessage(oldPlugin, pluginNode);
          if (Messages
                .showYesNoDialog(message, title, IdeBundle.message("button.disable"), Messages.getNoButton(), Messages.getWarningIcon()) ==
              Messages.YES) {
            toDisable.set(oldPlugin);
          }
        }, ModalityState.any());
      }
    }
    return toDisable;
  }

  @NotNull
  private static String getPluginsText(@NotNull List<PluginNode> pluginNodes) {
    int size = pluginNodes.size();
    if (size == 1) {
      return "\"" + pluginNodes.get(0).getName() + "\" plugin";
    }
    return StringUtil.join(pluginNodes.subList(0, size - 1), node -> "\"" + node.getName() + "\"", ", ") +
           " and \"" + pluginNodes.get(size - 1) + "\" plugins";
  }

  /**
   * Searches for plugin with id 'depPluginId' in custom repos and Marketplace and then takes one with bigger version number
   */
  @Nullable
  private IdeaPluginDescriptor findPluginInRepo(PluginId depPluginId) {
    IdeaPluginDescriptor pluginFromCustomRepos =
      myCustomReposPlugins.stream().parallel().filter(p -> p.getPluginId().equals(depPluginId)).findAny().orElse(null);
    PluginNode pluginFromMarketplace = MarketplaceRequests.getInstance().getLastCompatiblePluginUpdate(depPluginId.getIdString());
    if (pluginFromCustomRepos == null) {
      return pluginFromMarketplace;
    }
    if (pluginFromMarketplace == null) {
      return pluginFromCustomRepos;
    }
    if (PluginDownloader.compareVersionsSkipBrokenAndIncompatible(pluginFromCustomRepos.getVersion(), pluginFromMarketplace) > 0) {
      return pluginFromCustomRepos;
    } else {
      return pluginFromMarketplace;
    }
  }
}
