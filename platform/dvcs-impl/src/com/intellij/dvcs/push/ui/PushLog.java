/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.dvcs.push.ui;

import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.ui.*;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EventObject;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PushLog extends JPanel implements TypeSafeDataProvider {

  private final ReentrantReadWriteLock TREE_CONSTRUCTION_LOCK = new ReentrantReadWriteLock();

  private static final String START_EDITING = "startEditing";
  private final ChangesBrowser myChangesBrowser;
  private final CheckboxTree myTree;
  private final MyTreeCellRenderer myTreeCellRenderer;

  public PushLog(Project project, CheckedTreeNode root) {
    DefaultTreeModel treeModel = new DefaultTreeModel(root);
    treeModel.nodeStructureChanged(root);
    myTreeCellRenderer = new MyTreeCellRenderer();
    myTree = new CheckboxTree(myTreeCellRenderer, root) {

      public boolean isPathEditable(TreePath path) {
        return isEditable() && path.getLastPathComponent() instanceof DefaultMutableTreeNode;
      }

      @Override
      protected void onNodeStateChanged(CheckedTreeNode node) {
        if (node instanceof EditableTreeNode) {
          ((EditableTreeNode)node).fireOnSelectionChange(node.isChecked());
        }
      }

      @Override
      public String getToolTipText(MouseEvent event) {
        final TreePath path = myTree.getPathForLocation(event.getX(), event.getY());
        if (path == null) {
          return "";
        }
        Object node = path.getLastPathComponent();
        if (node == null || (!(node instanceof DefaultMutableTreeNode))) {
          return "";
        }
        if (node instanceof TooltipNode) {
          return ((TooltipNode)node).getTooltip();
        }
        return "";
      }
    };
    myTree.setEditable(true);
    MyTreeCellEditor treeCellEditor = new MyTreeCellEditor(new JBTextField());
    myTree.setCellEditor(treeCellEditor);
    treeCellEditor.addCellEditorListener(new CellEditorListener() {
      @Override
      public void editingStopped(ChangeEvent e) {
      }

      @Override
      public void editingCanceled(ChangeEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
        if (node != null && node instanceof EditableTreeNode) {
          //todo restore from appropriate editor
          ((EditableTreeNode)node).fireOnChange(((EditableTreeNode)node).getValue());
        }
      }
    });
    myTree.setRootVisible(false);
    TreeUtil.expandAll(myTree);
    final VcsBranchEditorListener linkMouseListener = new VcsBranchEditorListener(myTreeCellRenderer);
    linkMouseListener.installOn(myTree);

    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        TreePath[] nodes = myTree.getSelectionPaths();
        if (nodes != null) {
          ArrayList<Change> changes = new ArrayList<Change>();
          for (TreePath node : nodes) {
            Object nodeInfo = ((DefaultMutableTreeNode)node.getLastPathComponent()).getUserObject();
            if (nodeInfo instanceof VcsFullCommitDetails) {
              changes.addAll(((VcsFullCommitDetails)nodeInfo).getChanges());
            }
          }
          myChangesBrowser.getViewer().setEmptyText("No differences");
          myChangesBrowser.setChangesToDisplay(CommittedChangesTreeBrowser.zipChanges(changes));
          return;
        }
        setDefaultEmptyText();
        myChangesBrowser.setChangesToDisplay(Collections.<Change>emptyList());
      }
    });
    myTree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), START_EDITING);
    myTree.setRowHeight(0);
    ToolTipManager.sharedInstance().registerComponent(myTree);

    myChangesBrowser =
      new ChangesBrowser(project, null, Collections.<Change>emptyList(), null, false, true, null, ChangesBrowser.MyUseCase.LOCAL_CHANGES,
                         null);
    myChangesBrowser.getDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), myTree);
    setDefaultEmptyText();

    Splitter splitter = new Splitter(false, 0.7f);
    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree));
    splitter.setSecondComponent(myChangesBrowser);

    setLayout(new BorderLayout());
    add(splitter);
  }

  private void setDefaultEmptyText() {
    myChangesBrowser.getViewer().setEmptyText("No commits selected");
  }

  // Make changes available for diff action
  @Override
  public void calcData(DataKey key, DataSink sink) {
    if (VcsDataKeys.CHANGES.equals(key)) {
      DefaultMutableTreeNode[] selectedNodes = myTree.getSelectedNodes(DefaultMutableTreeNode.class, null);
      if (selectedNodes.length == 0) {
        return;
      }
      Object object = selectedNodes[0].getUserObject();
      if (object instanceof VcsFullCommitDetails) {
        sink.put(key, ArrayUtil.toObjectArray(((VcsFullCommitDetails)object).getChanges(), Change.class));
      }
    }
  }

  @Override
  protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
    if (e.getKeyCode() == KeyEvent.VK_ENTER && myTree.isEditing()) {
      myTree.cancelEditing();
      return true;
    }
    return super.processKeyBinding(ks, e, condition, pressed);
  }

  public void startLoading(DefaultMutableTreeNode parentNode) {
    LoadingTreeNode loading = new LoadingTreeNode();
    loading.getIcon().setImageObserver(new NodeImageObserver(myTree, loading));
    setChildren(parentNode, Collections.singleton(loading));
  }

  private class MyTreeCellEditor extends DefaultCellEditor {

    public MyTreeCellEditor(JTextField field) {
      super(field);
      setClickCountToStart(1);
    }

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
      final Object node = ((DefaultMutableTreeNode)value).getUserObject();
      editorComponent =
        (JComponent)((RepositoryWithBranchPanel)node).getTreeCellRendererComponent(tree, value, isSelected, expanded, leaf, row, true);
      return editorComponent;
    }

    @Override
    public boolean isCellEditable(EventObject anEvent) {
      if (anEvent instanceof MouseEvent) {
        MouseEvent me = ((MouseEvent)anEvent);
        final TreePath path = myTree.getClosestPathForLocation(me.getX(), me.getY());
        final int row = myTree.getRowForLocation(me.getX(), me.getY());
        myTree.getCellRenderer().getTreeCellRendererComponent(myTree, path.getLastPathComponent(), false, false, true, row, true);
        Object tag = me.getClickCount() >= clickCountToStart
                     ? PushLogTreeUtil.getTagAtForRenderer(myTreeCellRenderer, me)
                     : null;
        return tag instanceof EditorTextField;
      }
      //if keyboard event - then anEvent will be null =( See BasicTreeUi
      TreePath treePath = myTree.getAnchorSelectionPath();
      //there is no selection path if we start editing during initial validation//
      if (treePath == null) return true;
      Object treeNode = treePath.getLastPathComponent();
      return treeNode instanceof EditableTreeNode;
    }

    //Implement the one CellEditor method that AbstractCellEditor doesn't.
    public Object getCellEditorValue() {
      return ((RepositoryWithBranchPanel)editorComponent).getRemoteTargetName();
    }
  }

  private static class MyTreeCellRenderer extends CheckboxTree.CheckboxTreeCellRenderer {

    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (!(value instanceof DefaultMutableTreeNode)) {
        return;
      }
      if (value instanceof RepositoryNode) {
        //todo simplify, remove instance of
        myCheckbox.setVisible(((RepositoryNode)value).isCheckboxVisible());
      }
      Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      ColoredTreeCellRenderer renderer = getTextRenderer();
      renderer.setBorder(null);
      if (value instanceof CustomRenderedTreeNode) {
        ((CustomRenderedTreeNode)value).render(renderer);
      }
      else {
        renderer.append(userObject == null ? "" : userObject.toString());
      }
    }
  }

  public void setChildren(DefaultMutableTreeNode parentNode, @NotNull Collection<? extends DefaultMutableTreeNode> childrenNodes) {
    setChildren(parentNode, childrenNodes, true);
  }

  public void setChildren(DefaultMutableTreeNode parentNode,
                          @NotNull Collection<? extends DefaultMutableTreeNode> childrenNodes,
                          boolean shouldExpand) {
    try {
      TREE_CONSTRUCTION_LOCK.writeLock().lock();
      parentNode.removeAllChildren();
      for (DefaultMutableTreeNode child : childrenNodes) {
        parentNode.add(child);
      }
      final DefaultTreeModel model = ((DefaultTreeModel)myTree.getModel());
      model.nodeStructureChanged(parentNode);
      TreePath path = TreeUtil.getPathFromRoot(parentNode);
      if (shouldExpand) {
        myTree.expandPath(path);
      }
      else {
        myTree.collapsePath(path);
      }
    }
    finally {
      TREE_CONSTRUCTION_LOCK.writeLock().unlock();
    }
  }

  @Nullable
  public JComponent startEditNode(@NotNull TreeNode node) {
    TreePath path = TreeUtil.getPathFromRoot(node);
    if (!myTree.isEditing()) {
      myTree.startEditingAtPath(path);
    }
    return (JComponent)myTree.getCellEditor()
      .getTreeCellEditorComponent(myTree, node, false, false, false, myTree.getRowForPath(path));
  }
}
