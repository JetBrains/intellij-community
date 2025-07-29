// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.api.PluginDto;
import com.intellij.ide.plugins.marketplace.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.newEditor.SettingsDialog;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.platform.util.coroutines.CoroutineScopeKt;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.accessibility.AccessibleAnnouncerUtil;
import com.intellij.xml.util.XmlStringUtil;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@ApiStatus.Internal
public class MyPluginModel extends InstalledPluginsTableModel implements PluginEnabler {
  private static final Logger LOG = Logger.getInstance(MyPluginModel.class);
  private static final Boolean FINISH_DYNAMIC_INSTALLATION_WITHOUT_UI =
    SystemProperties.getBooleanProperty("plugins.finish-dynamic-plugin-installation-without-ui", true);

  private PluginsGroupComponent myInstalledPanel;
  private PluginsGroup myDownloaded;
  private PluginsGroup myInstalling;
  private Configurable.TopComponentController myTopController;
  private SortedSet<String> myVendors;
  private SortedSet<String> myTags;

  private static final Set<PluginUiModel> myInstallingPlugins = new HashSet<>();
  private static final Set<PluginId> myInstallingWithUpdatesPlugins = new HashSet<>();
  static final Map<PluginId, InstallPluginInfo> myInstallingInfos = new HashMap<>();

  public boolean needRestart;
  public boolean createShutdownCallback = true;

  private final @Nullable StatusBarEx myStatusBar;

  private PluginUpdatesService myPluginUpdatesService;

  private Runnable myInvalidFixCallback;
  private Consumer<PluginUiModel> myCancelInstallCallback;

  private final Map<PluginId, Boolean> myRequiredPluginsForProject = new HashMap<>();
  private final Set<PluginId> myUninstalled = new HashSet<>();
  private final PluginManagerCustomizer myPluginManagerCustomizer;

  private @Nullable FUSEventSource myInstallSource;

  public MyPluginModel(@Nullable Project project) {
    super(project);
    Window window = ProjectUtil.getActiveFrameOrWelcomeScreen();
    StatusBarEx statusBar = getStatusBar(window);
    myStatusBar = statusBar != null || window == null ?
                  statusBar :
                  getStatusBar(window.getOwner());
    myPluginManagerCustomizer = PluginManagerCustomizer.getInstance();
  }

  @ApiStatus.Internal
  public void setInstallSource(@Nullable FUSEventSource source) {
    this.myInstallSource = source;
  }

  private static @Nullable StatusBarEx getStatusBar(@Nullable Window frame) {
    return frame instanceof IdeFrame && !(frame instanceof WelcomeFrame) ?
           (StatusBarEx)((IdeFrame)frame).getStatusBar() :
           null;
  }

  @Override
  public boolean isModified() {
    return needRestart || !myInstallingInfos.isEmpty() || super.isModified();
  }

  /**
   * @return true if changes were applied without a restart
   */
  public boolean apply(@Nullable JComponent parent) throws ConfigurationException {
    ApplyPluginsStateResult applyResult = UiPluginManager.getInstance().applySession(mySessionId.toString(), parent, getProject());
    String error = applyResult.getError();
    if (error != null) {
      throw new ConfigurationException(XmlStringUtil.wrapInHtml(error)).withHtmlMessage();
    }
    applyResult.getPluginsToEnable().forEach(id -> super.setEnabled(id, PluginEnabledState.ENABLED));
    myUninstalled.clear();
    updateButtons();
    return !applyResult.getNeedRestart();
  }

  public void clear(@Nullable JComponent parentComponent) {
    UiPluginManager.getInstance().resetSession(mySessionId.toString(), false, parentComponent, newState -> {
      applyChangedStates(newState);
      updateAfterEnableDisable();
      return null;
    });
  }

  public void cancel(@Nullable JComponent parentComponent, boolean removeSession) {
    UiPluginManager.getInstance().resetSession(mySessionId.toString(), removeSession, parentComponent, newState -> {
      applyChangedStates(newState);
      return null;
    });
  }

  public void pluginInstalledFromDisk(@NotNull PluginInstallCallbackData callbackData, List<HtmlChunk> errors) {
    IdeaPluginDescriptor descriptor = callbackData.getPluginDescriptor();
    appendOrUpdateDescriptor(new PluginUiModelAdapter(descriptor), callbackData.getRestartNeeded(), errors);
  }

  public void addComponent(@NotNull ListPluginComponent component) {
    PluginUiModel descriptor = component.getPluginModel();
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

  public PluginUpdatesService getPluginUpdatesService() {
    return myPluginUpdatesService;
  }

  public @Nullable PluginsGroup getDownloadedGroup() {
    return myDownloaded;
  }

  public static @NotNull Set<PluginUiModel> getInstallingPlugins() {
    return myInstallingPlugins;
  }

  public static boolean isInstallingOrUpdate(PluginId pluginId) {
    return myInstallingWithUpdatesPlugins.contains(pluginId);
  }

  public String getSessionId() {
    return mySessionId.toString();
  }

  void installOrUpdatePlugin(@Nullable JComponent parentComponent,
                             @NotNull PluginUiModel descriptor,
                             @Nullable PluginUiModel updateDescriptor,
                             @NotNull ModalityState modalityState,
                             @NotNull UiPluginManagerController controller,
                             @NotNull Consumer<Boolean> callback) {
    boolean isUpdate = updateDescriptor != null;
    PluginUiModel actionDescriptor = isUpdate ? updateDescriptor : descriptor;
    if (!PluginManagerMain.checkThirdPartyPluginsAllowed(List.of(actionDescriptor.getDescriptor()))) {
      return;
    }

    var customization = PluginInstallationCustomization.findPluginInstallationCustomization(descriptor.getPluginId());
    if (customization != null) {
      customization.beforeInstallOrUpdate(isUpdate);
    }

    if (myInstallSource != null) {
      String pluginId = descriptor.getPluginId().getIdString();
      myInstallSource.logInstallPlugins(Collections.singletonList(pluginId));
    }

    if (descriptor.isFromMarketplace()) {
      FUSEventSource installSource = descriptor.getInstallSource();
      if (installSource != null) {
        installSource.logInstallPlugins(List.of(descriptor.getPluginId().getIdString()));
      }
    }

    Ref<Boolean> allowInstallWithoutRestart = Ref.create(true);
    Ref<Boolean> uninstallPlugin = Ref.create(false);
    if (isUpdate) {
      if (descriptor.isBundled()) {
        allowInstallWithoutRestart.set(false);
      }
      else if (!controller.allowLoadUnloadWithoutRestart(descriptor.getPluginId())) {
        allowInstallWithoutRestart.set(false);
      }
      else if (!descriptor.isEnabled()) {
        controller.deletePluginFiles(descriptor.getPluginId());
      }
      else if (controller.allowLoadUnloadSynchronously(descriptor.getPluginId())) {
        allowInstallWithoutRestart.set(controller.uninstallDynamicPlugin(parentComponent,
                                                                         mySessionId.toString(),
                                                                         descriptor.getPluginId(),
                                                                         true));
      }
      else {
        uninstallPlugin.set(true);
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
          if (uninstallPlugin.get()) {
            controller.performUninstall(mySessionId.toString(), descriptor.getPluginId());
          }
          PluginUiModel pluginUiModel = loadDetails(actionDescriptor, indicator);
          if (pluginUiModel == null) {
            return;
          }

          List<IdeaPluginDescriptor> pluginsToInstall = List.of(pluginUiModel.getDescriptor());
          ApplicationManager.getApplication().invokeAndWait(() -> {
            PluginManagerMain.suggestToEnableInstalledDependantPlugins(MyPluginModel.this, pluginsToInstall, updateDescriptor != null);
          }, modalityState);


          InstallPluginInfo info = new InstallPluginInfo((BgProgressIndicator)indicator,
                                                         descriptor,
                                                         MyPluginModel.this,
                                                         !isUpdate);
          prepareToInstall(info);
          InstallPluginRequest installPluginRequest = new InstallPluginRequest(mySessionId.toString(),
                                                                               descriptor.getPluginId(),
                                                                               List.of(PluginDto.fromModel(pluginUiModel)),
                                                                               allowInstallWithoutRestart.get(),
                                                                               FINISH_DYNAMIC_INSTALLATION_WITHOUT_UI,
                                                                               needRestart);

          controller.performInstallOperation(installPluginRequest,
                                             parentComponent,
                                             modalityState,
                                             indicator,
                                             MyPluginModel.this,
                                             result -> {
                                               applyInstallResult(result, info, callback);
                                               return null;
                                             });
        }

        private void applyInstallResult(InstallPluginResult result, InstallPluginInfo info, Consumer<Boolean> callback) {
          PluginDto installedDescriptor = result.getInstalledDescriptor();
          if (result.getSuccess()) {
            PluginUiModelKt.addInstalledSource(descriptor, controller.getTarget());
            if (installedDescriptor != null) {
              installedDescriptor.setInstallSource(descriptor.getInstallSource());
              info.setInstalledModel(installedDescriptor);
            }
          }
          disableById(result.getPluginsToDisable());
          if (myPluginManagerCustomizer != null) {
            myPluginManagerCustomizer.updateAfterModification(() -> {

              info.finish(result.getSuccess(), result.getCancel(), result.getShowErrors(), result.getRestartRequired(), getErrors(result));
              callback.accept(result.getSuccess());
              return null;
            });
          }
          else {
            info.finish(result.getSuccess(), result.getCancel(), result.getShowErrors(), result.getRestartRequired(), getErrors(result));
            callback.accept(result.getSuccess());
          }
        }

        private static @NotNull Map<PluginId, List<HtmlChunk>> getErrors(InstallPluginResult result) {
          return MyPluginModel.getErrors(result.getErrors());
        }


        private static @Nullable PluginUiModel loadDetails(@NotNull PluginUiModel descriptor, @NotNull ProgressIndicator indicator) {
          if (descriptor.isFromMarketplace()) {
            if (descriptor.getDetailsLoaded()) {
              return descriptor;
            }
            else {
              PluginUiModel model = MarketplaceRequests.getInstance().loadPluginDetails(descriptor, indicator);
              if (model != null) {
                return model;
              }
              return null;
            }
          }
          else {
            PluginUiModelBuilder builder = PluginUiModelBuilderFactory.getInstance().createBuilder(descriptor.getPluginId());
            builder.setName(descriptor.getName());
            builder.setDependencies(descriptor.getDependencies());
            builder.setRepositoryName(PluginInstaller.UNKNOWN_HOST_MARKER);
            return builder.build();
          }
        }
      }, new BgProgressIndicator());
  }


  public boolean toBackground() {
    for (InstallPluginInfo info : myInstallingInfos.values()) {
      info.toBackground(myStatusBar);
    }

    if (FINISH_DYNAMIC_INSTALLATION_WITHOUT_UI) {
      return !myInstallingInfos.isEmpty();
    }
    else {
      // FIXME(vadim.salavatov) idk what that does and it's not clear from the surrounding code :(
      boolean result = !myInstallingInfos.isEmpty();
      if (result) {
        InstallPluginInfo.showRestart();
      }
      return result;
    }
  }

  private void prepareToInstall(@NotNull InstallPluginInfo info) {
    PluginUiModel descriptor = info.getDescriptor();
    PluginId pluginId = descriptor.getPluginId();
    myInstallingInfos.put(pluginId, info);

    if (myInstallingWithUpdatesPlugins.isEmpty()) {
      myTopController.showProgress(true);
    }
    myInstallingWithUpdatesPlugins.add(pluginId);
    if (info.install) {
      myInstallingPlugins.add(descriptor);
    }

    if (info.install && myInstalling != null) {
      if (myInstalling.ui == null) {
        myInstalling.addModel(descriptor);
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
      if (panel.getDescriptorForActions() == descriptor) {
        panel.showInstallProgress();
      }
    }
  }

  /**
   * @param descriptor          Descriptor on which the installation was requested (can be a PluginNode or an IdeaPluginDescriptorImpl)
   * @param installedDescriptor If the plugin was loaded synchronously, the descriptor which has actually been installed; otherwise null.
   */
  void finishInstall(@NotNull PluginUiModel descriptor,
                     @Nullable PluginUiModel installedDescriptor,
                     @NotNull Map<PluginId, List<HtmlChunk>> errors, boolean success,
                     boolean showErrors,
                     boolean restartRequired) {
    InstallPluginInfo info = finishInstall(descriptor);

    if (myInstallingWithUpdatesPlugins.isEmpty()) {
      myTopController.showProgress(false);
    }

    PluginId pluginId = descriptor.getPluginId();
    List<ListPluginComponent> marketplaceComponents = myMarketplacePluginComponentMap.get(pluginId);
    List<HtmlChunk> errorList = errors.getOrDefault(pluginId, Collections.emptyList());
    if (marketplaceComponents != null) {
      for (ListPluginComponent gridComponent : marketplaceComponents) {
        if (installedDescriptor != null) {
          gridComponent.setPluginModel(installedDescriptor);
        }
        gridComponent.hideProgress(success, restartRequired, installedDescriptor);
        if (gridComponent.myInstalledDescriptorForMarketplace != null) {
          gridComponent.updateErrors(errorList);
        }
      }
    }
    List<ListPluginComponent> installedComponents = myInstalledPluginComponentMap.get(pluginId);
    if (installedComponents != null) {
      for (ListPluginComponent listComponent : installedComponents) {
        if (installedDescriptor != null) {
          listComponent.setPluginModel(installedDescriptor);
        }
        listComponent.hideProgress(success, restartRequired, installedDescriptor);
        listComponent.updateErrors(errorList);
      }
    }
    for (PluginDetailsPageComponent panel : myDetailPanels) {
      if (panel.isShowingPlugin(descriptor.getPluginId())) {
        panel.setPlugin(installedDescriptor);
        panel.hideProgress(success, restartRequired, installedDescriptor);
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
        appendOrUpdateDescriptor(installedDescriptor != null ? installedDescriptor : descriptor, restartRequired, errorList);
        appendDependsAfterInstall(success, restartRequired, errors, installedDescriptor);
        if (installedDescriptor == null && descriptor.isFromMarketplace() && myDownloaded != null && myDownloaded.ui != null) {
          ListPluginComponent component = myDownloaded.ui.findComponent(descriptor.getPluginId());
          if (component != null) {
            component.setInstalledPluginMarketplaceModel(descriptor);
          }
        }
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

    if (AccessibleAnnouncerUtil.isAnnouncingAvailable()) {
      JFrame frame = WindowManager.getInstance().findVisibleFrame();
      String key = success ? "plugins.configurable.plugin.installing.success" : "plugins.configurable.plugin.installing.failed";
      String message = IdeBundle.message(key, descriptor.getName());
      AccessibleAnnouncerUtil.announce(frame, message, true);
    }

    if (success) {
      needRestart |= restartRequired;
    }
    else if (showErrors) {
      Messages.showErrorDialog(getProject(), IdeBundle.message("plugins.configurable.plugin.installing.failed", descriptor.getName()),
                               IdeBundle.message("action.download.and.install.plugin"));
    }
  }

  private void clearInstallingProgress(@NotNull PluginUiModel descriptor) {
    if (myInstallingPlugins.isEmpty()) {
      for (ListPluginComponent listComponent : myInstalling.ui.plugins) {
        listComponent.clearProgress();
      }
    }
    else {
      for (ListPluginComponent listComponent : myInstalling.ui.plugins) {
        if (listComponent.getPluginModel() == descriptor) {
          listComponent.clearProgress();
          return;
        }
      }
    }
  }

  static @NotNull InstallPluginInfo finishInstall(@NotNull PluginUiModel descriptor) {
    InstallPluginInfo info = myInstallingInfos.remove(descriptor.getPluginId());
    info.close();
    myInstallingWithUpdatesPlugins.remove(descriptor.getPluginId());
    if (info.install) {
      myInstallingPlugins.remove(descriptor);
    }
    return info;
  }

  static void addProgress(@NotNull IdeaPluginDescriptor descriptor, @NotNull ProgressIndicatorEx indicator) {
    InstallPluginInfo info = myInstallingInfos.get(descriptor.getPluginId());
    if (info == null) return;
    info.indicator.addStateDelegate(indicator);
  }

  static void removeProgress(@NotNull IdeaPluginDescriptor descriptor, @NotNull ProgressIndicatorEx indicator) {
    InstallPluginInfo info = myInstallingInfos.get(descriptor.getPluginId());
    if (info == null) return;
    info.indicator.removeStateDelegate(indicator);
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

  private void appendDependsAfterInstall(boolean success,
                                         boolean restartRequired,
                                         Map<PluginId, List<HtmlChunk>> errors,
                                         @Nullable PluginUiModel installedDescriptor) {
    if (myDownloaded == null || myDownloaded.ui == null) {
      return;
    }
    for (IdeaPluginDescriptor descriptor : InstalledPluginsState.getInstance().getInstalledPlugins()) {
      PluginId pluginId = descriptor.getPluginId();
      if (myDownloaded.ui.findComponent(pluginId) != null) {
        continue;
      }

      appendOrUpdateDescriptor(new PluginUiModelAdapter(descriptor), restartRequired, errors.get(pluginId));

      String id = pluginId.getIdString();

      for (Map.Entry<PluginId, List<ListPluginComponent>> entry : myMarketplacePluginComponentMap.entrySet()) {
        if (id.equals(entry.getKey().getIdString())) {
          for (ListPluginComponent component : entry.getValue()) {
            component.hideProgress(success, restartRequired, installedDescriptor);
          }
          break;
        }
      }
    }
  }

  public void addDetailPanel(@NotNull PluginDetailsPageComponent detailPanel) {
    myDetailPanels.add(detailPanel);
  }

  private void appendOrUpdateDescriptor(@NotNull PluginUiModel descriptor) {
    int index = view.indexOf(descriptor);
    if (index < 0) {
      view.add(descriptor);
    }
    else {
      view.set(index, descriptor);
    }
  }

  void appendOrUpdateDescriptor(@NotNull PluginUiModel descriptor, boolean restartNeeded, List<HtmlChunk> errors) {
    PluginId id = descriptor.getPluginId();
    if (!UiPluginManager.getInstance().isPluginInstalled(id)) {
      appendOrUpdateDescriptor(descriptor);
      setEnabled(id, PluginEnabledState.ENABLED);
    }

    needRestart |= restartNeeded;

    if (myDownloaded == null) {
      return;
    }

    myVendors = null;
    myTags = null;

    if (myDownloaded.ui == null) {
      myDownloaded.addModel(descriptor);
      myDownloaded.titleWithEnabled(new PluginModelFacade(this));

      myInstalledPanel.addGroup(myDownloaded, myInstalling == null || myInstalling.ui == null ? 0 : 1);
      myInstalledPanel.setSelection(myDownloaded.ui.plugins.get(0));
      myInstalledPanel.doLayout();

      addEnabledGroup(myDownloaded);
    }
    else {
      ListPluginComponent component = myDownloaded.ui.findComponent(id);
      if (component != null) {
        if (restartNeeded) {
          myInstalledPanel.setSelection(component);
          component.enableRestart();
        }
        return;
      }
      myDownloaded.setErrors(descriptor, errors);
      myInstalledPanel.addToGroup(myDownloaded, descriptor);
      myDownloaded.titleWithEnabled(new PluginModelFacade(this));
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
      String sessionId = getSessionId();

      for (PluginUiModel descriptor : getInstalledDescriptors()) {
        myTags.addAll(PluginUiModelKt.calculateTags(descriptor, sessionId));
      }
    }
    return Collections.unmodifiableSortedSet(myTags);
  }

  public @NotNull List<PluginUiModel> getInstalledDescriptors() {
    assert myInstalledPanel != null;

    return myInstalledPanel
      .getGroups()
      .stream()
      .filter(group -> !group.excluded)
      .flatMap(group -> group.plugins.stream())
      .map(ListPluginComponent::getPluginModel)
      .collect(Collectors.toList());
  }

  private static @NotNull Map<String, Integer> getVendorsCount(@NotNull Collection<PluginUiModel> descriptors) {
    Map<String, Integer> vendors = new HashMap<>();

    for (PluginUiModel descriptor : descriptors) {
      String vendor = StringUtil.trim(descriptor.getVendor());
      if (!StringUtil.isEmptyOrSpaces(vendor)) {
        vendors.compute(vendor, (__, old) -> (old != null ? old : 0) + 1);
      }
    }

    return vendors;
  }

  public static boolean isVendor(@NotNull PluginUiModel descriptor, @NotNull Set<String> vendors) {
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

  boolean isUninstalled(@NotNull PluginId pluginId) {
    return myUninstalled.contains(pluginId);
  }

  void addUninstalled(@NotNull PluginId pluginId) {
    myUninstalled.add(pluginId);
  }

  @Override
  protected void setEnabled(@NotNull PluginId pluginId, @Nullable PluginEnabledState enabled) {
    super.setEnabled(pluginId, enabled);
    boolean isEnabled = enabled == null || enabled.isEnabled();
    UiPluginManager.getInstance().setPluginStatus(mySessionId.toString(), List.of(pluginId), isEnabled);
  }

  public boolean setEnabledState(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                                 @NotNull PluginEnableDisableAction action) {
    List<PluginId> pluginIds = ContainerUtil.map(descriptors, it -> it.getPluginId());
    SetEnabledStateResult result =
      UiPluginManager.getInstance().enablePlugins(mySessionId.toString(), pluginIds, action.isEnable(), getProject());
    if (result.getPluginNamesToSwitch().isEmpty()) {
      applyChangedStates(result.getChangedStates());
      updateEnabledStateInUi();
    }
    else {
      askToUpdateDependencies(action, result.getPluginNamesToSwitch(), result.getPluginsIdsToSwitch());
    }
    return true;
  }

  public boolean setEnabledStateAsync(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                                      @NotNull PluginEnableDisableAction action) {
    List<PluginId> pluginIds = ContainerUtil.map(descriptors, it -> it.getPluginId());
    PluginModelAsyncOperationsExecutor.INSTANCE.enablePlugins(getCoroutineScope(), mySessionId.toString(), pluginIds, action.isEnable(),
                                                              getProject(), result -> {
        if (result.getPluginNamesToSwitch().isEmpty()) {
          applyChangedStates(result.getChangedStates());
          updateEnabledStateInUi();
        }
        else {
          askToUpdateDependencies(action, result.getPluginNamesToSwitch(), result.getPluginsIdsToSwitch());
        }
        return null;
      });
    return true;
  }

  private void askToUpdateDependencies(@NotNull PluginEnableDisableAction action,
                                       Set<String> pluginNames,
                                       Set<PluginId> pluginIds) {
    if (!createUpdateDependenciesDialog(pluginNames, action)) {
      return;
    }
    SetEnabledStateResult result =
      UiPluginManager.getInstance().setEnableStateForDependencies(mySessionId.toString(), pluginIds, action.isEnable());
    if (!result.getChangedStates().isEmpty()) {
      applyChangedStates(result.getChangedStates());
      updateEnabledStateInUi();
    }
  }

  private boolean createUpdateDependenciesDialog(@NotNull Collection<String> dependencies,
                                                 @NotNull PluginEnableDisableAction action) {
    int size = dependencies.size();
    if (size == 0) {
      return true;
    }
    boolean hasOnlyOneDependency = size == 1;

    String key = switch (action) {
      case ENABLE_GLOBALLY -> hasOnlyOneDependency ? "dialog.message.enable.required.plugin" : "dialog.message.enable.required.plugins";
      case DISABLE_GLOBALLY ->
        hasOnlyOneDependency ? "dialog.message.disable.dependent.plugin" : "dialog.message.disable.dependent.plugins";
    };

    String dependenciesText = hasOnlyOneDependency ?
                              dependencies.iterator().next() :
                              dependencies.stream()
                                .map("&nbsp;".repeat(5)::concat)
                                .collect(Collectors.joining("<br>"));

    boolean enabled = action.isEnable();
    return MessageDialogBuilder
      .okCancel(IdeBundle.message(enabled ? "dialog.title.enable.required.plugins" : "dialog.title.disable.dependent.plugins"),
                IdeBundle.message(key, dependenciesText))
      .yesText(IdeBundle.message(enabled ? "plugins.configurable.enable" : "plugins.configurable.disable"))
      .noText(Messages.getCancelButton())
      .ask(getProject());
  }


  private void updateEnabledStateInUi() {
    updateAfterEnableDisable();
    for (PluginsGroup group : myEnabledGroups) {
      group.titleWithEnabled(new PluginModelFacade(this));
    }
    runInvalidFixCallback();
    PluginUpdatesService.reapplyFilter();
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
    Set<PluginId> pluginsToEnable = UiPluginManager.getInstance().enableRequiredPlugins(mySessionId.toString(), descriptor.getPluginId());
    setStatesByIds(pluginsToEnable, true);
  }

  private void runInvalidFixCallback() {
    if (myInvalidFixCallback != null) {
      ApplicationManager.getApplication().invokeLater(myInvalidFixCallback, ModalityState.any());
    }
  }

  public void setInvalidFixCallback(@Nullable Runnable invalidFixCallback) {
    myInvalidFixCallback = invalidFixCallback;
  }

  public void setCancelInstallCallback(@NotNull Consumer<PluginUiModel> callback) {
    myCancelInstallCallback = callback;
  }

  private void updateButtons() {
    PluginModelAsyncOperationsExecutor.INSTANCE.updateButtons(getCoroutineScope(),
                                                              myInstalledPluginComponents,
                                                              myMarketplacePluginComponentMap,
                                                              myDetailPanels);
  }

  private void applyChangedStates(Map<PluginId, Boolean> changedStates) {
    changedStates.forEach((pluginId, enabled) -> {
      super.setEnabled(pluginId, enabled ? PluginEnabledState.ENABLED : PluginEnabledState.DISABLED);
    });
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

  public void uninstallAndUpdateUi(@NotNull PluginUiModel descriptor) {
    uninstallAndUpdateUi(descriptor, UiPluginManager.getInstance().getController());
  }

  public static Map<PluginId, List<HtmlChunk>> getErrors(Map<PluginId, CheckErrorsResult> errorCheckResults) {
    return errorCheckResults.entrySet().stream()
      .collect(Collectors.toMap(
        Map.Entry::getKey,
        entry -> getErrors(entry.getValue())
      ));
  }

  @ApiStatus.Internal
  public void uninstallAndUpdateUi(@NotNull PluginUiModel descriptor, UiPluginManagerController controller) {
    CoroutineScope scope = CoroutineScopeKt.childScope(getCoroutineScope(), getClass().getName(), Dispatchers.getIO(), true);
    myTopController.showProgress(true);
    for (PluginDetailsPageComponent panel : myDetailPanels) {
      if (panel.getDescriptorForActions() == descriptor) {
        panel.showUninstallProgress(scope);
      }
    }
    try {
      PluginModelAsyncOperationsExecutor.INSTANCE
        .performUninstall(scope, descriptor, mySessionId.toString(), controller, (needRestartForUninstall, errorCheckResult) -> {
          needRestart |= descriptor.isEnabled() && needRestartForUninstall;
          Map<PluginId, List<HtmlChunk>> errors = getErrors(errorCheckResult);
          if (myPluginManagerCustomizer != null) {
            myPluginManagerCustomizer.updateAfterModification(() -> {
              updateUiAfterUninstall(descriptor, needRestartForUninstall, errors);
              return null;
            });
          }
          else {
            updateUiAfterUninstall(descriptor, needRestartForUninstall, errors);
          }
          return null;
        });
    }
    finally {
      for (PluginDetailsPageComponent panel : myDetailPanels) {
        if (panel.getDescriptorForActions() == descriptor) {
          panel.hideProgress();
        }
      }
    }
  }

  private void updateUiAfterUninstall(@NotNull PluginUiModel descriptor, boolean needRestartForUninstall,
                                      Map<PluginId, List<HtmlChunk>> errors) {
    PluginId pluginId = descriptor.getPluginId();
    myTopController.showProgress(false);
    List<ListPluginComponent> listComponents = myInstalledPluginComponentMap.get(pluginId);
    if (listComponents != null) {
      for (ListPluginComponent listComponent : listComponents) {
        listComponent.updateAfterUninstall(needRestartForUninstall);
      }
    }

    List<ListPluginComponent> marketplaceComponents = myMarketplacePluginComponentMap.get(pluginId);
    if (marketplaceComponents != null) {
      for (ListPluginComponent component : marketplaceComponents) {
        if (component.myInstalledDescriptorForMarketplace != null) {
          component.updateAfterUninstall(needRestartForUninstall);
        }
      }
    }
    for (ListPluginComponent component : myInstalledPluginComponents) {
      component.updateErrors(errors.getOrDefault(component.getPluginModel().getPluginId(), Collections.emptyList()));
    }
    for (List<ListPluginComponent> plugins : myMarketplacePluginComponentMap.values()) {
      for (ListPluginComponent plugin : plugins) {
        if (plugin.myInstalledDescriptorForMarketplace != null) {
          plugin.updateErrors(errors.get(plugin.getPluginModel().getPluginId()));
        }
      }
    }

    for (PluginDetailsPageComponent panel : myDetailPanels) {
      if (panel.getDescriptorForActions() == descriptor) {
        panel.updateAfterUninstall(needRestartForUninstall);
      }
    }
  }

  public boolean hasErrors(@NotNull IdeaPluginDescriptor descriptor) {
    return !getErrors(descriptor).isEmpty();
  }

  public @NotNull List<? extends HtmlChunk> getErrors(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId pluginId = descriptor.getPluginId();
    if (isDeleted(descriptor)) {
      return List.of();
    }
    CheckErrorsResult response = UiPluginManager.getInstance().getErrorsSync(mySessionId.toString(), pluginId);
    return getErrors(response);
  }

  public static @NotNull List<HtmlChunk> getErrors(@NotNull CheckErrorsResult checkErrorsResult) {
    if (checkErrorsResult.isDisabledDependencyError()) {
      String loadingError = checkErrorsResult.getLoadingError();
      return loadingError != null ? List.of(createTextChunk(loadingError)) : List.of();
    }

    ArrayList<HtmlChunk> errors = new ArrayList<>();

    Set<String> requiredPluginNames = checkErrorsResult.getRequiredPluginNames();
    if (requiredPluginNames.isEmpty()) {
      return errors;
    }
    String message = IdeBundle.message("new.plugin.manager.incompatible.deps.tooltip",
                                       requiredPluginNames.size(),
                                       joinPluginNamesOrIds(requiredPluginNames));
    errors.add(createTextChunk(message));

    if (checkErrorsResult.getSuggestToEnableRequiredPlugins()) {
      String action = IdeBundle.message("new.plugin.manager.incompatible.deps.action", requiredPluginNames.size());
      errors.add(HtmlChunk.link("link", action));
    }

    return Collections.unmodifiableList(errors);
  }

  protected @NotNull Collection<PluginUiModel> getCustomRepoPlugins() {
    return CustomPluginRepositoryService.getInstance().getCustomRepositoryPlugins();
  }

  public static @Unmodifiable @NotNull Set<String> getPluginNames(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors) {
    return ContainerUtil.map2Set(descriptors,
                                 IdeaPluginDescriptor::getName);
  }

  public static @NotNull String joinPluginNamesOrIds(@NotNull Set<String> pluginNames) {
    return StringUtil.join(pluginNames,
                           StringUtil::wrapWithDoubleQuote,
                           ", ");
  }

  private final Map<String, Icon> myIcons = new HashMap<>(); // local cache for PluginLogo WeakValueMap

  public @NotNull Icon getIcon(@NotNull IdeaPluginDescriptor descriptor, boolean big, boolean error, boolean disabled) {


    String key = descriptor.getPluginId().getIdString() + big + error + disabled;
    Icon icon = myIcons.get(key);
    if (icon == null) {
      icon = PluginLogo.getIcon(descriptor, big, error, disabled);
      if (icon != PluginLogo.INSTANCE.getDefault().getIcon(big, error, disabled)) {
        myIcons.put(key, icon);
      }
    }
    return icon;
  }

  private static @Unmodifiable @NotNull List<String> getDependenciesOnPlugins(@NotNull Project project) {
    return ContainerUtil.map(ExternalDependenciesManager.getInstance(project).getDependencies(DependencyOnPlugin.class),
                             DependencyOnPlugin::getPluginId);
  }

  private static @NotNull HtmlChunk.Element createTextChunk(@NotNull @Nls String message) {
    return HtmlChunk.span().addText(message);
  }
}
