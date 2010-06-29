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

package com.intellij.tools;

import com.intellij.CommonBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;

class ToolsPanel extends JPanel {
  static enum Direction {
    UP {
      @Override
      public boolean isAvailable(final int index, final int childCount) {
        return index != 0;
      }
      public int newIndex(final int index) {
        return index - 1;
      }
    }, DOWN{
    @Override
    public boolean isAvailable(final int index, final int childCount) {
      return index < childCount - 1;
    }
    public int newIndex(final int index) {
      return index + 1;
    }
  };

    public abstract boolean isAvailable(final int index, final int childCount);

    public abstract int newIndex(final int index);
  }

  private final CheckboxTree myTree;

  private final JButton myAddButton = new JButton(ToolsBundle.message("tools.add.button"));
  private final JButton myCopyButton = new JButton(ToolsBundle.message("tools.copy.button"));
  private final JButton myEditButton = new JButton(ToolsBundle.message("tools.edit.button"));
  private final JButton myMoveUpButton = new JButton(ToolsBundle.message("tools.move.up.button"));
  private final JButton myMoveDownButton = new JButton(ToolsBundle.message("tools.move.down.button"));
  private final JButton myRemoveButton = new JButton(ToolsBundle.message("tools.remove.button"));
  private boolean myIsModified = false;

  ToolsPanel() {

    myTree = new CheckboxTree(
        new CheckboxTree.CheckboxTreeCellRenderer(){
          public void customizeCellRenderer(final JTree tree,
                                            final Object value,
                                            final boolean selected,
                                            final boolean expanded,
                                            final boolean leaf,
                                            final int row,
                                            final boolean hasFocus) {
            Object object = ((CheckedTreeNode)value).getUserObject();
            if (object instanceof ToolsGroup) {
              final String groupName = ((ToolsGroup)object).getName();
              if (groupName != null) {
                getTextRenderer().append(groupName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
              }
              else {
                getTextRenderer().append("[unnamed group]", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
              }
            }
            else if (object instanceof Tool) {
              getTextRenderer().append(((Tool)object).getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            }
          }
        },
        new CheckedTreeNode(null)){
      @Override
      protected void onDoubleClick(final CheckedTreeNode node) {
        editSelected();
      }
    };

    myTree.setRootVisible(false);

    JScrollPane tableScrollPane = ScrollPaneFactory.createScrollPane(myTree);


    setLayout(new GridBagLayout());
    GridBagConstraints constr;

    // tools list
    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 0;
    constr.weightx = 1;
    constr.weighty = 1;
    constr.insets = new Insets(0, 2, 0, 0);
    constr.fill = GridBagConstraints.BOTH;
    constr.anchor = GridBagConstraints.WEST;
    add(tableScrollPane, constr);

    myTree.setSelectionModel(new DefaultTreeSelectionModel());
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);


    // right side buttons
    constr = new GridBagConstraints();
    constr.gridx = 1;
    constr.gridy = 0;
    constr.anchor = GridBagConstraints.NORTH;
    add(createRightButtonPane(), constr);

//    // for align
//    constr = new GridBagConstraints();
//    constr.gridx = 2;
//    constr.weightx = 1;
//    constr.fill = GridBagConstraints.HORIZONTAL;
//    add(new JPanel(), constr);

    addListeners();

  }

  // add/edit/remove buttons
  private JPanel createRightButtonPane() {
    JPanel pane = new JPanel(new GridBagLayout());
    GridBagConstraints constr = new GridBagConstraints();
    constr.insets = new Insets(0, 5, 5, 5);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.weightx = 1.0;
    constr.gridy = 0;
    pane.add(myAddButton, constr);
    constr.gridy = 1;
    pane.add(myCopyButton, constr);
    constr.gridy = 2;
    pane.add(myEditButton, constr);
    constr.gridy = 3;
    pane.add(myRemoveButton, constr);
    constr.gridy = 4;
    pane.add(myMoveUpButton, constr);
    constr.gridy = 5;
    pane.add(myMoveDownButton, constr);
    return pane;
  }

  void reset() {
    ToolsGroup[] groups = ToolManager.getInstance().getGroups();

    for (ToolsGroup group : groups) {
      insertNewGroup((ToolsGroup)group.copy());
    }
    
    if ((getTreeRoot()).getChildCount() > 0) {
      myTree.setSelectionInterval(0, 0);
    }
    else {
      myTree.getSelectionModel().clearSelection();
    }
    (getModel()).nodeStructureChanged(null);

    TreeUtil.expand(myTree, 5);

    myIsModified = false;

    update();
  }

  private CheckedTreeNode insertNewGroup(final ToolsGroup groupCopy) {
    CheckedTreeNode root = getTreeRoot();
    CheckedTreeNode groupNode = new CheckedTreeNode(groupCopy);
    root.add(groupNode);
    for (Tool tool : groupCopy.getElements()) {
      insertNewTool(groupNode, tool);
    }

    return groupNode;
  }

  private CheckedTreeNode insertNewTool(final CheckedTreeNode groupNode, final Tool toolCopy) {
    CheckedTreeNode toolNode = new CheckedTreeNode(toolCopy);
    ((ToolsGroup)groupNode.getUserObject()).addElement(toolCopy);
    groupNode.add(toolNode);
    nodeWasInserted(toolNode);
    return toolNode;
  }

  private CheckedTreeNode getTreeRoot() {
    return (CheckedTreeNode)myTree.getModel().getRoot();
  }

  void apply() throws IOException{
    // unregister removed tools
    ToolManager toolManager = ToolManager.getInstance();

    toolManager.setTools(getGroupList());
    myIsModified = false;
  }

  private ToolsGroup[] getGroupList() {
    ArrayList<ToolsGroup> result = new ArrayList<ToolsGroup>();
    MutableTreeNode root = (MutableTreeNode)myTree.getModel().getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      result.add((ToolsGroup)((CheckedTreeNode)root.getChildAt(i)).getUserObject());
    }

    return result.toArray(new ToolsGroup[result.size()]);
  }

  boolean isModified() {
    return myIsModified;
  }

  private void addListeners() {
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        update();
      }
    });

    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ToolEditorDialog dlg = new ToolEditorDialog(ToolsPanel.this);
        Tool tool = new Tool();
        tool.setUseConsole(true);
        tool.setFilesSynchronizedAfterRun(true);
        tool.setShownInMainMenu(true);
        tool.setShownInEditor(true);
        tool.setShownInProjectViews(true);
        tool.setShownInSearchResultsPopup(true);
        tool.setEnabled(true);
        dlg.setData(tool, getGroups());
        dlg.show();
        if (dlg.isOK()) {
          insertNewTool(dlg.getData(), true);
        }
        myTree.requestFocus();
      }
    });

    myCopyButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          Tool originalTool = getSelectedTool();

          if (originalTool != null) {
            ToolEditorDialog dlg = new ToolEditorDialog(ToolsPanel.this);
            Tool toolCopy = new Tool();
            toolCopy.copyFrom(originalTool);
            dlg.setData(toolCopy, getGroups());
            dlg.show();
            if (dlg.isOK()) {
              insertNewTool(dlg.getData(), true);
            }
            myTree.requestFocus();
          }
        }
      }
    );

    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        editSelected();
        myTree.requestFocus();
      }
    });

    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        removeSelected();
      }
    });


    myMoveUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        moveNode(Direction.UP);
        myIsModified = true;
      }
    });

    myMoveDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        moveNode(Direction.DOWN);
        myIsModified = true;
      }
    });


    //TODO check edit and delete
  }

  private void moveNode(final Direction direction) {
    CheckedTreeNode node = getSelectedNode();
    if (node != null) {
      if (isMovingAvailable(node, direction)) {
        moveNode(node, direction);
        if (node.getUserObject() instanceof Tool) {
          ToolsGroup group = (ToolsGroup)(((CheckedTreeNode)node.getParent()).getUserObject());
          Tool tool = (Tool)node.getUserObject();
          moveElementInsideGroup(tool, group, direction);
        }
        TreePath path = new TreePath(node.getPath());
        myTree.getSelectionModel().setSelectionPath(path);
        myTree.expandPath(path);
        myTree.requestFocus();
      }
    }
  }

  private void moveElementInsideGroup(final Tool tool, final ToolsGroup group, Direction dir) {
    if (dir == Direction.UP) {
      group.moveElementUp(tool);
    }
    else {
      group.moveElementDown(tool);
    }

  }

  private void moveNode(final CheckedTreeNode toolNode, Direction dir) {
    CheckedTreeNode parentNode = (CheckedTreeNode)toolNode.getParent();
    int index = parentNode.getIndex(toolNode);
    removeNodeFromParent(toolNode);
    int newIndex = dir.newIndex(index);
    parentNode.insert(toolNode, newIndex);
    getModel().nodesWereInserted(parentNode, new int[]{newIndex});
  }

  private boolean isMovingAvailable(final CheckedTreeNode toolNode, Direction dir) {
    TreeNode parent = toolNode.getParent();
    int index = parent.getIndex(toolNode);
    return dir.isAvailable(index, parent.getChildCount());
  }

  private void insertNewTool(final Tool newTool, boolean setSelection) {
    CheckedTreeNode groupNode = findGroupNode(newTool.getGroup());
    if (groupNode == null) {
      groupNode = insertNewGroup(new ToolsGroup(newTool.getGroup()));
      nodeWasInserted(groupNode);
    }
    CheckedTreeNode tool = insertNewTool(groupNode, newTool);
    if (setSelection) {
      TreePath treePath = new TreePath(tool.getPath());
      myTree.expandPath(treePath);
      myTree.getSelectionModel().setSelectionPath(treePath);
    }
    myIsModified = true;
  }

  private void nodeWasInserted(final CheckedTreeNode groupNode) {
    (getModel()).nodesWereInserted(groupNode.getParent(), new int[]{groupNode.getParent().getChildCount() - 1});
  }

  private DefaultTreeModel getModel() {
    return (DefaultTreeModel)myTree.getModel();
  }

  private CheckedTreeNode findGroupNode(final String group) {
    for (int i = 0; i < getTreeRoot().getChildCount(); i++) {
      CheckedTreeNode node = (CheckedTreeNode)getTreeRoot().getChildAt(i);
      ToolsGroup g = (ToolsGroup)node.getUserObject();
      if (Comparing.equal(group,g.getName())) return node;
    }

    return null;
  }

  @Nullable
  private Tool getSelectedTool() {
    CheckedTreeNode node = getSelectedToolNode();
    if (node == null) return null;
    return node.getUserObject() instanceof Tool ? (Tool)node.getUserObject() : null;
  }

  @Nullable
  private ToolsGroup getSelectedToolGroup() {
    CheckedTreeNode node = getSelectedToolNode();
    if (node == null) return null;
    return node.getUserObject() instanceof ToolsGroup ? (ToolsGroup)node.getUserObject() : null;
  }

  private void update() {
    CheckedTreeNode node = getSelectedToolNode();
    Tool selectedTool = getSelectedTool();
    ToolsGroup selectedGroup = getSelectedToolGroup();

    if (selectedTool != null) {
      myAddButton.setEnabled(true);
      myCopyButton.setEnabled(true);
      myEditButton.setEnabled(true);
      myMoveDownButton.setEnabled(isMovingAvailable(node, Direction.DOWN));
      myMoveUpButton.setEnabled(isMovingAvailable(node, Direction.UP));
      myRemoveButton.setEnabled(true);
    }
    else if (selectedGroup != null) {
      myAddButton.setEnabled(true);
      myCopyButton.setEnabled(false);
      myEditButton.setEnabled(false);
      myMoveDownButton.setEnabled(isMovingAvailable(node, Direction.DOWN));
      myMoveUpButton.setEnabled(isMovingAvailable(node, Direction.UP));
      myRemoveButton.setEnabled(true);

    }
    else {
      myAddButton.setEnabled(true);
      myCopyButton.setEnabled(false);
      myEditButton.setEnabled(false);
      myMoveDownButton.setEnabled(false);
      myMoveUpButton.setEnabled(false);
      myRemoveButton.setEnabled(false);
    }

    (getModel()).nodeStructureChanged(null);

    myTree.repaint();
  }

  private void removeSelected() {
    CheckedTreeNode node = getSelectedToolNode();
    if (node != null) {
      int result = Messages.showYesNoDialog(
        this,
        ToolsBundle.message("tools.delete.confirmation"),
        CommonBundle.getWarningTitle(),
        Messages.getWarningIcon()
      );
      if (result != 0) {
        return;
      }
      myIsModified = true;
      if (node.getUserObject() instanceof Tool) {
        Tool tool = (Tool)node.getUserObject();
        CheckedTreeNode parentNode = (CheckedTreeNode)node.getParent();
        ((ToolsGroup)parentNode.getUserObject()).removeElement(tool);
        removeNodeFromParent(node);
        if (parentNode.getChildCount() == 0) {
          removeNodeFromParent(parentNode);
        }
      }
      else if (node.getUserObject() instanceof ToolsGroup) {
        removeNodeFromParent(node);
      }
      update();
      myTree.requestFocus();
    }
  }

  private void removeNodeFromParent(DefaultMutableTreeNode node) {
    TreeNode parent = node.getParent();
    int idx = parent.getIndex(node);
    node.removeFromParent();

    (getModel()).nodesWereRemoved(parent, new int[]{idx}, new TreeNode[]{node});
  }


  private void editSelected() {
    CheckedTreeNode node = getSelectedToolNode();
    if (node != null && node.getUserObject() instanceof Tool) {
      Tool selected = (Tool)node.getUserObject();
      if (selected != null) {
        String oldGroupName = selected.getGroup();
        ToolEditorDialog dlg = new ToolEditorDialog(this);
        dlg.setData(selected, getGroups());
        dlg.show();
        if (dlg.isOK()) {
          selected.copyFrom(dlg.getData());
          String newGroupName = selected.getGroup();
          if (!Comparing.equal(oldGroupName,newGroupName)) {
            CheckedTreeNode oldGroupNode = (CheckedTreeNode)node.getParent();
            removeNodeFromParent(node);
            ((ToolsGroup)oldGroupNode.getUserObject()).removeElement(selected);
            if (oldGroupNode.getChildCount() == 0) {
              removeNodeFromParent(oldGroupNode);
            }

            insertNewTool(selected, true);
          }
          else {
            (getModel()).nodeChanged(node);
          }
          myIsModified = true;
          update();
        }
      }
    }
  }

  private CheckedTreeNode getSelectedToolNode() {
    TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null) {
      return (CheckedTreeNode)selectionPath.getLastPathComponent();
    }
    return null;
  }

  private CheckedTreeNode getSelectedNode() {
    TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null) {
      return (CheckedTreeNode)selectionPath.getLastPathComponent();
    }
    return null;
  }

  private String[] getGroups() {
    ArrayList<String> result = new ArrayList<String>();
    ToolsGroup[] groups = getGroupList();
    for (ToolsGroup group : groups) {
      result.add(group.getName());
    }
    return ArrayUtil.toStringArray(result);
  }
}
