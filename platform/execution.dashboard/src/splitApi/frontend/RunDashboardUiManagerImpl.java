// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi.frontend;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.RunContentDescriptorId;
import com.intellij.execution.dashboard.RunDashboardManagerProxy;
import com.intellij.execution.dashboard.RunDashboardService;
import com.intellij.execution.dashboard.RunDashboardUiManager;
import com.intellij.execution.services.ServiceEventListener;
import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.execution.services.ServiceViewManager;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.platform.execution.dashboard.BackendRunDashboardManagerState;
import com.intellij.platform.execution.dashboard.RunDashboardManagerImpl;
import com.intellij.platform.execution.dashboard.RunDashboardServiceViewContributor;
import com.intellij.platform.execution.dashboard.RunDashboardTypePanel;
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardServiceDto;
import com.intellij.platform.execution.dashboard.splitApi.frontend.tree.FrontendRunConfigurationNode;
import com.intellij.platform.execution.serviceView.ServiceViewManagerImpl;
import com.intellij.platform.ide.productMode.IdeProductMode;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static com.intellij.platform.execution.dashboard.splitApi.frontend.RunDashboardGroupingRule.GROUPING_RULE_EP_NAME;
import static com.intellij.platform.execution.dashboard.splitApi.frontend.RunDashboardUiUtils.updateContentToolbar;
import static com.intellij.platform.execution.serviceView.ServiceViewImplementationChooserKt.isOldMonolithServiceViewEnabled;
import static com.intellij.platform.execution.serviceView.ServiceViewImplementationChooserKt.isShowLuxedRunToolwindowInServicesView;

@ApiStatus.Internal
public final class RunDashboardUiManagerImpl implements RunDashboardUiManager {
  private final Project myProject;
  private final ContentManager myContentManager;
  private final ContentManagerListener myServiceContentManagerListener;
  private String myToolWindowId;
  private RunDashboardComponentWrapper myContentWrapper;
  private JComponent myEmptyContent;
  private RunDashboardTypePanel myTypeContent;

  public RunDashboardUiManagerImpl(Project project) {
    myProject = project;
    ContentFactory contentFactory = ContentFactory.getInstance();
    myContentManager = contentFactory.createContentManager(new PanelContentUI(), false, project);
    myServiceContentManagerListener = new ServiceContentManagerListener();
    myContentManager.addContentManagerListener(myServiceContentManagerListener);
    initExtensionPointListeners();
  }

  @ApiStatus.Internal
  @NotNull
  public static RunDashboardUiManagerImpl getInstance(@NotNull Project project) {
    return (RunDashboardUiManagerImpl)RunDashboardUiManager.getInstance(project);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void initExtensionPointListeners() {
    ExtensionPointListener frontendDashboardUpdater = new ExtensionPointListener() {
      @Override
      public void extensionAdded(@NotNull Object extension, @NotNull PluginDescriptor pluginDescriptor) {
        updateDashboard(true);
      }

      @Override
      public void extensionRemoved(@NotNull Object extension, @NotNull PluginDescriptor pluginDescriptor) {
        myProject.getMessageBus().syncPublisher(ServiceEventListener.TOPIC).handle(
          ServiceEventListener.ServiceEvent.createUnloadSyncResetEvent(RunDashboardServiceViewContributor.class));
      }
    };
    GROUPING_RULE_EP_NAME.addExtensionPointListener(frontendDashboardUpdater, myProject);
  }

  @NotNull
  @Override
  public ContentManager getDashboardContentManager() {
    return myContentManager;
  }

  @NotNull
  @Override
  public String getToolWindowId() {
    if (myToolWindowId == null) {
      if (LightEdit.owns(myProject)) {
        myToolWindowId = ToolWindowId.SERVICES;
      }
      else {
        String toolWindowId = ServiceViewManager.getInstance(myProject).getToolWindowId(RunDashboardServiceViewContributor.class);
        myToolWindowId = toolWindowId != null ? toolWindowId : ToolWindowId.SERVICES;
      }
    }
    return myToolWindowId;
  }

  @Override
  public @NotNull Icon getToolWindowIcon() {
    return AllIcons.Toolwindows.ToolWindowServices;
  }

  private void updateDashboard(boolean withStructure) {
    FrontendRunDashboardManager.getInstance(myProject).updateDashboard(withStructure);
  }

  @Override
  public @NotNull Predicate<Content> getReuseCondition() {
    return Conditions.alwaysFalse();
  }

  /**
   * Synchronizes content between the dashboard content manager and the run content manager
   * based on the current set of run configuration types that should be shown in services.
   * <p>
   * Content is moved out of the dashboard when its run configuration type is removed from services.
   * Content is moved into the dashboard when its run configuration type is added to services.
   */
  public boolean syncContentsFromBackend() {
    FrontendRunDashboardManager frontendManager = FrontendRunDashboardManager.getInstance(myProject);
    Set<String> types = frontendManager.getTypes();

    // Find content to remove from dashboard (type no longer in services)
    Set<Content> managedContents = Set.of(myContentManager.getContents());
    Set<Content> toRemove = new HashSet<>();
    for (Content content : managedContents) {
      RunContentDescriptor descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content);
      if (descriptor == null) continue;

      String typeId = descriptor.getRunConfigurationTypeId();
      if (typeId != null && !types.contains(typeId)) {
        toRemove.add(content);
      }
    }
    moveRemovedContent(toRemove);

    // Find content to add to dashboard (type now in services)
    RunContentManager runContentManager = RunContentManager.getInstance(myProject);
    Set<Content> toAdd = new HashSet<>();
    for (RunContentDescriptor descriptor : runContentManager.getRunContentDescriptors()) {
      String typeId = descriptor.getRunConfigurationTypeId();
      if (typeId == null || !types.contains(typeId)) continue;

      Content content = descriptor.getAttachedContent();
      if (content == null || managedContents.contains(content)) continue;

      // Only add content that is not already being managed by the dashboard
      if (content.getManager() != myContentManager) {
        toAdd.add(content);
      }
    }
    moveAddedContent(toAdd);

    return !toRemove.isEmpty() || !toAdd.isEmpty();
  }

  private void moveRemovedContent(Collection<Content> contents) {
    RunContentManagerImpl runContentManager = (RunContentManagerImpl)RunContentManager.getInstance(myProject);
    for (Content content : contents) {
      RunContentDescriptor descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content);
      if (descriptor == null) continue;

      Executor executor = RunContentManagerImpl.getExecutorByContent(content);
      if (executor == null) continue;

      descriptor.setContentToolWindowId(null);
      updateContentToolbar(content, true);
      runContentManager.moveContent(executor, descriptor);
    }
  }

  private void moveAddedContent(Collection<Content> contents) {
    RunContentManagerImpl runContentManager = (RunContentManagerImpl)RunContentManager.getInstance(myProject);
    for (Content content : contents) {
      RunContentDescriptor descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content);
      if (descriptor == null) continue;

      Executor executor = RunContentManagerImpl.getExecutorByContent(content);
      if (executor == null) continue;

      descriptor.setContentToolWindowId(getToolWindowId());
      runContentManager.moveContent(executor, descriptor);
    }
  }

  @Override
  public void setSelectedContent(@NotNull Content content) {
    ContentManager contentManager = content.getManager();
    if (contentManager == null || content == contentManager.getSelectedContent()) return;

    if (contentManager != myContentManager) {
      contentManager.setSelectedContent(content);
      return;
    }

    myContentManager.removeContentManagerListener(myServiceContentManagerListener);
    myContentManager.setSelectedContent(content);
    updateContentToolbar(content, false);
    myContentManager.addContentManagerListener(myServiceContentManagerListener);
  }

  @Override
  public void removeFromSelection(@NotNull Content content) {
    ContentManager contentManager = content.getManager();
    if (contentManager == null || content != contentManager.getSelectedContent()) return;

    if (contentManager != myContentManager) {
      contentManager.removeFromSelection(content);
      return;
    }

    myContentManager.removeContentManagerListener(myServiceContentManagerListener);
    myContentManager.removeFromSelection(content);
    myContentManager.addContentManagerListener(myServiceContentManagerListener);
  }

  @ApiStatus.Internal
  @NotNull
  public JComponent getEmptyContent() {
    if (myEmptyContent == null) {
      JBPanelWithEmptyText textPanel = new JBPanelWithEmptyText()
        .withEmptyText(ExecutionBundle.message("run.dashboard.configurations.message"));
      textPanel.setFocusable(true);
      JComponent wrapped = UiDataProvider.wrapComponent(textPanel,
                                                        sink -> sink.set(PlatformDataKeys.TREE_EXPANDER_HIDE_ACTIONS_IF_NO_EXPANDER, true));
      JPanel mainPanel = new NonOpaquePanel(new BorderLayout());
      mainPanel.add(wrapped, BorderLayout.CENTER);
      RunDashboardUiUtils.setupToolbar(mainPanel, wrapped, myProject);
      myEmptyContent = mainPanel;
    }
    return myEmptyContent;
  }

  @ApiStatus.Internal
  @NotNull
  public RunDashboardTypePanel getTypeContent() {
    if (myTypeContent == null) {
      myTypeContent = new RunDashboardTypePanel(myProject);
    }
    return myTypeContent;
  }

  @Override
  public void contentReused(@NotNull Content content, @NotNull RunContentDescriptor oldDescriptor) {
    if (content.getManager() == myContentManager) {
      RunDashboardManagerProxy.getInstance(myProject).updateServiceRunContentDescriptor(content, oldDescriptor);
    }
  }

  @Override
  public boolean isSupported(@NotNull Executor executor) {
    return IdeProductMode.isMonolith()
           || (IdeProductMode.isBackend() && isOldMonolithServiceViewEnabled())
           || ToolWindowId.DEBUG.equals(executor.getId())
           || (ToolWindowId.RUN.equals(executor.getId()) && isShowLuxedRunToolwindowInServicesView());
  }

  @Override
  public void navigateToServiceOnRun(@NotNull RunContentDescriptorId descriptorId, Boolean focus) {
    if (IdeProductMode.isFrontend()) return;
    RunDashboardManagerImpl runDashboardManager = RunDashboardManagerImpl.getInstance(myProject);
    runDashboardManager.navigateToServiceOnRun(descriptorId, focus);
  }

  @ApiStatus.Internal
  @NotNull
  public RunDashboardComponentWrapper getContentWrapper() {
    if (myContentWrapper == null) {
      myContentWrapper = new RunDashboardComponentWrapper();
      ClientProperty.put(myContentWrapper, ServiceViewDescriptor.ACTION_HOLDER_KEY, Boolean.TRUE);
    }
    return myContentWrapper;
  }

  private @Nullable FrontendRunDashboardService findService(Content content) {
    RunContentDescriptor descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content);
    if (descriptor == null) return null;
    RunContentDescriptorId contentId = descriptor.getId();
    if (contentId == null) return null;
    List<@NotNull FrontendRunDashboardService> services = FrontendRunDashboardManager.getInstance(myProject).getServicePresentations();
    return ContainerUtil.find(services,
                              service -> contentId.equals(service.getRunDashboardServiceDto().getContentId()));
  }

  private final class ServiceContentManagerListener implements ContentManagerListener {
    private volatile Content myPreviousSelection = null;

    @Override
    public void selectionChanged(@NotNull ContentManagerEvent event) {
      boolean onAdd = event.getOperation() == ContentManagerEvent.ContentOperation.add;
      Content content = event.getContent();
      if (onAdd) {
        updateContentToolbar(content, false);
      }

      FrontendRunDashboardManager.getInstance(myProject).updateDashboard(true);

      if (onAdd) {
        FrontendRunDashboardService service = findService(content);
        if (service != null) {
          RunDashboardServiceDto dto = service.getRunDashboardServiceDto();
          ((ServiceViewManagerImpl)ServiceViewManager.getInstance(myProject))
            .trackingSelect(new FrontendRunConfigurationNode(myProject, service), RunDashboardServiceViewContributor.class,
                            dto.isActivateToolWindowBeforeRun(), dto.isFocusToolWindowBeforeRun())
            .onSuccess(selected -> {
              if (selected != Boolean.TRUE) {
                selectPreviousContent();
              }
            })
            .onError(t -> {
              selectPreviousContent();
            });
        }
      }
      else {
        myPreviousSelection = content;
      }
    }

    private void selectPreviousContent() {
      Content previousSelection = myPreviousSelection;
      if (previousSelection != null) {
        AppUIExecutor.onUiThread().expireWith(previousSelection).submit(() -> {
          setSelectedContent(previousSelection);
        });
      }
    }

    @Override
    public void contentAdded(@NotNull ContentManagerEvent event) {
      Content content = event.getContent();
      if (IdeProductMode.isFrontend()) {
        FrontendRunDashboardManager.getInstance(myProject).attachServiceRunContentDescriptor(content);
        return;
      }

      // Call of RunDashboardManagerImpl is ok since we checked that it's not a frontend
      RunDashboardManagerImpl runDashboardManager = RunDashboardManagerImpl.getInstance(myProject);

      RunContentDescriptor descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content);
      RunContentDescriptorId descriptorId = descriptor == null ? null : descriptor.getId();
      if (descriptorId == null) return;

      RunDashboardService backendService = runDashboardManager.attachServiceRunContentDescriptor(descriptorId);
      if (IdeProductMode.isMonolith() && runDashboardManager.isOpenRunningConfigInNewTab() && backendService != null) {
        RunDashboardServiceDto dto = BackendRunDashboardManagerState.createServiceDto(backendService);
        FrontendRunDashboardService frontendService = new FrontendRunDashboardService(dto);
        ServiceViewManager.getInstance(myProject).extract(new FrontendRunConfigurationNode(myProject, frontendService),
                                                          RunDashboardServiceViewContributor.class);
      }
    }

    @Override
    public void contentRemoved(@NotNull ContentManagerEvent event) {
      Content content = event.getContent();
      if (myPreviousSelection == content) {
        myPreviousSelection = null;
      }
      if (IdeProductMode.isFrontend()) {
        FrontendRunDashboardManager.getInstance(myProject).detachServiceRunContentDescriptor(content);
        return;
      }

      // Call of RunDashboardManagerImpl is ok since we checked that it's not a frontend
      RunDashboardManagerImpl runDashboardManager = RunDashboardManagerImpl.getInstance(myProject);

      RunContentDescriptor descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content);
      RunContentDescriptorId descriptorId = descriptor == null ? null : descriptor.getId();
      if (descriptorId == null) return;

      runDashboardManager.detachServiceRunContentDescriptor(descriptorId);
    }
  }
}
