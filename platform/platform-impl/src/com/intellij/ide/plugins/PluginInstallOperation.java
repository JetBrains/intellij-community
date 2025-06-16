// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.ide.plugins.marketplace.MarketplacePluginDownloadService;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector;
import com.intellij.ide.plugins.marketplace.statistics.enums.InstallationSourceEnum;
import com.intellij.ide.plugins.newui.PluginUiModel;
import com.intellij.ide.plugins.newui.PluginUiModelAdapter;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
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

  private final @NotNull List<PluginUiModel> myPluginsToInstall;
  private final @NotNull Collection<PluginUiModel> myCustomReposPlugins;
  private final @NotNull PluginEnabler myPluginEnabler;
  private final @NotNull ProgressIndicator myIndicator;
  private boolean mySuccess = true;
  private final Set<PluginInstallCallbackData> myDependant = new HashSet<>();
  private boolean myAllowInstallWithoutRestart = false;
  private final List<PendingDynamicPluginInstall> myPendingDynamicPluginInstalls = new ArrayList<>();
  private boolean myRestartRequired = false;
  private boolean myShownErrors;
  private MarketplacePluginDownloadService myDownloadService;

  public PluginInstallOperation(@NotNull List<PluginNode> pluginsToInstall,
                                @NotNull Collection<PluginNode> customReposPlugins,
                                @NotNull PluginEnabler pluginEnabler,
                                @NotNull ProgressIndicator indicator) {
    this(ContainerUtil.map(pluginsToInstall, PluginUiModelAdapter::new),
         ContainerUtil.map(customReposPlugins, PluginUiModelAdapter::new),
         indicator,
         pluginEnabler);
  }

  public PluginInstallOperation(@NotNull List<PluginUiModel> pluginsToInstall,
                                @NotNull Collection<PluginUiModel> customReposPlugins,
                                @NotNull ProgressIndicator indicator,
                                @NotNull PluginEnabler pluginEnabler) {
    myPluginsToInstall = pluginsToInstall;
    myCustomReposPlugins = customReposPlugins;
    myPluginEnabler = pluginEnabler;
    myIndicator = indicator;

    synchronized (ourInstallLock) {
      for (PluginUiModel node : pluginsToInstall) {
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
    for (PluginUiModel node : myPluginsToInstall) {
      if (Strings.areSameInstance(node.getRepositoryName(), PluginInstaller.UNKNOWN_HOST_MARKER)) {
        unknownNodes = true;
        break;
      }
    }
    if (!unknownNodes) return;

    Map<PluginId, PluginUiModel> allPlugins = new HashMap<>();
    for (String host : RepositoryHelper.getCustomPluginRepositoryHosts()) {
      try {
        for (PluginUiModel descriptor : RepositoryHelper.loadPluginModels(host, null, myIndicator)) {
          allPlugins.put(descriptor.getPluginId(), descriptor);
        }
      }
      catch (IOException ignored) {
      }
    }

    for (PluginUiModel node : myPluginsToInstall) {
      if (Strings.areSameInstance(node.getRepositoryName(), PluginInstaller.UNKNOWN_HOST_MARKER)) {
        PluginUiModel descriptor = allPlugins.get(node.getPluginId());
        node.setRepositoryName(descriptor != null ? descriptor.getRepositoryName() : null);
        String oldUrl = node.getDownloadUrl();
        if (descriptor != null) {
          node.setDownloadUrl(descriptor.getDownloadUrl());
        }
        LOG.info("updateUrls for node: " +
                 node.getPluginId() + " | " + node.getVersion() + " | " + oldUrl +
                 " to: " + node.getRepositoryName() + " | " + node.getDownloadUrl());
      }
    }
  }

  private boolean prepareToInstall(@NotNull List<PluginUiModel> pluginsToInstall) {
    List<PluginId> pluginIds = new SmartList<>();
    for (PluginUiModel pluginNode : pluginsToInstall) {
      pluginIds.add(pluginNode.getPluginId());
    }

    boolean result = false;
    for (PluginUiModel pluginNode : pluginsToInstall) {
      myIndicator.setText(pluginNode.getName());
      try {
        result |= prepareToInstallWithCallback(pluginNode, pluginIds);
      }
      catch (IOException e) {
        String title = IdeBundle.message("title.plugin.error");
        LOG.warn(e);
        NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup("Plugin Error");
        Notifications.Bus.notify(group.createNotification(title, pluginNode.getName() + ": " + e.getMessage(), NotificationType.ERROR));
        return false;
      }
    }

    return result;
  }

  private boolean prepareToInstallWithCallback(@NotNull PluginUiModel pluginNode,
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

  @RequiresBackgroundThread
  private boolean prepareToInstall(@NotNull PluginUiModel pluginNode,
                                   @NotNull List<PluginId> pluginIds) throws IOException {
    if (!checkMissingDependencies(pluginNode.getDescriptor(), pluginIds)) return false;
    if (!PluginManagementPolicy.getInstance().canInstallPlugin(pluginNode.getDescriptor())) {
      LOG.warn("The plugin " + pluginNode.getPluginId() + " is not allowed to install for the organization");
      return false;
    }
    IdeaPluginDescriptor toDisable = checkDependenciesAndReplacements(pluginNode.getDescriptor());

    myShownErrors = false;

    PluginDownloader downloader = PluginDownloader.createDownloader(pluginNode, pluginNode.getRepositoryName(), null);

    IdeaPluginDescriptor previousDescriptor = PluginManagerCore.getPlugin(pluginNode.getPluginId());
    String previousVersion = (previousDescriptor == null) ? null : previousDescriptor.getVersion();
    PluginManagerUsageCollector.pluginInstallationStarted(
      pluginNode.getDescriptor(),
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
                               DynamicPlugins.allowLoadUnloadWithoutRestart(
                                 descriptor, null,
                                 ContainerUtil.map(myPendingDynamicPluginInstalls, pluginInstall -> pluginInstall.getPluginDescriptor())
                               );
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
      if(pluginNode.getDescriptor() instanceof PluginNode node){
        node.setStatus(PluginNode.Status.DOWNLOADED);
      }
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
    final List<PluginUiModel> depends = new ArrayList<>();
    final List<PluginUiModel> optionalDeps = new ArrayList<>();
    for (IdeaPluginDependency dependency : dependencies) {
      PluginId depPluginId = dependency.getPluginId();

      if (PluginManagerCore.looksLikePlatformPluginAlias(depPluginId)) {
        IdeaPluginDescriptorImpl descriptorByModule = PluginManagerCore.findPluginByPlatformAlias(depPluginId);
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

      PluginUiModel depPluginDescriptor = findPluginInRepo(depPluginId);
      if (depPluginDescriptor != null) {
        (dependency.isOptional() ? optionalDeps : depends).add(depPluginDescriptor);
      }
    }

    if (!prepareDependencies(pluginNode, depends, "plugin.manager.dependencies.detected.title",
                             "plugin.manager.dependencies.detected.message", false)) {
      return false;
    }

    return !Registry.is("ide.plugins.suggest.install.optional.dependencies") ||
           prepareDependencies(pluginNode, optionalDeps, "plugin.manager.optional.dependencies.detected.title",
                               "plugin.manager.optional.dependencies.detected.message", true);
  }

  private boolean prepareDependencies(@NotNull IdeaPluginDescriptor pluginNode,
                                      @NotNull List<PluginUiModel> dependencies,
                                      @NotNull @NonNls String titleKey,
                                      @NotNull @NonNls String messageKey,
                                      boolean askConfirmation) {
    if (dependencies.isEmpty()) {
      return true;
    }

    try {
      Ref<Boolean> result = new Ref<>(false);

      ApplicationManager.getApplication().invokeAndWait(() -> {
        synchronized (ourInstallLock) {
          InstalledPluginsState pluginsState = InstalledPluginsState.getInstance();
          Set<PluginId> dependenciesToShow = new LinkedHashSet<>();
          for (Iterator<PluginUiModel> iterator = dependencies.iterator(); iterator.hasNext(); ) {
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
            result.set(true);
            return;
          }

          if (!askConfirmation) {
            for (PluginId dependency : dependenciesToShow) {
              createInstallCallback(dependency);
            }
            result.set(true);
          }
          else {
            String deps = getPluginsText(dependencies);
            int dialogResult =
              Messages.showYesNoDialog(IdeBundle.message(messageKey, pluginNode.getName(), deps),
                                       IdeBundle.message(titleKey),
                                       IdeBundle.message("plugins.configurable.install"),
                                       Messages.getCancelButton(),
                                       Messages.getWarningIcon());

            result.set(dialogResult == Messages.YES);
            if (result.get()) {
              for (PluginId dependency : dependenciesToShow) {
                createInstallCallback(dependency);
              }
            }
          }
        }
      }, ModalityState.any());

      return dependencies.isEmpty() ||
             result.get() && prepareToInstall(dependencies);
    }
    catch (Exception e) {
      return false;
    }
  }

  private static @NotNull @Nls String getPluginsText(@NotNull List<PluginUiModel> nodes) {
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
  private @Nullable PluginUiModel findPluginInRepo(@NotNull PluginId depPluginId) {
    PluginUiModel pluginFromCustomRepos = myCustomReposPlugins.stream()
      .parallel()
      .filter(p -> p.getPluginId().equals(depPluginId))
      .findAny()
      .orElse(null);

    PluginUiModel pluginFromMarketplace = MarketplaceRequests.getInstance()
      .getLastCompatiblePluginUpdateModel(depPluginId);

    boolean fromCustomRepos = pluginFromMarketplace == null ||
                              pluginFromCustomRepos != null &&
                              PluginDownloader.compareVersionsSkipBrokenAndIncompatible(pluginFromCustomRepos.getVersion(),
                                                                                        pluginFromMarketplace.getDescriptor()) > 0;
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
