// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard;

import com.intellij.execution.actions.StopAction;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.dashboard.*;
import com.intellij.execution.dashboard.actions.ExecutorAction;
import com.intellij.execution.dashboard.actions.RunDashboardGroupNode;
import com.intellij.execution.runners.FakeRerunAction;
import com.intellij.execution.services.*;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.ui.icons.IconId;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.WeighedItem;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.platform.execution.dashboard.actions.RunDashboardDoubleClickRunAction;
import com.intellij.platform.execution.dashboard.splitApi.CustomLinkDto;
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardServiceDto;
import com.intellij.platform.execution.dashboard.splitApi.ServiceCustomizationDto;
import com.intellij.platform.execution.dashboard.splitApi.frontend.*;
import com.intellij.platform.execution.dashboard.splitApi.frontend.tree.FolderDashboardGroupingRule.FolderDashboardGroup;
import com.intellij.platform.execution.dashboard.splitApi.frontend.tree.FrontendRunConfigurationNode;
import com.intellij.platform.execution.dashboard.splitApi.frontend.tree.GroupingNode;
import com.intellij.platform.execution.dashboard.splitApi.frontend.tree.RunDashboardGroupImpl;
import com.intellij.platform.execution.dashboard.splitApi.frontend.tree.RunDashboardStatusFilter;
import com.intellij.platform.execution.serviceView.ServiceViewManagerImpl;
import com.intellij.pom.Navigatable;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;

import static com.intellij.execution.RunContentDescriptorIdImplKt.RUN_CONTENT_DESCRIPTOR_ID;
import static com.intellij.execution.dashboard.RunDashboardServiceIdKt.SELECTED_DASHBOARD_SERVICE_ID;
import static com.intellij.ide.ui.icons.IconIdKt.icon;
import static com.intellij.openapi.actionSystem.PlatformDataKeys.TREE_EXPANDER_HIDE_ACTIONS_IF_NO_EXPANDER;
import static com.intellij.platform.execution.dashboard.RunDashboardServiceViewContributorHelper.*;
import static com.intellij.platform.execution.serviceView.ServiceViewImplementationChooserKt.shouldEnableServicesViewInCurrentEnvironment;

public final class RunDashboardServiceViewContributor
  implements ServiceViewGroupingContributor<FrontendRunConfigurationNode, GroupingNode>,
             RunDashboardGroupNode {

  private static final DataProvider TREE_EXPANDER_HIDE_PROVIDER =
    id -> TREE_EXPANDER_HIDE_ACTIONS_IF_NO_EXPANDER.is(id) ? true : null;

  @Override
  public @NotNull ServiceViewDescriptor getViewDescriptor(@NotNull Project project) {
    return new RunDashboardContributorViewDescriptor(project);
  }

  @Override
  public @NotNull @Unmodifiable List<FrontendRunConfigurationNode> getServices(@NotNull Project project) {
    if (!shouldEnableServicesViewInCurrentEnvironment()) return Collections.emptyList();
    FrontendRunDashboardManager frontendManager = FrontendRunDashboardManager.getInstance(project);
    if (!frontendManager.isInitialized()) {
      frontendManager.tryStartInitialization();
      return ContainerUtil.emptyList();
    }

    return ContainerUtil.map(frontendManager.getServicePresentations(),
                             value -> new FrontendRunConfigurationNode(project, value));
  }

  @Override
  public @NotNull ServiceViewDescriptor getServiceDescriptor(@NotNull Project project, @NotNull FrontendRunConfigurationNode node) {
    return new RunConfigurationServiceViewDescriptor(node);
  }

  @Override
  public @NotNull List<GroupingNode> getGroups(@NotNull FrontendRunConfigurationNode node) {
    if (!shouldEnableServicesViewInCurrentEnvironment()) return Collections.emptyList();

    List<GroupingNode> result = new ArrayList<>();
    GroupingNode parentGroupNode = null;
    for (RunDashboardGroupingRule groupingRule : RunDashboardGroupingRule.GROUPING_RULE_EP_NAME.getExtensions()) {
      RunDashboardGroup group = groupingRule.getGroup(node);
      if (group != null) {
        GroupingNode groupingNode = new GroupingNode(node.getProject(),
                                                     parentGroupNode == null ? null : parentGroupNode.getGroup(), group);
        groupingNode.setParent(parentGroupNode);
        result.add(groupingNode);
        parentGroupNode = groupingNode;
      }
    }
    return result;
  }

  @Override
  public @NotNull ServiceViewDescriptor getGroupDescriptor(@NotNull GroupingNode node) {
    RunDashboardGroup group = node.getGroup();
    if (group instanceof FolderDashboardGroup) {
      return new RunDashboardFolderGroupViewDescriptor(node);
    }
    if (group instanceof RunDashboardGroupImpl<?> dashboardGroup && dashboardGroup.getValue() instanceof String type) {
      return new RunDashboardTypeGroupViewDescriptor(node, type);
    }
    return new RunDashboardGroupViewDescriptor(node);
  }

  @Override
  public @NotNull List<@NotNull Object> getChildren(@NotNull Project project, @NotNull AnActionEvent e) {
    if (!shouldEnableServicesViewInCurrentEnvironment()) return Collections.emptyList();

    return ((ServiceViewManagerImpl)ServiceViewManager.getInstance(project))
      .getChildrenSafe(e, List.of(this), RunDashboardServiceViewContributor.class);
  }

  private static ActionGroup getToolbarActions(@Nullable RunContentDescriptor descriptor) {
    DefaultActionGroup actionGroup = new DefaultActionGroup() {
      @Override
      public @NotNull List<? extends @NotNull AnAction> postProcessVisibleChildren(
        @NotNull AnActionEvent e,
        @NotNull List<? extends @NotNull AnAction> visibleChildren) {
        return visibleChildren.stream().filter(
            o -> !(o instanceof StopAction) &&
                 !(o instanceof FakeRerunAction) &&
                 !(o instanceof ExecutorAction))
          .toList();
      }
    };
    DefaultActionGroup result = new DefaultActionGroup();
    result.add(ActionManager.getInstance().getAction(RunDashboardUiUtils.RUN_DASHBOARD_CONTENT_TOOLBAR));
    result.add(actionGroup);

    RunnerLayoutUiImpl ui = RunDashboardUiUtils.getRunnerLayoutUi(descriptor);
    if (ui != null) {
      actionGroup.addAll(ui.getContentUI().getActions(true));
    }
    else {
      ActionToolbar toolbar = RunDashboardUiUtils.findActionToolbar(descriptor);
      if (toolbar != null) {
        actionGroup.add(toolbar.getActionGroup());
      }
    }
    return result;
  }

  private static ActionGroup getPopupActions() {
    DefaultActionGroup actions = new DefaultActionGroup();
    ActionManager actionManager = ActionManager.getInstance();
    actions.add(actionManager.getAction(RunDashboardUiUtils.RUN_DASHBOARD_CONTENT_TOOLBAR));
    actions.addSeparator();
    actions.add(actionManager.getAction(ActionPlaces.RUN_DASHBOARD_POPUP));
    return actions;
  }

  private static @Nullable FrontendRunConfigurationNode getRunConfigurationNode(@NotNull DnDEvent event, @NotNull Project project) {
    try {
      List<?> items = (List<?>)event.getTransferData(ServiceViewDnDDescriptor.LIST_DATA_FLAVOR);
      Object item = ContainerUtil.getOnlyItem(items);
      if (item == null) return null;

      FrontendRunConfigurationNode node = ObjectUtils.tryCast(item, FrontendRunConfigurationNode.class);
      if (node != null && !node.getProject().equals(project)) return null;

      return node;
    }
    catch (UnsupportedFlavorException | IOException e) {
      return null;
    }
  }

  private static final class RunConfigurationServiceViewDescriptor implements ServiceViewDescriptor,
                                                                              ServiceViewDnDDescriptor, UiDataProvider {
    private final FrontendRunConfigurationNode myNode;

    RunConfigurationServiceViewDescriptor(FrontendRunConfigurationNode node) {
      myNode = node;
    }

    @Override
    public String getId() {
      return myNode.getService().getServiceViewId();
    }

    @Override
    public String getUniqueId() {
      return String.valueOf(myNode.getService().getUuid().getUid());
    }

    @Override
    public JComponent getContentComponent() {
      Project project = myNode.getProject();

      var luxedComponent = getLuxedComponentIfRegistered();
      if (luxedComponent != null) {
        luxedComponent.bind();
        return luxedComponent;
      }

      var uiManager = RunDashboardUiManagerImpl.getInstance(project);
      RunDashboardComponentWrapper wrapper = uiManager.getContentWrapper();
      Content content = myNode.getContent();
      if (content == null || content.getManager() != uiManager.getDashboardContentManager()) {
        wrapper.setContent(uiManager.getEmptyContent());
      }
      else {
        ContentManager contentManager = content.getManager();
        if (contentManager == null) return null;

        wrapper.setContent(contentManager.getComponent());
      }
      return wrapper;
    }

    private @Nullable FrontendDashboardLuxComponent getLuxedComponentIfRegistered() {
      var id = myNode.getValue().getRunDashboardServiceDto().getUuid();
      return FrontendRunDashboardLuxHolder.getInstance(myNode.getProject()).getComponentOrNull(id);
    }

    @Override
    public @NotNull ItemPresentation getContentPresentation() {
      Content content = myNode.getContent();
      if (content != null) {
        return new PresentationData(content.getDisplayName(), null, content.getIcon(), null);
      }
      else {
        RunDashboardServiceDto service = myNode.getService();
        IconId iconId = service.getIconId();
        Icon icon = iconId == null ? null : icon(iconId);
        return new PresentationData(service.getName(), null, icon, null);
      }
    }

    @Override
    public ActionGroup getToolbarActions() {
      return RunDashboardServiceViewContributor.getToolbarActions(myNode.getDescriptor());
    }

    @Override
    public ActionGroup getPopupActions() {
      return RunDashboardServiceViewContributor.getPopupActions();
    }

    @Override
    public @NotNull ItemPresentation getPresentation() {
      return myNode.getPresentation();
    }

    @Override
    public void onNodeSelected(List<Object> selectedServices) {
      if (selectedServices.size() != 1) return;

      FrontendDashboardLuxComponent luxedComponent = getLuxedComponentIfRegistered();
      if (luxedComponent != null) {
        luxedComponent.bind();
        return;
      }

      Content content = myNode.getContent();
      if (content == null) return;

      RunDashboardUiManager.getInstance(myNode.getProject()).setSelectedContent(content);
    }

    @Override
    public void onNodeUnselected() {
      FrontendDashboardLuxComponent luxedComponent = getLuxedComponentIfRegistered();
      if (luxedComponent != null) {
        luxedComponent.unbind();
        return;
      }

      Content content = myNode.getContent();
      if (content == null) return;

      RunDashboardUiManager.getInstance(myNode.getProject()).removeFromSelection(content);
    }

    @Override
    public Navigatable getNavigatable() {
      return new Navigatable() {
        @Override
        public void navigate(boolean requestFocus) {
          scheduleNavigateToService(myNode.getProject(), myNode.getValue(), requestFocus);
        }

        @Override
        public boolean canNavigate() {
          return true;
        }

        @Override
        public boolean canNavigateToSource() {
          return canNavigate();
        }
      };
    }

    @Override
    public @Nullable Object getPresentationTag(Object fragment) {
      if (fragment instanceof String stringFragment) {
        RunDashboardServiceId uuid = myNode.getValue().getRunDashboardServiceDto().getUuid();
        ServiceCustomizationDto customization = FrontendRunDashboardManager.getInstance(myNode.getProject()).getCustomizationById(uuid);
        if (customization == null) return null;

        CustomLinkDto tagDto = ContainerUtil.find(customization.getLinks(), it -> it.getPresentableText().equals(stringFragment));
        if (tagDto == null) {
          return null;
        }
        else {
          return (Runnable)() -> {
            scheduleNodeLinkNavigation(myNode.getProject(), tagDto.getPresentableText(), myNode);
          };
        }
      }
      return null;
    }

    @Override
    public @Nullable Runnable getRemover() {
      if (myNode.getValue().getRunDashboardServiceDto().isRemovable()) {
        return () -> {
          scheduleRemoveService(myNode.getProject(), myNode.getValue());
        };
      }
      else {
        return null;
      }
    }

    @Override
    public boolean canDrop(@NotNull DnDEvent event, @NotNull Position position) {
      if (position != Position.INTO) {
        return getRunConfigurationNode(event, myNode.getProject()) != null;
      }
      return false;
    }

    @Override
    public void drop(@NotNull DnDEvent event, @NotNull Position position) {
      if (position != Position.INTO) {
        Project project = myNode.getProject();
        FrontendRunConfigurationNode node = getRunConfigurationNode(event, project);
        if (node != null) {
          reorderConfigurations(project, node, position);
        }
      }
    }

    private void reorderConfigurations(Project project, FrontendRunConfigurationNode node, Position position) {
      scheduleReorderConfigurations(project, myNode, node, position);
    }

    @Override
    public boolean isVisible() {
      RunDashboardStatusFilter statusFilter =
        FrontendRunDashboardManager.getInstance(myNode.getProject()).getStatusFilter();
      return statusFilter.isVisible(myNode);
    }

    @Override
    public boolean handleDoubleClick(@NotNull MouseEvent event) {
      if (!RunDashboardDoubleClickRunAction.Companion.isDoubleClickRunEnabled$intellij_platform_execution_dashboard()) {
        return ServiceViewDescriptor.super.handleDoubleClick(event);
      }

      scheduleRerunConfiguration(myNode.getProject(), myNode.getValue());
      return true;
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(CommonDataKeys.PROJECT,  myNode.getProject());
      sink.set(SELECTED_DASHBOARD_SERVICE_ID, myNode.getValue().getRunDashboardServiceDto().getUuid());
      sink.set(RUN_CONTENT_DESCRIPTOR_ID, myNode.getValue().getRunDashboardServiceDto().getContentId());
      sink.set(TREE_EXPANDER_HIDE_ACTIONS_IF_NO_EXPANDER, true);

      Content content = myNode.getContent();
      if (content == null) {
        return;
      }
      var component = content.getComponent();
      if (component instanceof UiDataProvider provider) {
        provider.uiDataSnapshot(sink);
      }
      else {
        var provider = DataManagerImpl.getDataProviderEx(content.getComponent());
        if (provider == null) {
          return;
        }
        sink.uiDataSnapshot(provider);
      }
    }
  }

  private static class RunDashboardGroupViewDescriptor implements ServiceViewDescriptor, WeighedItem, UiDataProvider {
    protected final RunDashboardGroup myGroup;
    protected final GroupingNode myNode;
    private final PresentationData myPresentationData;

    protected RunDashboardGroupViewDescriptor(GroupingNode node) {
      myNode = node;
      myGroup = node.getGroup();
      myPresentationData = new PresentationData();
      myPresentationData.setPresentableText(myGroup.getName());
      myPresentationData.setIcon(myGroup.getIcon());
    }

    @Override
    public @Nullable String getId() {
      return getId(myNode);
    }

    @Override
    public ActionGroup getToolbarActions() {
      return RunDashboardServiceViewContributor.getToolbarActions(null);
    }

    @Override
    public ActionGroup getPopupActions() {
      return RunDashboardServiceViewContributor.getPopupActions();
    }

    @Override
    public @NotNull ItemPresentation getPresentation() {
      return myPresentationData;
    }

    @Override
    public int getWeight() {
      Object value = ((RunDashboardGroupImpl<?>)myGroup).getValue();
      if (value instanceof WeighedItem) {
        return ((WeighedItem)value).getWeight();
      }
      return 0;
    }

    @Override
    public @Nullable Runnable getRemover() {
      Object value = ((RunDashboardGroupImpl<?>)myGroup).getValue();
      if (!(value instanceof String configurationTypeId)) {
        Logger.getInstance(RunDashboardGroupViewDescriptor.class).debug("Unexpected group value class: " + value + " (" + value.getClass() + ")");
        return null;
      }
      ConfigurationType type = ConfigurationTypeUtil.findConfigurationType(configurationTypeId);
      if (type != null) {
        return () -> {
          RunDashboardManager runDashboardManager = RunDashboardManagerProxy.getInstance(myNode.getProject());
          Set<String> types = new HashSet<>(runDashboardManager.getTypes());
          types.remove(type.getId());
          runDashboardManager.setTypes(types);
        };
      }
      return null;
    }

    @Override
    public @NotNull JComponent getContentComponent() {
      return RunDashboardUiManagerImpl.getInstance(myNode.getProject()).getEmptyContent();
    }

    private static String getId(GroupingNode node) {
      AbstractTreeNode<?> parent = node.getParent();
      if (parent instanceof GroupingNode) {
        return getId((GroupingNode)parent) + "/" + getId(node.getGroup());
      }
      return getId(node.getGroup());
    }

    private static String getId(RunDashboardGroup group) {
      if (group instanceof RunDashboardGroupImpl) {
        Object value = ((RunDashboardGroupImpl<?>)group).getValue();
        if (value instanceof ConfigurationType) {
          return ((ConfigurationType)value).getId();
        }
      }
      return group.getName();
    }

    @Override
    public void uiDataSnapshot(DataSink sink) {
      sink.set(TREE_EXPANDER_HIDE_ACTIONS_IF_NO_EXPANDER, true);
    }
  }

  private static final class RunDashboardFolderGroupViewDescriptor extends RunDashboardGroupViewDescriptor
    implements ServiceViewDnDDescriptor {
    RunDashboardFolderGroupViewDescriptor(GroupingNode node) {
      super(node);
    }

    @Override
    public Runnable getRemover() {
      return () -> {
        scheduleRemoveFolderGroup(myNode.getProject(), myNode.getValue().second.getName());
      };
    }

    @Override
    public boolean canDrop(@NotNull DnDEvent event, @NotNull ServiceViewDnDDescriptor.Position position) {
      return position == Position.INTO && getRunConfigurationNode(event, ((FolderDashboardGroup)myGroup).getProject()) != null;
    }

    @Override
    public void drop(@NotNull DnDEvent event, @NotNull ServiceViewDnDDescriptor.Position position) {
      Project project = ((FolderDashboardGroup)myGroup).getProject();
      FrontendRunConfigurationNode node = getRunConfigurationNode(event, project);
      if (node == null) return;
      scheduleDropRunConfigurationNodeOnFolderNode(project, node.getValue(), myGroup);
    }
  }

  private static class RunDashboardTypeGroupViewDescriptor extends RunDashboardGroupViewDescriptor {
    private final ConfigurationType myType;

    RunDashboardTypeGroupViewDescriptor(GroupingNode node, String type) {
      super(node);
      myType = ConfigurationTypeUtil.findConfigurationType(type);
    }

    @Override
    public void onNodeSelected(List<Object> selectedServices) {
      RunDashboardUiManagerImpl.getInstance(myNode.getProject()).getTypeContent().setType(myType);
    }

    @Override
    public void onNodeUnselected() {
      RunDashboardUiManagerImpl.getInstance(myNode.getProject()).getTypeContent().setType(null);
    }

    @Override
    public @NotNull JComponent getContentComponent() {
      return RunDashboardUiManagerImpl.getInstance(myNode.getProject()).getTypeContent();
    }

    @Override
    public void uiDataSnapshot(DataSink sink) {
      sink.set(PlatformDataKeys.TREE_EXPANDER,
               RunDashboardUiManagerImpl.getInstance(myNode.getProject()).getTypeContent().getTreeExpander());
      sink.set(TREE_EXPANDER_HIDE_ACTIONS_IF_NO_EXPANDER, true);
    }
  }

  private static final class RunDashboardContributorViewDescriptor extends SimpleServiceViewDescriptor
    implements ServiceViewToolWindowDescriptor, UiDataProvider {
    private final Project myProject;

    RunDashboardContributorViewDescriptor(@NotNull Project project) {
      super("Run Dashboard", AllIcons.Actions.Execute);
      myProject = project;
    }

    @Override
    public ActionGroup getToolbarActions() {
      return RunDashboardServiceViewContributor.getToolbarActions(null);
    }

    @Override
    public ActionGroup getPopupActions() {
      return RunDashboardServiceViewContributor.getPopupActions();
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, new RunDashboardServiceViewDeleteProvider());
      sink.set(TREE_EXPANDER_HIDE_ACTIONS_IF_NO_EXPANDER, true);
    }

    @Override
    public @NotNull JComponent getContentComponent() {
      return RunDashboardUiManagerImpl.getInstance(myProject).getEmptyContent();
    }

    @Override
    public @NotNull String getToolWindowId() {
      return getId();
    }

    @Override
    public @NotNull Icon getToolWindowIcon() {
      return AllIcons.Toolwindows.ToolWindowRun;
    }

    @Override
    public @NotNull String getStripeTitle() {
      @NlsSafe String title = getToolWindowId();
      return title;
    }

    @Override
    public boolean isExclusionAllowed() {
      return false;
    }
  }
}
