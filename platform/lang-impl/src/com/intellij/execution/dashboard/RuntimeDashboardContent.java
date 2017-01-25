/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.dashboard;

import com.intellij.execution.*;
import com.intellij.execution.dashboard.tree.DashboardGrouper;
import com.intellij.execution.dashboard.tree.RuntimeDashboardTreeStructure;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.util.treeView.*;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.*;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.content.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author konstantin.aleev
 */
public class RuntimeDashboardContent extends JPanel implements TreeContent, Disposable {
  public static final DataKey<RuntimeDashboardContent> KEY = DataKey.create("runtimeDashboardContent");
  @NonNls private static final String PLACE_TOOLBAR = "RuntimeDashboardContent#Toolbar";
  @NonNls private static final String RUNTIME_DASHBOARD_TOOLBAR = "RuntimeDashboardToolbar";
  @NonNls private static final String RUNTIME_DASHBOARD_POPUP = "RuntimeDashboardPopup";

  private static final String MESSAGE_CARD = "message";
  private static final String CONTENT_CARD = "content";

  private final Tree myTree;
  private final CardLayout myDetailsPanelLayout;
  private final JPanel myDetailsPanel;
  private final JBPanelWithEmptyText myMessagePanel;

  private final DefaultTreeModel myTreeModel;
  private AbstractTreeBuilder myBuilder;
  private AbstractTreeNode<?> myLastSelection;
  private Set<Object> myCollapsedTreeNodeValues = new HashSet<>();
  private List<DashboardGrouper> myGroupers;

  @NotNull private final ContentManager myContentManager;
  @NotNull private final ContentManagerListener myContentManagerListener;

  @NotNull private final Project myProject;

  public RuntimeDashboardContent(@NotNull Project project, @NotNull ContentManager contentManager, @NotNull List<DashboardGrouper> groupers) {
    super(new BorderLayout());
    myProject = project;
    myGroupers = groupers;

    myTreeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(myTreeModel);
    myTree.setRootVisible(false);

    myTree.setShowsRootHandles(true);
    myTree.setCellRenderer(new NodeRenderer());
    myTree.setLineStyleAngled();

    add(createToolbar(), BorderLayout.WEST);

    Splitter splitter = new OnePixelSplitter(false, 0.3f);
    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree, SideBorder.LEFT));
    myDetailsPanelLayout = new CardLayout();
    myDetailsPanel = new JPanel(myDetailsPanelLayout);
    myMessagePanel = new JBPanelWithEmptyText().withEmptyText(ExecutionBundle.message("runtime.dashboard.empty.selection.message"));
    myDetailsPanel.add(MESSAGE_CARD, myMessagePanel);
    splitter.setSecondComponent(myDetailsPanel);
    add(splitter, BorderLayout.CENTER);

    myContentManager = contentManager;
    myContentManagerListener = new ContentManagerAdapter() {
      @Override
      public void selectionChanged(final ContentManagerEvent event) {
        if (ContentManagerEvent.ContentOperation.add != event.getOperation()) {
          return;
        }
        myBuilder.queueUpdate().doWhenDone(() -> myBuilder.accept(DashboardNode.class, new TreeVisitor<DashboardNode>() {
          @Override
          public boolean visit(@NotNull DashboardNode node) {
            if (node.getContent() == event.getContent()) {
              myBuilder.select(node);
            }
            return false;
          }
        }));
        showContentPanel();
      }
    };
    myContentManager.addContentManagerListener(myContentManagerListener);
    myDetailsPanel.add(CONTENT_CARD, myContentManager.getComponent());

    setupBuilder();

    myTree.addTreeSelectionListener(e -> onSelectionChanged());
    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        Object value = getNodeValue(event);
        if (value != null) {
          myCollapsedTreeNodeValues.remove(value);
        }
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        Object value = getNodeValue(event);
        if (value != null) {
          myCollapsedTreeNodeValues.add(value);
        }
      }

      private Object getNodeValue(TreeExpansionEvent event) {
        DefaultMutableTreeNode treeNode = ObjectUtils.tryCast(event.getPath().getLastPathComponent(), DefaultMutableTreeNode.class);
        if (treeNode == null) {
          return null;
        }
        AbstractTreeNode nodeDescriptor = ObjectUtils.tryCast(treeNode.getUserObject(), AbstractTreeNode.class);
        if (nodeDescriptor == null) {
          return null;
        }
        return nodeDescriptor.getValue();
      }
    });

    DefaultActionGroup popupActionGroup = new DefaultActionGroup();
    popupActionGroup.add(ActionManager.getInstance().getAction(RUNTIME_DASHBOARD_TOOLBAR));
    popupActionGroup.add(ActionManager.getInstance().getAction(RUNTIME_DASHBOARD_POPUP));
    PopupHandler.installPopupHandler(myTree, popupActionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());

    new TreeSpeedSearch(myTree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true);
  }

  private void onSelectionChanged() {
    Set<AbstractTreeNode> nodes = myBuilder.getSelectedElements(AbstractTreeNode.class);
    if (nodes.size() != 1) {
      showMessagePanel(ExecutionBundle.message("runtime.dashboard.empty.selection.message"));
      myLastSelection = null;
      return;
    }

    AbstractTreeNode<?> node = nodes.iterator().next();
    if (Comparing.equal(node, myLastSelection)) {
      return;
    }

    myLastSelection = node;
    if (node instanceof DashboardNode) {
      Content content = ((DashboardNode)node).getContent();
      if (content != null) {
        if (content != myContentManager.getSelectedContent()) {
          myContentManager.removeContentManagerListener(myContentManagerListener);
          myContentManager.setSelectedContent(content);
          myContentManager.addContentManagerListener(myContentManagerListener);
        }
        showContentPanel();
        return;
      }
    }

    showMessagePanel("");
  }

  private void showMessagePanel(String text) {
    Content selectedContent = myContentManager.getSelectedContent();
    if (selectedContent != null) {
      myContentManager.removeContentManagerListener(myContentManagerListener);
      myContentManager.removeFromSelection(selectedContent);
      myContentManager.addContentManagerListener(myContentManagerListener);
    }

    myMessagePanel.getEmptyText().setText(text);
    myDetailsPanelLayout.show(myDetailsPanel, MESSAGE_CARD);
  }

  private void showContentPanel() {
    myDetailsPanelLayout.show(myDetailsPanel, CONTENT_CARD);
  }

  private void setupBuilder() {
    RuntimeDashboardTreeStructure structure = new RuntimeDashboardTreeStructure(myProject, myGroupers);
    myBuilder = new AbstractTreeBuilder(myTree, myTreeModel, structure, IndexComparator.INSTANCE) {
      @Override
      protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
        return super.isAutoExpandNode(nodeDescriptor) ||
               !myCollapsedTreeNodeValues.contains(((AbstractTreeNode)nodeDescriptor).getValue());
      }
    };
    myBuilder.initRootNode();
    Disposer.register(this, myBuilder);
    RunManagerEx.getInstanceEx(myProject).addRunManagerListener(new RunManagerListener() {
      @Override
      public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
        updateTreeIfNeeded(settings);
      }

      @Override
      public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
        updateTreeIfNeeded(settings);
      }

      @Override
      public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
        updateTreeIfNeeded(settings);
      }
    });
    myProject.getMessageBus().connect(myProject).subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
      @Override
      public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, final @NotNull ProcessHandler handler) {
        updateTreeIfNeeded(env.getRunnerAndConfigurationSettings());
      }

      @Override
      public void processTerminated(@NotNull String executorId,
                                    @NotNull ExecutionEnvironment env,
                                    @NotNull ProcessHandler handler,
                                    int exitCode) {
        updateTreeIfNeeded(env.getRunnerAndConfigurationSettings());
      }
    });
  }

  private void updateTreeIfNeeded(@Nullable RunnerAndConfigurationSettings settings) {
    if (settings != null && RuntimeDashboardContributor.isShowInDashboard(settings.getType())) {
      updateTree();
    }
  }

  private JComponent createToolbar() {
    JPanel toolBarPanel = new JPanel(new GridLayout());
    DefaultActionGroup leftGroup = new DefaultActionGroup();
    leftGroup.add(ActionManager.getInstance().getAction(RUNTIME_DASHBOARD_TOOLBAR));
    // TODO [konstantin.aleev] provide context help ID
    //leftGroup.add(new Separator());
    //leftGroup.add(new ContextHelpAction(HELP_ID));

    ActionToolbar leftActionToolBar = ActionManager.getInstance().createActionToolbar(PLACE_TOOLBAR, leftGroup, false);
    toolBarPanel.add(leftActionToolBar.getComponent());

    myTree.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, new DataProvider() {
      @Override
      public Object getData(@NonNls String dataId) {
        if (KEY.getName().equals(dataId)) {
          return RuntimeDashboardContent.this;
        }
        return null;
      }
    });
    leftActionToolBar.setTargetComponent(myTree);

    DefaultActionGroup rightGroup = new DefaultActionGroup();

    TreeExpander treeExpander = new DefaultTreeExpander(myTree);
    AnAction expandAllAction = CommonActionsManager.getInstance().createExpandAllAction(treeExpander, this);
    rightGroup.add(expandAllAction);

    AnAction collapseAllAction = CommonActionsManager.getInstance().createCollapseAllAction(treeExpander, this);
    rightGroup.add(collapseAllAction);

    rightGroup.add(new Separator());
    myGroupers.forEach(grouper -> rightGroup.add(new GroupAction(grouper)));

    ActionToolbar rightActionToolBar = ActionManager.getInstance().createActionToolbar(PLACE_TOOLBAR, rightGroup, false);
    toolBarPanel.add(rightActionToolBar.getComponent());
    rightActionToolBar.setTargetComponent(myTree);
    return toolBarPanel;
  }

  @Override
  public void dispose() {
  }

  public void updateTree() {
    ApplicationManager.getApplication().invokeLater(() -> myBuilder.queueUpdate().doWhenDone(() -> {
      // Remove nodes not presented in the tree from collapsed node values set.
      // Children retrieving is quick since grouping and run configuration nodes are already constructed.
      Set<Object> nodes = new HashSet<>();
      myBuilder.accept(AbstractTreeNode.class, new TreeVisitor<AbstractTreeNode>() {
        @Override
        public boolean visit(@NotNull AbstractTreeNode node) {
          nodes.add(node.getValue());
          return false;
        }
      });
      myCollapsedTreeNodeValues.retainAll(nodes);
    }), myProject.getDisposed());
  }

  @Override
  @NotNull
  public AbstractTreeBuilder getBuilder() {
    return myBuilder;
  }

  private class GroupAction extends ToggleAction implements DumbAware {
    private DashboardGrouper myGrouper;

    public GroupAction(DashboardGrouper grouper) {
      super();
      myGrouper = grouper;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      Presentation presentation = e.getPresentation();
      ActionPresentation actionPresentation = myGrouper.getRule().getPresentation();
      presentation.setText(actionPresentation.getText());
      presentation.setDescription(actionPresentation.getDescription());
      presentation.setIcon(actionPresentation.getIcon());
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myGrouper.isEnabled();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myGrouper.setEnabled(state);
      updateTree();
    }
  }
}
