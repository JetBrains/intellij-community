// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.task.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl.ExternalProjectsStateProvider;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator.Phase;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator.TaskActivationEntry;
import com.intellij.openapi.externalSystem.service.project.manage.TaskActivationState;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.SwingHelper;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.*;

public final class ConfigureTasksActivationDialog extends DialogWrapper {
  @NotNull private final Project myProject;
  @NotNull private final ExternalSystemTaskActivator myTaskActivator;
  @NotNull ProjectSystemId myProjectSystemId;
  private JPanel contentPane;

  private JPanel tasksPanel;
  @SuppressWarnings("unused")
  private JPanel projectFieldPanel;
  private SimpleTree myTree;
  private StructureTreeModel<SimpleTreeStructure.Impl> myTreeModel;
  private ComboBox projectCombobox;
  @NotNull
  private final ExternalSystemUiAware uiAware;
  private RootNode myRootNode;

  public ConfigureTasksActivationDialog(@NotNull Project project, @NotNull ProjectSystemId externalSystemId, @NotNull String projectPath) {
    super(project, true);
    myProject = project;
    myProjectSystemId = externalSystemId;
    uiAware = ExternalSystemUiUtil.getUiAware(myProjectSystemId);
    setUpDialog(projectPath);
    setModal(true);
    setTitle(ExternalSystemBundle.message("external.system.task.activation.title", externalSystemId.getReadableName()));
    init();
    myTaskActivator = ExternalProjectsManagerImpl.getInstance(myProject).getTaskActivator();
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction()};
  }

  private void setUpDialog(@NotNull String projectPath) {
    final AbstractExternalSystemSettings externalSystemSettings = ExternalSystemApiUtil.getSettings(myProject, myProjectSystemId);
    //noinspection unchecked
    Collection<ExternalProjectSettings> projectsSettings = externalSystemSettings.getLinkedProjectsSettings();
    List<ProjectItem> projects = ContainerUtil.map(projectsSettings,
                                                   settings -> new ProjectItem(uiAware.getProjectRepresentationName(settings.getExternalProjectPath(), null), settings));

    myTree = new SimpleTree();
    myRootNode = new RootNode();
    myTreeModel = createModel(myRootNode, myTree);
    final ExternalProjectSettings currentProjectSettings = externalSystemSettings.getLinkedProjectSettings(projectPath);
    if (currentProjectSettings != null) {
      SwingHelper.updateItems(projectCombobox, projects,
                              new ProjectItem(uiAware.getProjectRepresentationName(projectPath, null), currentProjectSettings));
    }
    projectCombobox.addActionListener(e -> updateTree(myRootNode));
  }

  private StructureTreeModel<SimpleTreeStructure.Impl> createModel(@NotNull SimpleNode root, @NotNull Tree tree) {
    tree.setRootVisible(false);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    StructureTreeModel<SimpleTreeStructure.Impl> model = new StructureTreeModel<>(new SimpleTreeStructure.Impl(root), getDisposable());
    tree.setModel(new AsyncTreeModel(model, getDisposable()));
    return model;
  }

  @Override
  protected JComponent createCenterPanel() {
    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTree).
      setAddAction(button -> {
        ProjectItem projectItem = (ProjectItem)projectCombobox.getSelectedItem();
        if(projectItem == null) return;

        final ExternalProjectInfo projectData = ProjectDataManager.getInstance()
          .getExternalProjectData(myProject, myProjectSystemId, projectItem.myProjectSettings.getExternalProjectPath());

        if (projectData == null || projectData.getExternalProjectStructure() == null) return;

        final List<ProjectPopupItem> popupItems = new ArrayList<>();
        for (DataNode<ModuleData> moduleDataNode : ExternalSystemApiUtil
          .findAllRecursively(projectData.getExternalProjectStructure(), ProjectKeys.MODULE)) {
          if(moduleDataNode.isIgnored()) continue;

          final List<String> tasks = ContainerUtil.map(
            ExternalSystemApiUtil.findAll(moduleDataNode, ProjectKeys.TASK), node -> node.getData().getName());
          if (!tasks.isEmpty()) {
            popupItems.add(new ProjectPopupItem(moduleDataNode.getData(), tasks));
          }
        }

        final ChooseProjectStep projectStep = new ChooseProjectStep(popupItems);
        final List<ProjectPopupItem> projectItems = projectStep.getValues();
        ListPopupStep step = projectItems.size() == 1 ? (ListPopupStep)projectStep.onChosen(projectItems.get(0), false) : projectStep;
        assert step != null;
        JBPopupFactory.getInstance().createListPopup(step).show(
          ObjectUtils.notNull(button.getPreferredPopupPoint(), RelativePoint.getSouthEastOf(projectCombobox)));
      }).
      setRemoveAction(button -> {
        List<TaskActivationEntry> tasks = findSelectedTasks();
        myTaskActivator.removeTasks(tasks);
        updateTree(null);
      }).
      setMoveUpAction(button -> moveAction(-1)).
      setMoveUpActionUpdater(e -> isMoveActionEnabled(-1)).
      setMoveDownAction(button -> moveAction(+1)).
      setMoveDownActionUpdater(e -> isMoveActionEnabled(+1)).
      setToolbarPosition(ActionToolbarPosition.RIGHT);
    tasksPanel.add(decorator.createPanel());
    return contentPane;
  }

  private boolean isMoveActionEnabled(int increment) {
    final DefaultMutableTreeNode[] selectedNodes = myTree.getSelectedNodes(DefaultMutableTreeNode.class, null);
    if (selectedNodes.length == 0) return false;

    boolean enabled = true;
    for (DefaultMutableTreeNode node : selectedNodes) {
      final DefaultMutableTreeNode sibling = increment == -1 ? node.getPreviousSibling() : node.getNextSibling();
      enabled = enabled && (node.getUserObject() instanceof TaskNode) && sibling != null;
    }
    if (!enabled) {
      enabled = true;
      for (DefaultMutableTreeNode node : selectedNodes) {
        final DefaultMutableTreeNode sibling = increment == -1 ? node.getPreviousSibling() : node.getNextSibling();
        enabled = enabled && (node.getUserObject() instanceof ProjectNode) && sibling != null;
      }
    }
    return enabled;
  }

  private void moveAction(int increment) {
    List<TaskActivationEntry> tasks = findSelectedTasks();
    if (!tasks.isEmpty()) {
      myTaskActivator.moveTasks(tasks, increment);
    }
    else {
      List<String> projectsPaths = findSelectedProjects();
      if (projectsPaths.isEmpty()) return;
      ProjectItem item = (ProjectItem)projectCombobox.getSelectedItem();
      myTaskActivator.moveProjects(myProjectSystemId, projectsPaths, item.myProjectSettings.getModules(), increment);
    }
    moveSelectedRows(myTree, increment);
  }

  private static void moveSelectedRows(@NotNull final SimpleTree tree, final int direction) {
    final TreePath[] selectionPaths = tree.getSelectionPaths();
    if (selectionPaths == null) return;

    ContainerUtil.sort(selectionPaths, new Comparator<>() {
      @Override
      public int compare(TreePath o1, TreePath o2) {
        return -direction * compare(tree.getRowForPath(o1), tree.getRowForPath(o2));
      }

      private int compare(int x, int y) {
        return Integer.compare(x, y);
      }
    });

    for (TreePath selectionPath : selectionPaths) {
      final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
      final DefaultMutableTreeNode parent = (DefaultMutableTreeNode)treeNode.getParent();
      final int idx = parent.getIndex(treeNode);
      ((DefaultTreeModel)tree.getModel()).removeNodeFromParent(treeNode);
      ((DefaultTreeModel)tree.getModel()).insertNodeInto(treeNode, parent, idx + direction);
    }

    tree.addSelectionPaths(selectionPaths);
  }

  @NotNull
  private List<TaskActivationEntry> findSelectedTasks() {
    List<TaskActivationEntry> tasks = new SmartList<>();
    for (DefaultMutableTreeNode node : myTree.getSelectedNodes(DefaultMutableTreeNode.class, null)) {
      tasks.addAll(findTasksUnder(ContainerUtil.ar((MyNode)node.getUserObject())));
    }
    return tasks;
  }

  @NotNull
  private List<TaskActivationEntry> findTasksUnder(SimpleNode @NotNull [] nodes) {
    List<TaskActivationEntry> tasks = new SmartList<>();
    for (SimpleNode node : nodes) {
      if (node instanceof TaskNode taskNode) {
        final String taskName = taskNode.getName();
        final PhaseNode phaseNode = (PhaseNode)taskNode.getParent();
        tasks.add(new TaskActivationEntry(myProjectSystemId, phaseNode.myPhase, phaseNode.myProjectPath, taskName));
      }
      else {
        tasks.addAll(findTasksUnder(node.getChildren()));
      }
    }
    return tasks;
  }

  private List<String> findSelectedProjects() {
    List<String> tasks = new ArrayList<>();
    for (DefaultMutableTreeNode node : myTree.getSelectedNodes(DefaultMutableTreeNode.class, null)) {
      if (node.getUserObject() instanceof ProjectNode projectNode) {
        tasks.add(projectNode.myProjectPath);
      }
    }
    return tasks;
  }

  private MyNode[] buildProjectsNodes(final ExternalProjectSettings projectSettings,
                                      final ExternalProjectsStateProvider stateProvider,
                                      final RootNode parent) {
    List<String> paths = new ArrayList<>(stateProvider.getProjectsTasksActivationMap(myProjectSystemId).keySet());
    paths.retainAll(projectSettings.getModules());

    return ContainerUtil.mapNotNull(ArrayUtilRt.toStringArray(paths), path -> {
      final MyNode node = new ProjectNode(parent, stateProvider, projectSettings.getExternalProjectPath(), path);
      return node.getChildren().length > 0 ? node : null;
    }, new MyNode[]{});
  }

  private MyNode[] buildProjectPhasesNodes(final String projectPath,
                                           final TaskActivationState tasksActivation,
                                           final MyNode parent) {
    return ContainerUtil.mapNotNull(Phase.values(), phase -> tasksActivation.getTasks(phase).isEmpty() ? null : new PhaseNode(projectPath, phase, tasksActivation, parent), new MyNode[]{});
  }

  private static class ProjectItem {
    private static final int MAX_LENGTH = 80;

    @NotNull String projectName;
    @NotNull ExternalProjectSettings myProjectSettings;

    ProjectItem(@NotNull String projectName, @NotNull ExternalProjectSettings projectPath) {
      this.projectName = projectName;
      this.myProjectSettings = projectPath;
    }

    @Override
    public String toString() {
      return projectName + " (" + truncate(myProjectSettings.getExternalProjectPath()) + ")";
    }

    @NotNull
    private static String truncate(@NotNull String s) {
      return s.length() < MAX_LENGTH ? s : s.substring(0, MAX_LENGTH / 2) + "..." + s.substring(s.length() - MAX_LENGTH / 2 - 3);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ProjectItem item)) return false;
      if (!myProjectSettings.equals(item.myProjectSettings)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return myProjectSettings.hashCode();
    }
  }

  private void updateTree(@Nullable CachingSimpleNode nodeToUpdate) {
    Set<CachingSimpleNode> toUpdate = new ReferenceOpenHashSet<>();
    if (nodeToUpdate == null) {
      for (DefaultMutableTreeNode node : myTree.getSelectedNodes(DefaultMutableTreeNode.class, null)) {
        final Object userObject = node.getUserObject();
        if (userObject instanceof SimpleNode && ((SimpleNode)userObject).getParent() instanceof CachingSimpleNode) {
          toUpdate.add((CachingSimpleNode)((SimpleNode)userObject).getParent());
        }
      }
    }
    else {
      toUpdate.add(nodeToUpdate);
    }

    if (toUpdate.isEmpty()) {
      toUpdate.add(myRootNode);
    }

    Element treeStateElement = new Element("root");
    try {
      TreeState.createOn(myTree).writeExternal(treeStateElement);
    }
    catch (WriteExternalException ignore) {
    }

    for (CachingSimpleNode node : toUpdate) {
      cleanUpEmptyNodes(node);
    }

    TreeState.createFrom(treeStateElement).applyTo(myTree);
  }

  private void cleanUpEmptyNodes(@NotNull CachingSimpleNode node) {
    node.cleanUpCache();
    myTreeModel.invalidateAsync(node, true);
    if (node.getChildren().length == 0) {
      if (node.getParent() instanceof CachingSimpleNode) {
        cleanUpEmptyNodes((CachingSimpleNode)node.getParent());
      }
    }
  }

  private static class ProjectPopupItem {
    ModuleData myModuleData;
    List<String> myTasks;

    ProjectPopupItem(ModuleData moduleData, List<String> tasks) {
      myModuleData = moduleData;
      myTasks = tasks;
    }

    @Override
    public String toString() {
      return myModuleData.getId();
    }
  }

  private class ChooseProjectStep extends BaseListPopupStep<ProjectPopupItem> {
    protected ChooseProjectStep(List<? extends ProjectPopupItem> values) {
      super(ExternalSystemBundle.message("popup.title.choose.project"), values);
    }

    @Override
    public PopupStep onChosen(final ProjectPopupItem projectPopupItem, final boolean finalChoice) {
      return new BaseListPopupStep<>(ExternalSystemBundle.message("popup.title.choose.activation.phase"), Phase.values()) {
        @Override
        public PopupStep onChosen(final Phase selectedPhase, boolean finalChoice) {
          final Map<String, TaskActivationState> activationMap =
            ExternalProjectsManagerImpl.getInstance(myProject).getStateProvider().getProjectsTasksActivationMap(myProjectSystemId);
          final String projectPath = projectPopupItem.myModuleData.getLinkedExternalProjectPath();
          final List<String> tasks = activationMap.get(projectPath).getTasks(selectedPhase);

          final List<String> tasksToSuggest = new ArrayList<>(projectPopupItem.myTasks);
          tasksToSuggest.removeAll(tasks);
          return new BaseListPopupStep<>(ExternalSystemBundle.message("popup.title.choose.task"), tasksToSuggest) {
            @Override
            public PopupStep onChosen(final String taskName, boolean finalChoice) {
              return doFinalStep(() -> {
                myTaskActivator.addTask(new TaskActivationEntry(myProjectSystemId, selectedPhase, projectPath, taskName));
                updateTree(myRootNode);
              });
            }
          };
        }

        @Override
        public boolean hasSubstep(Phase phase) {
          return true;
        }
      };
    }

    @Override
    public boolean hasSubstep(ProjectPopupItem selectedValue) {
      return true;
    }
  }

  private abstract static class MyNode extends CachingSimpleNode {
    protected MyNode(SimpleNode aParent) {
      super(aParent);
    }

    MyNode(Project aProject, @Nullable NodeDescriptor aParentDescriptor) {
      super(aProject, aParentDescriptor);
    }
  }

  private class RootNode extends MyNode {
    private final ExternalProjectsStateProvider myStateProvider;

    RootNode() {
      super(ConfigureTasksActivationDialog.this.myProject, null);
      myStateProvider = ExternalProjectsManagerImpl.getInstance(ConfigureTasksActivationDialog.this.myProject).getStateProvider();
    }

    @Override
    public boolean isAutoExpandNode() {
      return true;
    }

    @Override
    protected MyNode[] buildChildren() {
      ProjectItem item = (ProjectItem)projectCombobox.getSelectedItem();
      if(item == null) return new MyNode[]{};
      if (item.myProjectSettings.getModules().isEmpty() || item.myProjectSettings.getModules().size() == 1) {
        final TaskActivationState tasksActivation =
          myStateProvider.getTasksActivation(myProjectSystemId, item.myProjectSettings.getExternalProjectPath());
        return buildProjectPhasesNodes(item.myProjectSettings.getExternalProjectPath(), tasksActivation, this);
      }
      else {
        return buildProjectsNodes(item.myProjectSettings, myStateProvider, this);
      }
    }
  }

  private class ProjectNode extends MyNode {
    private final ExternalProjectsStateProvider myStateProvider;
    private final String myRootProjectPath;
    private final String myProjectPath;
    private final String myProjectName;

    ProjectNode(RootNode parent,
                       ExternalProjectsStateProvider stateProvider,
                       String rootProjectPath,
                       String projectPath) {
      super(parent);
      myStateProvider = stateProvider;
      myProjectPath = projectPath;
      myRootProjectPath = rootProjectPath;
      myProjectName = uiAware.getProjectRepresentationName(myProjectPath, myRootProjectPath);
    }

    @Override
    protected void update(@NotNull PresentationData presentation) {
      super.update(presentation);
      presentation.setIcon(AllIcons.Nodes.ConfigFolder);
    }

    @Override
    public String getName() {
      return myProjectName;
    }

    @Override
    protected MyNode[] buildChildren() {
      final TaskActivationState tasksActivation = myStateProvider.getTasksActivation(myProjectSystemId, myProjectPath);
      return buildProjectPhasesNodes(myProjectPath, tasksActivation, this);
    }
  }

  private class PhaseNode extends MyNode {
    private final Phase myPhase;
    private final TaskActivationState myTaskActivationState;
    private final String myProjectPath;

    PhaseNode(final String projectPath, Phase phase, TaskActivationState taskActivationState, SimpleNode parent) {
      super(parent);
      myPhase = phase;
      myTaskActivationState = taskActivationState;
      myProjectPath = projectPath;
    }

    @Override
    protected void update(@NotNull PresentationData presentation) {
      super.update(presentation);
      presentation.setIcon(AllIcons.Nodes.ConfigFolder);
    }

    @Override
    public boolean isAutoExpandNode() {
      return true;
    }

    @Override
    public MyNode[] buildChildren() {
      return ContainerUtil.map2Array(myTaskActivationState.getTasks(myPhase), MyNode.class, taskName -> new TaskNode(taskName, this));
    }

    @Override
    public String getName() {
      return myPhase.toString();
    }
  }

  private class TaskNode extends MyNode {
    private final String myTaskName;

    TaskNode(String taskName, PhaseNode parent) {
      super(parent);
      myTaskName = taskName;
    }

    @Override
    protected void update(@NotNull PresentationData presentation) {
      super.update(presentation);
      presentation.setIcon(uiAware.getTaskIcon());
    }

    @Override
    public MyNode[] buildChildren() {
      return new MyNode[0];
    }

    @Override
    public String getName() {
      return myTaskName;
    }

    @Override
    public boolean isAlwaysLeaf() {
      return true;
    }
  }
}