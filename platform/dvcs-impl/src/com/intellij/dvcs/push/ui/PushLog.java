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

import com.intellij.dvcs.push.PushTargetPanel;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.EditSourceAction;
import com.intellij.idea.ActionsBundle;
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
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;

public class PushLog extends JPanel implements TypeSafeDataProvider {

  private static final String START_EDITING = "startEditing";
  private final ChangesBrowser myChangesBrowser;
  private final CheckboxTree myTree;
  private final MyTreeCellRenderer myTreeCellRenderer;
  private boolean myShouldRepaint = false;

  public PushLog(Project project, final CheckedTreeNode root) {
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

      @Override
      public boolean stopEditing() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
        if (node instanceof EditableTreeNode) {
          JComponent editedComponent = (JComponent)node.getUserObject();
          InputVerifier verifier = editedComponent.getInputVerifier();
          if (verifier != null && !verifier.verify(editedComponent)) return false;
        }
        boolean result = super.stopEditing();
        if (myShouldRepaint) {
          refreshNode(root);
        }
        return result;
      }

      @Override
      public void cancelEditing() {
        super.cancelEditing();
        if (myShouldRepaint) {
          refreshNode(root);
        }
      }
    };
    myTree.setEditable(true);
    MyTreeCellEditor treeCellEditor = new MyTreeCellEditor();
    myTree.setCellEditor(treeCellEditor);
    treeCellEditor.addCellEditorListener(new CellEditorListener() {
      @Override
      public void editingStopped(ChangeEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
        if (node != null && node instanceof EditableTreeNode) {
          ((EditableTreeNode)node).fireOnChange();
        }
        myTree.firePropertyChange(PushLogTreeUtil.EDIT_MODE_PROP, true, false);
      }

      @Override
      public void editingCanceled(ChangeEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
        if (node != null && node instanceof EditableTreeNode) {
          ((EditableTreeNode)node).fireOnCancel();
        }
        myTree.firePropertyChange(PushLogTreeUtil.EDIT_MODE_PROP, true, false);
      }
    });
    myTree.setRootVisible(false);
    TreeUtil.collapseAll(myTree, 1);
    final VcsBranchEditorListener linkMouseListener = new VcsBranchEditorListener(myTreeCellRenderer);
    linkMouseListener.installOn(myTree);

    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        updateChangesView();
      }
    });
    myTree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), START_EDITING);
    //override default tree behaviour.
    myTree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "");

    myTree.setRowHeight(0);
    ToolTipManager.sharedInstance().registerComponent(myTree);

    myChangesBrowser =
      new ChangesBrowser(project, null, Collections.<Change>emptyList(), null, false, true, null, ChangesBrowser.MyUseCase.LOCAL_CHANGES,
                         null);
    myChangesBrowser.getDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), myTree);
    myChangesBrowser.addToolbarAction(createEditSourceAction());
    setDefaultEmptyText();

    Splitter splitter = new Splitter(false, 0.7f);
    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree));
    splitter.setSecondComponent(myChangesBrowser);

    setLayout(new BorderLayout());
    add(splitter);
  }

  @NotNull
  private EditSourceAction createEditSourceAction() {
    final EditSourceAction editAction = new EditSourceAction();
    editAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), myChangesBrowser.getViewer());
    editAction.getTemplatePresentation().setIcon(AllIcons.Actions.EditSource);
    editAction.getTemplatePresentation().setText("Edit Source");
    editAction.getTemplatePresentation().setDescription(ActionsBundle.actionText("EditSource"));
    return editAction;
  }

  private void updateChangesView() {
    int[] rows = myTree.getSelectionRows();
    if (rows != null && rows.length != 0) {
      myChangesBrowser.getViewer().setEmptyText("No differences");
      myChangesBrowser.setChangesToDisplay(collectAllChanges(rows));
    }
    else {
      setDefaultEmptyText();
      myChangesBrowser.setChangesToDisplay(Collections.<Change>emptyList());
    }
  }

  @NotNull
  private List<Change> collectAllChanges(@NotNull int[] selectedRows) {
    List<DefaultMutableTreeNode> selectedNodes = getNodesForRows(getSortedRows(selectedRows));
    List<CommitNode> commitNodes = collectSelectedCommitNodes(selectedNodes);
    return CommittedChangesTreeBrowser.zipChanges(collectChanges(commitNodes));
  }

  @NotNull
  private static List<CommitNode> collectSelectedCommitNodes(@NotNull List<DefaultMutableTreeNode> selectedNodes) {
    List<CommitNode> nodes = ContainerUtil.newArrayList();
    for (DefaultMutableTreeNode node : selectedNodes) {
      if (node instanceof RepositoryNode) {
        nodes.addAll(getChildNodes((RepositoryNode)node));
      }
      else if (node instanceof CommitNode && !nodes.contains(node)) {
        nodes.add((CommitNode)node);
      }
    }
    return nodes;
  }

  @NotNull
  private static List<Change> collectChanges(@NotNull List<CommitNode> commitNodes) {
    List<Change> changes = ContainerUtil.newArrayList();
    for (CommitNode node : commitNodes) {
      changes.addAll(node.getUserObject().getChanges());
    }
    return changes;
  }

  @NotNull
  private static List<CommitNode> getChildNodes(@NotNull RepositoryNode node) {
    List<CommitNode> nodes = ContainerUtil.newArrayList();
    if (node.getChildCount() < 1) {
      return nodes;
    }
    for (DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)node.getFirstChild();
         childNode != null;
         childNode = (DefaultMutableTreeNode)node.getChildAfter(childNode)) {
      if (childNode instanceof CommitNode) {
        nodes.add(0, (CommitNode)childNode);
      }
    }
    return nodes;
  }

  private void setDefaultEmptyText() {
    myChangesBrowser.getViewer().setEmptyText("No commits selected");
  }

  // Make changes available for diff action
  @Override
  public void calcData(DataKey key, DataSink sink) {
    if (VcsDataKeys.CHANGES.equals(key)) {
      int[] rows = myTree.getSelectionRows();
      if (rows != null && rows.length != 0) {
        Collection<Change> changes = collectAllChanges(rows);
        sink.put(key, ArrayUtil.toObjectArray(changes, Change.class));
      }
    }
  }

  @NotNull
  private static List<Integer> getSortedRows(@NotNull int[] rows) {
    List<Integer> sorted = ContainerUtil.newArrayList();
    for (int row : rows) {
      sorted.add(row);
    }
    Collections.sort(sorted, Collections.reverseOrder());
    return sorted;
  }

  @NotNull
  private List<DefaultMutableTreeNode> getNodesForRows(@NotNull List<Integer> rows) {
    List<DefaultMutableTreeNode> nodes = ContainerUtil.newArrayList();
    for (Integer row : rows) {
      TreePath path = myTree.getPathForRow(row);
      Object pathComponent = path == null ? null : path.getLastPathComponent();
      if (pathComponent instanceof DefaultMutableTreeNode) {
        nodes.add((DefaultMutableTreeNode)pathComponent);
      }
    }
    return nodes;
  }

  @Override
  protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
    if (e.getKeyCode() == KeyEvent.VK_ENTER && e.getModifiers() == 0 && pressed) {
      if (myTree.isEditing()) {
        myTree.stopEditing();
      }
      else {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
        if (node != null) {
          myTree.startEditingAtPath(TreeUtil.getPathFromRoot(node));
        }
      }
      return true;
    }
    return super.processKeyBinding(ks, e, condition, pressed);
  }

  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  @NotNull
  public JTree getTree() {
    return myTree;
  }

  public void selectIfNothingSelected(@NotNull TreeNode node) {
    if (myTree.isSelectionEmpty()) {
      myTree.setSelectionPath(TreeUtil.getPathFromRoot(node));
    }
  }

  private class MyTreeCellEditor extends AbstractCellEditor implements TreeCellEditor {

    private RepositoryWithBranchPanel myValue;

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
      RepositoryWithBranchPanel panel = (RepositoryWithBranchPanel)((DefaultMutableTreeNode)value).getUserObject();
      myValue = panel;
      myTree.firePropertyChange(PushLogTreeUtil.EDIT_MODE_PROP, false, true);
      return panel.getTreeCellEditorComponent(tree, value, isSelected, expanded, leaf, row, true);
    }

    @Override
    public boolean isCellEditable(EventObject anEvent) {
      if (anEvent instanceof MouseEvent) {
        MouseEvent me = ((MouseEvent)anEvent);
        final TreePath path = myTree.getClosestPathForLocation(me.getX(), me.getY());
        final int row = myTree.getRowForLocation(me.getX(), me.getY());
        myTree.getCellRenderer().getTreeCellRendererComponent(myTree, path.getLastPathComponent(), false, false, true, row, true);
        Object tag = me.getClickCount() >= 1
                     ? PushLogTreeUtil.getTagAtForRenderer(myTreeCellRenderer, me)
                     : null;
        return tag instanceof PushTargetPanel;
      }
      //if keyboard event - then anEvent will be null =( See BasicTreeUi
      TreePath treePath = myTree.getAnchorSelectionPath();
      //there is no selection path if we start editing during initial validation//
      if (treePath == null) return true;
      Object treeNode = treePath.getLastPathComponent();
      return treeNode instanceof EditableTreeNode;
    }

    public Object getCellEditorValue() {
      return myValue;
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
      if (value instanceof CustomRenderedTreeNode) {
        ((CustomRenderedTreeNode)value).render(renderer);
      }
      else {
        renderer.append(userObject == null ? "" : userObject.toString());
      }
    }
  }

  public void setChildren(@NotNull DefaultMutableTreeNode parentNode,
                          @NotNull Collection<? extends DefaultMutableTreeNode> childrenNodes) {
    parentNode.removeAllChildren();
    for (DefaultMutableTreeNode child : childrenNodes) {
      parentNode.add(child);
    }
    if (!myTree.isEditing()) {
      refreshNode(parentNode);
      TreePath path = TreeUtil.getPathFromRoot(parentNode);
      if (myTree.getSelectionModel().isPathSelected(path)) {
        updateChangesView();
      }
    }
    else {
      myShouldRepaint = true;
    }
  }

  private void refreshNode(@NotNull DefaultMutableTreeNode parentNode) {
    //todo should be optimized in case of start loading just edited node
    final DefaultTreeModel model = ((DefaultTreeModel)myTree.getModel());
    model.nodeStructureChanged(parentNode);
    expandSelected(parentNode);
    myShouldRepaint = false;
  }

  private void expandSelected(@NotNull DefaultMutableTreeNode node) {
    if (node.getChildCount() <= 0) return;
    if (node instanceof RepositoryNode) {
      TreePath path = TreeUtil.getPathFromRoot(node);
      myTree.expandPath(path);
      return;
    }
    for (DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)node.getFirstChild();
         childNode != null;
         childNode = (DefaultMutableTreeNode)node.getChildAfter(childNode)) {
      if (!(childNode instanceof RepositoryNode)) return;
      TreePath path = TreeUtil.getPathFromRoot(childNode);
      if (((RepositoryNode)childNode).isChecked()) {
        myTree.expandPath(path);
      }
    }
  }
}
