// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.view;

import com.intellij.execution.Location;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.action.ExternalSystemActionUtil;
import com.intellij.openapi.externalSystem.action.ExternalSystemViewGearAction;
import com.intellij.openapi.externalSystem.model.*;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemTaskLocation;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemShortcutsManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.pom.Navigatable;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.mac.touchbar.Touchbar;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.List;
import java.util.*;

/**
 * @author Vladislav.Soroka
 */
public final class ExternalProjectsViewImpl extends SimpleToolWindowPanel implements ExternalProjectsView, Disposable {
  public static final Logger LOG = Logger.getInstance(ExternalProjectsViewImpl.class);

  private final @NotNull Project myProject;
  private final @NotNull ExternalProjectsManagerImpl myProjectsManager;
  private final @NotNull ToolWindowEx myToolWindow;
  private final @NotNull ProjectSystemId myExternalSystemId;
  private final @NotNull ExternalSystemUiAware myUiAware;
  private final @NotNull Set<Listener> listeners = new HashSet<>();

  private @Nullable ExternalProjectsStructure myStructure;
  private SimpleTree myTree;
  private final @NotNull NotificationGroup myNotificationGroup;
  private final List<ExternalSystemViewContributor> myViewContributors;

  private ExternalProjectsViewState myState = new ExternalProjectsViewState();

  public ExternalProjectsViewImpl(@NotNull Project project, @NotNull ToolWindowEx toolWindow, @NotNull ProjectSystemId externalSystemId) {
    super(true, true);
    myProject = project;
    myToolWindow = toolWindow;
    myExternalSystemId = externalSystemId;
    myUiAware = ExternalSystemUiUtil.getUiAware(externalSystemId);
    myProjectsManager = ExternalProjectsManagerImpl.getInstance(myProject);

    String toolWindowId = toolWindow.getId();
    String notificationId = "notification.group.id." + StringUtil.toLowerCase(externalSystemId.getId());
    NotificationGroup registeredGroup = NotificationGroup.findRegisteredGroup(notificationId);
    myNotificationGroup = registeredGroup != null ? registeredGroup : NotificationGroup.toolWindowGroup(notificationId, toolWindowId);

    Condition<ExternalSystemViewContributor> contributorPredicate = c -> {
      return ProjectSystemId.IDE.equals(c.getSystemId()) || myExternalSystemId.equals(c.getSystemId());
    };
    myViewContributors = new ArrayList<>(ContainerUtil.filter(ExternalSystemViewContributor.EP_NAME.getExtensions(), contributorPredicate));
    ExternalSystemViewContributor.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull ExternalSystemViewContributor extension, @NotNull PluginDescriptor pluginDescriptor) {
        if (contributorPredicate.value(extension)) {
          myViewContributors.add(extension);
        }
      }

      @Override
      public void extensionRemoved(@NotNull ExternalSystemViewContributor extension, @NotNull PluginDescriptor pluginDescriptor) {
        if (contributorPredicate.value(extension)) {
          myViewContributors.remove(extension);
        }
      }
    }, this);

    setName(myExternalSystemId.getReadableName() + " tool window");
    Touchbar.setActions(this, "ExternalSystem.RefreshAllProjects");
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(ExternalSystemDataKeys.VIEW, this);
    sink.set(PlatformCoreDataKeys.HELP_ID, "reference.toolwindows.gradle");
    sink.set(CommonDataKeys.PROJECT, myProject);
    sink.set(ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID, myExternalSystemId);
    sink.set(ExternalSystemDataKeys.UI_AWARE, myUiAware);
    sink.set(ExternalSystemDataKeys.PROJECTS_TREE, myTree);
    sink.set(ExternalSystemDataKeys.NOTIFICATION_GROUP, myNotificationGroup);

    //noinspection rawtypes
    List<ExternalSystemNode> selection = getSelectedNodes(ExternalSystemNode.class);
    ProjectNode projectNode = ObjectUtils.tryCast(ContainerUtil.getOnlyItem(selection), ProjectNode.class);

    sink.set(ExternalSystemDataKeys.SELECTED_NODES, selection);
    sink.set(ExternalSystemDataKeys.SELECTED_PROJECT_NODE, projectNode);

    sink.set(CommonDataKeys.VIRTUAL_FILE, selection.isEmpty() ? null : selection.get(0).getVirtualFile());
    sink.set(CommonDataKeys.VIRTUAL_FILE_ARRAY, VfsUtilCore.toVirtualFileArray(
      JBIterable.from(selection).filterMap(o -> o.getVirtualFile()).toList()));
    sink.set(CommonDataKeys.NAVIGATABLE_ARRAY, JBIterable.from(selection)
      .filterMap(o -> o.getNavigatable()).toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY));

    sink.lazy(Location.DATA_KEY, () -> extractLocation(selection));
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull ExternalSystemUiAware getUiAware() {
    return myUiAware;
  }

  @Override
  public ExternalSystemShortcutsManager getShortcutsManager() {
    return myProjectsManager.getShortcutsManager();
  }

  @Override
  public ExternalSystemTaskActivator getTaskActivator() {
    return myProjectsManager.getTaskActivator();
  }

  @Override
  public @NotNull ProjectSystemId getSystemId() {
    return myExternalSystemId;
  }

  public @NotNull NotificationGroup getNotificationGroup() {
    return myNotificationGroup;
  }

  public void init() {
    Disposer.register(myProject, this);
    initTree();

    MessageBusConnection busConnection = myProject.getMessageBus().connect(this);
    busConnection.subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      boolean wasVisible;

      @Override
      public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
        if (myToolWindow.isDisposed()) {
          return;
        }

        boolean visible = ((ToolWindowManagerEx)toolWindowManager).shouldUpdateToolWindowContent(myToolWindow);
        if (!visible || wasVisible) {
          wasVisible = visible;
          if (!visible) {
            scheduleStructureCleanupCache();
          }
          return;
        }

        scheduleStructureUpdate();
        wasVisible = true;
      }
    });

    getShortcutsManager().addListener(() -> scheduleTaskAndRunConfigUpdate(), this);

    getTaskActivator().addListener(() -> scheduleTaskAndRunConfigUpdate(), this);

    busConnection.subscribe(RunManagerListener.TOPIC, new RunManagerListener() {
      private void changed() {
        scheduleStructureRequest(() -> {
          assert myStructure != null;
          myStructure.visitExistingNodes(ModuleNode.class, node -> node.updateRunConfigurations());
        });
      }

      @Override
      public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
        changed();
      }

      @Override
      public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
        changed();
      }

      @Override
      public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
        changed();
      }
    });

    myToolWindow.setAdditionalGearActions(createAdditionalGearActionsGroup());

    scheduleStructureUpdate();
  }

  private void scheduleTaskAndRunConfigUpdate() {
    scheduleStructureRequest(() -> {
      assert myStructure != null;
      myStructure.updateNodesAsync(Arrays.asList(TaskNode.class, RunConfigurationNode.class));
    });
  }


  @Override
  public void handleDoubleClickOrEnter(@NotNull ExternalSystemNode node, @Nullable String actionId, InputEvent inputEvent) {
    if (actionId != null) {
      ExternalSystemActionUtil.executeAction(actionId, getName(), inputEvent);
    }
    for (Listener listener : listeners) {
      listener.onDoubleClickOrEnter(node, inputEvent);
    }
  }

  @Override
  public void addListener(@NotNull Listener listener) {
    listeners.add(listener);
  }

  private ActionGroup createAdditionalGearActionsGroup() {
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup group = new DefaultActionGroup();
    String[] ids = new String[]{"ExternalSystem.GroupModules", "ExternalSystem.GroupTasks", "ExternalSystem.ShowInheritedTasks", "ExternalSystem.ShowIgnored"};
    for (String id : ids) {
      final AnAction gearAction = actionManager.getAction(id);
      if (gearAction instanceof ExternalSystemViewGearAction) {
        ((ExternalSystemViewGearAction)gearAction).setView(this);
        group.add(gearAction);
        Disposer.register(this, () -> ((ExternalSystemViewGearAction)gearAction).setView(null));
      }
    }
    return group;
  }

  @ApiStatus.Internal
  public void initStructure() {
    myStructure = new ExternalProjectsStructure(myProject, myTree);
    Disposer.register(this, myStructure);
    myStructure.init(this);
  }

  private void initTree() {
    myTree = new ExternalProjectTree(myProject);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);

    final ActionManager actionManager = ActionManager.getInstance();
    ActionToolbar actionToolbar = actionManager.createActionToolbar(myExternalSystemId.getReadableName() + " View Toolbar",
                                                                    (DefaultActionGroup)actionManager
                                                                      .getAction("ExternalSystemView.ActionsToolbar"), true);
    // make the view data context available for the toolbar actions
    actionToolbar.setTargetComponent(this);
    setToolbar(actionToolbar.getComponent());
    setContent(ScrollPaneFactory.createScrollPane(myTree));

    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(final Component comp, final int x, final int y) {
        final String id = getMenuId(getSelectedNodes(ExternalSystemNode.class));
        if (id != null) {
          final ActionGroup actionGroup = (ActionGroup)actionManager.getAction(id);
          if (actionGroup != null) {
            actionManager.createActionPopupMenu(ExternalProjectsViewImpl.this.getName(), actionGroup).getComponent().show(comp, x, y);
          }
        }
      }

      private static @Nullable String getMenuId(Collection<? extends ExternalSystemNode> nodes) {
        String id = null;
        for (ExternalSystemNode node : nodes) {
          String menuId = node.getMenuId();
          if (menuId == null) {
            return null;
          }
          if (id == null) {
            id = menuId;
          }
          else if (!id.equals(menuId)) {
            return null;
          }
        }
        return id;
      }
    });
  }

  public void scheduleStructureUpdate() {
    scheduleStructureRequest(() -> {
      final Collection<ExternalProjectInfo> projectsData =
        ProjectDataManager.getInstance().getExternalProjectsData(myProject, myExternalSystemId);

      final List<DataNode<ProjectData>> toImport =
        ContainerUtil.mapNotNull(projectsData, info -> info.getExternalProjectStructure());

      assert myStructure != null;
      myStructure.updateProjects(toImport);
    });
  }

  private void scheduleStructureCleanupCache() {
    scheduleStructureRequest(() -> {
      if (myStructure != null) {
        myStructure.cleanupCache();
      }
    });
  }

  private static void invokeLater(Project p, Runnable r) {
    invokeLater(p, ModalityState.defaultModalityState(), r);
  }

  private static void invokeLater(final Project p, final ModalityState state, final Runnable r) {
    if (isNoBackgroundMode()) {
      r.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(r, state, p.getDisposed());
    }
  }

  public static boolean isNoBackgroundMode() {
    return ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment();
  }

  @Override
  public void updateUpTo(ExternalSystemNode node) {
    ExternalProjectsStructure structure = getStructure();
    if (structure != null) {
      structure.updateUpTo(node);
    }
  }

  @Override
  public @Nullable ExternalProjectsStructure getStructure() {
    return myStructure;
  }

  @Override
  public @NotNull List<ExternalSystemNode<?>> createNodes(@NotNull ExternalProjectsView externalProjectsView,
                                                          @Nullable ExternalSystemNode<?> parent,
                                                          @NotNull DataNode<?> dataNode) {
    final List<ExternalSystemNode<?>> result = new SmartList<>();
    final MultiMap<Key<?>, DataNode<?>> groups = ExternalSystemApiUtil.group(dataNode.getChildren());
    for (ExternalSystemViewContributor contributor : myViewContributors) {
      final MultiMap<Key<?>, DataNode<?>> dataNodes = new ContainerUtil.KeyOrderedMultiMap<>();
      for (Key<?> key : contributor.getKeys()) {
        ContainerUtil.putIfNotNull(key, groups.get(key), dataNodes);
      }

      if (dataNodes.isEmpty()) {
        continue;
      }

      List<ExternalSystemNode<?>> childNodes = contributor.createNodes(externalProjectsView, dataNodes);
      result.addAll(childNodes);

      if (parent == null) continue;

      for (ExternalSystemNode<?> childNode : childNodes) {
        childNode.setParent(parent);
      }
    }

    return result;
  }

  @Override
  public ExternalProjectsStructure.ErrorLevel getErrorLevelRecursively(@NotNull DataNode node) {
    Ref<ExternalProjectsStructure.ErrorLevel> ref = new Ref<>(ExternalProjectsStructure.ErrorLevel.NONE);
    ExternalSystemApiUtil.visit(node, currentNode -> {
      for (ExternalSystemViewContributor contributor : myViewContributors) {
        ExternalProjectsStructure.ErrorLevel errorLevel = contributor.getErrorLevel(currentNode);
        if (ref.get() == null || errorLevel.compareTo(ref.get()) > 0) {
          ref.set(errorLevel);
        }
      }
    });
    return ref.get();
  }

  @RequiresReadLock
  public @Nullable ExternalProjectsViewState getState() {
    if (myStructure != null) {
      try {
        myState.treeState = new Element("root");
        TreeState.createOn(myTree).writeExternal(myState.treeState);
      }
      catch (WriteExternalException e) {
        LOG.warn(e);
      }
    }
    return myState;
  }

  public void loadState(ExternalProjectsViewState state) {
    myState = state;
  }

  @Override
  public boolean getShowIgnored() {
    return myState.showIgnored;
  }

  public void setShowIgnored(boolean value) {
    if (myState.showIgnored != value) {
      myState.showIgnored = value;
      scheduleStructureUpdate();
    }
  }

  @Override
  public boolean getGroupTasks() {
    return myState.groupTasks;
  }

  @Override
  public boolean getGroupModules() {
    return myState.groupModules;
  }

  @Override
  public boolean useTasksNode() {
    return true;
  }

  public void setGroupTasks(boolean value) {
    if (myState.groupTasks != value) {
      myState.groupTasks = value;
      scheduleNodesRebuild(TasksNode.class);
    }
  }

  public void setGroupModules(boolean value) {
    if (myState.groupModules != value) {
      myState.groupModules = value;
      scheduleNodesRebuild(ModuleNode.class);
      scheduleNodesRebuild(ProjectNode.class);
    }
  }

  @Override
  public boolean showInheritedTasks() {
    return myState.showInheritedTasks;
  }

  public void setShowInheritedTasks(boolean value) {
    if (myState.showInheritedTasks != value) {
      myState.showInheritedTasks = value;
      scheduleStructureUpdate();
    }
  }

  @Override
  public @Nullable String getDisplayName(@Nullable DataNode node) {
    if (node == null) return null;
    for (ExternalSystemViewContributor contributor : myViewContributors) {
      String name = contributor.getDisplayName(node);
      if (name != null) {
        return name;
      }
    }
    return null;
  }

  private <T extends ExternalSystemNode> void scheduleNodesRebuild(@NotNull Class<T> nodeClass) {
    scheduleStructureRequest(() -> {
      assert myStructure != null;
      for (T tasksNode : myStructure.getNodes(nodeClass)) {
        tasksNode.cleanUpCache();
      }
      myStructure.updateNodesAsync(Collections.singleton(nodeClass));
    });
  }

  private void scheduleStructureRequest(final Runnable r) {
    invokeLater(myProject, () -> {
      if (!ToolWindowManagerEx.getInstanceEx(myProject).shouldUpdateToolWindowContent(myToolWindow)) return;

      boolean shouldCreate = myStructure == null;
      if (shouldCreate) {
        initStructure();
      }

      myTree.setPaintBusy(true);
      try {
        r.run();
        if (shouldCreate) {
          restoreTreeState();
        }
      }
      finally {
        myTree.setPaintBusy(false);
      }
    });
  }

  private void restoreTreeState() {
    TreeState.createFrom(myState.treeState).applyTo(myTree);
  }

  private <T extends ExternalSystemNode> List<T> getSelectedNodes(Class<T> aClass) {
    return myStructure != null ? myStructure.getSelectedNodes(myTree, aClass) : ContainerUtil.emptyList();
  }

  private @Nullable ExternalSystemTaskLocation extractLocation(List<ExternalSystemNode> selectedNodes) {
    if (selectedNodes.isEmpty()) return null;

    List<TaskData> tasks = new SmartList<>();

    ExternalTaskExecutionInfo taskExecutionInfo = new ExternalTaskExecutionInfo();

    String projectPath = null;

    for (ExternalSystemNode node : selectedNodes) {
      final Object data = node.getData();
      if (data instanceof TaskData taskData) {
        if (projectPath == null) {
          projectPath = taskData.getLinkedExternalProjectPath();
        }
        else if (!taskData.getLinkedExternalProjectPath().equals(projectPath)) {
          return null;
        }

        taskExecutionInfo.getSettings().getTaskNames().add(taskData.getName());
        taskExecutionInfo.getSettings().getTaskDescriptions().add(taskData.getDescription());
        tasks.add(taskData);
      }
    }

    if (tasks.isEmpty()) return null;

    taskExecutionInfo.getSettings().setExternalSystemIdString(myExternalSystemId.toString());
    taskExecutionInfo.getSettings().setExternalProjectPath(projectPath);

    return ExternalSystemTaskLocation.create(myProject, myExternalSystemId, projectPath, taskExecutionInfo);
  }

  @Override
  public void dispose() {
    this.listeners.clear();
    this.myViewContributors.clear();
    this.myStructure = null;
    this.myTree = null;
  }
}