// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.newEditor.SettingsDialog;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Alexander Lobas
 */
public class MyPluginModel extends InstalledPluginsTableModel implements PluginEnabler {
  private static final Logger LOG = Logger.getInstance(MyPluginModel.class);

  private final List<ListPluginComponent> myInstalledPluginComponents = new ArrayList<>();
  private final Map<PluginId, List<ListPluginComponent>> myInstalledPluginComponentMap = new HashMap<>();
  private final Map<PluginId, List<ListPluginComponent>> myMarketplacePluginComponentMap = new HashMap<>();
  private final List<PluginsGroup> myEnabledGroups = new ArrayList<>();

  private PluginsGroupComponent myInstalledPanel;
  private PluginsGroup myDownloaded;
  private PluginsGroup myInstalling;
  private Configurable.TopComponentController myTopController;
  private SortedSet<String> myVendors;
  private SortedSet<String> myTags;

  private static final Set<IdeaPluginDescriptor> myInstallingPlugins = new HashSet<>();
  private static final Set<IdeaPluginDescriptor> myInstallingWithUpdatesPlugins = new HashSet<>();
  static final Map<PluginId, InstallPluginInfo> myInstallingInfos = new HashMap<>();

  public boolean needRestart;
  public boolean createShutdownCallback = true;
  private boolean myInstallsRequiringRestart;
  private final List<PluginDetailsPageComponent> myDetailPanels = new ArrayList<>();

  private final @Nullable StatusBarEx myStatusBar;

  private PluginUpdatesService myPluginUpdatesService;

  private Runnable myInvalidFixCallback;
  private Consumer<? super IdeaPluginDescriptor> myCancelInstallCallback;

  private final Map<PluginId, PendingDynamicPluginInstall> myDynamicPluginsToInstall = new LinkedHashMap<>();
  private final Set<IdeaPluginDescriptorImpl> myDynamicPluginsToUninstall = new HashSet<>();
  private final Set<IdeaPluginDescriptorImpl> myPluginsToRemoveOnCancel = new HashSet<>();

  private final Map<PluginId, Set<PluginId>> myDependentToRequiredListMap = new HashMap<>();
  private final Map<IdeaPluginDescriptor, Pair<PluginEnableDisableAction, PluginEnabledState>> myDiff = new HashMap<>();
  private final Map<PluginId, Boolean> myRequiredPluginsForProject = new HashMap<>();
  private final Map<IdeaPluginDescriptorImpl, Boolean> myRequiresRestart = new HashMap<>();
  private final Set<IdeaPluginDescriptor> myUninstalled = new HashSet<>();

  private final Set<PluginId> myErrorPluginsToDisable = new HashSet<>();

  public MyPluginModel(@Nullable Project project) {
    super(project);

    Window window = ProjectUtil.getActiveFrameOrWelcomeScreen();
    StatusBarEx statusBar = getStatusBar(window);
    myStatusBar = statusBar != null || window == null ?
                  statusBar :
                  getStatusBar(window.getOwner());

    updatePluginDependencies(null);
  }

  private static @Nullable StatusBarEx getStatusBar(@Nullable Window frame) {
    return frame instanceof IdeFrame && !(frame instanceof WelcomeFrame) ?
           (StatusBarEx)((IdeFrame)frame).getStatusBar() :
           null;
  }

  public boolean isModified() {
    return needRestart ||
           !myDynamicPluginsToInstall.isEmpty() ||
           !myDynamicPluginsToUninstall.isEmpty() ||
           !myPluginsToRemoveOnCancel.isEmpty() ||
           !myDiff.isEmpty();
  }

  /**
   * @return true if changes were applied without restart
   */
  public boolean apply(@Nullable JComponent parent) throws ConfigurationException {
    Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap = PluginManagerCore.buildPluginIdMap();
    updatePluginDependencies(pluginIdMap);
    assertCanApply(pluginIdMap);

    PluginEnabler pluginEnabler = PluginEnabler.getInstance();
    DynamicPluginEnablerState pluginEnablerState = pluginEnabler instanceof DynamicPluginEnabler ?
                                                   ((DynamicPluginEnabler)pluginEnabler).getState() :
                                                   null;
    Set<PluginId> uninstallsRequiringRestart = new HashSet<>();
    for (IdeaPluginDescriptorImpl pluginDescriptor : myDynamicPluginsToUninstall) {
      myDiff.remove(pluginDescriptor);
      PluginId pluginId = pluginDescriptor.getPluginId();

      if (!PluginInstaller.uninstallDynamicPlugin(parent, pluginDescriptor, false)) {
        uninstallsRequiringRestart.add(pluginId);
      }
      else {
        getEnabledMap().remove(pluginId);
      }

      if (pluginEnablerState != null) {
        pluginEnablerState.stopTracking(List.of(pluginId));
      }
    }

    boolean installsRequiringRestart = myInstallsRequiringRestart;
    List<PluginId> dynamicPluginsRequiringRestart = new ArrayList<>();

    for (PendingDynamicPluginInstall pendingPluginInstall : myDynamicPluginsToInstall.values()) {
      PluginId pluginId = pendingPluginInstall.getPluginDescriptor().getPluginId();
      if (!uninstallsRequiringRestart.contains(pluginId)) {
        InstalledPluginsState.getInstance().trackPluginInstallation(() -> {
          if (!PluginInstaller.installAndLoadDynamicPlugin(pendingPluginInstall.getFile(),
                                                           parent,
                                                           pendingPluginInstall.getPluginDescriptor())) {
            dynamicPluginsRequiringRestart.add(pluginId);
          }
        });
      }
      else {
        try {
          PluginInstaller.installAfterRestart(pendingPluginInstall.getFile(), !Registry.is("ide.plugins.keep.archive", true),
                                              null, pendingPluginInstall.getPluginDescriptor());
          installsRequiringRestart = true;
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }

    myDynamicPluginsToInstall.clear();
    myPluginsToRemoveOnCancel.clear();

    boolean enableDisableAppliedWithoutRestart = applyEnableDisablePlugins(pluginEnabler, parent);
    myDynamicPluginsToUninstall.clear();
    myDiff.clear();

    boolean changesAppliedWithoutRestart = enableDisableAppliedWithoutRestart &&
                                           uninstallsRequiringRestart.isEmpty() &&
                                           !installsRequiringRestart &&
                                           dynamicPluginsRequiringRestart.isEmpty();
    if (!changesAppliedWithoutRestart) {
      InstalledPluginsState.getInstance().setRestartRequired(true);
    }
    return changesAppliedWithoutRestart;
  }

  public void clear(@Nullable JComponent parentComponent) {
    cancel(parentComponent);

    updateAfterEnableDisable();
  }

  public void cancel(@Nullable JComponent parentComponent) {
    myDiff.forEach((key, value) -> setEnabled(key.getPluginId(), value.getSecond()));
    myDiff.clear();

    myPluginsToRemoveOnCancel.forEach(pluginDescriptor -> PluginInstaller.uninstallDynamicPlugin(parentComponent, pluginDescriptor, false));
    myPluginsToRemoveOnCancel.clear();
  }

  private boolean applyEnableDisablePlugins(@NotNull PluginEnabler pluginEnabler,
                                            @Nullable JComponent parentComponent) {
    EnumMap<PluginEnableDisableAction, List<IdeaPluginDescriptor>> descriptorsByAction = new EnumMap<>(PluginEnableDisableAction.class);

    for (Map.Entry<IdeaPluginDescriptor, Pair<PluginEnableDisableAction, PluginEnabledState>> entry : myDiff.entrySet()) {
      IdeaPluginDescriptor descriptor = entry.getKey();
      PluginId pluginId = descriptor.getPluginId();

      Pair<PluginEnableDisableAction, PluginEnabledState> pair = entry.getValue();
      PluginEnabledState oldState = pair.getSecond();
      PluginEnabledState newState = getState(pluginId);

      if (isDeleted(descriptor) ||
          (isHiddenImplementationDetail(descriptor) && newState.isDisabled()) ||
          !isLoaded(pluginId) /* if enableMap contains null for id => enable/disable checkbox don't touch */) {
        continue;
      }

      if (oldState != newState ||
          newState.isDisabled() && myErrorPluginsToDisable.contains(pluginId)) {
        descriptorsByAction
          .computeIfAbsent(pair.getFirst(), __ -> new ArrayList<>())
          .add(descriptor);
      }
    }

    boolean appliedWithoutRestart = true;
    for (Map.Entry<PluginEnableDisableAction, List<IdeaPluginDescriptor>> entry : descriptorsByAction.entrySet()) {
      PluginEnableDisableAction action = entry.getKey();
      List<IdeaPluginDescriptor> descriptors = entry.getValue();

      appliedWithoutRestart &= pluginEnabler instanceof DynamicPluginEnabler ?
                               ((DynamicPluginEnabler)pluginEnabler).updatePluginsState(descriptors,
                                                                                        action,
                                                                                        getProject(),
                                                                                        parentComponent) :
                               action.isEnable() ?
                               pluginEnabler.enable(descriptors) :
                               pluginEnabler.disable(descriptors);
    }
    return appliedWithoutRestart;
  }

  public void pluginInstalledFromDisk(@NotNull PluginInstallCallbackData callbackData) {
    appendOrUpdateDescriptor(callbackData.getPluginDescriptor(), callbackData.getRestartNeeded());
    if (!callbackData.getRestartNeeded()) {
      myDynamicPluginsToInstall.put(callbackData.getPluginDescriptor().getPluginId(),
                                    new PendingDynamicPluginInstall(callbackData.getFile(), callbackData.getPluginDescriptor()));
    }
  }

  public void addComponent(@NotNull ListPluginComponent component) {
    IdeaPluginDescriptor descriptor = component.getPluginDescriptor();
    PluginId pluginId = descriptor.getPluginId();
    if (!component.isMarketplace()) {
      if (myInstallingPlugins.contains(descriptor) &&
          (myInstalling == null || myInstalling.ui == null || myInstalling.ui.findComponent(pluginId) == null)) {
        return;
      }

      myInstalledPluginComponents.add(component);

      List<ListPluginComponent> components =
        myInstalledPluginComponentMap.computeIfAbsent(pluginId, __ -> new ArrayList<>());
      components.add(component);
    }
    else {
      List<ListPluginComponent> components =
        myMarketplacePluginComponentMap.computeIfAbsent(pluginId, __ -> new ArrayList<>());
      components.add(component);
    }
  }

  public void removeComponent(@NotNull ListPluginComponent component) {
    PluginId pluginId = component.getPluginDescriptor().getPluginId();
    if (!component.isMarketplace()) {
      myInstalledPluginComponents.remove(component);

      List<ListPluginComponent> components = myInstalledPluginComponentMap.get(pluginId);
      if (components != null) {
        components.remove(component);
        if (components.isEmpty()) {
          myInstalledPluginComponentMap.remove(pluginId);
        }
      }
    }
    else {
      List<ListPluginComponent> components = myMarketplacePluginComponentMap.get(pluginId);
      if (components != null) {
        components.remove(component);
        if (components.isEmpty()) {
          myMarketplacePluginComponentMap.remove(pluginId);
        }
      }
    }
  }

  public void setTopController(@NotNull Configurable.TopComponentController topController) {
    myTopController = topController;
    myTopController.showProject(false);

    for (InstallPluginInfo info : myInstallingInfos.values()) {
      info.fromBackground(this);
    }
    if (!myInstallingInfos.isEmpty()) {
      myTopController.showProgress(true);
    }
  }

  public void setPluginUpdatesService(@NotNull PluginUpdatesService service) {
    myPluginUpdatesService = service;
  }

  @Nullable
  public PluginsGroup getDownloadedGroup() {
    return myDownloaded;
  }

  @NotNull
  public static Set<IdeaPluginDescriptor> getInstallingPlugins() {
    return myInstallingPlugins;
  }

  static boolean isInstallingOrUpdate(@NotNull IdeaPluginDescriptor descriptor) {
    return myInstallingWithUpdatesPlugins.contains(descriptor);
  }

  void installOrUpdatePlugin(@Nullable JComponent parentComponent,
                             @NotNull IdeaPluginDescriptor descriptor,
                             @Nullable IdeaPluginDescriptor updateDescriptor,
                             @NotNull ModalityState modalityState) {
    boolean isUpdate = updateDescriptor != null;
    IdeaPluginDescriptor actionDescriptor = isUpdate ? updateDescriptor : descriptor;
    if (!PluginManagerMain.checkThirdPartyPluginsAllowed(List.of(actionDescriptor))) {
      return;
    }

    Ref<Boolean> allowInstallWithoutRestart = Ref.create(true);
    if (isUpdate) {
      IdeaPluginDescriptorImpl installedPluginDescriptor = (IdeaPluginDescriptorImpl)descriptor;
      if (!DynamicPlugins.allowLoadUnloadWithoutRestart(installedPluginDescriptor)) {
        allowInstallWithoutRestart.set(false);
      }
      else if (!installedPluginDescriptor.isEnabled()) {
        try {
          FileUtil.delete(installedPluginDescriptor.getPluginPath());
        }
        catch (IOException e) {
          LOG.debug(e);
        }
      }
      else if (DynamicPlugins.allowLoadUnloadSynchronously(installedPluginDescriptor)) {
        allowInstallWithoutRestart.set(PluginInstaller.uninstallDynamicPlugin(parentComponent,
                                                                              installedPluginDescriptor,
                                                                              true));
      }
      else {
        performUninstall(installedPluginDescriptor);
      }
    }

    ProgressManager.getInstance()
      .runProcessWithProgressAsynchronously(new Task.Backgroundable(getProject(),
                                                                    parentComponent,
                                                                    IdeBundle.message("progress.title.loading.plugin.details"),
                                                                    true,
                                                                    null) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          PluginNode pluginNode = toPluginNode(actionDescriptor, indicator);
          if (pluginNode == null) {
            return;
          }

          List<PluginNode> pluginsToInstall = List.of(pluginNode);
          ApplicationManager.getApplication().invokeAndWait(() -> {
            PluginManagerMain.suggestToEnableInstalledDependantPlugins(MyPluginModel.this,
                                                                       pluginsToInstall);
          }, modalityState);


          InstallPluginInfo info = new InstallPluginInfo((BgProgressIndicator)indicator,
                                                         descriptor,
                                                         MyPluginModel.this,
                                                         !isUpdate);
          ApplicationManager.getApplication().invokeLater(() -> prepareToInstall(info), modalityState);

          PluginInstallOperation operation = new PluginInstallOperation(pluginsToInstall,
                                                                        getCustomRepoPlugins(),
                                                                        MyPluginModel.this,
                                                                        indicator);
          operation.setAllowInstallWithoutRestart(allowInstallWithoutRestart.get());

          runInstallOperation(operation, info, modalityState);
        }

        private @Nullable PluginNode toPluginNode(@NotNull IdeaPluginDescriptor descriptor,
                                                  @NotNull ProgressIndicator indicator) {
          if (descriptor instanceof PluginNode) {
            PluginNode pluginNode = (PluginNode)descriptor;
            return pluginNode.detailsLoaded() ?
                   pluginNode :
                   MarketplaceRequests.getInstance().loadPluginDetails(pluginNode, indicator);
          }
          else {
            PluginNode pluginNode = new PluginNode(descriptor.getPluginId(),
                                                   descriptor.getName(),
                                                   "-1");
            pluginNode.setDependencies(descriptor.getDependencies());
            pluginNode.setRepositoryName(PluginInstaller.UNKNOWN_HOST_MARKER);
            return pluginNode;
          }
        }
      }, new BgProgressIndicator());
  }

  private void runInstallOperation(@NotNull PluginInstallOperation operation,
                                   @NotNull InstallPluginInfo info,
                                   @NotNull ModalityState modalityState) {
    boolean cancel = false;
    boolean success = true;
    boolean showErrors = true;
    boolean restartRequired = true;
    List<PendingDynamicPluginInstall> pluginsToInstallSynchronously = new ArrayList<>();
    try {
      operation.run();
      for (PendingDynamicPluginInstall install : operation.getPendingDynamicPluginInstalls()) {
        if (DynamicPlugins.allowLoadUnloadSynchronously(install.getPluginDescriptor())) {
          pluginsToInstallSynchronously.add(install);
          myPluginsToRemoveOnCancel.add(install.getPluginDescriptor());
        }
        else {
          myDynamicPluginsToInstall.put(install.getPluginDescriptor().getPluginId(), install);
        }
      }

      success = operation.isSuccess();
      showErrors = !operation.isShownErrors();
      restartRequired = operation.isRestartRequired();
    }
    catch (ProcessCanceledException e) {
      cancel = true;
    }
    catch (Throwable e) {
      LOG.error(e);
      success = false;
    }

    boolean _success = success;
    boolean _cancel = cancel;
    boolean _showErrors = showErrors;
    boolean _restartRequired = restartRequired;
    ApplicationManager.getApplication().invokeLater(() -> {
      boolean dynamicRestartRequired = false;
      for (PendingDynamicPluginInstall install : pluginsToInstallSynchronously) {
        boolean installedWithoutRestart = PluginInstaller.installAndLoadDynamicPlugin(install.getFile(),
                                                                                      myInstalledPanel,
                                                                                      install.getPluginDescriptor());
        if (installedWithoutRestart) {
          IdeaPluginDescriptor installedDescriptor = PluginManagerCore.getPlugin(info.getDescriptor().getPluginId());
          if (installedDescriptor != null) {
            info.setInstalledDescriptor((IdeaPluginDescriptorImpl)installedDescriptor);
          }
        }
        else {
          dynamicRestartRequired = true;
        }
      }
      info.finish(_success, _cancel, _showErrors, _restartRequired || dynamicRestartRequired);
    }, modalityState);
  }

  public boolean toBackground() {
    for (InstallPluginInfo info : myInstallingInfos.values()) {
      info.toBackground(myStatusBar);
    }

    boolean result = !myInstallingInfos.isEmpty();
    if (result) {
      InstallPluginInfo.showRestart();
    }
    return result;
  }

  private void prepareToInstall(@NotNull InstallPluginInfo info) {
    IdeaPluginDescriptor descriptor = info.getDescriptor();
    PluginId pluginId = descriptor.getPluginId();
    myInstallingInfos.put(pluginId, info);

    if (myInstallingWithUpdatesPlugins.isEmpty()) {
      myTopController.showProgress(true);
    }
    myInstallingWithUpdatesPlugins.add(descriptor);
    if (info.install) {
      myInstallingPlugins.add(descriptor);
    }

    if (info.install && myInstalling != null) {
      if (myInstalling.ui == null) {
        myInstalling.descriptors.add(descriptor);
        myInstalledPanel.addGroup(myInstalling, 0);
      }
      else {
        myInstalledPanel.addToGroup(myInstalling, descriptor);
      }

      myInstalling.titleWithCount();
      myInstalledPanel.doLayout();
    }

    List<ListPluginComponent> gridComponents = myMarketplacePluginComponentMap.get(pluginId);
    if (gridComponents != null) {
      for (ListPluginComponent gridComponent : gridComponents) {
        gridComponent.showProgress();
      }
    }
    List<ListPluginComponent> listComponents = myInstalledPluginComponentMap.get(pluginId);
    if (listComponents != null) {
      for (ListPluginComponent listComponent : listComponents) {
        listComponent.showProgress();
      }
    }
    for (PluginDetailsPageComponent panel : myDetailPanels) {
      if (panel.getPlugin() == descriptor) {
        panel.showProgress();
      }
    }
  }

  /**
   * @param descriptor          Descriptor on which the installation was requested (can be a PluginNode or an IdeaPluginDescriptorImpl)
   * @param installedDescriptor If the plugin was loaded synchronously, the descriptor which has actually been installed; otherwise null.
   */
  void finishInstall(@NotNull IdeaPluginDescriptor descriptor,
                     @Nullable IdeaPluginDescriptorImpl installedDescriptor,
                     boolean success,
                     boolean showErrors,
                     boolean restartRequired) {
    InstallPluginInfo info = finishInstall(descriptor);

    if (myInstallingWithUpdatesPlugins.isEmpty()) {
      myTopController.showProgress(false);
    }

    PluginId pluginId = descriptor.getPluginId();
    List<ListPluginComponent> marketplaceComponents = myMarketplacePluginComponentMap.get(pluginId);
    if (marketplaceComponents != null) {
      for (ListPluginComponent gridComponent : marketplaceComponents) {
        if (installedDescriptor != null) {
          gridComponent.setPluginDescriptor(installedDescriptor);
        }
        gridComponent.hideProgress(success, restartRequired);
      }
    }
    List<ListPluginComponent> installedComponents = myInstalledPluginComponentMap.get(pluginId);
    if (installedComponents != null) {
      for (ListPluginComponent listComponent : installedComponents) {
        if (installedDescriptor != null) {
          listComponent.setPluginDescriptor(installedDescriptor);
        }
        listComponent.hideProgress(success, restartRequired);
        listComponent.updateErrors();
      }
    }
    for (PluginDetailsPageComponent panel : myDetailPanels) {
      if (panel.isShowingPlugin(descriptor)) {
        panel.setPlugin(installedDescriptor);
        panel.hideProgress(success);
      }
    }

    if (info.install) {
      if (myInstalling != null && myInstalling.ui != null) {
        clearInstallingProgress(descriptor);
        if (myInstallingPlugins.isEmpty()) {
          myInstalledPanel.removeGroup(myInstalling);
        }
        else {
          myInstalledPanel.removeFromGroup(myInstalling, descriptor);
          myInstalling.titleWithCount();
        }
        myInstalledPanel.doLayout();
      }
      if (success) {
        appendOrUpdateDescriptor(installedDescriptor != null ? installedDescriptor : descriptor, restartRequired);
        appendDependsAfterInstall();
      }
      else if (myCancelInstallCallback != null) {
        myCancelInstallCallback.accept(descriptor);
      }
    }
    else if (success) {
      if (myDownloaded != null && myDownloaded.ui != null && restartRequired) {
        ListPluginComponent component = myDownloaded.ui.findComponent(pluginId);
        if (component != null) {
          component.enableRestart();
        }
      }
    }
    else {
      myPluginUpdatesService.finishUpdate();
    }

    info.indicator.cancel();

    if (success) {
      needRestart = true;
      myInstallsRequiringRestart |= restartRequired;
    }
    if (!success && showErrors) {
      Messages.showErrorDialog(getProject(), IdeBundle.message("plugins.configurable.plugin.installing.failed", descriptor.getName()),
                               IdeBundle.message("action.download.and.install.plugin"));
    }
  }

  private void clearInstallingProgress(@NotNull IdeaPluginDescriptor descriptor) {
    if (myInstallingPlugins.isEmpty()) {
      for (ListPluginComponent listComponent : myInstalling.ui.plugins) {
        listComponent.clearProgress();
      }
    }
    else {
      for (ListPluginComponent listComponent : myInstalling.ui.plugins) {
        if (listComponent.getPluginDescriptor() == descriptor) {
          listComponent.clearProgress();
          return;
        }
      }
    }
  }

  @NotNull
  static InstallPluginInfo finishInstall(@NotNull IdeaPluginDescriptor descriptor) {
    InstallPluginInfo info = myInstallingInfos.remove(descriptor.getPluginId());
    info.close();
    myInstallingWithUpdatesPlugins.remove(descriptor);
    if (info.install) {
      myInstallingPlugins.remove(descriptor);
    }
    return info;
  }

  static void addProgress(@NotNull IdeaPluginDescriptor descriptor, @NotNull ProgressIndicatorEx indicator) {
    myInstallingInfos.get(descriptor.getPluginId()).indicator.addStateDelegate(indicator);
  }

  static void removeProgress(@NotNull IdeaPluginDescriptor descriptor, @NotNull ProgressIndicatorEx indicator) {
    myInstallingInfos.get(descriptor.getPluginId()).indicator.removeStateDelegate(indicator);
  }

  public void addEnabledGroup(@NotNull PluginsGroup group) {
    myEnabledGroups.add(group);
  }

  public void setDownloadedGroup(@NotNull PluginsGroupComponent panel,
                                 @NotNull PluginsGroup downloaded,
                                 @NotNull PluginsGroup installing) {
    myInstalledPanel = panel;
    myDownloaded = downloaded;
    myInstalling = installing;
  }

  private void appendDependsAfterInstall() {
    if (myDownloaded == null || myDownloaded.ui == null) {
      return;
    }

    for (IdeaPluginDescriptor descriptor : InstalledPluginsState.getInstance().getInstalledPlugins()) {
      PluginId pluginId = descriptor.getPluginId();
      if (myDownloaded.ui.findComponent(pluginId) != null) {
        continue;
      }

      appendOrUpdateDescriptor(descriptor, true);

      String id = pluginId.getIdString();

      for (Map.Entry<PluginId, List<ListPluginComponent>> entry : myMarketplacePluginComponentMap.entrySet()) {
        if (id.equals(entry.getKey().getIdString())) {
          for (ListPluginComponent component : entry.getValue()) {
            component.hideProgress(true, true);
          }
          break;
        }
      }
    }
  }

  public void addDetailPanel(@NotNull PluginDetailsPageComponent detailPanel) {
    myDetailPanels.add(detailPanel);
  }

  private void appendOrUpdateDescriptor(@NotNull IdeaPluginDescriptor descriptor) {
    int index = view.indexOf(descriptor);
    if (index < 0) {
      view.add(descriptor);
    }
    else {
      view.set(index, descriptor);
    }
  }

  void appendOrUpdateDescriptor(@NotNull IdeaPluginDescriptor descriptor,
                                boolean restartNeeded) {
    PluginId id = descriptor.getPluginId();
    if (!PluginManagerCore.isPluginInstalled(id)) {
      appendOrUpdateDescriptor(descriptor);
      setEnabled(id, PluginEnabledState.ENABLED);
    }

    if (restartNeeded) {
      needRestart = myInstallsRequiringRestart = true;
    }
    if (myDownloaded == null) {
      return;
    }

    myVendors = null;
    myTags = null;

    if (myDownloaded.ui == null) {
      myDownloaded.descriptors.add(descriptor);
      myDownloaded.titleWithEnabled(this);

      myInstalledPanel.addGroup(myDownloaded, myInstalling == null || myInstalling.ui == null ? 0 : 1);
      myInstalledPanel.setSelection(myDownloaded.ui.plugins.get(0));
      myInstalledPanel.doLayout();

      addEnabledGroup(myDownloaded);
    }
    else {
      ListPluginComponent component = myDownloaded.ui.findComponent(id);
      if (component != null) {
        myInstalledPanel.setSelection(component);
        component.enableRestart();
        return;
      }

      myInstalledPanel.addToGroup(myDownloaded, descriptor);
      myDownloaded.titleWithEnabled(this);
      myInstalledPanel.doLayout();
    }
  }

  public @NotNull SortedSet<String> getVendors() {
    if (ContainerUtil.isEmpty(myVendors)) {
      Map<String, Integer> vendorsCount = getVendorsCount(getInstalledDescriptors());
      myVendors = new TreeSet<>((v1, v2) -> {
        int result = vendorsCount.get(v2) - vendorsCount.get(v1);
        return result == 0 ? v2.compareToIgnoreCase(v1) : result;
      });
      myVendors.addAll(vendorsCount.keySet());
    }
    return Collections.unmodifiableSortedSet(myVendors);
  }

  public @NotNull SortedSet<String> getTags() {
    if (ContainerUtil.isEmpty(myTags)) {
      myTags = new TreeSet<>(String::compareToIgnoreCase);

      for (IdeaPluginDescriptor descriptor : getInstalledDescriptors()) {
        myTags.addAll(PluginManagerConfigurable.getTags(descriptor));
      }
    }
    return Collections.unmodifiableSortedSet(myTags);
  }

  public @NotNull List<IdeaPluginDescriptor> getInstalledDescriptors() {
    assert myInstalledPanel != null;

    return myInstalledPanel
      .getGroups()
      .stream()
      .filter(group -> !group.excluded)
      .flatMap(group -> group.plugins.stream())
      .map(ListPluginComponent::getPluginDescriptor)
      .collect(Collectors.toList());
  }

  private static @NotNull Map<String, Integer> getVendorsCount(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors) {
    Map<String, Integer> vendors = new HashMap<>();

    for (IdeaPluginDescriptor descriptor : descriptors) {
      String vendor = StringUtil.trim(descriptor.getVendor());
      if (!StringUtil.isEmptyOrSpaces(vendor)) {
        vendors.compute(vendor, (__, old) -> (old != null ? old : 0) + 1);
      }
    }

    return vendors;
  }

  public static boolean isVendor(@NotNull IdeaPluginDescriptor descriptor, @NotNull Set<String> vendors) {
    String vendor = StringUtil.trim(descriptor.getVendor());
    if (StringUtil.isEmpty(vendor)) {
      return false;
    }

    for (String vendorToFind : vendors) {
      if (vendor.equalsIgnoreCase(vendorToFind) || StringUtil.containsIgnoreCase(vendor, vendorToFind)) {
        return true;
      }
    }

    return false;
  }

  public boolean isEnabled(@NotNull IdeaPluginDescriptor descriptor) {
    return !isDisabled(descriptor.getPluginId());
  }

  public @NotNull PluginEnabledState getState(@NotNull IdeaPluginDescriptor descriptor) {
    return getState(descriptor.getPluginId());
  }

  @NotNull ProjectDependentPluginEnabledState getProjectDependentState(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId pluginId = descriptor.getPluginId();
    return new ProjectDependentPluginEnabledState(pluginId,
                                                  getState(pluginId),
                                                  getProject());
  }

  /**
   * @see #isEnabled(PluginId, Map)
   */
  @NotNull PluginEnabledState getState(@NotNull PluginId pluginId) {
    PluginEnabledState state = getEnabledMap().get(pluginId);
    return state != null ? state : PluginEnabledState.ENABLED;
  }

  boolean isRequiredPluginForProject(@NotNull PluginId pluginId) {
    Project project = getProject();
    return project != null &&
           myRequiredPluginsForProject
             .computeIfAbsent(pluginId,
                              id -> ContainerUtil.exists(getDependenciesOnPlugins(project),
                                                         id.getIdString()::equals));
  }

  boolean requiresRestart(@NotNull IdeaPluginDescriptor descriptor) {
    return myRequiresRestart
      .computeIfAbsent(descriptor instanceof IdeaPluginDescriptorImpl ? (IdeaPluginDescriptorImpl)descriptor : null,
                       it -> it == null || DynamicPlugins.INSTANCE.checkCanUnloadWithoutRestart(it) != null);
  }

  boolean isUninstalled(@NotNull IdeaPluginDescriptor descriptor) {
    return myUninstalled.contains(descriptor);
  }

  void addUninstalled(@NotNull IdeaPluginDescriptor descriptor) {
    myUninstalled.add(descriptor);
  }

  public boolean setEnabledState(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                                 @NotNull PluginEnableDisableAction action) {
    enableRows(descriptors, action);
    updateAfterEnableDisable();
    runInvalidFixCallback();
    return true;
  }

  @Override
  public boolean isDisabled(@NotNull PluginId pluginId) {
    return !isEnabled(pluginId, getEnabledMap());
  }

  @Override
  public boolean enable(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors) {
    return setEnabledState(descriptors, PluginEnableDisableAction.ENABLE_GLOBALLY);
  }

  @Override
  public boolean disable(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors) {
    return setEnabledState(descriptors, PluginEnableDisableAction.DISABLE_GLOBALLY);
  }

  void enableRequiredPlugins(@NotNull IdeaPluginDescriptor descriptor) {
    Set<PluginId> requiredPluginIds = getRequiredPluginIds(descriptor.getPluginId());
    if (requiredPluginIds.isEmpty()) {
      return;
    }

    Set<IdeaPluginDescriptor> requiredPlugins = new HashSet<>();

    for (PluginId pluginId : requiredPluginIds) {
      IdeaPluginDescriptor result = ContainerUtil.find(view, d -> pluginId.equals(d.getPluginId()));
      if (result == null && PluginManagerCore.isModuleDependency(pluginId)) {
        result = ContainerUtil.find(view,
                                    d -> d instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)d).modules.contains(pluginId));
        if (result != null) {
          setEnabled(pluginId, PluginEnabledState.ENABLED); // todo
        }
      }
      if (result != null) {
        requiredPlugins.add(result);
      }
    }

    if (!requiredPlugins.isEmpty()) {
      enablePlugins(requiredPlugins);
    }
  }

  @Override
  protected void handleBeforeChangeEnableState(@NotNull IdeaPluginDescriptor descriptor,
                                               @NotNull Pair<PluginEnableDisableAction, PluginEnabledState> pair) {
    PluginId pluginId = descriptor.getPluginId();
    Pair<PluginEnableDisableAction, PluginEnabledState> oldPair = myDiff.get(descriptor);

    PluginEnabledState oldState = oldPair != null ? oldPair.getSecond() : null;
    PluginEnabledState newState = pair.getSecond();
    if (oldState != newState) {
      PluginEnabledState state = oldState != null ? oldState : getState(pluginId);
      myDiff.put(descriptor,
                 Pair.create(pair.getFirst(), state));
    }
    else {
      myDiff.remove(descriptor);
    }

    myErrorPluginsToDisable.remove(pluginId);

    if (newState.isEnabled() ||
        descriptor.isEnabled()) {
      return;
    }

    if (PluginManagerCore.isIncompatible(descriptor) ||
        PluginManagerCore.isBrokenPlugin(descriptor) ||
        hasProblematicDependencies(pluginId)) {
      myErrorPluginsToDisable.add(pluginId);
    }
  }

  private void runInvalidFixCallback() {
    if (myInvalidFixCallback != null) {
      ApplicationManager.getApplication().invokeLater(myInvalidFixCallback, ModalityState.any());
    }
  }

  public void setInvalidFixCallback(@Nullable Runnable invalidFixCallback) {
    myInvalidFixCallback = invalidFixCallback;
  }

  public void setCancelInstallCallback(@NotNull Consumer<? super IdeaPluginDescriptor> callback) {
    myCancelInstallCallback = callback;
  }

  private void updateAfterEnableDisable() {
    for (ListPluginComponent component : myInstalledPluginComponents) {
      component.updateEnabledState();
    }
    for (PluginDetailsPageComponent detailPanel : myDetailPanels) {
      detailPanel.updateEnabledState();
    }
    for (PluginsGroup group : myEnabledGroups) {
      group.titleWithEnabled(this);
    }
  }

  public void runRestartButton(@NotNull Component component) {
    if (PluginManagerConfigurable.showRestartDialog() == Messages.YES) {
      needRestart = true;
      createShutdownCallback = false;

      DialogWrapper settings = DialogWrapper.findInstance(component);
      if (settings instanceof SettingsDialog) {
        ((SettingsDialog)settings).applyAndClose(false /* will be saved on app exit */);
      }
      else if (isModified()) {
        try {
          apply(null);
        }
        catch (ConfigurationException e) {
          LOG.error(e);
        }
      }
      ApplicationManagerEx.getApplicationEx().restart(true);
    }
  }

  void uninstallAndUpdateUi(@NotNull IdeaPluginDescriptor descriptor) {
    boolean needRestartForUninstall = performUninstall((IdeaPluginDescriptorImpl)descriptor);
    needRestart |= descriptor.isEnabled() && needRestartForUninstall;
    myInstallsRequiringRestart |= needRestartForUninstall;

    List<ListPluginComponent> listComponents = myInstalledPluginComponentMap.get(descriptor.getPluginId());
    if (listComponents != null) {
      for (ListPluginComponent listComponent : listComponents) {
        listComponent.updateAfterUninstall(needRestartForUninstall);
      }
    }

    for (ListPluginComponent component : myInstalledPluginComponents) {
      component.updateErrors();
    }

    for (PluginDetailsPageComponent panel : myDetailPanels) {
      if (panel.getPlugin() == descriptor) {
        panel.updateButtons();
      }
    }
  }

  private boolean performUninstall(@NotNull IdeaPluginDescriptorImpl descriptorImpl) {
    boolean needRestartForUninstall = true;
    try {
      descriptorImpl.setDeleted(true);
      needRestartForUninstall = PluginInstaller.prepareToUninstall(descriptorImpl);
      InstalledPluginsState.getInstance().onPluginUninstall(descriptorImpl, !needRestartForUninstall);
      if (!needRestartForUninstall) {
        myDynamicPluginsToUninstall.add(descriptorImpl);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return needRestartForUninstall;
  }

  private boolean hasProblematicDependencies(@NotNull PluginId pluginId) {
    return getRequiredPluginsById(pluginId).anyMatch(pair -> {
      IdeaPluginDescriptor descriptor = pair.getSecond();
      return descriptor != null && !isEnabled(descriptor);
    });
  }

  public boolean hasErrors(@NotNull IdeaPluginDescriptor descriptor) {
    return !getErrors(descriptor).isEmpty();
  }

  public @NotNull List<? extends HtmlChunk> getErrors(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId pluginId = descriptor.getPluginId();
    if (isDeleted(descriptor) ||
        InstalledPluginsState.getInstance().wasUninstalledWithoutRestart(pluginId) ||
        InstalledPluginsState.getInstance().wasInstalledWithoutRestart(pluginId)) {
      // we'll actually install the plugin when the configurable is closed; at this time we don't know if there's any loadingError
      return List.of();
    }

    PluginLoadingError loadingError = PluginManagerCore.getLoadingError(pluginId);
    PluginId disabledDependency = loadingError != null ? loadingError.disabledDependency : null;
    if (disabledDependency == null) {
      return loadingError != null ?
             List.of(createTextChunk(loadingError.getShortMessage())) :
             List.of();
    }

    Map<PluginId, IdeaPluginDescriptor> requiredPlugins = new HashMap<>();
    getRequiredPluginsById(pluginId).filter(pair -> {
      IdeaPluginDescriptor requiredDescriptor = pair.getSecond();
      return requiredDescriptor == null || !requiredDescriptor.isEnabled();
    }).forEach(pair -> requiredPlugins.put(pair.getFirst(), pair.getSecond()));
    if (requiredPlugins.isEmpty()) {
      return List.of();
    }

    ArrayList<HtmlChunk> errors = new ArrayList<>();

    Set<String> pluginNamesOrIds = requiredPlugins.entrySet().stream()
      .map(entry -> getPluginNameOrId(entry.getKey(), entry.getValue()))
      .collect(Collectors.toUnmodifiableSet());
    String message = IdeBundle.message("new.plugin.manager.incompatible.deps.tooltip",
                                       pluginNamesOrIds.size(),
                                       joinPluginNamesOrIds(pluginNamesOrIds));
    errors.add(createTextChunk(message));

    //noinspection SSBasedInspection
    if (requiredPlugins.entrySet().stream().noneMatch(entry -> {
      IdeaPluginDescriptor requiredDescriptor = entry.getValue();
      return requiredDescriptor == null || PluginManagerCore.isIncompatible(requiredDescriptor);
    })) {
      String action = IdeBundle.message("new.plugin.manager.incompatible.deps.action", requiredPlugins.size());
      errors.add(HtmlChunk.link("link", action));
    }

    return Collections.unmodifiableList(errors);
  }

  @Override
  protected void updatePluginDependencies(@Nullable Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap) {
    myDependentToRequiredListMap.clear();

    InstalledPluginsState pluginsState = InstalledPluginsState.getInstance();
    for (IdeaPluginDescriptor rootDescriptor : view) {
      final PluginId pluginId = rootDescriptor.getPluginId();
      myDependentToRequiredListMap.remove(pluginId);
      if (isDeleted(rootDescriptor) ||
          isDisabled(pluginId)) {
        continue;
      }

      if (pluginIdMap == null) {
        pluginIdMap = PluginManagerCore.buildPluginIdMap();
      }

      boolean loaded = isLoaded(pluginId);
      if (rootDescriptor instanceof IdeaPluginDescriptorImpl) {
        PluginManagerCore.processAllNonOptionalDependencyIds((IdeaPluginDescriptorImpl)rootDescriptor, pluginIdMap, depId -> {
          if (depId.equals(pluginId)) {
            return FileVisitResult.CONTINUE;
          }

          if ((!isLoaded(depId) &&
               !pluginsState.wasInstalled(depId) &&
               !pluginsState.wasUpdated(depId) &&
               !pluginsState.wasInstalledWithoutRestart(depId)) ||
              isDisabled(depId)) {
            myDependentToRequiredListMap.putIfAbsent(pluginId, new HashSet<>());
            myDependentToRequiredListMap.get(pluginId).add(depId);
          }

          return FileVisitResult.CONTINUE;
        });
      }

      if (!loaded &&
          !myDependentToRequiredListMap.containsKey(pluginId) &&
          PluginManagerCore.isCompatible(rootDescriptor)) {
        setEnabled(pluginId, PluginEnabledState.ENABLED); // todo
      }
    }
  }

  private @NotNull Set<PluginId> getRequiredPluginIds(@NotNull PluginId pluginId) {
    return myDependentToRequiredListMap.getOrDefault(pluginId, Set.of());
  }

  private void assertCanApply(@NotNull Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap) throws ConfigurationException {
    List<IdeaPluginDescriptorImpl> descriptors = new ArrayList<>();
    for (Map.Entry<PluginId, Set<PluginId>> entry : myDependentToRequiredListMap.entrySet()) {
      PluginId pluginId = entry.getKey();

      if (!isLoaded(pluginId)) {
        continue;
      }

      for (PluginId dependencyPluginId : entry.getValue()) {
        if (PluginManagerCore.isModuleDependency(dependencyPluginId)) {
          continue;
        }

        IdeaPluginDescriptorImpl descriptor = pluginIdMap.get(dependencyPluginId);
        if (descriptor != null && !isHidden(descriptor)) {
          descriptors.add(descriptor);
        }
        break;
      }
    }

    if (!descriptors.isEmpty()) {
      Set<String> pluginNames = getPluginNames(descriptors);
      String message = IdeBundle.message("dialog.message.unable.to.apply.changes",
                                         pluginNames.size(),
                                         joinPluginNamesOrIds(pluginNames));
      throw new ConfigurationException(XmlStringUtil.wrapInHtml(message)).withHtmlMessage();
    }
  }

  private @NotNull Stream<Pair<@NotNull PluginId, @Nullable IdeaPluginDescriptorImpl>> getRequiredPluginsById(@NotNull PluginId pluginId) {
    Set<PluginId> pluginIds = getRequiredPluginIds(pluginId);
    if (pluginIds.isEmpty()) {
      return Stream.of();
    }

    Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap = PluginManagerCore.buildPluginIdMap();
    return pluginIds
      .stream()
      .map(requiredPluginId -> {
        IdeaPluginDescriptorImpl requiredDescriptor = pluginIdMap.get(requiredPluginId);
        return Pair.create(requiredPluginId, requiredDescriptor == null && PluginManagerCore.isModuleDependency(requiredPluginId) ?
                                             PluginManagerCore.findPluginByModuleDependency(requiredPluginId) :
                                             requiredDescriptor);
      });
  }

  protected @NotNull Collection<PluginNode> getCustomRepoPlugins() {
    return CustomPluginRepositoryService.getInstance().getCustomRepositoryPlugins();
  }

  public static @NotNull Set<String> getPluginNames(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors) {
    return Collections.unmodifiableSet(ContainerUtil.map2Set(descriptors,
                                                             IdeaPluginDescriptor::getName));
  }

  public static @NotNull String joinPluginNamesOrIds(@NotNull Set<String> pluginNames) {
    return StringUtil.join(pluginNames,
                           StringUtil::wrapWithDoubleQuote,
                           ", ");
  }

  // todo move from the class
  public static @NotNull List<IdeaPluginDescriptorImpl> getDependents(@NotNull IdeaPluginDescriptor rootDescriptor,
                                                                      @NotNull ApplicationInfoEx applicationInfo,
                                                                      @NotNull Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap) {
    PluginId rootId = rootDescriptor.getPluginId();

    List<IdeaPluginDescriptorImpl> result = new ArrayList<>();
    for (Map.Entry<PluginId, IdeaPluginDescriptorImpl> entry : pluginIdMap.entrySet()) {
      PluginId pluginId = entry.getKey();
      IdeaPluginDescriptorImpl descriptor = entry.getValue();

      if (pluginId.equals(rootId) ||
          applicationInfo.isEssentialPlugin(pluginId) ||
          !descriptor.isEnabled() ||
          isHidden(descriptor)) {
        continue;
      }

      PluginManagerCore.processAllNonOptionalDependencies(descriptor, pluginIdMap, dependency -> {
        if (dependency.getPluginId().equals(rootId)) {
          result.add(descriptor);
          return FileVisitResult.TERMINATE;
        }
        return FileVisitResult.CONTINUE;
      });
    }

    return Collections.unmodifiableList(result);
  }

  private final Map<String, Icon> myIcons = new HashMap<>(); // local cache for PluginLogo WeakValueMap

  @NotNull
  public Icon getIcon(@NotNull IdeaPluginDescriptor descriptor, boolean big, boolean error, boolean disabled) {
    String key = descriptor.getPluginId().getIdString() + big + error + disabled;
    Icon icon = myIcons.get(key);
    if (icon == null) {
      icon = PluginLogo.getIcon(descriptor, big, error, disabled);
      if (icon != PluginLogo.getDefault().getIcon(big, error, disabled)) {
        myIcons.put(key, icon);
      }
    }
    return icon;
  }

  private static @NotNull List<String> getDependenciesOnPlugins(@NotNull Project project) {
    return ContainerUtil.map(ExternalDependenciesManager.getInstance(project).getDependencies(DependencyOnPlugin.class),
                             DependencyOnPlugin::getPluginId);
  }

  private static @NotNull HtmlChunk.Element createTextChunk(@NotNull @Nls String message) {
    return HtmlChunk.span().addText(message);
  }
}