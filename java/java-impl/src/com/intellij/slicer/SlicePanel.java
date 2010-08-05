/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.slicer;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.ide.actions.RefreshAction;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.pom.Navigatable;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageViewSettings;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public abstract class SlicePanel extends JPanel implements TypeSafeDataProvider, Disposable {
  private final SliceTreeBuilder myBuilder;
  private final JTree myTree;

  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
    protected boolean isAutoScrollMode() {
      return isAutoScroll();
    }

    protected void setAutoScrollMode(final boolean state) {
      setAutoScroll(state);
    }
  };
  private UsagePreviewPanel myUsagePreviewPanel;
  private final Project myProject;
  private boolean isDisposed;
  private final ToolWindow myToolWindow;

  public SlicePanel(final Project project, boolean dataFlowToThis, SliceNode rootNode, boolean splitByLeafExpressions, final ToolWindow toolWindow) {
    super(new BorderLayout());
    myToolWindow = toolWindow;
    final ToolWindowManagerListener listener = new ToolWindowManagerListener() {
      ToolWindowAnchor myAnchor = toolWindow.getAnchor();
      public void toolWindowRegistered(@NotNull String id) {
      }

      public void stateChanged() {
        if (!project.isOpen()) return;
        if (toolWindow.getAnchor() != myAnchor) {
          myAnchor = myToolWindow.getAnchor();
          layoutPanel();
        }
      }
    };
    ToolWindowManagerEx.getInstanceEx(project).addToolWindowManagerListener(listener);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        ToolWindowManagerEx.getInstanceEx(project).removeToolWindowManagerListener(listener);
      }
    });

    ApplicationManager.getApplication().assertIsDispatchThread();
    myProject = project;
    myTree = createTree();

    myBuilder = new SliceTreeBuilder(myTree, project, dataFlowToThis, rootNode, splitByLeafExpressions);
    myBuilder.setCanYieldUpdate(!ApplicationManager.getApplication().isUnitTestMode());

    Disposer.register(this, myBuilder);

    myBuilder.addSubtreeToUpdate((DefaultMutableTreeNode)myTree.getModel().getRoot(), new Runnable() {
      public void run() {
        if (isDisposed || myBuilder.isDisposed() || myProject.isDisposed()) return;
        final SliceNode rootNode = myBuilder.getRootSliceNode();
        myBuilder.expand(rootNode, new Runnable() {
          public void run() {
            if (isDisposed || myBuilder.isDisposed() || myProject.isDisposed()) return;
            myBuilder.select(rootNode.myCachedChildren.get(0)); //first there is ony one child
          }
        });
        treeSelectionChanged();
      }
    });

    layoutPanel();
  }

  private void layoutPanel() {
    if (myUsagePreviewPanel != null) {
      Disposer.dispose(myUsagePreviewPanel);
    }
    removeAll();
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myTree);

    if (isPreview()) {
      pane.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT | SideBorder.RIGHT));

      boolean vertical = myToolWindow.getAnchor() == ToolWindowAnchor.LEFT || myToolWindow.getAnchor() == ToolWindowAnchor.RIGHT;
      Splitter splitter = new Splitter(vertical, UsageViewSettings.getInstance().PREVIEW_USAGES_SPLITTER_PROPORTIONS);
      splitter.setFirstComponent(pane);
      myUsagePreviewPanel = new UsagePreviewPanel(myProject);
      myUsagePreviewPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));

      Disposer.register(this, myUsagePreviewPanel);
      splitter.setSecondComponent(myUsagePreviewPanel);
      add(splitter, BorderLayout.CENTER);
    }
    else {
      pane.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));
      add(pane, BorderLayout.CENTER);
    }

    add(createToolbar().getComponent(), BorderLayout.WEST);

    revalidate();
  }

  public void dispose() {
    if (myUsagePreviewPanel != null) {
      UsageViewSettings.getInstance().PREVIEW_USAGES_SPLITTER_PROPORTIONS = ((Splitter)myUsagePreviewPanel.getParent()).getProportion();
      myUsagePreviewPanel = null;
    }
    
    isDisposed = true;
    ToolTipManager.sharedInstance().unregisterComponent(myTree);
  }

  private JTree createTree() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    final Tree tree = new Tree(new DefaultTreeModel(root)){
      protected void paintComponent(Graphics g) {
        DuplicateNodeRenderer.paintDuplicateNodesBackground(g, this);
        super.paintComponent(g);
      }
    };
    tree.setOpaque(false);

    tree.setToggleClickCount(-1);
    SliceUsageCellRenderer renderer = new SliceUsageCellRenderer();
    renderer.setOpaque(false);
    tree.setCellRenderer(renderer);
    UIUtil.setLineStyleAngled(tree);
    tree.setRootVisible(false);
    
    tree.setShowsRootHandles(true);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setSelectionPath(new TreePath(root.getPath()));
    //ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_METHOD_HIERARCHY_POPUP);
    //PopupHandler.installPopupHandler(tree, group, ActionPlaces.METHOD_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    EditSourceOnDoubleClickHandler.install(tree);

    new TreeSpeedSearch(tree);
    TreeUtil.installActions(tree);
    ToolTipManager.sharedInstance().registerComponent(tree);

    myAutoScrollToSourceHandler.install(tree);

    tree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        treeSelectionChanged();
      }
    });

    tree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {
          List<Navigatable> navigatables = getNavigatables();
          if (navigatables.isEmpty()) return;
          for (Navigatable navigatable : navigatables) {
            if (navigatable instanceof AbstractTreeNode && ((AbstractTreeNode)navigatable).getValue() instanceof Usage) {
              navigatable = (Usage)((AbstractTreeNode)navigatable).getValue();
            }
            if (navigatable.canNavigateToSource()) {
              navigatable.navigate(false);
              if (navigatable instanceof Usage) {
                ((Usage)navigatable).highlightInEditor();
              }
            }
          }
          e.consume();
        }
      }
    });

    tree.addTreeWillExpandListener(new TreeWillExpandListener() {
      public void treeWillCollapse(TreeExpansionEvent event) {
      }

      public void treeWillExpand(TreeExpansionEvent event) {
        TreePath path = event.getPath();
        SliceNode node = fromPath(path);
        node.calculateDupNode();
      }
    });

    return tree;
  }

  private void treeSelectionChanged() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (isDisposed) return;
        List<UsageInfo> infos = getSelectedUsageInfos();
        if (infos != null && myUsagePreviewPanel != null) {
          myUsagePreviewPanel.updateLayout(infos);
        }
      }
    });
  }

  private static SliceNode fromPath(TreePath path) {
    Object lastPathComponent = path.getLastPathComponent();
    if (lastPathComponent instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)lastPathComponent;
      Object userObject = node.getUserObject();
      if (userObject instanceof SliceNode) {
        return (SliceNode)userObject;
      }
    }
   return null;
  }

  private List<UsageInfo> getSelectedUsageInfos() {
    TreePath[] paths = myTree.getSelectionPaths();
    if (paths == null) return null;
    final ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    for (TreePath path : paths) {
      SliceNode sliceNode = fromPath(path);
      if (sliceNode != null) {
        result.add(sliceNode.getValue().getUsageInfo());
      }
    }
    if (result.isEmpty()) return null;
    return result;
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key == PlatformDataKeys.NAVIGATABLE_ARRAY) {
      List<Navigatable> navigatables = getNavigatables();
      if (!navigatables.isEmpty()) {
        sink.put(PlatformDataKeys.NAVIGATABLE_ARRAY, navigatables.toArray(new Navigatable[navigatables.size()]));
      }
    }
  }

  @NotNull
  private List<Navigatable> getNavigatables() {
    TreePath[] paths = myTree.getSelectionPaths();
    if (paths == null) return Collections.emptyList();
    final ArrayList<Navigatable> navigatables = new ArrayList<Navigatable>();
    for (TreePath path : paths) {
      Object lastPathComponent = path.getLastPathComponent();
      if (lastPathComponent instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)lastPathComponent;
        Object userObject = node.getUserObject();
        if (userObject instanceof Navigatable) {
          navigatables.add((Navigatable)userObject);
        }
        else if (node instanceof Navigatable) {
          navigatables.add((Navigatable)node);
        }
      }
    }
    return navigatables;
  }

  private ActionToolbar createToolbar() {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new MyRefreshAction(myTree));
    actionGroup.add(myAutoScrollToSourceHandler.createToggleAction());
    actionGroup.add(new CloseAction());
    actionGroup.add(new ToggleAction(UsageViewBundle.message("preview.usages.action.text"), "preview", IconLoader.getIcon("/actions/preview.png")) {
      public boolean isSelected(AnActionEvent e) {
        return isPreview();
      }

      public void setSelected(AnActionEvent e, boolean state) {
        setPreview(state);
        layoutPanel();
      }
    });

    if (myBuilder.dataFlowToThis) {
      actionGroup.add(new AnalyzeLeavesAction(myBuilder));
      actionGroup.add(new CanItBeNullAction(myBuilder));
    }

    //actionGroup.add(new ContextHelpAction(HELP_ID));

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.TYPE_HIERARCHY_VIEW_TOOLBAR, actionGroup, false);
  }

  public abstract boolean isAutoScroll();

  public abstract void setAutoScroll(boolean autoScroll);

  public abstract boolean isPreview();

  public abstract void setPreview(boolean preview);

  private class CloseAction extends CloseTabToolbarAction {
    public final void actionPerformed(final AnActionEvent e) {
      close();
    }
  }

  protected abstract void close();

  private final class MyRefreshAction extends RefreshAction {
    private MyRefreshAction(JComponent tree) {
      super(IdeBundle.message("action.refresh"), IdeBundle.message("action.refresh"), IconLoader.getIcon("/actions/sync.png"));
      registerShortcutOn(tree);
    }

    public final void actionPerformed(final AnActionEvent e) {
      SliceNode rootNode = (SliceNode)myBuilder.getRootNode().getUserObject();
      rootNode.setChanged();
      myBuilder.addSubtreeToUpdate(myBuilder.getRootNode());
    }

    public final void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(true);
    }
  }

  @TestOnly
  public SliceTreeBuilder getBuilder() {
    return myBuilder;
  }
}
