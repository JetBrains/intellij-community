// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.ide.plugins.marketplace.MarketplacePluginDownloadService;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector;
import com.intellij.ide.plugins.marketplace.statistics.enums.InstallationSourceEnum;
import com.intellij.ide.plugins.org.PluginManagerFilters;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@ApiStatus.Internal
public final class PluginInstallOperation {
  private static final Logger LOG = Logger.getInstance(PluginInstallOperation.class);

  private static final Cache<String, Optional<PluginId>> ourCache = Caffeine
    .newBuilder()
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build();

  private final @NotNull List<PluginNode> myPluginsToInstall;
  private final @NotNull Collection<PluginNode> myCustomReposPlugins;
  private final @NotNull PluginEnabler myPluginEnabler;
  private final @NotNull ProgressIndicator myIndicator;
  private boolean mySuccess = true;
  private final Set<PluginInstallCallbackData> myDependant = new HashSet<>();
  private boolean myAllowInstallWithoutRestart = false;
  private final List<PendingDynamicPluginInstall> myPendingDynamicPluginInstalls = new ArrayList<>();
  private boolean myRestartRequired = false;
  private boolean myShownErrors;
  private MarketplacePluginDownloadService myDownloadService;

  /**
   * @deprecated use {@link #PluginInstallOperation(List, Collection, PluginEnabler, ProgressIndicator)} instead
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @Deprecated
  public PluginInstallOperation(@NotNull List<PluginNode> pluginsToInstall,
                                @NotNull List<? extends IdeaPluginDescriptor> customReposPlugins,
                                @NotNull PluginManagerMain.PluginEnabler pluginEnabler,
                                @NotNull ProgressIndicator indicator) {
    this(pluginsToInstall,
         (Collection<PluginNode>)ContainerUtil.filterIsInstance(customReposPlugins, PluginNode.class),
         pluginEnabler,
         indicator);
  }

  public PluginInstallOperation(@NotNull List<PluginNode> pluginsToInstall,
                                @NotNull Collection<PluginNode> customReposPlugins,
                                @NotNull PluginEnabler pluginEnabler,
                                @NotNull ProgressIndicator indicator) {
    myPluginsToInstall = pluginsToInstall;
    myCustomReposPlugins = customReposPlugins;
    myPluginEnabler = pluginEnabler;
    myIndicator = indicator;

    synchronized (ourInstallLock) {
      for (PluginNode node : pluginsToInstall) {
        PluginId id = node.getPluginId();
        ActionCallback callback = ourInstallCallbacks.get(id);
        if (callback == null) {
          createInstallCallback(id);
        }
        else {
          myLocalWaitInstallCallbacks.put(id, callback);
        }
      }
    }
  }

  private static final Map<PluginId, ActionCallback> ourInstallCallbacks = new IdentityHashMap<>();
  private final Map<PluginId, ActionCallback> myLocalInstallCallbacks = new IdentityHashMap<>();
  private final Map<PluginId, ActionCallback> myLocalWaitInstallCallbacks = new IdentityHashMap<>();
  private static final Object ourInstallLock = new Object();

  private static void removeInstallCallback(@NotNull PluginId id, @NotNull ActionCallback callback, boolean isDone) {
    synchronized (ourInstallLock) {
      ActionCallback oldValue = ourInstallCallbacks.get(id);
      if (oldValue == callback) {
        ourInstallCallbacks.remove(id);
      }
    }
    if (isDone) {
      callback.setDone();
    }
    else {
      callback.setRejected();
    }
  }

  private void createInstallCallback(@NotNull PluginId id) {
    ActionCallback callback = new ActionCallback();
    ourInstallCallbacks.put(id, callback);
    myLocalInstallCallbacks.put(id, callback);
  }

  public void setDownloadService(MarketplacePluginDownloadService downloadService) {
    myDownloadService = downloadService;
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
    Map<PluginId, PluginNode> allPlugins = new HashMap<>();
    for (String host : hosts) {
      try {
        for (PluginNode descriptor : RepositoryHelper.loadPlugins(host, null, myIndicator)) {
          allPlugins.put(descriptor.getPluginId(), descriptor);
        }
      }
      catch (IOException ignored) {
      }
    }

    for (PluginNode node : myPluginsToInstall) {
      if (node.getRepositoryName() == PluginInstaller.UNKNOWN_HOST_MARKER) {
        PluginNode descriptor = allPlugins.get(node.getPluginId());
        node.setRepositoryName(descriptor != null ? descriptor.getRepositoryName() : null);
        if (descriptor != null) {
          node.setDownloadUrl(descriptor.getDownloadUrl());
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
        result |= prepareToInstallWithCallback(pluginNode, pluginIds);
      }
      catch (IOException e) {
        String title = IdeBundle.message("title.plugin.error");
        NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup("Plugin Error");
        Notifications.Bus.notify(group.createNotification(title, pluginNode.getName() + ": " + e.getMessage(), NotificationType.ERROR));
        return false;
      }
    }

    return result;
  }

  private boolean prepareToInstallWithCallback(@NotNull PluginNode pluginNode,
                                               @NotNull List<PluginId> pluginIds) throws IOException {
    PluginId id = pluginNode.getPluginId();
    ActionCallback localCallback = myLocalInstallCallbacks.remove(id);

    if (localCallback == null) {
      ActionCallback callback = myLocalWaitInstallCallbacks.remove(id);
      if (callback == null) {
        return prepareToInstall(pluginNode, pluginIds);
      }
      return callback.waitFor(-1) && callback.isDone();
    }
    else {
      try {
        boolean result = prepareToInstall(pluginNode, pluginIds);
        removeInstallCallback(id, localCallback, result);
        return result;
      }
      catch (IOException | RuntimeException e) {
        removeInstallCallback(id, localCallback, false);
        throw e;
      }
    }
  }

  private boolean prepareToInstall(@NotNull PluginNode pluginNode,
                                   @NotNull List<PluginId> pluginIds) throws IOException {
    if (!checkMissingDependencies(pluginNode, pluginIds)) return false;
    if (!PluginManagerFilters.getInstance().allowInstallingPlugin(pluginNode)) {
      LOG.warn("The plugin " + pluginNode.getPluginId() + " is not allowed to install for the organization");
      return false;
    }
    IdeaPluginDescriptor toDisable = checkDependenciesAndReplacements(pluginNode);

    myShownErrors = false;

    PluginDownloader downloader = PluginDownloader.createDownloader(pluginNode, pluginNode.getRepositoryName(), null);

    IdeaPluginDescriptor previousDescriptor = PluginManagerCore.getPlugin(pluginNode.getPluginId());
    String previousVersion = (previousDescriptor == null) ? null : previousDescriptor.getVersion();
    PluginManagerUsageCollector.pluginInstallationStarted(
      pluginNode,
      downloader.isFromMarketplace() ? InstallationSourceEnum.MARKETPLACE : InstallationSourceEnum.CUSTOM_REPOSITORY,
      previousVersion
    );

    boolean prepared = downloader.prepareToInstall(myIndicator);
    if (prepared) {
      IdeaPluginDescriptorImpl descriptor = (IdeaPluginDescriptorImpl)downloader.getDescriptor();

      if (pluginNode.getDependencies().isEmpty() && !descriptor.getDependencies().isEmpty()) {  // installing from custom plugins repo
        if (!checkMissingDependencies(descriptor, pluginIds)) return false;
      }

      boolean allowNoRestart = myAllowInstallWithoutRestart &&
                               DynamicPlugins.allowLoadUnloadWithoutRestart(descriptor);
      if (allowNoRestart) {
        myPendingDynamicPluginInstalls.add(new PendingDynamicPluginInstall(downloader.getFilePath(), descriptor));
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
      myDependant.add(new PluginInstallCallbackData(downloader.getFilePath(), descriptor, !allowNoRestart));
      pluginNode.setStatus(PluginNode.Status.DOWNLOADED);
      if (toDisable != null) {
        myPluginEnabler.disable(Set.of(toDisable));
      }

      return true;
    }
    else {
      myShownErrors = downloader.isShownErrors();
      return false;
    }
  }

  @Nullable IdeaPluginDescriptor checkDependenciesAndReplacements(@NotNull IdeaPluginDescriptor pluginNode) {
    PluginReplacement pluginReplacement = ContainerUtil.find(PluginReplacement.EP_NAME.getExtensions(),
                                                             r -> r.getNewPluginId().equals(pluginNode.getPluginId().getIdString()));
    if (pluginReplacement == null) {
      return null;
    }

    PluginId oldPluginId = pluginReplacement.getOldPluginDescriptor().getPluginId();
    IdeaPluginDescriptor oldPlugin = PluginManagerCore.getPlugin(oldPluginId);
    if (oldPlugin == null) {
      LOG.warn("Plugin with id '" + oldPluginId + "' not found");
      return null;
    }

    if (myPluginEnabler.isDisabled(oldPlugin.getPluginId())) {
      return null;
    }

    AtomicBoolean toDisable = new AtomicBoolean();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      boolean choice = MessageDialogBuilder.yesNo(pluginReplacement.getReplacementMessage(oldPlugin, pluginNode),
                                                  IdeBundle.message("plugin.manager.obsolete.plugins.detected.title"))
        .yesText(IdeBundle.message("plugins.configurable.disable")).noText(Messages.getNoButton()).icon(Messages.getWarningIcon())
        .guessWindowAndAsk();
      toDisable.set(choice);
    }, ModalityState.any());

    return toDisable.get() ? oldPlugin : null;
  }

  boolean checkMissingDependencies(@NotNull IdeaPluginDescriptor pluginNode,
                                   @Nullable List<PluginId> pluginIds) {
    // check for dependent plugins at first.
    List<IdeaPluginDependency> dependencies = pluginNode.getDependencies();
    if (dependencies.isEmpty()) {
      return true;
    }

    // prepare plugins list for install
    final List<PluginNode> depends = new ArrayList<>();
    final List<PluginNode> optionalDeps = new ArrayList<>();
    for (IdeaPluginDependency dependency : dependencies) {
      PluginId depPluginId = dependency.getPluginId();

      if (PluginManagerCore.isModuleDependency(depPluginId)) {
        IdeaPluginDescriptorImpl descriptorByModule = PluginManagerCore.findPluginByModuleDependency(depPluginId);
        PluginId pluginIdByModule = descriptorByModule != null ?
                                    descriptorByModule.getPluginId() :
                                    getCachedPluginId(depPluginId.getIdString());

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

      PluginNode depPluginDescriptor = findPluginInRepo(depPluginId);
      if (depPluginDescriptor != null) {
        (dependency.isOptional() ? optionalDeps : depends).add(depPluginDescriptor);
      }
    }

    if (!prepareDependencies(pluginNode, depends, "plugin.manager.dependencies.detected.title",
                             "plugin.manager.dependencies.detected.message")) {
      return false;
    }

    return !Registry.is("ide.plugins.suggest.install.optional.dependencies") ||
           prepareDependencies(pluginNode, optionalDeps, "plugin.manager.optional.dependencies.detected.title",
                               "plugin.manager.optional.dependencies.detected.message");
  }

  private boolean prepareDependencies(@NotNull IdeaPluginDescriptor pluginNode,
                                      @NotNull List<PluginNode> dependencies,
                                      @NotNull @NonNls String titleKey,
                                      @NotNull @NonNls String messageKey) {
    if (dependencies.isEmpty()) {
      return true;
    }

    try {
      final boolean[] result = new boolean[1];
      ApplicationManager.getApplication().invokeAndWait(() -> {
        synchronized (ourInstallLock) {
          InstalledPluginsState pluginsState = InstalledPluginsState.getInstance();
          Set<PluginId> dependenciesToShow = new LinkedHashSet<>();
          for (Iterator<PluginNode> iterator = dependencies.iterator(); iterator.hasNext(); ) {
            PluginId pluginId = iterator.next().getPluginId();
            ActionCallback callback = ourInstallCallbacks.get(pluginId);
            if (callback == null || callback.isRejected()) {
              if (pluginsState.wasInstalled(pluginId) || pluginsState.wasInstalledWithoutRestart(pluginId)) {
                iterator.remove();
                continue;
              }
              dependenciesToShow.add(pluginId);
            }
            else {
              myLocalWaitInstallCallbacks.put(pluginId, callback);
            }
          }

          if (dependenciesToShow.isEmpty()) {
            result[0] = true;
            return;
          }

          String deps = getPluginsText(dependencies);
          int dialogResult =
            Messages.showYesNoDialog(IdeBundle.message(messageKey, pluginNode.getName(), deps),
                                     IdeBundle.message(titleKey),
                                     IdeBundle.message("plugins.configurable.install"),
                                     Messages.getCancelButton(),
                                     Messages.getWarningIcon());

          result[0] = dialogResult == Messages.YES;
          if (result[0]) {
            for (PluginId dependency : dependenciesToShow) {
              createInstallCallback(dependency);
            }
          }
        }
      }, ModalityState.any());

      return dependencies.isEmpty() ||
             result[0] && prepareToInstall(dependencies);
    }
    catch (Exception e) {
      return false;
    }
  }

  private static @NotNull @Nls String getPluginsText(@NotNull List<PluginNode> nodes) {
    List<String> pluginNames = ContainerUtil.map(nodes,
                                                 node -> StringUtil.wrapWithDoubleQuote(node.getName()));

    int size = pluginNames.size();
    if (size == 1) {
      return pluginNames.get(0);
    }

    return NlsMessages.formatAndList(pluginNames);
  }

  /**
   * Searches for plugin with id 'depPluginId' in custom repos and Marketplace and then takes one with bigger version number
   */
  private @Nullable PluginNode findPluginInRepo(@NotNull PluginId depPluginId) {
    PluginNode pluginFromCustomRepos = myCustomReposPlugins.stream()
      .parallel()
      .filter(p -> p.getPluginId().equals(depPluginId))
      .findAny()
      .orElse(null);

    PluginNode pluginFromMarketplace = MarketplaceRequests.getInstance()
      .getLastCompatiblePluginUpdate(depPluginId);

    boolean fromCustomRepos = pluginFromMarketplace == null ||
                              pluginFromCustomRepos != null &&
                              PluginDownloader.compareVersionsSkipBrokenAndIncompatible(pluginFromCustomRepos.getVersion(),
                                                                                        pluginFromMarketplace) > 0;
    return fromCustomRepos ? pluginFromCustomRepos : pluginFromMarketplace;
  }

  private static @Nullable PluginId getCachedPluginId(@NotNull String pluginId) {
    Optional<PluginId> cachedModule = ourCache.getIfPresent(pluginId);
    if (cachedModule != null && cachedModule.isPresent()) {
      return cachedModule.get();
    }

    PluginId result = MarketplaceRequests.getInstance().getCompatibleUpdateByModule(pluginId);
    ourCache.put(pluginId, Optional.ofNullable(result));
    return result;
  }
}
