// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * @author Alexander Lobas
 */
public class MyPluginModel extends InstalledPluginsTableModel implements PluginManagerMain.PluginEnabler {
  private static final Logger LOG = Logger.getInstance(MyPluginModel.class);

  private final List<ListPluginComponent> myInstalledPluginComponents = new ArrayList<>();
  private final Map<PluginId, List<ListPluginComponent>> myInstalledPluginComponentMap = new HashMap<>();
  private final Map<PluginId, List<ListPluginComponent>> myMarketplacePluginComponentMap = new HashMap<>();
  private final List<PluginsGroup> myEnabledGroups = new ArrayList<>();
  private PluginsGroupComponent myInstalledPanel;
  private PluginsGroup myDownloaded;
  private PluginsGroup myInstalling;
  private PluginsGroup myUpdates;
  private Configurable.TopComponentController myTopController;
  private List<String> myVendorsSorted;
  private List<String> myTagsSorted;

  private static final Set<IdeaPluginDescriptor> myInstallingPlugins = new HashSet<>();
  private static final Set<IdeaPluginDescriptor> myInstallingWithUpdatesPlugins = new HashSet<>();
  private static final Map<PluginId, InstallPluginInfo> myInstallingInfos = new HashMap<>();

  public boolean needRestart;
  public boolean createShutdownCallback = true;
  private boolean myInstallsRequiringRestart;
  private final List<PluginDetailsPageComponent> myDetailPanels = new ArrayList<>();

  private StatusBarEx myStatusBar;

  private PluginUpdatesService myPluginUpdatesService;

  private Runnable myInvalidFixCallback;

  private final Map<PluginId, PendingDynamicPluginInstall> myDynamicPluginsToInstall = new LinkedHashMap<>();
  private final Set<IdeaPluginDescriptor> myDynamicPluginsToUninstall = new HashSet<>();
  private final Set<IdeaPluginDescriptor> myPluginsToRemoveOnCancel = new HashSet<>();

  protected MyPluginModel() {
    Window window = ProjectUtil.getActiveFrameOrWelcomeScreen();
    myStatusBar = getStatusBar(window);
    if (myStatusBar == null && window != null) {
      myStatusBar = getStatusBar(window.getOwner());
    }
  }

  @Nullable
  private static StatusBarEx getStatusBar(@Nullable Window frame) {
    if (frame instanceof IdeFrame && !(frame instanceof WelcomeFrame)) {
      return (StatusBarEx)((IdeFrame)frame).getStatusBar();
    }
    return null;
  }

  public boolean isModified() {
    if (needRestart || !myDynamicPluginsToInstall.isEmpty() || !myDynamicPluginsToUninstall.isEmpty()) {
      return true;
    }

    int rowCount = getRowCount();

    for (int i = 0; i < rowCount; i++) {
      IdeaPluginDescriptor descriptor = getObjectAt(i);
      boolean enabledInTable = isEnabled(descriptor);

      if (descriptor.isEnabled() != enabledInTable) {
        if (enabledInTable && !PluginManagerCore.isDisabled(descriptor.getPluginId())) {
          continue; // was disabled automatically on startup
        }
        return true;
      }
    }

    for (Map.Entry<PluginId, Boolean> entry : getEnabledMap().entrySet()) {
      Boolean enabled = entry.getValue();
      if (enabled != null && !enabled && !PluginManagerCore.isDisabled(entry.getKey())) {
        return true;
      }
    }

    return false;
  }

  /**
   * @return true if changes were applied without restart
   */
  public boolean apply(Component parent) throws ConfigurationException {
    Map<PluginId, Boolean> enabledMap = getEnabledMap();
    List<String> dependencies = new ArrayList<>();

    for (Map.Entry<PluginId, Set<PluginId>> entry : getDependentToRequiredListMap().entrySet()) {
      PluginId id = entry.getKey();

      if (enabledMap.get(id) == null) {
        continue;
      }

      for (PluginId dependId : entry.getValue()) {
        if (!PluginManagerCore.isModuleDependency(dependId)) {
          IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(id);
          if (!(descriptor instanceof IdeaPluginDescriptorImpl) ||
              !((IdeaPluginDescriptorImpl)descriptor).isDeleted() && !descriptor.isImplementationDetail()) {
            dependencies.add("\"" + (descriptor == null ? id.getIdString() : descriptor.getName()) + "\"");
          }
          break;
        }
      }
    }

    if (!dependencies.isEmpty()) {
      throw new ConfigurationException("<html><body style=\"padding: 5px;\">Unable to apply changes: plugin" +
                                       (dependencies.size() == 1 ? " " : "s ") +
                                       StringUtil.join(dependencies, ", ") +
                                       " won't be able to load.</body></html>");
    }

    Set<PluginId> uninstallsRequiringRestart = new HashSet<>();
    for (IdeaPluginDescriptor pluginDescriptor : myDynamicPluginsToUninstall) {
      if (!PluginInstaller.uninstallDynamicPlugin(pluginDescriptor)) {
        uninstallsRequiringRestart.add(pluginDescriptor.getPluginId());
      }
      else {
        enabledMap.remove(pluginDescriptor.getPluginId());
      }
    }

    boolean installsRequiringRestart = myInstallsRequiringRestart;

    for (PendingDynamicPluginInstall pendingPluginInstall : myDynamicPluginsToInstall.values()) {
      if (!uninstallsRequiringRestart.contains(pendingPluginInstall.getPluginDescriptor().getPluginId())) {
        PluginInstaller.installAndLoadDynamicPlugin(pendingPluginInstall.getFile(), parent,
                                                    pendingPluginInstall.getPluginDescriptor());
      }
      else {
        try {
          PluginInstaller.installAfterRestart(pendingPluginInstall.getFile(), true, null, pendingPluginInstall.getPluginDescriptor());
          installsRequiringRestart = true;
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }

    myDynamicPluginsToInstall.clear();
    myPluginsToRemoveOnCancel.clear();

    boolean enableDisableAppliedWithoutRestart = applyEnableDisablePlugins(enabledMap);
    myDynamicPluginsToUninstall.clear();
    return enableDisableAppliedWithoutRestart && uninstallsRequiringRestart.isEmpty() && !installsRequiringRestart;
  }

  public void removePluginsOnCancel() {
    myPluginsToRemoveOnCancel.forEach(PluginInstaller::uninstallDynamicPlugin);
    myPluginsToRemoveOnCancel.clear();
  }

  private boolean applyEnableDisablePlugins(Map<PluginId, Boolean> enabledMap) {
    List<IdeaPluginDescriptorImpl> pluginDescriptorsToDisable = new ArrayList<>();
    List<IdeaPluginDescriptorImpl> pluginDescriptorsToEnable = new ArrayList<>();

    int rowCount = getRowCount();
    for (int i = 0; i < rowCount; i++) {
      IdeaPluginDescriptor descriptor = getObjectAt(i);
      if (myDynamicPluginsToUninstall.contains(descriptor)) {
        continue;
      }

      boolean enabled = isEnabled(descriptor.getPluginId());
      if (enabled != descriptor.isEnabled()) {
        // PluginDescriptor fields are cleaned after the plugin is loaded, so we need to reload the descriptor to check if it's dynamic
        IdeaPluginDescriptorImpl fullDescriptor = PluginManager.loadDescriptor(((IdeaPluginDescriptorImpl)descriptor).getPluginPath(), PluginManagerCore.PLUGIN_XML, Collections.emptySet());
        if (fullDescriptor == null) {
          LOG.error("Could not load full descriptor for plugin " + descriptor.getPath());
          fullDescriptor = (IdeaPluginDescriptorImpl)descriptor;
        }
        if (!enabled) {
          pluginDescriptorsToDisable.add(fullDescriptor);
        }
        else {
          pluginDescriptorsToEnable.add(fullDescriptor);
        }
      }
      descriptor.setEnabled(enabled);
    }

    List<PluginId> disableIds = new ArrayList<>();
    for (Map.Entry<PluginId, Boolean> entry : enabledMap.entrySet()) {
      Boolean enabled = entry.getValue();
      if (enabled != null && !enabled) {
        disableIds.add(entry.getKey());
      }
    }

    try {
      PluginManagerCore.saveDisabledPlugins(disableIds, false);
    }
    catch (IOException e) {
      PluginManagerMain.LOG.error(e);
    }

    if (ContainerUtil.all(pluginDescriptorsToDisable, (plugin) -> DynamicPlugins.allowLoadUnloadWithoutRestart(plugin)) &&
        ContainerUtil.all(pluginDescriptorsToEnable, (plugin) -> DynamicPlugins.allowLoadUnloadWithoutRestart(plugin))) {
      boolean needRestart = false;
      for (IdeaPluginDescriptor descriptor : pluginDescriptorsToDisable) {
        if (!DynamicPlugins.unloadPlugin((IdeaPluginDescriptorImpl)descriptor, true)) {
          needRestart = true;
          break;
        }
      }

      if (!needRestart) {
        for (IdeaPluginDescriptor descriptor : pluginDescriptorsToEnable) {
          DynamicPlugins.loadPlugin((IdeaPluginDescriptorImpl)descriptor);
        }
        return true;
      }
    }
    return false;
  }

  public void pluginInstalledFromDisk(@NotNull PluginInstallCallbackData callbackData) {
    appendOrUpdateDescriptor(callbackData.getPluginDescriptor(), callbackData.getRestartNeeded());
    if (!callbackData.getRestartNeeded()) {
      myDynamicPluginsToInstall.put(callbackData.getPluginDescriptor().getPluginId(),
                                    new PendingDynamicPluginInstall(callbackData.getFile(), callbackData.getPluginDescriptor()));
    }
  }

  public void addComponent(@NotNull ListPluginComponent component) {
    if (!component.isMarketplace()) {
      if (myInstallingPlugins.contains(component.myPlugin)) {
        return;
      }

      myInstalledPluginComponents.add(component);

      List<ListPluginComponent> components =
        myInstalledPluginComponentMap.computeIfAbsent(component.myPlugin.getPluginId(), __ -> new ArrayList<>());
      components.add(component);
    }
    else {
      List<ListPluginComponent> components =
        myMarketplacePluginComponentMap.computeIfAbsent(component.myPlugin.getPluginId(), __ -> new ArrayList<>());
      components.add(component);
    }
  }

  public void removeComponent(@NotNull ListPluginComponent component) {
    if (!component.isMarketplace()) {
      myInstalledPluginComponents.remove(component);

      List<ListPluginComponent> components = myInstalledPluginComponentMap.get(component.myPlugin.getPluginId());
      if (components != null) {
        components.remove(component);
        if (components.isEmpty()) {
          myInstalledPluginComponentMap.remove(component.myPlugin.getPluginId());
        }
      }
    }
    else {
      List<ListPluginComponent> components = myMarketplacePluginComponentMap.get(component.myPlugin.getPluginId());
      if (components != null) {
        components.remove(component);
        if (components.isEmpty()) {
          myMarketplacePluginComponentMap.remove(component.myPlugin.getPluginId());
        }
      }
    }
  }

  public void setTopController(@NotNull Configurable.TopComponentController topController) {
    myTopController = topController;

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

  void installOrUpdatePlugin(@NotNull IdeaPluginDescriptor descriptor, @Nullable IdeaPluginDescriptor updateDescriptor) {
    if (!PluginManagerMain.checkThirdPartyPluginsAllowed(Collections.singletonList(descriptor))) {
      return;
    }

    boolean allowUninstallWithoutRestart = true;
    if (updateDescriptor != null) {
      IdeaPluginDescriptorImpl installedPluginDescriptor = PluginManager.loadDescriptor(((IdeaPluginDescriptorImpl)descriptor).getPluginPath(), PluginManagerCore.PLUGIN_XML, Collections.emptySet());
      if (installedPluginDescriptor == null || !DynamicPlugins.allowLoadUnloadWithoutRestart(installedPluginDescriptor)) {
        allowUninstallWithoutRestart = false;
      }
      else if (DynamicPlugins.allowLoadUnloadSynchronously(installedPluginDescriptor)) {
        if (!PluginInstaller.uninstallDynamicPlugin(installedPluginDescriptor)) {
          allowUninstallWithoutRestart = false;
        }
      }
      else {
        performUninstall(installedPluginDescriptor);
      }
    }

    IdeaPluginDescriptor actionDescriptor = updateDescriptor == null ? descriptor : updateDescriptor;
    PluginNode pluginNode;
    if (actionDescriptor instanceof PluginNode) {
      pluginNode = (PluginNode)actionDescriptor;
    }
    else {
      pluginNode = new PluginNode(actionDescriptor.getPluginId(), actionDescriptor.getName(), "-1");
      pluginNode.setDepends(Arrays.asList(actionDescriptor.getDependentPluginIds()), actionDescriptor.getOptionalDependentPluginIds());
      pluginNode.setRepositoryName(PluginInstaller.UNKNOWN_HOST_MARKER);
    }
    List<PluginNode> pluginsToInstall = ContainerUtil.newArrayList(pluginNode);

    PluginManagerMain.suggestToEnableInstalledDependantPlugins(this, pluginsToInstall);

    installPlugin(pluginsToInstall, getAllRepoPlugins(), prepareToInstall(descriptor, updateDescriptor), allowUninstallWithoutRestart);
  }

  private void installPlugin(@NotNull List<PluginNode> pluginsToInstall,
                             @NotNull List<? extends IdeaPluginDescriptor> allPlugins,
                             @NotNull InstallPluginInfo info, boolean allowInstallWithoutRestart) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      boolean cancel = false;
      boolean error = false;
      boolean showErrors = true;
      boolean restartRequired = true;
      List<PendingDynamicPluginInstall> pluginsToInstallSynchronously = new ArrayList<>();
      try {
        PluginInstallOperation operation = new PluginInstallOperation(pluginsToInstall, allPlugins, this, info.indicator);
        operation.setAllowInstallWithoutRestart(allowInstallWithoutRestart);
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

        error = !operation.isSuccess();
        showErrors = !operation.isShownErrors();
        restartRequired = operation.isRestartRequired();
      }
      catch (ProcessCanceledException e) {
        cancel = true;
      }
      catch (Throwable e) {
        PluginManagerMain.LOG.error(e);
        error = true;
      }

      boolean success = !error;
      boolean _cancel = cancel;
      boolean _showErrors = showErrors;
      boolean finalRestartRequired = restartRequired;
      ApplicationManager.getApplication()
        .invokeLater(() -> {
          for (PendingDynamicPluginInstall install : pluginsToInstallSynchronously) {
            IdeaPluginDescriptorImpl installedDescriptor =
              PluginInstaller.installAndLoadDynamicPlugin(install.getFile(), myInstalledPanel, install.getPluginDescriptor());
            if (installedDescriptor != null && installedDescriptor.getPluginId().equals(info.getDescriptor().getPluginId())) {
              info.setInstalledDescriptor(installedDescriptor);
            }
          }
          info.finish(success, _cancel, _showErrors, finalRestartRequired);
        }, ModalityState.any());
    });
  }

  public void toBackground() {
    for (InstallPluginInfo info : myInstallingInfos.values()) {
      info.toBackground(myStatusBar);
    }
  }

  @NotNull
  private InstallPluginInfo prepareToInstall(@NotNull IdeaPluginDescriptor descriptor, @Nullable IdeaPluginDescriptor updateDescriptor) {
    boolean install = updateDescriptor == null;
    InstallPluginInfo info = new InstallPluginInfo(descriptor, updateDescriptor, this, install);
    myInstallingInfos.put(descriptor.getPluginId(), info);

    if (myInstallingWithUpdatesPlugins.isEmpty()) {
      myTopController.showProgress(true);
    }
    myInstallingWithUpdatesPlugins.add(descriptor);
    if (install) {
      myInstallingPlugins.add(descriptor);
    }

    if (install && myInstalling != null) {
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

    List<ListPluginComponent> gridComponents = myMarketplacePluginComponentMap.get(descriptor.getPluginId());
    if (gridComponents != null) {
      for (ListPluginComponent gridComponent : gridComponents) {
        gridComponent.showProgress();
      }
    }
    List<ListPluginComponent> listComponents = myInstalledPluginComponentMap.get(descriptor.getPluginId());
    if (listComponents != null) {
      for (ListPluginComponent listComponent : listComponents) {
        listComponent.showProgress();
      }
    }
    for (PluginDetailsPageComponent panel : myDetailPanels) {
      if (panel.myPlugin == descriptor) {
        panel.showProgress();
      }
    }

    return info;
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

    List<ListPluginComponent> marketplaceComponents = myMarketplacePluginComponentMap.get(descriptor.getPluginId());
    if (marketplaceComponents != null) {
      for (ListPluginComponent gridComponent : marketplaceComponents) {
        if (installedDescriptor != null) {
          gridComponent.myPlugin = installedDescriptor;
        }
        gridComponent.hideProgress(success, restartRequired);
      }
    }
    List<ListPluginComponent> installedComponents = myInstalledPluginComponentMap.get(descriptor.getPluginId());
    if (installedComponents != null) {
      for (ListPluginComponent listComponent : installedComponents) {
        if (installedDescriptor != null) {
          listComponent.myPlugin = installedDescriptor;
        }
        listComponent.hideProgress(success, restartRequired);
      }
    }
    for (PluginDetailsPageComponent panel : myDetailPanels) {
      if (panel.isShowingPlugin(descriptor)) {
        if (installedDescriptor != null) {
          panel.myPlugin = installedDescriptor;
        }
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
    }
    else if (success) {
      if (myDownloaded != null && myDownloaded.ui != null && restartRequired) {
        ListPluginComponent component = myDownloaded.ui.findComponent(descriptor);
        if (component != null) {
          component.enableRestart();
        }
      }
      if (myUpdates != null) {
        myUpdates.titleWithCount();
      }
      myPluginUpdatesService.finishUpdate(info.updateDescriptor);
    }

    info.indicator.cancel();

    if (success) {
      needRestart = true;
      myInstallsRequiringRestart |= restartRequired;
    }
    if (!success && showErrors) {
      Messages.showErrorDialog("Plugin \"" + descriptor.getName() + "\" download or installing failed",
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
        if (listComponent.myPlugin == descriptor) {
          listComponent.clearProgress();
          return;
        }
      }
    }
  }

  @NotNull
  static InstallPluginInfo finishInstall(@NotNull IdeaPluginDescriptor descriptor) {
    InstallPluginInfo info = myInstallingInfos.remove(descriptor.getPluginId());
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

  public void setUpdateGroup(@NotNull PluginsGroup group) {
    myUpdates = group;
  }

  private void appendDependsAfterInstall() {
    if (myDownloaded == null || myDownloaded.ui == null) {
      return;
    }

    for (IdeaPluginDescriptor descriptor : InstalledPluginsState.getInstance().getInstalledPlugins()) {
      if (myDownloaded.ui.findComponent(descriptor) != null) {
        continue;
      }

      appendOrUpdateDescriptor(descriptor, true);

      String id = descriptor.getPluginId().getIdString();

      for (Entry<PluginId, List<ListPluginComponent>> entry : myMarketplacePluginComponentMap.entrySet()) {
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

  @Override
  public void appendOrUpdateDescriptor(@NotNull IdeaPluginDescriptor descriptor, boolean restartNeeded) {
    super.appendOrUpdateDescriptor(descriptor, restartNeeded);
    if (restartNeeded) {
      needRestart = myInstallsRequiringRestart = true;
    }
    if (myDownloaded == null) {
      return;
    }

    myVendorsSorted = null;
    myTagsSorted = null;

    if (myDownloaded.ui == null) {
      myDownloaded.descriptors.add(descriptor);
      myDownloaded.titleWithEnabled(this);

      myInstalledPanel.addGroup(myDownloaded, myInstalling == null || myInstalling.ui == null ? 0 : 1);
      myInstalledPanel.setSelection(myDownloaded.ui.plugins.get(0));
      myInstalledPanel.doLayout();

      addEnabledGroup(myDownloaded);
    }
    else {
      ListPluginComponent component = myDownloaded.ui.findComponent(descriptor);
      if (component != null) {
        myInstalledPanel.setSelection(component);
        component.enableRestart();
        return;
      }

      myInstalledPanel.addToGroup(myDownloaded, descriptor);
      myDownloaded.titleWithEnabled(this);
      myInstalledPanel.setSelection(myDownloaded.ui.plugins.get(myDownloaded.descriptors.indexOf(descriptor)));
      myInstalledPanel.doLayout();
    }
  }

  @NotNull
  public List<String> getVendors() {
    if (ContainerUtil.isEmpty(myVendorsSorted)) {
      myVendorsSorted = getVendors(getInstalledDescriptors());
    }
    return myVendorsSorted;
  }

  @NotNull
  public List<String> getTags() {
    if (ContainerUtil.isEmpty(myTagsSorted)) {
      Set<String> allTags = new HashSet<>();

      for (IdeaPluginDescriptor descriptor : getInstalledDescriptors()) {
        allTags.addAll(PluginManagerConfigurable.getTags(descriptor));
      }

      myTagsSorted = ContainerUtil.sorted(allTags, String::compareToIgnoreCase);
    }
    return myTagsSorted;
  }

  @NotNull
  public List<IdeaPluginDescriptor> getInstalledDescriptors() {
    assert myInstalledPanel != null;

    return myInstalledPanel.getGroups().stream().flatMap(group -> group.plugins.stream()).map(plugin -> plugin.myPlugin)
      .collect(Collectors.toList());
  }

  @NotNull
  public static List<String> getVendors(@NotNull List<? extends IdeaPluginDescriptor> descriptors) {
    Map<String, Integer> vendors = new HashMap<>();

    for (IdeaPluginDescriptor descriptor : descriptors) {
      String vendor = StringUtil.trim(descriptor.getVendor());
      if (!StringUtil.isEmptyOrSpaces(vendor)) {
        Integer count = vendors.get(vendor);
        if (count == null) {
          vendors.put(vendor, 1);
        }
        else {
          vendors.put(vendor, count + 1);
        }
      }
    }

    vendors.put("JetBrains", Integer.MAX_VALUE);

    return ContainerUtil.sorted(vendors.keySet(), (v1, v2) -> {
      int result = vendors.get(v2) - vendors.get(v1);
      return result == 0 ? v2.compareToIgnoreCase(v1) : result;
    });
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

  public boolean isEnabled(@NotNull IdeaPluginDescriptor plugin) {
    Boolean enabled = getEnabledMap().get(plugin.getPluginId());
    return enabled == null || enabled;
  }

  @NotNull
  String getEnabledTitle(@NotNull IdeaPluginDescriptor plugin) {
    return isEnabled(plugin) ? "Disable" : "Enable";
  }

  void changeEnableDisable(@NotNull IdeaPluginDescriptor plugin) {
    changeEnableDisable(new IdeaPluginDescriptor[]{plugin}, !isEnabled(plugin));
  }

  public void changeEnableDisable(@NotNull IdeaPluginDescriptor[] plugins, boolean state) {
    enableRows(plugins, state);
    updateAfterEnableDisable();
    runInvalidFixCallback();
  }

  @Override
  public void enablePlugins(Set<? extends IdeaPluginDescriptor> disabled) {
    changeEnableDisable(disabled.toArray(new IdeaPluginDescriptor[0]), true);
  }

  @Override
  public void disablePlugins(Set<? extends IdeaPluginDescriptor> disabled) {
    changeEnableDisable(disabled.toArray(new IdeaPluginDescriptor[0]), false);
  }

  void enableRequiredPlugins(@NotNull IdeaPluginDescriptor descriptor) {
    Set<PluginId> requiredPluginIds = getRequiredPlugins(descriptor.getPluginId());
    if (ContainerUtil.isEmpty(requiredPluginIds)) {
      return;
    }

    List<IdeaPluginDescriptor> allPlugins = getAllPlugins();
    Set<IdeaPluginDescriptor> requiredPlugins = new HashSet<>();

    for (PluginId pluginId : requiredPluginIds) {
      IdeaPluginDescriptor result = ContainerUtil.find(allPlugins, d -> pluginId.equals(d.getPluginId()));
      if (result == null && PluginManagerCore.isModuleDependency(pluginId)) {
        result = ContainerUtil.find(allPlugins, d -> {
          if (d instanceof IdeaPluginDescriptorImpl) {
            return ((IdeaPluginDescriptorImpl)d).getModules().contains(pluginId);
          }
          return false;
        });
        if (result != null) {
          getEnabledMap().put(pluginId, Boolean.TRUE);
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

  private void runInvalidFixCallback() {
    if (myInvalidFixCallback != null) {
      ApplicationManager.getApplication().invokeLater(myInvalidFixCallback, ModalityState.any());
    }
  }

  public void setInvalidFixCallback(@Nullable Runnable invalidFixCallback) {
    myInvalidFixCallback = invalidFixCallback;
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

  static boolean showUninstallDialog(@NotNull Component uiParent, @NotNull List<? extends ListPluginComponent> selection) {
    int size = selection.size();
    return showUninstallDialog(uiParent, size == 1 ? selection.get(0).myPlugin.getName() : null, size);
  }

  static boolean showUninstallDialog(@NotNull Component uiParent, @Nullable String singleName, int count) {
    String message;
    if (singleName == null) {
      message = IdeBundle.message("prompt.uninstall.several.plugins", count);
    }
    else {
      message = IdeBundle.message("prompt.uninstall.plugin", singleName);
    }

    return Messages.showYesNoDialog(uiParent, message, IdeBundle.message("title.plugin.uninstall"), Messages.getQuestionIcon()) ==
           Messages.YES;
  }

  void uninstallAndUpdateUi(@NotNull Component uiParent, @NotNull IdeaPluginDescriptor descriptor) {
    IdeaPluginDescriptorImpl descriptorImpl = (IdeaPluginDescriptorImpl)descriptor;
    if (!dependent(descriptorImpl).isEmpty()) {
      String message = IdeBundle.message("several.plugins.depend.on.0.continue.to.remove", descriptor.getName());
      String title = IdeBundle.message("title.plugin.uninstall");
      if (Messages.showYesNoDialog(uiParent, message, title, Messages.getQuestionIcon()) != Messages.YES) {
        return;
      }
    }

    boolean needRestartForUninstall = performUninstall(descriptorImpl);
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
      if (panel.myPlugin == descriptor) {
        panel.updateButtons();
      }
    }
  }

  private boolean performUninstall(IdeaPluginDescriptorImpl descriptorImpl) {
    boolean needRestartForUninstall = true;
    try {
      descriptorImpl.setDeleted(true);
      // Load descriptor to make sure we get back all the data cleared after the descriptor has been loaded
      IdeaPluginDescriptorImpl fullDescriptor = PluginManager.loadDescriptor(descriptorImpl.getPluginPath(), PluginManagerCore.PLUGIN_XML, Collections.emptySet());
      LOG.assertTrue(fullDescriptor != null);
      needRestartForUninstall = PluginInstaller.prepareToUninstall(fullDescriptor);
      InstalledPluginsState.getInstance().onPluginUninstall(descriptorImpl, needRestartForUninstall);
      if (!needRestartForUninstall) {
        myDynamicPluginsToUninstall.add(fullDescriptor);
      }
    }
    catch (IOException e) {
      PluginManagerMain.LOG.error(e);
    }
    return needRestartForUninstall;
  }

  @Nullable
  public static IdeaPluginDescriptor findPlugin(@NotNull PluginId id) {
    IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(id);
    if (plugin == null && PluginManagerCore.isModuleDependency(id)) {
      for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
        if (descriptor instanceof IdeaPluginDescriptorImpl) {
          if (((IdeaPluginDescriptorImpl)descriptor).getModules().contains(id)) {
            return descriptor;
          }
        }
      }
    }
    return plugin;
  }

  @Override
  public boolean hasProblematicDependencies(PluginId pluginId) {
    Set<PluginId> ids = getDependentToRequiredListMap().get(pluginId);
    if (ContainerUtil.isEmpty(ids)) {
      return false;
    }

    for (PluginId id : ids) {
      IdeaPluginDescriptor plugin = findPlugin(id);
      if (plugin != null && !isEnabled(plugin)) {
        return true;
      }
    }

    return false;
  }

  public boolean hasErrors(@NotNull IdeaPluginDescriptor plugin) {
    PluginId pluginId = plugin.getPluginId();
    if (PluginManagerCore.isIncompatible(plugin) || hasProblematicDependencies(pluginId)) {
      return true;
    }
    if (!isLoaded(pluginId)) {
      InstalledPluginsState state = InstalledPluginsState.getInstance();
      return !state.wasInstalled(pluginId) && !state.wasInstalledWithoutRestart(pluginId);
    }
    return false;
  }

  @NotNull
  public String getErrorMessage(@NotNull PluginDescriptor pluginDescriptor, @NotNull Ref<? super String> enableAction) {
    String message;

    Set<PluginId> requiredPlugins = getRequiredPlugins(pluginDescriptor.getPluginId());
    if (ContainerUtil.isEmpty(requiredPlugins)) {
      message = "Incompatible with the current " + ApplicationNamesInfo.getInstance().getFullProductName() + " version.";
    }
    else if (requiredPlugins.contains(PluginId.getId("com.intellij.modules.ultimate"))) {
      message = "The plugin requires IntelliJ IDEA Ultimate.";
    }
    else {
      boolean[] enable = {true};
      String deps = StringUtil.join(requiredPlugins, id -> {
        IdeaPluginDescriptor plugin = findPlugin(id);
        if (enable[0] && (plugin == null || PluginManagerCore.isIncompatible(plugin))) {
          enable[0] = false;
        }
        return StringUtil.wrapWithDoubleQuote(plugin != null ? plugin.getName() : id.getIdString());
      }, ", ");

      int size = requiredPlugins.size();
      message = IdeBundle.message("new.plugin.manager.incompatible.deps.tooltip", size, deps);
      if (enable[0]) {
        enableAction.set(IdeBundle.message("new.plugin.manager.incompatible.deps.action", size));
      }
    }

    return message;
  }
}