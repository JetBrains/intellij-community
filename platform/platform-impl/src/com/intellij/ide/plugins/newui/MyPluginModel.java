// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.ui.Messages;
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

/**
 * @author Alexander Lobas
 */
public class MyPluginModel extends InstalledPluginsTableModel implements PluginManagerMain.PluginEnabler {
  private final List<ListPluginComponent> myListComponents = new ArrayList<>();
  private final Map<IdeaPluginDescriptor, List<ListPluginComponent>> myListMap = new HashMap<>();
  private final Map<IdeaPluginDescriptor, List<GridCellPluginComponent>> myGridMap = new HashMap<>();
  private final List<PluginsGroup> myEnabledGroups = new ArrayList<>();
  private PluginsGroupComponent myDownloadedPanel;
  private PluginsGroup myDownloaded;
  private PluginsGroup myInstalling;
  private PluginsGroup myUpdates;
  private Configurable.TopComponentController myTopController;

  private static final Set<IdeaPluginDescriptor> myInstallingPlugins = new HashSet<>();
  private static final Set<IdeaPluginDescriptor> myInstallingWithUpdatesPlugins = new HashSet<>();
  private static final Map<IdeaPluginDescriptor, InstallPluginInfo> myInstallingInfos = new HashMap<>();

  public boolean needRestart;
  public boolean createShutdownCallback = true;
  public DetailsPagePluginComponent detailPanel;

  private StatusBarEx myStatusBar;

  private PluginUpdatesService myPluginUpdatesService;

  public MyPluginModel() {
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

  public void addComponent(@NotNull CellPluginComponent component) {
    if (component instanceof ListPluginComponent) {
      if (myInstallingPlugins.contains(component.myPlugin)) {
        return;
      }
      myListComponents.add((ListPluginComponent)component);

      List<ListPluginComponent> components = myListMap.get(component.myPlugin);
      if (components == null) {
        myListMap.put(component.myPlugin, components = new ArrayList<>());
      }
      components.add((ListPluginComponent)component);
    }
    else {
      List<GridCellPluginComponent> components = myGridMap.get(component.myPlugin);
      if (components == null) {
        myGridMap.put(component.myPlugin, components = new ArrayList<>());
      }
      components.add((GridCellPluginComponent)component);
    }
  }

  public void removeComponent(@NotNull CellPluginComponent component) {
    if (component instanceof ListPluginComponent) {
      myListComponents.remove((ListPluginComponent)component);

      List<ListPluginComponent> components = myListMap.get(component.myPlugin);
      if (components != null) {
        components.remove(component);
        if (components.isEmpty()) {
          myListMap.remove(component.myPlugin);
        }
      }
    }
    else {
      List<GridCellPluginComponent> components = myGridMap.get(component.myPlugin);
      if (components != null) {
        components.remove(component);
        if (components.isEmpty()) {
          myGridMap.remove(component.myPlugin);
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

  @NotNull
  public static Set<IdeaPluginDescriptor> getInstallingPlugins() {
    return myInstallingPlugins;
  }

  public static boolean isInstallingOrUpdate(@NotNull IdeaPluginDescriptor descriptor) {
    return myInstallingWithUpdatesPlugins.contains(descriptor);
  }

  public void installOrUpdatePlugin(@NotNull IdeaPluginDescriptor descriptor, boolean install) {
    if (!PluginManagerMain.checkThirdPartyPluginsAllowed(Collections.singletonList(descriptor))) {
      return;
    }

    PluginNode pluginNode;
    if (descriptor instanceof PluginNode) {
      pluginNode = (PluginNode)descriptor;
    }
    else {
      pluginNode = new PluginNode(descriptor.getPluginId(), descriptor.getName(), "-1");
      pluginNode.setDepends(Arrays.asList(descriptor.getDependentPluginIds()), descriptor.getOptionalDependentPluginIds());
      pluginNode.setRepositoryName(PluginInstaller.UNKNOWN_HOST_MARKER);
    }
    List<PluginNode> pluginsToInstall = ContainerUtil.newArrayList(pluginNode);

    PluginManagerMain.suggestToEnableInstalledDependantPlugins(this, pluginsToInstall);
    needRestart = true;

    installPlugin(pluginsToInstall, getAllRepoPlugins(), this, prepareToInstall(descriptor, install));
  }

  private static void installPlugin(@NotNull List<PluginNode> pluginsToInstall,
                                    @NotNull List<IdeaPluginDescriptor> allPlugins,
                                    @NotNull PluginManagerMain.PluginEnabler pluginEnabler,
                                    @NotNull InstallPluginInfo info) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      boolean cancel = false;
      boolean error = false;

      try {
        error = !PluginInstaller.prepareToInstall(pluginsToInstall, allPlugins, pluginEnabler, info.indicator);
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
      ApplicationManager.getApplication().invokeLater(() -> info.finish(success, _cancel), ModalityState.any());
    });
  }

  public void toBackground() {
    for (InstallPluginInfo info : myInstallingInfos.values()) {
      info.toBackground(myStatusBar);
    }
  }

  @NotNull
  private InstallPluginInfo prepareToInstall(@NotNull IdeaPluginDescriptor descriptor, boolean install) {
    InstallPluginInfo info = new InstallPluginInfo(descriptor, this, install);
    myInstallingInfos.put(descriptor, info);

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
        myDownloadedPanel.addGroup(myInstalling, 0);
      }
      else {
        myDownloadedPanel.addToGroup(myInstalling, descriptor);
      }

      myInstalling.titleWithCount();
      myDownloadedPanel.doLayout();
    }

    List<GridCellPluginComponent> gridComponents = myGridMap.get(descriptor);
    if (gridComponents != null) {
      for (GridCellPluginComponent gridComponent : gridComponents) {
        gridComponent.showProgress();
      }
    }
    List<ListPluginComponent> listComponents = myListMap.get(descriptor);
    if (listComponents != null) {
      for (ListPluginComponent listComponent : listComponents) {
        listComponent.showProgress();
      }
    }
    if (detailPanel != null && detailPanel.myPlugin == descriptor) {
      detailPanel.showProgress();
    }

    return info;
  }

  public void finishInstall(@NotNull IdeaPluginDescriptor descriptor, boolean success, boolean showErrors) {
    InstallPluginInfo info = finishInstall(descriptor);

    if (myInstallingWithUpdatesPlugins.isEmpty()) {
      myTopController.showProgress(false);
    }

    List<GridCellPluginComponent> gridComponents = myGridMap.get(descriptor);
    if (gridComponents != null) {
      for (GridCellPluginComponent gridComponent : gridComponents) {
        gridComponent.hideProgress(success);
      }
    }
    List<ListPluginComponent> listComponents = myListMap.get(descriptor);
    if (listComponents != null) {
      for (ListPluginComponent listComponent : listComponents) {
        listComponent.hideProgress(success);
      }
    }
    if (detailPanel != null && detailPanel.myPlugin == descriptor) {
      detailPanel.hideProgress(success);
    }

    if (info.install) {
      if (myInstalling != null && myInstalling.ui != null) {
        clearInstallingProgress(descriptor);
        if (myInstallingPlugins.isEmpty()) {
          myDownloadedPanel.removeGroup(myInstalling);
        }
        else {
          myDownloadedPanel.removeFromGroup(myInstalling, descriptor);
          myInstalling.titleWithCount();
        }
        myDownloadedPanel.doLayout();
      }
      if (success) {
        appendOrUpdateDescriptor(descriptor);
        appendDependsAfterInstall();
      }
    }
    else if (success) {
      if (myDownloaded != null && myDownloaded.ui != null) {
        CellPluginComponent component = myDownloaded.ui.findComponent(descriptor);
        if (component != null) {
          ((ListPluginComponent)component).changeUpdateToRestart();
        }
      }
      if (myUpdates != null) {
        myUpdates.titleWithCount();
      }
      myPluginUpdatesService.finishUpdate(descriptor);
    }

    info.indicator.cancel();

    if (!success && showErrors) {
      Messages.showErrorDialog("Plugin " + descriptor.getName() + " download or installing failed",
                               IdeBundle.message("action.download.and.install.plugin"));
    }
  }

  private void clearInstallingProgress(@NotNull IdeaPluginDescriptor descriptor) {
    if (myInstallingPlugins.isEmpty()) {
      for (CellPluginComponent listComponent : myInstalling.ui.plugins) {
        ((ListPluginComponent)listComponent).clearProgress();
      }
    }
    else {
      for (CellPluginComponent listComponent : myInstalling.ui.plugins) {
        if (listComponent.myPlugin == descriptor) {
          ((ListPluginComponent)listComponent).clearProgress();
          return;
        }
      }
    }
  }

  @NotNull
  public static InstallPluginInfo finishInstall(@NotNull IdeaPluginDescriptor descriptor) {
    InstallPluginInfo info = myInstallingInfos.remove(descriptor);
    myInstallingWithUpdatesPlugins.remove(descriptor);
    if (info.install) {
      myInstallingPlugins.remove(descriptor);
    }
    return info;
  }

  public void addProgress(@NotNull IdeaPluginDescriptor descriptor, @NotNull ProgressIndicatorEx indicator) {
    myInstallingInfos.get(descriptor).indicator.addStateDelegate(indicator);
  }

  public void removeProgress(@NotNull IdeaPluginDescriptor descriptor, @NotNull ProgressIndicatorEx indicator) {
    myInstallingInfos.get(descriptor).indicator.removeStateDelegate(indicator);
  }

  public void addEnabledGroup(@NotNull PluginsGroup group) {
    myEnabledGroups.add(group);
  }

  public void setDownloadedGroup(@NotNull PluginsGroupComponent panel,
                                 @NotNull PluginsGroup downloaded,
                                 @NotNull PluginsGroup installing) {
    myDownloadedPanel = panel;
    myDownloaded = downloaded;
    myInstalling = installing;
  }

  public void setUpdateGroup(@NotNull PluginsGroup group) {
    myUpdates = group;
  }

  private void appendDependsAfterInstall() {
    for (IdeaPluginDescriptor descriptor : InstalledPluginsState.getInstance().getInstalledPlugins()) {
      if (myDownloaded != null && myDownloaded.ui != null && myDownloaded.ui.findComponent(descriptor) != null) {
        continue;
      }

      appendOrUpdateDescriptor(descriptor);

      String id = descriptor.getPluginId().getIdString();

      for (Entry<IdeaPluginDescriptor, List<GridCellPluginComponent>> entry : myGridMap.entrySet()) {
        if (id.equals(entry.getKey().getPluginId().getIdString())) {
          for (GridCellPluginComponent component : entry.getValue()) {
            component.hideProgress(true);
          }
          break;
        }
      }
    }
  }

  @Override
  public void appendOrUpdateDescriptor(@NotNull IdeaPluginDescriptor descriptor) {
    super.appendOrUpdateDescriptor(descriptor);
    needRestart = true;
    if (myDownloaded == null) {
      return;
    }

    if (myDownloaded.ui == null) {
      myDownloaded.descriptors.add(descriptor);
      myDownloaded.titleWithEnabled(this);

      myDownloadedPanel.addGroup(myDownloaded, myInstalling == null || myInstalling.ui == null ? 0 : 1);
      myDownloadedPanel.setSelection(myDownloaded.ui.plugins.get(0));
      myDownloadedPanel.doLayout();

      addEnabledGroup(myDownloaded);
    }
    else {
      CellPluginComponent component = myDownloaded.ui.findComponent(descriptor);
      if (component != null) {
        myDownloadedPanel.setSelection(component);
        ((ListPluginComponent)component).changeUpdateToRestart();
        return;
      }

      myDownloadedPanel.addToGroup(myDownloaded, descriptor);
      myDownloaded.titleWithEnabled(this);
      myDownloadedPanel.setSelection(myDownloaded.ui.plugins.get(myDownloaded.descriptors.indexOf(descriptor)));
      myDownloadedPanel.doLayout();
    }
  }

  public boolean isEnabled(@NotNull IdeaPluginDescriptor plugin) {
    Boolean enabled = getEnabledMap().get(plugin.getPluginId());
    return enabled == null || enabled;
  }

  @NotNull
  public String getEnabledTitle(@NotNull IdeaPluginDescriptor plugin) {
    return isEnabled(plugin) ? "Disable" : "Enable";
  }

  public void changeEnableDisable(@NotNull IdeaPluginDescriptor plugin) {
    enableRows(new IdeaPluginDescriptor[]{plugin}, !isEnabled(plugin));
    updateAfterEnableDisable();
  }

  public void changeEnableDisable(@NotNull IdeaPluginDescriptor[] plugins, boolean state) {
    enableRows(plugins, state);
    updateAfterEnableDisable();
  }

  @Override
  public void enablePlugins(Set<IdeaPluginDescriptor> disabled) {
    changeEnableDisable(disabled.toArray(new IdeaPluginDescriptor[0]), true);
  }

  @Override
  public void disablePlugins(Set<IdeaPluginDescriptor> disabled) {
    changeEnableDisable(disabled.toArray(new IdeaPluginDescriptor[0]), false);
  }

  public void enableRequiredPlugins(@NotNull IdeaPluginDescriptor descriptor) {
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
            return ((IdeaPluginDescriptorImpl)d).getModules().contains(pluginId.getIdString());
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

  private void updateAfterEnableDisable() {
    for (ListPluginComponent component : myListComponents) {
      component.updateEnabledState();
    }
    for (PluginsGroup group : myEnabledGroups) {
      group.titleWithEnabled(this);
    }
  }

  public boolean showUninstallDialog(@NotNull List<CellPluginComponent> selection) {
    int size = selection.size();
    return showUninstallDialog(size == 1 ? selection.get(0).myPlugin.getName() : null, size);
  }

  public boolean showUninstallDialog(@Nullable String singleName, int count) {
    String message;
    if (singleName == null) {
      message = IdeBundle.message("prompt.uninstall.several.plugins", count);
    }
    else {
      message = IdeBundle.message("prompt.uninstall.plugin", singleName);
    }

    return Messages.showYesNoDialog(message, IdeBundle.message("title.plugin.uninstall"), Messages.getQuestionIcon()) == Messages.YES;
  }

  public void doUninstall(@NotNull Component uiParent, @NotNull IdeaPluginDescriptor descriptor, @Nullable Runnable update) {
    if (!dependent((IdeaPluginDescriptorImpl)descriptor).isEmpty()) {
      String message = IdeBundle.message("several.plugins.depend.on.0.continue.to.remove", descriptor.getName());
      String title = IdeBundle.message("title.plugin.uninstall");
      if (Messages.showYesNoDialog(uiParent, message, title, Messages.getQuestionIcon()) != Messages.YES) {
        return;
      }
    }

    try {
      ((IdeaPluginDescriptorImpl)descriptor).setDeleted(true);
      PluginInstaller.prepareToUninstall(descriptor.getPluginId());
      needRestart |= descriptor.isEnabled();
    }
    catch (IOException e) {
      PluginManagerMain.LOG.error(e);
    }

    if (update != null) {
      update.run();
    }

    List<ListPluginComponent> listComponents = myListMap.get(descriptor);
    if (listComponents != null) {
      for (ListPluginComponent listComponent : listComponents) {
        listComponent.updateAfterUninstall();
      }
    }

    for (ListPluginComponent component : myListComponents) {
      component.updateErrors();
    }
  }

  public boolean hasErrors(@NotNull IdeaPluginDescriptor plugin) {
    return PluginManagerCore.isIncompatible(plugin) || hasProblematicDependencies(plugin.getPluginId());
  }
}