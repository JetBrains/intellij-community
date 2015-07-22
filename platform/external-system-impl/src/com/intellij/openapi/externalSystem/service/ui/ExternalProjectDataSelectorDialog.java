/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.service.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.importing.ExternalProjectStructureCustomizer;
import com.intellij.openapi.externalSystem.importing.ExternalProjectStructureCustomizerImpl;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.CachedValueImpl;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 5/12/2015
 */
public class ExternalProjectDataSelectorDialog extends DialogWrapper {

  private static final int MAX_PATH_LENGTH = 50;
  private static final Set<? extends Key<?>> DATA_KEYS = ContainerUtil.set(ProjectKeys.PROJECT, ProjectKeys.MODULE);
  private static final com.intellij.openapi.util.Key<DataNode> MODIFIED_NODE_KEY = com.intellij.openapi.util.Key.create("modifiedData");
  private static final com.intellij.openapi.util.Key<DataNodeCheckedTreeNode> CONNECTED_UI_NODE_KEY =
    com.intellij.openapi.util.Key.create("connectedUiNode");
  @NotNull
  private Project myProject;
  private volatile boolean myDisposed = false;
  private JBLoadingPanel loadingPanel;
  private JPanel mainPanel;
  private JPanel contentPanel;
  @SuppressWarnings("unused")
  private JBLabel myDescriptionLbl;
  private JBLabel mySelectionStatusLbl;
  private ExternalSystemUiAware myExternalSystemUiAware;
  private ExternalProjectInfo myProjectInfo;
  private final Set<Key<?>> myIgnorableKeys;
  private final Set<Key<?>> myPublicKeys;
  @Nullable
  private final Object myPreselectedNodeObject;
  private CheckboxTree myTree;
  @SuppressWarnings("unchecked")
  private final MultiMap<DataNode<ModuleData>, DataNode<ModuleData>> dependentNodeMap = MultiMap.create(TObjectHashingStrategy.IDENTITY);

  private final SimpleModificationTracker myModificationTracker = new SimpleModificationTracker();
  private final CachedValue<SelectionState> selectionState = new CachedValueImpl<SelectionState>(new CachedValueProvider<SelectionState>() {
    @Nullable
    @Override
    public Result<SelectionState> compute() {
      return Result.createSingleDependency(getSelectionStatus(), myModificationTracker);
    }
  });

  private boolean myShowSelectedRowsOnly;
  private int myModulesCount;

  public ExternalProjectDataSelectorDialog(@NotNull Project project,
                                           @NotNull ExternalProjectInfo projectInfo) {
    this(project, projectInfo, null);
  }

  public ExternalProjectDataSelectorDialog(@NotNull Project project,
                                           @NotNull ExternalProjectInfo projectInfo,
                                           @Nullable Object preselectedNodeDataObject) {
    super(project, true);
    myProject = project;
    myIgnorableKeys = getIgnorableKeys();
    myPublicKeys = getPublicKeys();
    myPreselectedNodeObject = preselectedNodeDataObject;
    init(projectInfo);
  }

  private void init(@NotNull ExternalProjectInfo projectInfo) {
    myProjectInfo = projectInfo;
    myExternalSystemUiAware = ExternalSystemUiUtil.getUiAware(myProjectInfo.getProjectSystemId());
    myTree = createTree();
    updateSelectionState();

    myTree.addCheckboxTreeListener(new CheckboxTreeAdapter() {
      @Override
      public void nodeStateChanged(@NotNull CheckedTreeNode node) {
        updateSelectionState();
      }
    });

    String externalSystemName = myProjectInfo.getProjectSystemId().getReadableName();
    setTitle(String.format("%s Project Data To Import", externalSystemName));
    init();
  }

  private void updateSelectionState() {
    myModificationTracker.incModificationCount();
    mySelectionStatusLbl.setText(selectionState.getValue().message);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTree).
      addExtraAction(new SelectAllButton()).
      addExtraAction(new UnselectAllButton()).
      addExtraAction(new ShowSelectedOnlyButton()).
      addExtraAction(new SelectRequiredButton()).
      setToolbarPosition(ActionToolbarPosition.BOTTOM).
      setToolbarBorder(IdeBorderFactory.createEmptyBorder());

    contentPanel.add(decorator.createPanel());
    loadingPanel = new JBLoadingPanel(new BorderLayout(), getDisposable());
    loadingPanel.add(mainPanel, BorderLayout.CENTER);
    return loadingPanel;
  }

  private void reloadTree() {
    final DefaultTreeModel treeModel = (DefaultTreeModel)myTree.getModel();
    final Object root = treeModel.getRoot();
    if (!(root instanceof CheckedTreeNode)) return;

    final CheckedTreeNode rootNode = (CheckedTreeNode)root;

    final Couple<CheckedTreeNode> rootAndPreselectedNode = createRoot();
    final CheckedTreeNode rootCopy = rootAndPreselectedNode.first;

    List<TreeNode> nodes = TreeUtil.childrenToArray(rootCopy);
    rootNode.removeAllChildren();
    TreeUtil.addChildrenTo(rootNode, nodes);
    treeModel.reload();
  }

  @Override
  protected void doOKAction() {
    loadingPanel.setLoadingText("Please wait...");
    loadingPanel.startLoading();

    final DataNode<ProjectData> projectStructure = myProjectInfo.getExternalProjectStructure();
    if (projectStructure != null) {
      final boolean[] isModified = {false};
      ExternalSystemApiUtil.visit(projectStructure, new Consumer<DataNode<?>>() {
        @Override
        public void consume(DataNode<?> node) {
          final DataNode modifiedDataNode = node.getUserData(MODIFIED_NODE_KEY);
          if (modifiedDataNode != null) {
            if (node.isIgnored() != modifiedDataNode.isIgnored()) {
              node.setIgnored(modifiedDataNode.isIgnored());
              isModified[0] = true;
            }
            node.removeUserData(MODIFIED_NODE_KEY);
            node.removeUserData(CONNECTED_UI_NODE_KEY);
          }
        }
      });
      if (isModified[0]) {
        DataNode<?> notIgnoredNode = ContainerUtil.find(projectStructure.getChildren(), new Condition<DataNode<?>>() {
          @Override
          public boolean value(DataNode<?> node) {
            return !node.isIgnored();
          }
        });
        projectStructure.setIgnored(notIgnoredNode == null);
        ExternalSystemApiUtil.executeProjectChangeAction(true, new DisposeAwareProjectChange(myProject) {
          @Override
          public void execute() {
            ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(new Runnable() {
              @Override
              public void run() {
                ServiceManager.getService(ProjectDataManager.class).importData(projectStructure, myProject, true);
              }
            });
          }
        });
      }
    }

    super.doOKAction();
  }

  @Override
  public void doCancelAction() {
    ExternalSystemApiUtil.visit(myProjectInfo.getExternalProjectStructure(), new Consumer<DataNode<?>>() {
      @Override
      public void consume(DataNode<?> node) {
        node.removeUserData(MODIFIED_NODE_KEY);
        node.removeUserData(CONNECTED_UI_NODE_KEY);
      }
    });

    super.doCancelAction();
  }

  @Override
  public void dispose() {
    super.dispose();
    myDisposed = true;
  }

  private CheckboxTree createTree() {
    final Couple<CheckedTreeNode> rootAndPreselectedNode = createRoot();
    final CheckedTreeNode root = rootAndPreselectedNode.first;

    final CheckboxTree tree = new CheckboxTree(new CheckboxTree.CheckboxTreeCellRenderer(true, false) {

      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (!(value instanceof DataNodeCheckedTreeNode)) {
          return;
        }
        final DataNodeCheckedTreeNode node = (DataNodeCheckedTreeNode)value;
        ColoredTreeCellRenderer renderer = getTextRenderer();

        renderer.setIcon(node.icon);
        renderer.append(node.text);

        if (!StringUtil.isEmptyOrSpaces(node.comment)) {
          String description = node.comment;
          if (node.comment.length() > MAX_PATH_LENGTH) {
            description = node.comment.substring(0, MAX_PATH_LENGTH) + "...";
          }

          renderer.append(" (" + description + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
          setToolTipText(node.comment);
        }
        else {
          setToolTipText(null);
        }
      }
    }, root, new CheckboxTreeBase.CheckPolicy(true, true, false, false));

    TreeUtil.expand(tree, 1);
    if (rootAndPreselectedNode.second != null) {
      TreeUtil.selectNode(tree, rootAndPreselectedNode.second);
    }
    else {
      tree.setSelectionRow(0);
    }
    return tree;
  }

  private Couple<CheckedTreeNode> createRoot() {
    final Map<DataNode, DataNodeCheckedTreeNode> treeNodeMap = ContainerUtil.newIdentityTroveMap();

    final DataNodeCheckedTreeNode[] preselectedNode = {null};
    final DataNodeCheckedTreeNode[] rootModuleNode = {null};

    final MultiMap<String, String> moduleDependenciesMap = MultiMap.create();
    final Map<String, DataNode<ModuleData>> modulesNodeMap = ContainerUtil.newHashMap();

    for (DataNode<ModuleDependencyData> moduleDependencyDataNode : ExternalSystemApiUtil.findAllRecursively(
      myProjectInfo.getExternalProjectStructure(), ProjectKeys.MODULE_DEPENDENCY)) {
      final ModuleDependencyData moduleDependencyData = moduleDependencyDataNode.getData();
      moduleDependenciesMap.putValue(
        moduleDependencyData.getOwnerModule().getLinkedExternalProjectPath(),
        moduleDependencyData.getTarget().getLinkedExternalProjectPath());
    }

    final int[] modulesCount = {0};

    ExternalSystemApiUtil.visit(myProjectInfo.getExternalProjectStructure(), new Consumer<DataNode<?>>() {
      @Override
      public void consume(DataNode<?> node) {
        final Key key = node.getKey();
        if (!myPublicKeys.contains(key)) return;

        DataNode modifiableDataNode = getModifiableDataNode(node);

        if (node.getKey().equals(ProjectKeys.MODULE)) {
          modulesCount[0]++;
        }

        if (modifiableDataNode.isIgnored() && myShowSelectedRowsOnly) return;

        DataNodeCheckedTreeNode treeNode = treeNodeMap.get(node);
        if (treeNode == null) {
          treeNode = new DataNodeCheckedTreeNode(node);

          if (node.getKey().equals(ProjectKeys.MODULE)) {
            final ModuleData moduleData = (ModuleData)node.getData();
            //noinspection unchecked
            modulesNodeMap.put(moduleData.getLinkedExternalProjectPath(), (DataNode<ModuleData>)node);
          }

          if (myPreselectedNodeObject != null && myPreselectedNodeObject.equals(node.getData())) {
            preselectedNode[0] = treeNode;
          }
          if (node.getData() instanceof ModuleData) {
            if (myProjectInfo.getExternalProjectPath().equals(((ModuleData)node.getData()).getLinkedExternalProjectPath())) {
              rootModuleNode[0] = treeNode;
            }
          }
          treeNode.setEnabled(myIgnorableKeys.contains(key));
          treeNodeMap.put(node, treeNode);
          final DataNode parent = node.getParent();
          if (parent != null) {
            final CheckedTreeNode parentTreeNode = treeNodeMap.get(parent);
            if (parentTreeNode != null) {
              parentTreeNode.add(treeNode);
            }
          }
        }
      }
    });

    myModulesCount = modulesCount[0];

    dependentNodeMap.clear();
    for (String modulePath : moduleDependenciesMap.keySet()) {
      final Collection<String> moduleDependencies = moduleDependenciesMap.get(modulePath);
      final DataNode<ModuleData> moduleNode = modulesNodeMap.get(modulePath);
      if (moduleNode != null) {
        dependentNodeMap.putValues(moduleNode, ContainerUtil.mapNotNull(moduleDependencies, new Function<String, DataNode<ModuleData>>() {
          @Override
          public DataNode<ModuleData> fun(String s) {
            return modulesNodeMap.get(s);
          }
        }));
      }
    }

    final CheckedTreeNode root = new CheckedTreeNode(null);
    final DataNodeCheckedTreeNode projectNode = treeNodeMap.get(myProjectInfo.getExternalProjectStructure());

    if (rootModuleNode[0] != null) {
      rootModuleNode[0].comment = "root module";
      projectNode.remove(rootModuleNode[0]);
      projectNode.insert(rootModuleNode[0], 0);
    }

    List<TreeNode> nodes = TreeUtil.childrenToArray(projectNode);
    TreeUtil.addChildrenTo(root, nodes);
    return Couple.of(root, preselectedNode[0]);
  }

  @NotNull
  private static Set<Key<?>> getPublicKeys() {
    Set<Key<?>> result = ContainerUtil.newHashSet(DATA_KEYS);
    for (ExternalProjectStructureCustomizer customizer : ExternalProjectStructureCustomizer.EP_NAME.getExtensions()) {
      result.addAll(customizer.getPublicDataKeys());
    }
    return result;
  }

  @NotNull
  private static Set<Key<?>> getIgnorableKeys() {
    Set<Key<?>> result = ContainerUtil.newHashSet(DATA_KEYS);
    for (ExternalProjectStructureCustomizer customizer : ExternalProjectStructureCustomizer.EP_NAME.getExtensions()) {
      result.addAll(customizer.getIgnorableDataKeys());
    }
    return result;
  }

  private class DataNodeCheckedTreeNode extends CheckedTreeNode {
    private final DataNode myDataNode;
    @Nullable
    private Icon icon;
    private String text;
    @Nullable
    private String comment;

    private DataNodeCheckedTreeNode(DataNode node) {
      super(node);
      myDataNode = node;
      node.putUserData(CONNECTED_UI_NODE_KEY, this);
      DataNode modifiableDataNode = (DataNode)node.getUserData(MODIFIED_NODE_KEY);
      assert modifiableDataNode != null;
      isChecked = !modifiableDataNode.isIgnored();

      Icon anIconCandidate = null;
      boolean multipleIconCandidatesFound = false;
      ExternalProjectStructureCustomizer projectStructureCustomizer = new ExternalProjectStructureCustomizerImpl();
      for (ExternalProjectStructureCustomizer customizer : ExternalProjectStructureCustomizer.EP_NAME.getExtensions()) {
        Icon icon = customizer.suggestIcon(node, myExternalSystemUiAware);
        if (!multipleIconCandidatesFound && icon != null) {
          if (anIconCandidate != null) {
            multipleIconCandidatesFound = true;
            anIconCandidate = null;
          }
          else {
            anIconCandidate = icon;
          }
        }

        if (customizer.getPublicDataKeys().contains(node.getKey())) {
          projectStructureCustomizer = customizer;
          break;
        }
      }

      icon = anIconCandidate != null ? anIconCandidate : projectStructureCustomizer.suggestIcon(node, myExternalSystemUiAware);
      final Couple<String> representationName = projectStructureCustomizer.getRepresentationName(node);
      text = representationName.first;
      comment = representationName.second;

      if (text == null) {
        text = node.getKey().toString();
      }
    }

    @Override
    public boolean isChecked() {
      return super.isChecked();
    }

    @Override
    public void setChecked(final boolean checked) {
      super.setChecked(checked);
      if (checked) {
        DataNodeCheckedTreeNode parent = this;
        DataNodeCheckedTreeNode moduleNode = null;
        while (parent.parent instanceof DataNodeCheckedTreeNode) {
          if (moduleNode == null && ProjectKeys.MODULE.equals(parent.myDataNode.getKey())) {
            moduleNode = parent;
          }
          parent = (DataNodeCheckedTreeNode)parent.parent;
        }
        parent.isChecked = true;

        final DataNode modifiedParentDataNode = getModifiableDataNode(parent.myDataNode);
        modifiedParentDataNode.setIgnored(false);

        if (moduleNode != null) {
          moduleNode.isChecked = true;
        }
        ExternalSystemApiUtil.visit(moduleNode == null ? myDataNode : moduleNode.myDataNode, new Consumer<DataNode<?>>() {
          @Override
          public void consume(DataNode node) {
            final DataNode modifiedDataNode = getModifiableDataNode(node);
            modifiedDataNode.setIgnored(false);
          }
        });
      }
      else {
        ExternalSystemApiUtil.visit(myDataNode, new Consumer<DataNode<?>>() {
          @Override
          public void consume(DataNode node) {
            final DataNode modifiedDataNode = getModifiableDataNode(node);
            modifiedDataNode.setIgnored(true);
          }
        });
        if (myShowSelectedRowsOnly) {
          final DefaultTreeModel treeModel = (DefaultTreeModel)myTree.getModel();
          treeModel.removeNodeFromParent(this);
        }
      }

      if (!checked && parent instanceof DataNodeCheckedTreeNode) {
        if (myDataNode.getKey().equals(ProjectKeys.MODULE) &&
            ((DataNodeCheckedTreeNode)parent).myDataNode.getKey().equals(ProjectKeys.PROJECT)) {
          final DataNode projectDataNode = ((DataNodeCheckedTreeNode)parent).myDataNode;
          final ProjectData projectData = (ProjectData)projectDataNode.getData();
          final ModuleData moduleData = (ModuleData)myDataNode.getData();
          if (moduleData.getLinkedExternalProjectPath().equals(projectData.getLinkedExternalProjectPath())) {
            if (ExternalSystemApiUtil.findAll(projectDataNode, ProjectKeys.MODULE).size() == 1) {
              ((DataNodeCheckedTreeNode)parent).setChecked(false);
            }
          }
        }
      }

      updateSelectionState();
    }
  }

  @NotNull
  private static DataNode getModifiableDataNode(@NotNull DataNode node) {
    DataNode modifiedDataNode = (DataNode)node.getUserData(MODIFIED_NODE_KEY);
    if (modifiedDataNode == null) {
      modifiedDataNode = node.nodeCopy();
      node.putUserData(MODIFIED_NODE_KEY, modifiedDataNode);
    }
    return modifiedDataNode;
  }

  private SelectionState getSelectionStatus() {
    boolean isRequiredSelectionEnabled = computeRequiredSelectionStatus();

    String stateMessage = "";
    final Object root = myTree.getModel().getRoot();
    if (root instanceof CheckedTreeNode) {

      final int[] selectedModulesCount = {0};

      TreeUtil.traverse((CheckedTreeNode)root, new TreeUtil.Traverse() {
        @Override
        public boolean accept(Object node) {
          if (node instanceof DataNodeCheckedTreeNode &&
              ((DataNodeCheckedTreeNode)node).isChecked() &&
              ((DataNodeCheckedTreeNode)node).myDataNode.getKey().equals(ProjectKeys.MODULE)) {
            selectedModulesCount[0]++;
          }
          return true;
        }
      });
      stateMessage = String.format("%1$d Modules. %2$d selected", myModulesCount, selectedModulesCount[0]);
    }


    return new SelectionState(isRequiredSelectionEnabled, stateMessage);
  }

  private boolean computeRequiredSelectionStatus() {
    for (DataNode<ModuleData> node : dependentNodeMap.keySet()) {
      final DataNodeCheckedTreeNode uiNode = node.getUserData(CONNECTED_UI_NODE_KEY);
      assert uiNode != null;
      if (!uiNode.isChecked()) continue;

      for (DataNode<ModuleData> depNode : dependentNodeMap.get(node)) {
        final DataNodeCheckedTreeNode uiDependentNode = depNode.getUserData(CONNECTED_UI_NODE_KEY);
        assert uiDependentNode != null;
        if (!uiDependentNode.isChecked()) return true;
      }
    }
    return false;
  }

  private static class SelectionState {
    boolean isRequiredSelectionEnabled;
    @Nullable String message;

    public SelectionState(boolean isRequiredSelectionEnabled, @Nullable String message) {
      this.isRequiredSelectionEnabled = isRequiredSelectionEnabled;
      this.message = message;
    }
  }

  private class SelectAllButton extends AnActionButton {
    public SelectAllButton() {
      super("Select All", AllIcons.Actions.Selectall);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final DefaultTreeModel treeModel = (DefaultTreeModel)myTree.getModel();
      final Object root = treeModel.getRoot();
      if (!(root instanceof CheckedTreeNode)) return;

      if (!myShowSelectedRowsOnly) {
        myTree.setNodeState((CheckedTreeNode)root, true);
      }
      else {
        myShowSelectedRowsOnly = false;
        reloadTree();
        myTree.setNodeState((CheckedTreeNode)root, true);
        myShowSelectedRowsOnly = true;
      }
    }
  }

  private class UnselectAllButton extends AnActionButton {
    public UnselectAllButton() {
      super("Unselect All", AllIcons.Actions.Unselectall);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final DefaultTreeModel treeModel = (DefaultTreeModel)myTree.getModel();
      final Object root = treeModel.getRoot();
      if (!(root instanceof CheckedTreeNode)) return;

      if (!myShowSelectedRowsOnly) {
        myTree.setNodeState((CheckedTreeNode)root, false);
      }
      else {
        myShowSelectedRowsOnly = false;
        reloadTree();
        myTree.setNodeState((CheckedTreeNode)root, false);
        myShowSelectedRowsOnly = true;
        reloadTree();
      }
    }
  }

  private class ShowSelectedOnlyButton extends ToggleActionButton {

    public ShowSelectedOnlyButton() {
      super("Show Selected Only", AllIcons.Actions.ShowHiddens);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myShowSelectedRowsOnly;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myShowSelectedRowsOnly = state;
      reloadTree();
    }
  }

  private class SelectRequiredButton extends AnActionButton {
    public SelectRequiredButton() {
      super("Select Required", "select required projects based on your current selection", AllIcons.Actions.IntentionBulb);

      addCustomUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          return selectionState.getValue().isRequiredSelectionEnabled;
        }
      });
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      boolean showSelectedRowsOnly = myShowSelectedRowsOnly;
      if (showSelectedRowsOnly) {
        myShowSelectedRowsOnly = false;
        reloadTree();
      }
      for (DataNode<ModuleData> node : dependentNodeMap.keySet()) {
        final DataNodeCheckedTreeNode uiNode = node.getUserData(CONNECTED_UI_NODE_KEY);
        assert uiNode != null;
        if (!uiNode.isChecked()) continue;

        for (DataNode<ModuleData> treeNode : dependentNodeMap.get(node)) {
          final DataNodeCheckedTreeNode uiDependentNode = treeNode.getUserData(CONNECTED_UI_NODE_KEY);
          assert uiDependentNode != null;
          myTree.setNodeState(uiDependentNode, true);
        }
      }

      if (showSelectedRowsOnly) {
        myShowSelectedRowsOnly = true;
        reloadTree();
      }
      updateSelectionState();
    }

    @Override
    public boolean displayTextInToolbar() {
      return true;
    }
  }
}
