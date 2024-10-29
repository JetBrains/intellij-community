// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard;

import com.intellij.execution.*;
import com.intellij.execution.actions.StopAction;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.dashboard.*;
import com.intellij.execution.dashboard.actions.ExecutorAction;
import com.intellij.execution.dashboard.actions.RunDashboardGroupNode;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.FakeRerunAction;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.services.*;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.ide.util.treeView.WeighedItem;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.platform.execution.dashboard.actions.RunDashboardDoubleClickRunAction;
import com.intellij.platform.execution.dashboard.tree.FolderDashboardGroupingRule.FolderDashboardGroup;
import com.intellij.platform.execution.dashboard.tree.GroupingNode;
import com.intellij.platform.execution.dashboard.tree.RunConfigurationNode;
import com.intellij.platform.execution.dashboard.tree.RunDashboardGroupImpl;
import com.intellij.platform.execution.dashboard.tree.RunDashboardStatusFilter;
import com.intellij.platform.execution.serviceView.ServiceViewManagerImpl;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;

public final class RunDashboardServiceViewContributor
  implements ServiceViewGroupingContributor<RunDashboardServiceViewContributor.RunConfigurationContributor, GroupingNode>,
             RunDashboardGroupNode {

  @NonNls static final String RUN_DASHBOARD_CONTENT_TOOLBAR = "RunDashboardContentToolbar";

  private static final DataProvider TREE_EXPANDER_HIDE_PROVIDER =
    id -> PlatformDataKeys.TREE_EXPANDER_HIDE_ACTIONS_IF_NO_EXPANDER.is(id) ? true : null;

  @NotNull
  @Override
  public ServiceViewDescriptor getViewDescriptor(@NotNull Project project) {
    return new RunDashboardContributorViewDescriptor(project);
  }

  @NotNull
  @Override
  public List<RunConfigurationContributor> getServices(@NotNull Project project) {
    RunDashboardManagerImpl runDashboardManager = (RunDashboardManagerImpl)RunDashboardManager.getInstance(project);
    return ContainerUtil.map(runDashboardManager.getRunConfigurations(),
                             value -> new RunConfigurationContributor(
                               new RunConfigurationNode(project, value,
                                                        RunDashboardManagerImpl.getCustomizers(value.getSettings(),
                                                                                               value.getDescriptor()))));
  }

  @NotNull
  @Override
  public ServiceViewDescriptor getServiceDescriptor(@NotNull Project project, @NotNull RunConfigurationContributor contributor) {
    return contributor.getViewDescriptor(project);
  }

  @NotNull
  @Override
  public List<GroupingNode> getGroups(@NotNull RunConfigurationContributor contributor) {
    List<GroupingNode> result = new ArrayList<>();
    GroupingNode parentGroupNode = null;
    for (RunDashboardGroupingRule groupingRule : RunDashboardManagerImpl.GROUPING_RULE_EP_NAME.getExtensions()) {
      RunDashboardGroup group = groupingRule.getGroup(contributor.asService());
      if (group != null) {
        GroupingNode node = new GroupingNode(contributor.asService().getProject(),
                                             parentGroupNode == null ? null : parentGroupNode.getGroup(), group);
        node.setParent(parentGroupNode);
        result.add(node);
        parentGroupNode = node;
      }
    }
    return result;
  }

  @NotNull
  @Override
  public ServiceViewDescriptor getGroupDescriptor(@NotNull GroupingNode node) {
    RunDashboardGroup group = node.getGroup();
    if (group instanceof FolderDashboardGroup) {
      return new RunDashboardFolderGroupViewDescriptor(node);
    }
    if (group instanceof RunDashboardGroupImpl<?> dashboardGroup && dashboardGroup.getValue() instanceof ConfigurationType type) {
      return new RunDashboardTypeGroupViewDescriptor(node, type);
    }
    return new RunDashboardGroupViewDescriptor(node);
  }

  @Override
  public @NotNull List<@NotNull Object> getChildren(@NotNull Project project, @NotNull AnActionEvent e) {
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
    result.add(ActionManager.getInstance().getAction(RUN_DASHBOARD_CONTENT_TOOLBAR));
    result.add(actionGroup);

    RunnerLayoutUiImpl ui = RunDashboardManagerImpl.getRunnerLayoutUi(descriptor);
    if (ui != null) {
      actionGroup.addAll(ui.getContentUI().getActions(true));
    }
    else {
      ActionToolbar toolbar = RunDashboardManagerImpl.findActionToolbar(descriptor);
      if (toolbar != null) {
        actionGroup.add(toolbar.getActionGroup());
      }
    }
    return result;
  }

  private static ActionGroup getPopupActions() {
    DefaultActionGroup actions = new DefaultActionGroup();
    ActionManager actionManager = ActionManager.getInstance();
    actions.add(actionManager.getAction(RUN_DASHBOARD_CONTENT_TOOLBAR));
    actions.addSeparator();
    actions.add(actionManager.getAction(ActionPlaces.RUN_DASHBOARD_POPUP));
    return actions;
  }

  @Nullable
  private static RunDashboardRunConfigurationNode getRunConfigurationNode(@NotNull DnDEvent event, @NotNull Project project) {
    try {
      List<?> items = (List<?>)event.getTransferData(ServiceViewDnDDescriptor.LIST_DATA_FLAVOR);
      Object item = ContainerUtil.getOnlyItem(items);
      if (item == null) return null;

      RunDashboardRunConfigurationNode node = ObjectUtils.tryCast(item, RunDashboardRunConfigurationNode.class);
      if (node != null && !node.getConfigurationSettings().getConfiguration().getProject().equals(project)) return null;

      return node;
    }
    catch (UnsupportedFlavorException | IOException e) {
      return null;
    }
  }

  private static final class RunConfigurationServiceViewDescriptor implements ServiceViewDescriptor,
                                                                              ServiceViewLocatableDescriptor,
                                                                              ServiceViewDnDDescriptor {
    private final RunConfigurationNode myNode;

    RunConfigurationServiceViewDescriptor(RunConfigurationNode node) {
      myNode = node;
    }

    @Override
    public String getId() {
      RunConfiguration configuration = myNode.getConfigurationSettings().getConfiguration();
      return configuration.getType().getId() + "/" + configuration.getName();
    }

    @Override
    public JComponent getContentComponent() {
      RunDashboardManagerImpl manager = ((RunDashboardManagerImpl)RunDashboardManager.getInstance(myNode.getProject()));
      RunDashboardComponentWrapper wrapper = manager.getContentWrapper();
      Content content = myNode.getContent();
      if (content == null || content.getManager() != manager.getDashboardContentManager()) {
        wrapper.setContent(manager.getEmptyContent());
        wrapper.setContentId(null);
      }
      else {
        ContentManager contentManager = content.getManager();
        if (contentManager == null) return null;

        wrapper.setContent(contentManager.getComponent());
        wrapper.setContentId(getContentId());
      }
      return wrapper;
    }

    private Integer getContentId() {
      RunContentDescriptor descriptor = myNode.getDescriptor();
      ProcessHandler handler = descriptor == null ? null : descriptor.getProcessHandler();
      return handler == null ? null : handler.hashCode();
    }

    @NotNull
    @Override
    public ItemPresentation getContentPresentation() {
      Content content = myNode.getContent();
      if (content != null) {
        return new PresentationData(content.getDisplayName(), null, content.getIcon(), null);
      }
      else {
        RunConfiguration configuration = myNode.getConfigurationSettings().getConfiguration();
        return new PresentationData(configuration.getName(), null, configuration.getIcon(), null);
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

    @NotNull
    @Override
    public ItemPresentation getPresentation() {
      return myNode.getPresentation();
    }

    @Override
    public DataProvider getDataProvider() {
      Content content = myNode.getContent();
      if (content == null) return TREE_EXPANDER_HIDE_PROVIDER;

      // Try to get data provider from content's component itself.
      // No need to search for data providers in content's component swing hierarchy,
      // because it is inside service view component for which data is provided.
      DataProvider componentProvider = DataManagerImpl.getDataProviderEx(content.getComponent());
      return id -> {
        Object data = TREE_EXPANDER_HIDE_PROVIDER.getData(id);
        if (data != null) return data;

        return componentProvider == null ? null : componentProvider.getData(id);
      };
    }

    @Override
    public void onNodeSelected(List<Object> selectedServices) {
      Content content = myNode.getContent();
      if (content == null) return;

      ((RunDashboardManagerImpl)RunDashboardManager.getInstance(myNode.getProject())).setSelectedContent(content);
    }

    @Override
    public void onNodeUnselected() {
      Content content = myNode.getContent();
      if (content == null) return;

      ((RunDashboardManagerImpl)RunDashboardManager.getInstance(myNode.getProject())).removeFromSelection(content);
    }

    @Override
    public Navigatable getNavigatable() {
      NullableLazyValue<PsiElement> value = NullableLazyValue.lazyNullable(() -> {
        for (RunDashboardCustomizer customizer : myNode.getCustomizers()) {
          PsiElement psiElement = customizer.getPsiElement(myNode);
          if (psiElement != null) return psiElement;
        }
        return null;
      });
      return new Navigatable() {
        @Override
        public void navigate(boolean requestFocus) {
          PsiNavigateUtil.navigate(value.getValue(), requestFocus);
        }

        @Override
        public boolean canNavigate() {
          return value.getValue() != null;
        }

        @Override
        public boolean canNavigateToSource() {
          return canNavigate();
        }
      };
    }

    @Nullable
    @Override
    public VirtualFile getVirtualFile() {
      return ReadAction.compute(() -> {
        for (RunDashboardCustomizer customizer : myNode.getCustomizers()) {
          PsiElement psiElement = customizer.getPsiElement(myNode);
          if (psiElement != null) {
            return PsiUtilCore.getVirtualFile(psiElement);
          }
        }
        return null;
      });
    }

    @Nullable
    @Override
    public Object getPresentationTag(Object fragment) {
      Map<Object, Object> links = myNode.getUserData(RunDashboardCustomizer.NODE_LINKS);
      return links == null ? null : links.get(fragment);
    }

    @Nullable
    @Override
    public Runnable getRemover() {
      RunnerAndConfigurationSettings settings = myNode.getConfigurationSettings();
      RunManager runManager = RunManager.getInstance(settings.getConfiguration().getProject());
      return runManager.hasSettings(settings) ? () -> runManager.removeConfiguration(settings) : null;
    }

    @Override
    public boolean canDrop(@NotNull DnDEvent event, @NotNull Position position) {
      if (position != Position.INTO) {
        return getRunConfigurationNode(event, myNode.getConfigurationSettings().getConfiguration().getProject()) != null;
      }
      for (RunDashboardCustomizer customizer : myNode.getCustomizers()) {
        if (customizer.canDrop(myNode, event)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void drop(@NotNull DnDEvent event, @NotNull Position position) {
      if (position != Position.INTO) {
        Project project = myNode.getConfigurationSettings().getConfiguration().getProject();
        RunDashboardRunConfigurationNode node = getRunConfigurationNode(event, project);
        if (node != null) {
          reorderConfigurations(project, node, position);
        }
        return;
      }
      for (RunDashboardCustomizer customizer : myNode.getCustomizers()) {
        if (customizer.canDrop(myNode, event)) {
          customizer.drop(myNode, event);
          return;
        }
      }
    }

    private void reorderConfigurations(Project project, RunDashboardRunConfigurationNode node, Position position) {
      RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
      runManager.fireBeginUpdate();
      try {
        node.getConfigurationSettings().setFolderName(myNode.getConfigurationSettings().getFolderName());

        Object2IntMap<RunnerAndConfigurationSettings> indices = new Object2IntOpenHashMap<>();
        int i = 0;
        for (RunnerAndConfigurationSettings each : runManager.getAllSettings()) {
          if (each.equals(node.getConfigurationSettings())) continue;

          if (each.equals(myNode.getConfigurationSettings())) {
            if (position == Position.ABOVE) {
              indices.put(node.getConfigurationSettings(), i++);
              indices.put(myNode.getConfigurationSettings(), i++);
            }
            else if (position == Position.BELOW) {
              indices.put(myNode.getConfigurationSettings(), i++);
              indices.put(node.getConfigurationSettings(), i++);
            }
          }
          else {
            indices.put(each, i++);
          }
        }
        runManager.setOrder(Comparator.comparingInt(indices::getInt));
      }
      finally {
        runManager.fireEndUpdate();
      }
    }

    @Override
    public boolean isVisible() {
      RunDashboardStatusFilter statusFilter =
        ((RunDashboardManagerImpl)RunDashboardManager.getInstance(myNode.getProject())).getStatusFilter();
      return statusFilter.isVisible(myNode);
    }

    @Override
    public boolean handleDoubleClick(@NotNull MouseEvent event) {
      if (!RunDashboardDoubleClickRunAction.Companion.isDoubleClickRunEnabled$intellij_platform_execution_dashboard()) {
        return ServiceViewDescriptor.super.handleDoubleClick(event);
      }

      Executor executor = getExecutor();
      if (executor == null) return true;

      DataContext dataContext = DataManager.getInstance().getDataContext(event.getComponent());
      AnAction action = new ExecutorAction() {
        @Override
        protected Executor getExecutor() {
          return executor;
        }

        @Override
        protected void update(@NotNull AnActionEvent e, boolean running) {
        }
      };
      Project project = myNode.getProject();
      Presentation presentation = new Presentation();
      AnActionEvent actionEvent =
        AnActionEvent.createEvent(dataContext, presentation, ActionPlaces.SERVICES_POPUP, ActionUiKind.POPUP, event);
      ReadAction.nonBlocking(() -> {
          action.update(actionEvent);
          return presentation.isEnabled();
        })
        .coalesceBy(RunDashboardManager.getInstance(project))
        .expireWith(project)
        .finishOnUiThread(ModalityState.current(), enabled -> {
          if (enabled) {
            action.actionPerformed(actionEvent);
          }
        })
        .submit(AppExecutorUtil.getAppExecutorService());

      return true;
    }

    private Executor getExecutor() {
      RunContentDescriptor descriptor = myNode.getDescriptor();
      if (descriptor != null) {
        Set<Executor> executors = ExecutionManager.getInstance(myNode.getProject()).getExecutors(descriptor);
        Executor executor = ContainerUtil.getFirstItem(executors);
        if (executor != null) return executor;
      }
      RunConfiguration configuration = myNode.getConfigurationSettings().getConfiguration();
      Executor runExecutor = DefaultRunExecutor.getRunExecutorInstance();

      ProgramRunner<?> runner = ProgramRunner.getRunner(runExecutor.getId(), configuration);
      if (runner != null) {
        return runExecutor;
      }

      Executor debugExecutor = ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG);
      if (debugExecutor != null &&
          ProgramRunner.getRunner(ToolWindowId.DEBUG, configuration) != null) {
        return debugExecutor;
      }

      return null;
    }
  }

  private static class RunDashboardGroupViewDescriptor implements ServiceViewDescriptor, WeighedItem {
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

    @Nullable
    @Override
    public String getId() {
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

    @NotNull
    @Override
    public ItemPresentation getPresentation() {
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

    @Nullable
    @Override
    public Runnable getRemover() {
      ConfigurationType type = ObjectUtils.tryCast(((RunDashboardGroupImpl<?>)myGroup).getValue(), ConfigurationType.class);
      if (type != null) {
        return () -> {
          RunDashboardManager runDashboardManager = RunDashboardManager.getInstance(myNode.getProject());
          Set<String> types = new HashSet<>(runDashboardManager.getTypes());
          types.remove(type.getId());
          runDashboardManager.setTypes(types);
        };
      }
      return null;
    }

    @Override
    public @Nullable JComponent getContentComponent() {
      return ((RunDashboardManagerImpl)RunDashboardManager.getInstance(myNode.getProject())).getEmptyContent();
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
    public @Nullable DataProvider getDataProvider() {
      return TREE_EXPANDER_HIDE_PROVIDER;
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
        String groupName = myGroup.getName();
        Project project = ((FolderDashboardGroup)myGroup).getProject();
        List<RunDashboardManager.RunDashboardService> services = RunDashboardManager.getInstance(project).getRunConfigurations();

        final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
        runManager.fireBeginUpdate();
        try {
          for (RunDashboardManager.RunDashboardService service : services) {
            RunnerAndConfigurationSettings settings = service.getSettings();
            if (groupName.equals(settings.getFolderName())) {
              settings.setFolderName(null);
            }
          }
        }
        finally {
          runManager.fireEndUpdate();
        }
      };
    }

    @Override
    public boolean canDrop(@NotNull DnDEvent event, @NotNull ServiceViewDnDDescriptor.Position position) {
      return position == Position.INTO && getRunConfigurationNode(event, ((FolderDashboardGroup)myGroup).getProject()) != null;
    }

    @Override
    public void drop(@NotNull DnDEvent event, @NotNull ServiceViewDnDDescriptor.Position position) {
      Project project = ((FolderDashboardGroup)myGroup).getProject();
      RunDashboardRunConfigurationNode node = getRunConfigurationNode(event, project);
      if (node == null) return;

      RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
      runManager.fireBeginUpdate();
      try {
        node.getConfigurationSettings().setFolderName(myGroup.getName());
      }
      finally {
        runManager.fireEndUpdate();
      }
    }
  }

  private static class RunDashboardTypeGroupViewDescriptor extends RunDashboardGroupViewDescriptor {
    private final ConfigurationType myType;

    RunDashboardTypeGroupViewDescriptor(GroupingNode node, ConfigurationType type) {
      super(node);
      myType = type;
    }

    @Override
    public void onNodeSelected(List<Object> selectedServices) {
      ((RunDashboardManagerImpl)RunDashboardManager.getInstance(myNode.getProject())).getTypeContent().setType(myType);
    }

    @Override
    public void onNodeUnselected() {
      ((RunDashboardManagerImpl)RunDashboardManager.getInstance(myNode.getProject())).getTypeContent().setType(null);
    }

    @Override
    public @Nullable JComponent getContentComponent() {
      return ((RunDashboardManagerImpl)RunDashboardManager.getInstance(myNode.getProject())).getTypeContent();
    }

    @Override
    public @Nullable DataProvider getDataProvider() {
      return new DataProvider() {
        @Override
        public @Nullable Object getData(@NotNull String dataId) {
          if (PlatformDataKeys.TREE_EXPANDER.is(dataId)) {
            RunDashboardTypePanel typeContent =
              ((RunDashboardManagerImpl)RunDashboardManager.getInstance(myNode.getProject())).getTypeContent();
            return typeContent.getTreeExpander();
          }
          return TREE_EXPANDER_HIDE_PROVIDER.getData(dataId);
        }
      };
    }
  }

  static final class RunConfigurationContributor implements ServiceViewProvidingContributor<AbstractTreeNode<?>, RunConfigurationNode> {
    private final RunConfigurationNode myNode;

    RunConfigurationContributor(@NotNull RunConfigurationNode node) {
      myNode = node;
    }

    @NotNull
    @Override
    public RunConfigurationNode asService() {
      return myNode;
    }

    @NotNull
    @Override
    public ServiceViewDescriptor getViewDescriptor(@NotNull Project project) {
      return new RunConfigurationServiceViewDescriptor(myNode);
    }

    @NotNull
    @Override
    public List<AbstractTreeNode<?>> getServices(@NotNull Project project) {
      return new ArrayList<>(myNode.getChildren());
    }

    @NotNull
    @Override
    public ServiceViewDescriptor getServiceDescriptor(@NotNull Project project, @NotNull AbstractTreeNode service) {
      return new ServiceViewDescriptor() {
        @Override
        public ActionGroup getToolbarActions() {
          return RunDashboardServiceViewContributor.getToolbarActions(null);
        }

        @Override
        public ActionGroup getPopupActions() {
          return RunDashboardServiceViewContributor.getPopupActions();
        }

        @NotNull
        @Override
        public ItemPresentation getPresentation() {
          return service.getPresentation();
        }

        @Nullable
        @Override
        public String getId() {
          ItemPresentation presentation = getPresentation();
          String text = presentation.getPresentableText();
          if (!StringUtil.isEmpty(text)) {
            return text;
          }
          if (presentation instanceof PresentationData) {
            List<PresentableNodeDescriptor.ColoredFragment> fragments = ((PresentationData)presentation).getColoredText();
            if (!fragments.isEmpty()) {
              StringBuilder result = new StringBuilder();
              for (PresentableNodeDescriptor.ColoredFragment fragment : fragments) {
                result.append(fragment.getText());
              }
              return result.toString();
            }
          }
          return null;
        }

        @Nullable
        @Override
        public Runnable getRemover() {
          return service instanceof RunDashboardNode ? ((RunDashboardNode)service).getRemover() : null;
        }

        @Override
        public @Nullable JComponent getContentComponent() {
          return ((RunDashboardManagerImpl)RunDashboardManager.getInstance(myNode.getProject())).getEmptyContent();
        }

        @Override
        public @NotNull DataProvider getDataProvider() {
          return TREE_EXPANDER_HIDE_PROVIDER;
        }
      };
    }
  }

  private static final class RunDashboardContributorViewDescriptor extends SimpleServiceViewDescriptor
    implements ServiceViewToolWindowDescriptor {
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
    public DataProvider getDataProvider() {
      return id -> PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(id)
                   ? new RunDashboardServiceViewDeleteProvider()
                   : TREE_EXPANDER_HIDE_PROVIDER.getData(id);
    }

    @Override
    public @Nullable JComponent getContentComponent() {
      return ((RunDashboardManagerImpl)RunDashboardManager.getInstance(myProject)).getEmptyContent();
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
